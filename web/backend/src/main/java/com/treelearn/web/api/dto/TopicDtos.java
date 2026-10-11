package com.treelearn.web.api.dto;

import java.util.List;

/**
 * 主题域 DTO（契约 {@code web/contracts/api.md} §5.1、§5.1b、§5.15、§6.2、§6.3）。
 *
 * <p>字段名、可空性与单位逐字对齐契约与 fixtures；{@code *Millis} 为 UTC epoch 毫秒的 JSON number。
 */
public final class TopicDtos {

    private TopicDtos() {}

    /** §5.1 Topic；主题不携带自身名称，标题来自根分支。 */
    public record Topic(
            String id,
            String lastVisitedBranchId,
            long createdAtMillis,
            long updatedAtMillis) {}

    /** §5.1b TopicSummary；主页概览行 = 主题 + 派生显示名。 */
    public record TopicSummary(
            String topicId,
            String rootTitle,
            String rootQuestion,
            String displayTitle,
            long lastUsedAtMillis) {}

    /** §6.2 POST /api/topics 响应：首问原子接受的一组对象。 */
    public record TopicBootstrap(
            Topic topic,
            BranchDtos.Branch rootBranch,
            MessageDtos.Message userMessage,
            MessageDtos.Message aiMessage,
            OperationDtos.Operation operation) {}

    /** §6.3 POST /api/branches/{id}/messages 响应。 */
    public record TurnAccepted(
            MessageDtos.Message userMessage,
            MessageDtos.Message aiMessage,
            OperationDtos.Operation operation) {}

    /** §6.4 POST /api/messages/{id}/retry 与 /reset 响应：{message, operation}。 */
    public record MessageOperation(
            MessageDtos.Message message,
            OperationDtos.Operation operation) {}

    /** §6.3 GET /api/branches/{id}/messages 响应。 */
    public record Conversation(
            String branchId,
            BranchDtos.ReadingAnchor readingAnchor,
            List<MessageDtos.Message> messages,
            List<String> recommendations) {}

    /** §5.15 DeletionPreview；整树或子树删除预览（含 digest 指纹）。 */
    public record DeletionPreview(
            String topicId,
            String targetBranchId,
            boolean wholeTopic,
            List<String> branchIds,
            int branchCount,
            String digest) {}

    /** §6.2 POST /api/topics/{id}/delete 响应；重放（主题已不存在）为空数组。 */
    public record TopicDeleted(
            String deletedTopicId,
            List<String> deletedBranchIds) {}
}
