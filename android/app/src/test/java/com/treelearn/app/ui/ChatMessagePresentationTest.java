package com.treelearn.app.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * T-074: the presentation state one answer owns - which version the switcher names, and which
 * actions each attempt state (generating / failed / stable) leaves usable.
 *
 * <p>The availability matrix reads the same predicates the row draws and the presenter obeys, so a
 * button the matrix calls usable is a command the store accepts (the {@code ChatPositionAgreementTest}
 * guarantee). The version switcher's "n/N" needs the displayed version's number, which the row can
 * only learn from the message itself.
 */
public final class ChatMessagePresentationTest {

    @Test
    public void theSwitcherCarriesTheDisplayedVersionNumber() {
        ChatMessage message = ChatMessage.ai(ChatTestDoubles.M_ANSWER, "the answer", "",
                ChatMessage.Status.STABLE, ChatMessage.Phase.ANSWER, ChatTestDoubles.REV_1, 1,
                3, 0, false, 1L);

        assertTrue(message.hasVersionHistory());
        assertEquals("the chip shows n/N with the displayed n", 1, message.activeRevisionNo());
    }

    @Test
    public void theVersionNumberIsUnknownWhenTheFactoryDoesNotCarryIt() {
        assertEquals(0,
                ChatTestDoubles.stableAnswer(ChatTestDoubles.M_ANSWER, "a").activeRevisionNo());
    }

    @Test
    public void aStableAnswerOpensEveryActionTheStoreAccepts() {
        ChatMessage stable = ChatMessage.ai(ChatTestDoubles.M_ANSWER, "the answer", "",
                ChatMessage.Status.STABLE, ChatMessage.Phase.ANSWER, ChatTestDoubles.REV_1, 1,
                3, 0, false, 1L);

        assertTrue(stable.canCopy());
        assertTrue(stable.canQuote());
        assertTrue(stable.canFork());
        assertTrue(stable.canRetry());
        assertTrue(stable.canReset());
        assertTrue(stable.canSelectRevision(ChatTestDoubles.REV_2));
        assertFalse("the displayed version is not offered again",
                stable.canSelectRevision(ChatTestDoubles.REV_1));
        assertTrue(stable.hasVersionHistory());
    }

    @Test
    public void aRunningAttemptDisablesTheDestructiveActionsButKeepsTheDisplayedVersion() {
        ChatMessage regenerating = ChatMessage.ai(ChatTestDoubles.M_ANSWER, "the answer", "",
                ChatMessage.Status.STABLE, ChatMessage.Phase.ANSWER, ChatTestDoubles.REV_1, 1,
                2, 0, true, 1L);

        assertFalse(regenerating.canRetry());
        assertFalse(regenerating.canReset());
        assertFalse("no version switch lands while an attempt is in flight",
                regenerating.canSelectRevision(ChatTestDoubles.REV_2));
        assertTrue("the switcher stays drawn", regenerating.hasVersionHistory());
        assertTrue("the displayed version stays copyable", regenerating.canCopy());
    }

    @Test
    public void aGeneratingAnswerOffersNoActionAtAll() {
        for (ChatMessage.Phase phase : ChatMessage.Phase.values()) {
            ChatMessage generating = ChatMessage.ai(ChatTestDoubles.M_ANSWER, "half an ans",
                    "half a thought", ChatMessage.Status.GENERATING, phase, "", 0,
                    0, 0, true, 1L);

            assertFalse(phase + " copy", generating.canCopy());
            assertFalse(phase + " quote", generating.canQuote());
            assertFalse(phase + " fork", generating.canFork());
            assertFalse(phase + " retry", generating.canRetry());
            assertFalse(phase + " reset", generating.canReset());
            assertFalse(phase + " switcher", generating.hasVersionHistory());
        }
    }

    @Test
    public void aFailedAttemptKeepsRetryAlone() {
        ChatMessage failed = ChatMessage.ai(ChatTestDoubles.M_ANSWER, "half an ans", "",
                ChatMessage.Status.FAILED, ChatMessage.Phase.ANSWER, "", 0,
                1, 0, false, 1L);

        assertTrue(failed.canRetry());
        assertFalse(failed.canReset());
        assertFalse(failed.canCopy());
        assertFalse(failed.canQuote());
        assertFalse(failed.canFork());
        assertFalse(failed.hasVersionHistory());
    }
}
