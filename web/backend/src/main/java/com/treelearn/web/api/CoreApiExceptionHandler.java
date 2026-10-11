package com.treelearn.web.api;

import com.treelearn.web.api.dto.ErrorDtos;
import com.treelearn.web.generation.ServiceRefusalException;
import com.treelearn.web.storage.StorageException;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 核心闭环端点的统一异常边界（任务 T-2026-10-10-028，契约 §3）。
 *
 * <p>业务拒绝按码映射；本地写失败统一为 {@code STORAGE_REFUSED}（不泄漏 SQLite 原文）；请求体不可解析
 * 为 {@code REQUEST_INVALID}；其余未分类失败为 {@code INTERNAL_ERROR}。绝不把原始异常文本/堆栈透给客户端。
 */
@RestControllerAdvice
class CoreApiExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(CoreApiExceptionHandler.class);

    @ExceptionHandler(ServiceRefusalException.class)
    ResponseEntity<ErrorDtos.ApiErrorResponse> refusal(ServiceRefusalException refused) {
        return ErrorResponses.of(refused.code, refused.params);
    }

    /**
     * 静态托管把 {@code /api/**} 的大小写变体（如 {@code /API/foo}）同样短路为“无资源”。这些路径
     * 未命中任何已声明端点（Spring 路由大小写敏感），落到这里给出与 {@code UnknownApiController}
     * 一致的 §3.1 封装（契约 §3.3/§11：未知 API 路径 → {@code NOT_FOUND}，{@code resource=endpoint}）。
     */
    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<ErrorDtos.ApiErrorResponse> resourceNotFound(NoResourceFoundException missing) {
        return ErrorResponses.of("NOT_FOUND", Map.of("resource", "endpoint"));
    }

    @ExceptionHandler(StorageException.class)
    ResponseEntity<ErrorDtos.ApiErrorResponse> storage(StorageException refused) {
        LOG.warn("local write refused: {}", refused.getMessage());
        return ErrorResponses.of("STORAGE_REFUSED", Map.of());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ErrorDtos.ApiErrorResponse> unreadable(HttpMessageNotReadableException refused) {
        return ErrorResponses.of("REQUEST_INVALID", Map.of("httpStatus", 400));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    ResponseEntity<ErrorDtos.ApiErrorResponse> missingParameter(
            MissingServletRequestParameterException refused) {
        return ErrorResponses.of("REQUEST_INVALID", Map.of("httpStatus", 400));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ErrorDtos.ApiErrorResponse> unexpected(Exception failure) {
        LOG.error("unclassified API failure: {}", failure.getClass().getSimpleName());
        return ErrorResponses.of("INTERNAL_ERROR", Map.of());
    }
}
