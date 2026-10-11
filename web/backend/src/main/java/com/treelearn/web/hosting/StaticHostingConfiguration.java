package com.treelearn.web.hosting;

import java.io.IOException;
import java.util.Locale;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

/**
 * 发行口静态托管（任务 T-2026-10-10-029；契约 {@code web/contracts/api.md} §10）。
 *
 * <p>把前端构建产物（Vite 的 {@code web/frontend/dist}）以**文件系统目录**同源托管：真实存在的
 * 文件按静态资源返回，其余非 {@code /api/**} 路径回退到 {@code index.html}（SPA history 回退）。
 * {@code /api/**} 命名空间在这里被显式短路，**绝不**回退——未知接口由
 * {@code com.treelearn.web.api.UnknownApiController} 给出 §3.1 错误封装。
 *
 * <p>产物缺失时不阻断启动：{@link FrontendDist} 记一条警告日志并用可读提示页作为回退目标。
 * 根路径 {@code /} 由 {@link SpaRootController} 直接返回同一个回退目标（Spring 的
 * {@code ResourceHttpRequestHandler} 无法服务空路径）。
 *
 * <p>之所以由本类独占 {@code /**} 映射（并在 {@code application.properties} 关闭 Boot 默认的
 * classpath 静态映射），是因为默认映射无法携带 SPA 回退，且与自定义 resolver 同时注册同一模式会
 * 产生覆盖顺序不确定的问题。旧的 classpath 占位页 {@code static/index.html} 已由本任务移除，
 * 以免 Boot 的 welcome page 抢先占用 {@code /}。
 */
@Configuration
class StaticHostingConfiguration implements WebMvcConfigurer {

    /** 前端 dist 目录的可覆盖属性名。 */
    static final String DIST_PROPERTY = "treelearn.frontend-dist";

    private static final String API_SEGMENT = "api";

    private final FrontendDist dist;

    StaticHostingConfiguration(FrontendDist dist) {
        this.dist = dist;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**")
                .addResourceLocations(dist.location())
                .resourceChain(false)
                .addResolver(new SpaResourceResolver(dist.index()));
    }

    /**
     * 解析一个请求路径：先短路 {@code /api/**}（永不由静态托管处理），再按文件命中，最后对非 API
     * 路径回退到 {@code index.html}（dist 缺失时回退到可读提示页）。
     */
    private static final class SpaResourceResolver extends PathResourceResolver {

        private final Resource index;

        SpaResourceResolver(Resource index) {
            this.index = index;
        }

        @Override
        protected Resource getResource(String resourcePath, Resource location) throws IOException {
            if (isApiPath(resourcePath)) {
                return null;
            }
            Resource found = super.getResource(resourcePath, location);
            if (found != null && !isDirectory(found)) {
                return found;
            }
            return index;
        }

        /**
         * 大小写不敏感地判定 API 命名空间（{@code /api}、{@code /API}、{@code /Api/...} 都是 API）。
         * Spring 路由本身大小写敏感，这里只扩大“不回退”的范围：大小写变体路径同样不落 index.html，
         * 由未命中端点的兜底通道给出 §3.1 封装；已声明的 {@code /api/**} 路由更具体，优先级更高不受影响。
         */
        private static boolean isApiPath(String resourcePath) {
            String normalized = resourcePath.toLowerCase(Locale.ROOT);
            return normalized.equals(API_SEGMENT) || normalized.startsWith(API_SEGMENT + "/");
        }

        private static boolean isDirectory(Resource resource) {
            try {
                return resource.getFile().isDirectory();
            } catch (IOException | RuntimeException notAFile) {
                return false;
            }
        }
    }
}
