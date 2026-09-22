package com.treelearn.ai.gateway;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.treelearn.ai.prompt.DeepSeekMessage;
import com.treelearn.ai.prompt.DeepSeekPromptRegistry;
import com.treelearn.ai.prompt.DeepSeekRequest;
import com.treelearn.ai.prompt.DeepSeekRequestAssembler;
import com.treelearn.ai.prompt.PromptBudget;
import com.treelearn.core.domain.GenerationModel;
import com.treelearn.core.domain.PromptPurpose;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.Test;

/**
 * The stream contract of {@link AiGateway} (technical design 9.1 - 9.5): the accepted gate, the
 * arrival order of the two channels, the terminal outcome and cancellation.
 *
 * <p>The provider's JSON is replaced by a scripted decoder here, because {@code org.json} only
 * exists on the device; the real decoder is exercised by {@code :app}'s instrumentation suite. What
 * this class proves is everything above the payload: when a callback may run, in which order, and
 * what the terminal outcome says.
 *
 * <p>The fixtures use the payload format {@code <reasoning>#<content>}; an empty channel is the
 * empty string, and a payload with no {@code #} decodes to an empty delta.
 */
public class AiGatewayStreamTest {

    private static final Executor INLINE = Runnable::run;

    private final List<String> trace = new ArrayList<>();
    private final List<String> logLines = new ArrayList<>();
    private final GatewayPorts.Log log = (name, details) -> logLines.add(name + " " + details);
    private final FakeTransport transport = new FakeTransport(trace);
    private final ScriptedDecoder decoder = new ScriptedDecoder();
    private final GatewayPorts.BalanceDecoder balance = payload -> payload.contains("true");

    private AiGateway gateway(Executor executor) {
        return gateway(executor, (model, request) -> "{\"model\":\"fixture\"}".getBytes(UTF_8));
    }

    private AiGateway gateway(Executor executor, GatewayPorts.BodyEncoder encoder) {
        return new AiGateway(transport, encoder, decoder, balance, log, executor);
    }

    private static DeepSeekRequest request(boolean thinking) {
        return DeepSeekRequestAssembler.assemble(PromptPurpose.LEARNING_ANSWER, thinking,
                List.of(new DeepSeekMessage(DeepSeekMessage.Role.USER, "\u4e4b\u524d\u7684\u63d0\u95ee"),
                        new DeepSeekMessage(DeepSeekMessage.Role.ASSISTANT, "\u4e4b\u524d\u7684\u56de\u7b54")),
                "\u73b0\u5728\u7684\u95ee\u9898", new PromptBudget(1_000_000, 1000));
    }

    @Test
    public void acceptedIsReportedBeforeTheBodyIsReadAndBeforeAnyDelta() {
        transport.sseBody = "data: \u601d#\u7b54\n\ndata: [DONE]\n\n";
        RecordingSink sink = new RecordingSink();

        gateway(INLINE).streamChat("key", GenerationModel.FLASH, request(true), sink);

        int accepted = trace.indexOf("accepted:200");
        assertTrue("the sink must be told about the accepted response, trace=" + trace, accepted >= 0);
        assertTrue("the body must not be read before the caller confirmed acceptance, trace=" + trace,
                accepted < trace.indexOf("body"));
        assertEquals(List.of("\u601d"), sink.reasoning);
        assertEquals(List.of("\u7b54"), sink.answers);
        assertEquals(StreamOutcome.Kind.COMPLETED, sink.outcome.kind);
        assertEquals(1, sink.terminalCount);
        assertTrue(transport.transportClosed);
    }

    @Test
    public void refusedAcceptanceClosesTheConnectionWithoutReadingOrDeliveringAnything() {
        transport.sseBody = "data: \u601d#\u7b54\n\n";
        RecordingSink sink = new RecordingSink();
        sink.accept = false;

        gateway(INLINE).streamChat("key", GenerationModel.FLASH, request(false), sink);

        assertFalse("no delta may race ahead of the caller's local transaction", transport.bodyOpened);
        assertTrue(transport.transportClosed);
        assertTrue(sink.reasoning.isEmpty());
        assertTrue(sink.answers.isEmpty());
        assertEquals(StreamOutcome.Kind.CANCELED, sink.outcome.kind);
        assertNull(sink.outcome.error);
        assertEquals(1, sink.terminalCount);
    }

