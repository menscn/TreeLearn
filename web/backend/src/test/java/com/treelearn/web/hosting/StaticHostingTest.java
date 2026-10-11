package com.treelearn.web.hosting;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * 前端产物缺失时的静态托管行为（任务 T-2026-10-10-029；契约 §10）。
 *
 * <p>dist 缺失**不得**阻断启动：上下文仍能加载、`/api/**` 照常工作；`/` 与前端路由返回可读提示
 * 而不是 404；SSE 端点命名空间同样不被回退吞掉。
 */
@SpringBootTest
class StaticHostingTest {

    private static final String HINT_MARKER = "treelearn-frontend-build-missing";

    @TempDir
    static Path tempRoot;

    @TempDir
    static Path dataDir;

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("treelearn.frontend-dist", () -> tempRoot.resolve("absent-dist").toString());
        registry.add("treelearn.data-dir", () -> dataDir.toString());
    }

    @Autowired
    WebApplicationContext context;

    MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    void missingDistDoesNotBlockStartupAndRootShowsAReadableHint() throws Exception {
        mvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(HINT_MARKER)));
    }

    @Test
    void frontendRoutesAlsoShowTheHintWhenDistIsMissing() throws Exception {
        mvc.perform(get("/library"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(HINT_MARKER)));
    }

    @Test
    void apiNamespaceStillWorksWhenDistIsMissing() throws Exception {
        mvc.perform(get("/api/health/ready"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ready"));
    }

    @Test
    void unknownApiStillReturnsTheErrorEnvelopeWhenDistIsMissing() throws Exception {
        mvc.perform(get("/api/does-not-exist"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    @Test
    void sseEndpointNamespaceIsNotSwallowedByTheFallback() throws Exception {
        mvc.perform(get("/api/operations/" + UUID.randomUUID() + "/events"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"))
                .andExpect(content().string(containsString("NOT_FOUND")));
    }
}
