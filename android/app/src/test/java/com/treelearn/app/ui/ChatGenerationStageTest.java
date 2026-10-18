package com.treelearn.app.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Collections;
import org.junit.Test;

/**
 * T-075: the generation stage machine the presenter derives from the newest row (073 visual spec
 * §2): sending while nothing has streamed yet, reasoning while the attempt fills the reasoning
 * stream, answering while it fills the answer, and the three terminals - stable, canceled, failed.
 *
 * <p>Cancel and failure are different user semantics (task file), and the store reports both as one
 * unfinished row, so the presenter keeps the learner's stop request as the tie-breaker: a stop the
 * attempt obeyed reads canceled, the same terminal without one reads failed. The intent is consumed
 * by the terminal it explains, so it cannot leak onto the next attempt.
 *
 * <p>The send tests drive their reads by hand through a queueing executor. The generation poller is
 * a real thread, so a test cannot keep its reads out of the assertion window; instead every case
 * re-drains and asserts the stage did not move - the same rows must derive one stage sequence, not
 * one that depends on whether a background read happened to land.
 */
public final class ChatGenerationStageTest {

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

    private ChatPresenter deferredPresenter(ChatTestDoubles.QueueingExecutor deferred) {
        return new ChatPresenter(sink, new TestDoubles.InlineUi(), deferred, key, data, commands,
                settings, LABELS, ChatTestDoubles.BRANCH, new ChatTestDoubles.NoPositionStore());
    }

    private static ChatMessage failedAnswer(String id) {
        return ChatMessage.ai(id, "", "", ChatMessage.Status.FAILED, ChatMessage.Phase.ANSWER, "",
                0, 0, false, 1_700_000_000_000L);
    }

    /** Runs every read a background thread managed to queue, so a stage that moves cannot hide. */
    private void assertStageSurvivesMoreReads(ChatTestDoubles.QueueingExecutor deferred,
            ChatViewState.Stage expected, String what) {
        deferred.drain();
        deferred.drain();
        assertEquals(what, expected, sink.last().stage);
    }

    private static ChatData.Snapshot conversationWith(ChatMessage answer) {
        return ChatTestDoubles.read("Transformer attention",
                ChatTestDoubles.messages(ChatTestDoubles.ask(ChatTestDoubles.M_ASK, "why"),
                        answer),
                Collections.<Recommendation>emptyList(), "");
    }

    @Test
    public void theConversationStartsOnTheIdleStage() {
        data.snapshot = ChatTestDoubles.snapshot(Collections.<ChatMessage>emptyList());

        presenter().start();

        assertEquals(ChatViewState.Stage.IDLE, sink.last().stage);
    }

    @Test
    public void anAcceptedSendShowsTheSendingStageUntilTheFirstRowArrives() {
        data.snapshot = ChatTestDoubles.snapshot(Collections.<ChatMessage>emptyList());
        ChatTestDoubles.QueueingExecutor deferred = new ChatTestDoubles.QueueingExecutor();
        ChatPresenter presenter = deferredPresenter(deferred);

        presenter.onDraftChanged("why");
        presenter.onSend();
        deferred.drain();

        assertTrue(commands.called("send"));
        assertEquals(ChatViewState.Stage.SENDING, sink.last().stage);
        assertStageSurvivesMoreReads(deferred, ChatViewState.Stage.SENDING,
                "no read may turn the wait for the first row back into idle");
    }

    @Test
    public void aReasoningStreamShowsTheReasoningStage() {
        data.snapshot = conversationWith(ChatTestDoubles.formingReasoning(
                ChatTestDoubles.M_ANSWER, ChatTestDoubles.REASONING));

        presenter().onResumed();

        assertEquals(ChatViewState.Stage.REASONING, sink.last().stage);
    }

    @Test
    public void anAnswerStreamShowsTheAnsweringStage() {
        data.snapshot = conversationWith(ChatMessage.ai(ChatTestDoubles.M_ANSWER, "half an ans", "",
                ChatMessage.Status.GENERATING, ChatMessage.Phase.ANSWER, "", 0, 0, true,
                1_700_000_000_000L));

        presenter().onResumed();

        assertEquals(ChatViewState.Stage.ANSWERING, sink.last().stage);
    }

    @Test
    public void aStableAnswerShowsTheStableStage() {
        data.snapshot = conversationWith(
                ChatTestDoubles.stableAnswer(ChatTestDoubles.M_ANSWER, "because"));

        presenter().start();

        assertEquals(ChatViewState.Stage.STABLE, sink.last().stage);
    }

    @Test
    public void anUnfinishedTerminalWithoutAStopRequestShowsTheFailedStage() {
        data.snapshot = conversationWith(failedAnswer(ChatTestDoubles.M_ANSWER));

        presenter().onResumed();

        assertEquals(ChatViewState.Stage.FAILED, sink.last().stage);
    }

