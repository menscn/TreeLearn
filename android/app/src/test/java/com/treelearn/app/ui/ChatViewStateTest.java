package com.treelearn.app.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.treelearn.core.domain.GenerationModel;
import com.treelearn.core.domain.Persona;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

/**
 * The chat screen's state as one value: what the four areas show, and - the part that is easy to get
 * wrong - what a re-read from the store must not disturb.
 *
 * <p>Two rules are asserted here rather than in the presenter, because they are properties of the
 * value: an expanded reasoning block is keyed by message id (never by position, which is what makes
 * it unable to leak from one revision to another), and replacing the message list keeps the draft,
 * the expanded set and the composer settings, so a read that lands mid-typing cannot throw away what
 * the learner has done.
 */
public final class ChatViewStateTest {

    @Test
    public void theInitialStateHasNothingButTheBranchItWasOpenedFor() {
        ChatViewState state = initial();

        assertEquals(ChatViewState.Phase.LOADING, state.phase);
        assertEquals(ChatTestDoubles.BRANCH, state.branchId);
        assertEquals("", state.branchTitle);
        assertTrue(state.messages.isEmpty());
        assertEquals("", state.draft);
        assertEquals("", state.quote);
        assertTrue(state.recommendations.isEmpty());
        assertEquals("", state.recommendationsMessageId);
        assertNull(state.notice);
        assertFalse(state.sending);
        assertFalse(state.recommendationsVisible());
        assertFalse(state.canSubmit());
    }

    @Test
    public void aBlankDraftAndAnInFlightSendBothBlockSubmission() {
        assertTrue(initial().withDraft("why").canSubmit());
        assertFalse(initial().withDraft("   ").canSubmit());
        assertFalse(initial().withDraft("why").withSending(true).canSubmit());
    }

    @Test
    public void theStyleStartsAtDefaultAndTravelsThroughEveryBuilder() {
        assertEquals(Persona.DEFAULT, initial().persona);

        ChatViewState styled = initial().withPersona(Persona.STUDY_BUDDY).withDraft("why");

        assertEquals(Persona.STUDY_BUDDY, styled.persona);
        assertEquals("a read must not reset the style",
                Persona.STUDY_BUDDY, styled.withMessages(Collections.<ChatMessage>emptyList()).persona);
        assertEquals("why", styled.withMessages(Collections.<ChatMessage>emptyList()).draft);
    }

    @Test
    public void reasoningStartsCollapsedAndIsKeyedByMessageNotByPosition() {
        ChatViewState state = withConversation();

        assertFalse(state.isReasoningExpanded(ChatTestDoubles.M_ANSWER));
        assertFalse(state.isReasoningExpanded(ChatTestDoubles.M_ANSWER_2));

        ChatViewState opened = state.withReasoningExpanded(ChatTestDoubles.M_ANSWER, true);

        assertTrue(opened.isReasoningExpanded(ChatTestDoubles.M_ANSWER));
        assertFalse(opened.isReasoningExpanded(ChatTestDoubles.M_ANSWER_2));
        assertFalse("the previous state must not be mutated",
                state.isReasoningExpanded(ChatTestDoubles.M_ANSWER));
        assertFalse(opened.withReasoningExpanded(ChatTestDoubles.M_ANSWER, false)
                .isReasoningExpanded(ChatTestDoubles.M_ANSWER));
    }

    @Test
    public void theStripIsShownOnlyWhenItBelongsToAMessage() {
        assertFalse(initial().withRecommendations(
                Collections.singletonList(ChatTestDoubles.recommendation("q")), "")
                .recommendationsVisible());
        assertFalse(initial().withRecommendations(Collections.<Recommendation>emptyList(),
                ChatTestDoubles.M_ANSWER).recommendationsVisible());
        assertTrue(initial().withRecommendations(
                Collections.singletonList(ChatTestDoubles.recommendation("q")),
                ChatTestDoubles.M_ANSWER).recommendationsVisible());
    }

    @Test
    public void aMessageIsFoundByIdAndNothingIsFoundForAnUnknownId() {
        ChatViewState state = withConversation();

        assertEquals(ChatTestDoubles.ANSWER, state.message(ChatTestDoubles.M_ANSWER).text);
        assertEquals(ChatTestDoubles.QUESTION, state.message(ChatTestDoubles.M_ASK).text);
        assertNull(state.message(ChatTestDoubles.M_ANSWER_2));
    }

