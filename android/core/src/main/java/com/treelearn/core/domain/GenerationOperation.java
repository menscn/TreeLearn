package com.treelearn.core.domain;

/**
 * One attempt at producing an answer (technical design §5.1, §7, §9.3).
 *
 * <p>This is the only mutable type in the domain package, and that is the whole point of it: the
 * stream arrives in arbitrary chunks, so something has to accumulate them. The design keeps the
 * mutability from leaking into the immutable contract in four ways:
 *
 * <ul>
 *   <li>The pinned {@link #promptPolicyVersion}, the identities and the model are {@code final}; only
 *       {@link #state()} and the two buffers move.
 *   <li>The buffers are never handed out as a stable version. They become an {@link AnswerRevision}
 *       only through {@link #stabilize}, which is also the only way to reach
 *       {@link GenerationState#STABLE} — {@link #advanceTo} refuses that target, so a
 *       {@code STABLE} attempt can never exist without a revision beside it.
 *   <li>Once terminal, nothing is accepted: a late SSE callback is reported as a domain error instead
 *       of writing back over an outcome the user has already seen (§7). The partial content stays
 *       readable, which is what an in-place retry needs, but it can no longer become history.
 *   <li>Appends drive the phase change themselves. A delta cannot arrive "after" the state was moved
 *       by someone else, because there is no other someone.
 * </ul>
 *
 * <p>The phase machine encoded by {@link #appendReasoning} and {@link #appendAnswer} matches §9.3's
 * warning that the two channels are not guaranteed to be framed together: reasoning deltas are
 * accepted while the attempt is already streaming the answer, and appending one does not step the
 * attempt back into the reasoning phase.
 *
 * <p>Physical batching (250 ms or 256 characters, §7) is a persistence concern and lives in T-008;
 * this type appends every delta it is given.
 */
public final class GenerationOperation {

    public final String id;
    /** The command that pinned this attempt's policy version. */
    public final String commandId;
    public final String branchId;
    public final String messageId;
    public final PromptPurpose purpose;
    /**
     * The policy version pinned by the command, copied verbatim. Final: §3.2 requires the version to
     * be fixed before submission, so changing it means starting a new operation, never editing this
     * one.
     */
    public final String promptPolicyVersion;
    public final GenerationModel model;
    public final boolean thinkingEnabled;
    /** The style the command pinned; copied verbatim into the stable revision. */
    public final Persona persona;
    public final long createdAt;

    private GenerationState state;
    private final StringBuilder partialReasoning = new StringBuilder();
    private final StringBuilder partialAnswer = new StringBuilder();

    private GenerationOperation(GenerationCommand command, long now) {
        this.id = Guard.newId();
        this.commandId = Guard.id(command.id, "command.id");
        this.branchId = Guard.id(command.branchId, "command.branchId");
        this.messageId = Guard.id(command.messageId, "command.messageId");
        this.purpose = Guard.object(command.purpose, "command.purpose");
        this.promptPolicyVersion = Guard.nonBlank(
                command.promptPolicyVersion, "command.promptPolicyVersion");
        this.model = Guard.object(command.model, "command.model");
        this.thinkingEnabled = command.thinkingEnabled;
        this.persona = Guard.object(command.persona, "command.persona");
        this.state = GenerationState.STAGED;
        this.createdAt = Guard.epochMillis(now, "createdAt");
    }

    /**
     * The only creation path: an attempt is always started from the command it belongs to, which is
     * what guarantees that the attempt cannot carry a version the command never pinned.
     */
    public static GenerationOperation start(GenerationCommand command, long now) {
        Guard.object(command, "command");
        return new GenerationOperation(command, now);
    }

    public GenerationState state() {
        return state;
    }

    /** Reasoning received so far. Non-empty once reasoning deltas have arrived. */
    public String partialReasoning() {
        return partialReasoning.toString();
    }

    /** Answer received so far. Non-empty does not mean complete — see {@link #stabilize}. */
    public String partialAnswer() {
        return partialAnswer.toString();
    }

    public boolean hasPartialContent() {
        return partialReasoning.length() > 0 || partialAnswer.length() > 0;
    }

