package com.treelearn.web.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.treelearn.core.domain.GenerationModel;
import com.treelearn.core.domain.Message;
import com.treelearn.web.generation.ChatCommandService;
import com.treelearn.web.generation.ServiceRefusalException;
import com.treelearn.web.storage.AnswerRevisionStore;
import com.treelearn.web.storage.MessageStore;
import com.treelearn.web.storage.OperationStore;
import com.treelearn.web.storage.TopicStore;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * T-2026-10-10-051：reset 与版本选择端点（契约 §6.4）——控制器 shape 与错误信封。
 *
 * <p>判定语义（OPERATION_NOT_STABLE / REVISION_IMMUTABLE / OPERATION_NOT_COMPLETABLE /
 * ILLEGAL_STATE_TRANSITION / 指针移动与网关调用）在 {@code ChatVersionCommandsTest} 用真实服务 +
 * 计数网关逐条覆盖；本类用服务桩隔离网关，只核对路由、响应形状与拒绝映射。revision 用真实
 * store 插入（@MockitoBean 只替换 ChatCommandService）。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class VersionCommandEndpointsTest {

    private static final long T0 = 1_700_000_000_000L;
    private static final String POLICY = "tl-learning-answer/1.1.0";

    @TempDir
    static Path dataDir;

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("treelearn.data-dir", () -> dataDir.toString().replace('\\', '/'));
    }

    @Autowired WebApplicationContext context;
    @Autowired TopicStore topics;
    @Autowired MessageStore messages;
    @Autowired AnswerRevisionStore revisions;

    @MockitoBean ChatCommandService commands;

    MockMvc mvc;
    String aiId;

    @Autowired OperationStore operations;

    private OperationStore.Operation seedOperation(String branchId, String messageId) {
        com.treelearn.core.domain.GenerationCommand command =
                com.treelearn.core.domain.GenerationCommand.create(branchId, messageId,
                        com.treelearn.core.domain.PromptPurpose.LEARNING_ANSWER, POLICY,
                        GenerationModel.FLASH, false, T0).value();
        return operations.insert(
                com.treelearn.core.domain.GenerationOperation.start(command, T0));
    }

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
        TopicStore.Created created = topics.create(T0);
        messages.appendUserMessage(created.rootBranch.id, 0, "光合作用是什么？", "", T0);
        messages.appendAiPosition(created.rootBranch.id, 1, T0);
        aiId = messages.listForBranch(created.rootBranch.id).get(1).message.id;
    }

    @Test
    void resetReturnsMessageAndOperationShape() throws Exception {
        Message ai = messages.get(aiId).orElseThrow();
        OperationStore.Operation op = seedOperation(ai.branchId, aiId);
        doReturn(new ChatCommandService.Replaced(ai, op))
                .when(commands).reset(anyString(), anyString(), any(), anyBoolean());

        mvc.perform(post("/api/messages/" + aiId + "/reset").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"commandId\":\"c1\",\"model\":\"deepseek-flash\",\"thinkingEnabled\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message.id").value(aiId))
                .andExpect(jsonPath("$.operation.id").value(op.id));
    }

    @Test
    void resetRefusalMapsToEnvelope() throws Exception {
        doThrow(new ServiceRefusalException("OPERATION_NOT_STABLE", Map.of()))
                .when(commands).reset(anyString(), anyString(), any(), anyBoolean());

        mvc.perform(post("/api/messages/" + aiId + "/reset").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"commandId\":\"c1\",\"model\":\"deepseek-flash\",\"thinkingEnabled\":false}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("OPERATION_NOT_STABLE"));
    }

    @Test
    void selectReturnsRevisionShape() throws Exception {
        var revision = revisions.insert(aiId, "", "答案 v2", GenerationModel.FLASH, false,
                POLICY, T0 + 1);
        doReturn(revision).when(commands).selectRevision(anyString(), anyString(), anyString());

        mvc.perform(post("/api/messages/" + aiId + "/revisions/" + revision.id + "/select")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"commandId\":\"c2\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(revision.id))
                .andExpect(jsonPath("$.messageId").value(aiId))
                .andExpect(jsonPath("$.answerContent").value("答案 v2"));
    }

    @Test
    void selectRefusalMapsToEnvelope() throws Exception {
        doThrow(new ServiceRefusalException("REVISION_IMMUTABLE", Map.of()))
                .when(commands).selectRevision(anyString(), anyString(), anyString());

        mvc.perform(post("/api/messages/" + aiId + "/revisions/rev1/select")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"commandId\":\"c2\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("REVISION_IMMUTABLE"));
    }
}
