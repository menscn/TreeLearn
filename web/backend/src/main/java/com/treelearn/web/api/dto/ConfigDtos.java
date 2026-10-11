package com.treelearn.web.api.dto;

import java.util.List;
import java.util.Map;

/**
 * 配置域 DTO（契约 {@code web/contracts/api.md} §5.9、§5.11、§5.12、§6.6）。
 *
 * <p>Key 只写不读：{@link Provider} 只回 {@code hasKey}，绝不回明文。{@code roles} 键固定为
 * {@code CHAT}/{@code FAST}/{@code TRANSLATION}。
 */
public final class ConfigDtos {

    private ConfigDtos() {}

    /** §5.9 Provider。 */
    public record Provider(
            String id,
            String name,
            String baseUrl,
            boolean enabled,
            boolean hasKey,
            int modelCount) {}

    /** §5.9 Model。 */
    public record Model(
            String providerId,
            String providerName,
            String modelId) {}

    /** §5.11 WebSearchSettings。 */
    public record WebSearchSettings(
            boolean searchEnabled,
            String engine,
            String tier,
            List<WebSearchService> services,
            String notice) {}

    /** §5.11 services 单项（引擎 + 是否有 Key）。 */
    public record WebSearchService(
            String engine,
            boolean hasKey) {}

    /** §5.12 GET /api/settings/model-roles 响应。 */
    public record ModelRoles(
            Map<String, ModelRole> roles,
            String budget,
            boolean suggestions,
            String notice) {}

    /** §5.12 单个角色解析项：最终模型 + 回落 token（NONE/BUILTIN/EARLIEST/EMPTY）。 */
    public record ModelRole(
            String providerId,
            String modelId,
            String fallback) {}

    /** §5.12 GET /api/settings/model-resolution 响应；只含 roles，每项字段同 ModelRoles.roles。 */
    public record ModelResolution(
            Map<String, ModelRole> roles) {}

    /** §6.6 DELETE /api/providers/{id} 响应。 */
    public record DeletedProvider(
            String deletedProviderId) {}

    /** §6.6 POST /api/providers/{id}/models 响应：本次新增数与刷新后的完整模型列表。 */
    public record ModelsRefreshed(
            int added,
            List<Model> models) {}
}
