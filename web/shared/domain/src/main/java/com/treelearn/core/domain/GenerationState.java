package com.treelearn.core.domain;

/**
 * Lifecycle of one generation attempt (technical design 7).
 *
 * <pre>
 * STAGED -&gt; VALIDATING -&gt; SUBMITTING -&gt; STREAMING_REASONING -&gt; STREAMING_ANSWER -&gt; STABLE
 * any unfinished state -&gt; FAILED | INTERRUPTED | CANCELED
 * </pre>
 *
 * <p>Two invariants decide the shape of this type:
 *
 * <ul>
 *   <li>Only {@link #STABLE} may become a historical branch source. A partially streamed answer
 *       must never be frozen into a child branch's provenance.
 *   <li>Terminal states never change again, so a late network callback cannot rewrite an outcome
 *       that was already reported to the user.
 * </ul>
 *
 * <p>Recorded deviations from the technical-design §7 diagram. The diagram's last block is
 *
 * <pre>
 * STAGED -&gt; VALIDATING -&gt; SUBMITTING -&gt; STREAMING_REASONING
 *                                       -&gt; STREAMING_ANSWER
 *                                       -&gt; STABLE
 * </pre>
 *
 * <p>whose second and third lines are a <em>fan-out</em>, not a wrap of one chain: both continuation
 * arrows start at column 38, the column where {@code STREAMING_REASONING} begins, and a chain would
 * have to indent the third arrow past {@code STREAMING_ANSWER}. The diagram names no source for those
 * arrows, so the alignment is the only evidence, and reading them as successors of
 * {@code SUBMITTING} (column 24) is not what the layout shows. Taken that way the diagram states
 * {@code STREAMING_REASONING -> STREAMING_ANSWER} and {@code STREAMING_REASONING -> STABLE}.
 *
 * <p>Counting exactly, this type deviates from that diagram in <strong>two additions and one
 * deletion</strong> — not "three deviations", which would count a removed edge as though it were
 * still present, and not "two deviations", which would hide the deletion. All three are listed
 * below. They are recorded rather than silent, and the strength of the justification is stated
 * honestly per entry instead of being asserted uniformly:
 *
 * <ul>
 *   <li>{@code SUBMITTING -> STREAMING_ANSWER} — <em>required</em>. With
 *       {@code thinking.enabled = false} the provider emits no reasoning deltas at all, so without
 *       this edge a thinking-disabled generation could never reach {@link #STABLE}. Explicitly
 *       evidenced by §9.1's {@code thinking.disabled} request shape. (Under the diagram's fan-out
 *       reading {@code SUBMITTING} leads only to {@code STREAMING_REASONING}, so this edge is an
 *       addition to §7, not a choice among its arrows.)
 *   <li>{@code STAGED -> CANCELED} in {@link #recoveryTarget()} — a documented extension of §7, which
 *       only describes {@code VALIDATING/SUBMITTING/STREAMING_*} becoming {@code INTERRUPTED}. A
 *       {@code STAGED} attempt never reached the provider, so there is nothing to interrupt;
 *       {@code CANCELED} is the honest terminal state.
 *   <li>{@code STREAMING_REASONING -> STABLE} is <strong>absent here although the diagram draws
 *       it</strong> — removed as unreachable rather than left as defensive surface. Reaching
 *       {@link #STABLE} requires a non-empty final answer, and the only writer of answer deltas is
 *       {@code GenerationOperation.appendAnswer}, which moves the attempt to {@link #STREAMING_ANSWER}
 *       before it appends. A non-empty answer therefore implies {@code STREAMING_ANSWER}, so no
 *       execution can ever stabilize <em>from</em> {@code STREAMING_REASONING}, and §9.3's "a stable
 *       revision requires a non-empty final answer" independently forbids the only run the edge could
 *       have served (a reasoning-only stream ending empty). The old unit test justified the edge with
 *       "a model that returns the answer without a separate reasoning phase", but that shape is
 *       {@code SUBMITTING -> STREAMING_ANSWER}, which is asserted separately; the stated reason never
 *       matched the edge. Keeping a rule no code path can exercise is the same defect class as a
 *       domain error code with no producer.
 * </ul>
 *
 * <p>One §7 fact the diagram's reading does not spell out and this type therefore states on its own:
 * {@code STREAMING_ANSWER -> STABLE} is the <em>only</em> route into {@link #STABLE}, and
 * {@code GenerationOperation.advanceTo} refuses that target outright so the transition can only
 * happen inside {@code stabilize}, together with the revision it creates.
 *
 * <p>The graph stays one-directional on purpose: {@code STREAMING_ANSWER -> STREAMING_REASONING} is
 * refused. §9.3's "the two fields are not guaranteed to be chunked in a fixed way" is about chunk
 * framing, not ordering, and the buffers live on the operation rather than in the state, so a late
 * reasoning delta is appended to the reasoning buffer while the state remains
 * {@link #STREAMING_ANSWER}.
 */
public enum GenerationState {

    /** Created locally, waiting for the application-wide concurrency budget. */
    STAGED,

