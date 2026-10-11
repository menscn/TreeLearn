package com.treelearn.web.generation;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.treelearn.ai.gateway.AiGatewayError;
import com.treelearn.ai.gateway.GenerationSink;
import com.treelearn.ai.gateway.StreamOutcome;
import com.treelearn.ai.prompt.DeepSeekRequest;
import com.treelearn.ai.prompt.PromptBudget;
import com.treelearn.core.domain.GenerationModel;
import com.treelearn.core.domain.GenerationState;
import com.treelearn.web.credential.SecretPort;
import com.treelearn.web.storage.AnswerRevisionStore;
import com.treelearn.web.storage.BranchStore;
import com.treelearn.web.storage.DatabaseMigrator;
import com.treelearn.web.storage.DesktopDatabase;
import com.treelearn.web.storage.DesktopSchema;
import com.treelearn.web.storage.JdbcSupport;
import com.treelearn.web.storage.MessageStore;
import com.treelearn.web.storage.ModelStore;
import com.treelearn.web.storage.OperationStore;
import com.treelearn.web.storage.ProviderStore;
import com.treelearn.web.storage.TopicStore;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * T-2026-10-10-035: {@code commandId} 幂等必须持久承载（契约 §4）。
 *
 * <p>每条用例都直接驱动 {@link ChatCommandService}，网关为可手推的计数桩。判定的核心是：同一
 * {@code commandId} 重放（无论同进程还是“重启”后）只对应一个 operation、一次网关调用，终态操作重放
 * 只回当前状态。
 *
 * <p>“重启”以仓库既有代理表达：{@link #freshService()} 每次都构造一个全新的
 * {@link DesktopGenerationCoordinator}——它的进程内 attempt 表是空的，正是进程重启会丢失的部分——但
 * 指向同一个 SQLite 文件。若去重依赖进程内 Map，新实例必然二次执行；若去重读持久行，则命中同一
 * operation。同 {@code GenerationCoordinatorTest.anAttemptLeftByARestart...} 的口径。
 */
class CommandIdIdempotencyTest {

    private static final long T0 = 1_700_000_000_000L;

    @TempDir Path dataDir;

    JdbcSupport jdbc;
    TopicStore topics;
    BranchStore branches;
    MessageStore messages;
    AnswerRevisionStore revisions;
    OperationStore operations;
    ProviderStore providers;
    ModelStore models;
    SecretPort secrets;
    PromptBudget budget;
    Clock clock;
    CountingGateway gateway;

    @BeforeEach
    void setUp() {
        new DatabaseMigrator().migrate(dataDir.resolve("treelearn.db"));
        jdbc = new JdbcSupport(new DesktopDatabase(dataDir));
        topics = new TopicStore(jdbc);
        branches = new BranchStore(jdbc);
        messages = new MessageStore(jdbc);
        revisions = new AnswerRevisionStore(jdbc);
        operations = new OperationStore(jdbc);
        providers = new ProviderStore(jdbc);
        models = new ModelStore(jdbc);
        secrets = new StubSecretPort();
        budget = new PromptBudget(60_000, 512);
        clock = Clock.fixed(Instant.ofEpochMilli(T0), ZoneOffset.UTC);
        gateway = new CountingGateway();
        seedProvider();
    }

    private void seedProvider() {
        String providerId = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO " + DesktopSchema.Providers.TABLE + "("
                + DesktopSchema.Providers.ID + "," + DesktopSchema.Providers.NAME + ","
                + DesktopSchema.Providers.BASE_URL + "," + DesktopSchema.Providers.ENABLED + ","
                + DesktopSchema.Providers.IS_BUILTIN + "," + DesktopSchema.Providers.CREATED_AT + ","
                + DesktopSchema.Providers.UPDATED_AT + ") VALUES (?,?,?,?,?,?,?)", s -> {
            s.setString(1, providerId); s.setString(2, "DeepSeek-" + providerId.substring(0, 8));
            s.setString(3, "https://api.deepseek.com"); s.setInt(4, 1); s.setInt(5, 1);
            s.setLong(6, T0); s.setLong(7, T0);
        });
        models.upsertMany(providerId, List.of("deepseek-flash"), () -> UUID.randomUUID().toString(), T0);
    }

    /**
     * 一个全新装配的 service：新的 coordinator（空进程内 Map）+ 各 Store，但共用同一个 SQLite 文件与
     * 计数网关。同一测试里第二次调用即等价于“进程重启后再来一次”。
     */
    private ChatCommandService freshService() {
        DesktopGenerationCoordinator coordinator = new DesktopGenerationCoordinator(jdbc, topics, branches,
                messages, operations, new GenerationEventBus(), gateway, clock);
        return new ChatCommandService(topics, branches, messages, operations, revisions, coordinator,
                providers, models, secrets, budget, clock);
    }

    private int operationRowCount() {
        return jdbc.queryOne("SELECT COUNT(*) FROM " + DesktopSchema.GenerationOperations.TABLE,
                JdbcSupport.NO_PARAMS, rows -> rows.getInt(1)).orElse(0);
    }

    @Test
    void aRepeatedCommandIdInTheSameProcessReturnsTheSameOperationWithoutASecondCall() {
        ChatCommandService service = freshService();
        String commandId = UUID.randomUUID().toString();

        ChatCommandService.CreatedTopic first = service.createTopic(commandId, "光合作用是什么？",
                "deepseek-flash", false, null);
        assertEquals(1, gateway.openCount(), "首问应打开一次网关调用");

        ChatCommandService.CreatedTopic replay = service.createTopic(commandId, "光合作用是什么？",
                "deepseek-flash", false, null);

        assertEquals(first.topic().id, replay.topic().id);
        assertEquals(first.rootBranch().id, replay.rootBranch().id);
        assertEquals(first.userMessage().id, replay.userMessage().id);
        assertEquals(first.aiMessage().id, replay.aiMessage().id);
        assertEquals(first.operation().id, replay.operation().id);
        assertEquals(1, gateway.openCount(), "同进程重放不得再打开一次网关调用");
        assertEquals(2, messages.listForBranch(first.rootBranch().id).size(), "重放不得写入第二对位置");
        assertEquals(1, operationRowCount(), "重放不得写入第二个 operation 行");
    }

    @Test
    void aReplayAfterARestartFindsTheStoredOperationWithoutASecondCallOrRow() {
        ChatCommandService before = freshService();
        String commandId = UUID.randomUUID().toString();
        ChatCommandService.CreatedTopic first = before.createTopic(commandId, "重启后重放",
                "deepseek-flash", false, null);
        int rowsAfterFirst = operationRowCount();
        assertEquals(1, gateway.openCount());

        // “重启”：新实例（新 coordinator，空进程内 Map），同一 SQLite 文件。
        ChatCommandService after = freshService();
        ChatCommandService.CreatedTopic replay = after.createTopic(commandId, "重启后重放",
                "deepseek-flash", false, null);

        assertEquals(first.operation().id, replay.operation().id, "重启后重放必须返回同一 operation");
        assertEquals(first.topic().id, replay.topic().id);
        assertEquals(first.userMessage().id, replay.userMessage().id);
        assertEquals(first.aiMessage().id, replay.aiMessage().id);
        assertEquals(rowsAfterFirst, operationRowCount(), "重启后重放不得新增 operation 行");
        assertEquals(1, gateway.openCount(), "重启后重放不得再打开一次网关调用");
    }

    @Test
    void aFollowUpReplayAfterARestartReturnsTheSameOperation() {
        ChatCommandService before = freshService();
        ChatCommandService.CreatedTopic bootstrap = before.createTopic(UUID.randomUUID().toString(),
                "首问", "deepseek-flash", false, null);
        CountingGateway.Call firstCall = gateway.last();
        firstCall.accept();
        firstCall.delta("", "首问回答");
        firstCall.complete();
        String branchId = bootstrap.rootBranch().id;

        String commandId = UUID.randomUUID().toString();
        ChatCommandService.Turn turn = before.sendMessage(branchId, commandId, "追问", "",
                "deepseek-flash", false);
        int opens = gateway.openCount();
        int rows = operationRowCount();

        ChatCommandService after = freshService();
        ChatCommandService.Turn replay = after.sendMessage(branchId, commandId, "追问", "",
                "deepseek-flash", false);

        assertEquals(turn.operation().id, replay.operation().id, "追问重启后重放必须返回同一 operation");
        assertEquals(turn.userMessage().id, replay.userMessage().id);
        assertEquals(turn.aiMessage().id, replay.aiMessage().id);
        assertEquals(rows, operationRowCount(), "追问重放不得新增 operation 行");
        assertEquals(opens, gateway.openCount(), "追问重放不得再打开一次网关调用");
    }

    @Test
    void replayingATerminalOperationReturnsItsCurrentStateWithoutANewVersionOrCall() {
        ChatCommandService service = freshService();
        String commandId = UUID.randomUUID().toString();
        ChatCommandService.CreatedTopic first = service.createTopic(commandId, "终态重放",
                "deepseek-flash", false, null);

        CountingGateway.Call call = gateway.last();
        call.accept();
        call.fail(AiGatewayError.Kind.PROVIDER_UNAVAILABLE);
        assertEquals(GenerationState.FAILED,
                operations.byId(first.operation().id).orElseThrow().state,
                "网关失败后该操作应落为 FAILED 终态");
        int rows = operationRowCount();
        int revisionsBefore = revisions.countForMessage(first.aiMessage().id);

        ChatCommandService.CreatedTopic replay = service.createTopic(commandId, "终态重放",
                "deepseek-flash", false, null);

        assertEquals(first.operation().id, replay.operation().id);
        assertEquals(GenerationState.FAILED, replay.operation().state, "终态重放返回当前状态");
        assertEquals(rows, operationRowCount(), "终态重放不得新增 operation 行");
        assertEquals(revisionsBefore, revisions.countForMessage(first.aiMessage().id),
                "终态重放不得产生新版本");
        assertEquals(1, gateway.openCount(), "终态重放不得重启生成");
    }

    /** 测试用凭据端口：任何 provider 都有 Key。 */
    static final class StubSecretPort implements SecretPort {

        @Override
        public void save(String target, char[] secret) {
        }

        @Override
        public boolean has(String target) {
            return true;
        }

        @Override
        public Optional<char[]> read(String target) {
            return Optional.of("sk-test".toCharArray());
        }

        @Override
        public void delete(String target) {
        }
    }

    /** 计数网关：每次 open 记一笔，由测试手推 accept/delta/complete/fail。 */
    static final class CountingGateway implements DesktopGenerationGateway {

        private final List<Call> calls = new ArrayList<>();

        @Override
        public Stream open(GenerationModel model, DeepSeekRequest request, GenerationSink sink) {
            Call call = new Call(sink);
            calls.add(call);
            return call;
        }

        int openCount() {
            return calls.size();
        }

        Call last() {
            return calls.get(calls.size() - 1);
        }

        static final class Call implements DesktopGenerationGateway.Stream {

            private final GenerationSink sink;
            private boolean accepted;
            private boolean cancelled;

            Call(GenerationSink sink) {
                this.sink = sink;
            }

            void accept() {
                accepted = sink.onAccepted(200);
            }

            void delta(String reasoning, String content) {
                if (accepted) {
                    sink.onDelta(reasoning, content);
                }
            }

            void complete() {
                sink.onTerminal(new StreamOutcome(StreamOutcome.Kind.COMPLETED, null, 200, 0, 0));
            }

            void fail(AiGatewayError.Kind kind) {
                sink.onTerminal(new StreamOutcome(StreamOutcome.Kind.FAILED,
                        AiGatewayError.of(kind, 500, "provider failed"), 500, 0, 0));
            }

            @Override
            public void cancel() {
                cancelled = true;
            }

            @Override
            public boolean isCancelled() {
                return cancelled;
            }
        }
    }
}
