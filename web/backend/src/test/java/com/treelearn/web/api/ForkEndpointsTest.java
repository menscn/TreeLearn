package com.treelearn.web.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.treelearn.ai.gateway.GenerationSink;
import com.treelearn.ai.prompt.DeepSeekRequest;
import com.treelearn.core.domain.AnswerRevision;
import com.treelearn.core.domain.Branch;
import com.treelearn.core.domain.ContextSnapshot;
import com.treelearn.core.domain.GenerationModel;
import com.treelearn.web.api.dto.ApiJson;
import com.treelearn.web.credential.SecretPort;
import com.treelearn.web.generation.DesktopGenerationGateway;
import com.treelearn.web.storage.AnswerRevisionStore;
import com.treelearn.web.storage.BranchStore;
import com.treelearn.web.storage.DesktopSchema;
import com.treelearn.web.storage.JdbcSupport;
import com.treelearn.web.storage.MessageStore;
import com.treelearn.web.storage.ModelStore;
import com.treelearn.web.storage.OperationStore;
import com.treelearn.web.storage.SnapshotStore;
import com.treelearn.web.storage.StorageException;
import com.treelearn.web.storage.TopicStore;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.JsonNode;

/**
 * T-2026-10-10-037：稳定回答分叉端点（契约 {@code web/contracts/api.md} §6.3）。
 *
 * <p>真实 HTTP + 真 SQLite + 桩网关：覆盖整条/选文分叉、非稳定源拒绝、锚点畸形拒绝、出生快照冻结
 * 且不回流、根分支携带溯源列被 schema 拒绝、{@code commandId} 幂等、可选首问原子接受。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ForkEndpointsTest {

    private static final long T0 = 1_700_000_000_000L;
    private static final long T1 = 1_700_000_500_000L;
    private static final String POLICY = "tl-learning-answer/1.1.0";
    private static final String HASH =
            "9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08";

    @TempDir
    static Path dataDir;

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("treelearn.data-dir", () -> dataDir.toString().replace('\\', '/'));
    }

    @LocalServerPort int port;
    @Autowired JdbcSupport jdbc;
    @Autowired TopicStore topics;
    @Autowired BranchStore branches;
    @Autowired MessageStore messages;
    @Autowired AnswerRevisionStore revisions;
    @Autowired OperationStore operations;
    @Autowired SnapshotStore snapshots;
    @Autowired ModelStore models;
    @Autowired StubGateway gateway;

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    @BeforeEach
    void seedProvider() {
        String providerId = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO " + DesktopSchema.Providers.TABLE + "("
                + DesktopSchema.Providers.ID + "," + DesktopSchema.Providers.NAME + ","
                + DesktopSchema.Providers.BASE_URL + "," + DesktopSchema.Providers.ENABLED + ","
                + DesktopSchema.Providers.IS_BUILTIN + "," + DesktopSchema.Providers.CREATED_AT + ","
                + DesktopSchema.Providers.UPDATED_AT + ") VALUES (?,?,?,?,?,?,?)", s -> {
            s.setString(1, providerId); s.setString(2, "DeepSeek-" + providerId.substring(0, 8));
            s.setString(3, "https://api.deepseek.com"); s.setInt(4, 1); s.setInt(5, 1);
            s.setLong(6, T0); s.setLong(7, T0);
        });
        models.upsertMany(providerId, List.of("deepseek-flash"), () -> UUID.randomUUID().toString(), T0);
    }

    // ------------------------------------------------------------------ whole fork

    @Test
    void wholeAnswerForkFreezesBirthSnapshot() throws Exception {
        Seed seed = seedStableTopic("光合作用是什么？", "植物利用光能合成有机物。");

        JsonNode result = post("/api/branches/" + seed.rootBranchId + "/forks",
                forkBody(uuid(), seed.aiMessageId, seed.revisionId, "null", null, null, null));

        String childId = result.get("branch").get("id").asText();
        assertEquals(seed.rootBranchId, result.get("branch").get("parentBranchId").asText());
        assertEquals("null-anchor", wholeAnchor(result));
        assertEquals(seed.aiMessageId, result.get("source").get("sourceMessageId").asText());
        assertEquals(seed.revisionId, result.get("source").get("sourceRevisionId").asText());
        assertFalse(result.get("source").get("contextSnapshotId").asText().isBlank());
        assertTrue(result.get("bootstrap").isNull(), "无 question 时分叉响应不含 bootstrap");

        // 出生快照已冻结：一分支一快照，来源 = 到分叉点为止的 USER 消息与显示版本
        ContextSnapshot stored = snapshots.findForBranch(childId).orElseThrow();
        assertEquals(2, stored.sources.size());
        assertEquals(ContextSnapshot.SourceKind.USER_MESSAGE, stored.sources.get(0).kind);
        assertEquals(seed.userMessageId, stored.sources.get(0).refId);
        assertEquals(ContextSnapshot.SourceKind.STABLE_REVISION, stored.sources.get(1).kind);
        assertEquals(seed.revisionId, stored.sources.get(1).refId);
        assertEquals(stored.id, result.get("source").get("contextSnapshotId").asText(),
                "子分支 context_snapshot_id 必须指向刚写入的快照");
    }

    // -------------------------------------------------------------- selection fork

    @Test
    void selectionForkPersistsAnchorColumns() throws Exception {
        Seed seed = seedStableTopic("问题", "植物利用光能");
        String quote = "利用光能"; // 4 个 UTF-16 单元，区间 [2, 6)

        JsonNode result = post("/api/branches/" + seed.rootBranchId + "/forks",
                forkBody(uuid(), seed.aiMessageId, seed.revisionId,
                        "{\"startInclusive\":2,\"endExclusive\":6,\"quote\":\"" + quote
                                + "\",\"hash\":\"" + HASH + "\"}", null, null, null));

        JsonNode anchor = result.get("source").get("anchor");
        assertEquals(2, anchor.get("startInclusive").asInt());
        assertEquals(6, anchor.get("endExclusive").asInt());
        assertEquals(quote, anchor.get("quote").asText());
        assertEquals(HASH, anchor.get("hash").asText());

        // 四列确实落到 branches 行并可读回
        Branch child = branches.get(result.get("branch").get("id").asText()).orElseThrow();
        assertEquals(Integer.valueOf(2), child.source.anchor.startInclusive);
        assertEquals(Integer.valueOf(6), child.source.anchor.endExclusive);
        assertEquals(quote, child.source.anchor.quote);
        assertEquals(HASH, child.source.anchor.hash);
    }

    // ---------------------------------------------------------------- refusals

    @Test
    void nonStableSourceIsRefused() throws Exception {
        // AI 位置尚未产生任何稳定版本
        TopicStore.Created created = topics.create(T0);
        String rootId = created.rootBranch.id;
        String questionId = messages.appendUserMessage(rootId, 0, "问题", "", T0).id;
        String aiId = messages.appendAiPosition(rootId, 1, T0).id;

        assertRefusal(postRaw("/api/branches/" + rootId + "/forks",
                forkBody(uuid(), aiId, uuid(), "null", null, null, null)),
                409, "OPERATION_NOT_STABLE");

        // 对 USER 位置分叉同样拒绝
        assertRefusal(postRaw("/api/branches/" + rootId + "/forks",
                forkBody(uuid(), questionId, uuid(), "null", null, null, null)),
                409, "OPERATION_NOT_STABLE");
    }

    @Test
    void malformedAnchorIsRefused() throws Exception {
        Seed seed = seedStableTopic("问题", "植物利用光能");
        // quote 长度 5 != 区间跨度 10
        assertRefusal(postRaw("/api/branches/" + seed.rootBranchId + "/forks",
                forkBody(uuid(), seed.aiMessageId, seed.revisionId,
                        "{\"startInclusive\":0,\"endExclusive\":10,\"quote\":\"too short\","
                                + "\"hash\":\"" + HASH + "\"}", null, null, null)),
                400, "ANCHOR_INVALID");
    }

    @Test
    void foreignTopicSourceIsRefused() throws Exception {
        Seed seed = seedStableTopic("问题", "答案");
        // 另一个主题里的稳定回答：来源合法但不属于本主题
        TopicStore.Created other = topics.create(T1);
        String otherAi = messages.appendAiPosition(other.rootBranch.id, 0, T1).id;
        AnswerRevision otherRev = revisions.insert(otherAi, "想", "别处答案", GenerationModel.FLASH,
                false, POLICY, T1);
        messages.setActiveRevision(otherAi, otherRev.id);

        assertRefusal(postRaw("/api/branches/" + seed.rootBranchId + "/forks",
                forkBody(uuid(), otherAi, otherRev.id, "null", null, null, null)),
                400, "SOURCE_MESSAGE_NOT_IN_TOPIC");
    }

    // ------------------------------------------------------------------- freeze

    @Test
    void birthSnapshotDoesNotReflectLaterParentContent() throws Exception {
        Seed seed = seedStableTopic("问题", "答案");
        JsonNode result = post("/api/branches/" + seed.rootBranchId + "/forks",
                forkBody(uuid(), seed.aiMessageId, seed.revisionId, "null", null, null, null));
        String childId = result.get("branch").get("id").asText();
        List<String> before = refs(snapshots.findForBranch(childId).orElseThrow().sources);

        // 父分支后续追加新问答与稳定版本
        String q2 = messages.appendUserMessage(seed.rootBranchId, 2, "追问", "", T1).id;
        String ai2 = messages.appendAiPosition(seed.rootBranchId, 3, T1).id;
        AnswerRevision rev2 = revisions.insert(ai2, "想", "新的答案", GenerationModel.FLASH, false,
                POLICY, T1);
        messages.setActiveRevision(ai2, rev2.id);

        List<String> after = refs(snapshots.findForBranch(childId).orElseThrow().sources);
        assertEquals(before, after, "出生快照不因父分支后续新内容回流");
        assertEquals(2, after.size());
        assertEquals("USER_MESSAGE:" + seed.userMessageId, after.get(0));
        assertEquals("STABLE_REVISION:" + seed.revisionId, after.get(1));
        assertFalse(after.contains("USER_MESSAGE:" + q2) || after.contains("STABLE_REVISION:" + rev2.id),
                "分叉点之后的父内容不得进入子快照");
    }

    @Test
    void rootBranchWithProvenanceIsRejectedBySchema() {
        Seed seed = seedStableTopic("问题", "答案");
        // 根分支（parent_id IS NULL）携带溯源列 → branches CHECK 拒绝
        assertThrows(StorageException.class, () -> jdbc.update(
                "INSERT INTO " + DesktopSchema.Branches.TABLE + "(" + DesktopSchema.Branches.ID + ","
                        + DesktopSchema.Branches.TOPIC_ID + ","
                        + DesktopSchema.Branches.SOURCE_MESSAGE_ID + ","
                        + DesktopSchema.Branches.SOURCE_REVISION_ID + ","
                        + DesktopSchema.Branches.CREATED_AT + ","
                        + DesktopSchema.Branches.UPDATED_AT + ") VALUES (?,?,?,?,?,?)", s -> {
                    s.setString(1, UUID.randomUUID().toString());
                    s.setString(2, seed.topicId);
                    s.setString(3, seed.aiMessageId);
                    s.setString(4, seed.revisionId);
                    s.setLong(5, T1); s.setLong(6, T1);
                }));
    }

    // ----------------------------------------------------- optional first question

    @Test
    void firstQuestionIsAcceptedAtomically() throws Exception {
        Seed seed = seedStableTopic("问题", "答案");

        JsonNode result = post("/api/branches/" + seed.rootBranchId + "/forks",
                forkBody(uuid(), seed.aiMessageId, seed.revisionId, "null", "新分支的追问",
                        "deepseek-flash", Boolean.FALSE));

        String childId = result.get("branch").get("id").asText();
        JsonNode bootstrap = result.get("bootstrap");
        assertFalse(bootstrap.isNull(), "带 question 时必须返回 bootstrap");
        assertEquals(childId, bootstrap.get("rootBranch").get("id").asText());
        assertEquals("USER", bootstrap.get("userMessage").get("role").asText());
        assertEquals("AI", bootstrap.get("aiMessage").get("role").asText());
        assertFalse(bootstrap.get("operation").get("id").asText().isBlank());

        List<MessageStore.Position> childMessages = messages.listForBranch(childId);
        assertEquals(2, childMessages.size(), "USER 位 + AI 位");
        assertEquals("新分支的追问", childMessages.get(0).message.userContent);
        assertTrue(operations.byId(bootstrap.get("operation").get("id").asText()).isPresent());
    }

    @Test
    void failedFirstQuestionLandsNoPositions() throws Exception {
        Seed seed = seedStableTopic("问题", "答案");

        // 未知模型：模型解析在写入位置之前拒绝，问题位置不得落库
        HttpResponse<String> response = postRaw("/api/branches/" + seed.rootBranchId + "/forks",
                forkBody(uuid(), seed.aiMessageId, seed.revisionId, "null", "追问", "gpt-4",
                        Boolean.FALSE));
        assertRefusal(response, 400, "REQUEST_INVALID");

        List<Branch> children = branches.children(seed.rootBranchId);
        assertEquals(1, children.size());
        assertTrue(messages.listForBranch(children.get(0).id).isEmpty(),
                "首问接受失败时子分支不得留下 USER/AI 位");
    }

    @Test
    void repeatedCommandIdReturnsSameForkAndOperation() throws Exception {
        Seed seed = seedStableTopic("问题", "答案");
        String commandId = uuid();
        String body = forkBody(commandId, seed.aiMessageId, seed.revisionId, "null", "首问",
                "deepseek-flash", Boolean.FALSE);

        JsonNode first = post("/api/branches/" + seed.rootBranchId + "/forks", body);
        JsonNode replay = post("/api/branches/" + seed.rootBranchId + "/forks", body);

        assertEquals(first.get("branch").get("id").asText(), replay.get("branch").get("id").asText());
        assertEquals(first.get("bootstrap").get("operation").get("id").asText(),
                replay.get("bootstrap").get("operation").get("id").asText());
        assertEquals(1, gateway.calls.size(), "重放不得开启第二次模型调用");
        assertEquals(1, branches.children(seed.rootBranchId).size(), "重放不得二次创建分支");
    }

    // -------------------------------------------------------------------- helpers

    private record Seed(String topicId, String rootBranchId, String userMessageId, String aiMessageId,
            String revisionId) {}

    private Seed seedStableTopic(String question, String answer) {
        TopicStore.Created created = topics.create(T0);
        String rootId = created.rootBranch.id;
        String user = messages.appendUserMessage(rootId, 0, question, "", T0).id;
        String ai = messages.appendAiPosition(rootId, 1, T0).id;
        AnswerRevision revision = revisions.insert(ai, "想", answer, GenerationModel.FLASH, false,
                POLICY, T0);
        messages.setActiveRevision(ai, revision.id);
        return new Seed(created.topic.id, rootId, user, ai, revision.id);
    }

    /** 快照来源的稳定字符串投影（`ContextSnapshot.Source` 无 equals，按 kind+refId 比对）。 */
    private static List<String> refs(List<ContextSnapshot.Source> sources) {
        return sources.stream().map(ContextSnapshot.Source::toString).toList();
    }

    private static String wholeAnchor(JsonNode result) {
        JsonNode anchor = result.get("source").get("anchor");
        return anchor.get("startInclusive").isNull() && anchor.get("endExclusive").isNull()
                && anchor.get("quote").isNull() && anchor.get("hash").isNull()
                        ? "null-anchor" : "ranged";
    }

    private static String forkBody(String commandId, String sourceMessageId, String sourceRevisionId,
            String anchorJson, String question, String model, Boolean thinking) {
        StringBuilder body = new StringBuilder("{\"commandId\":\"").append(commandId)
                .append("\",\"sourceMessageId\":\"").append(sourceMessageId)
                .append("\",\"sourceRevisionId\":\"").append(sourceRevisionId)
                .append("\",\"anchor\":").append(anchorJson)
                .append(",\"question\":").append(question == null ? "null" : "\"" + question + "\"")
                .append(",\"model\":").append(model == null ? "null" : "\"" + model + "\"")
                .append(",\"thinkingEnabled\":").append(thinking == null ? "null" : thinking.toString())
                .append("}");
        return body.toString();
    }

    private JsonNode post(String path, String body) throws Exception {
        HttpResponse<String> response = postRaw(path, body);
        assertEquals(200, response.statusCode(), "POST " + path + " -> " + response.body());
        return ApiJson.mapper().readTree(response.body());
    }

    private HttpResponse<String> postRaw(String path, String body) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create(base() + path))
                .header("Content-Type", "application/json; charset=UTF-8")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8)).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private void assertRefusal(HttpResponse<String> response, int status, String code) throws Exception {
        assertEquals(status, response.statusCode(), "body: " + response.body());
        assertEquals(code, ApiJson.mapper().readTree(response.body()).get("error").get("code").asText());
    }

    private String base() {
        return "http://127.0.0.1:" + port;
    }

    private static String uuid() {
        return UUID.randomUUID().toString();
    }

    // -------------------------------------------------------------- test wiring

    @TestConfiguration
    static class StubConfiguration {

        @Bean
        @Primary
        StubGateway stubGateway() {
            return new StubGateway();
        }

        @Bean
        @Primary
        SecretPort stubSecretPort() {
            return new StubSecretPort();
        }
    }

    static final class StubSecretPort implements SecretPort {

        @Override
        public void save(String target, char[] secret) {
        }

        @Override
        public boolean has(String target) {
            return true;
        }

        @Override
        public Optional<char[]> read(String target) {
            return Optional.of("sk-test".toCharArray());
        }

        @Override
        public void delete(String target) {
        }
    }

    /** 只记录调用、不自动推进的网关：分叉只接受首问并开流，测试无需 SSE。 */
    static final class StubGateway implements DesktopGenerationGateway {

        final List<Call> calls = Collections.synchronizedList(new ArrayList<>());

        @Override
        public Stream open(GenerationModel model, DeepSeekRequest request, GenerationSink sink) {
            Call call = new Call(sink);
            calls.add(call);
            return call;
        }

        static final class Call implements DesktopGenerationGateway.Stream {

            private final GenerationSink sink;
            private boolean cancelled;

            Call(GenerationSink sink) {
                this.sink = sink;
            }

            @Override
            public void cancel() {
                cancelled = true;
            }

            @Override
            public boolean isCancelled() {
                return cancelled;
            }
        }
    }
}
