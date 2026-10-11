package com.treelearn.web.generation;

import com.treelearn.ai.prompt.PromptBudget;
import com.treelearn.web.credential.SecretPort;
import com.treelearn.web.credential.WindowsCredentialSecretPort;
import com.treelearn.web.storage.AnswerRevisionStore;
import com.treelearn.web.storage.BranchStore;
import com.treelearn.web.storage.JdbcSupport;
import com.treelearn.web.storage.MessageStore;
import com.treelearn.web.storage.ModelStore;
import com.treelearn.web.storage.OperationStore;
import com.treelearn.web.storage.ProviderStore;
import com.treelearn.web.storage.TopicStore;
import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 核心闭环 REST 端点的 Spring 装配（任务 T-2026-10-10-028）。
 *
 * <p>协调器、网关、凭据端口与命令装配此前只在测试里手动构造；端点需要它们成为单例。网关与凭据端口
 * 均以 {@code @ConditionalOnMissingBean} 声明，测试用 {@code @Primary} 桩覆盖，生产走 Windows 凭据端口。
 */
@Configuration
public class GenerationApiConfiguration {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    PromptBudget promptBudget() {
        return new PromptBudget(60_000, 512);
    }

    @Bean
    @ConditionalOnMissingBean(SecretPort.class)
    SecretPort secretPort() {
        return new WindowsCredentialSecretPort();
    }

    @Bean
    ProviderStore providerStore(JdbcSupport jdbc) {
        return new ProviderStore(jdbc);
    }

    @Bean
    ModelStore modelStore(JdbcSupport jdbc) {
        return new ModelStore(jdbc);
    }

    @Bean
    GenerationPlanner generationPlanner(ProviderStore providers, ModelStore models, SecretPort secrets) {
        return new GenerationPlanner(providers, models, secrets);
    }

    @Bean
    DesktopGenerationGateway desktopGenerationGateway(ProviderStore providers, ModelStore models,
            SecretPort secrets) {
        return new ProviderGateway(providers, models, secrets);
    }

    @Bean
    DesktopGenerationCoordinator desktopGenerationCoordinator(JdbcSupport jdbc, TopicStore topics,
            BranchStore branches, MessageStore messages, OperationStore operations, GenerationEventBus bus,
            DesktopGenerationGateway gateway, Clock clock) {
        return new DesktopGenerationCoordinator(jdbc, topics, branches, messages, operations, bus, gateway,
                clock);
    }

    @Bean
    ChatCommandService chatCommandService(TopicStore topics, BranchStore branches, MessageStore messages,
            OperationStore operations, AnswerRevisionStore revisions,
            DesktopGenerationCoordinator coordinator, ProviderStore providers,
            ModelStore models, SecretPort secrets, PromptBudget budget, Clock clock) {
        return new ChatCommandService(topics, branches, messages, operations, revisions, coordinator,
                providers, models, secrets, budget, clock);
    }
}
