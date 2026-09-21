package com.treelearn.core.domain;

/**
 * One immutable, stable answer version (technical design 5.1).
 *
 * <p>A revision is created only when an attempt finished normally <em>and</em> produced a non-empty
 * final answer. From that moment it is frozen: every field is final, there is no setter, and the
 * constructor is the only way to build one. Resetting an answer therefore creates a
 * <em>new</em> revision with a higher {@link #revisionNo} and leaves every existing revision exactly
 * as it was — including the {@code promptPolicyVersion} that produced it, which is copied verbatim
 * from the attempt's command and can never be rewritten afterwards.
 *
 * <p>{@link #reasoningContent} and {@link #answerContent} belong to the same stable version: a
 * reset never preserves one while replacing the other, so an old child branch that references this
 * revision still sees the reasoning that actually led to its answer.
 */
public final class AnswerRevision {

    public final String id;
    public final String messageId;
    /** Monotonic, stable, starts at 1 and only ever increases for a given position. */
    public final int revisionNo;
    /** Full persisted reasoning; may be empty when thinking was disabled. */
    public final String reasoningContent;
    /** Full final answer; never blank. */
    public final String answerContent;
    public final GenerationModel model;
    public final boolean thinkingEnabled;
    /** The exact prompt policy version that produced this answer; never blank. */
    public final String promptPolicyVersion;
    public final long createdAt;

    private AnswerRevision(
            String id,
            String messageId,
            int revisionNo,
            String reasoningContent,
            String answerContent,
            GenerationModel model,
            boolean thinkingEnabled,
            String promptPolicyVersion,
            long createdAt) {
        this.id = Guard.id(id, "id");
        this.messageId = Guard.id(messageId, "messageId");
        this.revisionNo = Guard.atLeast(revisionNo, 1, "revisionNo");
        this.reasoningContent = Guard.text(reasoningContent, "reasoningContent");
        this.answerContent = Guard.nonBlank(answerContent, "answerContent");
        this.model = Guard.object(model, "model");
        this.thinkingEnabled = thinkingEnabled;
        this.promptPolicyVersion = Guard.nonBlank(promptPolicyVersion, "promptPolicyVersion");
        this.createdAt = Guard.epochMillis(createdAt, "createdAt");
    }

    /**
     * Creates the first stable revision for a position.
     *
     * @return a failure with {@link DomainErrorCode#PROMPT_POLICY_VERSION_REQUIRED} when the attempt
     *         did not pin a policy version, or {@link DomainErrorCode#PROMPT_POLICY_VERSION_MISMATCH}
     *         when the version does not belong to any {@link PromptPurpose}. A stable answer whose
     *         policy version is unknown could never be reproduced, migrated or reasoned about.
     *
     * <p><strong>One of the declared exceptions to the error-channel rule in {@link Guard}</strong>,
     * where the full list lives. A missing policy version reports a code instead of throwing, even for
     * null, because absence is itself a declared business condition of the prompt-policy contract
     * (prompt design §3.2). Two differences from the other two entries in that list, both deliberate:
     * this method asks whether <em>any</em> purpose owns the version — a revision records no declared
     * purpose to compare against — and it asks it through {@link PromptPurpose#ownerOf}. Every other
     * missing mandatory value in this package throws.
     */
    public static DomainResult<AnswerRevision> create(
            String messageId,
            int revisionNo,
            String reasoningContent,
            String answerContent,
            GenerationModel model,
            boolean thinkingEnabled,
            String promptPolicyVersion,
            long now) {
        // Argument shapes first, in the declared order of the arguments, for the reason
        // GenerationCommand.create states: a call carrying both a malformed argument and an
        // un-pinnable version must still report the caller bug rather than a business code. This entry
        // point was the one missed when that rule was first written down — a round-2-independent audit
        // of T-021 showed a single call carrying four separate caller bugs answered with
        // PROMPT_POLICY_VERSION_REQUIRED. The constructor repeats these checks because it is the
        // invariant authority for any future caller.
        Guard.id(messageId, "messageId");
        Guard.atLeast(revisionNo, 1, "revisionNo");
        Guard.text(reasoningContent, "reasoningContent");
        Guard.nonBlank(answerContent, "answerContent");
        Guard.object(model, "model");
        Guard.epochMillis(now, "createdAt");
        if (PromptPurpose.isAbsent(promptPolicyVersion)) {
            return DomainResult.fail(DomainErrorCode.PROMPT_POLICY_VERSION_REQUIRED,
                    "a stable revision must record the prompt policy version that produced it");
        }
        if (PromptPurpose.ownerOf(promptPolicyVersion) == null) {
            return DomainResult.fail(DomainErrorCode.PROMPT_POLICY_VERSION_MISMATCH,
                    "no prompt purpose owns policy version <" + promptPolicyVersion
                            + ">; a stable answer must be attributable to a known usage");
        }
        return DomainResult.ok(new AnswerRevision(
                Guard.newId(), messageId, revisionNo, reasoningContent, answerContent,
                model, thinkingEnabled, promptPolicyVersion, now));
    }

    /**
     * Rehydrates a revision already persisted for an existing message.
     *
     * <p>Not a creation path: on reload the stored row is the authority, so no policy-ownership check
     * is repeated. Identity fields are still validated so a corrupt row fails loudly. Any use of this
     * method inside a write path should stand out during review, exactly like
     * {@link BranchSource#restore}.
     */
    public static AnswerRevision restore(
            String id,
            String messageId,
            int revisionNo,
            String reasoningContent,
            String answerContent,
            GenerationModel model,
            boolean thinkingEnabled,
            String promptPolicyVersion,
            long createdAt) {
        return new AnswerRevision(id, messageId, revisionNo, reasoningContent, answerContent,
                model, thinkingEnabled, promptPolicyVersion, createdAt);
    }

    public boolean hasReasoning() {
        return !reasoningContent.isEmpty();
    }

    @Override
    public String toString() {
        return "AnswerRevision[no=" + revisionNo + ", model=" + model.wireId
                + ", policy=" + promptPolicyVersion + ", answerChars=" + answerContent.length() + "]";
    }
}
