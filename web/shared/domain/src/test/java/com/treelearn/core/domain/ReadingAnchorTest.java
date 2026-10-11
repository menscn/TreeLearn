package com.treelearn.core.domain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

/**
 * T-015 acceptance: the reading position is a semantic anchor, never an absolute scroll offset
 * (technical design 11).
 *
 * <p>An absolute {@code scrollY} stops meaning anything the moment the font size, the answer version
 * or the window changes — the same number then points at a different sentence. The stored form is
 * therefore "which message, optionally which revision, and how far inside it", and resolving it
 * against the current list is what turns that back into something a {@code RecyclerView} can scroll
 * to. Two fallbacks are exercised here because both are reachable without a bug in this class: the
 * message may be gone (the row's column is cleared by {@code ON DELETE SET NULL}, and a restored
 * backup or a hand-written row can leave it dangling), and the pinned revision may no longer be the
 * one the position displays (an answer was reset while the child branch kept its own history).
 */
public class ReadingAnchorTest {

    private static final long NOW = 1_700_000_000_000L;
    private static final String BRANCH = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa";
    private static final String REVISION = "dddddddd-dddd-4ddd-8ddd-dddddddddddd";
    private static final String REVISION_2 = "eeeeeeee-eeee-4eee-8eee-eeeeeeeeeeee";

    @Test
    public void aMessageThatStillExistsResolvesToItsOwnIndex() {
        List<Message> messages = conversation(3);
        ReadingAnchor anchor = ReadingAnchor.of(messages.get(1).id, null, 12, 30);

        ReadingPosition position = anchor.resolve(messages, 100);

        assertEquals(1, position.messageIndex);
        assertEquals(messages.get(1).id, position.messageId);
        assertEquals(12, position.listItemOffset);
        assertEquals(30, position.viewportOffset);
        assertFalse(position.atLiveEnd);
    }

    @Test
    public void offsetsAreClampedToWhatTheCurrentLayoutStillHas() {
        List<Message> messages = conversation(3);
        // Saved at a larger font: 400 px into an item that is now only 100 px tall. That is
        // "nearest legal", not "as stored" — the same answer, the end of it.
        ReadingAnchor anchor = ReadingAnchor.of(messages.get(2).id, null, 400, 30);

        ReadingPosition position = anchor.resolve(messages, 100);

        assertEquals(2, position.messageIndex);
        assertEquals(99, position.listItemOffset);
        assertEquals("an offset that still fits is left alone", 30, position.viewportOffset);
    }

    @Test
    public void aDeletedAnchorFallsBackToTheLiveEndWithoutInventingAnOffset() {
        List<Message> messages = conversation(3);
        ReadingAnchor dangling = ReadingAnchor.of(Guard.newId(), REVISION, 40, 80);

        ReadingPosition position = dangling.resolve(messages, 100);

        assertTrue(position.atLiveEnd);
        assertEquals(messages.size() - 1, position.messageIndex);
        assertEquals(messages.get(messages.size() - 1).id, position.messageId);
        assertEquals(0, position.listItemOffset);
        assertEquals(0, position.viewportOffset);
        assertNull("a position that had to fall back must not keep pointing at the lost revision",
                position.revisionId);
    }

    @Test
    public void anEmptyBranchHasNoIndexAtAll() {
        ReadingPosition position = ReadingAnchor.of(Guard.newId(), null, 0, 0)
                .resolve(Collections.<Message>emptyList(), 100);

        assertTrue(position.atLiveEnd);
        assertEquals(-1, position.messageIndex);
        assertNull(position.messageId);
    }

    @Test
    public void aRevisionThatIsNoLongerActiveIsRebasedOntoTheCurrentOne() {
        List<Message> messages = conversation(2);
        // The position now displays REVISION_2 after a reset; the child branch keeps its own REVISION.
        messages.set(1, ai(messages.get(1).id, 1, REVISION_2));
        ReadingAnchor pinned = ReadingAnchor.of(messages.get(1).id, REVISION, 5, 5);

        ReadingAnchor rebased = pinned.rebasedOn(messages.get(1).activeRevisionId());

        assertEquals(messages.get(1).id, rebased.messageId);
        assertEquals(REVISION_2, rebased.revisionId);
        assertEquals(5, rebased.listItemOffset);
        assertEquals(5, rebased.viewportOffset);
        // Rebasing is about the revision, not the offsets: the position inside the message is
        // still the same reading spot, and rebasing an already-current anchor changes nothing.
        assertSame(rebased, rebased.rebasedOn(REVISION_2));
        assertEquals(5, rebased.listItemOffset);
    }

    @Test
    public void aPositionThatCarriesNoRevisionStaysThatWayWhenRebased() {
        ReadingAnchor unbound = ReadingAnchor.atMessage(Guard.newId(), null);

        assertFalse(unbound.isRevisionBound());
        assertEquals(null, unbound.rebasedOn(null).revisionId);
        assertEquals(0, unbound.listItemOffset);
    }

    @Test
    public void malformedAnchorsAndLayoutsAreCallerBugs() {
        assertThrows(IllegalArgumentException.class,
                () -> ReadingAnchor.of("not-a-uuid", null, 0, 0));
        assertThrows(IllegalArgumentException.class,
                () -> ReadingAnchor.of(Guard.newId(), null, -1, 0));
        assertThrows(IllegalArgumentException.class,
                () -> ReadingAnchor.of(Guard.newId(), null, 0, -1));
        assertThrows(IllegalArgumentException.class,
                () -> ReadingAnchor.atMessage(Guard.newId(), null)
                        .resolve(conversation(1), 0));
        assertThrows(IllegalArgumentException.class,
                () -> ReadingAnchor.atMessage(Guard.newId(), null).resolve(null, 100));
    }

    private static List<Message> conversation(int size) {
        List<Message> messages = new ArrayList<Message>();
        for (int ordinal = 0; ordinal < size; ordinal++) {
            messages.add(ai(Guard.newId(), ordinal, ordinal % 2 == 1 ? REVISION : null));
        }
        return messages;
    }

    private static Message ai(String id, int ordinal, String revisionId) {
        return Message.restore(id, BRANCH, MessageRole.AI, ordinal, null, NOW, revisionId, null);
    }
}