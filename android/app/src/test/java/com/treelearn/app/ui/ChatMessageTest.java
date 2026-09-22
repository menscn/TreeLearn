package com.treelearn.app.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * The facts one rendered answer owns: what "copy the whole answer" produces, which actions its state
 * allows, and which of those the row can actually draw.
 *
 * <p>The copy assertions use the prototype's own headings, and the same two strings are declared in
 * {@code res/values/strings.xml} as {@code chat_copy_reasoning} / {@code chat_copy_answer}; a static
 * check compares the resource to these literals so the clipboard layout cannot drift from the UI.
 *
 * <p>{@code canSelectRevision} is the permission (the same judgement T-010's {@code PositionCommands}
 * makes) while {@code hasVersionHistory} only says whether a switcher is worth drawing; the two are
 * separate so the permission can be compared against the use case in
 * {@link ChatPositionAgreementTest}.
 */
public final class ChatMessageTest {

    private static final CopyLabels LABELS = CopyLabels.of("思考过程", "最终回答");

    @Test
    public void copyIncludesTheReasoningBeforeTheAnswer() {
        assertEquals("思考过程\nthe reasoning\n\n最终回答\nthe answer", stable().copyText(LABELS));
    }

    @Test
    public void copyIsTheAnswerAloneWhenTheAnswerHasNoReasoning() {
        ChatMessage message = ChatMessage.ai(ChatTestDoubles.M_ANSWER, "the answer", "",
                ChatMessage.Status.STABLE, ChatMessage.Phase.ANSWER, ChatTestDoubles.REV_1, 1, 0, false, 1L);

        assertEquals("the answer", message.copyText(LABELS));
    }

    @Test
    public void copyIsAllowedOnlyForAStableAnswer() {
        assertTrue(stable().canCopy());
        assertFalse(generating(ChatMessage.Phase.ANSWER).canCopy());
        assertFalse(generating(ChatMessage.Phase.REASONING).canCopy());
        assertFalse(failed().canCopy());
        assertFalse(ChatMessage.user(ChatTestDoubles.M_ASK, "q", "", 1L).canCopy());
    }

    @Test
    public void theReasoningEntryExistsOnlyWhenThereIsReasoning() {
        assertTrue(stable().canExpandReasoning());
        assertFalse(ChatMessage.ai(ChatTestDoubles.M_ANSWER, "a", "", ChatMessage.Status.STABLE,
                ChatMessage.Phase.ANSWER, ChatTestDoubles.REV_1, 1, 0, false, 1L).canExpandReasoning());
        assertFalse(ChatMessage.user(ChatTestDoubles.M_ASK, "q", "", 1L).canExpandReasoning());
    }

    @Test
    public void theDisclosureExistsWhileTheReasoningIsStillForming() {
        ChatMessage forming = ChatMessage.ai(ChatTestDoubles.M_ANSWER, "", "",
                ChatMessage.Status.GENERATING, ChatMessage.Phase.REASONING, "", 0, 0, false, 1L);

        assertTrue(forming.isFormingReasoning());
        assertTrue("the entry says what is happening before the first token arrives",
                forming.canExpandReasoning());
    }

    @Test
    public void aGeneratingAnswerReportsWhichPhaseItIsIn() {
        assertEquals(ChatMessage.Phase.REASONING, generating(ChatMessage.Phase.REASONING).phase());
        assertEquals(ChatMessage.Phase.ANSWER, generating(ChatMessage.Phase.ANSWER).phase());
        assertTrue(generating(ChatMessage.Phase.REASONING).isGenerating());
        assertTrue(failed().isFailed());
        assertTrue(stable().isStable());
    }

    @Test
    public void onlyAStableAnswerMayBeQuotedOrForked() {
        assertTrue(stable().canQuote());
        assertTrue(stable().canFork());
        assertFalse(failed().canFork());
        assertFalse(failed().canQuote());
        assertFalse(generating(ChatMessage.Phase.ANSWER).canFork());
        assertFalse(ChatMessage.user(ChatTestDoubles.M_ASK, "q", "", 1L).canQuote());
    }

    @Test
    public void theChildrenEntryExistsOnlyWhenTheAnswerHasChildren() {
        assertFalse(stable().hasChildren());
        assertTrue(aiWithChildren(2).hasChildren());
    }

    @Test
    public void theVersionSwitcherIsDrawnOnlyWhenThereIsMoreThanOneVersion() {
        assertFalse(stable().hasVersionHistory());
        assertTrue(aiWithVersions(2).hasVersionHistory());
    }

    @Test
    public void aVersionSwitchIsRefusedForTheVersionAlreadyDisplayed() {
        assertTrue(stable().canSelectRevision(ChatTestDoubles.REV_2));
        assertFalse(stable().canSelectRevision(ChatTestDoubles.REV_1));
    }

    @Test
    public void aLearnerMessageOwnsNoAnswerActions() {
        ChatMessage question = ChatMessage.user(ChatTestDoubles.M_ASK, "q", "the quoted sentence", 1L);

        assertFalse(question.canRetry());
        assertFalse(question.canReset());
        assertFalse(question.canFork());
        assertFalse(question.hasVersionHistory());
        assertEquals("the quoted sentence", question.quote);
    }

    private static ChatMessage stable() {
        return ChatMessage.ai(ChatTestDoubles.M_ANSWER, "the answer", "the reasoning",
                ChatMessage.Status.STABLE, ChatMessage.Phase.ANSWER, ChatTestDoubles.REV_1, 1, 0, false, 1L);
    }

    private static ChatMessage aiWithChildren(int children) {
        return ChatMessage.ai(ChatTestDoubles.M_ANSWER, "the answer", "the reasoning",
                ChatMessage.Status.STABLE, ChatMessage.Phase.ANSWER, ChatTestDoubles.REV_1, 1, children,
                false, 1L);
    }

    private static ChatMessage aiWithVersions(int versions) {
        return ChatMessage.ai(ChatTestDoubles.M_ANSWER, "the answer", "the reasoning",
                ChatMessage.Status.STABLE, ChatMessage.Phase.ANSWER, ChatTestDoubles.REV_1, versions, 0,
                false, 1L);
    }

    private static ChatMessage generating(ChatMessage.Phase phase) {
        return ChatMessage.ai(ChatTestDoubles.M_ANSWER, "half an ans", "half a thou",
                ChatMessage.Status.GENERATING, phase, "", 0, 0, false, 1L);
    }

    private static ChatMessage failed() {
        return ChatMessage.ai(ChatTestDoubles.M_ANSWER, "half an ans", "half a thou",
                ChatMessage.Status.FAILED, ChatMessage.Phase.ANSWER, "", 1, 0, false, 1L);
    }
}