package com.treelearn.web.generation;

import com.treelearn.web.storage.AnswerRevisionStore;
import com.treelearn.web.storage.BranchStore;
import com.treelearn.web.storage.MessageStore;
import com.treelearn.web.storage.OperationStore;
import com.treelearn.web.storage.TopicStore;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 分支生命周期与删除端点的 Spring 装配（任务 T-2026-10-10-042）。
 *
 * <p>独立成配置：{@link BranchLifecycleService} 依赖协调器与五个存储，与核心闭环
 * （{@code GenerationApiConfiguration}）和分叉（{@code ForkConfiguration}）各走各的装配，
 * 避免在既有配置文件上并行写入。{@code Clock} 由 {@code GenerationApiConfiguration} 提供。
 */
@Configuration
public class BranchLifecycleConfiguration {

    @Bean
    BranchLifecycleService branchLifecycleService(TopicStore topics, BranchStore branches,
            MessageStore messages, AnswerRevisionStore revisions, OperationStore operations,
            DesktopGenerationCoordinator coordinator, Clock clock) {
        return new BranchLifecycleService(topics, branches, messages, revisions, operations,
                coordinator, clock);
    }
}
