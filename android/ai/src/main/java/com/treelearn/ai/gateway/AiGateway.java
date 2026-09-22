package com.treelearn.ai.gateway;

import com.treelearn.ai.prompt.DeepSeekRequest;
import com.treelearn.core.domain.GenerationModel;
import com.treelearn.core.domain.Guard;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executor;

/**
 * The DeepSeek streaming boundary (technical design 9.1 - 9.5): it opens one HTTPS SSE exchange,
 * reports the accepted response before reading anything, delivers the two channels in arrival order,
 * and reports exactly one terminal outcome.
 *
 * <p>What this class deliberately does <em>not</em> do: it creates no database row, it holds no
 * operation or revision, it never decides that a finished answer is stable, it adds no system
 * message and it reorders, merges or drops no message — it transports the {@link DeepSeekRequest} it
 * was handed, byte for byte. It also never writes the key, a prompt, a question, an answer or a
 * reasoning text to the log; see {@link GatewayPorts.Log}.
 *
 * <p>The class imports nothing from {@code android.*} and nothing from {@code :data}: the four
 * external capabilities it needs arrive as {@link GatewayPorts}. That is what lets the whole stream
 * contract — accepted gate, ordering, terminal outcome, cancellation — be proven in a plain JVM
 * test, with only the platform JSON and socket layers exercised on a device.
 */
public final class AiGateway {

    /** Cap for the balance response, so a broken endpoint cannot stream without bound. */
    public static final int MAX_BALANCE_BYTES = 16 * 1024;

    private static final String DONE_SENTINEL = "[DONE]";
    private static final int READ_BUFFER_BYTES = 8 * 1024;
    private static final int BALANCE_BUFFER_BYTES = 4 * 1024;

    /** Handle of one in-flight call. */
    public interface Call {
        /** Aborts the exchange; safe to call from any thread, and idempotent. */
        void cancel();

        boolean isCancelled();
    }

    /** The result of the pre-flight balance check (technical design 8). */
    public static final class Balance {

        public final boolean available;
        /** Non-null when the check itself failed; an unavailable balance is not an error. */
        public final AiGatewayError error;

        public Balance(boolean available, AiGatewayError error) {
            this.available = available;
            this.error = error;
        }

        @Override
        public String toString() {
            return "Balance[available=" + available + (error == null ? "" : " " + error) + "]";
        }
    }

    private final GatewayPorts.Transport transport;
    private final GatewayPorts.BodyEncoder bodyEncoder;
    private final GatewayPorts.DeltaDecoder deltaDecoder;
    private final GatewayPorts.BalanceDecoder balanceDecoder;
    private final GatewayPorts.Log log;
    private final Executor executor;

    public AiGateway(GatewayPorts.Transport transport, GatewayPorts.BodyEncoder bodyEncoder,
            GatewayPorts.DeltaDecoder deltaDecoder, GatewayPorts.BalanceDecoder balanceDecoder,
            GatewayPorts.Log log, Executor executor) {
        this.transport = Guard.object(transport, "transport");
        this.bodyEncoder = Guard.object(bodyEncoder, "bodyEncoder");
        this.deltaDecoder = Guard.object(deltaDecoder, "deltaDecoder");
        this.balanceDecoder = Guard.object(balanceDecoder, "balanceDecoder");
        this.log = Guard.object(log, "log");
        this.executor = Guard.object(executor, "executor");
    }

    /**
     * Starts one streaming call on the injected executor and returns its cancel handle immediately.
     *
     * <p>A missing key never reaches the network: the call fails with {@code KEY_MISSING} before a
     * request body is built.
     */
    public Call streamChat(String apiKey, GenerationModel model, DeepSeekRequest request,
            GenerationSink sink) {
        StreamCall call = new StreamCall(apiKey, Guard.object(model, "model"),
                Guard.object(request, "request"), Guard.object(sink, "sink"));
        executor.execute(call);
        return call;
    }

