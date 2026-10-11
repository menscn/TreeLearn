package com.treelearn.web.api;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.treelearn.core.domain.GenerationModel;
import com.treelearn.web.api.dto.ApiJson;
import com.treelearn.web.storage.AnswerRevisionStore;
import com.treelearn.web.storage.DesktopSchema;
import com.treelearn.web.storage.JdbcSupport;
import com.treelearn.web.storage.MessageStore;
import com.treelearn.web.storage.TopicStore;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
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
 * T-2026-10-10-047：批注端点（契约 {@code web/contracts/api.md} §6.10、§5.8）。
 *
 * <p>真实 HTTP + 真 SQLite：覆盖 digest 失效自愈读取（删除不重定位）、HIGHLIGHT 相交合并与
 * absorbed 清除、NOTE 非空校验、区间畸形拒绝、DELETE 幂等、消息不存在 404、
 * 生成中的 AI 消息拒绝写入（`OPERATION_NOT_STABLE`）。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AnnotationEndpointsTest {

    private static final long T0 = 1_700_000_000_000L;
    private static final String POLICY = "tl-learning-answer/1.1.0";
    private static final String ANSWER = "光合作用是植物利用光能把二氧化碳和水合成有机物的过程。";

    @TempDir
    static Path dataDir;

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("treelearn.data-dir", () -> dataDir.toString().replace('\\', '/'));
    }

    @LocalServerPort int port;
    @Autowired JdbcSupport jdbc;
    @Autowired TopicStore topics;
    @Autowired MessageStore messages;
    @Autowired AnswerRevisionStore revisions;

    private final HttpClient http = HttpClient.newHttpClient();

    @BeforeEach
    void resetStore() {
        jdbc.update("DELETE FROM " + DesktopSchema.Topics.TABLE, JdbcSupport.NO_PARAMS);
    }

    // ------------------------------------------------------------------- cases

    @Test
    void missingMessageIs404() throws Exception {
        assertRefusal(getRaw("/api/messages/nope/annotations"), 404, "NOT_FOUND");
    }

    @Test
    void emptyMessageHasNoAnnotations() throws Exception {
        String ai = stableAiMessage("回答甲", T0);
        JsonNode body = get("/api/messages/" + ai + "/annotations");
        assertEquals(ai, body.get("messageId").asText());
        assertEquals(0, body.get("annotations").size());
    }

    @Test
    void highlightSurvivesRereadAndClearsWhenContentChanges() throws Exception {
        String ai = stableAiMessage(ANSWER, T0);
        postHighlight(ai, 0, 4);
        JsonNode body = get("/api/messages/" + ai + "/annotations");
        assertEquals(1, body.get("annotations").size());
        assertEquals("HIGHLIGHT", body.get("annotations").get(0).get("kind").asText());
        assertEquals(0, body.get("annotations").get(0).get("start").asInt());
        assertEquals(4, body.get("annotations").get(0).get("end").asInt());

        // 内容变化（新 revision）→ 下一次读取按新 digest 自愈清理，不重定位。
        String revised = " completely different answer text";
        var revision = revisions.insert(ai, "", ANSWER + revised, GenerationModel.FLASH, false,
                POLICY, T0 + 60_000);
        messages.setActiveRevision(ai, revision.id);
        JsonNode after = get("/api/messages/" + ai + "/annotations");
        assertEquals(0, after.get("annotations").size(), "digest 失效的批注应被清理");
    }

    @Test
    void intersectingHighlightsMergeIntoUnionWithAbsorbedRemoved() throws Exception {
        String ai = stableAiMessage(ANSWER, T0);
        postHighlight(ai, 0, 4);
        postHighlight(ai, 3, 8);
        JsonNode body = get("/api/messages/" + ai + "/annotations");
        assertEquals(1, body.get("annotations").size(), "相交高亮应合并为并集");
        assertEquals(0, body.get("annotations").get(0).get("start").asInt());
        assertEquals(8, body.get("annotations").get(0).get("end").asInt());
    }

    @Test
    void noteRequiresBody() throws Exception {
        String ai = stableAiMessage(ANSWER, T0);
        HttpResponse<String> response = postRaw("/api/messages/" + ai + "/annotations",
                "{\"commandId\":\"" + uuid() + "\",\"kind\":\"NOTE\",\"container\":\"PROSE\","
                        + "\"proseIndex\":0,\"start\":0,\"end\":4}");
        assertRefusal(response, 400, "NOTE_BODY_REQUIRED");
    }

    @Test
    void noteWithBodyIsStored() throws Exception {
        String ai = stableAiMessage(ANSWER, T0);
        HttpResponse<String> response = postRaw("/api/messages/" + ai + "/annotations",
                "{\"commandId\":\"" + uuid() + "\",\"kind\":\"NOTE\",\"container\":\"PROSE\","
                        + "\"proseIndex\":0,\"start\":0,\"end\":4,\"body\":\"重点\"}");
        assertEquals(200, response.statusCode(), response.body());
        JsonNode body = ApiJson.mapper().readTree(response.body());
        assertEquals(1, body.get("annotations").size());
        assertEquals("重点", body.get("annotations").get(0).get("body").asText());
    }

    @Test
    void invalidRangeIsRejected() throws Exception {
        String ai = stableAiMessage(ANSWER, T0);
        HttpResponse<String> response = postRaw("/api/messages/" + ai + "/annotations",
                "{\"commandId\":\"" + uuid() + "\",\"kind\":\"HIGHLIGHT\",\"container\":\"PROSE\","
                        + "\"proseIndex\":0,\"start\":8,\"end\":4}");
        assertRefusal(response, 400, "ANCHOR_INVALID");
    }

    @Test
    void deleteIsIdempotent() throws Exception {
        String ai = stableAiMessage(ANSWER, T0);
        postHighlight(ai, 0, 4);
        String id = get("/api/messages/" + ai + "/annotations").get("annotations").get(0)
                .get("id").asText();
        assertEquals(200, deleteRaw("/api/annotations/" + id).statusCode());
        assertEquals(0, get("/api/messages/" + ai + "/annotations").get("annotations").size());
        // 再删一次：幂等成功。
        assertEquals(200, deleteRaw("/api/annotations/" + id).statusCode());
    }

    @Test
    void streamingAiMessageRefusesWrite() throws Exception {
        String ai = streamingAiMessage(T0);
        HttpResponse<String> response = postRaw("/api/messages/" + ai + "/annotations",
                "{\"commandId\":\"" + uuid() + "\",\"kind\":\"HIGHLIGHT\",\"container\":\"PROSE\","
                        + "\"proseIndex\":0,\"start\":0,\"end\":4}");
        assertRefusal(response, 409, "OPERATION_NOT_STABLE");
    }

    // ----------------------------------------------------------------- helpers

    /** Stable AI 消息：一条 AI 行 + active revision，返回消息 id。 */
    private String stableAiMessage(String answer, long now) {
        var created = topics.create(now);
        String root = created.rootBranch.id;
        messages.appendUserMessage(root, 0, "问题", "", now);
        String ai = messages.appendAiPosition(root, 1, now + 1).id;
        var revision = revisions.insert(ai, "", answer, GenerationModel.FLASH, false, POLICY,
                now + 2);
        messages.setActiveRevision(ai, revision.id);
        return ai;
    }

    /** 生成中的 AI 消息：有行、无 active revision（digest 不可得 → 拒绝写入）。 */
    private String streamingAiMessage(long now) {
        var created = topics.create(now + 120_000);
        String root = created.rootBranch.id;
        messages.appendUserMessage(root, 0, "问题", "", now + 120_000);
        return messages.appendAiPosition(root, 1, now + 120_001).id;
    }

    private void postHighlight(String messageId, int start, int end) throws Exception {
        HttpResponse<String> response = postRaw("/api/messages/" + messageId + "/annotations",
                "{\"commandId\":\"" + uuid() + "\",\"kind\":\"HIGHLIGHT\",\"container\":\"PROSE\","
                        + "\"proseIndex\":0,\"start\":" + start + ",\"end\":" + end + "}");
        assertEquals(200, response.statusCode(), response.body());
    }

    private String displayedText(String messageId) {
        var message = messages.get(messageId).orElseThrow();
        if ("USER".equals(message.role.name())) {
            return message.userContent;
        }
        return revisions.findActiveForMessage(messageId).orElseThrow().answerContent;
    }

    /** 与控制器同源：显示文本的 SHA-256 hex（测试用它断言 digest 过滤边界）。 */
    private String digestOf(String messageId) throws Exception {
        byte[] hash = MessageDigest.getInstance("SHA-256")
                .digest(displayedText(messageId).getBytes(StandardCharsets.UTF_8));
        StringBuilder hex = new StringBuilder(hash.length * 2);
        for (byte b : hash) {
            hex.append(String.format("%02x", b));
        }
        return hex.toString();
    }

    private static String uuid() {
        return java.util.UUID.randomUUID().toString();
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

    private HttpResponse<String> postRaw(String path, String json) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create(base() + path))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8)).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> deleteRaw(String path) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create(base() + path))
                        .DELETE().build(),
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
