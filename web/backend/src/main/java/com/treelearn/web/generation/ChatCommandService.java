package com.treelearn.web.generation;

import com.treelearn.ai.prompt.DeepSeekContextEncoder;
import com.treelearn.ai.prompt.DeepSeekMessage;
import com.treelearn.ai.prompt.DeepSeekRequest;
import com.treelearn.ai.prompt.DeepSeekRequestAssembler;
import com.treelearn.ai.prompt.PromptBudget;
import com.treelearn.ai.prompt.PromptBuildException;
import com.treelearn.core.domain.Branch;
import com.treelearn.core.domain.ContextPlan;
import com.treelearn.core.domain.DomainResult;
import com.treelearn.core.domain.GenerationCommand;
import com.treelearn.core.domain.GenerationModel;
import com.treelearn.core.domain.Guard;
import com.treelearn.core.domain.Message;
import com.treelearn.core.domain.PromptPurpose;
import com.treelearn.core.domain.Topic;
import com.treelearn.web.credential.SecretPort;
import com.treelearn.web.storage.BranchStore;
import com.treelearn.web.storage.MessageStore;
import com.treelearn.web.storage.ModelStore;
import com.treelearn.web.storage.OperationStore;
import com.treelearn.web.storage.ProviderStore;
import com.treelearn.web.storage.TopicStore;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 核心闭环命令装配（任务 T-2026-10-10-028）：把 §6 请求体变成 {@link GenerationCommand} 与
 * {@link DeepSeekRequest}，委托 {@link DesktopGenerationCoordinator} 接受/提交，并把拒绝翻译成 §3.3 码。
 *
 * <p>只有一条会话用途：{@code LEARNING_ANSWER}。历史由分支自身的行重建（引用稳定修订的散文由
 * {@link DeepSeekContextEncoder} 编码），当前输入为该轮的提问与引用；容量由
 * {@link DeepSeekRequestAssembler} 判定（不裁剪、不丢历史）。
 *
 * <p>模型由客户端请求携带的 wire id 解析：必须是本构建已知的档位，且存在一个启用提供商提供该模型，
 * 其凭据端口声明有 Key；否则分别拒绝为 {@code REQUEST_INVALID}/{@code KEY_MISSING}/{@code NO_MODELS}。
 *
 * <p><strong>{@code commandId} 幂等（契约 §4，任务 T-2026-10-10-035）</strong>：客户端 {@code commandId}
 * 经共享领域 {@link GenerationCommand#createWithId} 成为命令身份，落库到
 * {@code generation_operations.command_id}。接受前先以 {@link OperationStore#byCommandId} 查持久记录：
 * 命中即按既有 operation 重建同一响应（含终态操作——返回其当前状态，不重启生成、不产生新版本）；未命中
 * 才接受并提交。去重因此是持久事实而非进程内缓存，服务重启后同一 commandId 重放仍返回同一 operation
 * 且不产生第二次行与第二次模型调用。
 */
public final class ChatCommandService {

    private static final Logger LOG = LoggerFactory.getLogger(ChatCommandService.class);
    private static final PromptPurpose ANSWER_PURPOSE = PromptPurpose.LEARNING_ANSWER;

    private final TopicStore topics;
    private final BranchStore branches;
    private final MessageStore messages;
    private final OperationStore operations;
    private final DesktopGenerationCoordinator coordinator;
    private final ProviderStore providers;
    private final ModelStore models;
    private final SecretPort secrets;
    private final PromptBudget budget;
    private final Clock clock;
    private final Object acceptLock = new Object();

    public ChatCommandService(TopicStore topics, BranchStore branches, MessageStore messages,
            OperationStore operations, DesktopGenerationCoordinator coordinator, ProviderStore providers,
            ModelStore models, SecretPort secrets, PromptBudget budget, Clock clock) {
        this.topics = topics; this.branches = branches; this.messages = messages;
        this.operations = operations; this.coordinator = coordinator; this.providers = providers;
        this.models = models; this.secrets = secrets; this.budget = budget; this.clock = clock;
    }

    /** 首问结果：主题 + 根分支 + 两个位置 + 操作（契约 §6.2 TopicBootstrap 的域侧素材）。 */
    public record CreatedTopic(Topic topic, Branch rootBranch, Message userMessage, Message aiMessage,
            OperationStore.Operation operation) {}

    /** 追问结果：两个位置 + 操作（契约 §6.3 TurnAccepted）。 */
    public record Turn(Message userMessage, Message aiMessage, OperationStore.Operation operation) {}

    /** 原位重试结果：同一位置 + 新操作（契约 §6.4）。 */
    public record Replaced(Message message, OperationStore.Operation operation) {}

    /** §6.2 首问原子接受。 */
    public CreatedTopic createTopic(String commandId, String question, String modelId, boolean thinking,
            String quotedText) {
        requireCommand(commandId);
        requireText(question);
        synchronized (acceptLock) {
            Optional<OperationStore.Operation> stored = operations.byCommandId(commandId);
            if (stored.isPresent()) {
                return replayedCreated(stored.get());
            }
            GenerationModel model = resolveModel(modelId);
            long now = clock.millis();
            Topic topic = Topic.create(now);
            Branch root = Branch.root(topic.id, now);
            Message user = Message.user(root.id, 0, question, quote(quotedText), now);
            Message ai = Message.ai(root.id, 1, now);
            DeepSeekRequest request = assemble(root.id, 0, question, quotedText, thinking);
            GenerationCommand command = command(commandId, root.id, ai.id, model, thinking, request, now);
            DesktopGenerationCoordinator.AcceptOutcome accept =
                    coordinator.acceptForNewTopic(command, topic, root, user, ai);
            if (!accept.isAccepted()) {
                throw coordinatorRefusal(accept.refusal, root.id);
            }
            submit(command, request);
            LOG.info("first question accepted: topic={} operation={} model={}", topic.id,
                    accept.accepted.operationId, model.wireId);
            return replayedCreated(operation(accept.accepted.operationId));
        }
    }

    /** §6.3 连续追问（只向当前分支追加）。 */
    public Turn sendMessage(String branchId, String commandId, String text, String quote, String modelId,
            boolean thinking) {
        requireCommand(commandId);
        requireText(text);
        synchronized (acceptLock) {
            Optional<OperationStore.Operation> stored = operations.byCommandId(commandId);
            if (stored.isPresent()) {
                return replayedTurn(stored.get());
            }
            GenerationModel model = resolveModel(modelId);
            Branch branch = branches.get(branchId).orElseThrow(() -> notFound("branch"));
            long now = clock.millis();
            int userOrdinal = messages.nextOrdinal(branchId);
            Message user = Message.user(branchId, userOrdinal, text, quote(quote), now);
            Message ai = Message.ai(branchId, userOrdinal + 1, now);
            DeepSeekRequest request = assemble(branchId, userOrdinal, text, quote, thinking);
            GenerationCommand command = command(commandId, branchId, ai.id, model, thinking, request, now);
            DesktopGenerationCoordinator.AcceptOutcome accept =
                    coordinator.acceptForBranch(command, user, ai);
            if (!accept.isAccepted()) {
                throw coordinatorRefusal(accept.refusal, branchId);
            }
            submit(command, request);
            return replayedTurn(operation(accept.accepted.operationId));
        }
    }

    /** §6.4 原位重试（同一位置新尝试，不改 ordinal、不新增问答）。 */
    public Replaced retry(String messageId, String commandId, String modelId, boolean thinking) {
        requireCommand(commandId);
        synchronized (acceptLock) {
            Optional<OperationStore.Operation> stored = operations.byCommandId(commandId);
            if (stored.isPresent()) {
                return replayedReplaced(stored.get());
            }
            GenerationModel model = resolveModel(modelId);
            Message ai = messages.get(messageId).orElseThrow(
                    () -> new ServiceRefusalException("OPERATION_NOT_COMPLETABLE", Map.of()));
            if (!ai.isAi()) {
                throw new ServiceRefusalException("MESSAGE_ROLE_MISMATCH", Map.of());
            }
            Optional<OperationStore.Operation> active = operations.activeForBranch(ai.branchId);
            if (active.isPresent()) {
                throw new ServiceRefusalException("ILLEGAL_STATE_TRANSITION",
                        Map.of("state", active.get().state.name()));
            }
            MessageStore.Position question = precedingQuestion(ai);
            long now = clock.millis();
            DeepSeekRequest request = assemble(ai.branchId, question.message.ordinal,
                    question.message.userContent, question.message.quotedText(), thinking);
            GenerationCommand command =
                    command(commandId, ai.branchId, ai.id, model, thinking, request, now);
            DesktopGenerationCoordinator.AcceptOutcome accept = coordinator.acceptForRetry(command);
            if (!accept.isAccepted()) {
                throw coordinatorRefusal(accept.refusal, ai.branchId);
            }
            submit(command, request);
            return replayedReplaced(operation(accept.accepted.operationId));
        }
    }

    // ------------------------------------------------------------------ internals

    private MessageStore.Position precedingQuestion(Message ai) {
        for (MessageStore.Position position : messages.listForBranch(ai.branchId)) {
            if (position.message.ordinal == ai.ordinal - 1 && position.message.isUser()) {
                return position;
            }
        }
        throw new ServiceRefusalException("OPERATION_NOT_COMPLETABLE", Map.of());
    }

    private DeepSeekRequest assemble(String branchId, int questionOrdinal, String question, String quote,
            boolean thinking) {
        List<ContextPlan.Entry> entries = new ArrayList<>();
        for (MessageStore.Position position : messages.listForBranch(branchId)) {
            if (position.message.ordinal >= questionOrdinal) {
                break;
            }
            if (position.message.isUser()) {
                entries.add(ContextPlan.Entry.userMessage(position.message.id, position.message.userContent));
            } else if (position.hasActiveRevision()) {
                entries.add(ContextPlan.Entry.stableRevision(position.message.activeRevisionId(),
                        position.reasoningContent, position.answerContent));
            }
        }
        List<DeepSeekMessage> history = DeepSeekContextEncoder.encode(new ContextPlan(entries));
        String currentInput = DeepSeekContextEncoder.currentInput(question, quote);
        try {
            return DeepSeekRequestAssembler.assemble(ANSWER_PURPOSE, thinking, history, currentInput, budget);
        } catch (PromptBuildException refused) {
            throw promptRefusal(refused);
        }
    }

    private ServiceRefusalException promptRefusal(PromptBuildException refused) {
        if (refused.reason == PromptBuildException.Reason.RESERVE_EXCEEDS_BUDGET
                || refused.reason == PromptBuildException.Reason.HISTORY_EXCEEDS_BUDGET) {
            return new ServiceRefusalException("CONTEXT_EXCEEDS_BUDGET",
                    Map.of("capacityChars", budget.capacityChars, "minAnswerChars", budget.minAnswerChars));
        }
        return new ServiceRefusalException("INTERNAL_ERROR", Map.of());
    }

    private GenerationCommand command(String commandId, String branchId, String messageId,
            GenerationModel model, boolean thinking, DeepSeekRequest request, long now) {
        DomainResult<GenerationCommand> created = GenerationCommand.createWithId(commandId, branchId,
                messageId, ANSWER_PURPOSE, request.policyVersion, model, thinking, now);
        if (created.isFailure()) {
            throw new ServiceRefusalException(created.error().code.name(), Map.of());
        }
        return created.value();
    }

    private void submit(GenerationCommand command, DeepSeekRequest request) {
        DesktopGenerationCoordinator.SubmitOutcome outcome = coordinator.submit(command, ignored -> request);
        if (!outcome.isAccepted()) {
            switch (outcome.refusal) {
                case STORAGE -> throw new ServiceRefusalException("STORAGE_REFUSED", Map.of());
                case VERSION_MISMATCH -> throw new ServiceRefusalException(
                        "PROMPT_POLICY_VERSION_MISMATCH", Map.of("purpose", ANSWER_PURPOSE.name()));
                default -> throw new ServiceRefusalException("INTERNAL_ERROR", Map.of());
            }
        }
    }

    private static ServiceRefusalException coordinatorRefusal(
            DesktopGenerationCoordinator.Refusal refusal, String branchId) {
        switch (refusal) {
            case BRANCH_BUSY:
                return new ServiceRefusalException("BRANCH_BUSY", Map.of("branchId", branchId));
            case TOPIC_ID_ALREADY_EXISTS:
                return new ServiceRefusalException("TOPIC_ID_ALREADY_EXISTS", Map.of());
            case STORAGE:
                return new ServiceRefusalException("STORAGE_REFUSED", Map.of());
            case VERSION_MISMATCH:
                return new ServiceRefusalException("PROMPT_POLICY_VERSION_MISMATCH",
                        Map.of("purpose", ANSWER_PURPOSE.name()));
            default:
                return new ServiceRefusalException("INTERNAL_ERROR", Map.of());
        }
    }

    /**
     * 解析客户端携带的模型 wire id：必须是已知档位；必须存在启用提供商提供该模型；其凭据须声明有 Key。
     */
    private GenerationModel resolveModel(String modelId) {
        GenerationModel model;
        try {
            model = GenerationModel.fromWireId(modelId);
        } catch (IllegalArgumentException unknown) {
            throw new ServiceRefusalException("REQUEST_INVALID", Map.of("httpStatus", 400));
        }
        boolean offered = false;
        for (ProviderStore.Provider provider : providers.list()) {
            if (!provider.enabled) {
                continue;
            }
            for (ModelStore.Model candidate : models.listForProvider(provider.id)) {
                if (!candidate.modelId.equals(model.wireId)) {
                    continue;
                }
                offered = true;
                if (secrets.has(SecretPort.providerTarget(provider.id))) {
                    return model;
                }
            }
        }
        throw new ServiceRefusalException(offered ? "KEY_MISSING" : "NO_MODELS", Map.of());
    }

    /**
     * 重放首问：以持久 operation 重建响应。主题与根分支由 operation 的 branch 反查（根分支的
     * {@code topicId} 即主题 id），两个位置由 operation 的 messageId 与其前一条 USER 位置还原。
     */
    private CreatedTopic replayedCreated(OperationStore.Operation replay) {
        Branch root = branches.get(replay.branchId).orElseThrow(() -> notFound("branch"));
        Topic topic = topics.get(root.topicId).orElseThrow(() -> notFound("topic"));
        Message ai = messages.get(replay.messageId).orElseThrow(() -> notFound("message"));
        Message user = precedingQuestion(ai).message;
        return new CreatedTopic(topic, root, user, ai, operation(replay.id));
    }

    /** 重放追问：两个位置由 operation 的 messageId 与其前一条 USER 位置还原。 */
    private Turn replayedTurn(OperationStore.Operation replay) {
        Message ai = messages.get(replay.messageId).orElseThrow(() -> notFound("message"));
        Message user = precedingQuestion(ai).message;
        return new Turn(user, ai, operation(replay.id));
    }

    /** 重放原位重试：只涉及 operation 固定的那个 AI 位置。 */
    private Replaced replayedReplaced(OperationStore.Operation replay) {
        Message ai = messages.get(replay.messageId).orElseThrow(() -> notFound("message"));
        return new Replaced(ai, operation(replay.id));
    }

    private OperationStore.Operation operation(String operationId) {
        return operations.byId(operationId).orElseThrow(() -> notFound("operation"));
    }

    private static ServiceRefusalException notFound(String resource) {
        return new ServiceRefusalException("NOT_FOUND", Map.of("resource", resource));
    }

    /**
     * 校验客户端 {@code commandId}：必填且必须是规范 UUID 字符串（契约 §4）。它随后会成为命令身份
     * 落库，非规范形态属客户端可修复错误，统一为 {@code 400 REQUEST_INVALID}（§3.3），不落 500。
     */
    private static void requireCommand(String commandId) {
        if (commandId == null || commandId.trim().isEmpty()) {
            throw new ServiceRefusalException("REQUEST_INVALID", Map.of("httpStatus", 400));
        }
        try {
            Guard.id(commandId, "commandId");
        } catch (IllegalArgumentException malformed) {
            throw new ServiceRefusalException("REQUEST_INVALID", Map.of("httpStatus", 400));
        }
    }

    private static void requireText(String text) {
        if (text == null || text.trim().isEmpty()) {
            throw new ServiceRefusalException("REQUEST_INVALID", Map.of("httpStatus", 400));
        }
    }

    private static String quote(String quotedText) {
        return quotedText == null ? "" : quotedText;
    }
}
