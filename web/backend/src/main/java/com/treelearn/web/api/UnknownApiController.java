package com.treelearn.web.api;

import com.treelearn.web.api.dto.ErrorDtos;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 未命中任何已声明端点的 {@code /api/**} 请求的唯一出口（任务 T-2026-10-10-029；契约 §3.1/§10）。
 *
 * <p>它保证接口命名空间与静态托管的 SPA 回退彻底隔离：任何未知接口路径都得到 404 +
 * {@code {error:{code,params,retryable},requestId}} 统一封装，而不会拿到前端 {@code index.html}。
 * 已声明的端点（含 SSE）路径更具体，优先级高于本通配映射，行为不变。
 */
@RestController
class UnknownApiController {

    @RequestMapping("/api/**")
    ResponseEntity<ErrorDtos.ApiErrorResponse> unknownEndpoint() {
        return ErrorResponses.of("NOT_FOUND", Map.of("resource", "endpoint"));
    }
}
