package com.treelearn.ai.gateway;

/**
 * The callbacks one streaming call makes, in order (technical design 9.2, 9.3).
 *
 * <p><strong>Caller obligation that this boundary cannot observe.</strong> The gateway reports that
 * the stream ended normally; deciding that the answer is non-empty and stabilizing a revision is the
 * caller's job, because the gateway has no operation, revision or database concept (that is
 * {@code GenerationCoordinator}'s transaction, T-008/T-023). A {@code COMPLETED} outcome with
 * {@code answerChars == 0} is therefore reported as completed, not as a failure.
 *
 * <p>Still the caller's obligation, and likewise not observable here: keeping the pinned
 * {@code promptPolicyVersion} on the command in step with {@code DeepSeekRequest.policyVersion}. The
 * gateway transports the request it is given and compares nothing.
 */
public interface GenerationSink {

    /**
     * Called once, on the reader thread, after a 2xx response and before any body byte is read. The
     * caller persists the accepted operation in a transaction here.
     *
     * @return true to continue reading the stream; false when the local transaction failed, which
     *         closes the connection immediately so no delta can race ahead of it
     */
    boolean onAccepted(int httpStatus);

    /** One event's channels, in arrival order. At least one chunk is non-empty. */
    void onDelta(String reasoningChunk, String contentChunk);

    /** Called exactly once per call, unless one of the two callbacks above throws. */
    void onTerminal(StreamOutcome outcome);
}