    @Test
    public void keepsEachChannelInArrivalOrderWhenTheyInterleave() {
        transport.sseBody = "data: \u601d1#\n\ndata: #\u7b541\n\ndata: \u601d2#\u7b542\n\n"
                + "data: [DONE]\n\n";
        RecordingSink sink = new RecordingSink();

        gateway(INLINE).streamChat("key", GenerationModel.FLASH, request(true), sink);

        assertEquals(List.of("\u601d1", "", "\u601d2"), sink.reasoning);
        assertEquals(List.of("", "\u7b541", "\u7b542"), sink.answers);
        assertEquals(StreamOutcome.Kind.COMPLETED, sink.outcome.kind);
        assertEquals(4, sink.outcome.reasoningChars);
        assertEquals(4, sink.outcome.answerChars);
        assertEquals(200, sink.outcome.httpStatus);
    }

    @Test
    public void eofWithoutTheDoneSentinelIsInterruptedAndKeepsWhatArrived() {
        transport.sseBody = "data: \u601d#\u7b54\n\n";
        RecordingSink sink = new RecordingSink();

        gateway(INLINE).streamChat("key", GenerationModel.FLASH, request(true), sink);

        assertEquals(StreamOutcome.Kind.INTERRUPTED, sink.outcome.kind);
        assertNull(sink.outcome.error);
        assertEquals(List.of("\u601d"), sink.reasoning);
        assertEquals(1, sink.terminalCount);
        assertTrue(transport.transportClosed);
    }

    @Test
    public void completedIsReportedEvenWhenNoAnswerTextArrived() {
        transport.sseBody = "data: [DONE]\n\n";
        RecordingSink sink = new RecordingSink();

        gateway(INLINE).streamChat("key", GenerationModel.FLASH, request(true), sink);

        assertEquals(StreamOutcome.Kind.COMPLETED, sink.outcome.kind);
        assertNull(sink.outcome.error);
        assertEquals(0, sink.outcome.answerChars);
    }

    @Test
    public void transportFailureIsClassifiedAsNetworkInterrupted() {
        transport.failOnOpen = true;
        RecordingSink sink = new RecordingSink();

        gateway(INLINE).streamChat("key", GenerationModel.FLASH, request(true), sink);

        assertEquals(StreamOutcome.Kind.FAILED, sink.outcome.kind);
        assertEquals(AiGatewayError.Kind.NETWORK_INTERRUPTED, sink.outcome.error.kind);
        assertEquals(0, sink.outcome.error.httpStatus);
        assertEquals(1, sink.terminalCount);
        assertFalse(sink.accepted);
    }

    @Test
    public void providerStatusesMapToStableKindsAndTheirBodiesAreNeverRead() {
        Map<Integer, AiGatewayError.Kind> expected = new LinkedHashMap<>();
        expected.put(400, AiGatewayError.Kind.REQUEST_INVALID);
        expected.put(401, AiGatewayError.Kind.AUTH_INVALID);
        expected.put(402, AiGatewayError.Kind.BALANCE_UNAVAILABLE);
        expected.put(403, AiGatewayError.Kind.AUTH_INVALID);
        expected.put(422, AiGatewayError.Kind.REQUEST_INVALID);
        expected.put(429, AiGatewayError.Kind.RATE_LIMITED);
        expected.put(500, AiGatewayError.Kind.PROVIDER_UNAVAILABLE);
        expected.put(503, AiGatewayError.Kind.PROVIDER_UNAVAILABLE);

        for (Map.Entry<Integer, AiGatewayError.Kind> entry : expected.entrySet()) {
            transport.status = entry.getKey();
            transport.sseBody = "{\"error\":{\"message\":\"secret provider text\"}}";
            RecordingSink sink = new RecordingSink();

            gateway(INLINE).streamChat("key", GenerationModel.FLASH, request(true), sink);

            assertEquals("status " + entry.getKey(), StreamOutcome.Kind.FAILED, sink.outcome.kind);
            assertEquals("status " + entry.getKey(), entry.getValue(), sink.outcome.error.kind);
            assertEquals("status " + entry.getKey(), entry.getKey().intValue(),
                    sink.outcome.error.httpStatus);
            assertFalse("status " + entry.getKey(), sink.accepted);
            assertFalse("a failed response body must never be read", transport.bodyOpened);
            assertTrue(transport.transportClosed);
            assertFalse(sink.outcome.error.detail.contains("secret provider text"));
            assertEquals(1, sink.terminalCount);
        }
    }

