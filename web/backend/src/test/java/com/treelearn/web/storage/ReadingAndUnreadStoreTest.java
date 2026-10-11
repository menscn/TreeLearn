package com.treelearn.web.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.treelearn.core.domain.Branch;
import com.treelearn.core.domain.Message;
import com.treelearn.core.domain.ReadingAnchor;
import org.junit.jupiter.api.Test;

/**
 * T-2026-10-10-010: the semantic reading position and the derived unread pair, on real sqlite-jdbc.
 * Unread is only ever the gap between the two sequence columns — there is no independent counter.
 */
class ReadingAndUnreadStoreTest extends AbstractStoreTest {

    @Test
    void aReadingPositionRoundTripsAndClearsWithItsMessage() {
        TopicStore.Created created = topics.create(T0);
        String rootId = created.rootBranch.id;
        Message question = messages.appendUserMessage(rootId, 0, "问题", null, T0);

        branches.writeReadingPosition(rootId, ReadingAnchor.of(question.id, null, 42, 7));
        ReadingAnchor read = branches.readReadingPosition(rootId).orElseThrow();
        assertEquals(question.id, read.messageId);
        assertNull(read.revisionId);
        assertEquals(42, read.listItemOffset);
        assertEquals(7, read.viewportOffset);

        deleteMessage(question.id);
        assertFalse(branches.readReadingPosition(rootId).isPresent(),
                "clearing the anchor message (ON DELETE SET NULL) must leave no reading position");
    }

    @Test
    void aBranchWithoutAStoredPositionHasNone() {
        TopicStore.Created created = topics.create(T0);
        assertFalse(branches.readReadingPosition(created.rootBranch.id).isPresent());
    }

    @Test
    void unreadDerivesFromTheSequenceGapAndNeverMovesBackwardsOrPastTheLatest() {
        TopicStore.Created created = topics.create(T0);
        String rootId = created.rootBranch.id;

        branches.incrementLatestStable(rootId, T0 + 1);
        Branch afterOne = branches.incrementLatestStable(rootId, T0 + 2);
        assertEquals(2, afterOne.latestStableSequence());
        assertEquals(0, afterOne.lastSeenStableSequence());
        assertEquals(2, afterOne.unreadCount());

        assertTrue(branches.markSeenThrough(rootId, 2).isOk());
        Branch seen = branches.get(rootId).orElseThrow();
        assertEquals(2, seen.lastSeenStableSequence());
        assertEquals(0, seen.unreadCount());

        assertTrue(branches.markSeenThrough(rootId, 3).isFailure(),
                "a sequence that does not exist yet must not be marked read");
        assertTrue(branches.markSeenThrough(rootId, 1).isFailure(),
                "the reading position must never move backwards");
        assertEquals(2, branches.get(rootId).orElseThrow().lastSeenStableSequence(),
                "a refused mark must not have written anything");
    }
}
