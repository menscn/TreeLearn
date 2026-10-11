package com.treelearn.web.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import com.treelearn.web.api.dto.ApiJson;
import tools.jackson.databind.JsonNode;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.treelearn.web.storage.JdbcSupport;

/**
 * T-2026-10-10-054：附件端点（契约 §6.9）。真实 HTTP + 真 SQLite + 真文件系统：
 * 内容寻址去重（首个 displayName 保留）、20 MiB 上限（测试注入小上限）、类型白名单、
 * 列表含物理/引用事实、content 端点字节与缺失 404、DELETE 引用保留缺失标记、
 * 孤儿清理幂等（重跑安全）。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AttachmentEndpointsTest {

    private static final long T0 = 1_700_000_000_000L;

    @TempDir
    static Path dataDir;

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("treelearn.data-dir", () -> dataDir.toString().replace('\\', '/'));
        // 测试用小上限（契约上限 20 MiB 的判定同一处，注入只影响数值）。
        registry.add("treelearn.attachments.max-bytes", () -> "16");
    }

    @LocalServerPort int port;
    @Autowired JdbcSupport jdbc;
    @Autowired com.treelearn.web.storage.TopicStore topics;
    @Autowired com.treelearn.web.storage.MessageStore messages;
    @Autowired com.treelearn.web.storage.AnswerRevisionStore revisions;

    private final HttpClient http = HttpClient.newHttpClient();

    @BeforeEach
    void resetStore() {
        jdbc.update("DELETE FROM message_attachments", s -> {});
        jdbc.update("DELETE FROM attachments", s -> {});
    }

    private String base() { return "http://127.0.0.1:" + port; }

    /** 真实 FK 目标：一条稳定 AI 消息（047 同款 seed）。 */
    private String stableAiMessage(long now) {
        var created = topics.create(now);
        String root = created.rootBranch.id;
        messages.appendUserMessage(root, 0, "问题", "", now);
        String ai = messages.appendAiPosition(root, 1, now + 1).id;
        var revision = revisions.insert(ai, "", "答案", com.treelearn.core.domain.GenerationModel.FLASH,
                false, "tl-learning-answer/1.1.0", now + 2);
        messages.setActiveRevision(ai, revision.id);
        return ai;
    }

    private HttpResponse<String> send(String method, String path) throws Exception {
        return send(method, path, null);
    }

    private HttpResponse<String> send(String method, String path, String body) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(base() + path));
        HttpRequest request = (body == null
                ? builder.GET()
                : builder.header("Content-Type", "application/json")
                        .method(method, HttpRequest.BodyPublishers.ofString(body))).build();
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private String b64(String text) {
        return Base64.getEncoder().encodeToString(text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private String upload(String displayName, String mimeType, String contentB64) throws Exception {
        HttpResponse<String> response = send("POST", "/api/attachments",
                "{\"commandId\":\"" + UUID.randomUUID() + "\",\"displayName\":\"" + displayName
                        + "\",\"mimeType\":\"" + mimeType + "\",\"contentBase64\":\"" + contentB64 + "\"}");
        assertEquals(200, response.statusCode(), "POST /api/attachments -> " + response.body());
        return ApiJson.mapper().readTree(response.body()).get("attachmentId").asText();
    }

    @Test
    void registerReturnsAttachmentShapeAndDedupesByContent() throws Exception {
        String first = upload("notes.txt", "text/plain", b64("笔记"));
        String second = upload("另一份.txt", "text/plain", b64("笔记"));
        assertEquals(first, second, "same bytes must resolve to the existing record");
        assertTrue(first.matches("[0-9a-f-]{36}"), first);
    }

    @Test
    void oversizedUploadIsRefusedWithMaxBytes() throws Exception {
        HttpResponse<String> response = send("POST", "/api/attachments",
                "{\"commandId\":\"" + UUID.randomUUID() + "\",\"displayName\":\"big.txt\","
                        + "\"mimeType\":\"text/plain\",\"contentBase64\":\"" + b64("12345678901234567890") + "\"}");
        assertEquals(400, response.statusCode(), response.body());
        JsonNode error = ApiJson.mapper().readTree(response.body());
        assertEquals("ATTACHMENT_TOO_LARGE", error.get("error").get("code").asText());
        assertEquals(16, error.get("error").get("params").get("maxBytes").asInt());
    }

    @Test
    void unsupportedMimeIsRefused() throws Exception {
        HttpResponse<String> response = send("POST", "/api/attachments",
                "{\"commandId\":\"" + UUID.randomUUID() + "\",\"displayName\":\"x.exe\","
                        + "\"mimeType\":\"application/x-msdownload\",\"contentBase64\":\"" + b64("MZ") + "\"}");
        assertEquals(400, response.statusCode(), response.body());
        assertEquals("ATTACHMENT_TYPE_UNSUPPORTED",
                ApiJson.mapper().readTree(response.body()).get("error").get("code").asText());
    }

    @Test
    void listCarriesPhysicalAndReferenceFacts() throws Exception {
        String id = upload("notes.txt", "text/plain", b64("列表"));
        HttpResponse<String> response = send("GET", "/api/attachments");
        assertEquals(200, response.statusCode(), response.body());
        JsonNode list = ApiJson.mapper().readTree(response.body());
        assertEquals(1, list.get("attachments").size());
        JsonNode entry = list.get("attachments").get(0);
        assertEquals(id, entry.get("attachmentId").asText());
        assertEquals("notes.txt", entry.get("displayName").asText());
        assertTrue(entry.get("present").asBoolean(), "physical file must exist after register");
        assertEquals(0, entry.get("referenceCount").asInt());
        assertEquals(entry.get("sizeBytes").asLong(), list.get("usageBytes").asLong());
    }

    @Test
    void contentEndpointServesRawBytesAnd404WhenFileMissing() throws Exception {
        String id = upload("notes.txt", "text/plain", b64("字节"));
        HttpResponse<String> content = send("GET", "/api/attachments/" + id + "/content");
        assertEquals(200, content.statusCode());
        assertEquals("text/plain", content.headers().firstValue("Content-Type").orElse(""));
        assertEquals("字节", content.body());

        // 物理缺失：手删文件后 content → 404（引用保留缺失标记，行不动）。
        try (var files = Files.list(dataDir.resolve("attachments"))) {
            for (Path body : files.toList()) {
                Files.delete(body);
            }
        }
        HttpResponse<String> missing = send("GET", "/api/attachments/" + id + "/content");
        assertEquals(404, missing.statusCode(), missing.body());
        HttpResponse<String> list = send("GET", "/api/attachments");
        JsonNode entry = ApiJson.mapper().readTree(list.body()).get("attachments").get(0);
        assertEquals(false, entry.get("present").asBoolean(), "row survives with missing marker");
    }

    @Test
    void deleteRemovesRecordAndKeepsReferenceRows() throws Exception {
        String id = upload("notes.txt", "text/plain", b64("删除"));
        String messageId = stableAiMessage(T0);
        jdbc.update("INSERT INTO message_attachments (id, message_id, attachment_id, created_at) VALUES (?, ?, ?, ?)",
                s -> { s.setString(1, UUID.randomUUID().toString()); s.setString(2, messageId);
                       s.setString(3, id); s.setLong(4, T0); });
        HttpResponse<String> response = send("DELETE", "/api/attachments/" + id,
                "{\"commandId\":\"" + UUID.randomUUID() + "\"}");
        assertEquals(200, response.statusCode(), response.body());
        assertEquals(id, ApiJson.mapper().readTree(response.body()).get("deletedAttachmentId").asText());
        Integer refs = jdbc.queryOne(
                "SELECT COUNT(*) AS n FROM message_attachments WHERE attachment_id = ?",
                s -> s.setString(1, id),
                rs -> rs.getInt("n")).orElse(0);
        assertEquals(1, refs, "reference rows must survive the delete (missing marker)");
        HttpResponse<String> second = send("DELETE", "/api/attachments/" + id,
                "{\"commandId\":\"" + UUID.randomUUID() + "\"}");
        assertEquals(200, second.statusCode(), "idempotent delete");
    }

    @Test
    void cleanupRemovesOrphansIdempotently() throws Exception {
        upload("孤.txt", "text/plain", b64("无引用"));
        String referenced = upload("引.txt", "text/plain", b64("有引用"));
        String messageId = stableAiMessage(T0);
        jdbc.update("INSERT INTO message_attachments (id, message_id, attachment_id, created_at) VALUES (?, ?, ?, ?)",
                s -> { s.setString(1, UUID.randomUUID().toString()); s.setString(2, messageId);
                       s.setString(3, referenced); s.setLong(4, T0); });
        HttpResponse<String> first = send("POST", "/api/files/cleanup",
                "{\"commandId\":\"" + UUID.randomUUID() + "\"}");
        assertEquals(200, first.statusCode(), first.body());
        assertEquals(1, ApiJson.mapper().readTree(first.body()).get("cleaned").asInt());
        HttpResponse<String> second = send("POST", "/api/files/cleanup",
                "{\"commandId\":\"" + UUID.randomUUID() + "\"}");
        assertEquals(0, ApiJson.mapper().readTree(second.body()).get("cleaned").asInt(), "rerun safe");
        HttpResponse<String> list = send("GET", "/api/attachments");
        assertEquals(1, ApiJson.mapper().readTree(list.body()).get("attachments").size(),
                "referenced record survives cleanup");
    }
}
