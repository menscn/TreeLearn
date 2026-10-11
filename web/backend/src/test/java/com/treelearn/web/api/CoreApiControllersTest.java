package com.treelearn.web.api;

import static org.hamcrest.Matchers.hasItem;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.treelearn.core.domain.GenerationCommand;
import com.treelearn.core.domain.GenerationModel;
import com.treelearn.core.domain.GenerationOperation;
import com.treelearn.core.domain.Message;
import com.treelearn.core.domain.PromptPurpose;
import com.treelearn.web.generation.ChatCommandService;
import com.treelearn.web.generation.ServiceRefusalException;
import com.treelearn.web.storage.AnswerRevisionStore;
import com.treelearn.web.storage.BranchStore;
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
 * T-2026-10-10-028: MockMvc 形状与错误码矩阵（契约 §3/§5/§6）。
 *
 * <p>GET 端点走真实 Store（临时数据目录）；变更端点由 {@link ChatCommandService} 桩驱动，以便对
 * §3.3 每个涉及码断言 HTTP 状态与封装形态（映射层正确性）。
 */
@SpringBootTest
class CoreApiControllersTest {

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
    @Autowired BranchStore branches;
    @Autowired MessageStore messages;
    @Autowired OperationStore operations;
    @Autowired AnswerRevisionStore revisions;

    @MockitoBean ChatCommandService commands;

    MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    private TopicStore.Created seedTopic(String question) {
        TopicStore.Created created = topics.create(T0);
        messages.appendUserMessage(created.rootBranch.id, 0, question, "", T0);
        messages.appendAiPosition(created.rootBranch.id, 1, T0);
        return created;
    }

    private OperationStore.Operation seedOperation(String branchId, String aiMessageId) {
        GenerationCommand command = GenerationCommand.create(branchId, aiMessageId,
                PromptPurpose.LEARNING_ANSWER, POLICY, GenerationModel.FLASH, false, T0).value();
        return operations.insert(GenerationOperation.start(command, T0));
    }

    private String firstUser(String branchId) {
        return messages.listForBranch(branchId).get(0).message.id;
    }

    private String aiMessage(String branchId) {
        return messages.listForBranch(branchId).get(1).message.id;
    }

    // ---------------------------------------------------------------- shapes

    @Test
    void topicsListReturnsSummaryRows() throws Exception {
        TopicStore.Created created = seedTopic("光合作用是什么？");
        mvc.perform(get("/api/topics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[?(@.topicId=='" + created.topic.id + "')]").isNotEmpty())
                .andExpect(jsonPath("$[?(@.topicId=='" + created.topic.id + "')].displayTitle",
                        hasItem("光合作用是什么？")))
                .andExpect(jsonPath("$[?(@.topicId=='" + created.topic.id + "')].rootQuestion",
                        hasItem("光合作用是什么？")));
    }

