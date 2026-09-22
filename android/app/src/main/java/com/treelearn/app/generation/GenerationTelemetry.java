package com.treelearn.app.generation;

import com.treelearn.ai.gateway.AiGatewayError;

/**
 * The redacted lifecycle record (technical design 14, 15).
 *
 * <p>The signature is the redaction policy made mechanical: there is no parameter a question, an
 * answer, a reasoning text, a prompt body, a snapshot or a key could travel in. Only identifiers, a
 * character count and a provider error category cross this boundary, which is exactly the set the
 * design permits.
 */
public interface GenerationTelemetry {

    /** One redacted lifecycle event. */
    enum Event {
        START,
        ACCEPTED,
        STREAMING,
        STABLE,
        FAILED,
        INTERRUPTED,
        CANCELED,
        /** A prompt or request build was refused, so no call was made. */
        PROMPT_REFUSED,
        /** The attempt could not be persisted locally, so no call was made. */
        STORAGE_REFUSED,
        /** An event that arrived after its attempt was no longer the active one. */
        STALE_DISCARDED
    }

    /**
     * @param operationId the attempt, or null when the event precedes one (a prompt refusal has no
     *        attempt at all)
     * @param chars the number of characters the event covers; never the characters themselves
     * @param errorKind the provider error category, or null
     */
    void record(Event event, String operationId, String branchId, int chars,
            AiGatewayError.Kind errorKind);
}
