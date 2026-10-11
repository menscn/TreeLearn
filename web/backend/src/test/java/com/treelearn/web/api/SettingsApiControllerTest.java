package com.treelearn.web.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.treelearn.core.domain.GenerationModel;
import com.treelearn.web.api.dto.ApiJson;
import com.treelearn.web.credential.SecretPort;
import com.treelearn.web.storage.DesktopSchema;
import com.treelearn.web.storage.JdbcSupport;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.JsonNode;

/**
 * T-2026-10-10-034: MockMvc 形状与回落闭环（契约 §5.12 / §6.6）。
 *
 * <p>主页加载即请求 {@code GET /api/settings/model-resolution}；本测试固定：无配置时回落解析以
 * {@code fallback} token 表达（EMPTY/BUILTIN…）而不是 404，HTTP 恒为 200，且形状与共享 fixture
 * {@code web/contracts/fixtures/dtos/model-resolution.json} 一致。回落判定全部来自
 * {@code GenerationPlanner}，本测试只断言端点投影，不重造回落规则。
 */
@SpringBootTest
class SettingsApiControllerTest {

    private static final long T0 = 1_752_000_000_000L;

    @TempDir
    static Path dataDir;

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("treelearn.data-dir", () -> dataDir.toString().replace('\\', '/'));
    }

    @Autowired WebApplicationContext context;
    @Autowired JdbcSupport jdbc;

    MockMvc mvc;

    @BeforeEach
    void setUp() throws IOException {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
        // 每个用例从空配置开始，回落分支由本用例写入的行决定，而非迁移的内置 seed。
        jdbc.update("DELETE FROM " + DesktopSchema.Providers.TABLE, JdbcSupport.NO_PARAMS);
        // 分工/预算保存在数据目录的非敏感偏好文件；清掉以隔离用例（保存分工的用例会写它）。
        Files.deleteIfExists(dataDir.resolve("settings.properties"));
    }

    /** 契约 §5.12：ModelResolution 只含 roles，roles 恰有 CHAT/FAST/TRANSLATION，每项三个字段。 */
    @Test
    void modelResolutionShapeMatchesTheContractFixture() throws Exception {
        insertProvider(true, true);
        JsonNode body = body();
        JsonNode fixture = ApiJson.mapper().readTree(Files.readString(
                requestFixture(), StandardCharsets.UTF_8));

        assertEquals(Set.of("roles"), fieldNames(body), "ModelResolution 只含 roles");
        assertEquals(fieldNames(fixture), fieldNames(body));
        assertEquals(fieldNames(fixture.get("roles")), fieldNames(body.get("roles")));
        for (String role : new String[] {"CHAT", "FAST", "TRANSLATION"}) {
            assertEquals(fieldNames(fixture.get("roles").get(role)),
                    fieldNames(body.get("roles").get(role)), "role " + role + " 字段集必须同 fixture");
        }
    }

    /** 无 Provider/模型/Key：必须 200 且 fallback=EMPTY，前端据此禁用发送并给出引导，而不是 404。 */
    @Test
    void emptyConfigurationReportsEmptyFallbackWithHttp200() throws Exception {
        mvc.perform(get("/api/settings/model-resolution"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles.CHAT.fallback").value("EMPTY"))
                .andExpect(jsonPath("$.roles.CHAT.modelId").isEmpty())
                .andExpect(jsonPath("$.roles.CHAT.providerId").isEmpty())
                .andExpect(jsonPath("$.roles.FAST.fallback").value("EMPTY"))
                .andExpect(jsonPath("$.roles.TRANSLATION.fallback").value("EMPTY"));
    }

    /** 有内置 Provider 但无 Key：解析给出内置默认（BUILTIN），无 Key 由写端点收口，读端点不 404。 */
    @Test
    void builtinProviderWithoutKeyResolvesToBuiltinToken() throws Exception {
        insertProvider(true, true);
        mvc.perform(get("/api/settings/model-resolution"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles.CHAT.fallback").value("BUILTIN"))
                .andExpect(jsonPath("$.roles.CHAT.providerId")
                        .value(DesktopSchema.BUILTIN_DEEPSEEK_PROVIDER_ID))
                .andExpect(jsonPath("$.roles.CHAT.modelId").value(GenerationModel.FLASH.wireId));
    }

    /** 无内置、但有可用模型：回落最早可用（EARLIEST）。 */
    @Test
    void earliestAvailableModelResolvesToEarliestToken() throws Exception {
        insertProvider(true, false);
        mvc.perform(get("/api/settings/model-resolution"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles.CHAT.fallback").value("EARLIEST"))
                .andExpect(jsonPath("$.roles.FAST.fallback").value("EARLIEST"));
    }

    /**
     * T-041 缺陷修复：设置页保存的 CHAT 分工必须被 model-resolution 反映（该角色 `NONE` + 所选
     * provider/model），未配置的 FAST/TRANSLATION 仍按规划器链回落到内置默认（BUILTIN）。
     */
    @Test
    void savedRoleAssignmentIsReflectedByModelResolution() throws Exception {
        String chosen = insertProvider(true, false);
        insertProvider(true, true);

        mvc.perform(put("/api/settings/model-roles").contentType(MediaType.APPLICATION_JSON)
                        .content(roleUpdateBody("CHAT", chosen, GenerationModel.FLASH.wireId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles.CHAT.fallback").value("NONE"));

        mvc.perform(get("/api/settings/model-resolution"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles.CHAT.fallback").value("NONE"))
                .andExpect(jsonPath("$.roles.CHAT.providerId").value(chosen))
                .andExpect(jsonPath("$.roles.CHAT.modelId").value(GenerationModel.FLASH.wireId))
                .andExpect(jsonPath("$.roles.FAST.fallback").value("BUILTIN"))
                .andExpect(jsonPath("$.roles.TRANSLATION.fallback").value("BUILTIN"));
    }

    /**
     * T-041 断链回落：分工指向的 Provider 被删后，该角色按规划器链回落（此处落内置默认 BUILTIN），
     * 起点仍是同一 {@code resolveAll}，不另立规则。
     */
    @Test
    void unavailableSavedRoleFallsBackThroughThePlannerChain() throws Exception {
        String chosen = insertProvider(true, false);
        insertProvider(true, true);

        mvc.perform(put("/api/settings/model-roles").contentType(MediaType.APPLICATION_JSON)
                        .content(roleUpdateBody("CHAT", chosen, GenerationModel.FLASH.wireId)))
                .andExpect(status().isOk());

        jdbc.update("DELETE FROM " + DesktopSchema.Providers.TABLE + " WHERE "
                + DesktopSchema.Providers.ID + " = ?", s -> s.setString(1, chosen));

        mvc.perform(get("/api/settings/model-resolution"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles.CHAT.fallback").value("BUILTIN"))
                .andExpect(jsonPath("$.roles.CHAT.providerId")
                        .value(DesktopSchema.BUILTIN_DEEPSEEK_PROVIDER_ID))
                .andExpect(jsonPath("$.roles.CHAT.modelId").value(GenerationModel.FLASH.wireId));
    }

    /** 回归：未保存分工时，回落链与修复前一致（有内置默认即 BUILTIN，非内置可用模型不抢占）。 */
    @Test
    void unsavedRolesKeepThePreFixFallbackChain() throws Exception {
        insertProvider(true, false);
        insertProvider(true, true);

        mvc.perform(get("/api/settings/model-resolution"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles.CHAT.fallback").value("BUILTIN"))
                .andExpect(jsonPath("$.roles.CHAT.providerId")
                        .value(DesktopSchema.BUILTIN_DEEPSEEK_PROVIDER_ID))
                .andExpect(jsonPath("$.roles.FAST.fallback").value("BUILTIN"))
                .andExpect(jsonPath("$.roles.TRANSLATION.fallback").value("BUILTIN"));
    }

    // ------------------------------------------------------------------ helpers

    private static String roleUpdateBody(String role, String providerId, String modelId) {
        return "{\"commandId\":\"" + UUID.randomUUID() + "\",\"roles\":{\"" + role + "\":{\"providerId\":\""
                + providerId + "\",\"modelId\":\"" + modelId + "\"}}}";
    }

    private JsonNode body() throws Exception {
        return ApiJson.mapper().readTree(mvc.perform(get("/api/settings/model-resolution"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private static Path requestFixture() {
        return Path.of("..", "contracts", "fixtures", "dtos", "model-resolution.json");
    }

    private static Set<String> fieldNames(JsonNode node) {
        return new TreeSet<>(node.propertyNames());
    }

    private String insertProvider(boolean enabled, boolean builtin) {
        String id = builtin ? DesktopSchema.BUILTIN_DEEPSEEK_PROVIDER_ID : UUID.randomUUID().toString();
        jdbc.update("INSERT INTO " + DesktopSchema.Providers.TABLE + "("
                + DesktopSchema.Providers.ID + ", " + DesktopSchema.Providers.NAME + ", "
                + DesktopSchema.Providers.BASE_URL + ", " + DesktopSchema.Providers.ENABLED + ", "
                + DesktopSchema.Providers.IS_BUILTIN + ", " + DesktopSchema.Providers.CREATED_AT + ", "
                + DesktopSchema.Providers.UPDATED_AT + ") VALUES (?, ?, ?, ?, ?, ?, ?)", s -> {
                    s.setString(1, id);
                    s.setString(2, "p-" + id);
                    s.setString(3, "https://api.deepseek.com");
                    s.setInt(4, enabled ? 1 : 0);
                    s.setInt(5, builtin ? 1 : 0);
                    s.setLong(6, T0);
                    s.setLong(7, T0);
                });
        jdbc.update("INSERT INTO " + DesktopSchema.Models.TABLE + "(" + DesktopSchema.Models.ID + ", "
                + DesktopSchema.Models.PROVIDER_ID + ", " + DesktopSchema.Models.MODEL_ID + ", "
                + DesktopSchema.Models.DISPLAY_NAME + ", " + DesktopSchema.Models.CREATED_AT + ") "
                + "VALUES (?, ?, ?, ?, ?)", s -> {
                    s.setString(1, UUID.randomUUID().toString());
                    s.setString(2, id);
                    s.setString(3, GenerationModel.FLASH.wireId);
                    s.setString(4, GenerationModel.FLASH.wireId);
                    s.setLong(5, T0);
                });
        return id;
    }

    /** 测试凭据端口：一律报告「无 Key」，让回落解析的读路径不触碰 Windows 凭据管理器。 */
    @TestConfiguration
    static class StubConfiguration {

        @Bean
        @Primary
        SecretPort stubSecretPort() {
            return new SecretPort() {
                @Override
                public void save(String target, char[] secret) {
                }

                @Override
                public boolean has(String target) {
                    return false;
                }

                @Override
                public Optional<char[]> read(String target) {
                    return Optional.empty();
                }

                @Override
                public void delete(String target) {
                }
            };
        }
    }
}
