package com.treelearn.app.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.treelearn.core.domain.AnswerRevision;
import com.treelearn.core.domain.GenerationModel;

import java.util.Arrays;
import java.util.Collections;

import org.junit.Test;

/**
 * The chat presenter's decisions. Every assertion here is a requirement from the task file or the
 * prototype, not a rendering detail:
 *
 * <ul>
 *   <li><strong>The reasoning is a disclosure, not a paragraph.</strong> Collapsed by default, keyed
 *       by message id, and absent when the answer carries none.
 *   <li><strong>Copy carries the whole answer.</strong> The clipboard text is composed in one place
 *       and handed straight to the sink; nothing here logs it or keeps a second copy.
 *   <li><strong>Retry and reset are the position use cases.</strong> The presenter chooses which one
 *       a message permits and forwards, so no code outside T-010 can write a revision.
 *   <li><strong>The store is the only source of messages.</strong> A send never appends a row in the
 *       view state, so a reload cannot duplicate what the learner just asked.
 *   <li><strong>A failure keeps the draft.</strong> Only an accepted send clears it, the same rule the
 *       home composer follows.
 *   <li><strong>The strip belongs to the latest stable answer.</strong> A follow-up hides it at once.
 * </ul>
 */
public final class ChatPresenterTest {

    private static final CopyLabels LABELS = CopyLabels.of("思考过程", "最终回答");

    private final ChatTestDoubles.RecordingChatSink sink = new ChatTestDoubles.RecordingChatSink();
    private final ChatTestDoubles.FakeChatData data = new ChatTestDoubles.FakeChatData();
    private final ChatTestDoubles.FakeChatCommands commands = new ChatTestDoubles.FakeChatCommands();
    private final TestDoubles.FakeKeyVault key = new TestDoubles.FakeKeyVault(true);
    private final TestDoubles.FakeSettingsStore settings = new TestDoubles.FakeSettingsStore();

    @Test
    public void startRendersTheLoadingPhaseAndThenWhatTheStoreHolds() {
        data.snapshot = conversation();

        ChatPresenter presenter = presenter();
        presenter.start();

        assertEquals(ChatViewState.Phase.LOADING, sink.states.get(0).phase);
        assertEquals(ChatViewState.Phase.READY, sink.last().phase);
        assertEquals("Transformer attention", sink.last().branchTitle);
        assertEquals(2, sink.last().messages.size());
        assertEquals("the store must be read once per load", 1, data.reads);
        assertEquals(ChatTestDoubles.BRANCH, data.branchesRead.get(0));
    }

    @Test
    public void aReadThatFailsShowsTheFailureWithoutLosingTheDraft() {
        data.throwsInstead = true;

        ChatPresenter presenter = presenter();
        presenter.onDraftChanged("a half typed question");
        presenter.start();

        assertEquals(ChatViewState.Phase.ERROR, sink.last().phase);
        assertEquals("a half typed question", sink.last().draft);
    }

    @Test
    public void aReadThatArrivesAfterTheLearnerStartedTypingKeepsTheDraft() {
        data.snapshot = conversation();
        ChatTestDoubles.QueueingExecutor deferred = new ChatTestDoubles.QueueingExecutor();

        ChatPresenter presenter = new ChatPresenter(sink, new TestDoubles.InlineUi(), deferred, key,
                data, commands, settings, LABELS, ChatTestDoubles.BRANCH,
                new ChatTestDoubles.NoPositionStore());
        presenter.start();
        assertEquals("the store must be read off the UI thread", 0, data.reads);

        presenter.onDraftChanged("typed while the read was in flight");
        deferred.drain();

        assertEquals(2, sink.last().messages.size());
        assertEquals("typed while the read was in flight", sink.last().draft);
    }

    @Test
    public void theStripIsAttachedToTheLatestStableAnswer() {
        data.snapshot = withStrip(recommendation(), ChatTestDoubles.REV_1);

        ChatPresenter presenter = presenter();
        presenter.start();

        assertTrue(sink.last().recommendationsVisible());
        assertEquals(ChatTestDoubles.M_ANSWER, sink.last().recommendationsMessageId);
    }