    @Test
    public void aStopRequestTurnsTheTerminalReadIntoTheCanceledStage() {
        data.snapshot = ChatTestDoubles.snapshot(Collections.<ChatMessage>emptyList());
        ChatTestDoubles.QueueingExecutor deferred = new ChatTestDoubles.QueueingExecutor();
        ChatPresenter presenter = deferredPresenter(deferred);
        presenter.onDraftChanged("why");
        presenter.onSend();
        deferred.drain();
        presenter.onStopRequested();

        data.snapshot = conversationWith(failedAnswer(ChatTestDoubles.M_ANSWER));
        presenter.onResumed();
        deferred.drain();

        assertEquals("the stop the attempt obeyed reads canceled, not failed",
                ChatViewState.Stage.CANCELED, sink.last().stage);
        assertStageSurvivesMoreReads(deferred, ChatViewState.Stage.CANCELED,
                "a read that only repeated the same rows must not relabel the stop as a failure");
    }

    /**
     * D8: one batch of rows derives one stage sequence. The generation poller re-reads on its own
     * thread, so a stage that depends on how many reads landed is not a fact about the conversation.
     */
    @Test
    public void theTerminalRowAStopExplainedKeepsReadingCanceledOnEveryLaterRead() {
        data.snapshot = ChatTestDoubles.snapshot(Collections.<ChatMessage>emptyList());
        ChatTestDoubles.QueueingExecutor deferred = new ChatTestDoubles.QueueingExecutor();
        ChatPresenter presenter = deferredPresenter(deferred);
        presenter.onDraftChanged("why");
        presenter.onSend();
        deferred.drain();
        presenter.onStopRequested();
        data.snapshot = conversationWith(failedAnswer(ChatTestDoubles.M_ANSWER));

        presenter.onResumed();
        deferred.drain();
        presenter.onResumed();
        presenter.onResumed();
        deferred.drain();

        assertEquals(ChatViewState.Stage.CANCELED, sink.last().stage);
    }

    /**
     * Retry and reset regenerate in place, so one row can fail twice for two different reasons. The
     * stop that explained the first failure must not speak for the second.
     */
    @Test
    public void aRetryOfTheCanceledRowThatFailsAgainReadsFailedNotCanceled() {
        data.snapshot = ChatTestDoubles.snapshot(Collections.<ChatMessage>emptyList());
        ChatTestDoubles.QueueingExecutor deferred = new ChatTestDoubles.QueueingExecutor();
        ChatPresenter presenter = deferredPresenter(deferred);
        presenter.onDraftChanged("why");
        presenter.onSend();
        deferred.drain();
        presenter.onStopRequested();
        data.snapshot = conversationWith(failedAnswer(ChatTestDoubles.M_ANSWER));
        presenter.onResumed();
        deferred.drain();
        assertEquals(ChatViewState.Stage.CANCELED, sink.last().stage);

        data.snapshot = conversationWith(ChatMessage.ai(ChatTestDoubles.M_ANSWER, "", "",
                ChatMessage.Status.GENERATING, ChatMessage.Phase.ANSWER, "", 0, 0, true,
                1_700_000_000_000L));
        presenter.onResumed();
        deferred.drain();
        assertEquals(ChatViewState.Stage.ANSWERING, sink.last().stage);

        data.snapshot = conversationWith(failedAnswer(ChatTestDoubles.M_ANSWER));
        presenter.onResumed();
        deferred.drain();

        assertEquals("the same row failing again is a new attempt, not the old stop",
                ChatViewState.Stage.FAILED, sink.last().stage);
    }

    @Test
    public void theStopIntentIsConsumedByTheTerminalItExplains() {
        data.snapshot = ChatTestDoubles.snapshot(Collections.<ChatMessage>emptyList());
        ChatTestDoubles.QueueingExecutor deferred = new ChatTestDoubles.QueueingExecutor();
        ChatPresenter presenter = deferredPresenter(deferred);
        presenter.onDraftChanged("why");
        presenter.onSend();
        deferred.drain();
        presenter.onStopRequested();
        data.snapshot = conversationWith(failedAnswer(ChatTestDoubles.M_ANSWER));
        presenter.onResumed();
        deferred.drain();
        assertEquals(ChatViewState.Stage.CANCELED, sink.last().stage);

        data.snapshot = conversationWith(failedAnswer(ChatTestDoubles.M_ANSWER_2));
        presenter.onResumed();
        deferred.drain();

        assertEquals("a stale stop intent must not label the next failure as canceled",
                ChatViewState.Stage.FAILED, sink.last().stage);
    }

    @Test
    public void aStopRequestThatLosesTheRaceKeepsTheStableStage() {
        data.snapshot = ChatTestDoubles.snapshot(Collections.<ChatMessage>emptyList());
        ChatTestDoubles.QueueingExecutor deferred = new ChatTestDoubles.QueueingExecutor();
        ChatPresenter presenter = deferredPresenter(deferred);
        presenter.onDraftChanged("why");
        presenter.onSend();
        deferred.drain();
        presenter.onStopRequested();

        data.snapshot = conversationWith(
                ChatTestDoubles.stableAnswer(ChatTestDoubles.M_ANSWER, "because"));
        presenter.onResumed();
        deferred.drain();

        assertEquals("[DONE] landed before the stop mattered: the answer is stable",
                ChatViewState.Stage.STABLE, sink.last().stage);
    }
}
