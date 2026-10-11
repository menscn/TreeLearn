package com.treelearn.web.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.treelearn.ai.gateway.AiGatewayError;
import com.treelearn.ai.gateway.GenerationSink;
import com.treelearn.ai.gateway.StreamOutcome;
import com.treelearn.ai.prompt.DeepSeekRequest;
import com.treelearn.ai.prompt.DeepSeekRequestAssembler;
import com.treelearn.ai.prompt.PromptBudget;
import com.treelearn.core.domain.Branch;
import com.treelearn.core.domain.GenerationCommand;
import com.treelearn.core.domain.GenerationModel;
import com.treelearn.core.domain.Message;
import com.treelearn.core.domain.PromptPurpose;
import com.treelearn.core.domain.Topic;
import com.treelearn.web.api.dto.ApiJson;
import com.treelearn.web.generation.DesktopGenerationCoordinator;
import com.treelearn.web.generation.DesktopGenerationGateway;
import com.treelearn.web.generation.GenerationEventBus;
import com.treelearn.web.storage.AnswerRevisionStore;
import com.treelearn.web.storage.BranchStore;
import com.treelearn.web.storage.MessageStore;
import com.treelearn.web.storage.OperationStore;
import com.treelearn.web.storage.TopicStore;
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
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.JsonNode;

