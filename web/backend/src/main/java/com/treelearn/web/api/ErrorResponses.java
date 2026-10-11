package com.treelearn.web.api;

import com.treelearn.web.api.dto.ErrorDtos;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;

/**
 * 构造 §3.1 错误封装响应：{@code {error:{code,params,retryable},requestId}}，状态与 retryable 由
 * {@link ErrorCatalog} 决定。{@code requestId} 为传输标识，绝不当 {@code commandId}（契约 §3.1/§4）。
 */
final class ErrorResponses {

    private ErrorResponses() {}

    static ResponseEntity<ErrorDtos.ApiErrorResponse> of(String code, Map<String, Object> params) {
        ErrorDtos.ApiError error = new ErrorDtos.ApiError(code, params == null ? Map.of() : params,
                ErrorCatalog.retryable(code));
        return ResponseEntity.status(ErrorCatalog.status(code))
                .body(new ErrorDtos.ApiErrorResponse(error, UUID.randomUUID().toString()));
    }
}
