package com.treelearn.app.artifact;

import com.treelearn.core.domain.Guard;
import com.treelearn.core.domain.PromptPurpose;

/**
 * The derived artifact kinds and the usage/baseline each one owns (technical design 10; prompt
 * design 6).
 *
 * <p>The table is the only place the mapping lives. {@link #ofPurpose} is an exhaustive lookup with
 * no default branch: a usage this build does not derive raises, and {@link PromptPurpose#LEARNING_ANSWER}
 * in particular can never be reached as a kind, so no derived call can borrow the teaching body.
 * Adding a usage to {@link PromptPurpose} does not compile until this table answers for it.
 *
 * <p><strong>No key-points kind.</strong> AI key points are a payload field of
 * {@link #BRANCH_SUMMARY} produced by the same structured response (prompt design 6.2, technical
 * design 10), so a separate kind would be a second prompt for one answer.
 *
 * <p><strong>Scope and baseline.</strong> {@link #scope} says which table {@code scope_id} names —
 * {@code ai_artifacts} carries no foreign key because that column is polymorphic (T-024).
 * {@link #revisionBound()} says whether the artifact is invalidated by a new stable answer
 * ({@code based_on_revision_id}) or only by its scope's existence ({@code input_digest}).
 * A title's input is immutable, so only its scope's life can invalidate it; a summary or a
 * recommendation must name the revision it describes.
 */
public enum ArtifactKind {

    /** The root branch title, from the root question alone. */
    ROOT_TITLE("root_title", PromptPurpose.ROOT_TITLE, Scope.BRANCH, false, false),

    /** A non-root branch title, from the source anchor and the first question. */
    BRANCH_TITLE("branch_title", PromptPurpose.BRANCH_TITLE, Scope.BRANCH, false, false),

    /** Branch summary plus its key points, bound to the revision the branch currently shows. */
    BRANCH_SUMMARY("branch_summary", PromptPurpose.BRANCH_SUMMARY, Scope.BRANCH, true, false),

    /** Follow-up questions bound to one specific stable revision. */
    ANSWER_RECOMMENDATIONS("answer_recommendations", PromptPurpose.ANSWER_RECOMMENDATIONS, Scope.BRANCH,
            true, false),

    /** Home recommendations, bound to the topic-tree outline digest and never to a revision. */
    HOME_RECOMMENDATIONS("home_recommendations", PromptPurpose.HOME_RECOMMENDATIONS, Scope.TOPIC, false,
            false),

    /** Context compression over older revisions, bound to the newest one it covers. */
    CONTEXT_COMPRESSION("context_compression", PromptPurpose.CONTEXT_COMPRESSION, Scope.BRANCH, true, true);

    /** Which table {@code scope_id} refers to. Polymorphic on purpose, so it is not a foreign key. */
    public enum Scope {
        BRANCH,
        TOPIC
    }

    /** The value stored in {@code ai_artifacts.kind}. Never a prompt prefix: the usage has its own column. */
    public final String wireId;

    public final PromptPurpose purpose;

    public final Scope scope;

    private final boolean revisionBound;

    private final boolean refusesEmptySummary;

    ArtifactKind(String wireId, PromptPurpose purpose, Scope scope, boolean revisionBound,
            boolean refusesEmptySummary) {
        this.wireId = Guard.nonBlank(wireId, "wireId");
        this.purpose = Guard.object(purpose, "purpose");
        this.scope = Guard.object(scope, "scope");
        this.revisionBound = revisionBound;
        this.refusesEmptySummary = refusesEmptySummary;
    }

    /** True when the artifact names the stable revision it was derived from. */
    public boolean revisionBound() {
        return revisionBound;
    }

    /** True only for {@link #CONTEXT_COMPRESSION}: an empty summary there is a failed compression. */
    public boolean refusesEmptySummary() {
        return refusesEmptySummary;
    }

    /**
     * @return the kind that serves {@code purpose}
     * @throws IllegalArgumentException when no kind serves it. The message names
     *         {@code LEARNING_ANSWER} explicitly, because borrowing the teaching prompt for a derived
     *         job is the exact mistake this table exists to make impossible.
     */
    public static ArtifactKind ofPurpose(PromptPurpose purpose) {
        Guard.object(purpose, "purpose");
        for (ArtifactKind kind : values()) {
            if (kind.purpose == purpose) {
                return kind;
            }
        }
        throw new IllegalArgumentException(purpose + " owns no derived artifact kind"
                + (purpose == PromptPurpose.LEARNING_ANSWER
                        ? "; LEARNING_ANSWER is the teaching usage and a derived artifact never "
                                + "borrows its body"
                        : ""));
    }

    /**
     * @return the kind a stored {@code kind} column names
     * @throws IllegalArgumentException when no kind uses that wire id — the same choice
     *         {@link PromptPurpose#fromPrefix} makes, and for the same reason: a row this build
     *         cannot name must fail loudly rather than be mapped onto another usage's prompt.
     */
    public static ArtifactKind fromWireId(String wireId) {
        for (ArtifactKind kind : values()) {
            if (kind.wireId.equals(wireId)) {
                return kind;
            }
        }
        throw new IllegalArgumentException("no artifact kind uses the wire id <" + wireId
                + ">; a stored kind must be one this build produces");
    }
}