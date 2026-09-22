package com.treelearn.app.reading;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.treelearn.core.domain.Branch;
import com.treelearn.core.domain.Guard;
import com.treelearn.core.domain.Message;
import com.treelearn.core.domain.MessageRole;
import com.treelearn.core.domain.ReadingAnchor;
import com.treelearn.core.domain.ReadingPosition;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.Test;

/**
 * T-015 acceptance: what the chat screen stores and restores when a branch is opened and left.
 *
 * <p>This is the only production consumer of the two "restore" rules that T-013's screen depends on:
 * a position is stored as a message plus an offset inside it (never a scroll offset, which stops
 * meaning anything after a font or version change), and the unread badge is cleared through the
 * branch's own sequence authority, only when the newest unread answer really entered the viewport.
 */
public class ReadingSessionTest {

    private static final long NOW = 1_700_000_000_000L;
    private static final String TOPIC = "11111111-1111-4111-8111-111111111111";
    private static final String REVISION = "dddddddd-dddd-4ddd-8ddd-dddddddddddd";

    @Test
    public void openingRestoresTheStoredSemanticPosition() {
        Branch branch = Branch.root(TOPIC, NOW);
        List<Message> messages = conversation(3);
        FakeStore store = new FakeStore();
        store.anchor = ReadingAnchor.of(messages.get(1).id, REVISION, 18, 6);

        ReadingPosition position = new ReadingSession(store).open(branch, messages, 90);

        assertEquals(1, position.messageIndex);
        assertEquals(18, position.listItemOffset);
        assertEquals(6, position.viewportOffset);
        assertEquals(REVISION, position.revisionId);
        assertFalse(position.atLiveEnd);
    }

    @Test
    public void aBranchWithNothingStoredOpensAtTheLiveEnd() {
        Branch branch = Branch.root(TOPIC, NOW);
        List<Message> messages = conversation(2);

        ReadingPosition position = new ReadingSession(new FakeStore()).open(branch, messages, 90);

        assertEquals(1, position.messageIndex);
        assertEquals(0, position.listItemOffset);
        assertTrue(position.atLiveEnd);
    }

    @Test
    public void aStoredPositionWhoseMessageIsGoneFallsBackToTheLiveEnd() {
        Branch branch = Branch.root(TOPIC, NOW);
        List<Message> messages = conversation(3);
        FakeStore store = new FakeStore();
        store.anchor = ReadingAnchor.of(Guard.newId(), null, 40, 40);

        ReadingPosition position = new ReadingSession(store).open(branch, messages, 90);

        assertEquals(2, position.messageIndex);
        assertTrue(position.atLiveEnd);
        // The position now describes the newest message and whatever it displays; it does not keep
        // the revision of the anchor that could not be used.
        assertEquals(messages.get(2).activeRevisionId(), position.revisionId);
    }

    @Test
    public void aPinnedRevisionThatWasResetIsRebasedOntoTheLiveOneWithoutMovingThePosition() {
        Branch branch = Branch.root(TOPIC, NOW);
        List<Message> messages = conversation(3);
        // A reset replaced this position's displayed revision; the stored anchor still pins the old
        // one. The fixture must really differ, or the case would pass without the rebase existing.
        String liveRevision = "eeeeeeee-eeee-4eee-8eee-eeeeeeeeeeee";
        messages.set(2, Message.restore(messages.get(2).id, "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa",
                MessageRole.AI, 2, null, NOW, liveRevision, null));
        FakeStore store = new FakeStore();
        store.anchor = ReadingAnchor.of(messages.get(2).id, REVISION, 11, 0);

        ReadingPosition position = new ReadingSession(store).open(branch, messages, 90);

        assertEquals(2, position.messageIndex);
        assertEquals("the position shows whatever the message shows now", liveRevision,
                position.revisionId);
        assertEquals(11, position.listItemOffset);
    }

    @Test
    public void theNewestAnswerEnteringTheViewportIsPersistedThroughTheBranchAuthority() {
        Branch branch = Branch.root(TOPIC, NOW);
        List<Message> messages = conversation(2);
        branch.onStableAnswer(NOW);
        FakeStore store = new FakeStore();

        boolean advanced = new ReadingSession(store).onViewport(branch, messages,
                visible(messages.get(1).id));

        assertTrue(advanced);
        assertEquals(1, store.savedSeen);
        assertEquals(1, store.lastSeenStored);
    }

    @Test
    public void anOlderAnswerEnteringTheViewportPersistsNothing() {
        Branch branch = Branch.root(TOPIC, NOW);
        List<Message> messages = conversation(2);
        branch.onStableAnswer(NOW);
        branch.onStableAnswer(NOW);
        FakeStore store = new FakeStore();

        boolean advanced = new ReadingSession(store).onViewport(branch, messages,
                visible(messages.get(0).id));

        assertFalse(advanced);
        assertEquals(0, store.savedSeen);
        assertEquals(0, branch.lastSeenStableSequence());
    }

    @Test
    public void leavingStoresTheMessageAndTheOffsetInsideIt() {
        Branch branch = Branch.root(TOPIC, NOW);
        List<Message> messages = conversation(3);
        FakeStore store = new FakeStore();

        new ReadingSession(store).remember(branch,
                ReadingAnchor.of(messages.get(1).id, null, 37, 12));

        assertEquals(branch.id, store.savedBranchId);
        assertEquals(messages.get(1).id, store.savedAnchor.messageId);
        assertEquals(37, store.savedAnchor.listItemOffset);
        assertEquals(12, store.savedAnchor.viewportOffset);
    }

    private static Set<String> visible(String... messageIds) {
        return new HashSet<String>(Arrays.asList(messageIds));
    }

    private static List<Message> conversation(int size) {
        List<Message> messages = new ArrayList<Message>();
        for (int ordinal = 0; ordinal < size; ordinal++) {
            messages.add(Message.restore(Guard.newId(), "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa",
                    MessageRole.AI, ordinal, null, NOW, REVISION, null));
        }
        return messages;
    }

    private static final class FakeStore implements ReadingStore {

        ReadingAnchor anchor;
        String savedBranchId;
        ReadingAnchor savedAnchor;
        int savedSeen;
        long lastSeenStored;

        @Override
        public ReadingAnchor anchorOf(String branchId) {
            return anchor;
        }

        @Override
        public void saveAnchor(String branchId, ReadingAnchor anchor) {
            savedBranchId = branchId;
            savedAnchor = anchor;
        }

        @Override
        public void saveSeen(Branch branch) {
            savedSeen++;
            lastSeenStored = branch.lastSeenStableSequence();
        }
    }
}