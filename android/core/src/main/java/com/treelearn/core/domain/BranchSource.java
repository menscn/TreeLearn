package com.treelearn.core.domain;

/**
 * Immutable provenance of a non-root branch (technical design 5.1, 5.2).
 *
 * <p>This is the record that protects history. A child branch stores the exact
 * {@code sourceRevisionId} it was forked from, so resetting the parent's active answer — which
 * switches {@link Message#activeRevisionId()} — never migrates the child. The child keeps pointing
 * at the reasoning and answer that existed when it was created.
 *
 * <p>There are exactly two ways to obtain an instance, and neither one lets a caller quietly turn a
 * partial answer into history:
 *
 * <ul>
 *   <li>{@link #fromOperation} — the only <em>creation</em> path. It refuses anything that is not
 *       {@link GenerationState#STABLE}, because only a completed attempt produces an
 *       {@link AnswerRevision}.
 *   <li>{@link #restore} — rehydration only. It exists because {@code :data} must rebuild the entity
 *       from a database row, where the branch already exists and its provenance is an established
 *       fact rather than a new decision. A round-3-independent audit found the constructor was
 *       public, which let a caller construct provenance directly and bypass the STABLE rule; the
 *       constructor is now private and {@code restore} is named so that any use of it in a write
 *       path is obvious during review.
 * </ul>
 */
public final class BranchSource {

    public final String parentBranchId;
    public final String sourceMessageId;
    public final String sourceRevisionId;
    public final BranchAnchor anchor;
    public final String contextSnapshotId;

    private BranchSource(
            String parentBranchId,
            String sourceMessageId,
            String sourceRevisionId,
            BranchAnchor anchor,
            String contextSnapshotId) {
        this.parentBranchId = Guard.id(parentBranchId, "parentBranchId");
        this.sourceMessageId = Guard.id(sourceMessageId, "sourceMessageId");
        this.sourceRevisionId = Guard.id(sourceRevisionId, "sourceRevisionId");
        this.anchor = Guard.object(anchor, "anchor");
        this.contextSnapshotId = Guard.id(contextSnapshotId, "contextSnapshotId");
    }

    /**
     * The only creation path: builds provenance, refusing anything that is not a completed attempt.
     *
     * <p>Input is split across the two error channels by the rule stated in {@link Guard}: absent or
     * malformed arguments (a null state, a missing or non-UUID revision id) are caller bugs and throw
     * {@link IllegalArgumentException}; a well-formed but <em>unfinished</em> attempt is a business
     * refusal and returns a failure with {@link DomainErrorCode#OPERATION_NOT_STABLE}.
     *
     * @param operationState the state the source generation attempt ended in
     * @param stableRevisionId the revision produced by that attempt; required once STABLE
     * @return a failure with {@link DomainErrorCode#OPERATION_NOT_STABLE} when the attempt is still
     *         streaming, failed, interrupted or canceled
     */
    public static DomainResult<BranchSource> fromOperation(
            String parentBranchId,
            String sourceMessageId,
            String contextSnapshotId,
            GenerationState operationState,
            String stableRevisionId,
            BranchAnchor anchor) {
        if (operationState == null) {
            throw new IllegalArgumentException("operationState must not be null");
        }
        if (!operationState.isStable()) {
            return DomainResult.fail(DomainErrorCode.OPERATION_NOT_STABLE,
                    "a branch source requires a STABLE attempt but the source attempt is " + operationState
                            + "; a partial answer must never be frozen into branch history");
        }
        if (stableRevisionId == null || stableRevisionId.isEmpty()) {
            // A STABLE attempt always produced a revision, so omitting it is a caller bug rather than
            // a business refusal. A round-3-independent audit found this case reused the
            // OPERATION_NOT_STABLE business code while a null operationState threw, i.e. the same
            // class of malformed input travelled on two different channels.
            throw new IllegalArgumentException(
                    "stableRevisionId must name the revision produced by a STABLE attempt");
        }
        return DomainResult.ok(new BranchSource(
                parentBranchId, sourceMessageId, stableRevisionId, anchor, contextSnapshotId));
    }

    /**
     * Rehydrates provenance already persisted for an existing branch.
     *
     * <p>This is not a creation path: on reload the row is the authority, so no {@link
     * GenerationState} is available or needed. Identities and the anchor are still validated, so a
     * corrupt row fails loudly instead of producing a half-built branch.
     */
    public static BranchSource restore(
            String parentBranchId,
            String sourceMessageId,
            String sourceRevisionId,
            BranchAnchor anchor,
            String contextSnapshotId) {
        return new BranchSource(parentBranchId, sourceMessageId, sourceRevisionId, anchor, contextSnapshotId);
    }

    /** @return true when this provenance pins exactly the given revision. */
    public boolean pinsRevision(String revisionId) {
        return sourceRevisionId.equals(revisionId);
    }

    @Override
    public String toString() {
        return "BranchSource[parent=" + parentBranchId + ", revision=" + sourceRevisionId
                + ", " + anchor + "]";
    }
}
