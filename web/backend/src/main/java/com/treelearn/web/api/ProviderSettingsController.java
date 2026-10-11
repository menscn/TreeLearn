package com.treelearn.web.api;

import com.treelearn.core.domain.Guard;
import com.treelearn.web.api.dto.ConfigDtos;
import com.treelearn.web.api.dto.RequestDtos;
import com.treelearn.web.credential.SecretAccessException;
import com.treelearn.web.credential.SecretPort;
import com.treelearn.web.generation.GenerationPlanner;
import com.treelearn.web.generation.GenerationPlanner.Assignment;
import com.treelearn.web.generation.GenerationPlanner.Budget;
import com.treelearn.web.generation.GenerationPlanner.Fallback;
import com.treelearn.web.generation.GenerationPlanner.Role;
import com.treelearn.web.generation.ProviderModelCatalog;
import com.treelearn.web.generation.ServiceRefusalException;
import com.treelearn.web.storage.DesktopPreferences;
import com.treelearn.web.storage.ModelStore;
import com.treelearn.web.storage.ProviderStore;
import java.time.Clock;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Provider/Key 与模型分工端点（任务 T-2026-10-10-036，契约 {@code web/contracts/api.md} §6.6 / §5.9 / §5.12）。
 *
 * <p>Provider CRUD 走 {@link ProviderStore}，Key 只写不读（只经 {@link SecretPort} 落 Windows 凭据管理器，
 * target {@code Treelearn-Web/provider/<id>}，任何响应只回 {@code hasKey}），模型列表读端点
 * （{@code GET /api/providers/{id}/models}）直接走 {@link ModelStore#listForProvider}，模型刷新经
 * {@link ProviderModelCatalog} 复用共享网关后交 {@link ModelStore#upsertMany}（只增不删、精确去重）。
 *
 * <p>模型分工（{@code GET/PUT /api/settings/model-roles}）持久化在数据目录的非敏感偏好文件
 * （{@link DesktopPreferences}），对照 Android {@code SettingsRepository}（SharedPreferences 非表），桌面十二张
 * 准入表无分工表，故不新增永久表；回落判定复用 {@link GenerationPlanner#resolveAll}，与生成路径同源。
 *
 * <p>错误经 {@link CoreApiExceptionHandler} 统一成 §3.1 封装；错误码只取 §3.3。
 */
@RestController
class ProviderSettingsController {

    private static final Logger LOG = LoggerFactory.getLogger(ProviderSettingsController.class);

    private final ProviderStore providers;
    private final ModelStore models;
    private final SecretPort secrets;
    private final GenerationPlanner planner;
    private final ProviderModelCatalog catalog;
    private final ModelRoleSettings roleSettings;
    private final Clock clock;

    ProviderSettingsController(ProviderStore providers, ModelStore models, SecretPort secrets,
            GenerationPlanner planner, ProviderModelCatalog catalog, DesktopPreferences preferences,
            Clock clock) {
        this.providers = providers;
        this.models = models;
        this.secrets = secrets;
        this.planner = planner;
        this.catalog = catalog;
        this.roleSettings = new ModelRoleSettings(preferences);
        this.clock = clock;
    }

    // ------------------------------------------------------------------ Provider CRUD

    @GetMapping("/api/providers")
    List<ConfigDtos.Provider> listProviders() {
        return providers.list().stream().map(this::providerDto).toList();
    }

    @PostMapping("/api/providers")
    ConfigDtos.Provider createProvider(@RequestBody RequestDtos.ProviderCreateRequest request) {
        requireCommand(request.commandId());
        String name = requiredName(request.name());
        String baseUrl = requiredBaseUrl(request.baseUrl());
        if (providers.findByName(name).isPresent()) {
            throw new ServiceRefusalException("PROVIDER_NAME_TAKEN", Map.of());
        }
        long now = clock.millis();
        String id = UUID.randomUUID().toString();
        providers.insertNew(id, name, baseUrl, true, now);
        if (request.apiKey() != null && !request.apiKey().isEmpty()) {
            writeKey(id, request.apiKey());
        }
        return providerDto(providers.get(id).orElseThrow(() -> notFound("provider")));
    }

    @PutMapping("/api/providers/{id}")
    ConfigDtos.Provider updateProvider(@PathVariable String id,
            @RequestBody RequestDtos.ProviderUpdateRequest request) {
        requireCommand(request.commandId());
        ProviderStore.Provider current = providers.get(id).orElseThrow(() -> notFound("provider"));
        String name = request.name() == null ? current.name : requiredName(request.name());
        String baseUrl = request.baseUrl() == null ? current.baseUrl
                : requiredBaseUrl(request.baseUrl());
        boolean enabled = request.enabled() == null ? current.enabled : request.enabled();
        providers.findByName(name).filter(other -> !other.id.equals(id))
                .ifPresent(other -> { throw new ServiceRefusalException("PROVIDER_NAME_TAKEN",
                        Map.of()); });
        providers.update(id, name, baseUrl, enabled, clock.millis());
        return providerDto(providers.get(id).orElseThrow(() -> notFound("provider")));
    }

    @DeleteMapping("/api/providers/{id}")
    ConfigDtos.DeletedProvider deleteProvider(@PathVariable String id,
            @RequestBody RequestDtos.CommandRequest request) {
        requireCommand(request.commandId());
        providers.get(id).orElseThrow(() -> notFound("provider"));
        providers.delete(id);
        // 镜像 Android SqliteProviderAdminStore.delete：行删除后再清凭据别名（凭据删除失败不回滚已删的行）。
        try {
            secrets.delete(SecretPort.providerTarget(id));
        } catch (SecretAccessException refused) {
            LOG.warn("provider row deleted but its credential alias cleanup failed for target {}",
                    refused.target());
        }
        return new ConfigDtos.DeletedProvider(id);
    }

    // ------------------------------------------------------------------ Key

    @PutMapping("/api/providers/{id}/key")
    ConfigDtos.Provider saveKey(@PathVariable String id,
            @RequestBody RequestDtos.ProviderKeyRequest request) {
        requireCommand(request.commandId());
        providers.get(id).orElseThrow(() -> notFound("provider"));
        String apiKey = request.apiKey();
        if (apiKey == null || apiKey.isEmpty()) {
            throw new ServiceRefusalException("KEY_REQUIRED", Map.of("scope", "PROVIDER"));
        }
        writeKey(id, apiKey);
        return providerDto(providers.get(id).orElseThrow(() -> notFound("provider")));
    }

    @DeleteMapping("/api/providers/{id}/key")
    ConfigDtos.Provider deleteKey(@PathVariable String id,
            @RequestBody RequestDtos.CommandRequest request) {
        requireCommand(request.commandId());
        providers.get(id).orElseThrow(() -> notFound("provider"));
        try {
            secrets.delete(SecretPort.providerTarget(id));
        } catch (SecretAccessException refused) {
            throw new ServiceRefusalException("KEY_SAVE_FAILED", Map.of("scope", "PROVIDER"));
        }
        return providerDto(providers.get(id).orElseThrow(() -> notFound("provider")));
    }

    // ------------------------------------------------------------------ 模型列表 / 刷新

    @GetMapping("/api/providers/{id}/models")
    List<ConfigDtos.Model> listModels(@PathVariable String id) {
        ProviderStore.Provider provider = providers.get(id).orElseThrow(() -> notFound("provider"));
        return modelDtos(provider);
    }

    @PostMapping("/api/providers/{id}/models")
    ConfigDtos.ModelsRefreshed refreshModels(@PathVariable String id,
            @RequestBody RequestDtos.CommandRequest request) {
        requireCommand(request.commandId());
        ProviderStore.Provider provider = providers.get(id).orElseThrow(() -> notFound("provider"));
        Optional<char[]> stored = secrets.read(SecretPort.providerTarget(id));
        if (stored.isEmpty()) {
            throw new ServiceRefusalException("KEY_MISSING", Map.of());
        }
        char[] key = stored.get();
        List<String> remote;
        try {
            remote = catalog.fetchModels(provider.baseUrl, key);
        } finally {
            Arrays.fill(key, '\0');
        }
        int added = models.upsertMany(id, remote, () -> UUID.randomUUID().toString(), clock.millis());
        return new ConfigDtos.ModelsRefreshed(added, modelDtos(provider));
    }

    // ------------------------------------------------------------------ 模型分工

    @GetMapping("/api/settings/model-roles")
    ConfigDtos.ModelRoles modelRoles() {
        return modelRolesSnapshot();
    }

    @PutMapping("/api/settings/model-roles")
    ConfigDtos.ModelRoles updateModelRoles(
            @RequestBody RequestDtos.ModelRolesUpdateRequest request) {
        requireCommand(request.commandId());
        if (request.roles() != null) {
            for (Map.Entry<String, RequestDtos.ModelRoleSelection> entry
                    : request.roles().entrySet()) {
                Role role = requireRole(entry.getKey());
                RequestDtos.ModelRoleSelection selection = entry.getValue();
                roleSettings.save(role, selection == null ? null : selection.providerId(),
                        selection == null ? null : selection.modelId());
            }
        }
        if (request.budget() != null) {
            roleSettings.saveBudget(requireBudget(request.budget()));
        }
        if (request.suggestions() != null) {
            roleSettings.saveSuggestions(request.suggestions());
        }
        return modelRolesSnapshot();
    }

    // ------------------------------------------------------------------ helpers

    private ConfigDtos.Provider providerDto(ProviderStore.Provider provider) {
        return new ConfigDtos.Provider(provider.id, provider.name, provider.baseUrl, provider.enabled,
                secrets.has(SecretPort.providerTarget(provider.id)),
                models.listForProvider(provider.id).size());
    }

    private List<ConfigDtos.Model> modelDtos(ProviderStore.Provider provider) {
        return models.listForProvider(provider.id).stream()
                .map(model -> new ConfigDtos.Model(provider.id, provider.name, model.modelId)).toList();
    }

    private void writeKey(String id, String apiKey) {
        char[] secret = apiKey.toCharArray();
        try {
            secrets.save(SecretPort.providerTarget(id), secret);
        } catch (SecretAccessException refused) {
            throw new ServiceRefusalException("KEY_SAVE_FAILED", Map.of("scope", "PROVIDER"));
        } finally {
            Arrays.fill(secret, '\0');
        }
    }

    private ConfigDtos.ModelRoles modelRolesSnapshot() {
        Map<Role, Assignment> resolved = planner.resolveAll(roleSettings.choice(Role.CHAT),
                roleSettings.choice(Role.FAST), roleSettings.choice(Role.TRANSLATION));
        Map<String, ConfigDtos.ModelRole> roles = new LinkedHashMap<>();
        boolean anyAvailable = false;
        boolean anyFallenBack = false;
        for (Map.Entry<Role, Assignment> entry : resolved.entrySet()) {
            Assignment assignment = entry.getValue();
            roles.put(entry.getKey().name(), new ConfigDtos.ModelRole(assignment.providerId,
                    assignment.modelId, assignment.fallback.name()));
            anyAvailable |= assignment.fallback != Fallback.EMPTY;
            anyFallenBack |= assignment.fallback == Fallback.BUILTIN
                    || assignment.fallback == Fallback.EARLIEST;
        }
        String notice = !anyAvailable ? "NO_MODELS" : (anyFallenBack ? "FALLEN_BACK" : "NONE");
        return new ConfigDtos.ModelRoles(roles, roleSettings.budget().name(),
                roleSettings.suggestions(), notice);
    }

    private static Role requireRole(String raw) {
        for (Role role : Role.values()) {
            if (role.name().equals(raw)) {
                return role;
            }
        }
        throw requestInvalid();
    }

    private static Budget requireBudget(String raw) {
        for (Budget budget : Budget.values()) {
            if (budget.name().equals(raw)) {
                return budget;
            }
        }
        throw requestInvalid();
    }

    private static String requiredName(String raw) {
        String name = raw == null ? "" : raw.trim();
        if (name.isEmpty()) {
            throw new ServiceRefusalException("PROVIDER_INVALID", Map.of("field", "name"));
        }
        return name;
    }

    private static String requiredBaseUrl(String raw) {
        String baseUrl = raw == null ? "" : raw.trim();
        if (baseUrl.isEmpty() || !baseUrl.startsWith("https://")) {
            throw new ServiceRefusalException("PROVIDER_INVALID", Map.of("field", "baseUrl"));
        }
        return baseUrl;
    }

    private static void requireCommand(String commandId) {
        if (commandId == null || commandId.trim().isEmpty()) {
            throw requestInvalid();
        }
        try {
            Guard.id(commandId, "commandId");
        } catch (IllegalArgumentException malformed) {
            throw requestInvalid();
        }
    }

    private static ServiceRefusalException notFound(String resource) {
        return new ServiceRefusalException("NOT_FOUND", Map.of("resource", resource));
    }

    private static ServiceRefusalException requestInvalid() {
        return new ServiceRefusalException("REQUEST_INVALID", Map.of("httpStatus", 400));
    }
}
