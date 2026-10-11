package com.treelearn.web.generation;

import com.treelearn.web.storage.AnnotationStore;
import com.treelearn.web.storage.AttachmentStore;
import com.treelearn.web.storage.DesktopDatabase;
import com.treelearn.web.storage.AnswerRevisionStore;
import com.treelearn.web.storage.ArtifactStore;
import com.treelearn.web.storage.BranchStore;
import com.treelearn.web.storage.JdbcSupport;
import com.treelearn.web.storage.MessageStore;
import com.treelearn.web.storage.OperationStore;
import com.treelearn.web.storage.SnapshotStore;
import com.treelearn.web.storage.TopicStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 生成域与 SSE 端点的 Spring 装配（任务 T-2026-10-10-026）。
 *
 * <p>总线、存储与操作行在此前三者只由测试手动构造（{@code StorageConfiguration} 只暴露
 * {@link DesktopDatabase}）。SSE 端点需要它们成为单例，并且关键前提是：端点订阅的
 * {@link GenerationEventBus} 必须与生成协调器发布帧所用的是**同一个实例**——seq 语义以总线为准
 * （契约 §7.1/§7.3），否则实时转发与快照的序列号会各说各话。
 *
 * <p>本配置只把既有的读取/写入入口登记为 bean，不引入任何新行为，也不改变既有存储的构造方式。
 */
@Configuration
public class GenerationConfiguration {

    @Bean
    JdbcSupport jdbcSupport(DesktopDatabase database) {
        return new JdbcSupport(database);
    }

    @Bean
    TopicStore topicStore(JdbcSupport jdbc) {
        return new TopicStore(jdbc);
    }

    @Bean
    BranchStore branchStore(JdbcSupport jdbc) {
        return new BranchStore(jdbc);
    }

    @Bean
    MessageStore messageStore(JdbcSupport jdbc) {
        return new MessageStore(jdbc);
    }

    @Bean
    AnswerRevisionStore answerRevisionStore(JdbcSupport jdbc) {
        return new AnswerRevisionStore(jdbc);
    }

    @Bean
    ArtifactStore artifactStore(JdbcSupport jdbc) {
        return new ArtifactStore(jdbc);
    }

    @Bean
    AnnotationStore annotationStore(JdbcSupport jdbc) {
        return new AnnotationStore(jdbc);
    }

    @Bean
    AttachmentStore attachmentStore(JdbcSupport jdbc, DesktopDatabase database,
            @org.springframework.beans.factory.annotation.Value("${treelearn.attachments.max-bytes:20971520}") long maxBytes) {
        return new AttachmentStore(jdbc, database.dataDir(), maxBytes);
    }

    @Bean
    OperationStore operationStore(JdbcSupport jdbc) {
        return new OperationStore(jdbc);
    }

    @Bean
    SnapshotStore snapshotStore(JdbcSupport jdbc) {
        return new SnapshotStore(jdbc);
    }

    @Bean
    GenerationEventBus generationEventBus() {
        return new GenerationEventBus();
    }
}
