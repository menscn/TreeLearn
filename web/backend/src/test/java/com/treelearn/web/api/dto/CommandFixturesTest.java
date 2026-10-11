package com.treelearn.web.api.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 命令请求体反序列化测试（任务 T-2026-10-10-025）。
 *
 * <p>每个 {@code web/contracts/fixtures/commands/*.json} 的 {@code body} 必须能被对应 §6 请求体记录
 * 在严格未知字段模式下反序列化（漂移即失败）。命令 fixtures 固定 28 个。
 */
class CommandFixturesTest {

    private static final JsonMapper MAPPER = ApiJson.mapper();

    /** 端点路径 → §6 请求体记录类型（同一路径的多个 fixtures 复用同一记录）。 */
    private static final Map<String, Class<?>> REQUESTS = Map.ofEntries(
            Map.entry("/api/topics", RequestDtos.TopicCreateRequest.class),
            Map.entry("/api/topics/{topicId}/delete", RequestDtos.DeletionConfirmRequest.class),
            Map.entry("/api/branches/{branchId}/messages", RequestDtos.BranchMessageCreateRequest.class),
            Map.entry("/api/branches/{branchId}/forks", RequestDtos.BranchForkRequest.class),
            Map.entry("/api/branches/{branchId}/archive", RequestDtos.CommandRequest.class),
            Map.entry("/api/branches/{branchId}/restore", RequestDtos.CommandRequest.class),
            Map.entry("/api/branches/{branchId}/collapse", RequestDtos.BranchCollapseRequest.class),
            Map.entry("/api/branches/{branchId}/seen", RequestDtos.BranchSeenRequest.class),
            Map.entry("/api/branches/{branchId}/reading-position",
                    RequestDtos.ReadingPositionUpdateRequest.class),
            Map.entry("/api/branches/{branchId}/delete", RequestDtos.DeletionConfirmRequest.class),
            Map.entry("/api/operations/{operationId}/cancel", RequestDtos.CommandRequest.class),
            Map.entry("/api/messages/{messageId}/retry", RequestDtos.MessageRegenerateRequest.class),
            Map.entry("/api/messages/{messageId}/reset", RequestDtos.MessageRegenerateRequest.class),
            Map.entry("/api/messages/{messageId}/revisions/{revisionId}/select",
                    RequestDtos.CommandRequest.class),
            Map.entry("/api/messages/{messageId}/annotations", RequestDtos.AnnotationCreateRequest.class),
            Map.entry("/api/annotations/{annotationId}", RequestDtos.CommandRequest.class),
            Map.entry("/api/derived", RequestDtos.DerivedStartRequest.class),
            Map.entry("/api/providers", RequestDtos.ProviderCreateRequest.class),
            Map.entry("/api/providers/{providerId}", RequestDtos.ProviderUpdateRequest.class),
            Map.entry("/api/providers/{providerId}/models", RequestDtos.CommandRequest.class),
            Map.entry("/api/providers/{providerId}/key", RequestDtos.ProviderKeyRequest.class),
            Map.entry("/api/settings/model-roles", RequestDtos.ModelRolesUpdateRequest.class),
            Map.entry("/api/settings/web-search", RequestDtos.WebSearchUpdateRequest.class),
            Map.entry("/api/settings/web-search/keys/{engine}", RequestDtos.WebSearchKeyRequest.class),
            Map.entry("/api/attachments", RequestDtos.AttachmentUploadRequest.class),
            Map.entry("/api/files/cleanup", RequestDtos.CommandRequest.class));

    @Test
    void everyCommandFixtureDeserializesIntoItsRequestRecord() throws IOException {
        List<Path> files = ContractFixtures.jsonFiles("commands");
        assertEquals(28, files.size(),
                "the commands fixtures count changed; the request layer must be re-aligned");

        for (Path file : files) {
            JsonNode envelope = MAPPER.readTree(Files.readString(file, StandardCharsets.UTF_8));
            String path = envelope.get("path").asText();
            Class<?> type = REQUESTS.get(path);
            assertNotNull(type, "no request record is mapped for command path " + path);

            JsonNode body = envelope.get("body");
            assertNotNull(body, "command fixture has no body: " + file.getFileName());
            assertFalse(body.get("commandId").asText().isBlank(),
                    "every command body must carry a commandId: " + file.getFileName());

            Object request = MAPPER.readValue(MAPPER.writeValueAsString(body), type);
            assertNotNull(request, "body did not deserialize: " + file.getFileName());
        }
    }
}
