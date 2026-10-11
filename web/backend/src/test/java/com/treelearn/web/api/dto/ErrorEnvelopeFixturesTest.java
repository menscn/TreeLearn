package com.treelearn.web.api.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 错误封装结构测试（任务 T-2026-10-10-025，契约 §3.1）。
 *
 * <p>每个 {@code web/contracts/fixtures/errors/*.json} 必须反序列化为 {@link ErrorDtos.ApiErrorResponse}：
 * {@code error.code} 为非空字符串（未知码以 String 承载）、{@code error.params} 为对象、{@code error.retryable}
 * 为布尔、{@code requestId} 为字符串；并往返语义等价。错误 fixtures 固定 42 个。
 */
class ErrorEnvelopeFixturesTest {

    private static final JsonMapper MAPPER = ApiJson.mapper();

    @Test
    void everyErrorFixtureRoundTripsThroughTheEnvelope() throws IOException {
        List<Path> files = ContractFixtures.jsonFiles("errors");
        assertEquals(42, files.size(),
                "the errors fixtures count changed; the error vocabulary must be re-aligned");

        for (Path file : files) {
            String json = Files.readString(file, StandardCharsets.UTF_8);
            JsonNode original = MAPPER.readTree(json);
            ErrorDtos.ApiErrorResponse envelope = MAPPER.readValue(json, ErrorDtos.ApiErrorResponse.class);

            assertNotNull(envelope.error(), "error object is required: " + file.getFileName());
            assertFalse(envelope.error().code() == null || envelope.error().code().isBlank(),
                    "error.code must be a non-empty string: " + file.getFileName());
            assertNotNull(envelope.error().params(), "error.params is required (may be empty): " + file);
            assertNotNull(envelope.requestId(), "requestId is required: " + file.getFileName());

            JsonNode reparsed = MAPPER.readTree(MAPPER.writeValueAsString(envelope));
            assertEquals(original, reparsed,
                    "round-trip changed the JSON for " + file.getFileName());
        }
    }
}