    @Test
    public void payloadRefusalFailsClosedAndKeepsTheEarlierDeltas() {
        decoder.refuse("BAD");
        transport.sseBody = "data: \u601d1#\n\ndata: BAD\n\ndata: \u601d2#\u7b542\n\n";
        RecordingSink sink = new RecordingSink();

        gateway(INLINE).streamChat("key", GenerationModel.FLASH, request(true), sink);

        assertEquals(StreamOutcome.Kind.FAILED, sink.outcome.kind);
        assertEquals(AiGatewayError.Kind.PARSE_FAILED, sink.outcome.error.kind);
        assertEquals(List.of("\u601d1"), sink.reasoning);
        assertEquals("events after the refusal must not be delivered", List.of(""), sink.answers);
        assertFalse("a diagnostic must not echo the provider payload",
                sink.outcome.error.detail.contains("BAD"));
        assertEquals(1, sink.terminalCount);
    }

    @Test
    public void anUnexpectedDecoderFailureStillFailsClosed() {
        decoder.blowUpOn("BOOM");
        transport.sseBody = "data: BOOM\n\n";
        RecordingSink sink = new RecordingSink();

        gateway(INLINE).streamChat("key", GenerationModel.FLASH, request(true), sink);

        assertEquals(StreamOutcome.Kind.FAILED, sink.outcome.kind);
        assertEquals(AiGatewayError.Kind.PARSE_FAILED, sink.outcome.error.kind);
        assertEquals(1, sink.terminalCount);
    }

    @Test
    public void anEmptyDeltaIsNotDelivered() {
        transport.sseBody = "data: no-separator\n\ndata: #\n\ndata: [DONE]\n\n";
        RecordingSink sink = new RecordingSink();

        gateway(INLINE).streamChat("key", GenerationModel.FLASH, request(true), sink);

        assertTrue("empty deltas carry nothing to append: " + sink.reasoning + sink.answers,
                sink.reasoning.isEmpty() && sink.answers.isEmpty());
        assertEquals(StreamOutcome.Kind.COMPLETED, sink.outcome.kind);
    }

    @Test
    public void aMissingKeyFailsBeforeAnyConnectionIsOpened() {
        RecordingSink sink = new RecordingSink();

        gateway(INLINE).streamChat("   ", GenerationModel.FLASH, request(true), sink);

        assertEquals(StreamOutcome.Kind.FAILED, sink.outcome.kind);
        assertEquals(AiGatewayError.Kind.KEY_MISSING, sink.outcome.error.kind);
        assertNull(transport.requestedUrl);
        assertFalse(transport.bodyOpened);
        assertEquals(1, sink.terminalCount);
    }

    @Test
    public void aRequestBodyThatCannotBeEncodedFailsClosedAsAnInvalidRequest() {
        RecordingSink sink = new RecordingSink();
        AiGateway gateway = gateway(INLINE, (model, request) -> {
            throw new IllegalStateException("fixture: the body could not be encoded");
        });

        gateway.streamChat("key", GenerationModel.FLASH, request(true), sink);

        assertEquals(StreamOutcome.Kind.FAILED, sink.outcome.kind);
        assertEquals(AiGatewayError.Kind.REQUEST_INVALID, sink.outcome.error.kind);
        assertNull(transport.requestedUrl);
        assertEquals(1, sink.terminalCount);
    }