    @Test
    public void aFollowUpHidesTheStripOfTheAnswerItFollowed() {
        data.snapshot = withStrip(recommendation(), ChatTestDoubles.REV_1);

        ChatPresenter presenter = presenter();
        presenter.start();
        assertTrue(sink.last().recommendationsVisible());

        data.snapshot = ChatTestDoubles.read("Transformer attention",
                ChatTestDoubles.messages(ChatTestDoubles.ask(ChatTestDoubles.M_ASK, "why"),
                        ChatTestDoubles.stableAnswer(ChatTestDoubles.M_ANSWER, "because"),
                        ChatTestDoubles.ask(ChatTestDoubles.M_ASK_2, "and then")),
                Collections.singletonList(recommendation()), ChatTestDoubles.REV_1);
        presenter.onResumed();

        assertFalse(sink.last().recommendationsVisible());
    }

    @Test
    public void theStripIsHiddenWhenItDescribesAnotherVersion() {
        data.snapshot = withStrip(recommendation(), ChatTestDoubles.REV_2);

        ChatPresenter presenter = presenter();
        presenter.start();

        assertFalse(sink.last().recommendationsVisible());
    }

    @Test
    public void theStripIsHiddenWhileTheLatestAnswerIsNotStable() {
        data.snapshot = ChatTestDoubles.read("title",
                ChatTestDoubles.messages(ChatTestDoubles.ask(ChatTestDoubles.M_ASK, "why"),
                        ChatMessage.ai(ChatTestDoubles.M_ANSWER, "half", "half a thought",
                                ChatMessage.Status.GENERATING, ChatMessage.Phase.ANSWER, "", 0, 0,
                                false, 1L)),
                Collections.singletonList(recommendation()), "");

        ChatPresenter presenter = presenter();
        presenter.start();

        assertFalse(sink.last().recommendationsVisible());
    }

    @Test
    public void reasoningTogglesPerMessageAndIgnoresWhatItCannotExpand() {
        data.snapshot = conversation();

        ChatPresenter presenter = presenter();
        presenter.start();

        assertFalse(sink.last().isReasoningExpanded(ChatTestDoubles.M_ANSWER));
        presenter.onReasoningToggled(ChatTestDoubles.M_ANSWER);
        assertTrue(sink.last().isReasoningExpanded(ChatTestDoubles.M_ANSWER));

        presenter.onReasoningToggled(ChatTestDoubles.M_ASK);
        presenter.onReasoningToggled("99999999-9999-4999-8999-999999999999");
        assertTrue(sink.last().isReasoningExpanded(ChatTestDoubles.M_ANSWER));

        presenter.onReasoningToggled(ChatTestDoubles.M_ANSWER);
        assertFalse(sink.last().isReasoningExpanded(ChatTestDoubles.M_ANSWER));
    }

    @Test
    public void togglingAFormingAnswerFoldsItAgainstTheStreamingDefault() {
        data.snapshot = ChatTestDoubles.read("Transformer attention",
                ChatTestDoubles.messages(ChatTestDoubles.ask(ChatTestDoubles.M_ASK, "why"),
                        ChatTestDoubles.formingReasoning(ChatTestDoubles.M_ANSWER,
                                ChatTestDoubles.REASONING)),
                Collections.<Recommendation>emptyList(), "");

        ChatPresenter presenter = presenter();
        presenter.start();

        assertTrue("streaming reasoning is open before any tap",
                sink.last().isReasoningEffectivelyExpanded(ChatTestDoubles.M_ANSWER));

        presenter.onReasoningToggled(ChatTestDoubles.M_ANSWER);
        assertFalse("the fold is an explicit intent",
                sink.last().isReasoningEffectivelyExpanded(ChatTestDoubles.M_ANSWER));

        presenter.onReasoningToggled(ChatTestDoubles.M_ANSWER);
        assertTrue(sink.last().isReasoningEffectivelyExpanded(ChatTestDoubles.M_ANSWER));
    }

