package com.treelearn.core.domain;

/**
 * A fixed logical position inside a branch (technical design 5.1).
 *
 * <p>A message is a <em>position</em>, not a version container. The learner's text is stored once
 * and never changes; an AI position only references the attempt currently running and the stable
 * revision currently displayed. Resetting the answer switches which revision this position shows —
 * it never overwrites one, which is exactly what lets an old child branch keep its own history.
 */
public final class Message {

    public final String id;
    public final String branchId;
    public final MessageRole role;
    /** Branch-local order. Retry and reset never change it. */
    public final int ordinal;
    /** Immutable learner text; null for {@link MessageRole#AI} positions. */
    public final String userContent;
    public final long createdAt;

    private String activeRevisionId;
    private String activeOperationId;

    private Message(
            String id,
            String branchId,
            MessageRole role,
            int ordinal,
            String userContent,
            long createdAt,
            String activeRevisionId,
            String activeOperationId) {
        this.id = Guard.id(id, "id");
        this.branchId = Guard.id(branchId, "branchId");
        this.role = Guard.object(role, "role");
        this.ordinal = Guard.atLeast(ordinal, 0, "ordinal");
        this.createdAt = Guard.epochMillis(createdAt, "createdAt");
        if (role == MessageRole.USER) {
            this.userContent = Guard.nonBlank(userContent, "userContent");
            this.activeRevisionId = null;
            this.activeOperationId = null;
        } else {
            this.userContent = null;
            this.activeRevisionId = Guard.idOrNull(activeRevisionId, "activeRevisionId");
            this.activeOperationId = Guard.idOrNull(activeOperationId, "activeOperationId");
        }
    }

    /** Creates an immutable learner position. */
    public static Message user(String branchId, int ordinal, String content, long now) {
        return new Message(Guard.newId(), branchId, MessageRole.USER, ordinal, content, now, null, null);
    }

    /** Creates an AI position with no revision and no running attempt. */
    public static Message ai(String branchId, int ordinal, long now) {
        return new Message(Guard.newId(), branchId, MessageRole.AI, ordinal, null, now, null, null);
    }

    public boolean isUser() {
        return role == MessageRole.USER;
    }

    public boolean isAi() {
        return role == MessageRole.AI;
    }

    public String activeRevisionId() {
        return activeRevisionId;
    }

    public String activeOperationId() {
        return activeOperationId;
    }

    /**
     * Starts a new attempt at this position. Any previously displayed revision is left untouched:
     * the position keeps showing the old stable answer until the new attempt succeeds.
     */
    public DomainResult<Void> bindOperation(String operationId) {
        if (role != MessageRole.AI) {
            return DomainResult.fail(DomainErrorCode.MESSAGE_ROLE_MISMATCH,
                    "only an AI position can run a generation attempt");
        }
        if (activeOperationId != null) {
            return DomainResult.fail(DomainErrorCode.ILLEGAL_STATE_TRANSITION,
                    "an attempt is already running at this position: " + activeOperationId);
        }
        this.activeOperationId = Guard.id(operationId, "operationId");
        return DomainResult.ok();
    }

    /**
     * Atomically switches which stable revision this position displays. Called only inside the
     * transaction that also stores the new revision, so the position can never point at a revision
     * that does not exist yet.
     *
     * <p>A running attempt must be ended first ({@link #clearActiveOperation()}), in the same
     * transaction. Silently clearing it here would hide the fact that a display switch happened while
     * an attempt was still in flight — a state the coordinator is supposed to make impossible.
     */
    public DomainResult<Void> activateRevision(String revisionId) {
        if (role != MessageRole.AI) {
            return DomainResult.fail(DomainErrorCode.MESSAGE_ROLE_MISMATCH,
                    "only an AI position can display a revision");
        }
        String next = Guard.id(revisionId, "revisionId");
        if (activeOperationId != null) {
            return DomainResult.fail(DomainErrorCode.ILLEGAL_STATE_TRANSITION,
                    "cannot switch the displayed revision while attempt " + activeOperationId
                            + " is still running; end it in the same transaction first");
        }
        if (next.equals(activeRevisionId)) {
            return DomainResult.fail(DomainErrorCode.REVISION_IMMUTABLE,
                    "revision " + next + " is already the active version of this position");
        }
        this.activeRevisionId = next;
        return DomainResult.ok();
    }

    /** Clears the running attempt without touching the displayed revision. */
    public void clearActiveOperation() {
        this.activeOperationId = null;
    }
}
