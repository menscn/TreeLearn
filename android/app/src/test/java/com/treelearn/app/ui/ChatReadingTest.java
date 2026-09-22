package com.treelearn.app.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.treelearn.app.reading.ReadingStore;
import com.treelearn.core.domain.Branch;
import com.treelearn.core.domain.Message;
import com.treelearn.core.domain.MessageRole;
import com.treelearn.core.domain.ReadingAnchor;
import com.treelearn.core.domain.ReadingPosition;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executor;

import org.junit.Test;

/**
 * T-042 acceptance: the conversation page saves where the learner stopped reading and opens there
 * again, and the unread badge only ever moves forward.
 *
 * <p>Four behaviours, each one from the task file rather than from a rendering choice:
 *
 * <ul>
 *   <li><strong>Entering a branch restores the stored position.</strong> The screen reports the
 *       height of the row the stored position names; the presenter answers with the position the
 *       screen must scroll to.
 *   <li><strong>Leaving stores it.</strong> The position the screen hands over is written through
 *       the reading session, never composed here.
 *   <li><strong>A position whose message is gone degenerates.</strong> The store clears the anchor
 *       with its message, and a still-dangling reference must open at the live end without throwing.
 *   <li><strong>Unread moves forward only.</strong> Only the newest unread answer entering the
 *       viewport advances the sequence, and no sequence ever moves backwards.
 * </ul>
 *
 * <p>The fifth requirement is a thread one: the save must happen on the injected background
 * executor, so a queueing executor is drained explicitly and the store is asserted to be untouched
 * before it runs.
 */
public final class ChatReadingTest {

    private static final CopyLabels LABELS = CopyLabels.of("思考过程", "最终回答");
    private static final long NOW = 1_700_000_000_000L;
    private static final String TOPIC = "99999999-9999-4999-8999-999999999999";
    private static final String REVISION = ChatTestDoubles.REV_1;
    private static final String[] ANSWER_IDS = {
            "a1a1a1a1-a1a1-4a1a-8a1a-a1a1a1a1a1a1",
            "b1b1b1b1-b1b1-4b1b-8b1b-b1b1b1b1b1b1",
            "c1c1c1c1-c1c1-4c1c-8c1c-c1c1c1c1c1c1"};
    /** A message that is not in the conversation: what a stored position looks like after a delete. */
    private static final String GONE = "d1d1d1d1-d1d1-4d1d-8d1d-d1d1d1d1d1d1";

    private final ChatTestDoubles.RecordingChatSink sink = new ChatTestDoubles.RecordingChatSink();
    private final ChatTestDoubles.FakeChatData data = new ChatTestDoubles.FakeChatData();
    private final ChatTestDoubles.FakeChatCommands commands = new ChatTestDoubles.FakeChatCommands();
    private final TestDoubles.FakeKeyVault key = new TestDoubles.FakeKeyVault(true);
    private final TestDoubles.FakeSettingsStore settings = new TestDoubles.FakeSettingsStore();
    private final FakeReadingStore reading = new FakeReadingStore();

    @Test
    public void openingTheBranchRestoresTheStoredSemanticPosition() {
        List<Message> rows = rows(3);
        data.snapshot = snapshot(rows, branch(0L, 0L));
        reading.anchor = ReadingAnchor.of(rows.get(1).id, REVISION, 18, 6);

        ChatPresenter presenter = presenter(new TestDoubles.InlineExecutor());
        presenter.start();
        presenter.onRowsLaidOut(90, Collections.<String>emptySet());

        ReadingPosition position = sink.restored.get(0);
        assertEquals(1, position.messageIndex);
        assertEquals(18, position.listItemOffset);
        assertEquals(6, position.viewportOffset);
        assertEquals(REVISION, position.revisionId);
        assertFalse(position.atLiveEnd);
    }

    @Test
    public void aBranchWithNothingStoredOpensAtTheLiveEnd() {
        List<Message> rows = rows(2);
        data.snapshot = snapshot(rows, branch(0L, 0L));

        ChatPresenter presenter = presenter(new TestDoubles.InlineExecutor());
        presenter.start();
        presenter.onRowsLaidOut(90, Collections.<String>emptySet());

        ReadingPosition position = sink.restored.get(0);
        assertEquals(1, position.messageIndex);
        assertEquals(0, position.listItemOffset);
        assertTrue(position.atLiveEnd);
    }

    @Test
    public void aStoredPositionWhoseMessageIsGoneDegeneratesWithoutCrashing() {
        List<Message> rows = rows(3);
        data.snapshot = snapshot(rows, branch(0L, 0L));
        reading.anchor = ReadingAnchor.of(GONE, null, 40, 40);

        ChatPresenter presenter = presenter(new TestDoubles.InlineExecutor());
        presenter.start();
        presenter.onRowsLaidOut(90, Collections.<String>emptySet());

        ReadingPosition position = sink.restored.get(0);
        assertEquals("the newest row is the position nothing is left to point at",
                2, position.messageIndex);
        assertEquals(0, position.listItemOffset);
        assertTrue(position.atLiveEnd);
    }