    /**
     * Checks the credential and the account before a generation slot is spent. Synchronous: it is
     * called from a settings screen, not from the stream scheduler.
     */
    public Balance checkBalance(String apiKey) {
        if (isAbsent(apiKey)) {
            return reportBalance(false, AiGatewayError.keyMissing(), 0, System.nanoTime());
        }
        long startedNanos = System.nanoTime();
        try (GatewayPorts.Response response = transport.get(GatewayEndpoints.balanceUrl(), apiKey)) {
            int status = response.status();
            if (status / 100 != 2) {
                return reportBalance(false, AiGatewayError.forHttpStatus(status), status, startedNanos);
            }
            boolean available = balanceDecoder.isAvailable(
                    new String(readAllBytes(response.body()), StandardCharsets.UTF_8));
            return reportBalance(available, null, status, startedNanos);
        } catch (IOException transportFailure) {
            return reportBalance(false, AiGatewayError.networkInterrupted(
                    transportFailure.getClass().getSimpleName()), 0, startedNanos);
        } catch (GatewayPorts.PayloadException refused) {
            return reportBalance(false, refused.error, 0, startedNanos);
        }
    }

    private Balance reportBalance(boolean available, AiGatewayError failure, int status,
            long startedNanos) {
        log.event("balance_checked", "status=" + status + " available=" + available
                + " durationMs=" + millisSince(startedNanos)
                + (failure == null ? "" : " error=" + failure.kind));
        return new Balance(available, failure);
    }

    /** One streaming call: the whole lifecycle runs on the reader thread. */
    private final class StreamCall implements Call, Runnable {

        private final String apiKey;
        private final GenerationModel model;
        private final DeepSeekRequest request;
        private final GenerationSink sink;

        private volatile boolean cancelled;
        private volatile GatewayPorts.Response active;
        private int httpStatus;
        private int reasoningChars;
        private int answerChars;
        private boolean done;
        /** Set when the caller refuses the accepted response, so the call ends as cancelled. */
        private boolean refusedAccepted;
        private AiGatewayError failure;

        StreamCall(String apiKey, GenerationModel model, DeepSeekRequest request, GenerationSink sink) {
            this.apiKey = apiKey;
            this.model = model;
            this.request = request;
            this.sink = sink;
        }

        @Override
        public void cancel() {
            cancelled = true;
            // Closing the exchange is what unblocks a read that is waiting for the next event.
            close(active);
        }

        @Override
        public boolean isCancelled() {
            return cancelled;
        }

        @Override
        public void run() {
            long startedNanos = System.nanoTime();
            log.event("stream_start", describe() + " messages=" + request.messages.size());
            try {
                read(startedNanos);
            } finally {
                close(active);
            }
        }

        private void read(long startedNanos) {
            if (cancelled) {
                finish(startedNanos);
                return;
            }
            if (isAbsent(apiKey)) {
                failure = AiGatewayError.keyMissing();
                finish(startedNanos);
                return;
            }
            byte[] body;
            try {
                body = bodyEncoder.encode(model, request);
            } catch (RuntimeException notEncodable) {
                failure = AiGatewayError.of(AiGatewayError.Kind.REQUEST_INVALID, 0,
                        "the request body could not be encoded: "
                                + notEncodable.getClass().getSimpleName());
                finish(startedNanos);
                return;
            }
            try {
                active = transport.post(GatewayEndpoints.chatUrl(), apiKey, body);
                httpStatus = active.status();
            } catch (IOException transportFailure) {
                failure = AiGatewayError.networkInterrupted(
                        transportFailure.getClass().getSimpleName());
                finish(startedNanos);
                return;
            }
            if (httpStatus / 100 != 2) {
                // The failed response body is never read: it can echo the question.
                failure = AiGatewayError.forHttpStatus(httpStatus);
                finish(startedNanos);
                return;
            }
            boolean accepted;
            try {
                accepted = sink.onAccepted(httpStatus);
            } catch (RuntimeException callerBug) {
                close(active);
                throw callerBug;
            }
            if (!accepted) {
                // The local transaction failed, so this attempt never existed: close immediately and
                // report it as cancelled rather than as a stream that ran and ended.
                refusedAccepted = true;
                close(active);
                finish(startedNanos);
                return;
            }
            try {
                pumpEvents();
            } catch (IOException interrupted) {
                if (!cancelled) {
                    failure = AiGatewayError.networkInterrupted(
                            interrupted.getClass().getSimpleName());
                }
            }
            finish(startedNanos);
        }

