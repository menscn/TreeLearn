package com.treelearn.core.domain;

/**
 * A fixed logical position inside a branch (technical design 5.1).
 *
 * <p>A message is a <em>position</em>, not a version container. The learner's text is stored once
 * and never changes; an AI position only references the attempt currently running and the stable
 * revision currently displayed. Resetting the answer switches which revision this position shows —
 * it never overwrites one, which is exactly what lets an old child branch keep its own history.
 *
 * <p>§6.2.19: a USER position may also carry the passage of the answer it asks about, so a
 * quoted follow-up still shows what it was asked about after it has been sent. The domain has one
 * spelling for "no quote" — the empty string, never null — and the store's {@code NULL} is
 * translated to it at the DAO boundary, so a freshly built position and a reloaded one look the
 * same to a caller.
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
    /**
     * §6.2.19: the passage of the answer this position asks about; the empty string when it
     * quotes nothing. Never null, and never carried by an AI position.
     */
    private final String quotedText;

    private String activeRevisionId;
    private String activeOperationId;

    private Message(
            String id,
            String branchId,
            MessageRole role,
            int ordinal,
            String userContent,
            String quotedText,
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
            this.quotedText = requiredQuote(quotedText);
            this.activeRevisionId = null;
            this.activeOperationId = null;
        } else {
            this.userContent = null;
            if (quotedText != null && !quotedText.isEmpty()) {
                throw new IllegalArgumentException("quotedText must be empty on an AI position:"
                        + " the store cannot produce a quote there, so a row carrying one is a"
                        + " corrupt row and must be named rather than quietly dropped");
            }
            this.quotedText = "";
            this.activeRevisionId = Guard.idOrNull(activeRevisionId, "activeRevisionId");
            this.activeOperationId = Guard.idOrNull(activeOperationId, "activeOperationId");
        }
    }

    /** Creates an immutable learner position that quotes nothing in particular. */
    public static Message user(String branchId, int ordinal, String content, long now) {
        return user(branchId, ordinal, content, "", now);
    }

    /**
     * Creates an immutable learner position that quotes the passage it asks about (§6.2.19).
     *
     * @param quotedText the passage the learner selected, or the empty string for "no quote".
     *                   It is kept exactly as handed over: the learner's selection is not trimmed
     *                   here, and only the empty string means "none".
     */
    public static Message user(String branchId, int ordinal, String content, String quotedText,
            long now) {
        return new Message(Guard.newId(), branchId, MessageRole.USER, ordinal, content, quotedText,
                now, null, null);
    }

    /** Creates an AI position with no revision, no running attempt and therefore no quote. */
    public static Message ai(String branchId, int ordinal, long now) {
        return new Message(Guard.newId(), branchId, MessageRole.AI, ordinal, null, "", now, null,
                null);
    }

    /**
     * Rehydrates a position that was already persisted and quotes nothing (§6.2.19 columns did
     * not exist when these rows were written, and a row without a quote is the common case).
     *
     * @see #restore(String, String, MessageRole, int, String, String, long, String, String)
     */
    public static Message restore(
            String id,
            String branchId,
            MessageRole role,
            int ordinal,
            String userContent,
            long createdAt,
            String activeRevisionId,
            String activeOperationId) {
        return restore(id, branchId, role, ordinal, userContent, "", createdAt, activeRevisionId,
                activeOperationId);
    }

    /**
     * Rehydrates a message that was already persisted, including the passage it quotes and
     * whichever revision and attempt an AI position currently points at.
     *
     * <p>This is not a creation path: on reload the row is the authority, so no business rule is
     * re-decided. Identity, role, the role/content pairing and the role/quote pairing are still
     * validated, so a corrupt row fails loudly instead of producing a half-built message.
     *
     * <p>It is needed because {@link #ai} and {@link #user} always start with no active references,
     * and the only other way to set them ({@link #bindOperation}, {@link #activateRevision}) is a
     * business transition with its own rules. Replaying those on every read would treat a reload as
     * a state change.
     *
     * @param quotedText the stored quote, with the store's {@code NULL} already translated to the
     *                   empty string by the caller that read the row ({@code MessageDao})
     */
    public static Message restore(
            String id,
            String branchId,
            MessageRole role,
            int ordinal,
            String userContent,
            String quotedText,
            long createdAt,
            String activeRevisionId,
            String activeOperationId) {
        return new Message(id, branchId, role, ordinal, userContent, quotedText, createdAt,
                activeRevisionId, activeOperationId);
    }

    public boolean isUser() {
        return role == MessageRole.USER;
    }

    public boolean isAi() {
        return role == MessageRole.AI;
    }

    /**
     * §6.2.19: the passage of the answer this position asks about, or the empty string when it
     * asks about nothing in particular.
     *
     * <p>Absence has exactly one spelling on purpose. The store keeps it as {@code NULL} and the
     * DAO translates it while reading, so no caller has to null-check before putting the quote on
     * screen, and a rebuilt position and a freshly created one cannot look different.
     */
    public String quotedText() {
        return quotedText;
    }

    private static String requiredQuote(String quotedText) {
        if (quotedText == null) {
            throw new IllegalArgumentException("quotedText must not be null; \"no quote\" is the"
                    + " empty string, and the store's NULL is translated at the DAO boundary");
        }
        return quotedText;
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
