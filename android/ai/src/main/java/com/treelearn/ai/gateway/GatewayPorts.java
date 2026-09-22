package com.treelearn.ai.gateway;

import com.treelearn.ai.prompt.DeepSeekRequest;
import com.treelearn.core.domain.GenerationModel;
import com.treelearn.core.domain.Guard;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;

/**
 * The gateway's ports: the four things it needs from the outside world, so the streaming logic
 * itself stays pure Java and testable without a device or a network.
 *
 * <p>The platform implementations ({@code HttpsTransport}, {@code JsonChatRequestBody},
 * {@code ProviderJson}) are wired by {@code DeepSeekGatewayFactory}. Tests supply scripted ones.
 */
public final class GatewayPorts {

    private GatewayPorts() {
    }

    /** Encodes one assembled request into the exact bytes that are sent. */
    public interface BodyEncoder {
        byte[] encode(GenerationModel model, DeepSeekRequest request);
    }

    /**
     * Opens one HTTP exchange. A non-2xx status is returned, not thrown: classifying it is the
     * gateway's decision, and for a failed status the body must never be read.
     */
    public interface Transport {
        Response post(String url, String apiKey, byte[] body) throws IOException;

        Response get(String url, String apiKey) throws IOException;
    }

    public interface Response extends Closeable {
        int status();

        /** Never null; for a non-2xx response it is an empty stream, because the body is not read. */
        InputStream body();

        /** Aborts the exchange; called on every terminal path, including cancellation. */
        @Override
        void close();
    }

    /**
     * One decoded event: the two channels are independent, and either may be empty. An event that
     * carries neither (a role-only or {@code finish_reason}-only chunk) is {@link #EMPTY} and is not
     * delivered to the caller as a delta.
     */
    public static final class Delta {

        public static final Delta EMPTY = new Delta("", "");

        public final String reasoningChunk;
        public final String contentChunk;

        public Delta(String reasoningChunk, String contentChunk) {
            this.reasoningChunk = Guard.text(reasoningChunk, "reasoningChunk");
            this.contentChunk = Guard.text(contentChunk, "contentChunk");
        }

        public boolean isEmpty() {
            return reasoningChunk.isEmpty() && contentChunk.isEmpty();
        }
    }

    /** Decodes one SSE payload into its two channels. */
    public interface DeltaDecoder {
        Delta decode(String payload);
    }

    /** Reads the availability flag of the balance response. */
    public interface BalanceDecoder {
        boolean isAvailable(String payload);
    }

    /**
     * Sanitized diagnostics. The gateway passes metadata only (ids, states, counts, durations,
     * model, purpose, policy version, error category) and never a key, a prompt, a question, an
     * answer or a reasoning text.
     */
    public interface Log {
        void event(String name, String details);
    }

    /**
     * A payload the decoder refused. The gateway turns it into a {@code FAILED} outcome and keeps
     * every delta it already delivered, so a provider contract change cannot silently drop answer
     * text.
     */
    public static final class PayloadException extends RuntimeException {

        private static final long serialVersionUID = 1L;

        public final AiGatewayError error;

        public PayloadException(AiGatewayError error, String message) {
            super(message);
            this.error = Guard.object(error, "error");
        }
    }
}