package com.treelearn.web.api.dto;

import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * 本模块 API 层共用的严格 JSON 映射（任务 T-2026-10-10-025）。
 *
 * <p>开启 {@code FAIL_ON_UNKNOWN_PROPERTIES}：契约漂移（多出的字段）必须让反序列化失败，而不是被静默忽略。
 * 由 fixtures 往返测试与 {@code StrictnessTest} 固定该行为。
 *
 * <p>该映射刻意不注册为 Spring bean：Spring 默认的 Jackson 自动配置保持不变，其它端点（当前仅
 * {@code /api/health/ready}）不受影响。空值的显式序列化保持默认（fixtures 依赖显式 null 语义）。
 */
public final class ApiJson {

    private static final JsonMapper MAPPER = JsonMapper.builder()
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    private ApiJson() {}

    /** 严格 JSON 映射，供 DTO 层与端点编解码复用。 */
    public static JsonMapper mapper() {
        return MAPPER;
    }
}