    @Test
    public void transportsTheEncodedBodyAndTheKeyUntouched() {
        byte[] body = "{\"model\":\"deepseek-flash\",\"stream\":true}".getBytes(UTF_8);
        RecordingSink sink = new RecordingSink();

        new AiGateway(transport, (model, request) -> body, decoder, balance, log, INLINE)
                .streamChat("secret-key", GenerationModel.FLASH, request(true), sink);

        assertArrayEquals(body, transport.requestedBody);
        assertEquals("secret-key", transport.requestedKey);
        assertEquals(GatewayEndpoints.chatUrl(), transport.requestedUrl);
        assertEquals("{\"model\":\"deepseek-flash\",\"stream\":true}",
                new String(transport.requestedBody, UTF_8));
    }

    @Test
    public void logsCarryMetadataOnlyNeverTheKeyThePromptOrTheStreamedText() {
        transport.sseBody = "data: \u63a8\u7406\u6b63\u6587#\u56de\u7b54\u6b63\u6587\n\n"
                + "data: [DONE]\n\n";
        RecordingSink sink = new RecordingSink();

        gateway(INLINE).streamChat("SECRET-KEY-0001", GenerationModel.FLASH, request(true), sink);

        String joined = String.join("\n", logLines);
        String systemBody = DeepSeekPromptRegistry.systemContent(PromptPurpose.LEARNING_ANSWER, true);
        assertFalse(joined, joined.contains("SECRET-KEY-0001"));
        assertFalse(joined, joined.contains("\u63a8\u7406\u6b63\u6587"));
        assertFalse(joined, joined.contains("\u56de\u7b54\u6b63\u6587"));
        assertFalse(joined, joined.contains(systemBody.substring(0, 40)));
        assertFalse(joined, joined.contains("\u73b0\u5728\u7684\u95ee\u9898"));
        assertTrue(joined, joined.contains("stream_start"));
        assertTrue(joined, joined.contains("reasoningChars="));
        assertTrue(joined, joined.contains("answerChars="));
        assertTrue(joined, joined.contains("tl-learning-answer/1.1.0"));
    }

    @Test
    public void aFailingSinkCallbackStillClosesTheConnectionAndPropagates() {
        transport.sseBody = "data: \u601d#\u7b54\n\n";
        RecordingSink sink = new RecordingSink() {
            @Override
            public void onDelta(String reasoningChunk, String contentChunk) {
                throw new IllegalStateException("fixture: caller bug");
            }
        };

        try {
            gateway(INLINE).streamChat("key", GenerationModel.FLASH, request(true), sink);
            fail("a caller bug must not be swallowed");
        } catch (IllegalStateException expected) {
            assertEquals("fixture: caller bug", expected.getMessage());
        }
        assertTrue(transport.transportClosed);
    }

