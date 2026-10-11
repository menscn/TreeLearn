package com.treelearn.web.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.treelearn.ai.gateway.AiGateway;
import com.treelearn.ai.gateway.AiGatewayError;
import com.treelearn.ai.gateway.GatewayPorts;
import com.treelearn.ai.gateway.GenerationSink;
import com.treelearn.ai.gateway.ProviderEndpoint;
import com.treelearn.ai.gateway.StreamOutcome;
import com.treelearn.ai.prompt.DeepSeekRequest;
import com.treelearn.ai.prompt.DeepSeekRequestAssembler;
import com.treelearn.ai.prompt.PromptBudget;
import com.treelearn.core.domain.PromptPurpose;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 桌面网关的端到端证据（T-2026-10-10-021）：JDK 内建 {@link HttpServer} 监听 {@code 127.0.0.1} 随机端口，真实
 * SSE over a socket，把共享 {@link AiGateway} + 桌面 JSON 适配器走通；状态码分类、客户端取消、连接中断与畸形流
 * 分别可区分。不访问外网：test transport 只重写路径到本机 http 端口（共享 {@code HttpsTransport} 只用于生产）。
 */
class StubProviderStreamTest {

    private HttpServer server;
    private int port;
    private volatile Handler script;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/chat/completions", exchange -> {
            try {
                script.handle(exchange);
            } catch (IOException clientAborted) {
                // the client may abort mid-write (cancel / interrupted); the client outcome is asserted
            } finally {
                exchange.close();
            }
        });
        server.start();
        port = server.getAddress().getPort();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void realSseStreamDeliversIncrementalDeltasAndCompletes() {
        script = exchange -> writeSse(exchange, List.of(
                "data: {\"choices\":[{\"delta\":{\"reasoning_content\":\"先想\"}}]}",
                "data: {\"choices\":[{\"delta\":{\"content\":\"答案\"}}]}",
                "data: [DONE]"));
        RecordingSink sink = new RecordingSink();

        gateway(Runnable::run).streamChat("sk-x", endpoint(), request(), sink);

        assertEquals(StreamOutcome.Kind.COMPLETED, sink.terminal.kind);
        assertEquals(List.of("先想"), sink.reasoning);
        assertEquals(List.of("答案"), sink.answer);
        assertEquals(2, sink.terminal.reasoningChars);
        assertEquals(2, sink.terminal.answerChars);
    }

    @Test
    void providerStatusesAreClassified() {
        Map<Integer, AiGatewayError.Kind> cases = new LinkedHashMap<>();
        cases.put(401, AiGatewayError.Kind.AUTH_INVALID);
        cases.put(429, AiGatewayError.Kind.RATE_LIMITED);
        cases.put(500, AiGatewayError.Kind.PROVIDER_UNAVAILABLE);

        for (Map.Entry<Integer, AiGatewayError.Kind> entry : cases.entrySet()) {
            script = exchange -> exchange.sendResponseHeaders(entry.getKey(), -1);
            RecordingSink sink = new RecordingSink();
            gateway(Runnable::run).streamChat("sk-x", endpoint(), request(), sink);
            assertEquals(StreamOutcome.Kind.FAILED, sink.terminal.kind, "status " + entry.getKey());
            assertEquals(entry.getValue(), sink.terminal.error.kind, "status " + entry.getKey());
            assertEquals(entry.getKey().intValue(), sink.terminal.httpStatus, "status " + entry.getKey());
        }
    }

    @Test
    void aCleanEndWithoutTheSentinelIsInterrupted() {
        script = exchange -> writeSse(exchange, List.of(
                "data: {\"choices\":[{\"delta\":{\"content\":\"半句\"}}]}"));
        RecordingSink sink = new RecordingSink();

        gateway(Runnable::run).streamChat("sk-x", endpoint(), request(), sink);

        assertEquals(StreamOutcome.Kind.INTERRUPTED, sink.terminal.kind);
        assertEquals(null, sink.terminal.error);
        assertEquals(List.of("半句"), sink.answer);
    }

    @Test
    void aMalformedStreamIsClassifiedAsParseFailed() {
        script = exchange -> writeSse(exchange, List.of("data: {\"choices\": [oops"));
        RecordingSink sink = new RecordingSink();

        gateway(Runnable::run).streamChat("sk-x", endpoint(), request(), sink);

        assertEquals(StreamOutcome.Kind.FAILED, sink.terminal.kind);
        assertEquals(AiGatewayError.Kind.PARSE_FAILED, sink.terminal.error.kind);
    }

    @Test
    void anInBandErrorFrameIsClassifiedAsProviderUnavailable() {
        script = exchange -> writeSse(exchange, List.of(
                "data: {\"error\":{\"message\":\"insufficient balance\"}}"));
        RecordingSink sink = new RecordingSink();

        gateway(Runnable::run).streamChat("sk-x", endpoint(), request(), sink);

        assertEquals(StreamOutcome.Kind.FAILED, sink.terminal.kind);
        assertEquals(AiGatewayError.Kind.PROVIDER_UNAVAILABLE, sink.terminal.error.kind);
    }

    @Test
    void aReadFailureMidStreamIsClassifiedAsNetworkInterrupted() {
        byte[] firstChunk = "data: {\"choices\":[{\"delta\":{\"content\":\"半\"}}]}\n\n"
                .getBytes(StandardCharsets.UTF_8);
        GatewayPorts.Transport flaky = new GatewayPorts.Transport() {
            @Override
            public GatewayPorts.Response post(String url, String apiKey, byte[] body) {
                return new GatewayPorts.Response() {
                    private boolean served;
                    private final InputStream stream = new InputStream() {
                        @Override
                        public int read() throws IOException {
                            throw new IOException("connection reset");
                        }

                        @Override
                        public int read(byte[] target, int offset, int length) throws IOException {
                            if (!served) {
                                served = true;
                                System.arraycopy(firstChunk, 0, target, offset, firstChunk.length);
                                return firstChunk.length;
                            }
                            throw new IOException("connection reset");
                        }
                    };

                    @Override
                    public int status() {
                        return 200;
                    }

                    @Override
                    public InputStream body() {
                        return stream;
                    }

                    @Override
                    public void close() {
                    }
                };
            }

            @Override
            public GatewayPorts.Response get(String url, String apiKey) {
                throw new UnsupportedOperationException();
            }
        };
        RecordingSink sink = new RecordingSink();

        new AiGateway(flaky, new JacksonChatRequestBody(), new JacksonProviderJson.Deltas(),
                new JacksonProviderJson.Balance(), new JacksonProviderJson.ModelList(),
                (name, details) -> { }, Runnable::run).streamChat("sk-x", endpoint(), request(), sink);

        assertEquals(StreamOutcome.Kind.FAILED, sink.terminal.kind);
        assertEquals(AiGatewayError.Kind.NETWORK_INTERRUPTED, sink.terminal.error.kind);
        assertEquals(List.of("半"), sink.answer, "content delivered before the failure is kept");
    }

    @Test
    void cancelIsDistinctFromAStreamEnd() throws Exception {
        CountDownLatch firstDelta = new CountDownLatch(1);
        CountDownLatch hold = new CountDownLatch(1);
        script = exchange -> {
            exchange.getResponseHeaders().add("Content-Type", "text/event-stream; charset=utf-8");
            exchange.sendResponseHeaders(200, 0);
            OutputStream out = exchange.getResponseBody();
            out.write("data: {\"choices\":[{\"delta\":{\"content\":\"慢\"}}]}\n\n"
                    .getBytes(StandardCharsets.UTF_8));
            out.flush();
            await(hold);
        };
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            RecordingSink sink = new RecordingSink(firstDelta);
            AiGateway.Call call = gateway(pool).streamChat("sk-x", endpoint(), request(), sink);

            assertTrue(firstDelta.await(5, TimeUnit.SECONDS), "the first delta must arrive");
            call.cancel();
            hold.countDown();
            assertTrue(sink.terminalLatch.await(5, TimeUnit.SECONDS), "a terminal outcome must arrive");

            assertEquals(StreamOutcome.Kind.CANCELED, sink.terminal.kind);
            assertEquals(null, sink.terminal.error);
        } finally {
            pool.shutdownNow();
        }
    }

    private AiGateway gateway(java.util.concurrent.Executor executor) {
        return new AiGateway(new LoopbackTransport(port), new JacksonChatRequestBody(),
                new JacksonProviderJson.Deltas(), new JacksonProviderJson.Balance(),
                new JacksonProviderJson.ModelList(), (name, details) -> { }, executor);
    }

    private static ProviderEndpoint endpoint() {
        return ProviderEndpoint.of("https://127.0.0.1", "test-model");
    }

    private static DeepSeekRequest request() {
        return DeepSeekRequestAssembler.assemble(PromptPurpose.LEARNING_ANSWER, false, List.of(),
                "测试问题", new PromptBudget(1_000_000, 512));
    }

    private static void writeSse(HttpExchange exchange, List<String> sseLines) throws IOException {
        exchange.getResponseHeaders().add("Content-Type", "text/event-stream; charset=utf-8");
        exchange.sendResponseHeaders(200, 0);
        OutputStream out = exchange.getResponseBody();
        for (String line : sseLines) {
            out.write((line + "\n\n").getBytes(StandardCharsets.UTF_8));
            out.flush();
        }
        out.close();
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    @FunctionalInterface
    private interface Handler {
        void handle(HttpExchange exchange) throws IOException;
    }

    /** Records the callbacks so the test can assert the increment sequence and the single terminal. */
    private static final class RecordingSink implements GenerationSink {

        private final List<String> reasoning = Collections.synchronizedList(new ArrayList<>());
        private final List<String> answer = Collections.synchronizedList(new ArrayList<>());
        private final CountDownLatch terminalLatch = new CountDownLatch(1);
        private final CountDownLatch firstDelta;
        private volatile StreamOutcome terminal;

        RecordingSink() {
            this(null);
        }

        RecordingSink(CountDownLatch firstDelta) {
            this.firstDelta = firstDelta;
        }

        @Override
        public boolean onAccepted(int httpStatus) {
            return true;
        }

        @Override
        public void onDelta(String reasoningChunk, String contentChunk) {
            if (!reasoningChunk.isEmpty()) {
                reasoning.add(reasoningChunk);
            }
            if (!contentChunk.isEmpty()) {
                answer.add(contentChunk);
            }
            if (firstDelta != null) {
                firstDelta.countDown();
            }
        }

        @Override
        public void onTerminal(StreamOutcome outcome) {
            terminal = outcome;
            terminalLatch.countDown();
        }
    }

    /** Rewrites any provider URL to the loopback stub port; the shared HttpsTransport stays production-only. */
    private static final class LoopbackTransport implements GatewayPorts.Transport {

        private final int port;

        LoopbackTransport(int port) {
            this.port = port;
        }

        @Override
        public GatewayPorts.Response post(String url, String apiKey, byte[] body) throws IOException {
            return open("POST", url, apiKey, body);
        }

        @Override
        public GatewayPorts.Response get(String url, String apiKey) throws IOException {
            return open("GET", url, apiKey, null);
        }

        private GatewayPorts.Response open(String method, String url, String apiKey, byte[] body)
                throws IOException {
            String path = URI.create(url).getPath();
            HttpURLConnection connection =
                    (HttpURLConnection) new URL("http://127.0.0.1:" + port + path).openConnection();
            connection.setRequestMethod(method);
            connection.setConnectTimeout(5_000);
            connection.setReadTimeout(10_000);
            connection.setUseCaches(false);
            connection.setRequestProperty("Authorization", "Bearer " + apiKey);
            if (body != null) {
                connection.setDoOutput(true);
                connection.setFixedLengthStreamingMode(body.length);
                try (OutputStream out = connection.getOutputStream()) {
                    out.write(body);
                }
            }
            int status = connection.getResponseCode();
            InputStream stream = status / 100 == 2 ? connection.getInputStream()
                    : new ByteArrayInputStream(new byte[0]);
            return new LoopbackResponse(connection, status, stream);
        }
    }

    private static final class LoopbackResponse implements GatewayPorts.Response {

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
}
