package com.treelearn.app.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Collections;
import org.junit.Test;

/**
 * T-075: the scroll-follow policy (073 visual spec §6) - automatic following only while the
 * newest row is on screen, a detach the moment the learner scrolls it away, a count of what was
 * missed while detached, and two explicit ways back in: a new send and the follow-latest button.
 *
 * <p>The judgement rides the viewport facts the screen already reports; the presenter never
 * scrolls anything itself, it only decides what the state says.
 */
public final class ChatFollowPolicyTest {

    private static final CopyLabels LABELS = CopyLabels.of("思考过程", "最终回答");

    private final ChatTestDoubles.RecordingChatSink sink = new ChatTestDoubles.RecordingChatSink();
    private final ChatTestDoubles.FakeChatData data = new ChatTestDoubles.FakeChatData();
    private final ChatTestDoubles.FakeChatCommands commands = new ChatTestDoubles.FakeChatCommands();
    private final TestDoubles.FakeKeyVault key = new TestDoubles.FakeKeyVault(true);
    private final TestDoubles.FakeSettingsStore settings = new TestDoubles.FakeSettingsStore();

    private ChatPresenter presenter() {
        return new ChatPresenter(sink, new TestDoubles.InlineUi(), new TestDoubles.InlineExecutor(),
                key, data, commands, settings, LABELS, ChatTestDoubles.BRANCH,
                new ChatTestDoubles.NoPositionStore());
    }

    private static ChatMessage generating(String answer) {
        return ChatMessage.ai(ChatTestDoubles.M_ANSWER, answer, "", ChatMessage.Status.GENERATING,
                ChatMessage.Phase.ANSWER, "", 0, 0, true, 1_700_000_000_000L);
    }

    private ChatTestDoubles.FakeChatData dataWith(ChatMessage... rows) {
        java.util.List<ChatMessage> all = new java.util.ArrayList<ChatMessage>();
        all.add(ChatTestDoubles.ask(ChatTestDoubles.M_ASK, "why"));
        all.addAll(java.util.Arrays.asList(rows));
        data.snapshot = ChatTestDoubles.read("Transformer attention", all,
                Collections.<Recommendation>emptyList(), "");
        return data;
    }

    @Test
    public void theConversationStartsFollowing() {
        dataWith(ChatTestDoubles.stableAnswer(ChatTestDoubles.M_ANSWER, "because"));

        presenter().start();

        assertTrue(sink.last().followLatest);
        assertEquals(0, sink.last().missedCount);
    }

    @Test
    public void scrollingTheNewestRowAwayDetachesTheFollow() {
        dataWith(ChatTestDoubles.stableAnswer(ChatTestDoubles.M_ANSWER, "because"));
        ChatPresenter presenter = presenter();
        presenter.start();

        presenter.onViewportChanged(Collections.singleton(ChatTestDoubles.M_ASK));

        assertFalse("the learner scrolled up: the view belongs to them now",
                sink.last().followLatest);
    }

    @Test
    public void theNewestRowStillOnScreenKeepsTheFollow() {
        dataWith(ChatTestDoubles.stableAnswer(ChatTestDoubles.M_ANSWER, "because"));
        ChatPresenter presenter = presenter();
        presenter.start();

        presenter.onViewportChanged(new java.util.LinkedHashSet<>(
                java.util.Arrays.asList(ChatTestDoubles.M_ASK, ChatTestDoubles.M_ANSWER)));

        assertTrue(sink.last().followLatest);
        assertEquals(0, sink.last().missedCount);
    }

    @Test
    public void contentThatGrowsWhileDetachedIsCountedNotFollowed() {
        dataWith(generating("a"));
        ChatPresenter presenter = presenter();
        presenter.start();
        presenter.onViewportChanged(Collections.singleton(ChatTestDoubles.M_ASK));

        dataWith(generating("ab"));
        presenter.onResumed();
        dataWith(generating("abc"));
        presenter.onResumed();

        assertFalse(sink.last().followLatest);
        assertEquals("each growth the learner did not see is one missed entry",
                2, sink.last().missedCount);
    }

    @Test
    public void contentThatGrowsWhileFollowingIsNotCounted() {
        dataWith(generating("a"));
        ChatPresenter presenter = presenter();
        presenter.start();

        dataWith(generating("ab"));
        presenter.onResumed();

        assertTrue(sink.last().followLatest);
        assertEquals(0, sink.last().missedCount);
    }

    @Test
    public void catchingUpAgainResetsTheCountAndReattaches() {
        dataWith(generating("a"));
        ChatPresenter presenter = presenter();
        presenter.start();
        presenter.onViewportChanged(Collections.singleton(ChatTestDoubles.M_ASK));
        dataWith(generating("ab"));
        presenter.onResumed();
        assertEquals(1, sink.last().missedCount);

        presenter.onViewportChanged(new java.util.LinkedHashSet<>(
                java.util.Arrays.asList(ChatTestDoubles.M_ASK, ChatTestDoubles.M_ANSWER)));

        assertTrue(sink.last().followLatest);
        assertEquals(0, sink.last().missedCount);
    }

    @Test
    public void aNewSendReattachesTheFollow() {
        dataWith(generating("a"));
        ChatPresenter presenter = presenter();
        presenter.start();
        presenter.onViewportChanged(Collections.singleton(ChatTestDoubles.M_ASK));
        assertFalse(sink.last().followLatest);

        data.snapshot = ChatTestDoubles.snapshot(Collections.<ChatMessage>emptyList());
        presenter.onDraftChanged("and then");
        presenter.onSend();

        assertTrue("the learner asked a question: they want to see the answer",
                sink.last().followLatest);
        assertEquals(0, sink.last().missedCount);
    }

    @Test
    public void theFollowLatestButtonReattachesAndClearsTheCount() {
        dataWith(generating("a"));
        ChatPresenter presenter = presenter();
        presenter.start();
        presenter.onViewportChanged(Collections.singleton(ChatTestDoubles.M_ASK));
        dataWith(generating("ab"));
        presenter.onResumed();
        assertEquals(1, sink.last().missedCount);

        presenter.onFollowLatestPressed();

        assertTrue(sink.last().followLatest);
        assertEquals(0, sink.last().missedCount);
    }

    @Test
    public void anOverflowPauseDetachesWithoutDroppingTheMissedCount() {
        dataWith(generating("a"));
        ChatPresenter presenter = presenter();
        presenter.start();

        presenter.onFollowLatestPaused();

        assertFalse(sink.last().followLatest);
        assertEquals(0, sink.last().missedCount);
    }
}