    @Test
    public void leavingTheBranchStoresThePositionItWasGiven() {
        List<Message> rows = rows(3);
        data.snapshot = snapshot(rows, branch(0L, 0L));

        ChatPresenter presenter = presenter(new TestDoubles.InlineExecutor());
        presenter.start();
        presenter.onLeaving(ReadingAnchor.of(rows.get(1).id, null, 37, 12));

        assertEquals(ChatTestDoubles.BRANCH, reading.savedBranchId);
        assertEquals(rows.get(1).id, reading.savedAnchor.messageId);
        assertEquals(37, reading.savedAnchor.listItemOffset);
        assertEquals(12, reading.savedAnchor.viewportOffset);
    }

    @Test
    public void theNewestAnswerEnteringTheViewportClearsUnreadAndNeverMovesBackwards() {
        List<Message> rows = rows(2);
        Branch branch = branch(3L, 1L);
        data.snapshot = snapshot(rows, branch);

        ChatPresenter presenter = presenter(new TestDoubles.InlineExecutor());
        presenter.start();
        presenter.onViewportChanged(visible(rows.get(1).id));

        assertEquals(1, reading.savedSeen);
        assertEquals(3L, reading.lastSeenStored);

        // The newest answer is reported once more, now that the badge is already clear: there is
        // nothing left to write, and writing again would be the write amplification the design warns
        // about. This is the event that tells an "already caught up" branch from an unread one.
        presenter.onViewportChanged(visible(rows.get(1).id));
        assertEquals("an already-clear badge must not be written again", 1, reading.savedSeen);

        // The screen reports an older row afterwards - a stale viewport event. The seen sequence must
        // not follow it backwards, and there is nothing new to write.
        presenter.onViewportChanged(visible(rows.get(0).id));

        assertEquals("a stale viewport must not write again", 1, reading.savedSeen);
        assertEquals(3L, reading.lastSeenStored);
        assertEquals(3L, branch.lastSeenStableSequence());
        assertTrue("the seen sequence may only ever move forward",
                reading.seenHistory.get(0).longValue() <= reading.seenHistory.get(
                        reading.seenHistory.size() - 1).longValue());
    }

    @Test
    public void anOlderAnswerAloneLeavesUnreadUntouched() {
        List<Message> rows = rows(2);
        Branch branch = branch(2L, 0L);
        data.snapshot = snapshot(rows, branch);

        ChatPresenter presenter = presenter(new TestDoubles.InlineExecutor());
        presenter.start();
        presenter.onViewportChanged(visible(rows.get(0).id));

        assertEquals(0, reading.savedSeen);
        assertEquals(0L, branch.lastSeenStableSequence());
    }

    @Test
    public void aConversationThatFitsOnOneScreenClearsUnreadOnItsFirstFrame() {
        List<Message> rows = rows(2);
        Branch branch = branch(3L, 1L);
        data.snapshot = snapshot(rows, branch);

        ChatPresenter presenter = presenter(new TestDoubles.InlineExecutor());
        presenter.start();
        // One screen, no scroll: the only report this visit ever makes is the first frame's.
        presenter.onRowsLaidOut(90, visible(rows.get(0).id, rows.get(1).id));

        assertEquals("the newest answer was on screen, so the badge clears", 1, reading.savedSeen);
        assertEquals(3L, reading.lastSeenStored);
    }

    @Test
    public void aFirstFrameThatDoesNotShowTheNewestAnswerLeavesUnreadAlone() {
        List<Message> rows = rows(3);
        Branch branch = branch(3L, 1L);
        data.snapshot = snapshot(rows, branch);

        ChatPresenter presenter = presenter(new TestDoubles.InlineExecutor());
        presenter.start();
        presenter.onRowsLaidOut(90, visible(rows.get(0).id));

        assertEquals("opening a branch must not clear unread", 0, reading.savedSeen);
        assertEquals(1L, branch.lastSeenStableSequence());
    }

    @Test
    public void thePositionIsSavedOnTheBackgroundExecutorAndNotOnTheUiThread() {
        List<Message> rows = rows(2);
        data.snapshot = snapshot(rows, branch(0L, 0L));
        ChatTestDoubles.QueueingExecutor deferred = new ChatTestDoubles.QueueingExecutor();

        ChatPresenter presenter = presenter(deferred);
        presenter.start();
        deferred.drain();

        presenter.onLeaving(ReadingAnchor.of(rows.get(1).id, null, 5, 0));
        assertNull("the save must not run on the UI thread", reading.savedAnchor);

        deferred.drain();
        assertNotNull("the save runs on the injected background executor", reading.savedAnchor);
        assertEquals(5, reading.savedAnchor.listItemOffset);
    }

