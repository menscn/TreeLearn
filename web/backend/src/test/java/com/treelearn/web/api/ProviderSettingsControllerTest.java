package com.treelearn.web.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sun.net.httpserver.HttpServer;
import com.treelearn.ai.gateway.GatewayPorts;
import com.treelearn.web.api.dto.ApiJson;
import com.treelearn.web.credential.SecretAccessError;
import com.treelearn.web.credential.SecretAccessException;
import com.treelearn.web.credential.SecretPort;
import com.treelearn.web.storage.DesktopSchema;
import com.treelearn.web.storage.JdbcSupport;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.JsonNode;

/**
 * T-2026-10-10-036: Provider/Key 与模型分工端点的形状、错误码与合并语义（契约 §6.6 / §5.9 / §5.12）。
 *
 * <p>不访问外网：模型刷新经注入的 stub {@link GatewayPorts.Transport} 走**本机** JDK {@link HttpServer}
 * 的真 HTTP over socket（真 SSE/socket 层不参与本端点，仅 GET {@code /models}）。凭据端口用内存 stub，
 * Key 的真实 Windows 凭据往返由 {@code ProviderSettingsKeyTest} 单独覆盖。每个用例从空 providers 开始
 * （删除迁移内置种子），断言由本用例写入的行决定。
 */
@SpringBootTest
class ProviderSettingsControllerTest {

    private static final long T0 = 1_752_000_000_000L;
    private static final String KEY_CANARY = "sk-canary-036-must-not-leak";

    @TempDir
    static Path dataDir;

