package com.treelearn.web.hosting;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;

/**
 * 发行口静态托管的解析结果（任务 T-2026-10-10-029；契约 §10）。
 *
 * @param dir dist 目录；未探测/未配置到目录时为 {@code null}
 * @param index SPA 回退目标：dist 的 {@code index.html}，产物缺失时为可读提示页（始终非 null）
 * @param location 资源处理器使用的目录型位置 URL（以 {@code /} 收尾）
 */
record FrontendDist(Path dir, Resource index, String location) {

    private static final Logger LOG = LoggerFactory.getLogger(FrontendDist.class);
    private static final String INDEX_FILE = "index.html";
    private static final String[] PROBE_CANDIDATES = {
        "../frontend/dist", "web/frontend/dist", "frontend/dist"};

    private static final String PLACEHOLDER_HTML =
            "<!doctype html><html lang=\"zh-CN\"><head><meta charset=\"utf-8\">"
            + "<title>TreeLearn</title></head><body>"
            + "<!-- treelearn-frontend-build-missing -->"
            + "<h1>前端构建产物未找到 / Frontend build not found</h1>"
            + "<p>请在 <code>web/frontend</code> 运行 <code>pnpm build</code> 生成 <code>dist/</code>，"
            + "或用 <code>web/scripts/start-web.ps1 -Build</code> 重新启动本机服务。</p>"
            + "<p>The HTTP API under <code>/api/</code> is still available.</p></body></html>";

    /** 显式属性优先；否则探测相对目录；都不可用时目录为 {@code null} 并以提示页兜底。 */
    static FrontendDist resolve(String configured) throws IOException {
        Path dir = resolveDistDir(configured);
        Path indexFile = dir == null ? null : dir.resolve(INDEX_FILE);
        if (indexFile != null && Files.isRegularFile(indexFile)) {
            LOG.info("serving frontend static assets and SPA fallback from {}", dir);
            Path served = dir.toAbsolutePath();
            return new FrontendDist(dir, new FileSystemResource(indexFile), directoryLocation(served));
        }
        LOG.warn("frontend dist not available (property {} resolved to {}); serving a hint page until"
                + " the frontend is built with `pnpm build`", StaticHostingConfiguration.DIST_PROPERTY, dir);
        Path served = Path.of(System.getProperty("java.io.tmpdir"), "treelearn-absent-frontend-dist");
        return new FrontendDist(dir, hintResource(), directoryLocation(served));
    }

    private static Path resolveDistDir(String configured) {
        if (configured != null && !configured.isBlank()) {
            return Path.of(configured.trim());
        }
        for (String candidate : PROBE_CANDIDATES) {
            Path probe = Path.of(candidate);
            if (Files.isDirectory(probe)) {
                return probe;
            }
        }
        return null;
    }

    /**
     * 产物缺失时的回退页：写成临时文件再走同一个文件资源通道，避免内存型 Resource 在内容协商/下载
     * 路径上的 {@code getFile()} 差异；进程退出时清理。
     */
    private static Resource hintResource() throws IOException {
        Path hint = Files.createTempFile("treelearn-frontend-missing", ".html");
        Files.writeString(hint, PLACEHOLDER_HTML, StandardCharsets.UTF_8);
        hint.toFile().deleteOnExit();
        return new FileSystemResource(hint);
    }

    /** 目录型资源位置必须以 {@code /} 收尾，{@code createRelative} 才能拼出正确路径。 */
    private static String directoryLocation(Path dir) {
        String url = dir.toUri().toString();
        return url.endsWith("/") ? url : url + "/";
    }
}
