package com.treelearn.web.api.dto;

import tools.jackson.databind.JsonNode;

/**
 * 生成操作与派生结果域 DTO（契约 {@code web/contracts/api.md} §5.6、§5.10、§6.4、§6.5）。
 *
 * <p>{@code state} 枚举（{@code GenerationState}）；终态不再改变。{@code errorCode} 仅在 FAILED 时取自上游类
 * 错误码词汇表，否则为 null。{@code payload} 为按 kind 变化的自由对象，此处以 JSON 树透传。
 */
public final class OperationDtos {

    private OperationDtos() {}

    /** §5.6 Operation。 */
    public record Operation(
            String id,
            String commandId,
            String branchId,
            String messageId,
            String promptPurpose,
            String promptPolicyVersion,
            String model,
            boolean thinkingEnabled,
            String state,
            String partialReasoning,
            String partialAnswer,
            long createdAtMillis,
            String errorCode,
            String sseUrl) {}

    /** §5.10 DerivedArtifact；stale=true 表示与实时基线不符，前端作“无结果”处理。 */
    public record DerivedArtifact(
            String kind,
            String purpose,
            String scopeId,
            String basedOnRevisionId,
            String inputDigest,
            JsonNode payload,
            long createdAtMillis,
            boolean stale) {}
}