    /**
     * Moves the attempt along the §7 graph without touching the buffers.
     *
     * <p>{@link GenerationState#STABLE} is refused here even though the state graph allows it from a
     * streaming state. Stabilizing has to create the revision and set the state as one act, and the
     * only code that can do both is {@link #stabilize}; allowing a plain transition to
     * {@code STABLE} would let an attempt claim to be finished with no answer to show.
     *
     * @return the new state, or a failure with {@link DomainErrorCode#ILLEGAL_STATE_TRANSITION} when
     *         the move is illegal or the target is {@code STABLE}
     * @throws IllegalArgumentException if {@code next} is null, per the error-channel rule in
     *         {@link Guard}
     */
    public DomainResult<GenerationState> advanceTo(GenerationState next) {
        if (next == null) {
            throw new IllegalArgumentException("next must not be null; a transition is always to a "
                    + "named state");
        }
        if (next == GenerationState.STABLE) {
            return DomainResult.fail(DomainErrorCode.ILLEGAL_STATE_TRANSITION,
                    "STABLE is reached only through stabilize(...), which creates the stable revision "
                            + "in the same act; a plain transition would leave a finished attempt "
                            + "with no version to point at");
        }
        DomainResult<GenerationState> moved = state.transitionTo(next);
        if (moved.isOk()) {
            state = next;
        }
        return moved;
    }

    /**
     * Appends a {@code reasoning_content} delta.
     *
     * <p>Accepted in {@link GenerationState#SUBMITTING} (it opens the reasoning phase) and in both
     * streaming states. Being called while the attempt is already streaming the answer is normal, not
     * a step back: §9.3 does not promise the two channels are framed together, so the delta is
     * appended and the state stays where it is.
     *
     * <p>An empty delta is well formed and accepted, but does not open a phase — a bare
     * {@code reasoning_content: ""} carries no information and is routinely used as a keep-alive.
     *
     * @return the (possibly unchanged) state, or a failure with
     *         {@link DomainErrorCode#ILLEGAL_STATE_TRANSITION} when the attempt is terminal or has
     *         not reached the provider yet
     * @throws IllegalArgumentException if {@code delta} is null
     */
    public DomainResult<GenerationState> appendReasoning(String delta) {
        Guard.text(delta, "delta");
        DomainResult<GenerationState> refusal = refusalsFor("reasoning");
        if (refusal != null) {
            return refusal;
        }
        if (delta.isEmpty()) {
            return DomainResult.ok(state);
        }
        if (state == GenerationState.SUBMITTING) {
            state = GenerationState.STREAMING_REASONING;
        }
        partialReasoning.append(delta);
        return DomainResult.ok(state);
    }

    /**
     * Appends a {@code content} delta.
     *
     * <p>From {@link GenerationState#SUBMITTING} this moves straight to
     * {@link GenerationState#STREAMING_ANSWER}: with {@code thinking.enabled = false} the provider
     * emits no reasoning deltas at all (§9.1), so a thinking-disabled attempt never passes through the
     * reasoning phase.
     *
     * @return the (possibly unchanged) state, or a failure with
     *         {@link DomainErrorCode#ILLEGAL_STATE_TRANSITION} when the attempt is terminal or has
     *         not reached the provider yet
     * @throws IllegalArgumentException if {@code delta} is null
     */
    public DomainResult<GenerationState> appendAnswer(String delta) {
        Guard.text(delta, "delta");
        DomainResult<GenerationState> refusal = refusalsFor("answer");
        if (refusal != null) {
            return refusal;
        }
        if (delta.isEmpty()) {
            return DomainResult.ok(state);
        }
        if (state == GenerationState.SUBMITTING || state == GenerationState.STREAMING_REASONING) {
            state = GenerationState.STREAMING_ANSWER;
        }
        partialAnswer.append(delta);
        return DomainResult.ok(state);
    }