    /** Local pre-flight: key presence and balance are being checked. */
    VALIDATING,

    /** The request has been sent; the remote acceptance boundary has not been crossed yet. */
    SUBMITTING,

    /** Streaming, receiving {@code reasoning_content} deltas. */
    STREAMING_REASONING,

    /** Streaming, receiving {@code content} deltas. */
    STREAMING_ANSWER,

    /** Finished normally with a non-empty final answer. The immutable revision now exists. */
    STABLE,

    /** Terminated with a mapped error. Partial content is kept in place for a manual retry. */
    FAILED,

    /** The process was killed or the connection dropped. Partial content is kept, never resent. */
    INTERRUPTED,

    /** Canceled by the user or by deleting the surrounding branch. */
    CANCELED;

    public boolean isActive() {
        switch (this) {
            case STAGED:
            case VALIDATING:
            case SUBMITTING:
            case STREAMING_REASONING:
            case STREAMING_ANSWER:
                return true;
            default:
                return false;
        }
    }

    /** Terminal states are frozen: {@link GenerationState} only ever moves forward once here. */
    public boolean isTerminal() {
        return !isActive();
    }

    public boolean isStable() {
        return this == STABLE;
    }

    public boolean isStreaming() {
        return this == STREAMING_REASONING || this == STREAMING_ANSWER;
    }

    /** FAILED and INTERRUPTED keep their partial content and may be retried in place. */
    public boolean isRetryable() {
        return this == FAILED || this == INTERRUPTED;
    }

    /**
     * @return true when moving to {@code next} is legal. Terminal states accept nothing, so a late
     *         SSE callback is rejected rather than silently mutating a finished attempt.
     */
    public boolean canTransitionTo(GenerationState next) {
        if (next == null) {
            return false;
        }
        switch (this) {
            case STAGED:
                return next == VALIDATING || next == FAILED || next == INTERRUPTED || next == CANCELED;
            case VALIDATING:
                return next == SUBMITTING || next == FAILED || next == INTERRUPTED || next == CANCELED;
            case SUBMITTING:
                return next == STREAMING_REASONING || next == STREAMING_ANSWER
                        || next == FAILED || next == INTERRUPTED || next == CANCELED;
            case STREAMING_REASONING:
                return next == STREAMING_ANSWER
                        || next == FAILED || next == INTERRUPTED || next == CANCELED;
            case STREAMING_ANSWER:
                return next == STABLE || next == FAILED || next == INTERRUPTED || next == CANCELED;
            default:
                return false;
        }
    }

    /**
     * @return the next state, or a failure with {@link DomainErrorCode#ILLEGAL_STATE_TRANSITION}
     *         when the move is not allowed. Callers must handle the failure rather than assume a
     *         transition happened, which is what keeps a late callback from mutating a finished
     *         attempt.
     * @throws IllegalArgumentException if {@code next} is null. Per the error-channel rule in
     *         {@link Guard}, an omitted argument is a caller bug and must not be reported as a
     *         business refusal — a round-4-independent audit found this method returned
     *         {@code ILLEGAL_STATE_TRANSITION} for null while {@link BranchSource#fromOperation}
     *         threw for the same class of mistake.
     */
    public DomainResult<GenerationState> transitionTo(GenerationState next) {
        if (next == null) {
            throw new IllegalArgumentException(
                    "target state must not be null; a transition is always to a named state");
        }
        if (!canTransitionTo(next)) {
            String reason = isTerminal()
                    ? "; " + this + " is terminal and never changes again"
                    : "";
            return DomainResult.fail(DomainErrorCode.ILLEGAL_STATE_TRANSITION,
                    "cannot move from " + this + " to " + next + reason);
        }
        return DomainResult.ok(next);
    }

    /**
     * States that cannot survive a process restart, and so must be reaped when the app starts.
     *
     * <p>{@link #STAGED} is included deliberately. The technical-design §7 text lists only the
     * in-flight states, but {@link #STAGED} is {@link #isActive()} and the request queue is in memory:
     * after a restart nothing will ever schedule it again, and under the §7 rule "at most one active
     * generation per branch" a leftover {@code STAGED} row would block that branch forever.
     */
    public static GenerationState[] processRecoverableStates() {
        return new GenerationState[] {
                STAGED, VALIDATING, SUBMITTING, STREAMING_REASONING, STREAMING_ANSWER,
        };
    }

    /**
     * The terminal state a leftover attempt is moved to on startup.
     *
     * <p>Everything that actually reached the provider becomes {@link #INTERRUPTED} so the user can
     * retry in place with the partial content kept. {@link #STAGED} becomes {@link #CANCELED} instead:
     * no request was ever sent, so there is nothing to interrupt and no partial content worth keeping.
     */
    public GenerationState recoveryTarget() {
        switch (this) {
            case STAGED:
                return CANCELED;
            case VALIDATING:
            case SUBMITTING:
            case STREAMING_REASONING:
            case STREAMING_ANSWER:
                return INTERRUPTED;
            default:
                throw new IllegalStateException(this + " is terminal and must not be recovered");
        }
    }
}
