package com.treelearn.web.api.dto;

import java.util.List;

/**
 * 消息与版本域 DTO（契约 {@code web/contracts/api.md} §5.4、§5.5、§5.8、§6.10）。
 *
 * <p>消息是位置而非版本容器：USER 的 {@code userContent} 不可变、{@code quotedText} 无引用时为 {@code ""}
 * 且永不为 null；AI 的 {@code userContent} 为 null 且 {@code quotedText} 恒为 {@code ""}。
 * {@code versions} 为该位置全部稳定版本的完整对象列表（最旧在前）。
 */
public final class MessageDtos {

    private MessageDtos() {}

    /** §5.4 Message。 */
    public record Message(
            String id,
            String branchId,
            String role,
            int ordinal,
            String userContent,
            String quotedText,
            long createdAtMillis,
            String activeRevisionId,
            String activeOperationId,
            List<MessageAttachmentRef> attachments,
            List<AnswerRevision> versions) {}

    /** §5.4 attachments 引用：记录已被删除时 present=false 且 displayName=null。 */
    public record MessageAttachmentRef(
            String attachmentId,
            String displayName,
            boolean present) {}

    /** §5.5 AnswerRevision；版本一旦产生即冻结，revisionNo 从 1 单调递增。 */
    public record AnswerRevision(
            String id,
            String messageId,
            int revisionNo,
            String reasoningContent,
            String answerContent,
            String model,
            boolean thinkingEnabled,
            String promptPolicyVersion,
            long createdAtMillis) {}

    /** §5.8 Annotation；start inclusive、end exclusive，空容器/不适用索引用 -1 哨兵。 */
    public record Annotation(
            String id,
            String container,
            int proseIndex,
            int tableIndex,
            int rowIndex,
            int colIndex,
            int start,
            int end,
            String kind,
            String body) {}

    /** §6.10 GET/POST /api/messages/{id}/annotations 响应。 */
    public record AnnotationListResponse(
            String messageId,
            List<Annotation> annotations) {}
}