    @Test
    public void replacingTheMessagesKeepsWhatTheLearnerIsDoing() {
        ChatViewState state = initial()
                .withDraft("a half typed follow up")
                .withQuote("a quoted sentence")
                .withReasoningExpanded(ChatTestDoubles.M_ANSWER, true)
                .withSending(true)
                .withNotice(HomeViewState.Notice.RATE_LIMITED);

        ChatViewState reread = state.withMessages(Arrays.asList(
                ChatTestDoubles.ask(ChatTestDoubles.M_ASK, ChatTestDoubles.QUESTION),
                ChatTestDoubles.stableAnswer(ChatTestDoubles.M_ANSWER, ChatTestDoubles.ANSWER)));

        assertEquals("a half typed follow up", reread.draft);
        assertEquals("a quoted sentence", reread.quote);
        assertTrue(reread.isReasoningExpanded(ChatTestDoubles.M_ANSWER));
        assertTrue(reread.sending);
        assertEquals(HomeViewState.Notice.RATE_LIMITED, reread.notice);
        assertEquals(2, reread.messages.size());
    }

    @Test
    public void theMessageListIsCopiedAndCannotBeMutatedThroughTheState() {
        List<ChatMessage> source = new ArrayList<ChatMessage>();
        source.add(ChatTestDoubles.ask(ChatTestDoubles.M_ASK, ChatTestDoubles.QUESTION));

        ChatViewState state = initial().withMessages(source);
        source.add(ChatTestDoubles.stableAnswer(ChatTestDoubles.M_ANSWER, ChatTestDoubles.ANSWER));

        assertEquals(1, state.messages.size());
        try {
            state.messages.add(ChatTestDoubles.stableAnswer(ChatTestDoubles.M_ANSWER, "a"));
            fail("the message list must be unmodifiable");
        } catch (UnsupportedOperationException expected) {
            assertEquals(1, state.messages.size());
        }
    }

    @Test
    public void theComposerSettingsTravelWithEveryTransition() {
        ChatViewState state = initial().withKeyConfigured(false).withModel(GenerationModel.PRO)
                .withThinkingEnabled(true).withPhase(ChatViewState.Phase.READY);

        assertFalse(state.keyConfigured);
        assertEquals(GenerationModel.PRO, state.model);
        assertTrue(state.thinkingEnabled);
        assertEquals(ChatViewState.Phase.READY, state.phase);
        assertFalse("a refresh must not silently re-enable the composer",
                state.withMessages(Collections.<ChatMessage>emptyList()).keyConfigured);
    }

    @Test
    public void aQuoteIsTrimmedAndCanBeCleared() {
        assertEquals("a sentence", initial().withQuote("  a sentence ").quote);
        assertEquals("", initial().withQuote("a sentence").withQuote("").quote);
    }

    @Test
    public void formingReasoningIsExpandedByDefault() {
        ChatViewState state = initial().withMessages(Arrays.asList(
                ChatTestDoubles.ask(ChatTestDoubles.M_ASK, ChatTestDoubles.QUESTION),
                ChatTestDoubles.formingReasoning(ChatTestDoubles.M_ANSWER,
                        ChatTestDoubles.REASONING)));

        assertTrue("reasoning that is streaming is open without any tap",
                state.isReasoningEffectivelyExpanded(ChatTestDoubles.M_ANSWER));

        // A streaming refresh - more reasoning text, same attempt - keeps the default.
        ChatViewState refreshed = state.withMessages(Arrays.asList(
                ChatTestDoubles.ask(ChatTestDoubles.M_ASK, ChatTestDoubles.QUESTION),
                ChatTestDoubles.formingReasoning(ChatTestDoubles.M_ANSWER,
                        ChatTestDoubles.REASONING + " and more")));
        assertTrue(refreshed.isReasoningEffectivelyExpanded(ChatTestDoubles.M_ANSWER));
    }

