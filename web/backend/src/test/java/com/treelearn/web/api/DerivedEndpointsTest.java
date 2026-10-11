package com.treelearn.web.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.treelearn.core.domain.GenerationModel;
import com.treelearn.core.domain.PromptPurpose;
import com.treelearn.web.storage.ArtifactStore;
import com.treelearn.web.storage.AnswerRevisionStore;
import com.treelearn.web.storage.MessageStore;
import com.treelearn.web.storage.TopicStore;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * T-2026-10-10-052：派生任务端点（契约 §6.5）。
 *
 * <p>切片：POST 支持 MESSAGE_TRANSLATION（语义自足：源文本即 currentInput、稳定版前置、
 * purpose 正确、走 operation + SSE）；其余六个 kind 依赖 Android {@code ArtifactInputs}
 * 的深度输入组装（birth context / tree outline / compression turns），返回 400
 * {@code DERIVED_KIND_UNSUPPORTED} 明示不支持——绝不以错误语义替代。GET 全 kind 支持
 * （ArtifactStore 读取 + stale 判定）。派生失败保留旧结果、绝不回落教学提示词。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class DerivedEndpointsTest {

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
    @Autowired ArtifactStore artifacts;

    MockMvc mvc;
    String aiId;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
        TopicStore.Created created = topics.create(T0);
        messages.appendUserMessage(created.rootBranch.id, 0, "光合作用是什么？", "", T0);
        messages.appendAiPosition(created.rootBranch.id, 1, T0);
        aiId = messages.listForBranch(created.rootBranch.id).get(1).message.id;
    }

    private String seedStableRevision() {
        var revision = revisions.insert(aiId, "", "光合作用是植物把光能变成化学能的过程。",
                GenerationModel.FLASH, false, POLICY, T0 + 1);
        messages.setActiveRevision(aiId, revision.id);
        return revision.id;
    }

    @Test
    void postTranslationOnStableMessageStartsAnOperation() throws Exception {
        seedStableRevision();
        mvc.perform(post("/api/derived").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"commandId\":\"c1\",\"kind\":\"MESSAGE_TRANSLATION\","
                                + "\"scopeId\":\"" + aiId + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.sseUrl").value(org.hamcrest.Matchers.startsWith("/api/operations/")));
    }

    @Test
    void postTranslationWithoutStableVersionIsRefused() throws Exception {
        mvc.perform(post("/api/derived").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"commandId\":\"c2\",\"kind\":\"MESSAGE_TRANSLATION\","
                                + "\"scopeId\":\"" + aiId + "\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("OPERATION_NOT_STABLE"));
    }

    @Test
    void postUnknownKindIsRejected() throws Exception {
        mvc.perform(post("/api/derived").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"commandId\":\"c3\",\"kind\":\"BOGUS\",\"scopeId\":\"" + aiId + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("DERIVED_KIND_INVALID"));
    }

    @Test
    void postKindsNeedingDeepAssemblyAreExplicitlyUnsupported() throws Exception {
        mvc.perform(post("/api/derived").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"commandId\":\"c4\",\"kind\":\"BRANCH_SUMMARY\","
                                + "\"scopeId\":\"" + aiId + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("DERIVED_KIND_UNSUPPORTED"));
    }

    @Test
    void getDerivedWithoutArtifactIsNotFound() throws Exception {
        mvc.perform(get("/api/derived").param("kind", "branch_summary").param("scopeId", aiId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    @Test
    void getDerivedReturnsArtifactWithStaleJudgement() throws Exception {
        artifacts.insert(ArtifactStore.Artifact.create("branch_summary", aiId,
                PromptPurpose.BRANCH_SUMMARY, POLICY, "{\"summary\":\"摘要\",\"key_points\":[]}",
                null, "digest-1", T0 + 5));

        // 无 basedOnRevisionId 请求参数：不判 stale（fresh 路径）。
        mvc.perform(get("/api/derived").param("kind", "branch_summary").param("scopeId", aiId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kind").value("branch_summary"))
                .andExpect(jsonPath("$.payload.summary").value("摘要"))
                .andExpect(jsonPath("$.stale").value(false));

        // 请求带 basedOnRevisionId 且行内基线为 null：视为 stale（基线不符）。
        mvc.perform(get("/api/derived").param("kind", "branch_summary").param("scopeId", aiId)
                        .param("basedOnRevisionId", "rev-x"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stale").value(true));
    }
}
