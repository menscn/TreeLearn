package com.treelearn.web.api;

import com.treelearn.ai.gateway.GatewayPorts;
import com.treelearn.ai.gateway.HttpsTransport;
import com.treelearn.web.ai.DesktopGatewayFactory;
import com.treelearn.web.generation.ProviderModelCatalog;
import com.treelearn.web.storage.DesktopDatabase;
import com.treelearn.web.storage.DesktopPreferences;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 设置端点（Provider/Key、模型分工）的 Spring 装配（任务 T-2026-10-10-036）。
 *
 * <p>独立成配置类（不改 {@code GenerationApiConfiguration}）以与并行的分叉端点任务隔离共享文件。
 * {@link ProviderSettingsController} 需要非敏感偏好文件与模型拉取传输成为单例：偏好文件位于数据目录；
 * 模型拉取传输生产为 HTTPS，测试以 {@code @Primary} 桩覆盖（本机 stub 走真 HTTP，不访问外网）。
 */
@Configuration
public class SettingsApiConfiguration {

    /** 非敏感偏好文件（数据目录 {@code settings.properties}），承载模型分工/预算/建议。 */
    @Bean
    DesktopPreferences desktopPreferences(DesktopDatabase database) {
        return new DesktopPreferences(database.dataDir().resolve("settings.properties"));
    }

    /** 模型列表拉取的传输：生产 HTTPS；测试以 {@code @Primary} 桩覆盖。 */
    @Bean
    @ConditionalOnMissingBean(GatewayPorts.Transport.class)
    GatewayPorts.Transport providerModelsTransport() {
        return new HttpsTransport(DesktopGatewayFactory.CONNECT_TIMEOUT_MILLIS,
                DesktopGatewayFactory.READ_TIMEOUT_MILLIS);
    }

    @Bean
    ProviderModelCatalog providerModelCatalog(GatewayPorts.Transport transport) {
        return new ProviderModelCatalog(transport);
    }
}
