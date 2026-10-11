package com.treelearn.web.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.treelearn.ai.gateway.GenerationSink;
import com.treelearn.ai.gateway.StreamOutcome;
import com.treelearn.ai.prompt.DeepSeekRequest;
import com.treelearn.core.domain.GenerationModel;
import com.treelearn.web.api.dto.ApiJson;
import com.treelearn.web.credential.SecretPort;
import com.treelearn.web.generation.DesktopGenerationGateway;
import com.treelearn.web.storage.DesktopSchema;
import com.treelearn.web.storage.JdbcSupport;
import com.treelearn.web.storage.ModelStore;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.JsonNode;

/**
 * T-2026-10-10-028: 真实 HTTP 端到端（stub 网关）——首问→SSE stable→messages 读修订→追问→
 * cancel→retry 全链路，外加 token 白名单与 commandId 幂等（契约 §3/§4/§6）。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CoreApiEndToEndTest {

    private static final long T0 = 1_700_000_000_000L;
    private static final long WAIT = 5_000L;

    @TempDir
    static Path dataDir;

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("treelearn.data-dir", () -> dataDir.toString().replace('\\', '/'));
        registry.add("treelearn.sse.keepalive-millis", () -> "200");
    }

    @LocalServerPort int port;
    @Autowired JdbcSupport jdbc;
    @Autowired ModelStore models;
    @Autowired StubGateway gateway;

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    @BeforeEach
    void seedProvider() {
        String providerId = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO " + DesktopSchema.Providers.TABLE + "("
                + DesktopSchema.Providers.ID + "," + DesktopSchema.Providers.NAME + ","
                + DesktopSchema.Providers.BASE_URL + "," + DesktopSchema.Providers.ENABLED + ","
                + DesktopSchema.Providers.IS_BUILTIN + "," + DesktopSchema.Providers.CREATED_AT + ","
                + DesktopSchema.Providers.UPDATED_AT + ") VALUES (?,?,?,?,?,?,?)", s -> {
            s.setString(1, providerId); s.setString(2, "DeepSeek-" + providerId.substring(0, 8));
            s.setString(3, "https://api.deepseek.com"); s.setInt(4, 1); s.setInt(5, 1);
            s.setLong(6, T0); s.setLong(7, T0);
        });
        models.upsertMany(providerId, List.of("deepseek-flash"), () -> UUID.randomUUID().toString(), T0);
    }

    @Test
    void theCoreLoopRunsOverRealHttp() throws Exception {
        // 1) 首问：创建主题并打开 SSE
        JsonNode bootstrap = post("/api/topics", "{\"commandId\":\"" + uuid() + "\",\"question\":\"光合作用是什么？\","
                + "\"model\":\"deepseek-flash\",\"thinkingEnabled\":false}");
        String topicId = bootstrap.get("topic").get("id").asText();
        String rootBranchId = bootstrap.get("rootBranch").get("id").asText();
        String aiId = bootstrap.get("aiMessage").get("id").asText();
        String firstOperationId = bootstrap.get("operation").get("id").asText();
        assertEquals("/api/operations/" + firstOperationId + "/events",
                bootstrap.get("operation").get("sseUrl").asText());

        try (Sse sse = open("/api/operations/" + firstOperationId + "/events")) {
            assertEquals("snapshot", sse.next(WAIT).event());
            StubGateway.Call call = gateway.last();
            call.accept();
            call.delta("先想", "");
            call.delta("", "光合作用是植物利用光能合成有机物的过程。");
            call.complete();
            Event stable = sse.until("stable", WAIT);
            assertEquals("光合作用是植物利用光能合成有机物的过程。",
                    stable.data().get("revision").get("answerContent").asText());
            assertTrue(sse.awaitEnd(WAIT));
        }

        // 2) 读取分支消息：AI 位置带完整修订
        JsonNode conversation = get("/api/branches/" + rootBranchId + "/messages");
        JsonNode aiMessage = lastMessage(conversation.get("messages"));
        assertEquals(aiId, aiMessage.get("id").asText());
        assertEquals("光合作用是植物利用光能合成有机物的过程。",
                aiMessage.get("versions").get(0).get("answerContent").asText());
        assertEquals(0, aiMessage.get("attachments").size());

        // 3) 追问：同一分支追加，SSE 到 stable
        JsonNode turn = post("/api/branches/" + rootBranchId + "/messages",
                "{\"commandId\":\"" + uuid() + "\",\"text\":\"光反应在哪里进行？\",\"quote\":\"\","
                        + "\"model\":\"deepseek-flash\",\"thinkingEnabled\":false}");
        String followAi = turn.get("aiMessage").get("id").asText();
        String followOperation = turn.get("operation").get("id").asText();

        try (Sse sse = open("/api/operations/" + followOperation + "/events")) {
            assertEquals("snapshot", sse.next(WAIT).event());
            StubGateway.Call call = gateway.last();
            call.accept();
            call.delta("", "在类囊体薄膜上");
            // 4) cancel：收到部分内容后停止
            JsonNode canceled = post("/api/operations/" + followOperation + "/cancel",
                    "{\"commandId\":\"" + uuid() + "\"}");
            assertEquals("CANCELED", canceled.get("state").asText());
            assertEquals("canceled", sse.until("canceled", WAIT).event());
            assertTrue(sse.awaitEnd(WAIT));
        }

        // 5) retry：原位重试（同一位置新尝试）
        JsonNode retry = post("/api/messages/" + followAi + "/retry",
                "{\"commandId\":\"" + uuid() + "\",\"model\":\"deepseek-flash\",\"thinkingEnabled\":false}");
        assertEquals(followAi, retry.get("message").get("id").asText());
        String retryOperation = retry.get("operation").get("id").asText();
        assertEquals(retryOperation, retry.get("message").get("activeOperationId").asText());

        try (Sse sse = open("/api/operations/" + retryOperation + "/events")) {
            assertEquals("snapshot", sse.next(WAIT).event());
            StubGateway.Call call = gateway.last();
            call.accept();
            call.delta("", "第二次回答");
            call.complete();
            assertEquals("第二次回答",
                    sse.until("stable", WAIT).data().get("revision").get("answerContent").asText());
            assertTrue(sse.awaitEnd(WAIT));
        }

        JsonNode afterRetry = get("/api/branches/" + rootBranchId + "/messages");
        JsonNode retriedAi = lastMessage(afterRetry.get("messages"));
        assertEquals(1, retriedAi.get("versions").size());
        assertEquals("第二次回答", retriedAi.get("versions").get(0).get("answerContent").asText());
        assertNotNull(topicId, "主题 id 必须存在于首问响应");
    }

    @Test
    void malformedModelTokenIsRejectedWithFourHundred() throws Exception {
        HttpResponse<String> response = postRaw("/api/topics",
                "{\"commandId\":\"" + uuid() + "\",\"question\":\"q\",\"model\":\"gpt-4\","
                        + "\"thinkingEnabled\":false}");
        assertEquals(400, response.statusCode(), "body: " + response.body());
        assertEquals("REQUEST_INVALID", ApiJson.mapper().readTree(response.body())
                .get("error").get("code").asText());
    }

    @Test
    void noConfiguredProviderYieldsNoModels() throws Exception {
        jdbc.update("DELETE FROM " + DesktopSchema.Providers.TABLE, JdbcSupport.NO_PARAMS);
        HttpResponse<String> response = postRaw("/api/topics",
                "{\"commandId\":\"" + uuid() + "\",\"question\":\"q\",\"model\":\"deepseek-flash\","
                        + "\"thinkingEnabled\":false}");
        assertEquals(400, response.statusCode(), "body: " + response.body());
        assertEquals("NO_MODELS", ApiJson.mapper().readTree(response.body())
                .get("error").get("code").asText());
    }

    @Test
    void repeatedCommandIdReturnsTheSameOperation() throws Exception {
        String commandId = uuid();
        JsonNode first = post("/api/topics", "{\"commandId\":\"" + commandId + "\",\"question\":\"同一问\","
                + "\"model\":\"deepseek-flash\",\"thinkingEnabled\":false}");
        JsonNode replay = post("/api/topics", "{\"commandId\":\"" + commandId + "\",\"question\":\"同一问\","
                + "\"model\":\"deepseek-flash\",\"thinkingEnabled\":false}");
        assertEquals(first.get("topic").get("id").asText(), replay.get("topic").get("id").asText());
        assertEquals(first.get("operation").get("id").asText(), replay.get("operation").get("id").asText());
    }

    // ------------------------------------------------------------------ http

    private JsonNode post(String path, String body) throws Exception {
        HttpResponse<String> response = postRaw(path, body);
        assertEquals(200, response.statusCode(), "POST " + path + " -> " + response.body());
        return ApiJson.mapper().readTree(response.body());
    }

    private HttpResponse<String> postRaw(String path, String body) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create(base() + path))
                .header("Content-Type", "application/json; charset=UTF-8")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8)).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private JsonNode get(String path) throws Exception {
        HttpResponse<String> response = http.send(HttpRequest.newBuilder(URI.create(base() + path)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode(), "GET " + path + " -> " + response.body());
        return ApiJson.mapper().readTree(response.body());
    }

    private Sse open(String path) throws Exception {
        HttpResponse<InputStream> response = http.send(HttpRequest.newBuilder(URI.create(base() + path))
                .GET().timeout(Duration.ofSeconds(30)).build(), HttpResponse.BodyHandlers.ofInputStream());
        assertEquals(200, response.statusCode());
        return new Sse(response);
    }

    private String base() {
        return "http://127.0.0.1:" + port;
    }

    private static String uuid() {
        return UUID.randomUUID().toString();
    }

    private static JsonNode lastMessage(JsonNode messages) {
        return messages.get(messages.size() - 1);
    }

    // -------------------------------------------------------------- fixtures

    @TestConfiguration
    static class StubConfiguration {

        @Bean
        @Primary
        StubGateway stubGateway() {
            return new StubGateway();
        }

        @Bean
        @Primary
        SecretPort stubSecretPort() {
            return new StubSecretPort();
        }
    }

    /** 测试用凭据端口：声明任何 provider 都有 Key，且可导出测试 key。 */
    static final class StubSecretPort implements SecretPort {

        @Override
        public void save(String target, char[] secret) {
        }

        @Override
        public boolean has(String target) {
            return true;
        }

        @Override
        public Optional<char[]> read(String target) {
            return Optional.of("sk-test".toCharArray());
        }

        @Override
        public void delete(String target) {
        }
    }

    /** 可手动驱动的网关：每次打开记录一次调用，由测试推进 accept/delta/complete。 */
    static final class StubGateway implements DesktopGenerationGateway {

        private final List<Call> calls = Collections.synchronizedList(new ArrayList<>());

        @Override
        public Stream open(GenerationModel model, DeepSeekRequest request, GenerationSink sink) {
            Call call = new Call(sink);
            calls.add(call);
            return call;
        }

        synchronized Call last() {
            return calls.get(calls.size() - 1);
        }

        static final class Call implements DesktopGenerationGateway.Stream {

            private final GenerationSink sink;
            private boolean accepted;
            private boolean cancelled;

            Call(GenerationSink sink) {
                this.sink = sink;
            }

            void accept() {
                accepted = sink.onAccepted(200);
            }

            void delta(String reasoning, String content) {
                if (accepted) {
                    sink.onDelta(reasoning, content);
                }
            }

            void complete() {
                sink.onTerminal(new StreamOutcome(StreamOutcome.Kind.COMPLETED, null, 200, 0, 0));
            }

            @Override
            public void cancel() {
                cancelled = true;
            }

            @Override
            public boolean isCancelled() {
                return cancelled;
            }
        }
    }

    // -------------------------------------------------------------- sse read

    private record Event(String event, JsonNode data) {}

    private static final class End {
        static final End INSTANCE = new End();
    }

    /** 后台线程读取一条 SSE 响应，把帧投递到队列，供测试轮询。 */
    static final class Sse implements AutoCloseable {

        private final BlockingQueue<Object> items = new LinkedBlockingQueue<>();
        private final BufferedReader reader;
        private final Thread pump;

        Sse(HttpResponse<InputStream> response) {
            this.reader = new BufferedReader(new InputStreamReader(response.body(), StandardCharsets.UTF_8));
            this.pump = new Thread(this::read, "e2e-sse-reader");
            this.pump.setDaemon(true);
            this.pump.start();
        }

        private void read() {
            try {
                List<String> frame = new ArrayList<>();
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.isEmpty()) {
                        if (!frame.isEmpty()) {
                            items.add(parse(frame));
                            frame.clear();
                        }
                    } else if (!line.startsWith(":")) {
                        frame.add(line);
                    }
                }
            } catch (IOException | RuntimeException ignored) {
                // 客户端关闭或不可解析：由轮询侧报告。
            } finally {
                items.add(End.INSTANCE);
            }
        }

        private static Event parse(List<String> lines) {
            String event = "";
            StringBuilder data = new StringBuilder();
            for (String line : lines) {
                int colon = line.indexOf(':');
                String key = colon < 0 ? line : line.substring(0, colon);
                String value = colon < 0 ? "" : line.substring(colon + 1);
                if (value.startsWith(" ")) {
                    value = value.substring(1);
                }
                if (key.equals("event")) {
                    event = value;
                } else if (key.equals("data")) {
                    data.append(value);
                }
            }
            JsonNode node = data.isEmpty() ? ApiJson.mapper().missingNode()
                    : ApiJson.mapper().readTree(data.toString());
            return new Event(event, node);
        }

        Event next(long millis) throws InterruptedException {
            long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(millis);
            while (true) {
                long remaining = Math.max(0, (deadline - System.nanoTime()) / 1_000_000L);
                Object item = items.poll(remaining, TimeUnit.MILLISECONDS);
                if (item == null) {
                    fail("等待 SSE 帧超时");
                } else if (item instanceof Event event) {
                    return event;
                } else if (item == End.INSTANCE) {
                    fail("SSE 在期望帧之前结束");
                }
            }
        }

        Event until(String terminal, long millis) throws InterruptedException {
            Event event = next(millis);
            while (!event.event().equals(terminal)) {
                event = next(millis);
            }
            return event;
        }

        boolean awaitEnd(long millis) throws InterruptedException {
            long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(millis);
            while (true) {
                long remaining = Math.max(0, (deadline - System.nanoTime()) / 1_000_000L);
                Object item = items.poll(remaining, TimeUnit.MILLISECONDS);
                if (item == null) {
                    return false;
                }
                if (item == End.INSTANCE) {
                    return true;
                }
            }
        }

        @Override
        public void close() {
            try {
                reader.close();
            } catch (IOException ignored) {
                // 已关闭
            }
        }
    }
}
