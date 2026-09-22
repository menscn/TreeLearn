package com.treelearn.core.domain;

import java.util.List;

/**
 * A reading anchor resolved against the conversation that exists right now (technical design 11).
 *
 * <p>This is what the chat screen actually scrolls to. It is a <em>resolved</em> value, not a stored
 * one: the index and the offsets are only valid for the layout that produced them, which is exactly
 * why the stored form ({@link ReadingAnchor}) carries neither.
 */
public final class ReadingPosition {

    /** The message at this position; null only when the branch has no messages at all. */
    public final String messageId;
    /** The revision that spot displays now, or null when it displays none. */
    public final String revisionId;
    /** Index into the current message list, or -1 when there is nothing to scroll to. */
    public final int messageIndex;
    public final int listItemOffset;
    public final int viewportOffset;
    /**
     * True when the stored anchor could not be used and the live end was chosen instead.
     *
     * <p>The screen needs to know: "restored your place" and "we could not find your place, here is
     * the newest answer" are different messages to a learner, and only the first one explains why the
     * view is not where they expected.
     */
    public final boolean atLiveEnd;

    ReadingPosition(String messageId, String revisionId, int messageIndex, int listItemOffset,
            int viewportOffset, boolean atLiveEnd) {
        this.messageId = messageId;
        this.revisionId = revisionId;
        this.messageIndex = messageIndex;
        this.listItemOffset = Guard.atLeast(listItemOffset, 0, "listItemOffset");
        this.viewportOffset = Guard.atLeast(viewportOffset, 0, "viewportOffset");
        this.atLiveEnd = atLiveEnd;
    }

    /**
     * The end of the conversation: where a branch with nothing stored — or one whose stored anchor
     * was cleared with its message — opens (design 11).
     *
     * <p><strong>Caller obligation this factory cannot observe:</strong> {@code messages} must be in
     * reading order. "The newest one" is the last element, and nothing here can tell a shuffled list
     * from an ordered one.
     */
    public static ReadingPosition liveEnd(List<Message> messages) {
        if (messages == null) {
            throw new IllegalArgumentException("messages must not be null");
        }
        if (messages.isEmpty()) {
            return new ReadingPosition(null, null, -1, 0, 0, true);
        }
        Message last = messages.get(messages.size() - 1);
        return new ReadingPosition(last.id, last.activeRevisionId(), messages.size() - 1, 0, 0, true);
    }

    @Override
    public String toString() {
        return "ReadingPosition[#" + messageIndex + " +" + listItemOffset + "/+"
                + viewportOffset + (atLiveEnd ? " live-end" : "") + "]";
    }
}