    @Test
    public void copyHandsTheWholeAnswerToTheSinkAndNothingElse() {
        data.snapshot = conversation();

        ChatPresenter presenter = presenter();
        presenter.start();
        presenter.onCopyPressed(ChatTestDoubles.M_ANSWER);

        assertEquals(1, sink.copied.size());
        assertTrue(sink.copied.get(0).startsWith("思考过程\n" + ChatTestDoubles.REASONING));
        assertTrue(sink.copied.get(0).endsWith("最终回答\n" + ChatTestDoubles.ANSWER));
        assertTrue("copying must not write anything", commands.calls.isEmpty());
    }

    @Test
    public void copyIsNotOfferedForAPartialAnswer() {
        data.snapshot = ChatTestDoubles.read("title",
                ChatTestDoubles.messages(ChatTestDoubles.ask(ChatTestDoubles.M_ASK, "why"),
                        ChatMessage.ai(ChatTestDoubles.M_ANSWER, "half", "half a thought",
                                ChatMessage.Status.FAILED, ChatMessage.Phase.ANSWER, "", 0, 0, false, 1L)),
                Collections.<Recommendation>emptyList(), "");

        ChatPresenter presenter = presenter();
        presenter.start();
        presenter.onCopyPressed(ChatTestDoubles.M_ANSWER);

        assertTrue(sink.copied.isEmpty());
    }

    @Test
    public void aMissingKeyOpensSettingsInsteadOfSendingAndKeepsTheDraft() {
        key.configured = false;
        data.snapshot = conversation();

        ChatPresenter presenter = presenter();
        presenter.start();
        presenter.onDraftChanged("why is it scaled");
        presenter.onSend();

        assertEquals(1, sink.settingsOpened);
        assertEquals(HomeViewState.Notice.KEY_MISSING, sink.last().notice);
        assertEquals("why is it scaled", sink.last().draft);
        assertTrue(commands.calls.isEmpty());
    }

    @Test
    public void theOverviewEntryNavigatesToTheCanvasAndLeavesTheBranchAlone() {
        data.snapshot = conversation();

        ChatPresenter presenter = presenter();
        presenter.start();
        presenter.onOverviewPressed();

        assertEquals(Arrays.asList(ChatTestDoubles.BRANCH), sink.overviewsOpened);
        // Looking at the tree around a branch is not moving to another one: nothing was opened, and the
        // conversation still stands where it did.
        assertTrue(sink.branchesOpened.isEmpty());
        assertEquals(ChatTestDoubles.BRANCH, presenter.state().branchId);
    }

    @Test
    public void aBlankDraftIsNeverSent() {
        data.snapshot = conversation();

        ChatPresenter presenter = presenter();
        presenter.start();
        presenter.onDraftChanged("   ");
        presenter.onSend();

        assertTrue(commands.calls.isEmpty());
    }

    @Test
    public void aRefusedSendKeepsTheDraftAndReportsTheReason() {
        data.snapshot = conversation();
        commands.sendResult = ChatCommandResult.refused(HomeViewState.Notice.NETWORK_INTERRUPTED);

        ChatPresenter presenter = presenter();
        presenter.start();
        presenter.onDraftChanged("why is it scaled");
        presenter.onSend();

        assertEquals(Collections.singletonList("send"), commands.calls);
        assertEquals(HomeViewState.Notice.NETWORK_INTERRUPTED, sink.last().notice);
        assertEquals("why is it scaled", sink.last().draft);
        assertFalse(sink.last().sending);
        assertTrue(sink.branchesOpened.isEmpty());
    }

    @Test
    public void anAcceptedSendClearsTheDraftAndLetsTheStoreAnswerWithTheMessages() {
        data.snapshot = conversation();
        commands.sendResult = ChatCommandResult.ran(ChatTestDoubles.OP);

        ChatPresenter presenter = presenter();
        presenter.start();
        presenter.onDraftChanged("why is it scaled");
        presenter.onSend();

        assertEquals("", sink.last().draft);
        assertNull(sink.last().notice);
        assertFalse(sink.last().sending);
        assertEquals("the message must come from the store, not from the view state", 2,
                sink.last().messages.size());
        assertEquals("the read that follows an accepted send is the second one", 2, data.reads);
    }

