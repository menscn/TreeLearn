package com.treelearn.core.domain;

/**
 * An immutable request to start one generation attempt (technical design §5.1, §9.2).
 *
 * <p>The command exists for a single reason: the prompt policy version must be fixed <em>before</em>
 * the remote acceptance boundary is crossed, and must then travel unchanged through the attempt and
 * into the stable revision. Pinning it here — rather than reading "the current policy version" at
 * stabilization time — is what makes an old answer reproducible after the registry has moved on
 * (§3.2). Retrying or resetting a position creates a <em>new</em> command with the then-current
 * version; the revision already produced keeps the version that actually produced it.
 *
 * <p>Every field is final and the type has no setter, so a command cannot be re-pointed after the
 * request has been sent.
 *
 * <p>The class holds no prompt body and no question text. The body lives in {@code :ai}'s registry
 * (§9.2 forbids persisting it), and the conversation content is read from the context snapshot when
 * the request is assembled, so nothing here can go stale between submit and stream.
 */
public final class GenerationCommand {

    public final String id;
    /** The branch this attempt belongs to; §7 allows at most one active attempt per branch. */
    public final String branchId;
    /** The AI message position this attempt answers into. A retry reuses the same position. */
    public final String messageId;
    public final PromptPurpose purpose;
    /** The pinned version. Copied verbatim into the operation and into the stable revision. */
    public final String promptPolicyVersion;
    public final GenerationModel model;
    public final boolean thinkingEnabled;
    /**
     * The answer style this attempt was admitted with, copied from the topic row at admission.
     * Final for the same reason the policy version is: a switch affects future answers only, so an
     * attempt already in flight keeps the style it was sent with.
     */
    public final Persona persona;
    public final long createdAt;

    private GenerationCommand(
            String id,
            String branchId,
            String messageId,
            PromptPurpose purpose,
            String promptPolicyVersion,
            GenerationModel model,
            boolean thinkingEnabled,
            Persona persona,
            long createdAt) {
        this.id = Guard.id(id, "id");
        requireArgumentShape(branchId, messageId, purpose, model, createdAt);
        this.branchId = branchId;
        this.messageId = messageId;
        this.purpose = purpose;
        this.model = model;
        this.thinkingEnabled = thinkingEnabled;
        this.persona = Guard.object(persona, "persona");
        this.createdAt = createdAt;
        this.promptPolicyVersion = Guard.nonBlank(promptPolicyVersion, "promptPolicyVersion");
    }

    /**
     * The shape checks for the six non-version arguments, defined once and used by both
     * {@link #create} and the constructor.
     *
     * <p>It exists as a method because {@code create} has to run these <em>before</em> the policy
     * decision (see its javadoc) while the constructor must keep validating on its own — the
     * constructor is the invariant authority and must stay correct for any future caller. Writing the
     * checks once and calling them from both places keeps that ordering without a second definition of
     * what a well-formed argument is.
     */
    private static void requireArgumentShape(
            String branchId, String messageId, PromptPurpose purpose, GenerationModel model, long now) {
        Guard.id(branchId, "branchId");
        Guard.id(messageId, "messageId");
        Guard.object(purpose, "purpose");
        Guard.object(model, "model");
        Guard.epochMillis(now, "createdAt");
    }

    /**
     * Pins the policy version and returns the command, or refuses when the version cannot be trusted.
     *
     * <p>Per the error-channel rule in {@link Guard} this method uses both channels, split by what the
     * caller can do about the mistake. A missing or foreign version is a <em>declared</em> business
     * condition — §3.2 makes the version mandatory, so its absence is expressible and reportable — and
     * returns {@link DomainErrorCode#PROMPT_POLICY_VERSION_REQUIRED} or
     * {@link DomainErrorCode#PROMPT_POLICY_VERSION_MISMATCH}. A malformed identifier, a missing purpose
     * or model, or a negative timestamp is a caller bug and throws {@link IllegalArgumentException}.
     *
     * <p><strong>Declared exception to the first bullet of the error-channel rule.</strong> This entry
     * point reports an <em>absent</em> version as a code rather than throwing, which
     * {@link Guard}'s rule otherwise reserves for caller bugs. It is one of the exceptions listed
     * there, together with {@link AnswerRevision#create}, and for the same reason: §3.2 makes the
     * version a mandatory part of the request contract, so "this request pinned no version" is a
     * condition the caller must be able to report and not a malformed argument. A
     * round-1-independent audit of T-021 found this entry point using the exemption without
     * declaring it, which is why {@link Guard} now carries the list.
     *
     * <p>The decision itself lives in {@link PromptPurpose#versionFailureFor}, shared with
     * {@link PromptSpec#of}, so the two prompt-policy entry points cannot answer differently for the
     * same input again.
     *
     * <p><strong>Check order.</strong> Argument shapes are verified before the version decision, in
     * the declared order of the arguments. A caller that passes both a malformed identifier and an
     * absent version therefore gets the {@link IllegalArgumentException} for the identifier: a
     * malformed argument means the caller's own code is broken, and reporting a policy code for such a
     * call would suggest the input was well formed and merely needed a version pinned.
     *
     * @return a failure with {@code PROMPT_POLICY_VERSION_REQUIRED} when no version was pinned, or
     *         {@code PROMPT_POLICY_VERSION_MISMATCH} when the version is not owned by {@code purpose}.
     *         Accepting either would let an unattributable answer become a stable revision.
     */
    public static DomainResult<GenerationCommand> create(
            String branchId,
            String messageId,
            PromptPurpose purpose,
            String promptPolicyVersion,
            GenerationModel model,
            boolean thinkingEnabled,
            long now) {
        return create(branchId, messageId, purpose, promptPolicyVersion, model, thinkingEnabled,
                Persona.DEFAULT, now);
    }

    /**
     * The persona-carrying factory. A null persona is a caller bug, not "the default", for the same
     * reason a null purpose is: the caller has to say which style it means, and the fallback for an
     * unknown <em>stored</em> value belongs at the read boundary, not here.
     */
    public static DomainResult<GenerationCommand> create(
            String branchId,
            String messageId,
            PromptPurpose purpose,
            String promptPolicyVersion,
            GenerationModel model,
            boolean thinkingEnabled,
            Persona persona,
            long now) {
        requireArgumentShape(branchId, messageId, purpose, model, now);
        Guard.object(persona, "persona");
        DomainError versionFailure =
                PromptPurpose.versionFailureFor(purpose, promptPolicyVersion);
        if (versionFailure != null) {
            return DomainResult.fail(versionFailure);
        }
        return DomainResult.ok(new GenerationCommand(
                Guard.newId(), branchId, messageId, purpose, promptPolicyVersion, model,
                thinkingEnabled, persona, now));
    }

    @Override
    public String toString() {
        return "GenerationCommand[message=" + messageId + ", " + purpose + " "
                + promptPolicyVersion + ", " + model.wireId
                + (thinkingEnabled ? ", thinking" : ", no thinking")
                + ", persona=" + persona.wireId + "]";
    }
}
