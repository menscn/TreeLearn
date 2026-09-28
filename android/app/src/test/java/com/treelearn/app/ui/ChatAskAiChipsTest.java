package com.treelearn.app.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.treelearn.core.domain.GenerationModel;

import org.junit.Test;

import java.util.Collections;

/**
 * 2026-09-28: the 问 AI quick queries (why / what / how) that rise under the quote row once the
 * selection menu's 问 AI sets a quote.
 *
 * <p>Five behaviours are pinned here, each one a user decision rather than a rendering choice:
 *
 * <ul>
 *   <li><strong>The chips ride the quote.</strong> The menu's 问 AI raises them together with the
 *       quote; clearing the quote takes them down. A chip row over no quote would offer to ask
 *       about nothing.
 *   <li><strong>A chip tap sends its query as-is, carrying the quote.</strong> The query text is
 *       the message, the quote is the cargo, and the learner's own draft stays in the composer -
 *       the chip answers the selection, not what they were typing.
 *   <li><strong>The chips collapse on send.</strong> One tap, one query, then they are gone.
 *   <li><strong>A chip without a quote never sends.</strong> The chips are only drawn behind a
 *       quote, but the presenter re-checks, so the screen and the presenter agree on why nothing
 *       happens.
 *   <li><strong>A prefilled branch anchor is not the menu's 问 AI.</strong> The T-065 prefill
 *       draws the quote row alone; the chips belong to the tap that asked for them.
 * </ul>
 */
public final class ChatAskAiChipsTest {

    private static final CopyLabels LABELS = CopyLabels.of("思考过程", "最终回答");
    private static final String WHY_QUERY = "围绕我选择的内容，解释其中相关的原因、依据、推导过程或成立条件。";

    private final ChatTestDoubles.RecordingChatSink sink = new ChatTestDoubles.RecordingChatSink();
    private final ChatTestDoubles.FakeChatData data = new ChatTestDoubles.FakeChatData();
    private final ChatTestDoubles.FakeChatCommands commands = new ChatTestDoubles.FakeChatCommands();
    private final TestDoubles.FakeKeyVault key = new TestDoubles.FakeKeyVault(true);
    private final TestDoubles.FakeSettingsStore settings = new TestDoubles.FakeSettingsStore();
    private final ChatComposerInputTest.FakeDraftStore drafts = new ChatComposerInputTest.FakeDraftStore();

    private ChatTestDoubles.QueueingExecutor deferred;

    private ChatPresenter deferredPresenter() {
        deferred = new ChatTestDoubles.QueueingExecutor();
        return new ChatPresenter(sink, new TestDoubles.InlineUi(), deferred, key, data, commands,
                settings, LABELS, ChatTestDoubles.BRANCH, new ChatTestDoubles.NoPositionStore(),
                drafts);
    }

    @Test
    public void theSelectionMenusAskAiRaisesTheChipsWithTheQuote() {
        data.snapshot = ChatTestDoubles.snapshot(Collections.<ChatMessage>emptyList());
        ChatPresenter presenter = deferredPresenter();
        presenter.onResumed();
        deferred.drain();

        presenter.onQuotePressed(ChatTestDoubles.M_ASK, "the scaling sentence");

        assertTrue(sink.last().askAiChips);
        assertEquals("the scaling sentence", sink.last().quote);
    }

    @Test
    public void clearingTheQuoteTakesTheChipsDown() {
        data.snapshot = ChatTestDoubles.snapshot(Collections.<ChatMessage>emptyList());
        ChatPresenter presenter = deferredPresenter();
        presenter.onResumed();
        deferred.drain();
        presenter.onQuotePressed(ChatTestDoubles.M_ASK, "the scaling sentence");

        presenter.onQuoteCleared();

        assertFalse(sink.last().askAiChips);
        assertEquals("", sink.last().quote);
    }

    @Test
    public void aChipTapSendsItsQueryCarriesTheQuoteAndKeepsTheDraft() {
        data.snapshot = ChatTestDoubles.snapshot(Collections.<ChatMessage>emptyList());
        ChatPresenter presenter = deferredPresenter();
        presenter.onResumed();
        deferred.drain();
        presenter.onDraftChanged("a half typed question");
        presenter.onQuotePressed(ChatTestDoubles.M_ASK, "the scaling sentence");

        presenter.onAskAiChipPressed(WHY_QUERY);
        deferred.drain();

        assertTrue("the chip's own query is the message", commands.called("send"));
        assertEquals(WHY_QUERY, commands.lastText);
        assertEquals("the quote rides as the cargo", "the scaling sentence", commands.lastQuote);
        assertEquals("the learner's draft survives the chip send", "a half typed question",
                sink.last().draft);
        assertFalse("the stored record outlives the send", drafts.cleared);
        assertEquals("the stored record keeps the draft text", "a half typed question",
                drafts.lastSaved.text);
        assertEquals("the stored record drops the quote that left", "", drafts.lastSaved.quote);
        assertFalse("the chips collapse once their query went out", sink.last().askAiChips);
        assertEquals("", sink.last().quote);
    }

    @Test
    public void aRefusedChipSendKeepsEverythingTheLearnerHad() {
        data.snapshot = ChatTestDoubles.snapshot(Collections.<ChatMessage>emptyList());
        ChatPresenter presenter = deferredPresenter();
        presenter.onResumed();
        deferred.drain();
        presenter.onDraftChanged("a half typed question");
        presenter.onQuotePressed(ChatTestDoubles.M_ASK, "the scaling sentence");
        commands.sendResult = ChatCommandResult.refused(HomeViewState.Notice.RATE_LIMITED);

        presenter.onAskAiChipPressed(WHY_QUERY);
        deferred.drain();

        assertFalse(drafts.cleared);
        assertEquals("a half typed question", sink.last().draft);
        assertEquals("a refusal keeps the quote so the learner can retry", "the scaling sentence",
                sink.last().quote);
    }

    @Test
    public void aChipWithoutAQuoteNeverSends() {
        data.snapshot = ChatTestDoubles.snapshot(Collections.<ChatMessage>emptyList());
        ChatPresenter presenter = deferredPresenter();
        presenter.onResumed();
        deferred.drain();

        presenter.onAskAiChipPressed(WHY_QUERY);
        deferred.drain();

        assertFalse("no quote, no cargo, no send", commands.called("send"));
    }

    @Test
    public void aPrefilledBranchAnchorIsNotTheMenusAskAi() {
        ChatViewState state = ChatViewState.initial(ChatTestDoubles.BRANCH, GenerationModel.FLASH,
                false, true).withQuote("the scaling sentence");

        assertEquals("the prefill draws the quote row alone", "the scaling sentence", state.quote);
        assertFalse("the chips belong to the tap that asked for them", state.askAiChips);
    }
}
