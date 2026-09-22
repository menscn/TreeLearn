package com.treelearn.core.domain;

import java.util.List;

/**
 * Where inside a branch the learner was reading (technical design 11).
 *
 * <p><strong>Why not a scroll offset.</strong> An absolute {@code scrollY} stops meaning anything
 * the moment the font size, the answer version or the window changes: the same number then points at
 * a different sentence, and a saved position silently becomes a wrong one. The stored form is
 * therefore semantic — which message, which revision it showed, and how far inside it — and
 * {@link #resolve} turns that back into an index against the list that exists right now.
 *
 * <p>The offsets are deliberately small: pixels inside the one list item the anchor names (how far
 * down the answer the learner had read) and pixels of the viewport above it. Neither is a page
 * offset, so neither depends on the total content height.
 */
public final class ReadingAnchor {

    public final String messageId;
    /** Null means "whatever this position displays" — the anchor follows a reset instead of breaking. */
    public final String revisionId;
    /** Pixels scrolled into the anchored list item. */
    public final int listItemOffset;
    /** Pixels of viewport above the anchored item. */
    public final int viewportOffset;

    private ReadingAnchor(String messageId, String revisionId, int listItemOffset, int viewportOffset) {
        this.messageId = Guard.id(messageId, "messageId");
        this.revisionId = Guard.idOrNull(revisionId, "revisionId");
        this.listItemOffset = Guard.atLeast(listItemOffset, 0, "listItemOffset");
        this.viewportOffset = Guard.atLeast(viewportOffset, 0, "viewportOffset");
    }

    /** Anchors the top of a message, with no offset inside it. */
    public static ReadingAnchor atMessage(String messageId, String revisionId) {
        return new ReadingAnchor(messageId, revisionId, 0, 0);
    }

    public static ReadingAnchor of(String messageId, String revisionId, int listItemOffset,
            int viewportOffset) {
        return new ReadingAnchor(messageId, revisionId, listItemOffset, viewportOffset);
    }

    public boolean isRevisionBound() {
        return revisionId != null;
    }

    /**
     * Follows the message onto whichever revision it displays now.
     *
     * <p>A reset switches a position's revision without touching the child branches that forked from
     * the old one (design 5.1). An anchor pinned to the replaced revision would point at a version
     * nobody shows any more, so the message stays and the revision moves. Passing null — or an
     * unchanged revision — is a no-op, and the same instance comes back, because nothing about the
     * reading position changed.
     */
    public ReadingAnchor rebasedOn(String activeRevisionId) {
        String next = Guard.idOrNull(activeRevisionId, "activeRevisionId");
        if (next == null ? revisionId == null : next.equals(revisionId)) {
            return this;
        }
        return new ReadingAnchor(messageId, next, listItemOffset, viewportOffset);
    }

    /**
     * Resolves this anchor against the conversation as it is now.
     *
     * <p><strong>Caller obligation this type cannot observe:</strong> {@code messages} must be the
     * branch's messages in reading order (the order {@code MessageDao.findByBranch} returns, by
     * ordinal). "Newest message" is the last element, so a caller that passes them shuffled gets a
     * legal-looking position at the wrong end — nothing here can tell.
     *
     * <p>Two fallbacks, both reachable without a bug in this class:
     *
     * <ul>
     *   <li>The message is gone. The store clears the anchor when its message is deleted
     *       ({@code ON DELETE SET NULL}), and a restored backup or a hand-written row can leave one
     *       dangling. Nothing is left to point at, so the position moves to the live end — the same
     *       place the cleared column leaves it.
     *   <li>The offset inside the item no longer fits the layout, which is the ordinary case after a
     *       font or window change: 400 px into an item that is now 90 px tall is clamped to the end
     *       of that item, because "further down the same answer" is still where the learner was. A
     *       negative offset is not clamped — it cannot have been stored in the first place (see
     *       {@link #of}), so clamping one here would be a branch nothing can reach.
     * </ul>
     */
    public ReadingPosition resolve(List<Message> messages, int itemHeightPx) {
        if (messages == null) {
            throw new IllegalArgumentException("messages must not be null");
        }
        if (itemHeightPx <= 0) {
            throw new IllegalArgumentException("itemHeightPx must be positive: " + itemHeightPx);
        }
        int index = indexOf(messages);
        if (index < 0) {
            return ReadingPosition.liveEnd(messages);
        }
        Message message = messages.get(index);
        return new ReadingPosition(
                message.id,
                rebasedOn(message.activeRevisionId()).revisionId,
                index,
                Math.min(listItemOffset, itemHeightPx - 1),
                viewportOffset,
                false);
    }

    private int indexOf(List<Message> messages) {
        for (int i = 0; i < messages.size(); i++) {
            if (messageId.equals(messages.get(i).id)) {
                return i;
            }
        }
        return -1;
    }


    @Override
    public String toString() {
        return "ReadingAnchor[" + messageId + (revisionId == null ? "" : "@" + revisionId)
                + " +" + listItemOffset + "/+" + viewportOffset + "]";
    }
}
