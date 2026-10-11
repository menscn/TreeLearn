package com.treelearn.web.api.dto;

import java.util.Map;

/**
 * 错误封装 DTO（契约 {@code web/contracts/api.md} §3.1）。
 *
 * <p>所有非 2xx 响应体形状：{@code {error:{code,params,retryable},requestId}}。{@code code} 用字符串承载，
 * 以便未知错误码也能透传（前端负责 i18n 映射）；{@code params} 仅含安全插值参数，允许为空对象。
 * {@code requestId} 为传输标识，不是业务提交身份（不得当 commandId）。
 */
public final class ErrorDtos {

    private ErrorDtos() {}

    /** §3.1 顶层封装。 */
    public record ApiErrorResponse(
            ApiError error,
            String requestId) {}

    /** §3.1 error 对象。 */
    public record ApiError(
            String code,
            Map<String, Object> params,
            boolean retryable) {}
}
