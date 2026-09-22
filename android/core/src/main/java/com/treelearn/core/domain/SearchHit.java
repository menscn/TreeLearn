package com.treelearn.core.domain;

import java.util.Comparator;

/**
 * One located search result (technical design 6.3).
 *
 * <p>A hit is never a bare string. It names the branch it belongs to and, when the match is inside a
 * message, the exact message and the revision that message displays — that is what lets a result
 * jump back to the semantic reading position rather than to "roughly the top of that conversation".
 * A title or an anchor match is deliberately <em>not</em> message-bound for titles, and the shape
 * refuses to pretend otherwise: a hit that names no message cannot carry an ordinal or a revision.
 *
 * <p><strong>The order is total and lives here.</strong> SQLite's {@code UNION ALL} promises nothing
 * about row order, and "the results are the same every time" is a product promise, not a happy
 * accident, so the DAO sorts by {@link #DISPLAY_ORDER} before returning. Field first (a title is how
 * a learner recognises a thread; a reasoning match is the deepest kind), then the conversation's own
 * order inside the branch, then the revision number so a reset answer does not shuffle the list.
 */
public final class SearchHit {

    /** Which stored text matched. The partial pair is a failed attempt that never became a revision. */
    public enum Field {
        TITLE,
        QUESTION,
        ANCHOR,
        ANSWER,
        REASONING,
        PARTIAL_ANSWER,
        PARTIAL_REASONING
    }

    /** What the screen sorts by. See the class comment: this is the only ordering authority. */
    public static final Comparator<SearchHit> DISPLAY_ORDER =
            Comparator.comparingInt((SearchHit hit) -> hit.field.ordinal())
                    .thenComparing(hit -> hit.topicId)
                    .thenComparing(hit -> hit.branchId)
                    .thenComparingInt(hit -> hit.messageOrdinal)
                    .thenComparingInt(hit -> hit.revisionNo)
                    .thenComparing(hit -> hit.messageId,
                            Comparator.nullsFirst(Comparator.<String>naturalOrder()))
                    .thenComparing(hit -> hit.revisionId,
                            Comparator.nullsFirst(Comparator.<String>naturalOrder()));

    public final Field field;
    public final String topicId;
    public final String branchId;
    /** Null when the match is not inside a message (a title hit). */
    public final String messageId;
    /** Branch-local reading order, or -1 when this hit is not message-bound. */
    public final int messageOrdinal;
    /** Null when no stable revision is involved (a title, or a failed attempt's partial text). */
    public final String revisionId;
    /** The revision number, or 0 when there is no revision. */
    public final int revisionNo;
    /** The matched text itself, from the one column that matched. The screen truncates it. */
    public final String snippet;

    private SearchHit(Field field, String topicId, String branchId, String messageId,
            int messageOrdinal, String revisionId, int revisionNo, String snippet) {
        this.field = Guard.object(field, "field");
        this.topicId = Guard.id(topicId, "topicId");
        this.branchId = Guard.id(branchId, "branchId");
        this.messageId = Guard.idOrNull(messageId, "messageId");
        if (this.messageId == null && messageOrdinal != -1) {
            throw new IllegalArgumentException("a hit that names no message must not claim an"
                    + " ordinal: " + messageOrdinal);
        }
        if (this.messageId != null && messageOrdinal < 0) {
            throw new IllegalArgumentException("a message-bound hit needs the message's ordinal,"
                    + " not " + messageOrdinal);
        }
        this.messageOrdinal = messageOrdinal;
        this.revisionId = Guard.idOrNull(revisionId, "revisionId");
        if (this.revisionId != null && this.messageId == null) {
            throw new IllegalArgumentException("revision " + revisionId + " belongs to a message,"
                    + " but this hit names none");
        }
        this.revisionNo = Guard.atLeast(revisionNo, 0, "revisionNo");
        this.snippet = Guard.text(snippet, "snippet");
    }

    /**
     * Builds a hit from one stored row.
     *
     * <p>Input is validated by the same rules as every other domain value (see {@link Guard}): a
     * malformed identifier, a null snippet or a message-less hit claiming an ordinal is a caller
     * bug, not a business outcome, because the only producer is the search statement itself.
     */
    public static SearchHit of(Field field, String topicId, String branchId, String messageId,
            int messageOrdinal, String revisionId, int revisionNo, String snippet) {
        return new SearchHit(field, topicId, branchId, messageId, messageOrdinal, revisionId,
                revisionNo, snippet);
    }

    /**
     * Where to go when this hit is opened, or null for a hit that has no position (a title).
     *
     * <p>The anchor is the same semantic form the chat screen stores when it is left (design 11), so
     * opening a search result and returning to a branch later are the same kind of position.
     */
    public ReadingAnchor locatorAnchor() {
        return messageId == null ? null : ReadingAnchor.atMessage(messageId, revisionId);
    }

    /**
     * Value equality over everything that identifies the match, so a screen can remove exactly the
     * result it was handed instead of guessing at an index.
     *
     * <p>The snippet takes part: two hits of the same field on the same position are the same row of
     * the same query, and a result list that holds two of them can drop one without ambiguity.
     */
    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof SearchHit)) {
            return false;
        }
        SearchHit that = (SearchHit) other;
        return field == that.field
                && messageOrdinal == that.messageOrdinal
                && revisionNo == that.revisionNo
                && topicId.equals(that.topicId)
                && branchId.equals(that.branchId)
                && snippet.equals(that.snippet)
                && java.util.Objects.equals(messageId, that.messageId)
                && java.util.Objects.equals(revisionId, that.revisionId);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(field, topicId, branchId, messageId, messageOrdinal,
                revisionId, revisionNo, snippet);
    }

    @Override
    public String toString() {
        return "SearchHit[" + field + " " + (messageId == null ? branchId : messageId) + "]";
    }
}
