package com.treelearn.web.api;

import com.treelearn.web.generation.GenerationPlanner.Budget;
import com.treelearn.web.generation.GenerationPlanner.Choice;
import com.treelearn.web.generation.GenerationPlanner.Role;
import com.treelearn.web.storage.DesktopPreferences;
import java.util.Objects;

/**
 * 模型分工偏好的唯一读写入口（任务 T-2026-10-10-041）。
 *
 * <p>分工、思考预算与建议开关保存在数据目录的非敏感偏好文件（{@link DesktopPreferences}），键名与分隔符
 * 对照 Android {@code com.treelearn.data.settings.SettingsRepository} 与
 * {@code com.treelearn.app.wiring.SqliteModelSettingsStore}：{@code role_chat_model}/{@code role_fast_model}/
 * {@code role_translation_model}（值形如 {@code providerId\u001FmodelId}）、{@code thinking_budget}、
 * {@code answer_suggestions}。
 *
 * <p>{@code GET /api/settings/model-resolution} 与 {@code GET/PUT /api/settings/model-roles} 共用本类，
 * 保证「设置页保存的分工」与「回读的解析结果」读的是同一份事实，消除读取口径分叉。回落判定不在这里：
 * 两个端点都把 {@link Choice} 交给 {@code GenerationPlanner#resolveAll} 按同一条链解析，不另立规则。
 */
final class ModelRoleSettings {

    /** 角色偏好值内的分隔符，与 Android {@code SqliteModelSettingsStore.SEPARATOR} 一致。 */
    private static final String SEPARATOR = "\u001f";
    private static final String KEY_ROLE_CHAT = "role_chat_model";
    private static final String KEY_ROLE_FAST = "role_fast_model";
    private static final String KEY_ROLE_TRANSLATION = "role_translation_model";
    private static final String KEY_BUDGET = "thinking_budget";
    private static final String KEY_SUGGESTIONS = "answer_suggestions";

    private final DesktopPreferences preferences;

    ModelRoleSettings(DesktopPreferences preferences) {
        this.preferences = Objects.requireNonNull(preferences, "preferences");
    }

    /** 某个角色已保存的选择；从未保存（或只有一半）时为 {@code (null, null)}，即未配置。 */
    Choice choice(Role role) {
        String stored = preferences.get(key(role)).orElse("");
        int split = stored.indexOf(SEPARATOR);
        if (split <= 0) {
            return new Choice(null, null);
        }
        return new Choice(stored.substring(0, split), stored.substring(split + SEPARATOR.length()));
    }

    void save(Role role, String providerId, String modelId) {
        preferences.put(key(role),
                providerId == null || modelId == null ? "" : providerId + SEPARATOR + modelId);
    }

    Budget budget() {
        String stored = preferences.get(KEY_BUDGET).orElse(Budget.AUTO.name());
        for (Budget budget : Budget.values()) {
            if (budget.name().equals(stored)) {
                return budget;
            }
        }
        return Budget.AUTO;
    }

    void saveBudget(Budget budget) {
        preferences.put(KEY_BUDGET, budget.name());
    }

    boolean suggestions() {
        return preferences.getBoolean(KEY_SUGGESTIONS, true);
    }

    void saveSuggestions(boolean suggestions) {
        preferences.putBoolean(KEY_SUGGESTIONS, suggestions);
    }

    private static String key(Role role) {
        switch (role) {
            case CHAT:
                return KEY_ROLE_CHAT;
            case FAST:
                return KEY_ROLE_FAST;
            default:
                return KEY_ROLE_TRANSLATION;
        }
    }
}
