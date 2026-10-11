package com.treelearn.web.generation;

import java.util.Map;

/**
 * 服务层业务拒绝（任务 T-2026-10-10-028）：携带契约 §3.3 的错误码与安全插值参数，由 API 边界统一
 * 翻译为 §3.1 错误封装。错误码取自词汇表，绝不新造。
 */
public final class ServiceRefusalException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public final String code;
    public final Map<String, Object> params;

    public ServiceRefusalException(String code, Map<String, Object> params) {
        super(code);
        this.code = code;
        this.params = params == null ? Map.of() : Map.copyOf(params);
    }

    public static ServiceRefusalException of(String code) {
        return new ServiceRefusalException(code, Map.of());
    }
}