    @Test
    public void manualCollapseSurvivesStreamingRefresh() {
        ChatViewState state = initial().withMessages(Arrays.asList(
                ChatTestDoubles.ask(ChatTestDoubles.M_ASK, ChatTestDoubles.QUESTION),
                ChatTestDoubles.formingReasoning(ChatTestDoubles.M_ANSWER,
                        ChatTestDoubles.REASONING)));

        ChatViewState collapsed = state.withReasoningExpanded(ChatTestDoubles.M_ANSWER, false);
        assertFalse(collapsed.isReasoningEffectivelyExpanded(ChatTestDoubles.M_ANSWER));

        // The next read lands with more streamed text: the learner's fold must not spring back.
        ChatViewState refreshed = collapsed.withMessages(Arrays.asList(
                ChatTestDoubles.ask(ChatTestDoubles.M_ASK, ChatTestDoubles.QUESTION),
                ChatTestDoubles.formingReasoning(ChatTestDoubles.M_ANSWER,
                        ChatTestDoubles.REASONING + " and more")));
        assertFalse("a manual fold overrides the streaming default",
                refreshed.isReasoningEffectivelyExpanded(ChatTestDoubles.M_ANSWER));
    }

    @Test
    public void leavingFormingAutoCollapses() {
        ChatViewState state = initial().withMessages(Arrays.asList(
                ChatTestDoubles.ask(ChatTestDoubles.M_ASK, ChatTestDoubles.QUESTION),
                ChatTestDoubles.formingReasoning(ChatTestDoubles.M_ANSWER,
                        ChatTestDoubles.REASONING)));
        assertTrue(state.isReasoningEffectivelyExpanded(ChatTestDoubles.M_ANSWER));

        // The attempt moved on to the answer: the disclosure folds itself.
        ChatViewState done = state.withMessages(Arrays.asList(
                ChatTestDoubles.ask(ChatTestDoubles.M_ASK, ChatTestDoubles.QUESTION),
                ChatTestDoubles.stableAnswer(ChatTestDoubles.M_ANSWER, ChatTestDoubles.ANSWER)));
        assertFalse("reasoning must fold when the stream leaves the reasoning phase",
                done.isReasoningEffectivelyExpanded(ChatTestDoubles.M_ANSWER));
    }

    @Test
    public void manualExpandSurvivesCompletion() {
        ChatViewState state = initial().withMessages(Arrays.asList(
                ChatTestDoubles.ask(ChatTestDoubles.M_ASK, ChatTestDoubles.QUESTION),
                ChatTestDoubles.formingReasoning(ChatTestDoubles.M_ANSWER,
                        ChatTestDoubles.REASONING)));

        ChatViewState opened = state.withReasoningExpanded(ChatTestDoubles.M_ANSWER, true);
        ChatViewState done = opened.withMessages(Arrays.asList(
                ChatTestDoubles.ask(ChatTestDoubles.M_ASK, ChatTestDoubles.QUESTION),
                ChatTestDoubles.stableAnswer(ChatTestDoubles.M_ANSWER, ChatTestDoubles.ANSWER)));
        assertTrue("a manual open is an explicit intent and survives the completion",
                done.isReasoningEffectivelyExpanded(ChatTestDoubles.M_ANSWER));
    }

    @Test
    public void answerWithoutReasoningHasNoEntry() {
        ChatViewState state = initial().withMessages(Arrays.asList(
                ChatTestDoubles.ask(ChatTestDoubles.M_ASK, ChatTestDoubles.QUESTION),
                ChatTestDoubles.stableAnswerWithoutReasoning(ChatTestDoubles.M_ANSWER,
                        ChatTestDoubles.ANSWER)));

        assertFalse("no reasoning means no disclosure at all",
                state.message(ChatTestDoubles.M_ANSWER).canExpandReasoning());
        assertFalse(state.isReasoningEffectivelyExpanded(ChatTestDoubles.M_ANSWER));
    }

    private static ChatViewState initial() {
        return ChatViewState.initial(ChatTestDoubles.BRANCH, GenerationModel.FLASH, false, true);
    }

    private static ChatViewState withConversation() {
        return initial().withMessages(Arrays.asList(
                ChatTestDoubles.ask(ChatTestDoubles.M_ASK, ChatTestDoubles.QUESTION),
                ChatTestDoubles.stableAnswer(ChatTestDoubles.M_ANSWER, ChatTestDoubles.ANSWER)));
    }
}