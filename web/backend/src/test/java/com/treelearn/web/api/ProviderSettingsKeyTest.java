package com.treelearn.web.api;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.treelearn.web.api.dto.ApiJson;
import com.treelearn.web.credential.SecretPort;
import com.treelearn.web.storage.DesktopSchema;
import com.treelearn.web.storage.JdbcSupport;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.JsonNode;

/**
 * T-2026-10-10-036: Key 保存走**真实 Windows 凭据管理器**（生产 `WindowsCredentialSecretPort`，无 stub）。
 *
 * <p>每个用例用唯一 provider（随机名/UUID），`@AfterEach` 删除它创建的凭据别名并复核无残留。断言 Key
 * 落盘后列表 `hasKey=true`、响应与（捕获的）日志均不含 canary 原文；凭据可原样读回。
 */
@SpringBootTest
class ProviderSettingsKeyTest {

    private static final String KEY_CANARY = "sk-canary-036-real-credential-do-not-leak";

    @TempDir
    static Path dataDir;

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("treelearn.data-dir", () -> dataDir.toString().replace('\\', '/'));
    }

    @Autowired WebApplicationContext context;
    @Autowired JdbcSupport jdbc;
    @Autowired SecretPort secrets;

    MockMvc mvc;
    private final List<String> createdTargets = new ArrayList<>();

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @AfterEach
    void cleanupEveryCredentialThisTestCreated() {
        for (String target : createdTargets) {
            secrets.delete(target);
        }
        for (String target : createdTargets) {
            assertFalse(secrets.has(target), "残留凭据未被清理：" + target);
        }
    }

    @Test
    void aSavedKeyIsStoredInWindowsCredentialsAndNeverEchoed() throws Exception {
        String id = createProvider("真凭据提供商");
        String target = SecretPort.providerTarget(id);
        createdTargets.add(target);

        Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        root.addAppender(appender);
        String responseBody;
        try {
            responseBody = mvc.perform(put("/api/providers/" + id + "/key")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"commandId\":\"" + UUID.randomUUID() + "\",\"apiKey\":\""
                                    + KEY_CANARY + "\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.hasKey").value(true))
                    .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        } finally {
            root.detachAppender(appender);
        }

        assertFalse(responseBody.contains(KEY_CANARY), "保存响应不得回显 Key 明文");
        assertTrue(secrets.has(target), "Key 必须已落 Windows 凭据管理器");
        Optional<char[]> stored = secrets.read(target);
        assertTrue(stored.isPresent());
        assertArrayEquals(KEY_CANARY.toCharArray(), stored.orElseThrow(), "凭据必须原样读回");

        JsonNode list = json(mvc.perform(get("/api/providers")).andExpect(status().isOk()).andReturn());
        JsonNode row = findById(list, id);
        assertTrue(row.get("hasKey").asBoolean(), "列表 hasKey 必须为真");
        String listBody = list.toString();
        assertFalse(listBody.contains(KEY_CANARY), "列表响应不得含 Key 明文");
        assertTrue(appender.list.stream()
                        .noneMatch(event -> event.getFormattedMessage().contains(KEY_CANARY)),
                "日志不得含 Key 明文");
    }

    // ------------------------------------------------------------------ helpers

    private String createProvider(String name) throws Exception {
        jdbc.update("DELETE FROM " + DesktopSchema.Providers.TABLE + " WHERE "
                + DesktopSchema.Providers.NAME + " = ?", s -> s.setString(1, name));
        JsonNode created = json(mvc.perform(post("/api/providers")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"commandId\":\"" + UUID.randomUUID() + "\",\"name\":\"" + name
                        + "\",\"baseUrl\":\"https://credential.example\"}"))
                .andExpect(status().isOk()).andReturn());
        return created.get("id").asText();
    }

    private static JsonNode json(org.springframework.test.web.servlet.MvcResult result) throws Exception {
        return ApiJson.mapper().readTree(
                result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private static JsonNode findById(JsonNode array, String id) {
        for (JsonNode node : array) {
            if (id.equals(node.get("id").asText())) {
                return node;
            }
        }
        throw new AssertionError("provider not found in list: " + id);
    }
}