    @Test
    public void aRefusedStoredPositionBecomesANoticeInsteadOfKillingTheBackgroundThread() {
        List<Message> rows = rows(2);
        data.snapshot = snapshot(rows, branch(0L, 0L));

        ChatPresenter presenter = presenter(new TestDoubles.InlineExecutor());
        presenter.start();
        ChatViewState before = presenter.state();
        reading.failWrites = true;

        presenter.onLeaving(ReadingAnchor.of(rows.get(1).id, null, 5, 0));

        assertNotNull("a refused write must be told to the learner, not thrown at the process",
                presenter.state().notice);
        assertEquals("nothing the learner could already see may change",
                before.messages.size(), presenter.state().messages.size());
        assertEquals(before.phase, presenter.state().phase);
        assertEquals(before.branchId, presenter.state().branchId);
    }

    @Test
    public void aRefusedUnreadAdvanceBecomesANoticeAndWritesNothing() {
        List<Message> rows = rows(2);
        Branch branch = branch(3L, 1L);
        data.snapshot = snapshot(rows, branch);

        ChatPresenter presenter = presenter(new TestDoubles.InlineExecutor());
        presenter.start();
        reading.failWrites = true;

        presenter.onViewportChanged(visible(rows.get(1).id));

        assertNotNull("a refused unread write must be told to the learner",
                presenter.state().notice);
        assertEquals("a write that did not land must not be recorded as one", 0, reading.savedSeen);
        assertEquals(0L, reading.lastSeenStored);
    }

    private ChatPresenter presenter(Executor background) {
        return new ChatPresenter(sink, new TestDoubles.InlineUi(), background, key, data, commands,
                settings, LABELS, ChatTestDoubles.BRANCH, reading);
    }

    private static Set<String> visible(String... messageIds) {
        return new HashSet<String>(Arrays.asList(messageIds));
    }

    /** The stored answers, as the reading rules read them: AI positions carrying a version. */
    private static List<Message> rows(int count) {
        List<Message> rows = new ArrayList<Message>(count);
        for (int index = 0; index < count; index++) {
            rows.add(Message.restore(ANSWER_IDS[index], ChatTestDoubles.BRANCH, MessageRole.AI,
                    index, "answer " + index, NOW, REVISION, null));
        }
        return rows;
    }

    /** The same conversation as the screen draws it, one row per stored answer. */
    private static List<ChatMessage> viewOf(List<Message> rows) {
        List<ChatMessage> messages = new ArrayList<ChatMessage>(rows.size());
        for (Message row : rows) {
            messages.add(ChatMessage.ai(row.id, "answer", ChatTestDoubles.REASONING,
                    ChatMessage.Status.STABLE, ChatMessage.Phase.ANSWER, REVISION, 1, 0, false, NOW));
        }
        return messages;
    }

    private static ChatData.Snapshot snapshot(List<Message> rows, Branch branch) {
        return ChatData.Snapshot.of("Transformer attention", viewOf(rows),
                Collections.<Recommendation>emptyList(), "", branch, rows);
    }

    /** The branch row as it comes back from the store, with the two unread sequences as stored. */
    private static Branch branch(long latestStable, long lastSeenStable) {
        return Branch.restore(ChatTestDoubles.BRANCH, TOPIC, null, null, "Transformer attention", NOW,
                NOW, latestStable, lastSeenStable, false, null);
    }

    /** The reading port, answered from what a test set up, recording every write. */
    private static final class FakeReadingStore implements ReadingStore {

        ReadingAnchor anchor;
        String savedBranchId;
        ReadingAnchor savedAnchor;
        int savedSeen;
        long lastSeenStored;
        /** True when the store refuses every write, as the SQLite port does when the row is gone. */
        boolean failWrites;
        final List<Long> seenHistory = new ArrayList<Long>();

        @Override
        public ReadingAnchor anchorOf(String branchId) {
            return anchor;
        }

        @Override
        public void saveAnchor(String branchId, ReadingAnchor anchor) {
            if (failWrites) {
                throw new IllegalStateException("the branch row is not there to be written");
            }
            savedBranchId = branchId;
            savedAnchor = anchor;
        }

        @Override
        public void saveSeen(Branch branch) {
            if (failWrites) {
                throw new IllegalStateException("the branch row is not there to be written");
            }
            savedSeen++;
            lastSeenStored = branch.lastSeenStableSequence();
            seenHistory.add(Long.valueOf(lastSeenStored));
        }
    }
}