    static HttpServer providerServer;
    static volatile int providerPort;
    static volatile int modelsStatus;
    static volatile String modelsBody;

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("treelearn.data-dir", () -> dataDir.toString().replace('\\', '/'));
    }

    @BeforeAll
    static void startProviderStub() throws IOException {
        providerServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        providerServer.createContext("/models", exchange -> {
            byte[] bytes = modelsBody == null ? new byte[0]
                    : modelsBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
            exchange.sendResponseHeaders(modelsStatus, bytes.length == 0 ? -1 : bytes.length);
            if (bytes.length > 0) {
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(bytes);
                }
            }
            exchange.close();
        });
        providerServer.start();
        providerPort = providerServer.getAddress().getPort();
    }

    @AfterAll
    static void stopProviderStub() {
        providerServer.stop(0);
    }

    @Autowired WebApplicationContext context;
    @Autowired JdbcSupport jdbc;
    @Autowired SecretPort secrets;

    MockMvc mvc;

    @BeforeEach
    void setUp() throws IOException {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
        jdbc.update("DELETE FROM " + DesktopSchema.Providers.TABLE, JdbcSupport.NO_PARAMS);
        ((InMemorySecretPort) secrets).reset();
        Files.deleteIfExists(dataDir.resolve("settings.properties"));
        modelsStatus = 200;
        modelsBody = "{\"data\":[]}";
    }

    // ------------------------------------------------------------------ GET /api/providers

    /** 每项形状必须与共享 fixture provider.json 一致，且只回 hasKey 不回明文。 */
    @Test
    void providersListMatchesTheProviderFixtureShape() throws Exception {
        String id = UUID.randomUUID().toString();
        insertProvider(id, "列表提供商", "https://list.example", true, false);
        insertModel(id, "deepseek-v4-pro");
        secrets.save(SecretPort.providerTarget(id), "k".toCharArray());

        JsonNode array = json(mvc.perform(get("/api/providers")).andExpect(status().isOk()).andReturn());
        assertEquals(1, array.size());
        JsonNode row = array.get(0);
        JsonNode fixture = ApiJson.mapper().readTree(Files.readString(
                Path.of("..", "contracts", "fixtures", "dtos", "provider.json"), StandardCharsets.UTF_8));
        assertEquals(fieldNames(fixture), fieldNames(row), "Provider 键集必须同 fixture");
        assertTrue(row.get("hasKey").asBoolean());
        assertEquals(1, row.get("modelCount").asInt());
        assertTrue(row.get("enabled").asBoolean());
    }

    // ------------------------------------------------------------------ POST /api/providers

    @Test
    void createProviderValidatesNameAndBaseUrl() throws Exception {
        mvc.perform(post("/api/providers").contentType(MediaType.APPLICATION_JSON)
                        .content(createBody("   ", "https://api.example")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("PROVIDER_INVALID"))
                .andExpect(jsonPath("$.error.params.field").value("name"));

        mvc.perform(post("/api/providers").contentType(MediaType.APPLICATION_JSON)
                        .content(createBody("基址非法", "http://api.example")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("PROVIDER_INVALID"))
                .andExpect(jsonPath("$.error.params.field").value("baseUrl"));
    }

    @Test
    void createProviderRefusesDuplicateName() throws Exception {
        mvc.perform(post("/api/providers").contentType(MediaType.APPLICATION_JSON)
                        .content(createBody("重名提供商", "https://a.example")))
                .andExpect(status().isOk());
        mvc.perform(post("/api/providers").contentType(MediaType.APPLICATION_JSON)
                        .content(createBody("重名提供商", "https://b.example")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("PROVIDER_NAME_TAKEN"));
    }

    @Test
    void createdProviderIsEnabledOpenAiCompatibleWithoutKey() throws Exception {
        JsonNode created = json(mvc.perform(post("/api/providers").contentType(MediaType.APPLICATION_JSON)
                .content(createBody("新提供商", "https://new.example"))).andExpect(status().isOk())
                .andReturn());
        assertTrue(created.get("enabled").asBoolean(), "新增默认启用");
        assertFalse(created.get("hasKey").asBoolean());
        assertEquals(0, created.get("modelCount").asInt());
        assertEquals("https://new.example", created.get("baseUrl").asText());

        JsonNode list = json(mvc.perform(get("/api/providers")).andExpect(status().isOk()).andReturn());
        assertEquals(1, list.size());
    }

    // ------------------------------------------------------------------ PUT /api/providers/{id}

    @Test
    void updateProviderPersistsNameBaseUrlAndEnabled() throws Exception {
        String id = UUID.randomUUID().toString();
        insertProvider(id, "旧名", "https://old.example", true, false);

        JsonNode updated = json(mvc.perform(put("/api/providers/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"commandId\":\"" + UUID.randomUUID() + "\",\"name\":\"新名\","
                                + "\"baseUrl\":\"https://new.example\",\"enabled\":false}"))
                .andExpect(status().isOk()).andReturn());
        assertEquals("新名", updated.get("name").asText());
        assertEquals("https://new.example", updated.get("baseUrl").asText());
        assertFalse(updated.get("enabled").asBoolean());
    }

    @Test
    void updateUnknownProviderIsNotFound() throws Exception {
        mvc.perform(put("/api/providers/" + UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"commandId\":\"" + UUID.randomUUID() + "\",\"enabled\":true}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.error.params.resource").value("provider"));
    }

    // ------------------------------------------------------------------ DELETE /api/providers/{id}

    @Test
    void deleteProviderCascadesItsModels() throws Exception {
        String id = UUID.randomUUID().toString();
        insertProvider(id, "待删提供商", "https://del.example", true, false);
        insertModel(id, "m-1");
        insertModel(id, "m-2");

        mvc.perform(delete("/api/providers/" + id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"commandId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deletedProviderId").value(id));

        assertEquals(0, json(mvc.perform(get("/api/providers")).andReturn()).size());
        assertEquals(0, countModels(id), "删除 Provider 必须级联删掉它的模型行");
    }

    /** 契约 §6.6：删除最后一个已启用 Provider 服务端不阻断（警示属前端）。 */
    @Test
    void deletingTheLastEnabledProviderIsNotBlocked() throws Exception {
        String id = UUID.randomUUID().toString();
        insertProvider(id, "唯一提供商", "https://only.example", true, false);

        mvc.perform(delete("/api/providers/" + id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"commandId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isOk());
    }

    // ------------------------------------------------------------------ Key 端点

    @Test
    void savingAKeyMarksHasKeyAndNeverEchoesTheKey() throws Exception {
        String id = UUID.randomUUID().toString();
        insertProvider(id, "含 Key 提供商", "https://key.example", true, false);

        var logger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(
                ch.qos.logback.classic.Logger.ROOT_LOGGER_NAME);
        var appender = new ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();
        appender.start();
        logger.addAppender(appender);
        String body;
        try {
            body = mvc.perform(put("/api/providers/" + id + "/key")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"commandId\":\"" + UUID.randomUUID() + "\",\"apiKey\":\""
                                    + KEY_CANARY + "\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.hasKey").value(true))
                    .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        } finally {
            logger.detachAppender(appender);
        }

        assertFalse(body.contains(KEY_CANARY), "Key 明文不得出现在响应体");
        String listBody = mvc.perform(get("/api/providers")).andReturn()
                .getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertFalse(listBody.contains(KEY_CANARY), "Key 明文不得出现在列表响应");
        assertTrue(appender.list.stream()
                        .noneMatch(event -> event.getFormattedMessage().contains(KEY_CANARY)),
                "Key 明文不得出现在日志");
        assertTrue(secrets.has(SecretPort.providerTarget(id)), "Key 必须已写入凭据端口");
    }

    @Test
    void savingAnEmptyKeyIsKeyRequired() throws Exception {
        String id = UUID.randomUUID().toString();
        insertProvider(id, "空 Key 提供商", "https://emptykey.example", true, false);
        mvc.perform(put("/api/providers/" + id + "/key").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"commandId\":\"" + UUID.randomUUID() + "\",\"apiKey\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("KEY_REQUIRED"))
                .andExpect(jsonPath("$.error.params.scope").value("PROVIDER"));
    }

    @Test
    void savingAKeyOnAnUnknownProviderIsNotFound() throws Exception {
        mvc.perform(put("/api/providers/" + UUID.randomUUID() + "/key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"commandId\":\"" + UUID.randomUUID() + "\",\"apiKey\":\"x\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    @Test
    void keySaveFailureIsReportedAsKeySaveFailed() throws Exception {
        String id = UUID.randomUUID().toString();
        insertProvider(id, "落盘失败提供商", "https://refused.example", true, false);
        ((InMemorySecretPort) secrets).failSaves = true;

        mvc.perform(put("/api/providers/" + id + "/key").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"commandId\":\"" + UUID.randomUUID() + "\",\"apiKey\":\"x\"}"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error.code").value("KEY_SAVE_FAILED"))
                .andExpect(jsonPath("$.error.params.scope").value("PROVIDER"));
    }

    @Test
    void deletingAKeyClearsHasKey() throws Exception {
        String id = UUID.randomUUID().toString();
        insertProvider(id, "删 Key 提供商", "https://delkey.example", true, false);
        secrets.save(SecretPort.providerTarget(id), "k".toCharArray());

        JsonNode after = json(mvc.perform(delete("/api/providers/" + id + "/key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"commandId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isOk()).andReturn());
        assertFalse(after.get("hasKey").asBoolean());
        assertFalse(secrets.has(SecretPort.providerTarget(id)), "删 Key 必须经凭据端口删除别名");
    }

    // ------------------------------------------------------------------ POST /api/providers/{id}/models

    @Test
    void refreshModelsMergesOnlyNewIds() throws Exception {
        String id = UUID.randomUUID().toString();
        insertProvider(id, "刷新提供商", "https://refresh.example", true, false);
        secrets.save(SecretPort.providerTarget(id), "k".toCharArray());
        modelsBody = "{\"data\":[{\"id\":\"m-a\"},{\"id\":\"m-b\"}]}";

        JsonNode first = json(mvc.perform(post("/api/providers/" + id + "/models")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"commandId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isOk()).andReturn());
        assertEquals(2, first.get("added").asInt());
        assertEquals(2, first.get("models").size());

        JsonNode second = json(mvc.perform(post("/api/providers/" + id + "/models")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"commandId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isOk()).andReturn());
        assertEquals(0, second.get("added").asInt(), "已存在的模型不再新增（只增不删）");
        assertEquals(2, second.get("models").size());
    }

    @Test
    void refreshModelsDedupsCaseExactly() throws Exception {
        String id = UUID.randomUUID().toString();
        insertProvider(id, "大小写刷新提供商", "https://case.example", true, false);
        secrets.save(SecretPort.providerTarget(id), "k".toCharArray());
        modelsBody = "{\"data\":[{\"id\":\"Model-X\"},{\"id\":\"model-x\"},{\"id\":\"Model-X\"}]}";

        JsonNode refreshed = json(mvc.perform(post("/api/providers/" + id + "/models")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"commandId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isOk()).andReturn());
        assertEquals(2, refreshed.get("added").asInt(), "大小写精确：Model-X 与 model-x 共存，批内重复只入一行");
    }

    @Test
    void refreshModelsWithoutKeyIsKeyMissing() throws Exception {
        String id = UUID.randomUUID().toString();
        insertProvider(id, "无 Key 刷新提供商", "https://nokey.example", true, false);

        mvc.perform(post("/api/providers/" + id + "/models")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"commandId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("KEY_MISSING"));
    }

    @Test
    void refreshModelsOnProviderFailureIsProviderUnavailable() throws Exception {
        String id = UUID.randomUUID().toString();
        insertProvider(id, "故障提供商", "https://broken.example", true, false);
        secrets.save(SecretPort.providerTarget(id), "k".toCharArray());
        modelsStatus = 500;

        mvc.perform(post("/api/providers/" + id + "/models")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"commandId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error.code").value("PROVIDER_UNAVAILABLE"));
    }

    // ------------------------------------------------------------------ GET /api/providers/{id}/models

    /** 契约 §6.6：返回该 Provider 的模型列表，键集同 fixture model.json。 */
    @Test
    void listModelsReturnsTheProvidersModels() throws Exception {
        String id = UUID.randomUUID().toString();
        insertProvider(id, "模型列表提供商", "https://list-models.example", true, false);
        insertModel(id, "m-first");
        insertModel(id, "m-second");

        JsonNode array = json(mvc.perform(get("/api/providers/" + id + "/models"))
                .andExpect(status().isOk()).andReturn());
        Set<String> ids = new TreeSet<>();
        array.forEach(node -> ids.add(node.get("modelId").asText()));
        assertEquals(new TreeSet<>(Set.of("m-first", "m-second")), ids);
        JsonNode fixture = ApiJson.mapper().readTree(Files.readString(
                Path.of("..", "contracts", "fixtures", "dtos", "model.json"), StandardCharsets.UTF_8));
        assertEquals(fieldNames(fixture), fieldNames(array.get(0)), "Model 键集必须同 fixture");
        assertEquals(id, array.get(0).get("providerId").asText());
        assertEquals("模型列表提供商", array.get(0).get("providerName").asText());
    }

    @Test
    void listModelsReturnsEmptyArrayForAProviderWithoutModels() throws Exception {
        String id = UUID.randomUUID().toString();
        insertProvider(id, "空模型提供商", "https://empty-models.example", true, false);

        JsonNode array = json(mvc.perform(get("/api/providers/" + id + "/models"))
                .andExpect(status().isOk()).andReturn());
        assertEquals(0, array.size());
    }

    @Test
    void listModelsOnUnknownProviderIsNotFound() throws Exception {
        mvc.perform(get("/api/providers/" + UUID.randomUUID() + "/models"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.error.params.resource").value("provider"));
    }

    // ------------------------------------------------------------------ model-roles

    @Test
    void modelRolesDefaultToNoModelsWithoutConfiguration() throws Exception {
        JsonNode body = json(mvc.perform(get("/api/settings/model-roles")).andExpect(status().isOk())
                .andReturn());
        for (String role : new String[] {"CHAT", "FAST", "TRANSLATION"}) {
            assertEquals("EMPTY", body.get("roles").get(role).get("fallback").asText());
        }
        assertEquals("AUTO", body.get("budget").asText());
        assertTrue(body.get("suggestions").asBoolean());
        assertEquals("NO_MODELS", body.get("notice").asText());
    }

    @Test
    void modelRolesUpdatePersistsRolesBudgetAndSuggestions() throws Exception {
        String id = UUID.randomUUID().toString();
        insertProvider(id, "分工提供商", "https://roles.example", true, false);
        insertModel(id, "deepseek-v4-pro");

        mvc.perform(put("/api/settings/model-roles").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"commandId\":\"" + UUID.randomUUID() + "\",\"roles\":{\"CHAT\":"
                                + "{\"providerId\":\"" + id + "\",\"modelId\":\"deepseek-v4-pro\"}},"
                                + "\"budget\":\"MID\",\"suggestions\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles.CHAT.fallback").value("NONE"))
                .andExpect(jsonPath("$.roles.CHAT.providerId").value(id))
                .andExpect(jsonPath("$.roles.CHAT.modelId").value("deepseek-v4-pro"))
                .andExpect(jsonPath("$.budget").value("MID"))
                .andExpect(jsonPath("$.suggestions").value(false));

        JsonNode reloaded = json(mvc.perform(get("/api/settings/model-roles"))
                .andExpect(status().isOk()).andReturn());
        assertEquals("NONE", reloaded.get("roles").get("CHAT").get("fallback").asText());
        assertEquals("MID", reloaded.get("budget").asText());
        assertFalse(reloaded.get("suggestions").asBoolean());
    }

    @Test
    void modelRolesInvalidBudgetIsRequestInvalid() throws Exception {
        mvc.perform(put("/api/settings/model-roles").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"commandId\":\"" + UUID.randomUUID() + "\",\"budget\":\"HUGE\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("REQUEST_INVALID"));
    }

    @Test
    void modelRolesUnknownRoleKeyIsRequestInvalid() throws Exception {
        mvc.perform(put("/api/settings/model-roles").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"commandId\":\"" + UUID.randomUUID() + "\",\"roles\":{\"PLANNER\""
                                + ":{\"providerId\":null,\"modelId\":null}}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("REQUEST_INVALID"));
    }

    // ------------------------------------------------------------------ helpers

    private static String createBody(String name, String baseUrl) {
        return "{\"commandId\":\"" + UUID.randomUUID() + "\",\"name\":\"" + name
                + "\",\"baseUrl\":\"" + baseUrl + "\"}";
    }

    private static JsonNode json(MvcResult result) throws Exception {
        return ApiJson.mapper().readTree(
                result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private static Set<String> fieldNames(JsonNode node) {
        return new TreeSet<>(node.propertyNames());
    }

    private int countModels(String providerId) {
        return jdbc.queryList("SELECT " + DesktopSchema.Models.MODEL_ID + " FROM "
                        + DesktopSchema.Models.TABLE + " WHERE " + DesktopSchema.Models.PROVIDER_ID
                        + " = ?", s -> s.setString(1, providerId), rows -> rows.getString(1)).size();
    }

    private void insertProvider(String id, String name, String baseUrl, boolean enabled,
            boolean builtin) {
        jdbc.update("INSERT INTO " + DesktopSchema.Providers.TABLE + "("
                + DesktopSchema.Providers.ID + ", " + DesktopSchema.Providers.NAME + ", "
                + DesktopSchema.Providers.BASE_URL + ", " + DesktopSchema.Providers.ENABLED + ", "
                + DesktopSchema.Providers.IS_BUILTIN + ", " + DesktopSchema.Providers.CREATED_AT + ", "
                + DesktopSchema.Providers.UPDATED_AT + ") VALUES (?, ?, ?, ?, ?, ?, ?)", s -> {
                    s.setString(1, id);
                    s.setString(2, name);
                    s.setString(3, baseUrl);
                    s.setInt(4, enabled ? 1 : 0);
                    s.setInt(5, builtin ? 1 : 0);
                    s.setLong(6, T0);
                    s.setLong(7, T0);
                });
    }

    private void insertModel(String providerId, String modelId) {
        jdbc.update("INSERT INTO " + DesktopSchema.Models.TABLE + "(" + DesktopSchema.Models.ID
                + ", " + DesktopSchema.Models.PROVIDER_ID + ", " + DesktopSchema.Models.MODEL_ID
                + ", " + DesktopSchema.Models.DISPLAY_NAME + ", " + DesktopSchema.Models.CREATED_AT
                + ") VALUES (?, ?, ?, ?, ?)", s -> {
                    s.setString(1, UUID.randomUUID().toString());
                    s.setString(2, providerId);
                    s.setString(3, modelId);
                    s.setString(4, modelId);
                    s.setLong(5, T0);
                });
    }

    // ------------------------------------------------------------------ stubs

    /** 内存凭据端口：只保存 target→secret（测试内），可切换保存失败。 */
    static final class InMemorySecretPort implements SecretPort {

        private final Map<String, String> stored = new HashMap<>();
        volatile boolean failSaves;

        synchronized void reset() {
            stored.clear();
            failSaves = false;
        }

        @Override
        public synchronized void save(String target, char[] secret) {
            if (failSaves) {
                throw new SecretAccessException(SecretAccessError.UNEXPECTED, target,
                        "the stub refused the write");
            }
            stored.put(target, new String(secret));
        }

        @Override
        public synchronized boolean has(String target) {
            return stored.containsKey(target);
        }

        @Override
        public synchronized Optional<char[]> read(String target) {
            String value = stored.get(target);
            return value == null ? Optional.empty() : Optional.of(value.toCharArray());
        }

        @Override
        public synchronized void delete(String target) {
            stored.remove(target);
        }
    }

    /** 把 provider 的 {@code /models} 请求重写到本机 stub 端口；只实现 {@code get}。 */
    static final class LoopbackTransport implements GatewayPorts.Transport {

        @Override
        public GatewayPorts.Response post(String url, String apiKey, byte[] body) {
            throw new UnsupportedOperationException("the settings tests only list models");
        }

        @Override
        public GatewayPorts.Response get(String url, String apiKey) throws IOException {
            String path = URI.create(url).getPath();
            HttpURLConnection connection = (HttpURLConnection) URI
                    .create("http://127.0.0.1:" + providerPort + path).toURL().openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(5_000);
            connection.setReadTimeout(10_000);
            connection.setUseCaches(false);
            connection.setRequestProperty("Authorization", "Bearer " + apiKey);
            int status = connection.getResponseCode();
            InputStream stream = status / 100 == 2 ? connection.getInputStream()
                    : new ByteArrayInputStream(new byte[0]);
            return new LoopbackResponse(connection, status, stream);
        }
    }

    static final class LoopbackResponse implements GatewayPorts.Response {

        private final HttpURLConnection connection;
        private final int status;
        private final InputStream stream;

        LoopbackResponse(HttpURLConnection connection, int status, InputStream stream) {
            this.connection = connection;
            this.status = status;
            this.stream = stream;
        }

        @Override
        public int status() {
            return status;
        }

        @Override
        public InputStream body() {
            return stream;
        }

        @Override
        public void close() {
            connection.disconnect();
        }
    }

    @TestConfiguration
    static class StubConfiguration {

        @Bean
        @Primary
        SecretPort stubSecretPort() {
            return new InMemorySecretPort();
        }

        @Bean
        @Primary
        GatewayPorts.Transport stubProviderTransport() {
            return new LoopbackTransport();
        }
    }
}
