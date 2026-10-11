package com.treelearn.web.api.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
 * 契约 DTO 往返测试（任务 T-2026-10-10-025）。
 *
 * <p>每个 {@code web/contracts/fixtures/dtos/*.json} 先反序列化为对应记录类型、再序列化，并与原 JSON 做
 * {@link JsonNode} 深度语义等价比较（键集一致、{@code *Millis} 为 number、枚举 token 原样、null 语义按
 * fixtures）。fixtures 数量固定为 28，数量漂移即失败——这就是契约与 DTO 层之间的唯一约束。
 */
class DtoFixturesRoundTripTest {

    private static final JsonMapper MAPPER = ApiJson.mapper();

    /** fixtures 基名 → DTO 记录类型（覆盖 §5 全部形状与响应包裹）。 */
    private static final Map<String, Class<?>> TYPES = Map.ofEntries(
            Map.entry("topic", TopicDtos.Topic.class),
            Map.entry("topic-summary", TopicDtos.TopicSummary.class),
            Map.entry("topic-bootstrap", TopicDtos.TopicBootstrap.class),
            Map.entry("turn-accepted", TopicDtos.TurnAccepted.class),
            Map.entry("message-operation", TopicDtos.MessageOperation.class),
            Map.entry("conversation", TopicDtos.Conversation.class),
            Map.entry("deletion-preview", TopicDtos.DeletionPreview.class),
            Map.entry("branch", BranchDtos.Branch.class),
            Map.entry("branch-tree", BranchDtos.BranchTree.class),
            Map.entry("fork-result", BranchDtos.ForkResult.class),
            Map.entry("reading-anchor", BranchDtos.ReadingAnchor.class),
            Map.entry("message", MessageDtos.Message.class),
            Map.entry("answer-revision", MessageDtos.AnswerRevision.class),
            Map.entry("annotation", MessageDtos.Annotation.class),
            Map.entry("annotation-list", MessageDtos.AnnotationListResponse.class),
            Map.entry("attachment", AttachmentDtos.Attachment.class),
            Map.entry("attachment-list", AttachmentDtos.AttachmentListResponse.class),
            Map.entry("operation", OperationDtos.Operation.class),
            Map.entry("derived-artifact", OperationDtos.DerivedArtifact.class),
            Map.entry("provider", ConfigDtos.Provider.class),
            Map.entry("model", ConfigDtos.Model.class),
            Map.entry("deleted-provider", ConfigDtos.DeletedProvider.class),
            Map.entry("models-refreshed", ConfigDtos.ModelsRefreshed.class),
            Map.entry("web-search-settings", ConfigDtos.WebSearchSettings.class),
            Map.entry("model-roles", ConfigDtos.ModelRoles.class),
            Map.entry("model-resolution", ConfigDtos.ModelResolution.class),
            Map.entry("search-result", SearchStatsDtos.SearchResult.class),
            Map.entry("stats", SearchStatsDtos.Stats.class));

    @Test
    void everyDtoFixtureRoundTripsToTheSameJson() throws IOException {
        List<Path> files = ContractFixtures.jsonFiles("dtos");
        assertEquals(28, files.size(),
                "the dtos fixtures count changed; the DTO layer must be re-aligned before this passes");
        assertEquals(28, TYPES.size(), "every dtos fixture must have exactly one mapped DTO type");

        for (Path file : files) {
            String name = ContractFixtures.baseName(file);
            Class<?> type = TYPES.get(name);
            assertNotNull(type, "no DTO record is mapped for fixtures/dtos/" + name + ".json");

            String json = Files.readString(file, StandardCharsets.UTF_8);
            JsonNode original = MAPPER.readTree(json);
            Object dto = MAPPER.readValue(json, type);
            JsonNode reparsed = MAPPER.readTree(MAPPER.writeValueAsString(dto));

            assertEquals(original, reparsed,
                    "round-trip changed the JSON for " + file.getFileName());
        }
    }
}
