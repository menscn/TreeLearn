package com.treelearn.web.generation;

import com.treelearn.web.storage.AnswerRevisionStore;
import com.treelearn.web.storage.BranchStore;
import com.treelearn.web.storage.JdbcSupport;
import com.treelearn.web.storage.MessageStore;
import com.treelearn.web.storage.OperationStore;
import com.treelearn.web.storage.SnapshotStore;
import com.treelearn.web.storage.TopicStore;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 分叉端点的 Spring 装配（任务 T-2026-10-10-037）。
 *
 * <p>{@link ForkCommandService} 需要协调器、存储与 {@link ChatCommandService}；独立成配置，避免与
 * 核心闭环装配（{@code GenerationApiConfiguration}）相互争用。{@code SnapshotStore} 由
 * {@code GenerationConfiguration} 注册为单例。
 */
@Configuration
public class ForkConfiguration {

    @Bean
    ForkCommandService forkCommandService(JdbcSupport jdbc, TopicStore topics, BranchStore branches,
            MessageStore messages, AnswerRevisionStore revisions, OperationStore operations,
            SnapshotStore snapshots, ChatCommandService commands, Clock clock) {
        return new ForkCommandService(jdbc, topics, branches, messages, revisions, operations, snapshots,
                commands, clock);
    }
}
