package com.treelearn.web.api;

import com.treelearn.web.api.dto.ConfigDtos;
import com.treelearn.web.generation.GenerationPlanner;
import com.treelearn.web.generation.GenerationPlanner.Assignment;
import com.treelearn.web.generation.GenerationPlanner.Role;
import com.treelearn.web.storage.DesktopPreferences;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 设置读端点（任务 T-2026-10-10-034，契约 {@code web/contracts/api.md} §5.12/§6.6）。
 *
 * <p>{@code GET /api/settings/model-resolution} 恒返回 {@code 200 ModelResolution}：每个角色给出最终
 * 解析结果，回落类别只以 {@code fallback} token 表达。无配置时是 {@code EMPTY}（前端据此禁用发送并给出
 * 引导），绝不是 404——此前主页请求该路径落到未知端点封装，引导链路因此不闭环。
 *
 * <p><strong>回落判定不在本类。</strong> 三个角色的结果全部来自
 * {@link GenerationPlanner#resolveAll}，与生成路径共用同一条 Android 对齐的回落链
 * （{@code NONE}/{@code BUILTIN}/{@code EARLIEST}/{@code EMPTY}）；控制器只做域结果到 §5.12 DTO 的投影。
 * 每个角色的已保存分工经 {@link ModelRoleSettings} 读取（与 {@code GET/PUT /api/settings/model-roles}
 * 同一份事实，任务 T-2026-10-10-041），因此「设置页保存的分工」与「回读的解析结果」一致。
 *
 * <p>{@code GET/PUT /api/settings/model-roles}（§5.12/§6.6）由 {@link ProviderSettingsController}
 * （任务 T-2026-10-10-036）提供：分工、{@code budget}、{@code suggestions} 持久化在数据目录的非敏感偏好文件
 * （对照 Android {@code SettingsRepository}，SharedPreferences 非表），未新增永久表、未改 schema。
 */
@RestController
class SettingsApiController {

    private final GenerationPlanner planner;
    private final ModelRoleSettings roleSettings;

    SettingsApiController(GenerationPlanner planner, DesktopPreferences preferences) {
        this.planner = planner;
        this.roleSettings = new ModelRoleSettings(preferences);
    }

    @GetMapping("/api/settings/model-resolution")
    ConfigDtos.ModelResolution modelResolution() {
        Map<Role, Assignment> resolved = planner.resolveAll(roleSettings.choice(Role.CHAT),
                roleSettings.choice(Role.FAST), roleSettings.choice(Role.TRANSLATION));
        Map<String, ConfigDtos.ModelRole> roles = new LinkedHashMap<>();
        for (Map.Entry<Role, Assignment> entry : resolved.entrySet()) {
            Assignment assignment = entry.getValue();
            roles.put(entry.getKey().name(), new ConfigDtos.ModelRole(assignment.providerId,
                    assignment.modelId, assignment.fallback.name()));
        }
        return new ConfigDtos.ModelResolution(roles);
    }
}
