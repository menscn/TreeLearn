package com.treelearn.web.api.dto;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * 严格未知字段测试（任务 T-2026-10-10-025）。
 *
 * <p>契约守卫要求契约漂移可见：DTO 层必须拒绝未知字段，而不是静默忽略。合法 JSON 仍必须通过，
 * 以证明拒绝来自“多了一个字段”而不是解析器拒绝一切。
 */
class StrictnessTest {

    private static final JsonMapper MAPPER = ApiJson.mapper();

    @Test
    void anUnknownFieldMakesTheDeserializationFailSoContractDriftIsVisible() {
        String drifted = """
                {"id":"6f3a1c2b-0d4e-4a5f-9b6c-7d8e9f0a1b2c","lastVisitedBranchId":null,
                 "createdAtMillis":1700000000000,"updatedAtMillis":1700000000000,"unexpectedField":1}""";
        assertThrows(JacksonException.class,
                () -> MAPPER.readValue(drifted, TopicDtos.Topic.class));
    }

    @Test
    void theSameJsonWithoutTheExtraFieldIsAccepted() {
        String aligned = """
                {"id":"6f3a1c2b-0d4e-4a5f-9b6c-7d8e9f0a1b2c","lastVisitedBranchId":null,
                 "createdAtMillis":1700000000000,"updatedAtMillis":1700000000000}""";
        assertDoesNotThrow(() -> MAPPER.readValue(aligned, TopicDtos.Topic.class));
    }
}
