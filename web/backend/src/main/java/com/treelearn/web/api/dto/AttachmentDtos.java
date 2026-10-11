package com.treelearn.web.api.dto;

import java.util.List;

/**
 * 附件域 DTO（契约 {@code web/contracts/api.md} §5.7、§6.9）。
 *
 * <p>{@code parseState} ∈ {@code pending}/{@code parsed}/{@code parse_failed}；{@code present} = 物理文件存在；
 * {@code referenceCount} = 引用该记录的消息数。
 */
public final class AttachmentDtos {

    private AttachmentDtos() {}

    /** §5.7 Attachment。 */
    public record Attachment(
            String attachmentId,
            String displayName,
            String mimeType,
            long sizeBytes,
            String parseState,
            boolean present,
            int referenceCount) {}

    /** §6.9 GET /api/attachments 响应（列表 + 已用字节）。 */
    public record AttachmentListResponse(
            List<Attachment> attachments,
            long usageBytes) {}
}
