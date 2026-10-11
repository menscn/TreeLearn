package com.treelearn.web.generation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.treelearn.ai.gateway.AiGateway;
import com.treelearn.ai.gateway.GatewayPorts;
import com.treelearn.ai.prompt.DeepSeekRequestAssembler;
import com.treelearn.ai.prompt.PromptBudget;
import com.treelearn.core.domain.Branch;
import com.treelearn.core.domain.GenerationCommand;
import com.treelearn.core.domain.GenerationModel;
import com.treelearn.core.domain.GenerationState;
import com.treelearn.core.domain.Message;
import com.treelearn.core.domain.PromptPurpose;
import com.treelearn.core.domain.Topic;
import com.treelearn.web.ai.JacksonChatRequestBody;
import com.treelearn.web.ai.JacksonProviderJson;
import com.treelearn.web.storage.AnswerRevisionStore;
import com.treelearn.web.storage.BranchStore;
import com.treelearn.web.storage.DatabaseMigrator;
import com.treelearn.web.storage.DesktopDatabase;
import com.treelearn.web.storage.JdbcSupport;
import com.treelearn.web.storage.MessageStore;
import com.treelearn.web.storage.OperationStore;
import com.treelearn.web.storage.TopicStore;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * T-2026-10-10-024: the real end-to-end path — a loopback {@code HttpServer} serving DeepSeek SSE, the
 * shared {@link AiGateway} over a real socket, {@link AiGatewayStreams} as the port adapter, and the
 * coordinator persisting the rows and fanning the events out. Nothing touches the network beyond
 * {@code 127.0.0.1}, and the shared {@code HttpsTransport} stays production-only.
 */
class DesktopGenerationStubGatewayTest {

    private static final long T0 = 1_752_000_000_000L;

    @TempDir
    Path dataDir;

    private HttpServer server;
    private int port;
    private volatile Handler script;

    JdbcSupport jdbc;
    TopicStore topics;
    BranchStore branches;
    MessageStore messages;
    AnswerRevisionStore revisions;
    OperationStore operations;
    GenerationEventBus bus;
    DesktopGenerationCoordinator coordinator;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/chat/completions", exchange -> {
            try {
                script.handle(exchange);
            } catch (IOException clientAborted) {
                // the client may abort mid-write; the client outcome is asserted by the case
            } finally {
                exchange.close();
            }
        });
        server.start();
        port = server.getAddress().getPort();

        new DatabaseMigrator().migrate(dataDir.resolve("treelearn.db"));
        jdbc = new JdbcSupport(new DesktopDatabase(dataDir));
        topics = new TopicStore(jdbc);
        branches = new BranchStore(jdbc);
        messages = new MessageStore(jdbc);
        revisions = new AnswerRevisionStore(jdbc);
        operations = new OperationStore(jdbc);
        bus = new GenerationEventBus();
        AiGateway gateway = new AiGateway(new LoopbackTransport(port), new JacksonChatRequestBody(),
                new JacksonProviderJson.Deltas(), new JacksonProviderJson.Balance(),
                new JacksonProviderJson.ModelList(), (name, details) -> { }, Runnable::run);
        coordinator = new DesktopGenerationCoordinator(jdbc, topics, branches, messages, operations,
                bus, new AiGatewayStreams(gateway, "https://127.0.0.1", "sk-test".toCharArray()),
                Clock.fixed(Instant.ofEpochMilli(T0), ZoneOffset.UTC));
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void aRealSseStreamOverALoopbackSocketStabilizesTheAnswer() throws Exception {
        script = exchange -> writeSse(exchange, List.of(
                "data: {\"choices\":[{\"delta\":{\"reasoning_content\":\"先想\"}}]}",
                "data: {\"choices\":[{\"delta\":{\"content\":\"答案正文\"}}]}",
                "data: [DONE]"));

        Topic topic = Topic.create(T0);
        Branch root = Branch.root(topic.id, T0);
        Message user = Message.user(root.id, 0, "问题", "", T0);
        Message ai = Message.ai(root.id, 1, T0);
        GenerationCommand command = GenerationCommand.create(root.id, ai.id,
                PromptPurpose.LEARNING_ANSWER, policy(), GenerationModel.FLASH, false, T0).value();

        assertTrue(coordinator.acceptForNewTopic(command, topic, root, user, ai).isAccepted());
        String operationId = operations.byCommandId(command.id).orElseThrow().id;
        Recorder recorder = new Recorder();
        bus.subscribe(operationId, recorder);

        DesktopGenerationCoordinator.SubmitOutcome outcome = coordinator.submit(command,
                forCommand -> DeepSeekRequestAssembler.assemble(forCommand.purpose,
                        forCommand.thinkingEnabled, List.of(), "问题",
                        new PromptBudget(1_000_000, 512)));

        assertTrue(outcome.started);
        assertTrue(recorder.closedLatch.await(5, TimeUnit.SECONDS), "the stream must reach a terminal");
        assertTrue(recorder.saw(GenerationEventBus.Type.REASONING_DELTA));
        assertTrue(recorder.saw(GenerationEventBus.Type.ANSWER_DELTA));
        assertTrue(recorder.saw(GenerationEventBus.Type.STABLE));

        OperationStore.Operation stored = operations.byCommandId(command.id).orElseThrow();
        assertEquals(GenerationState.STABLE, stored.state);
        assertEquals("答案正文", stored.partialAnswer);
        assertEquals("先想", stored.partialReasoning);
        assertEquals(1, revisions.countForMessage(ai.id));
        assertEquals(revisions.findActiveForMessage(ai.id).orElseThrow().id,
                messages.get(ai.id).orElseThrow().activeRevisionId());
        assertEquals(1L, branches.get(root.id).orElseThrow().latestStableSequence());
    }

    private static String policy() {
        return DeepSeekRequestAssembler.assemble(PromptPurpose.LEARNING_ANSWER, false, List.of(),
                "q", new PromptBudget(1_000_000, 512)).policyVersion;
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

    @FunctionalInterface
    private interface Handler {
        void handle(HttpExchange exchange) throws IOException;
    }

    /** Records the frames and signals the single terminal close. */
    private static final class Recorder implements GenerationEventBus.Listener {

        final List<GenerationEventBus.Event> events = new ArrayList<>();
        final CountDownLatch closedLatch = new CountDownLatch(1);

        @Override
        public void onEvent(GenerationEventBus.Event event) {
            events.add(event);
        }

        @Override
        public void onClosed() {
            closedLatch.countDown();
        }

        boolean saw(GenerationEventBus.Type type) {
            return events.stream().anyMatch(event -> event.type == type);
        }
    }

    /** Rewrites any provider URL to the loopback stub port; the shared HttpsTransport stays production. */
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
