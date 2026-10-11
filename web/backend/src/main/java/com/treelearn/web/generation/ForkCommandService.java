package com.treelearn.web.generation;

import com.treelearn.core.domain.AnswerRevision;
import com.treelearn.core.domain.Branch;
import com.treelearn.core.domain.BranchAnchor;
import com.treelearn.core.domain.BranchSource;
import com.treelearn.core.domain.ContextSnapshot;
import com.treelearn.core.domain.DomainResult;
import com.treelearn.core.domain.GenerationState;
import com.treelearn.core.domain.Guard;
import com.treelearn.core.domain.Message;
import com.treelearn.core.domain.Topic;
import com.treelearn.web.storage.AnswerRevisionStore;
import com.treelearn.web.storage.BranchStore;
import com.treelearn.web.storage.JdbcSupport;
import com.treelearn.web.storage.MessageStore;
import com.treelearn.web.storage.OperationStore;
import com.treelearn.web.storage.SnapshotStore;
import com.treelearn.web.storage.TopicStore;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 稳定回答分叉命令装配（任务 T-2026-10-10-037；契约 {@code web/contracts/api.md} §6.3）。
 *
 * <p><strong>唯一判定来自共享域。</strong>来源稳定性、锚点合法性、快照载荷与溯源都由
 * {@link BranchSource#fromOperation}（要求 {@link GenerationState#STABLE}）、
 * {@link BranchAnchor#ofRange}（半开区间 + quote/hash + 跨度一致）、
 * {@link ContextSnapshot#create}/{@link ContextSnapshot#fromStored}、{@link Branch#child} 决定，
 * 本类不复制任何一条规则。语义对照 Android {@code ChatWiring.fork} +
 * {@code SqliteChatStore.createBranch}/{@code BranchCreation.create}。
 *
 * <p><strong>冻结与三分写。</strong>{@code branches.context_snapshot_id} 与
 * {@code context_snapshots.branch_id} 互相引用，单条 INSERT 无法承载，故子分支、出生快照与指针在
 * {@link #createBranchAtomically} 的同一事务内按「分支 → 快照 → 指针」写完，或全不写——与 Android
 * {@code BranchCreation} 的三分写单事务同构。快照是冻结的引用列表（不是内容副本），父分支此后的新
 * 问答/新版本不会回流进它。
 *
 * <p><strong>可选首问复用既有接受路径。</strong>请求带 {@code question} 时，分叉后委托
 * {@link ChatCommandService#sendMessage} 把 USER 位 + AI 位 + 操作在同一事务内落进新分支，从而复用
 * 模型解析与 {@code commandId} 持久幂等（不另立判定）；空问题只建分支。首问不存在时无操作行，故
 * {@code commandId} 去重只对带首问的分叉生效（无问题分叉无持久命令载体，见任务「偏差」）。
 */
public final class ForkCommandService {

    private static final Logger LOG = LoggerFactory.getLogger(ForkCommandService.class);

    private final JdbcSupport jdbc;
    private final TopicStore topics;
    private final BranchStore branches;
    private final MessageStore messages;
    private final AnswerRevisionStore revisions;
    private final OperationStore operations;
    private final SnapshotStore snapshots;
    private final ChatCommandService commands;
    private final Clock clock;

    public ForkCommandService(JdbcSupport jdbc, TopicStore topics, BranchStore branches,
            MessageStore messages, AnswerRevisionStore revisions, OperationStore operations,
            SnapshotStore snapshots, ChatCommandService commands, Clock clock) {
        this.jdbc = jdbc; this.topics = topics; this.branches = branches; this.messages = messages;
        this.revisions = revisions; this.operations = operations; this.snapshots = snapshots;
        this.commands = commands; this.clock = clock;
    }

    /**
     * 分叉的域侧素材：子分支 + 主题 + （可选首问成功时）两个位置与操作。{@code operation} 为 null
     * 表示只建了分支（无 {@code question}）。
     */
    public record Forked(Branch branch, Topic topic, Message userMessage, Message aiMessage,
            OperationStore.Operation operation) {}

    /**
     * 按 §6.3 执行一次分叉。
     *
     * @param anchor 整条分叉为 null，选文分叉为已通过 {@link BranchAnchor#ofRange} 校验的锚点
     */
    public Forked fork(String parentBranchId, String commandId, String sourceMessageId,
            String sourceRevisionId, BranchAnchor anchor, String question, String modelId,
            boolean thinking) {
        requireCommand(commandId);
        // 带首问的分叉会落一条操作行，其 command_id 就是持久幂等载体：命中即按既有子分支重建响应。
        Optional<OperationStore.Operation> stored = operations.byCommandId(commandId);
        if (stored.isPresent()) {
            return replayedFork(stored.get());
        }
        Branch parent = branches.get(parentBranchId).orElseThrow(() -> notFound("branch"));
        requireUuid(sourceMessageId, "messageId");
        Message source = messages.get(sourceMessageId).orElseThrow(() -> notFound("message"));
        if (!source.isAi()) {
            // 问题没有可分的版本；与「源版本不存在/非稳定」同一拒绝。
            throw refusal("OPERATION_NOT_STABLE");
        }
        requireUuid(sourceRevisionId, "revisionId");
        Optional<AnswerRevision> revision = revisions.get(sourceRevisionId);
        if (revision.isEmpty() || !revision.get().messageId.equals(sourceMessageId)) {
            throw refusal("OPERATION_NOT_STABLE");
        }
        Branch sourceBranch = branches.get(source.branchId).orElseThrow(() -> notFound("branch"));
        if (!sourceBranch.topicId.equals(parent.topicId)) {
            throw refusal("SOURCE_MESSAGE_NOT_IN_TOPIC");
        }
        Topic topic = topics.get(parent.topicId).orElseThrow(() -> notFound("topic"));

        long now = clock.millis();
        List<ContextSnapshot.Source> sources =
                sourcesThrough(sourceBranch.id, sourceMessageId, sourceRevisionId);
        String snapshotId = Guard.newId();
        DomainResult<BranchSource> provenance = BranchSource.fromOperation(sourceBranch.id,
                sourceMessageId, snapshotId, GenerationState.STABLE, sourceRevisionId,
                anchor == null ? BranchAnchor.wholeAnswer() : anchor);
        if (provenance.isFailure()) {
            throw refusal(provenance.error().code.name());
        }
        DomainResult<Branch> built = Branch.child(topic.id, provenance.value(), now);
        if (built.isFailure()) {
            throw refusal(built.error().code.name());
        }
        Branch child = built.value();
        ContextSnapshot derived = ContextSnapshot.create(child.id, sources, now);
        DomainResult<ContextSnapshot> pinned = ContextSnapshot.fromStored(snapshotId, child.id,
                derived.schemaVersion, derived.digest, derived.payload, derived.createdAt);
        if (pinned.isFailure()) {
            // 编解码器拒绝了自己刚产出的载荷：构建缺陷，不得写入一个上下文不可信的分支。
            throw refusal("SNAPSHOT_PAYLOAD_INVALID");
        }
        createBranchAtomically(child, pinned.value());

        if (question == null || question.isBlank()) {
            LOG.info("branch forked: parent={} child={} revision={} anchor={}", sourceBranch.id, child.id,
                    sourceRevisionId, anchor == null ? "wholeAnswer" : "range");
            return new Forked(child, topic, null, null, null);
        }
        ChatCommandService.Turn turn =
                commands.sendMessage(child.id, commandId, question, "", modelId, thinking);
        LOG.info("branch forked with first question: parent={} child={} operation={}", sourceBranch.id,
                child.id, turn.operation().id);
        return new Forked(child, topic, turn.userMessage(), turn.aiMessage(), turn.operation());
    }

    /** 分支 + 出生快照 + 指针，一个事务，或全不写。 */
    private void createBranchAtomically(Branch child, ContextSnapshot snapshot) {
        jdbc.runInTransaction(connection -> {
            branches.insertChild(connection, child);
            snapshots.insert(connection, snapshot);
            branches.attachContextSnapshot(connection, child.id, snapshot.id);
            return null;
        });
    }

    /**
     * 出生快照的来源：本分支到分叉点为止的每条 USER 消息与显示版本，按顺序排列（对照 Android
     * {@code ChatWiring.sourcesThrough}）。来源位置固定使用请求携带的
     * {@code sourceRevisionId}，因此重放或父级 reset 都不会改变子分支的出生上下文。
     */
    private List<ContextSnapshot.Source> sourcesThrough(String branchId, String sourceMessageId,
            String sourceRevisionId) {
        List<ContextSnapshot.Source> sources = new ArrayList<>();
        for (MessageStore.Position position : messages.listForBranch(branchId)) {
            if (position.message.isUser()) {
                sources.add(ContextSnapshot.Source.userMessage(position.message.id));
            } else if (position.message.id.equals(sourceMessageId)) {
                sources.add(ContextSnapshot.Source.stableRevision(sourceRevisionId));
            } else if (position.hasActiveRevision()) {
                sources.add(ContextSnapshot.Source.stableRevision(position.message.activeRevisionId()));
            }
            if (position.message.id.equals(sourceMessageId)) {
                break;
            }
        }
        return sources;
    }

    /** 重放带首问的分叉：operation 钉住的子分支与其两个位置即首次结果，不重启生成、不二次创建。 */
    private Forked replayedFork(OperationStore.Operation replay) {
        Branch child = branches.get(replay.branchId).orElseThrow(() -> notFound("branch"));
        Topic topic = topics.get(child.topicId).orElseThrow(() -> notFound("topic"));
        Message ai = messages.get(replay.messageId).orElseThrow(() -> notFound("message"));
        Message user = precedingQuestion(ai);
        return new Forked(child, topic, user, ai, replay);
    }

    private Message precedingQuestion(Message ai) {
        for (MessageStore.Position position : messages.listForBranch(ai.branchId)) {
            if (position.message.ordinal == ai.ordinal - 1 && position.message.isUser()) {
                return position.message;
            }
        }
        throw new ServiceRefusalException("OPERATION_NOT_COMPLETABLE", Map.of());
    }

    private static ServiceRefusalException refusal(String code) {
        return new ServiceRefusalException(code, Map.of());
    }

    private static ServiceRefusalException notFound(String resource) {
        return new ServiceRefusalException("NOT_FOUND", Map.of("resource", resource));
    }

    /** 客户端 {@code commandId}：必填且规范 UUID（契约 §4）；非规范形态属 400 可修复错误。 */
    private static void requireCommand(String commandId) {
        requireUuid(commandId, "commandId");
    }

    private static void requireUuid(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new ServiceRefusalException("REQUEST_INVALID", Map.of("httpStatus", 400));
        }
        try {
            Guard.id(value, field);
        } catch (IllegalArgumentException malformed) {
            throw new ServiceRefusalException("REQUEST_INVALID", Map.of("httpStatus", 400));
        }
    }
}
