package com.treelearn.web.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.treelearn.ai.gateway.GenerationSink;
import com.treelearn.ai.prompt.DeepSeekRequest;
import com.treelearn.core.domain.AnswerRevision;
import com.treelearn.core.domain.Branch;
import com.treelearn.core.domain.BranchAnchor;
import com.treelearn.core.domain.BranchSource;
import com.treelearn.core.domain.ContextSnapshot;
import com.treelearn.core.domain.GenerationModel;
import com.treelearn.core.domain.GenerationState;
import com.treelearn.web.api.dto.ApiJson;
import com.treelearn.web.credential.SecretPort;
import com.treelearn.web.generation.DesktopGenerationGateway;
import com.treelearn.web.storage.AnswerRevisionStore;
import com.treelearn.web.storage.BranchStore;
import com.treelearn.web.storage.DesktopSchema;
import com.treelearn.web.storage.JdbcSupport;
import com.treelearn.web.storage.MessageStore;
import com.treelearn.web.storage.OperationStore;
import com.treelearn.web.storage.SnapshotStore;
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
 * T-2026-10-10-042：分支生命周期与删除端点（契约 {@code web/contracts/api.md} §6.2/§6.3）。
 *
 * <p>真实 HTTP + 真 SQLite + 桩网关：覆盖根分支归档拒绝、归档/恢复状态冲突、归档不取消生成、折叠持久化、
 * {@code seen} 只前进、阅读位置写入/读回/锚点删除置空、删除预览范围（含子树与归档分支）与 digest、
 * digest 过期拒绝、主题删除先取消活跃操作、重复删除幂等、子树删除只删本子树。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class BranchLifecycleEndpointsTest {

    private static final long T0 = 1_700_000_000_000L;
    private static final String POLICY = "tl-learning-answer/1.1.0";

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
    @Autowired StubGateway gateway;

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    // ---------------------------------------------------------------- archive / restore

    @Test
    void rootBranchCannotBeArchivedOrRestored() throws Exception {
        Seed seed = seedStableTopic();

        assertRefusal(post("/api/branches/" + seed.rootBranchId + "/archive", command(uuid())),
                400, "BRANCH_NOT_ARCHIVABLE");
        assertRefusal(post("/api/branches/" + seed.rootBranchId + "/restore", command(uuid())),
                400, "BRANCH_NOT_ARCHIVABLE");
        assertFalse(branches.get(seed.rootBranchId).orElseThrow().isArchived(),
                "a refused archive must not have written anything");
    }

    @Test
    void archiveAndRestoreRefuseIllegalTransitions() throws Exception {
        TopicStore.Created created = topics.create(T0);
        Leaf child = forkChild(created.topic.id, created.rootBranch.id, T0 + 1_000);
        Leaf other = forkChild(created.topic.id, created.rootBranch.id, T0 + 2_000);

        JsonNode archived = json(post("/api/branches/" + child.branchId + "/archive", command(uuid())));
        assertFalse(archived.get("archivedAtMillis").isNull(), "归档后 archivedAtMillis 非空");

        assertRefusal(post("/api/branches/" + child.branchId + "/archive", command(uuid())),
                409, "ILLEGAL_STATE_TRANSITION");
        assertRefusal(post("/api/branches/" + other.branchId + "/restore", command(uuid())),
                409, "ILLEGAL_STATE_TRANSITION");

        JsonNode restored = json(post("/api/branches/" + child.branchId + "/restore", command(uuid())));
        assertTrue(restored.get("archivedAtMillis").isNull(), "恢复后 archivedAtMillis 为空");
        assertFalse(branches.get(child.branchId).orElseThrow().isArchived());
    }

    @Test
    void archivingDoesNotCancelGeneration() throws Exception {
        TopicStore.Created created = topics.create(T0);
        Leaf child = forkChild(created.topic.id, created.rootBranch.id, T0 + 1_000);
        String operationId = insertActiveOperationRow(child.branchId, child.aiMessageId, T0 + 3_000);

        json(post("/api/branches/" + child.branchId + "/archive", command(uuid())));

        assertTrue(operations.byId(operationId).orElseThrow().state.isActive(),
                "归档不等于取消生成：活跃操作必须保留");
    }

    // ------------------------------------------------------------------------ collapse

    @Test
    void collapsePersistsAndReadsBackThroughTheTree() throws Exception {
        TopicStore.Created created = topics.create(T0);
        Leaf child = forkChild(created.topic.id, created.rootBranch.id, T0 + 1_000);

        JsonNode collapsed = json(post("/api/branches/" + child.branchId + "/collapse",
                collapseBody(uuid(), true)));
        assertTrue(collapsed.get("collapsed").asBoolean());

        JsonNode tree = json(get("/api/topics/" + created.topic.id + "/branches"));
        JsonNode node = nodeById(tree.get("nodes"), child.branchId);
        assertTrue(node.get("collapsed").asBoolean(), "折叠状态必须持久化并经树读回");
    }

    // ---------------------------------------------------------------------------- seen

    @Test
    void seenOnlyMovesForward() throws Exception {
        TopicStore.Created created = topics.create(T0);
        String rootId = created.rootBranch.id;
        branches.incrementLatestStable(rootId, T0 + 1);
        branches.incrementLatestStable(rootId, T0 + 2);

        JsonNode seen = json(post("/api/branches/" + rootId + "/seen", seenBody(uuid(), 2)));
        assertEquals(2, seen.get("lastSeenStableSequence").asInt());
        assertEquals(0, seen.get("unreadCount").asInt());

        assertRefusal(post("/api/branches/" + rootId + "/seen", seenBody(uuid(), 1)),
                409, "SEQUENCE_OUT_OF_ORDER");
        assertRefusal(post("/api/branches/" + rootId + "/seen", seenBody(uuid(), 3)),
                409, "SEQUENCE_OUT_OF_ORDER");
        assertEquals(2, branches.get(rootId).orElseThrow().lastSeenStableSequence(),
                "被拒的已见标记不得写入");
    }

    // --------------------------------------------------------------- reading position

    @Test
    void readingPositionRoundTripsAndClearsWithItsMessage() throws Exception {
        Seed seed = seedStableTopic();

        JsonNode saved = json(put("/api/branches/" + seed.rootBranchId + "/reading-position",
                readingBody(uuid(), seed.userMessageId, null, 120, 8)));
        assertEquals(seed.rootBranchId, saved.get("branchId").asText());
        assertEquals(seed.userMessageId, saved.get("readingAnchor").get("messageId").asText());
        assertEquals(120, saved.get("readingAnchor").get("listItemOffset").asInt());

        JsonNode conversation = json(get("/api/branches/" + seed.rootBranchId + "/messages"));
        assertEquals(seed.userMessageId,
                conversation.get("readingAnchor").get("messageId").asText());

        deleteMessage(seed.userMessageId);
        JsonNode after = json(get("/api/branches/" + seed.rootBranchId + "/messages"));
        assertTrue(after.get("readingAnchor").isNull(),
                "锚点消息删除后（ON DELETE SET NULL）不得再有阅读位置");
    }

    // ---------------------------------------------------------------- deletion preview

    @Test
    void subtreePreviewCoversDescendantsIncludingArchived() throws Exception {
        TopicStore.Created created = topics.create(T0);
        Leaf parent = forkChild(created.topic.id, created.rootBranch.id, T0 + 1_000);
        json(post("/api/branches/" + parent.branchId + "/archive", command(uuid())));
        Leaf grandchild = forkChild(created.topic.id, parent.branchId, T0 + 2_000);

        JsonNode preview = json(get("/api/branches/" + parent.branchId + "/deletion-preview"));
        assertFalse(preview.get("wholeTopic").asBoolean());
        assertEquals(created.topic.id, preview.get("topicId").asText());
        assertEquals(parent.branchId, preview.get("targetBranchId").asText());

        List<String> ids = textList(preview.get("branchIds"));
        assertEquals(2, ids.size(), "子树范围 = 目标 + 后代");
        assertEquals(parent.branchId, ids.get(0), "父必须在子之前");
        assertTrue(ids.contains(grandchild.branchId));
        assertEquals(2, preview.get("branchCount").asInt());
        assertFalse(preview.get("digest").asText().isBlank());
    }

    @Test
    void wholeTopicPreviewMarksTheRootAsWholeTopic() throws Exception {
        TopicStore.Created created = topics.create(T0);
        forkChild(created.topic.id, created.rootBranch.id, T0 + 1_000);

        JsonNode preview = json(get("/api/branches/" + created.rootBranch.id + "/deletion-preview"));
        assertTrue(preview.get("wholeTopic").asBoolean(), "根分支删除等价整树删除");
        assertEquals(2, preview.get("branchCount").asInt());
    }

    // -------------------------------------------------------------------- delete flow

    @Test
    void staleDigestIsRefusedAndNothingIsDeleted() throws Exception {
        TopicStore.Created created = topics.create(T0);
        Leaf parent = forkChild(created.topic.id, created.rootBranch.id, T0 + 1_000);
        String staleDigest = json(get("/api/branches/" + parent.branchId + "/deletion-preview"))
                .get("digest").asText();

        forkChild(created.topic.id, parent.branchId, T0 + 2_000);

        assertRefusal(post("/api/branches/" + parent.branchId + "/delete",
                deleteBody(uuid(), staleDigest)), 409, "DELETION_PLAN_EXPIRED");
        assertTrue(branches.get(parent.branchId).isPresent(), "digest 过期时不得半删");
    }

    @Test
    void topicDeleteCancelsActiveOperationThenDeletes() throws Exception {
        JsonNode bootstrap = json(post("/api/topics",
                topicBody(uuid(), "光合作用是什么？", "deepseek-flash", false)));
        String topicId = bootstrap.get("topic").get("id").asText();
        String operationId = bootstrap.get("operation").get("id").asText();
        assertTrue(operations.byId(operationId).orElseThrow().state.isActive());

        String digest = json(get("/api/topics/" + topicId + "/deletion-preview")).get("digest").asText();

        JsonNode deleted = json(post("/api/topics/" + topicId + "/delete", deleteBody(uuid(), digest)));
        assertEquals(topicId, deleted.get("deletedTopicId").asText());
        assertEquals(1, deleted.get("deletedBranchIds").size());

        assertFalse(gateway.calls.isEmpty(), "首问已开启一次网关调用");
        assertTrue(gateway.calls.get(0).cancelled, "删除前必须先取消活跃操作（中止流）");
        assertTrue(topics.get(topicId).isEmpty());
        assertFalse(operations.byId(operationId).isPresent(), "操作行随删除级联移除");
    }

    @Test
    void subtreeDeleteRemovesOnlyThatSubtree() throws Exception {
        TopicStore.Created created = topics.create(T0);
        Leaf parent = forkChild(created.topic.id, created.rootBranch.id, T0 + 1_000);
        Leaf grandchild = forkChild(created.topic.id, parent.branchId, T0 + 1_500);
        Leaf sibling = forkChild(created.topic.id, created.rootBranch.id, T0 + 2_000);

        String digest = json(get("/api/branches/" + parent.branchId + "/deletion-preview"))
                .get("digest").asText();
        JsonNode deleted = json(post("/api/branches/" + parent.branchId + "/delete",
                deleteBody(uuid(), digest)));

        assertEquals(2, deleted.get("deletedBranchIds").size());
        assertTrue(branches.get(parent.branchId).isEmpty());
        assertTrue(branches.get(grandchild.branchId).isEmpty());
        assertTrue(branches.get(sibling.branchId).isPresent(), "兄弟子树保留");
        assertTrue(branches.get(created.rootBranch.id).isPresent());
    }

    @Test
    void deletingRootBranchRemovesTheWholeTopic() throws Exception {
        TopicStore.Created created = topics.create(T0);
        Leaf child = forkChild(created.topic.id, created.rootBranch.id, T0 + 1_000);

        String digest = json(get("/api/branches/" + created.rootBranch.id + "/deletion-preview"))
                .get("digest").asText();
        JsonNode deleted = json(post("/api/branches/" + created.rootBranch.id + "/delete",
                deleteBody(uuid(), digest)));

        assertEquals(2, deleted.get("deletedBranchIds").size());
        assertTrue(topics.get(created.topic.id).isEmpty(), "根分支删除等价整树删除");
        assertTrue(branches.get(child.branchId).isEmpty());
    }

    @Test
    void repeatedDeleteAndCommandIdReplayAreIdempotent() throws Exception {
        TopicStore.Created created = topics.create(T0);
        Leaf child = forkChild(created.topic.id, created.rootBranch.id, T0 + 1_000);
        String digest = json(get("/api/branches/" + child.branchId + "/deletion-preview"))
                .get("digest").asText();
        String commandId = uuid();

        JsonNode first = json(post("/api/branches/" + child.branchId + "/delete",
                deleteBody(commandId, digest)));
        assertEquals(1, first.get("deletedBranchIds").size());

        JsonNode replay = json(post("/api/branches/" + child.branchId + "/delete",
                deleteBody(commandId, digest)));
        assertTrue(replay.get("deletedBranchIds").isEmpty(), "目标已不存在视为幂等成功");
        assertTrue(branches.get(child.branchId).isEmpty());
    }

    // -------------------------------------------------------------------- helpers

    private record Seed(String topicId, String rootBranchId, String userMessageId, String aiMessageId,
            String revisionId) {}

    private record Leaf(String branchId, String aiMessageId, String revisionId) {}

    private Seed seedStableTopic() {
        TopicStore.Created created = topics.create(T0);
        String rootId = created.rootBranch.id;
        String user = messages.appendUserMessage(rootId, 0, "光合作用是什么？", "", T0).id;
        String ai = messages.appendAiPosition(rootId, 1, T0).id;
        AnswerRevision revision = revisions.insert(ai, "想", "植物利用光能合成有机物。",
                GenerationModel.FLASH, false, POLICY, T0);
        messages.setActiveRevision(ai, revision.id);
        return new Seed(created.topic.id, rootId, user, ai, revision.id);
    }

    /** 通过生产存储写出一棵可读子分支（分支行 + 出生快照 + 指针），对照 Android {@code BranchCreation}。 */
    private Leaf forkChild(String topicId, String parentBranchId, long now) {
        int ordinal = messages.nextOrdinal(parentBranchId);
        String ai = messages.appendAiPosition(parentBranchId, ordinal, now).id;
        AnswerRevision revision = revisions.insert(ai, "", "answer-" + now, GenerationModel.FLASH, false,
                POLICY, now);
        String snapshotId = UUID.randomUUID().toString();
        BranchSource source = BranchSource.fromOperation(parentBranchId, ai, snapshotId,
                GenerationState.STABLE, revision.id, BranchAnchor.wholeAnswer()).value();
        Branch child = branches.createChild(topicId, source, now);
        ContextSnapshot snapshot = snapshots.insert(child.id,
                List.of(ContextSnapshot.Source.stableRevision(revision.id)), now);
        branches.attachContextSnapshot(child.id, snapshot.id);
        return new Leaf(child.id, ai, revision.id);
    }

    /** 直接种一条活跃操作行（该表自身 store 属 T-011），仅用于“归档不取消生成”的断言。 */
    private String insertActiveOperationRow(String branchId, String messageId, long now) {
        String operationId = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO " + DesktopSchema.GenerationOperations.TABLE + "("
                        + DesktopSchema.GenerationOperations.ID + ", "
                        + DesktopSchema.GenerationOperations.BRANCH_ID + ", "
                        + DesktopSchema.GenerationOperations.MESSAGE_ID + ", "
                        + DesktopSchema.GenerationOperations.COMMAND_ID + ", "
                        + DesktopSchema.GenerationOperations.PROMPT_PURPOSE + ", "
                        + DesktopSchema.GenerationOperations.MODEL + ", "
                        + DesktopSchema.GenerationOperations.THINKING_ENABLED + ", "
                        + DesktopSchema.GenerationOperations.PROMPT_POLICY_VERSION + ", "
                        + DesktopSchema.GenerationOperations.STATE + ", "
                        + DesktopSchema.GenerationOperations.CREATED_AT + ", "
                        + DesktopSchema.GenerationOperations.UPDATED_AT
                        + ") VALUES (?, ?, ?, ?, ?, ?, 0, ?, 'STAGED', ?, ?)",
                s -> {
                    s.setString(1, operationId); s.setString(2, branchId); s.setString(3, messageId);
                    s.setString(4, UUID.randomUUID().toString());
                    s.setString(5, "tl-learning-answer"); s.setString(6, "deepseek-flash");
                    s.setString(7, POLICY); s.setLong(8, now); s.setLong(9, now);
                });
        return operationId;
    }

    private void deleteMessage(String messageId) {
        int deleted = jdbc.update("DELETE FROM " + DesktopSchema.Messages.TABLE
                        + " WHERE " + DesktopSchema.Messages.ID + " = ?",
                s -> s.setString(1, messageId));
        if (deleted != 1) {
            throw new AssertionError("deleting message " + messageId + " removed " + deleted + " rows");
        }
    }

    private static JsonNode nodeById(JsonNode nodes, String branchId) {
        for (JsonNode node : nodes) {
            if (node.get("id").asText().equals(branchId)) {
                return node;
            }
        }
        throw new AssertionError("no branch node for " + branchId);
    }

    private static List<String> textList(JsonNode array) {
        List<String> values = new ArrayList<>();
        for (JsonNode element : array) {
            values.add(element.asText());
        }
        return values;
    }

    private static String command(String commandId) {
        return "{\"commandId\":\"" + commandId + "\"}";
    }

    private static String collapseBody(String commandId, boolean collapsed) {
        return "{\"commandId\":\"" + commandId + "\",\"collapsed\":" + collapsed + "}";
    }

    private static String seenBody(String commandId, int sequence) {
        return "{\"commandId\":\"" + commandId + "\",\"seenThroughSequence\":" + sequence + "}";
    }

    private static String readingBody(String commandId, String messageId, String revisionId,
            int listItemOffset, int viewportOffset) {
        return "{\"commandId\":\"" + commandId + "\",\"messageId\":\"" + messageId
                + "\",\"revisionId\":" + (revisionId == null ? "null" : "\"" + revisionId + "\"")
                + ",\"listItemOffset\":" + listItemOffset + ",\"viewportOffset\":" + viewportOffset + "}";
    }

    private static String deleteBody(String commandId, String digest) {
        return "{\"commandId\":\"" + commandId + "\",\"digest\":\"" + digest + "\"}";
    }

    private static String topicBody(String commandId, String question, String model, boolean thinking) {
        return "{\"commandId\":\"" + commandId + "\",\"question\":\"" + question + "\",\"model\":\""
                + model + "\",\"thinkingEnabled\":" + thinking + "}";
    }

    /** 解析一个期望成功的响应；状态断言在此统一收口，拒绝路径走 {@link #assertRefusal}。 */
    private static JsonNode json(HttpResponse<String> response) throws Exception {
        assertEquals(200, response.statusCode(), response.request().method() + " "
                + response.uri().getPath() + " -> " + response.body());
        return ApiJson.mapper().readTree(response.body());
    }

    private HttpResponse<String> get(String path) throws Exception {
        return send("GET", path, null);
    }

    private HttpResponse<String> post(String path, String body) throws Exception {
        return send("POST", path, body);
    }

    private HttpResponse<String> put(String path, String body) throws Exception {
        return send("PUT", path, body);
    }

    private HttpResponse<String> send(String method, String path, String body) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(base() + path))
                .header("Content-Type", "application/json; charset=UTF-8");
        HttpRequest request = body == null
                ? builder.method(method, HttpRequest.BodyPublishers.noBody()).build()
                : builder.method(method,
                        HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8)).build();
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> rawPost(String path, String body) throws Exception {
        return send("POST", path, body);
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

    /** 只记录调用、不自动推进的网关：删除路径据此断言“先取消活跃操作”。 */
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
