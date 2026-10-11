package com.treelearn.web.hosting;

import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 发行口根路径 {@code /} 的入口（任务 T-2026-10-10-029；契约 §10）。
 *
 * <p>Spring 的 {@code ResourceHttpRequestHandler} 会把 {@code /} 规范化成空路径并直接判定“无资源”，
 * 因此仅靠 {@code /**} 资源处理器无法服务根路径。这里直接以 {@code text/html} 返回与静态托管同一个
 * 回退目标（dist 的 {@code index.html}，产物缺失时为提示页）；其它前端路由（非 {@code /}）由资源
 * 处理器的 SPA 回退处理，浏览器地址保持不变。
 */
@Controller
class SpaRootController {

    private final FrontendDist dist;

    SpaRootController(FrontendDist dist) {
        this.dist = dist;
    }

    @GetMapping("/")
    ResponseEntity<Resource> root() {
        return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body(dist.index());
    }
}