    @Test
    public void aSendInFlightIsNotStartedTwice() {
        data.snapshot = conversation();
        ChatTestDoubles.QueueingExecutor deferred = new ChatTestDoubles.QueueingExecutor();

        ChatPresenter presenter = new ChatPresenter(sink, new TestDoubles.InlineUi(), deferred, key,
                data, commands, settings, LABELS, ChatTestDoubles.BRANCH,
                new ChatTestDoubles.NoPositionStore());
        presenter.start();
        deferred.drain();
        presenter.onDraftChanged("why is it scaled");
        presenter.onSend();
        presenter.onSend();
        deferred.drain();

        assertEquals(Collections.singletonList("send"), commands.calls);
    }

    @Test
    public void theQuoteIsCarriedIntoTheSendAndClearedOnceTheSendIsAccepted() {
        data.snapshot = conversation();
        commands.sendResult = ChatCommandResult.ran(ChatTestDoubles.OP);

        ChatPresenter presenter = presenter();
        presenter.start();
        presenter.onQuotePressed(ChatTestDoubles.M_ANSWER, "  the dot products grow  ");
        assertEquals("the dot products grow", sink.last().quote);

        presenter.onDraftChanged("why is it scaled");
        presenter.onSend();
        assertEquals("the dot products grow", commands.lastQuote);
        assertEquals("", sink.last().quote);
    }

    @Test
    public void aQuoteIsNotTakenFromAPartialAnswer() {
        data.snapshot = ChatTestDoubles.read("title",
                ChatTestDoubles.messages(ChatTestDoubles.ask(ChatTestDoubles.M_ASK, "why"),
                        ChatMessage.ai(ChatTestDoubles.M_ANSWER, "half", "half a thought",
                                ChatMessage.Status.FAILED, ChatMessage.Phase.ANSWER, "", 0, 0, false, 1L)),
                Collections.<Recommendation>emptyList(), "");

        ChatPresenter presenter = presenter();
        presenter.start();
        presenter.onQuotePressed(ChatTestDoubles.M_ANSWER, "half");

        assertEquals("", sink.last().quote);
    }

    @Test
    public void retryIsForwardedToThePositionUseCaseWithTheCurrentComposerSettings() {
        data.snapshot = failedConversation();
        settings.model = GenerationModel.PRO;
        settings.thinking = true;

        ChatPresenter presenter = presenter();
        presenter.start();
        presenter.onRetryPressed(ChatTestDoubles.M_ANSWER);

        assertEquals(Collections.singletonList("retry"), commands.calls);
        assertEquals(ChatTestDoubles.M_ANSWER, commands.lastMessageId);
        assertEquals(GenerationModel.PRO, commands.lastModel);
        assertTrue(commands.lastThinking);
    }

    @Test
    public void aRefusedRetryIsReportedAndChangesNothingElse() {
        data.snapshot = failedConversation();
        commands.retryResult = ChatCommandResult.refused(HomeViewState.Notice.STORAGE);

        ChatPresenter presenter = presenter();
        presenter.start();
        presenter.onRetryPressed(ChatTestDoubles.M_ANSWER);

        assertEquals(HomeViewState.Notice.STORAGE, sink.last().notice);
        assertTrue(sink.last().messages.get(1).isFailed());
    }

    @Test
    public void resetIsForwardedOnlyForAnAnswerThatDisplaysAVersion() {
        data.snapshot = conversation();

        ChatPresenter presenter = presenter();
        presenter.start();
        presenter.onResetPressed(ChatTestDoubles.M_ANSWER);

        assertEquals(Collections.singletonList("reset"), commands.calls);
        assertEquals(ChatTestDoubles.M_ANSWER, commands.lastMessageId);
    }