    @Test
    void unknownTopicReturnsNotFoundEnvelope() throws Exception {
        mvc.perform(get("/api/topics/00000000-0000-4000-8000-000000000000"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.error.params.resource").value("topic"))
                .andExpect(jsonPath("$.error.retryable").value(false))
                .andExpect(jsonPath("$.requestId").isNotEmpty());
    }

    @Test
    void branchTreeCarriesTopicIdAndNodeFields() throws Exception {
        TopicStore.Created created = seedTopic("问题");
        mvc.perform(get("/api/topics/" + created.topic.id + "/branches"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.topicId").value(created.topic.id))
                .andExpect(jsonPath("$.nodes").isArray())
                .andExpect(jsonPath("$.nodes[0].id").value(created.rootBranch.id))
                .andExpect(jsonPath("$.nodes[0].unreadCount").value(0))
                .andExpect(jsonPath("$.nodes[0].collapsed").value(false))
                .andExpect(jsonPath("$.nodes[0].archivedAtMillis").isEmpty());
    }

    @Test
    void conversationReturnsMessagesWithEmptyAttachmentsAndVersions() throws Exception {
        TopicStore.Created created = seedTopic("问题");
        String aiId = aiMessage(created.rootBranch.id);
        revisions.insert(aiId, "想", "答案正文", GenerationModel.FLASH, false, POLICY, T0 + 10_000L);
        messages.setActiveRevision(aiId, revisions.listForMessage(aiId).get(0).id);
        mvc.perform(get("/api/branches/" + created.rootBranch.id + "/messages"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.branchId").value(created.rootBranch.id))
                .andExpect(jsonPath("$.messages").isArray())
                .andExpect(jsonPath("$.messages[0].attachments").isEmpty())
                .andExpect(jsonPath("$.messages[1].versions[0].answerContent").value("答案正文"))
                .andExpect(jsonPath("$.recommendations").isArray());
    }

    @Test
    void unknownBranchConversationReturnsNotFound() throws Exception {
        mvc.perform(get("/api/branches/00000000-0000-4000-8000-000000000001/messages"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.params.resource").value("branch"));
    }

    @Test
    void createTopicReturnsBootstrapShape() throws Exception {
        TopicStore.Created created = seedTopic("问题");
        String aiId = aiMessage(created.rootBranch.id);
        Message user = messages.get(firstUser(created.rootBranch.id)).orElseThrow();
        Message ai = messages.get(aiId).orElseThrow();
        OperationStore.Operation op = seedOperation(created.rootBranch.id, aiId);
        doReturn(new ChatCommandService.CreatedTopic(created.topic, created.rootBranch, user, ai, op))
                .when(commands).createTopic(any(), any(), any(), anyBoolean(), any());

        mvc.perform(post("/api/topics").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"commandId\":\"c1\",\"question\":\"问题\",\"model\":\"deepseek-flash\","
                                + "\"thinkingEnabled\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.topic.id").value(created.topic.id))
                .andExpect(jsonPath("$.rootBranch.id").value(created.rootBranch.id))
                .andExpect(jsonPath("$.userMessage.role").value("USER"))
                .andExpect(jsonPath("$.aiMessage.role").value("AI"))
                .andExpect(jsonPath("$.operation.sseUrl")
                        .value("/api/operations/" + op.id + "/events"));
    }

    @Test
    void sendMessageReturnsTurnAcceptedShape() throws Exception {
        TopicStore.Created created = seedTopic("问题");
        String aiId = aiMessage(created.rootBranch.id);
        Message user = messages.get(firstUser(created.rootBranch.id)).orElseThrow();
        Message ai = messages.get(aiId).orElseThrow();
        OperationStore.Operation op = seedOperation(created.rootBranch.id, aiId);
        doReturn(new ChatCommandService.Turn(user, ai, op))
                .when(commands).sendMessage(any(), any(), any(), any(), any(), anyBoolean());

        mvc.perform(post("/api/branches/" + created.rootBranch.id + "/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"commandId\":\"c2\",\"text\":\"追问\",\"model\":\"deepseek-flash\","
                                + "\"thinkingEnabled\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userMessage.id").value(user.id))
                .andExpect(jsonPath("$.aiMessage.id").value(ai.id))
                .andExpect(jsonPath("$.operation.id").value(op.id));
    }

    @Test
    void retryReturnsMessageAndOperationShape() throws Exception {
        TopicStore.Created created = seedTopic("问题");
        String aiId = aiMessage(created.rootBranch.id);
        Message ai = messages.get(aiId).orElseThrow();
        OperationStore.Operation op = seedOperation(created.rootBranch.id, aiId);
        doReturn(new ChatCommandService.Replaced(ai, op))
                .when(commands).retry(any(), any(), any(), anyBoolean());

        mvc.perform(post("/api/messages/" + aiId + "/retry").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"commandId\":\"c3\",\"model\":\"deepseek-flash\",\"thinkingEnabled\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message.id").value(aiId))
                .andExpect(jsonPath("$.operation.id").value(op.id));
    }

    @Test
    void operationReadsReturnOperationShape() throws Exception {
        TopicStore.Created created = seedTopic("问题");
        String aiId = aiMessage(created.rootBranch.id);
        OperationStore.Operation op = seedOperation(created.rootBranch.id, aiId);

        mvc.perform(get("/api/operations/" + op.id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("STAGED"))
                .andExpect(jsonPath("$.sseUrl").value("/api/operations/" + op.id + "/events"));

        mvc.perform(get("/api/operations/active").param("branchId", created.rootBranch.id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(op.id));
    }

    @Test
    void activeOperationWithoutOneReturnsNullBody() throws Exception {
        TopicStore.Created created = seedTopic("问题");
        mvc.perform(get("/api/operations/active").param("branchId", created.rootBranch.id))
                .andExpect(status().isOk())
                .andExpect(content().string("null"));
    }

    @Test
    void activeOperationWithMissingOrInvalidBranchIdIsRequestInvalid() throws Exception {
        mvc.perform(get("/api/operations/active"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("REQUEST_INVALID"))
                .andExpect(jsonPath("$.error.params.httpStatus").value(400));

        mvc.perform(get("/api/operations/active").param("branchId", "   "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("REQUEST_INVALID"));

        mvc.perform(get("/api/operations/active").param("branchId", "not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("REQUEST_INVALID"));
    }

    // ---------------------------------------------------------- error matrix

    @Test
    void errorMatrixMapsCodesToStatusAndEnvelope() throws Exception {
        assertRefusal("KEY_MISSING", 400);
        assertRefusal("NO_MODELS", 400);
        assertRefusal("REQUEST_INVALID", 400);
        assertRefusal("MESSAGE_ROLE_MISMATCH", 400);
        assertRefusal("CONTEXT_EXCEEDS_BUDGET", 400);
        assertRefusal("BRANCH_BUSY", 409);
        assertRefusal("ILLEGAL_STATE_TRANSITION", 409);
        assertRefusal("TOPIC_ID_ALREADY_EXISTS", 409);
        assertRefusal("OPERATION_NOT_COMPLETABLE", 404);
        assertRefusal("NOT_FOUND", 404);
        assertRefusal("STORAGE_REFUSED", 500);
    }

    private void assertRefusal(String code, int expectedStatus) throws Exception {
        doThrow(new ServiceRefusalException(code, Map.of()))
                .when(commands).createTopic(any(), any(), any(), anyBoolean(), any());
        mvc.perform(post("/api/topics").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"commandId\":\"e\",\"question\":\"q\",\"model\":\"deepseek-flash\","
                                + "\"thinkingEnabled\":false}"))
                .andExpect(status().is(expectedStatus))
                .andExpect(jsonPath("$.error.code").value(code))
                .andExpect(jsonPath("$.error.params").isMap())
                .andExpect(jsonPath("$.requestId").isNotEmpty());
    }

    @Test
    void unclassifiedFailureReturnsInternalError() throws Exception {
        doThrow(new IllegalStateException("boom"))
                .when(commands).createTopic(any(), any(), any(), anyBoolean(), any());
        mvc.perform(post("/api/topics").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"commandId\":\"e\",\"question\":\"q\",\"model\":\"deepseek-flash\","
                                + "\"thinkingEnabled\":false}"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.error.retryable").value(true));
    }
}
