package com.treelearn.web.api.dto;

import java.util.Map;

/**
 * 变更请求体 DTO（契约 {@code web/contracts/api.md} §6）。每个产生持久副作用的请求体都带必填
 * {@code commandId}（客户端生成、全生命周期唯一的 UUID 字符串）。只读 GET 无请求体，不在此列。
 *
 * <p>可选字段以包装类型承载，允许 JSON null 或缺省（注：fixtures 对可空字段通常显式写 null）。
 */
public final class RequestDtos {

    private RequestDtos() {}

    /** §6 仅需 commandId 的变更请求（archive/restore/cancel/select/cleanup/models 刷新/删除批注等）。 */
    public record CommandRequest(
            String commandId) {}

    /** §6.2 POST /api/topics。 */
    public record TopicCreateRequest(
            String commandId,
            String question,
            String model,
            boolean thinkingEnabled,
            String quotedText,
            String attachmentId) {}

    /** §6.3 POST /api/branches/{id}/messages。 */
    public record BranchMessageCreateRequest(
            String commandId,
            String text,
            String quote,
            String attachmentId,
            String model,
            boolean thinkingEnabled) {}

    /** §6.3 POST /api/branches/{id}/forks；anchor 为 null 表示整条分叉。 */
    public record BranchForkRequest(
            String commandId,
            String sourceMessageId,
            String sourceRevisionId,
            BranchDtos.BranchAnchor anchor,
            String question,
            String model,
            Boolean thinkingEnabled) {}

    /** §6.3 POST /api/branches/{id}/collapse。 */
    public record BranchCollapseRequest(
            String commandId,
            boolean collapsed) {}

    /** §6.3 POST /api/branches/{id}/seen。 */
    public record BranchSeenRequest(
            String commandId,
            int seenThroughSequence) {}

    /** §6.3 PUT /api/branches/{id}/reading-position；revisionId 为 null 表示跟随当前位置。 */
    public record ReadingPositionUpdateRequest(
            String commandId,
            String messageId,
            String revisionId,
            int listItemOffset,
            int viewportOffset) {}

    /** §6.2 / §6.3 删除确认；digest 过期由服务端拒绝。 */
    public record DeletionConfirmRequest(
            String commandId,
            String digest) {}

    /** §6.4 POST /api/messages/{id}/retry 与 /reset。 */
    public record MessageRegenerateRequest(
            String commandId,
            String model,
            boolean thinkingEnabled) {}

    /** §6.5 POST /api/derived；kind 为请求侧大写枚举。 */
    public record DerivedStartRequest(
            String commandId,
            String kind,
            String scopeId,
            String basedOnRevisionId,
            String model) {}

    /** §6.6 POST /api/providers。 */
    public record ProviderCreateRequest(
            String commandId,
            String name,
            String baseUrl,
            String apiKey) {}

    /** §6.6 PUT /api/providers/{id}。 */
    public record ProviderUpdateRequest(
            String commandId,
            String name,
            String baseUrl,
            Boolean enabled) {}

    /** §6.6 PUT /api/providers/{id}/key（只写不读）。 */
    public record ProviderKeyRequest(
            String commandId,
            String apiKey) {}

    /** §6.6 PUT /api/settings/web-search/keys/{engine}。 */
    public record WebSearchKeyRequest(
            String commandId,
            String apiKey) {}

    /** §6.6 PUT /api/settings/web-search。 */
    public record WebSearchUpdateRequest(
            String commandId,
            Boolean searchEnabled,
            String engine,
            String tier) {}

    /** §6.6 PUT /api/settings/model-roles。 */
    public record ModelRolesUpdateRequest(
            String commandId,
            Map<String, ModelRoleSelection> roles,
            String budget,
            Boolean suggestions) {}

    /** §6.6 更新请求中的角色选择（无 fallback，回落由服务端解析）。 */
    public record ModelRoleSelection(
            String providerId,
            String modelId) {}

    /** §6.10 POST /api/messages/{id}/annotations。 */
    public record AnnotationCreateRequest(
            String commandId,
            String kind,
            String container,
            Integer proseIndex,
            Integer tableIndex,
            Integer rowIndex,
            Integer colIndex,
            int start,
            int end,
            String body) {}

    /** §6.9 POST /api/attachments（JSON 形式样例）。 */
    public record AttachmentUploadRequest(
            String commandId,
            String displayName,
            String mimeType,
            String contentBase64) {}
}