    @Test
    public void resetIsNotForwardedForAnAnswerWithNoVersion() {
        data.snapshot = failedConversation();

        ChatPresenter presenter = presenter();
        presenter.start();
        presenter.onResetPressed(ChatTestDoubles.M_ANSWER);

        assertTrue(commands.calls.isEmpty());
    }

    @Test
    public void forkingOpensTheBranchTheStoreCreated() {
        data.snapshot = conversation();

        ChatPresenter presenter = presenter();
        presenter.start();
        presenter.onForkPressed(ChatTestDoubles.M_ANSWER, "", 0, 0);

        assertEquals(Collections.singletonList("fork"), commands.calls);
        assertEquals(ChatTestDoubles.M_ANSWER, commands.lastMessageId);
        assertEquals("", commands.lastQuestion);
        assertEquals("the bottom action bar carries no sentence", "", commands.lastQuote);
        assertEquals(0, commands.lastForkStart);
        assertEquals(0, commands.lastForkEnd);
        assertEquals(Collections.singletonList(ChatTestDoubles.BRANCH_2), sink.branchesOpened);
    }

    @Test
    public void forkingIsNotOfferedForAPartialAnswer() {
        data.snapshot = failedConversation();

        ChatPresenter presenter = presenter();
        presenter.start();
        presenter.onForkPressed(ChatTestDoubles.M_ANSWER, "", 0, 0);

        assertTrue(commands.calls.isEmpty());
        assertTrue(sink.branchesOpened.isEmpty());
    }

    @Test
    public void theChildrenEntryOpensOnlyWhenTheAnswerHasChildren() {
        data.snapshot = ChatTestDoubles.read("title",
                ChatTestDoubles.messages(ChatTestDoubles.ask(ChatTestDoubles.M_ASK, "why"),
                        ChatMessage.ai(ChatTestDoubles.M_ANSWER, "because", "a thought",
                                ChatMessage.Status.STABLE, ChatMessage.Phase.ANSWER,
                                ChatTestDoubles.REV_1, 1, 2, false, 1L)),
                Collections.<Recommendation>emptyList(), "");

        ChatPresenter presenter = presenter();
        presenter.start();
        presenter.onChildrenPressed(ChatTestDoubles.M_ANSWER);

        // T-041 D2: the sink no longer takes the branchId (no consumer), so the recording is by message.
        assertEquals(Collections.singletonList(ChatTestDoubles.M_ANSWER),
                sink.childrenOpened);
    }

    @Test
    public void theChildrenEntryDoesNothingWithoutChildren() {
        data.snapshot = conversation();

        ChatPresenter presenter = presenter();
        presenter.start();
        presenter.onChildrenPressed(ChatTestDoubles.M_ANSWER);

        assertTrue(sink.childrenOpened.isEmpty());
    }

    @Test
    public void theChildrenEntryHandsOverTheBranchesThePopupMayList() {
        data.snapshot = ChatTestDoubles.read("title",
                ChatTestDoubles.messages(ChatTestDoubles.ask(ChatTestDoubles.M_ASK, "why"),
                        ChatMessage.ai(ChatTestDoubles.M_ANSWER, "because", "a thought",
                                ChatMessage.Status.STABLE, ChatMessage.Phase.ANSWER,
                                ChatTestDoubles.REV_1, 1, 1, false, 1L)),
                Collections.<Recommendation>emptyList(), "");
        data.children = Collections.singletonList(new ChildBranch(ChatTestDoubles.CHILD_BRANCH,
                "从这条回答延伸", 3L));

        ChatPresenter presenter = presenter();
        presenter.start();
        presenter.onChildrenPressed(ChatTestDoubles.M_ANSWER);

        // T-041 D2: the sink no longer takes the branchId (no consumer), so the recording is by message.
        assertEquals(Collections.singletonList(ChatTestDoubles.M_ANSWER),
                sink.childrenOpened);
        assertEquals(1, sink.childrenListed.size());
        assertEquals(ChatTestDoubles.CHILD_BRANCH, sink.childrenListed.get(0).get(0).branchId);
        assertEquals("从这条回答延伸", sink.childrenListed.get(0).get(0).title);
        assertEquals(3L, sink.childrenListed.get(0).get(0).unreadCount);
    }