    @Test
    public void cancellationFromAnotherThreadReportsCanceledAndKeepsThePartialChannels()
            throws Exception {
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            transport.stream = new GatedStream("data: \u601d\u7eea#\u7b54\u7eea\n\n".getBytes(UTF_8));
            RecordingSink sink = new RecordingSink();

            AiGateway.Call call =
                    gateway(pool).streamChat("key", GenerationModel.FLASH, request(true), sink);
            assertTrue("the first event should arrive", sink.deltaLatched.await(5, TimeUnit.SECONDS));

            call.cancel();

            assertTrue("cancel must terminate the call",
                    sink.terminalLatched.await(5, TimeUnit.SECONDS));
            assertEquals(StreamOutcome.Kind.CANCELED, sink.outcome.kind);
            assertNull(sink.outcome.error);
            assertEquals(List.of("\u601d\u7eea"), sink.reasoning);
            assertEquals(List.of("\u7b54\u7eea"), sink.answers);
            assertEquals(1, sink.terminalCount);
            assertTrue(call.isCancelled());
            assertTrue(transport.transportClosed);
        } finally {
            pool.shutdownNow();
        }
    }

    /** The provider payload format of this suite: {@code <reasoning>#<content>}. */
    private static final class ScriptedDecoder implements GatewayPorts.DeltaDecoder {

        private final List<String> refused = new ArrayList<>();
        private final List<String> exploding = new ArrayList<>();

        void refuse(String payload) {
            refused.add(payload);
        }

        void blowUpOn(String payload) {
            exploding.add(payload);
        }

        @Override
        public GatewayPorts.Delta decode(String payload) {
            if (exploding.contains(payload)) {
                throw new IllegalStateException("fixture: unexpected decoder failure");
            }
            if (refused.contains(payload)) {
                throw new GatewayPorts.PayloadException(AiGatewayError.parseFailed("fixture refusal"),
                        "fixture refusal");
            }
            int separator = payload.indexOf('#');
            if (separator < 0) {
                return GatewayPorts.Delta.EMPTY;
            }
            return new GatewayPorts.Delta(payload.substring(0, separator),
                    payload.substring(separator + 1));
        }
    }

    /** One scripted HTTP response. Shared with the sink so the accepted-vs-body order is observable. */
    private static final class FakeTransport implements GatewayPorts.Transport {

        private final List<String> trace;
        String sseBody = "";
        int status = 200;
        boolean failOnOpen;
        boolean transportClosed;
        boolean bodyOpened;
        String requestedUrl;
        String requestedKey;
        byte[] requestedBody;
        InputStream stream;

        FakeTransport(List<String> trace) {
            this.trace = trace;
        }

        @Override
        public GatewayPorts.Response post(String url, String apiKey, byte[] body) throws IOException {
            requestedUrl = url;
            requestedKey = apiKey;
            requestedBody = body;
            if (failOnOpen) {
                throw new IOException("fixture: the connection could not be established");
            }
            return new FakeResponse();
        }

        @Override
        public GatewayPorts.Response get(String url, String apiKey) throws IOException {
            return post(url, apiKey, null);
        }

        private final class FakeResponse implements GatewayPorts.Response {

            private InputStream bodyStream;

            @Override
            public int status() {
                return status;
            }

            @Override
            public InputStream body() {
                bodyOpened = true;
                trace.add("body");
                bodyStream = stream != null ? stream : new ByteArrayInputStream(sseBody.getBytes(UTF_8));
                return bodyStream;
            }

            @Override
            public void close() {
                transportClosed = true;
                trace.add("closed");
                if (bodyStream != null) {
                    try {
                        bodyStream.close();
                    } catch (IOException ignored) {
                        // A fixture close failure must not mask the contract under test.
                    }
                }
            }
        }
    }

    /** Delivers one fixture chunk, then blocks until the call is cancelled. */
    private static final class GatedStream extends InputStream {

        private final byte[] first;
        private final CountDownLatch gate = new CountDownLatch(1);
        private int position;

        GatedStream(byte[] first) {
            this.first = first;
        }

        @Override
        public int read() throws IOException {
            byte[] one = new byte[1];
            int read = read(one, 0, 1);
            return read < 0 ? -1 : one[0];
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            if (position < first.length) {
                int count = Math.min(length, first.length - position);
                System.arraycopy(first, position, buffer, offset, count);
                position += count;
                return count;
            }
            try {
                gate.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IOException("fixture: interrupted while waiting for cancellation");
            }
            return -1;
        }

        @Override
        public void close() {
            gate.countDown();
        }
    }

    /** Records callbacks in arrival order and exposes the terminal outcome. */
    private class RecordingSink implements GenerationSink {

        final List<String> reasoning = new ArrayList<>();
        final List<String> answers = new ArrayList<>();
        final CountDownLatch deltaLatched = new CountDownLatch(1);
        final CountDownLatch terminalLatched = new CountDownLatch(1);
        StreamOutcome outcome;
        int terminalCount;
        boolean accepted;
        boolean accept = true;

        @Override
        public boolean onAccepted(int httpStatus) {
            accepted = true;
            trace.add("accepted:" + httpStatus);
            return accept;
        }

        @Override
        public void onDelta(String reasoningChunk, String contentChunk) {
            reasoning.add(reasoningChunk);
            answers.add(contentChunk);
            deltaLatched.countDown();
        }

        @Override
        public void onTerminal(StreamOutcome result) {
            outcome = result;
            terminalCount++;
            terminalLatched.countDown();
        }
    }
}