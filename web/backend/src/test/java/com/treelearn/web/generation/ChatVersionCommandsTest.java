package com.treelearn.web.generation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.treelearn.ai.prompt.PromptBudget;
import com.treelearn.ai.gateway.AiGatewayError;
import com.treelearn.core.domain.AnswerRevision;
import com.treelearn.core.domain.GenerationModel;
import com.treelearn.web.credential.SecretPort;
import com.treelearn.web.storage.AnswerRevisionStore;
import com.treelearn.web.storage.BranchStore;
import com.treelearn.web.storage.DesktopDatabase;
import com.treelearn.web.storage.DesktopSchema;
import com.treelearn.web.storage.DatabaseMigrator;
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
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * T-2026-10-10-051：reset 与版本选择的判定语义（契约 §6.4），镜像 Android
 * {@code AnswerVersions.reset/selectRevision}。真实服务 + 计数网关（同
 * {@link CommandIdIdempotencyTest} 口径）：reset 的稳定版前置、select 的指针移动与三类拒绝。
 */
class ChatVersionCommandsTest {

    private static final long T0 = 1_700_000_000_000L;
    private static final String POLICY = "tl-learning-answer/1.1.0";

    @TempDir Path dataDir;

    JdbcSupport jdbc;
    TopicStore topics;
    BranchStore branches;
    MessageStore messages;
    AnswerRevisionStore revisions;
    OperationStore operations;
    ProviderStore providers;
    ModelStore models;
    Clock clock;
    CommandIdIdempotencyTest.CountingGateway gateway;

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
        clock = Clock.fixed(Instant.ofEpochMilli(T0), ZoneOffset.UTC);
        gateway = new CommandIdIdempotencyTest.CountingGateway();
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
        models.upsertMany(providerId, List.of("deepseek-flash"),
                () -> UUID.randomUUID().toString(), T0);
    }

    private ChatCommandService freshService() {
        DesktopGenerationCoordinator coordinator = new DesktopGenerationCoordinator(jdbc, topics,
                branches, messages, operations, new GenerationEventBus(), gateway, clock);
        return new ChatCommandService(topics, branches, messages, operations, revisions, coordinator,
                providers, models, secrets(), budget(), clock);
    }

    private SecretPort secrets() {
        return new SecretPort() {
            @Override public void save(String target, char[] secret) { }
            @Override public boolean has(String target) { return true; }
            @Override public java.util.Optional<char[]> read(String target) {
                return java.util.Optional.of("sk-test".toCharArray());
            }
            @Override public void delete(String target) { }
        };
    }

    private PromptBudget budget() {
        return new PromptBudget(60_000, 512);
    }

    /**
     * 首问后走真实 Phase 4 稳定化路径落定（手推 sink：accept→delta→complete）：STABLE、不可变
     * revision 与显示指针在同一个 act 里产生——直接 markTerminal(STABLE) 会被存储层守卫拒绝
     * （「finished attempt with no revision」），这正是要走的门。
     */
    private ChatCommandService.CreatedTopic seedStable(ChatCommandService service) {
        ChatCommandService.CreatedTopic created = service.createTopic(UUID.randomUUID().toString(),
                "光合作用是什么？", "deepseek-flash", false, null);
        gateway.last().accept();
        gateway.last().delta("先想", "回答 v1");
        gateway.last().complete();
        return created;
    }

    /** 首问后把 attempt 以 FAILED 落定（无 revision 产生）：得到「无稳定版可显示」的位置。 */
    private ChatCommandService.CreatedTopic seedFailed(ChatCommandService service) {
        ChatCommandService.CreatedTopic created = service.createTopic(UUID.randomUUID().toString(),
                "问题", "deepseek-flash", false, null);
        gateway.last().accept();
        gateway.last().fail(AiGatewayError.Kind.PROVIDER_UNAVAILABLE);
        return created;
    }

    @Test
    void resetWithoutStableVersionIsRefusedAndCallsNoGateway() {
        ChatCommandService service = freshService();
        ChatCommandService.CreatedTopic created = seedFailed(service);
        int gatewayCalls = gateway.openCount();

        ServiceRefusalException refused = assertThrows(ServiceRefusalException.class,
                () -> service.reset(created.aiMessage().id, UUID.randomUUID().toString(),
                        "deepseek-flash", false));
        assertEquals("OPERATION_NOT_STABLE", refused.code);
        assertEquals(gatewayCalls, gateway.openCount(), "拒绝路径不得触网关");
    }

    @Test
    void resetOnStableVersionStartsNewAttemptAndKeepsOldRevision() {
        ChatCommandService service = freshService();
        ChatCommandService.CreatedTopic created = seedStable(service);
        String aiId = created.aiMessage().id;
        int gatewayCalls = gateway.openCount();
        int operationsBefore = operationRowCount();

        ChatCommandService.Replaced replaced = service.reset(aiId, UUID.randomUUID().toString(),
                "deepseek-flash", false);

        assertEquals(aiId, replaced.message().id, "ordinal 位置不变");
        assertEquals(created.aiMessage().ordinal, replaced.message().ordinal);
        assertEquals(gatewayCalls + 1, gateway.openCount(), "reset 应打开一次网关调用");
        assertEquals(operationsBefore + 1, operationRowCount(), "reset 恰好新增一个 operation");
        AnswerRevision beforeReset = revisions.listForMessage(aiId).get(0);
        // 新 attempt 走完稳定化路径：新版本落库、旧版本保留可追溯。
        gateway.last().accept();
        gateway.last().delta("再想", "回答 v2");
        gateway.last().complete();
        assertEquals(2, revisions.listForMessage(aiId).size(), "旧版本必须保留可追溯");
        assertEquals(beforeReset.id, revisions.listForMessage(aiId).get(0).id,
                "旧版本仍在且顺序不变");
    }

    @Test
    void selectSwitchesDisplayPointerAndReturnsTarget() {
        ChatCommandService service = freshService();
        ChatCommandService.CreatedTopic created = seedStable(service);
        String aiId = created.aiMessage().id;
        AnswerRevision v2 = revisions.insert(aiId, "", "回答 v2", GenerationModel.FLASH, false,
                POLICY, T0 + 3);
        String before = messages.get(aiId).orElseThrow().activeRevisionId();

        AnswerRevision selected = service.selectRevision(aiId, v2.id, UUID.randomUUID().toString());

        assertEquals(v2.id, selected.id);
        assertNotEquals(before, messages.get(aiId).orElseThrow().activeRevisionId(),
                "显示指针必须移动");
        assertEquals(v2.id, messages.get(aiId).orElseThrow().activeRevisionId());
    }

    @Test
    void selectingCurrentVersionIsRefusedAsImmutable() {
        ChatCommandService service = freshService();
        ChatCommandService.CreatedTopic created = seedStable(service);
        String current = messages.get(created.aiMessage().id).orElseThrow().activeRevisionId();

        ServiceRefusalException refused = assertThrows(ServiceRefusalException.class,
                () -> service.selectRevision(created.aiMessage().id, current,
                        UUID.randomUUID().toString()));
        assertEquals("REVISION_IMMUTABLE", refused.code);
    }

    @Test
    void revisionOfAnotherPositionIsNotFound() {
        ChatCommandService service = freshService();
        ChatCommandService.CreatedTopic first = seedStable(service);
        AnswerRevision foreign = revisions.insert(first.aiMessage().id, "", "回答 v2",
                GenerationModel.FLASH, false, POLICY, T0 + 3);
        ChatCommandService.CreatedTopic other = seedStable(service);

        ServiceRefusalException refused = assertThrows(ServiceRefusalException.class,
                () -> service.selectRevision(other.aiMessage().id, foreign.id,
                        UUID.randomUUID().toString()));
        assertEquals("OPERATION_NOT_COMPLETABLE", refused.code);
    }

    @Test
    void commandsAreRefusedWhileAnAttemptIsRunning() {
        ChatCommandService service = freshService();
        ChatCommandService.CreatedTopic created = seedStable(service);
        // 制造一个活跃 attempt（占住分支）：对追问位置发问后不落定。
        service.sendMessage(created.rootBranch().id, UUID.randomUUID().toString(), "追问", "",
                "deepseek-flash", false);

        assertThrows(ServiceRefusalException.class,
                () -> service.reset(created.aiMessage().id, UUID.randomUUID().toString(),
                        "deepseek-flash", false));
        String current = messages.get(created.aiMessage().id).orElseThrow().activeRevisionId();
        assertThrows(ServiceRefusalException.class,
                () -> service.selectRevision(created.aiMessage().id, current,
                        UUID.randomUUID().toString()));
        assertTrue(operations.activeForBranch(created.rootBranch().id).isPresent(),
                "前置条件：分支上确实有活跃尝试");
    }

    private int operationRowCount() {
        return jdbc.queryOne("SELECT COUNT(*) FROM " + DesktopSchema.GenerationOperations.TABLE,
                JdbcSupport.NO_PARAMS, rows -> rows.getInt(1)).orElse(0);
    }
}