    @Test
    public void copyingASelectedPassageHandsThatPassageToTheClipboard() {
        data.snapshot = conversation();

        ChatPresenter presenter = presenter();
        presenter.start();
        presenter.onSelectionCopyPressed("  the dot products grow with d  ");

        assertEquals(Collections.singletonList("the dot products grow with d"), sink.copied);
    }

    @Test
    public void aBlankSelectionIsNotCopiedAnywhere() {
        data.snapshot = conversation();

        ChatPresenter presenter = presenter();
        presenter.start();
        presenter.onSelectionCopyPressed("   ");
        presenter.onSelectionCopyPressed(null);

        assertTrue(sink.copied.isEmpty());
    }

    @Test
    public void theVersionMenuIsFilledFromTheStoreAndASwitchGoesThroughTheUseCase() {
        data.snapshot = withVersions();
        commands.versions = Collections.singletonList(AnswerRevision.restore(
                "88888888-8888-4888-8888-888888888888", ChatTestDoubles.M_ANSWER, 1, "a thought",
                "an answer", GenerationModel.FLASH, false, "v1", 1L));

        ChatPresenter presenter = presenter();
        presenter.start();
        presenter.onVersionPressed(ChatTestDoubles.M_ANSWER);

        assertEquals(Collections.singletonList(ChatTestDoubles.M_ANSWER + ":1"), sink.versionMenus);

        presenter.onRevisionSelected(ChatTestDoubles.M_ANSWER, ChatTestDoubles.REV_2);

        assertEquals(Arrays.asList("versions", "selectRevision"), commands.calls);
        assertEquals(ChatTestDoubles.REV_2, commands.lastRevisionId);
    }

    @Test
    public void selectingTheVersionAlreadyDisplayedIsNotForwarded() {
        data.snapshot = withVersions();

        ChatPresenter presenter = presenter();
        presenter.start();
        presenter.onRevisionSelected(ChatTestDoubles.M_ANSWER, ChatTestDoubles.REV_1);

        assertTrue(commands.calls.isEmpty());
    }

    @Test
    public void aRefusedVersionSwitchIsReported() {
        data.snapshot = withVersions();
        commands.selectResult = ChatCommandResult.refused(HomeViewState.Notice.STORAGE);

        ChatPresenter presenter = presenter();
        presenter.start();
        presenter.onRevisionSelected(ChatTestDoubles.M_ANSWER, ChatTestDoubles.REV_2);

        assertEquals(HomeViewState.Notice.STORAGE, sink.last().notice);
    }

    @Test
    public void aTappedRecommendationIsASubmissionNotASecondPath() {
        data.snapshot = withStrip(recommendation(), ChatTestDoubles.REV_1);
        commands.sendResult = ChatCommandResult.ran(ChatTestDoubles.OP);

        ChatPresenter presenter = presenter();
        presenter.start();
        presenter.onRecommendationFollowed("how does temperature affect it");

        assertEquals(Collections.singletonList("send"), commands.calls);
        assertEquals("how does temperature affect it", commands.lastText);
        assertEquals("", sink.last().draft);
    }

    @Test
    public void theForkHotZoneOnARecommendationIsIndependentOfTheFollowUp() {
        data.snapshot = withStrip(recommendation(), ChatTestDoubles.REV_1);

        ChatPresenter presenter = presenter();
        presenter.start();
        presenter.onRecommendationForked("how does temperature affect it");

        assertEquals(Collections.singletonList("fork"), commands.calls);
        assertEquals(ChatTestDoubles.M_ANSWER, commands.lastMessageId);
        assertEquals("how does temperature affect it", commands.lastQuestion);
        assertEquals("the suggestion fork anchors no sentence", "", commands.lastQuote);
        assertEquals(0, commands.lastForkStart);
        assertEquals(0, commands.lastForkEnd);
        assertEquals("the draft is not touched by a fork", "", sink.last().draft);
    }