        private void pumpEvents() throws IOException {
            SseFrameParser parser = new SseFrameParser();
            SseFrameParser.EventSink frames = payload -> onEvent(payload);
            InputStream stream = active.body();
            byte[] buffer = new byte[READ_BUFFER_BYTES];
            int read;
            while (!cancelled && !done && failure == null && (read = stream.read(buffer)) != -1) {
                parser.accept(buffer, read, frames);
            }
            if (!cancelled && !done && failure == null) {
                parser.endOfStream(frames);
            }
        }

        private void onEvent(String payload) {
            if (done || failure != null) {
                // One read call can carry several events; anything after a refusal or the sentinel
                // in the same chunk must be dropped, not half-applied.
                return;
            }
            if (DONE_SENTINEL.equals(payload.trim())) {
                done = true;
                return;
            }
            GatewayPorts.Delta delta;
            try {
                delta = deltaDecoder.decode(payload);
            } catch (GatewayPorts.PayloadException refused) {
                failure = refused.error;
                return;
            } catch (RuntimeException refused) {
                failure = AiGatewayError.parseFailed("the payload decoder refused an event: "
                        + refused.getClass().getSimpleName());
                return;
            }
            if (delta.isEmpty()) {
                return;
            }
            reasoningChars += delta.reasoningChunk.length();
            answerChars += delta.contentChunk.length();
            sink.onDelta(delta.reasoningChunk, delta.contentChunk);
        }

        private void finish(long startedNanos) {
            StreamOutcome.Kind kind;
            if (cancelled || refusedAccepted) {
                kind = StreamOutcome.Kind.CANCELED;
                failure = null;
            } else if (failure != null) {
                kind = StreamOutcome.Kind.FAILED;
            } else if (done) {
                kind = StreamOutcome.Kind.COMPLETED;
            } else {
                kind = StreamOutcome.Kind.INTERRUPTED;
            }
            log.event("stream_" + kind, describe() + " status=" + httpStatus
                    + " reasoningChars=" + reasoningChars + " answerChars=" + answerChars
                    + " durationMs=" + millisSince(startedNanos)
                    + (failure == null ? "" : " error=" + failure.kind));
            sink.onTerminal(new StreamOutcome(kind, failure, httpStatus, reasoningChars, answerChars));
        }

        private String describe() {
            return "purpose=" + request.purpose + " model=" + model.name()
                    + " thinking=" + request.thinkingEnabled + " policy=" + request.policyVersion;
        }
    }

    /** Reads a bounded body; the cap is a refusal, never a silent truncation. */
    private static byte[] readAllBytes(InputStream stream) throws IOException {
        ByteArrayOutputStream collected = new ByteArrayOutputStream();
        byte[] buffer = new byte[BALANCE_BUFFER_BYTES];
        int read;
        while ((read = stream.read(buffer)) != -1) {
            if (collected.size() + read > MAX_BALANCE_BYTES) {
                throw new GatewayPorts.PayloadException(
                        AiGatewayError.parseFailed("the response exceeded " + MAX_BALANCE_BYTES
                                + " bytes, so it was refused rather than truncated"),
                        "oversized response");
            }
            collected.write(buffer, 0, read);
        }
        return collected.toByteArray();
    }

    /** Closing is the abort path too, so it must never throw and mask the outcome being reported. */
    private static void close(GatewayPorts.Response response) {
        if (response != null) {
            response.close();
        }
    }

    private static boolean isAbsent(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static long millisSince(long startedNanos) {
        return (System.nanoTime() - startedNanos) / 1_000_000L;
    }
}