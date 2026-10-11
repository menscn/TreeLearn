package com.treelearn.web.api;

import com.treelearn.core.domain.AnswerRevision;
import com.treelearn.core.domain.Branch;
import com.treelearn.core.domain.BranchAnchor;
import com.treelearn.core.domain.BranchSource;
import com.treelearn.core.domain.Message;
import com.treelearn.core.domain.ReadingAnchor;
import com.treelearn.core.domain.Topic;
import com.treelearn.web.api.dto.BranchDtos;
import com.treelearn.web.api.dto.MessageDtos;
import com.treelearn.web.api.dto.OperationDtos;
import com.treelearn.web.api.dto.TopicDtos;
import com.treelearn.web.storage.OperationStore;
import com.treelearn.web.storage.TopicStore;
import java.util.List;

/**
 * 域模型/存储行 → 契约 §5 DTO 的集中映射（任务 T-2026-10-10-028）：单位（{@code *Millis} 为 UTC 毫秒）
 * 与可空性在此收口，控制器保持薄。
 */
final class ApiDtoMapper {

    private ApiDtoMapper() {}

    static TopicDtos.Topic topic(Topic topic) {
        return new TopicDtos.Topic(topic.id, topic.lastVisitedBranchId, topic.createdAt, topic.updatedAt);
    }

    static TopicDtos.TopicSummary topicSummary(TopicStore.Summary summary) {
        return new TopicDtos.TopicSummary(summary.topicId, summary.rootTitle, summary.rootQuestion,
                summary.displayTitle(), summary.lastUsedAt);
    }

    static BranchDtos.Branch branch(Branch branch) {
        return new BranchDtos.Branch(branch.id, branch.topicId, branch.parentBranchId,
                branchSource(branch.source), branch.title(), branch.createdAt, branch.updatedAt(),
                (int) branch.latestStableSequence(), (int) branch.lastSeenStableSequence(),
                (int) branch.unreadCount(), branch.isCollapsed(), branch.archivedAt());
    }

    static BranchDtos.BranchSource branchSource(BranchSource source) {
        if (source == null) {
            return null;
        }
        return new BranchDtos.BranchSource(source.parentBranchId, source.sourceMessageId,
                source.sourceRevisionId, anchor(source.anchor), source.contextSnapshotId);
    }

    static BranchDtos.BranchAnchor anchor(BranchAnchor anchor) {
        if (anchor == null) {
            return null;
        }
        return new BranchDtos.BranchAnchor(anchor.startInclusive, anchor.endExclusive, anchor.quote,
                anchor.hash);
    }

    static BranchDtos.ReadingAnchor readingAnchor(ReadingAnchor anchor) {
        if (anchor == null) {
            return null;
        }
        return new BranchDtos.ReadingAnchor(anchor.messageId, anchor.revisionId, anchor.listItemOffset,
                anchor.viewportOffset);
    }

    static OperationDtos.Operation operation(OperationStore.Operation operation) {
        return new OperationDtos.Operation(operation.id, operation.commandId, operation.branchId,
                operation.messageId, operation.purpose.name(), operation.promptPolicyVersion,
                operation.model.wireId, operation.thinkingEnabled, operation.state.name(),
                operation.partialReasoning, operation.partialAnswer, operation.createdAt,
                operation.errorCode, "/api/operations/" + operation.id + "/events");
    }

    static MessageDtos.AnswerRevision revision(AnswerRevision revision) {
        return new MessageDtos.AnswerRevision(revision.id, revision.messageId, revision.revisionNo,
                revision.reasoningContent, revision.answerContent, revision.model.wireId,
                revision.thinkingEnabled, revision.promptPolicyVersion, revision.createdAt);
    }

    /** §5.4：{@code versions} 为该位置全部稳定版本的完整对象列表；附件未实现，恒为空数组。 */
    static MessageDtos.Message message(Message message, List<AnswerRevision> versions) {
        return new MessageDtos.Message(message.id, message.branchId, message.role.name(),
                message.ordinal, message.userContent, message.quotedText(), message.createdAt,
                message.activeRevisionId(), message.activeOperationId(), List.of(),
                versions.stream().map(ApiDtoMapper::revision).toList());
    }

    static MessageDtos.Message message(Message message) {
        return message(message, List.of());
    }
}