    @Test
    public void aRecommendationGestureDoesNothingOnceTheStripIsHidden() {
        data.snapshot = conversation();

        ChatPresenter presenter = presenter();
        presenter.start();
        presenter.onRecommendationFollowed("anything");
        presenter.onRecommendationForked("anything");

        assertTrue(commands.calls.isEmpty());
        assertTrue(sink.branchesOpened.isEmpty());
    }

    @Test
    public void modelAndThinkingChangesArePersistedAndThenRendered() {
        data.snapshot = conversation();

        ChatPresenter presenter = presenter();
        presenter.start();
        presenter.onModelSelected(GenerationModel.PRO);
        presenter.onThinkingToggled(true);

        // T-103: the chip's model choice is a session override and never writes through — the
        // durable chat-role choice belongs to the settings screen. The thinking switch stays a
        // global preference, exactly as before.
        assertEquals(GenerationModel.FLASH, settings.model);
        assertTrue(settings.thinking);
        assertEquals(GenerationModel.PRO, sink.last().model);
        assertTrue(sink.last().thinkingEnabled);
        assertTrue("switching the model must not send anything", commands.calls.isEmpty());
    }

    @Test
    public void theComposerSettingsAreReReadWhenTheScreenComesBack() {
        data.snapshot = conversation();

        ChatPresenter presenter = presenter();
        presenter.start();
        settings.model = GenerationModel.PRO;
        settings.thinking = true;
        key.configured = false;
        presenter.onResumed();

        assertEquals(GenerationModel.PRO, sink.last().model);
        assertTrue(sink.last().thinkingEnabled);
        assertFalse(sink.last().keyConfigured);
    }

    @Test
    public void backClosesTheScreen() {
        ChatPresenter presenter = presenter();
        presenter.onBackPressed();

        assertEquals(1, sink.closed);
    }

    /**
     * T-065: entering a child branch whose provenance anchors a sentence prefills that sentence as
     * the quote, once - clearing it afterwards survives the reads that follow.
     */
    @Test
    public void childBranchPrefillsQuoteFromAnchor() {
        data.snapshot = ChatTestDoubles.withBranch(
                ChatTestDoubles.messages(ChatTestDoubles.stableAnswer(ChatTestDoubles.M_ANSWER,
                        "because")),
                ChatTestDoubles.childBranch("the anchored sentence"), null);

        ChatPresenter presenter = presenter();
        presenter.start();

        assertEquals("the anchored sentence", sink.last().quote);

        presenter.onQuoteCleared();
        presenter.onResumed();

        assertEquals("a cleared quote stays cleared on later reads", "", sink.last().quote);
    }

    /** T-065: a whole-answer provenance has no sentence to carry, so nothing is prefilled. */
    @Test
    public void aBranchWithoutARangedAnchorPrefillsNothing() {
        data.snapshot = ChatTestDoubles.withBranch(
                ChatTestDoubles.messages(ChatTestDoubles.stableAnswer(ChatTestDoubles.M_ANSWER,
                        "because")),
                ChatTestDoubles.childBranch(null), null);

        ChatPresenter presenter = presenter();
        presenter.start();

        assertEquals("", sink.last().quote);
    }

    /** T-065: the source card rides the read, starts collapsed, and expands on the learner's tap. */
    @Test
    public void sourceCardArrivesWithTheReadAndExpandsOnToggle() {
        data.snapshot = ChatTestDoubles.withBranch(
                ChatTestDoubles.messages(ChatTestDoubles.stableAnswer(ChatTestDoubles.M_ANSWER,
                        "because")),
                ChatTestDoubles.childBranch(null),
                new SourceCard("the source answer",
                        Arrays.asList(SourceCard.Entry.user("first question"),
                                SourceCard.Entry.answer("the source answer"))));

        ChatPresenter presenter = presenter();
        presenter.start();

        assertNotNull(sink.last().sourceCard);
        assertFalse("the card starts collapsed", sink.last().sourceCardExpanded);
        assertEquals(2, sink.last().sourceCard.history.size());
        assertTrue(sink.last().sourceCard.history.get(0).user);
        assertEquals("first question", sink.last().sourceCard.history.get(0).text);
        assertFalse(sink.last().sourceCard.history.get(1).user);
        assertEquals("the source answer", sink.last().sourceCard.history.get(1).text);

        presenter.onSourceCardToggled();

        assertTrue(sink.last().sourceCardExpanded);
    }

