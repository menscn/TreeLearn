package com.treelearn.web.hosting;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * 发行口静态托管 + SPA 回退的形状证明（任务 T-2026-10-10-029；契约 §10）。
 *
 * <p>用一个临时 dist（写入带标记的最小 `index.html` 与 `assets/app.js`）验证：前端路由
 * （含深层 `/topic/:id`、`/settings/*`）回退到 `index.html`；真实静态资源按文件返回；
 * `/api/**` 命名空间**绝不**被回退（未知接口得到 §3.1 错误封装），既有 ready 端点不受影响。
 */
@SpringBootTest
class SpaFallbackTest {

    private static final String INDEX_MARKER = "treelearn-spa-index-marker";
    private static final String ASSET_MARKER = "treelearn-asset-marker";
    private static final String TOPIC_ID = "11111111-1111-1111-1111-111111111111";

    @TempDir
    static Path tempRoot;

    static Path distDir;

    @TempDir
    static Path dataDir;

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) throws Exception {
        distDir = tempRoot.resolve("dist");
        // dist 必须在 Spring 上下文加载前就存在：静态托管在启动时解析 index.html。
        Files.createDirectories(distDir.resolve("assets"));
        Files.writeString(distDir.resolve("index.html"),
                "<!doctype html><html><body><div id=\"root\">" + INDEX_MARKER + "</div></body></html>",
                StandardCharsets.UTF_8);
        Files.writeString(distDir.resolve("assets/app.js"), "console.log('" + ASSET_MARKER + "');",
                StandardCharsets.UTF_8);
        registry.add("treelearn.frontend-dist", () -> distDir.toString());
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
    void rootServesTheSpaIndex() throws Exception {
        mvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(containsString(INDEX_MARKER)));
    }

    @Test
    void frontendRoutesFallBackToTheSpaIndex() throws Exception {
        for (String route : new String[] {"/library", "/topic/" + TOPIC_ID, "/settings/appearance"}) {
            mvc.perform(get(route))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString(INDEX_MARKER)));
        }
    }

    @Test
    void realStaticAssetIsServedFromDiskNotFallback() throws Exception {
        mvc.perform(get("/assets/app.js"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(ASSET_MARKER)))
                .andExpect(content().string(not(containsString(INDEX_MARKER))));
    }

    @Test
    void unknownApiIsNeverFellBackToTheSpaIndex() throws Exception {
        mvc.perform(get("/api/does-not-exist"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"))
                .andExpect(content().string(not(containsString(INDEX_MARKER))));
    }

    @Test
    void uppercaseApiPrefixIsNeverFellBackAndYieldsJsonNotFound() throws Exception {
        mvc.perform(get("/API/foo"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"))
                .andExpect(content().string(not(containsString(INDEX_MARKER))));
    }

    @Test
    void mixedCaseApiPrefixIsNeverFellBackAndYieldsJsonNotFound() throws Exception {
        mvc.perform(get("/Api/nothing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"))
                .andExpect(content().string(not(containsString(INDEX_MARKER))));
    }

    @Test
    void bareApiNamespaceStaysReservedAndUnchanged() throws Exception {
        for (String route : new String[] {"/api", "/api/"}) {
            mvc.perform(get(route))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error.code").value("NOT_FOUND"))
                    .andExpect(content().string(not(containsString(INDEX_MARKER))));
        }
    }

    @Test
    void readyEndpointIsUnchanged() throws Exception {
        mvc.perform(get("/api/health/ready"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ready"));
    }
}