    /**
     * Closes a normally finished attempt and freezes its buffers into a stable revision.
     *
     * <p>This is the one atomic act of the type: the revision is built <em>first</em> and the state is
     * only moved once that succeeded, so a refused stabilization leaves the attempt exactly as it was
     * rather than stranded in {@code STABLE} with nothing to show. The buffers are copied verbatim —
     * no trimming, no normalization, no version upgrade — because the revision must hold what the
     * model actually produced, and §3.2 requires a version change to be a new operation.
     *
     * <p><strong>Caller obligation: "normally finished" is not observable here.</strong> §9.3 only
     * allows a revision to be created once the stream ended normally <em>and</em> the final answer is
     * non-empty. The second half is checked below; the first half cannot be. This type never sees
     * {@code [DONE]}, an error frame or a dropped connection — the stream protocol, the retry policy
     * and the 250 ms batching window all live in the coordinator (T-008/T-009). The stream can still
     * be arriving when this method is called, and it will succeed: an attempt whose answer is non-empty
     * so far is indistinguishable from one that has finished. So the caller must establish the
     * termination itself — because the only alternative would be to smuggle a stream-state flag into a
     * pure domain type, which is exactly the coupling the module boundary exists to prevent.
     *
     * <p>What the domain does guarantee is the converse: once this returns ok the attempt is
     * {@link GenerationState#STABLE}, and from then on every late delta and every second stabilization
     * is refused, so a coordinator that stabilizes too early loses the tail of the answer but never
     * corrupts a frozen revision.
     *
     * @param revisionNo the next revision number for {@link #messageId}; supplied by the caller
     *        because the revision sequence belongs to the message and is a repository fact, not
     *        something an attempt can know on its own
     * @param now the completion time
     * @return the new revision, or a failure with {@link DomainErrorCode#ILLEGAL_STATE_TRANSITION}
     *         when the attempt is already terminal (a late callback), or
     *         {@link DomainErrorCode#OPERATION_NOT_COMPLETABLE} when the final answer is still empty
     * @throws IllegalArgumentException if {@code revisionNo} is below 1 or {@code now} is negative.
     *         Both are checked <em>before</em> the state, in the declared order of the package's
     *         check-order rule, so a malformed sequence number is never answered with
     *         {@code ILLEGAL_STATE_TRANSITION} on an attempt that happens to be finished already.
     */
    public DomainResult<AnswerRevision> stabilize(int revisionNo, long now) {
        // Argument shapes first, for the reason GenerationCommand.create states: a call carrying both a
        // malformed argument and a state that forbids the operation must still report the caller bug.
        // AnswerRevision.create re-checks both when it builds the revision; that is the invariant
        // authority validating for itself, not a second definition of what "well formed" means.
        Guard.atLeast(revisionNo, 1, "revisionNo");
        Guard.epochMillis(now, "createdAt");
        if (state.isTerminal()) {
            return DomainResult.fail(DomainErrorCode.ILLEGAL_STATE_TRANSITION,
                    "the attempt already ended as " + state + "; a late stabilization must not rewrite "
                            + "an outcome that has already been reported");
        }
        // Snapshot once. The blankness test below uses trim() to match Guard.nonBlank exactly, but the
        // value handed to the revision is the untrimmed snapshot: the stable version must hold what
        // the model produced, so nothing here may normalize it.
        String finalReasoning = partialReasoning.toString();
        String finalAnswer = partialAnswer.toString();
        if (finalAnswer.trim().isEmpty()) {
            return DomainResult.fail(DomainErrorCode.OPERATION_NOT_COMPLETABLE,
                    "a stable revision requires a non-empty final answer, but only "
                            + finalAnswer.length() + " answer character(s) arrived; the partial "
                            + "content is kept so the attempt can be retried in place");
        }
        DomainResult<AnswerRevision> created = AnswerRevision.create(
                messageId, revisionNo, finalReasoning, finalAnswer,
                model, thinkingEnabled, persona, promptPolicyVersion, now);
        if (created.isFailure()) {
            return created;
        }
        state = GenerationState.STABLE;
        return DomainResult.ok(created.value());
    }

    /**
     * @return a failure when a delta of this channel may not be appended right now, otherwise null.
     *         The allowed set is an allow-list, so a state added later is refused by default rather
     *         than silently accepting deltas.
     */
    private DomainResult<GenerationState> refusalsFor(String channel) {
        if (state.isTerminal()) {
            return DomainResult.fail(DomainErrorCode.ILLEGAL_STATE_TRANSITION,
                    "the attempt is " + state + "; a " + channel + " delta that arrives after a "
                            + "terminal state must not write back");
        }
        switch (state) {
            case SUBMITTING:
            case STREAMING_REASONING:
            case STREAMING_ANSWER:
                return null;
            default:
                return DomainResult.fail(DomainErrorCode.ILLEGAL_STATE_TRANSITION,
                        "a " + channel + " delta arrived while the attempt is still " + state
                                + "; no request has been accepted yet");
        }
    }

    @Override
    public String toString() {
        // Lengths, not text: partial content is conversation data and must not reach a log line (§15).
        return "GenerationOperation[" + state + ", reasoning=" + partialReasoning.length()
                + " chars, answer=" + partialAnswer.length() + " chars, policy="
                + promptPolicyVersion + "]";
    }
}
