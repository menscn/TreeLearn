package com.treelearn.web.hosting;

import java.io.IOException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * 解析发行口前端产物位置，供静态托管（{@link StaticHostingConfiguration}）与根路径入口
 * （{@link SpaRootController}）共享同一个 dist/index（任务 T-2026-10-10-029）。
 */
@Configuration
class FrontendDistConfiguration {

    @Bean
    FrontendDist frontendDist(Environment environment) throws IOException {
        return FrontendDist.resolve(environment.getProperty(StaticHostingConfiguration.DIST_PROPERTY));
    }
}