    /** T-065: the selected sentence and its offsets travel with the fork to the store. */
    @Test
    public void forkingWithASelectionForwardsTheSentenceToTheStore() {
        data.snapshot = conversation();
        ChatPresenter presenter = presenter();
        presenter.start();

        presenter.onForkPressed(ChatTestDoubles.M_ANSWER, ChatTestDoubles.ANSWER, 0,
                ChatTestDoubles.ANSWER.length());

        assertEquals(Collections.singletonList("fork"), commands.calls);
        assertEquals(ChatTestDoubles.M_ANSWER, commands.lastMessageId);
        assertEquals("a fork with a selection carries no question", "", commands.lastQuestion);
        assertEquals(ChatTestDoubles.ANSWER, commands.lastQuote);
        assertEquals(0, commands.lastForkStart);
        assertEquals(ChatTestDoubles.ANSWER.length(), commands.lastForkEnd);
    }

    private ChatPresenter presenter() {
        return new ChatPresenter(sink, new TestDoubles.InlineUi(), new TestDoubles.InlineExecutor(),
                key, data, commands, settings, LABELS, ChatTestDoubles.BRANCH,
                new ChatTestDoubles.NoPositionStore());
    }

    private static ChatData.Snapshot conversation() {
        return ChatTestDoubles.read("Transformer attention",
                ChatTestDoubles.messages(ChatTestDoubles.ask(ChatTestDoubles.M_ASK, ChatTestDoubles.QUESTION),
                        ChatTestDoubles.stableAnswer(ChatTestDoubles.M_ANSWER, ChatTestDoubles.ANSWER)),
                Collections.<Recommendation>emptyList(), "");
    }

    private static ChatData.Snapshot failedConversation() {
        return ChatTestDoubles.read("Transformer attention",
                ChatTestDoubles.messages(ChatTestDoubles.ask(ChatTestDoubles.M_ASK, ChatTestDoubles.QUESTION),
                        ChatMessage.ai(ChatTestDoubles.M_ANSWER, "half an answer", "half a thought",
                                ChatMessage.Status.FAILED, ChatMessage.Phase.ANSWER, "", 0, 0, false, 1L)),
                Collections.<Recommendation>emptyList(), "");
    }

    private static ChatData.Snapshot withVersions() {
        return ChatTestDoubles.read("Transformer attention",
                ChatTestDoubles.messages(ChatTestDoubles.ask(ChatTestDoubles.M_ASK, ChatTestDoubles.QUESTION),
                        ChatMessage.ai(ChatTestDoubles.M_ANSWER, ChatTestDoubles.ANSWER,
                                ChatTestDoubles.REASONING, ChatMessage.Status.STABLE,
                                ChatMessage.Phase.ANSWER, ChatTestDoubles.REV_1, 2, 0, false, 1L)),
                Collections.<Recommendation>emptyList(), "");
    }

    private static ChatData.Snapshot withStrip(Recommendation strip, String revisionId) {
        return ChatTestDoubles.read("Transformer attention",
                ChatTestDoubles.messages(ChatTestDoubles.ask(ChatTestDoubles.M_ASK, ChatTestDoubles.QUESTION),
                        ChatTestDoubles.stableAnswer(ChatTestDoubles.M_ANSWER, ChatTestDoubles.ANSWER)),
                Collections.singletonList(strip), revisionId);
    }

    private static Recommendation recommendation() {
        return ChatTestDoubles.recommendation("how does temperature affect it");
    }
}