/**
 * T-2026-10-10-026: SSE 端点 {@code GET /api/operations/{id}/events} 的真实 HTTP 流测试（契约 §7）。
 *
 * <p>测试通过真实 Spring 上下文（{@code RANDOM_PORT}）与 {@link java.net.http.HttpClient} 逐帧读取
 * {@code text/event-stream}，用 stub 网关驱动真实操作（对齐 {@code GenerationCoordinatorTest} 手法）。
 * 心跳间隔用配置属性注入为短间隔；生产默认 15s 不被改动。
 *
 * <p>六个 fixtures 场景映射：stream-stable→{@link #normalStreamEmitsSnapshotThenDeltasThenStableAndCloses}、
 * stream-failed→{@link #failedStreamEmitsFailedTerminalWithErrorCode}、
 * stream-interrupted→{@link #interruptedStreamEmitsInterruptedTerminal}、
 * stream-canceled→{@link #canceledStreamEmitsCanceledTerminal}、
 * reconnect-gap-snapshot→{@link #aGapBeforeTheBufferStartPublishesASnapshotFirst}、
 * late-duplicate-ignored→客户端去重规则（服务端保证 seq 严格递增、按 operationId 过滤，见 bus 测试与
 * {@link #normalStreamEmitsSnapshotThenDeltasThenStableAndCloses} 的 seq 断言）。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OperationEventsControllerTest {

    private static final long T0 = 1_752_000_000_000L;
    private static final long WAIT_MILLIS = 5_000L;

    @TempDir
    static Path dataDir;

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("treelearn.data-dir", () -> dataDir.toString().replace('\\', '/'));
        registry.add("treelearn.sse.keepalive-millis", () -> "80");
    }

    @LocalServerPort
    int port;

    @Autowired
    GenerationEventBus bus;
    @Autowired
    com.treelearn.web.storage.JdbcSupport jdbc;
    @Autowired
    TopicStore topics;
    @Autowired
    BranchStore branches;
    @Autowired
    MessageStore messages;
    @Autowired
    OperationStore operations;
    @Autowired
    AnswerRevisionStore revisions;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private DeepSeekRequest request;
    private FakeGateway gateway;
    private DesktopGenerationCoordinator coordinator;

    // ------------------------------------------------------------------
    // Fixtures: a real accepted + submitted operation on real SQLite.
    // ------------------------------------------------------------------

    private void newCoordinator() {
        request = DeepSeekRequestAssembler.assemble(PromptPurpose.LEARNING_ANSWER, false, List.of(),
                "问题", new PromptBudget(1_000_000, 512));
        gateway = new FakeGateway();
        coordinator = new DesktopGenerationCoordinator(jdbc, topics, branches, messages,
                operations, bus, gateway, Clock.systemUTC());
    }

    private record Started(String operationId, FakeGateway.Call call) {}

    private Started start() {
        newCoordinator();
        Topic topic = Topic.create(T0);
        Branch root = Branch.root(topic.id, T0);
        Message user = Message.user(root.id, 0, "问题", "", T0);
        Message ai = Message.ai(root.id, 1, T0);
        GenerationCommand command = GenerationCommand.create(root.id, ai.id,
                PromptPurpose.LEARNING_ANSWER, request.policyVersion, GenerationModel.FLASH, false, T0)
                .value();
        DesktopGenerationCoordinator.AcceptOutcome accept = coordinator.acceptForNewTopic(command,
                topic, root, user, ai);
        assertTrue(accept.isAccepted(), () -> "accept refused: " + accept.refusal);
        DesktopGenerationCoordinator.SubmitOutcome submit = coordinator.submit(command, c -> request);
        assertTrue(submit.started, () -> "submit refused: " + submit.refusal);
        return new Started(submit.operationId, gateway.last());
    }

    // ------------------------------------------------------------------
    // Cases.
    // ------------------------------------------------------------------

    @Test
    void normalStreamEmitsSnapshotThenDeltasThenStableAndCloses() throws Exception {
        Started started = start();
        try (SseClient sse = open(started.operationId, null)) {
            assertEquals(200, sse.status());
            assertTrue(sse.contentType().contains("text/event-stream"),
                    "the endpoint must answer text/event-stream, was: " + sse.contentType());

            Frame snapshot = sse.nextFrame(WAIT_MILLIS);
            assertEquals("snapshot", snapshot.event(), "the first frame must be a snapshot");
            assertEquals("id:", snapshot.firstPrefix(0), "each frame writes an id: line");
            assertTrue(snapshot.hasPrefix("event:") && snapshot.hasPrefix("data:"),
                    "each frame writes event: and data: lines, was: " + snapshot.raw());
            assertTrue(snapshot.seq() >= 1, "seq must be a positive integer");
            assertEquals(started.operationId, snapshot.node("operationId").asText());

            FakeGateway.Call call = started.call();
            call.accept();
            call.delta("先明确场所。", "");
            call.delta("再说明能量转化。", "");
            call.delta("", "光合作用是植物利用光能");
            call.delta("", "把二氧化碳和水合成有机物并释放氧气的过程。");
            call.complete();

            List<Frame> frames = readUntil(sse, "stable");
            assertTrue(frames.stream().anyMatch(f -> f.event().equals("state")), "a state frame appears");
            assertTrue(frames.stream().anyMatch(f -> f.event().equals("reasoning_delta")));
            assertTrue(frames.stream().anyMatch(f -> f.event().equals("answer_delta")));

            List<Long> seqs = new ArrayList<>();
            seqs.add(snapshot.seq());
            frames.forEach(f -> seqs.add(f.seq()));
            for (int index = 1; index < seqs.size(); index++) {
                assertTrue(seqs.get(index) > seqs.get(index - 1),
                        "seq must strictly increase within one operation: " + seqs);
            }

            Frame stable = frames.get(frames.size() - 1);
            JsonNode revision = stable.node("revision");
            assertNotNull(revision, "stable must carry the full revision object (contract §7.2)");
            assertEquals("光合作用是植物利用光能把二氧化碳和水合成有机物并释放氧气的过程。",
                    revision.get("answerContent").asText());
            assertEquals("deepseek-flash", revision.get("model").asText());

            assertTrue(sse.awaitEnd(WAIT_MILLIS), "the server closes the stream after the terminal frame");
        }
    }

    @Test
    void failedStreamEmitsFailedTerminalWithErrorCode() throws Exception {
        Started started = start();
        try (SseClient sse = open(started.operationId, null)) {
            assertEquals("snapshot", sse.nextFrame(WAIT_MILLIS).event());
            started.call().accept();
            started.call().delta("", "部分回答");
            started.call().fail(AiGatewayError.Kind.RATE_LIMITED, 429);

            List<Frame> frames = readUntil(sse, "failed");
            Frame failed = frames.get(frames.size() - 1);
            assertEquals("RATE_LIMITED", failed.str("errorCode"));
            assertNotNull(failed.node("params"), "failed carries the safe params object (contract §7.2)");
            assertEquals(429, failed.node("params").get("httpStatus").asInt(),
                    "the provider status travels in params, matching the fixture (contract §3.3/§7.2)");
            assertTrue(sse.awaitEnd(WAIT_MILLIS));
        }
    }

    @Test
    void interruptedStreamEmitsInterruptedTerminal() throws Exception {
        Started started = start();
        try (SseClient sse = open(started.operationId, null)) {
            assertEquals("snapshot", sse.nextFrame(WAIT_MILLIS).event());
            started.call().accept();
            started.call().delta("", "半句");
            started.call().interrupt();

            readUntil(sse, "interrupted");
            assertTrue(sse.awaitEnd(WAIT_MILLIS));
        }
    }

    @Test
    void canceledStreamEmitsCanceledTerminal() throws Exception {
        Started started = start();
        try (SseClient sse = open(started.operationId, null)) {
            assertEquals("snapshot", sse.nextFrame(WAIT_MILLIS).event());
            started.call().accept();
            started.call().delta("", "部分内容");
            assertTrue(coordinator.cancel(started.operationId));

            readUntil(sse, "canceled");
            assertTrue(sse.awaitEnd(WAIT_MILLIS));
        }
    }

    @Test
    void reconnectWithinTheBufferReplaysMissingFramesWithoutASnapshot() throws Exception {
        Started started = start();
        started.call().accept();
        started.call().delta("想", "");
        started.call().delta("", "答一");
        long tail = tailSeq(started.operationId);
        long from = tail - 2; // inside the retained window, no gap

        try (SseClient sse = open(started.operationId, Long.toString(from))) {
            Frame first = sse.nextFrame(WAIT_MILLIS);
            assertNotEquals("snapshot", first.event(),
                    "a resume inside the buffer continues the frames instead of re-snapshotting");
            assertTrue(first.seq() > from, "only frames after Last-Event-ID are replayed");

            started.call().complete();
            readUntil(sse, "stable");
            assertTrue(sse.awaitEnd(WAIT_MILLIS));
        }
    }

    @Test
    void aGapBeforeTheBufferStartPublishesASnapshotFirst() throws Exception {
        Started started = start();
        started.call().accept();
        int overflow = GenerationEventBus.BUFFER_CAPACITY + 20;
        for (int index = 0; index < overflow; index++) {
            started.call().delta("", "x");
        }

        try (SseClient sse = open(started.operationId, "1")) {
            Frame snapshot = sse.nextFrame(WAIT_MILLIS);
            assertEquals("snapshot", snapshot.event(), "a gap forces a snapshot (contract §7.3)");
            OperationStore.Operation row = operations.byId(started.operationId).orElseThrow();
            assertEquals(row.state.name(), snapshot.str("state"),
                    "the snapshot state matches the persisted fact");
            assertEquals(row.partialAnswer, snapshot.str("answerContent"),
                    "the snapshot carries the persisted partial answer");

            started.call().complete();
            List<Frame> stableFrames = readUntil(sse, "stable");
            Frame stable = stableFrames.get(stableFrames.size() - 1);
            assertTrue(stable.seq() > snapshot.seq());
            assertTrue(sse.awaitEnd(WAIT_MILLIS));
        }
    }

    @Test
    void subscribingAfterStableEmitsSnapshotThenStableThenCloses() throws Exception {
        Started started = start();
        started.call().accept();
        started.call().delta("想", "已成答案");
        started.call().complete();

        try (SseClient sse = open(started.operationId, null)) {
            Frame snapshot = sse.nextFrame(WAIT_MILLIS);
            assertEquals("snapshot", snapshot.event());
            assertEquals("STABLE", snapshot.str("state"));
            assertNotNull(snapshot.node("revision"),
                    "a terminal subscription still learns the revision from the snapshot");

            Frame stable = sse.nextFrame(WAIT_MILLIS);
            assertEquals("stable", stable.event(),
                    "an already-terminal subscription must still receive the terminal event frame, "
                            + "which is the only frame the client treats as terminal");
            assertTrue(stable.seq() > snapshot.seq(), "the terminal frame follows the snapshot in seq");
            assertNotNull(stable.node("revision"),
                    "the terminal stable frame carries the full revision object (contract §7.2)");
            assertEquals("已成答案", stable.node("revision").get("answerContent").asText());
            assertTrue(sse.awaitEnd(WAIT_MILLIS), "an already-terminal operation closes right away");
        }
    }

    @Test
    void subscribingAfterFailureEmitsSnapshotThenFailedThenCloses() throws Exception {
        Started started = start();
        started.call().accept();
        started.call().delta("", "部分回答");
        started.call().fail(AiGatewayError.Kind.RATE_LIMITED, 429);

        try (SseClient sse = open(started.operationId, null)) {
            Frame snapshot = sse.nextFrame(WAIT_MILLIS);
            assertEquals("snapshot", snapshot.event());
            assertEquals("FAILED", snapshot.str("state"));

            Frame failed = sse.nextFrame(WAIT_MILLIS);
            assertEquals("failed", failed.event(),
                    "an already-failed subscription must still receive the failed terminal frame");
            assertEquals("RATE_LIMITED", failed.str("errorCode"));
            assertNotNull(failed.node("params"),
                    "the terminal failed frame also carries the safe params object");
            assertTrue(sse.awaitEnd(WAIT_MILLIS));
        }
    }

    @Test
    void aMidStreamSubscriptionKeepsSeqContiguousFromTheSnapshot() throws Exception {
        Started started = start();
        started.call().accept();
        String operationId = started.operationId();
        java.util.concurrent.atomic.AtomicBoolean snapshotSeen =
                new java.util.concurrent.atomic.AtomicBoolean(false);

        // A publisher that hammers while the subscriber attaches: a live frame must not be sequenced
        // ahead of the snapshot, or the client sees a delta as its first frame and drops the catch-up.
        Thread publisher = new Thread(() -> {
            while (!snapshotSeen.get()) {
                bus.publish(operationId, GenerationEventBus.Type.ANSWER_DELTA, Map.of("delta", "x"));
            }
            bus.publish(operationId, GenerationEventBus.Type.STABLE, Map.of());
        }, "sse-race-publisher");
        publisher.start();

        List<Long> seqs = new ArrayList<>();
        try (SseClient sse = open(operationId, null)) {
            Frame snapshot = sse.nextFrame(WAIT_MILLIS);
            assertEquals("snapshot", snapshot.event(), "a fresh subscription starts from a snapshot");
            snapshotSeen.set(true);
            seqs.add(snapshot.seq());
            Frame frame = sse.nextFrame(WAIT_MILLIS);
            while (!frame.event().equals("stable")) {
                seqs.add(frame.seq());
                frame = sse.nextFrame(WAIT_MILLIS);
            }
            seqs.add(frame.seq());
            assertTrue(sse.awaitEnd(WAIT_MILLIS));
        } finally {
            snapshotSeen.set(true);
            publisher.join(WAIT_MILLIS);
        }
        for (int index = 1; index < seqs.size(); index++) {
            assertEquals(seqs.get(index - 1) + 1, seqs.get(index),
                    "the subscriber must see one contiguous seq run from the snapshot, no gaps: " + seqs);
        }
        assertTrue(seqs.size() >= 2, "at least the snapshot and the terminal frame are observed");
    }

    @Test
    void unknownOperationReturnsTheErrorEnvelope() throws Exception {
        HttpResponse<String> response = httpClient.send(
                HttpRequest.newBuilder(URI.create(baseUrl() + "/api/operations/"
                        + UUID.randomUUID() + "/events")).GET().build(),
                HttpResponse.BodyHandlers.ofString());

        assertEquals(404, response.statusCode(), "body was: " + response.body());
        JsonNode body = ApiJson.mapper().readTree(response.body());
        assertEquals("NOT_FOUND", body.get("error").get("code").asText());
        assertEquals("operation", body.get("error").get("params").get("resource").asText());
        assertFalse(body.get("error").get("retryable").asBoolean());
        assertFalse(body.get("requestId").asText().isBlank(),
                "the error envelope carries a transport requestId (contract §3.1)");
    }

    @Test
    void keepaliveCommentAppearsDuringSilence() throws Exception {
        Started started = start();
        try (SseClient sse = open(started.operationId, null)) {
            assertEquals("snapshot", sse.nextFrame(WAIT_MILLIS).event());
            Comment comment = sse.nextComment(WAIT_MILLIS);
            assertTrue(comment.text().startsWith(":"),
                    "a heartbeat is a comment line, was: " + comment.text());
        }
    }

    @Test
    void closingTheClientClearsTheBusSubscription() throws Exception {
        Started started = start();
        SseClient sse = open(started.operationId, null);
        assertEquals("snapshot", sse.nextFrame(WAIT_MILLIS).event());
        assertTrue(waitUntil(() -> bus.listenerCount(started.operationId) >= 1, WAIT_MILLIS),
                "the subscriber is attached while it reads");

        sse.close();
        assertTrue(waitUntil(() -> bus.listenerCount(started.operationId) == 0, WAIT_MILLIS),
                "the server must unsubscribe once the client goes away (no leak)");
    }

    @Test
    void capturesTheRawWireFormatForEvidence() throws Exception {
        Started started = start();
        SseClient sse = open(started.operationId, null);
        started.call().accept();
        started.call().delta("想", "");
        started.call().delta("", "答");
        started.call().complete();
        assertTrue(sse.awaitEnd(WAIT_MILLIS));
        for (String line : sse.wire()) {
            System.out.println("[SSE-RAW] " + line);
        }
        sse.close();
    }

    // ------------------------------------------------------------------
    // Helpers.
    // ------------------------------------------------------------------

    private String baseUrl() {
        return "http://127.0.0.1:" + port;
    }

    private SseClient open(String operationId, String lastEventId) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(
                        URI.create(baseUrl() + "/api/operations/" + operationId + "/events"))
                .GET().timeout(Duration.ofSeconds(30));
        if (lastEventId != null) {
            builder.header("Last-Event-ID", lastEventId);
        }
        HttpResponse<InputStream> response = httpClient.send(builder.build(),
                HttpResponse.BodyHandlers.ofInputStream());
        return new SseClient(response);
    }

    private long tailSeq(String operationId) {
        List<GenerationEventBus.Event> buffered = bus.replaySince(operationId, 0).events;
        return buffered.isEmpty() ? 0 : buffered.get(buffered.size() - 1).seq;
    }

    private List<Frame> readUntil(SseClient sse, String terminalEvent) throws InterruptedException {
        List<Frame> frames = new ArrayList<>();
        Frame frame = sse.nextFrame(WAIT_MILLIS);
        frames.add(frame);
        while (!frame.event().equals(terminalEvent)) {
            frame = sse.nextFrame(WAIT_MILLIS);
            frames.add(frame);
        }
        return frames;
    }

    private static boolean waitUntil(BooleanSupplier condition, long millis) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(millis);
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) {
                return true;
            }
            Thread.sleep(20);
        }
        return condition.getAsBoolean();
    }

    /** One parsed SSE frame: the raw lines plus the id/event and the JSON {@code data}. */
    private record Frame(List<String> raw, String id, String event, JsonNode data) {

        static Frame parse(List<String> lines) {
            String id = null;
            String event = null;
            StringBuilder data = new StringBuilder();
            for (String line : lines) {
                int colon = line.indexOf(':');
                String key = colon < 0 ? line : line.substring(0, colon);
                String value = colon < 0 ? "" : line.substring(colon + 1);
                if (value.startsWith(" ")) {
                    value = value.substring(1);
                }
                switch (key) {
                    case "id" -> id = value;
                    case "event" -> event = value;
                    case "data" -> data.append(value);
                    default -> { }
                }
            }
            JsonNode node = data.isEmpty() ? ApiJson.mapper().missingNode()
                    : ApiJson.mapper().readTree(data.toString());
            return new Frame(List.copyOf(lines), id, event, node);
        }

        long seq() {
            return data.get("seq").asLong();
        }

        String str(String key) {
            JsonNode value = data.get(key);
            return value == null || value.isNull() ? null : value.asText();
        }

        JsonNode node(String key) {
            return data.get(key);
        }

        String firstPrefix(int index) {
            String line = raw.get(index);
            int colon = line.indexOf(':');
            return colon < 0 ? line : line.substring(0, colon + 1);
        }

        boolean hasPrefix(String prefix) {
            return raw.stream().anyMatch(line -> line.startsWith(prefix));
        }
    }

    /** A standalone SSE comment line (the heartbeat). */
    private record Comment(String text) {}

    /** End-of-stream marker pushed by the reader thread. */
    private enum End { INSTANCE }

    /** Reads one HTTP SSE response on a background thread and exposes frames/comments by polling. */
    private static final class SseClient implements AutoCloseable {

        private final HttpResponse<InputStream> response;
        private final BufferedReader reader;
        private final BlockingQueue<Object> items = new LinkedBlockingQueue<>();
        private final List<String> wire = java.util.Collections.synchronizedList(new ArrayList<>());
        private final Thread pump;

        SseClient(HttpResponse<InputStream> response) {
            this.response = response;
            this.reader = new BufferedReader(new InputStreamReader(response.body(), StandardCharsets.UTF_8));
            this.pump = new Thread(this::read, "sse-test-reader");
            this.pump.setDaemon(true);
            this.pump.start();
        }

        /** Every non-empty raw line this client received, for the GREEN wire-format evidence. */
        List<String> wire() {
            synchronized (wire) {
                return List.copyOf(wire);
            }
        }

        int status() {
            return response.statusCode();
        }

        String contentType() {
            return response.headers().firstValue("content-type").orElse("");
        }

        private void read() {
            try {
                List<String> current = new ArrayList<>();
                String line;
                while ((line = reader.readLine()) != null) {
                    if (!line.isEmpty()) {
                        wire.add(line);
                    }
                    if (line.isEmpty()) {
                        if (!current.isEmpty()) {
                            items.add(Frame.parse(current));
                            current.clear();
                        }
                    } else if (line.startsWith(":")) {
                        items.add(new Comment(line));
                    } else {
                        current.add(line);
                    }
                }
            } catch (IOException | RuntimeException ignored) {
                // The client closed the socket, or the frame did not parse: the poll side reports it.
            } finally {
                items.add(End.INSTANCE);
            }
        }

        Frame nextFrame(long millis) throws InterruptedException {
            long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(millis);
            while (true) {
                long remaining = (deadline - System.nanoTime()) / 1_000_000L;
                Object item = items.poll(Math.max(remaining, 0), TimeUnit.MILLISECONDS);
                if (item == null) {
                    fail("timed out waiting for an SSE frame");
                } else if (item instanceof Frame frame) {
                    return frame;
                } else if (item == End.INSTANCE) {
                    fail("the stream ended before the expected frame");
                }
            }
        }

        Comment nextComment(long millis) throws InterruptedException {
            long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(millis);
            while (true) {
                long remaining = (deadline - System.nanoTime()) / 1_000_000L;
                Object item = items.poll(Math.max(remaining, 0), TimeUnit.MILLISECONDS);
                if (item == null) {
                    fail("timed out waiting for a heartbeat comment");
                } else if (item instanceof Comment comment) {
                    return comment;
                } else if (item == End.INSTANCE) {
                    fail("the stream ended before a heartbeat comment");
                }
            }
        }

        boolean awaitEnd(long millis) throws InterruptedException {
            long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(millis);
            while (true) {
                long remaining = (deadline - System.nanoTime()) / 1_000_000L;
                Object item = items.poll(Math.max(remaining, 0), TimeUnit.MILLISECONDS);
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
                // already closed
            }
        }
    }

    /** A gateway the test drives by hand, mirroring the coordinator test's fake. */
    static final class FakeGateway implements DesktopGenerationGateway {

        private final List<Call> calls = new ArrayList<>();

        @Override
        public Stream open(GenerationModel model, DeepSeekRequest request, GenerationSink sink) {
            Call call = new Call(sink);
            calls.add(call);
            return call;
        }

        Call last() {
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

            void fail(AiGatewayError.Kind kind, int status) {
                sink.onTerminal(new StreamOutcome(StreamOutcome.Kind.FAILED,
                        AiGatewayError.of(kind, status, "provider failed"), status, 0, 0));
            }

            void interrupt() {
                sink.onTerminal(new StreamOutcome(StreamOutcome.Kind.INTERRUPTED, null, 0, 0, 0));
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
}
