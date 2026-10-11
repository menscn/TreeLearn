package com.treelearn.web.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.treelearn.core.domain.BranchAnchor;
import com.treelearn.core.domain.ContextSnapshot;
import com.treelearn.core.domain.GenerationModel;
import com.treelearn.core.domain.GenerationState;
import com.treelearn.core.domain.BranchSource;
import com.treelearn.core.domain.PromptPurpose;
import com.treelearn.web.api.dto.ApiJson;
import com.treelearn.web.storage.AnswerRevisionStore;
import com.treelearn.web.storage.ArtifactStore;
import com.treelearn.web.storage.BranchStore;
import com.treelearn.web.storage.DesktopSchema;
import com.treelearn.web.storage.JdbcSupport;
import com.treelearn.web.storage.MessageStore;
import com.treelearn.web.storage.SnapshotStore;
import com.treelearn.web.storage.TopicStore;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.JsonNode;

/**
 * T-2026-10-10-045：C 树图布局端点（契约 {@code web/contracts/api.md} §6.11）。
 *
 * <p>真实 HTTP + 真 SQLite + 固定 UTC 时钟：覆盖 404、scale 校验、空布局、单根、子树几何、
 * 当前路径加亮、密度层级、summary artifact、折叠 BUD、归档子树离场、根被归档的 fail-closed。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class TreeLayoutEndpointsTest {

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
    @Autowired ArtifactStore artifacts;
    @Autowired SnapshotStore snapshots;

    private final HttpClient http = HttpClient.newHttpClient();

    @BeforeEach
    void resetStore() {
        jdbc.update("DELETE FROM " + DesktopSchema.Topics.TABLE, JdbcSupport.NO_PARAMS);
    }

    // ------------------------------------------------------------------- cases

    @Test
    void missingTopicIs404() throws Exception {
        assertRefusal(getRaw("/api/topics/nope/tree-layout"), 404, "NOT_FOUND");
    }

    @Test
    void scaleMustBePositiveAndFinite() throws Exception {
        Seed seed = seedTopic("根", "问题", "回答", null, T0);
        String base = "/api/topics/" + seed.topicId() + "/tree-layout";
        for (String bad : new String[] {"0", "-1", "abc", "Infinity", "NaN"}) {
            assertRefusal(getRaw(base + "?scale=" + bad), 400, "SCALE_INVALID");
        }
    }

    @Test
    void topicWithoutRootRowsReturnsEmptyLayout() throws Exception {
        Seed seed = seedTopic("根", "问题", "回答", null, T0);
        jdbc.update("DELETE FROM " + DesktopSchema.Branches.TABLE, JdbcSupport.NO_PARAMS);
        JsonNode body = get("/api/topics/" + seed.topicId() + "/tree-layout");
        assertEquals(0, body.get("branches").size());
        assertEquals(0, body.get("labels").size());
        assertEquals(0, body.get("hits").size());
        assertEquals(0.0, body.get("bounds").get("minX").asDouble());
    }

    @Test
    void singleRootIsLeafWithRounds() throws Exception {
        Seed seed = seedTopic("根", "问题一", "回答", null, T0);
        appendUser(seed.rootBranchId(), messages.nextOrdinal(seed.rootBranchId()), "问题二", T0 + 1);
        JsonNode body = get("/api/topics/" + seed.topicId() + "/tree-layout");
        assertEquals(1, body.get("branches").size());
        JsonNode root = body.get("branches").get(0);
        assertEquals(seed.rootBranchId(), root.get("branchId").asText());
        assertEquals("LEAF", root.get("kind").asText());
        assertEquals(0, root.get("depth").asInt());
        assertEquals(2, root.get("rounds").asInt());
        assertTrue(body.get("bounds").get("maxX").asDouble() > body.get("bounds").get("minX").asDouble());
    }

    @Test
    void descendantsGetDepthAndRootBecomesLimb() throws Exception {
        Seed seed = seedTopic("根", "问题", "回答", null, T0);
        String child = addChild(seed.topicId(), seed.rootBranchId(), T0 + 1);
        String grand = addChild(seed.topicId(), child, T0 + 2);
        JsonNode body = get("/api/topics/" + seed.topicId() + "/tree-layout");
        assertEquals(3, body.get("branches").size());
        JsonNode rootNode = node(body, seed.rootBranchId());
        JsonNode childNode = node(body, child);
        JsonNode grandNode = node(body, grand);
        assertEquals("LIMB", rootNode.get("kind").asText());
        assertEquals(0, rootNode.get("depth").asInt());
        assertEquals(1, childNode.get("depth").asInt());
        assertEquals(2, grandNode.get("depth").asInt());
        // 父在子前（装配顺序 = BranchStore.listForTopic 行序）
        assertTrue(indexOf(body, seed.rootBranchId()) < indexOf(body, child));
        assertTrue(indexOf(body, child) < indexOf(body, grand));
    }

    @Test
    void currentPathIsHighlightedOnlyWithinTree() throws Exception {
        Seed seed = seedTopic("根", "问题", "回答", null, T0);
        String child = addChild(seed.topicId(), seed.rootBranchId(), T0 + 1);
        Seed other = seedTopic("别题", "问题", "回答", null, T0 + 60_000);
        JsonNode body = get("/api/topics/" + seed.topicId() + "/tree-layout"
                + "?currentBranchId=" + child);
        for (JsonNode b : body.get("branches")) {
            assertTrue(b.get("onCurrentPath").asBoolean(), b.get("branchId").asText());
        }
        JsonNode outside = get("/api/topics/" + seed.topicId() + "/tree-layout"
                + "?currentBranchId=" + other.rootBranchId());
        // 共享模块语义：current 不在树内时回落到根分支（resolveCurrent→root），
        // 根标记为当前路径，其余分支不在路径上。
        assertTrue(node(outside, seed.rootBranchId()).get("onCurrentPath").asBoolean());
        assertFalse(node(outside, child).get("onCurrentPath").asBoolean());
    }

    @Test
    void densityTierControlsLabels() throws Exception {
        Seed seed = seedTopic("根", "问题", "回答", null, T0);
        addChild(seed.topicId(), seed.rootBranchId(), T0 + 1);
        JsonNode near = get("/api/topics/" + seed.topicId() + "/tree-layout?scale=1.2");
        assertEquals(1, near.get("labels").size(), "near=" + near.toString());
        JsonNode far = get("/api/topics/" + seed.topicId() + "/tree-layout?scale=0.3");
        assertEquals(0, far.get("labels").size());
    }

    @Test
    void summaryArtifactDrivesShowSummary() throws Exception {
        Seed seed = seedTopic("根", "问题", "回答", null, T0);
        String child = addChild(seed.topicId(), seed.rootBranchId(), T0 + 1);
        artifacts.insert(ArtifactStore.Artifact.create("branch_summary", child,
                PromptPurpose.BRANCH_SUMMARY, POLICY,
                "{\"summary\":\"摘要首行\",\"key_points\":[]}", null, "digest-1", T0 + 5));
        JsonNode body = get("/api/topics/" + seed.topicId() + "/tree-layout?scale=1.2");
        JsonNode childLabel = label(body, child);
        assertTrue(childLabel.get("showSummary").asBoolean());
        // 根是 LIMB：placeLabels 只给 LEAF 放盘，LIMB 不应有标签条目。
        for (JsonNode l : body.get("labels")) {
            assertFalse(seed.rootBranchId().equals(l.get("branchId").asText()));
        }
    }

    @Test
    void collapsedBranchWithDescendantsIsBud() throws Exception {
        Seed seed = seedTopic("根", "问题", "回答", null, T0);
        String child = addChild(seed.topicId(), seed.rootBranchId(), T0 + 1);
        addChild(seed.topicId(), child, T0 + 2);
        branches.setCollapsed(child, true, T0 + 3);
        JsonNode body = get("/api/topics/" + seed.topicId() + "/tree-layout");
        JsonNode childNode = node(body, child);
        assertEquals("BUD", childNode.get("kind").asText());
        assertEquals(1, childNode.get("hiddenDescendants").asInt());
    }

    @Test
    void archivedSubtreeLeavesDefaultLayout() throws Exception {
        Seed seed = seedTopic("根", "问题", "回答", null, T0);
        String child = addChild(seed.topicId(), seed.rootBranchId(), T0 + 1);
        String grand = addChild(seed.topicId(), child, T0 + 2);
        branches.archive(child, T0 + 3);
        JsonNode body = get("/api/topics/" + seed.topicId() + "/tree-layout");
        assertEquals(1, body.get("branches").size());
        JsonNode rootNode = node(body, seed.rootBranchId());
        assertEquals("LEAF", rootNode.get("kind").asText(), "body=" + body.toString());
        assertTrue(indexOf(body, seed.rootBranchId()) >= 0);
        assertFalse(body.toString().contains(grand));
    }

    @Test
    void archivedRootIsFailClosed() throws Exception {
        Seed seed = seedTopic("根", "问题", "回答", null, T0);
        jdbc.update("UPDATE " + DesktopSchema.Branches.TABLE + " SET archived_at = ? WHERE id = ?",
                        s -> {
                            s.setLong(1, T0 + 1);
                            s.setString(2, seed.rootBranchId());
                        });
        assertRefusal(getRaw("/api/topics/" + seed.topicId() + "/tree-layout"), 500,
                "STORAGE_REFUSED");
    }

    // ----------------------------------------------------------------- helpers

    private record Seed(String topicId, String rootBranchId) {}

    private Seed seedTopic(String title, String question, String answer, String reasoning, long now) {
        TopicStore.Created created = topics.create(now);
        String rootId = created.rootBranch.id;
        branches.renameTitle(rootId, title, now);
        appendUser(rootId, 0, question, now);
        String ai = messages.appendAiPosition(rootId, 1, now).id;
        var revision = revisions.insert(ai, reasoning == null ? "" : reasoning, answer,
                GenerationModel.FLASH, false, POLICY, now);
        messages.setActiveRevision(ai, revision.id);
        return new Seed(created.topic.id, rootId);
    }

    private String addChild(String topicId, String parentBranchId, long now) {
        // 与 042 相同的 fork 链：BranchSource 构造必须落在一次 STABLE 尝试上。
        String parent = parentBranchId;
        int ordinal = messages.nextOrdinal(parent);
        String ai = messages.appendAiPosition(parent, ordinal, now).id;
        var revision = revisions.insert(ai, "", "answer-" + now, GenerationModel.FLASH, false,
                POLICY, now);
        String snapshotId = UUID.randomUUID().toString();
        BranchSource source = BranchSource.fromOperation(parent, ai, snapshotId,
                GenerationState.STABLE, revision.id, BranchAnchor.wholeAnswer()).value();
        var child = branches.createChild(topicId, source, now);
        ContextSnapshot snapshot = snapshots.insert(child.id,
                List.of(ContextSnapshot.Source.stableRevision(revision.id)), now);
        branches.attachContextSnapshot(child.id, snapshot.id);
        return child.id;
    }

    private String rootOf(String topicId) {
        return branches.listForTopic(topicId).stream()
                .filter(b -> b.parentBranchId == null).findFirst().orElseThrow().id;
    }

    private void appendUser(String branchId, int ordinal, String question, long now) {
        messages.appendUserMessage(branchId, ordinal, question, "", now);
    }

    private static JsonNode node(JsonNode body, String branchId) {
        for (JsonNode b : body.get("branches")) {
            if (branchId.equals(b.get("branchId").asText())) {
                return b;
            }
        }
        throw new AssertionError("branch not in layout: " + branchId);
    }

    private static int indexOf(JsonNode body, String branchId) {
        int i = 0;
        for (JsonNode b : body.get("branches")) {
            if (branchId.equals(b.get("branchId").asText())) {
                return i;
            }
            i++;
        }
        return -1;
    }

    private static JsonNode label(JsonNode body, String branchId) {
        for (JsonNode l : body.get("labels")) {
            if (branchId.equals(l.get("branchId").asText())) {
                return l;
            }
        }
        throw new AssertionError("label not placed: " + branchId);
    }

    private JsonNode get(String path) throws Exception {
        HttpResponse<String> response = getRaw(path);
        assertEquals(200, response.statusCode(), "GET " + path + " -> " + response.body());
        return ApiJson.mapper().readTree(response.body());
    }

    private HttpResponse<String> getRaw(String path) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create(base() + path)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private void assertRefusal(HttpResponse<String> response, int status, String code)
            throws Exception {
        assertEquals(status, response.statusCode(), "body: " + response.body());
        assertEquals(code,
                ApiJson.mapper().readTree(response.body()).get("error").get("code").asText());
    }

    private String base() {
        return "http://127.0.0.1:" + port;
    }
}
