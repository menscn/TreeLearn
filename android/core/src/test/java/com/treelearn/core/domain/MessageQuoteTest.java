package com.treelearn.core.domain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

/**
 * T-037 acceptance, {@code :core} half: the passage a learner quotes back travels with the USER
 * position.
 *
 * <p>PRD 6.2.19 asks that a quoted follow-up still shows what it was asked about after the message
 * is sent, so the quote has to be part of the position, not of one screen's memory. The domain
 * speaks one shape for it: the empty string means "no quote" and is never null, so no caller has to
 * null-check before putting the passage on screen.
 *
 * <p>What this suite cannot prove is the row-level half — that the store really keeps the text, and
 * that it refuses a quote on an AI row. That needs the real Android SQLite engine (rules/sql.md)
 * and is deferred with the device rounds (user instruction 2026-09-21).
 */
public class MessageQuoteTest {

    private static final long NOW = 1_700_000_000_000L;

    private static final String QUOTED = "the quoted sentence";

    @Test
    public void aUserPositionCarriesTheQuotedPassage() {
        Message question = Message.user(Guard.newId(), 0, "why is the sky blue?", QUOTED, NOW);

        assertEquals(QUOTED, question.quotedText());
    }

    @Test
    public void theExistingFactoryStillProducesAPositionWithoutAQuote() {
        Message question = Message.user(Guard.newId(), 0, "why is the sky blue?", NOW);

        assertEquals("a position without a quote carries the empty string, never null", "",
                question.quotedText());
    }

    @Test
    public void anEmptyQuoteIsNoQuoteRatherThanAQuotedEmptyPassage() {
        assertEquals("", Message.user(Guard.newId(), 0, "q", "", NOW).quotedText());
    }

    @Test
    public void anAiPositionNeverCarriesAQuote() {
        assertEquals("", Message.ai(Guard.newId(), 1, NOW).quotedText());
    }

    @Test
    public void aNullQuoteIsRefusedAtTheDomainBoundary() {
        try {
            Message.user(Guard.newId(), 0, "q", null, NOW);
            fail("the store's NULL is the DAO's spelling of \"no quote\"; the domain has one"
                    + " spelling, and it is the empty string");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("quotedText"));
        }
    }

    @Test
    public void restoringAUserRowKeepsTheQuote() {
        Message restored = Message.restore(Guard.newId(), Guard.newId(), MessageRole.USER, 0,
                "why is the sky blue?", QUOTED, NOW, null, null);

        assertEquals(QUOTED, restored.quotedText());
    }

    @Test
    public void aRowWhoseQuoteColumnIsNullReadsBackAsAnEmptyString() {
        Message restored = Message.restore(Guard.newId(), Guard.newId(), MessageRole.USER, 0,
                "why is the sky blue?", "", NOW, null, null);

        assertEquals("a NULL quote column is \"no quote\", and the caller must not have to"
                + " null-check it", "", restored.quotedText());
    }

    @Test
    public void theEightArgumentRestoreStillMeansNoQuote() {
        Message restored = Message.restore(Guard.newId(), Guard.newId(), MessageRole.AI, 4, null,
                NOW, Guard.newId(), null);

        assertEquals("", restored.quotedText());
    }

    @Test
    public void aCorruptAiRowCarryingAQuoteFailsLoudly() {
        try {
            Message.restore(Guard.newId(), Guard.newId(), MessageRole.AI, 4, null, QUOTED, NOW,
                    null, null);
            fail("the store cannot produce this row, so a reader that quietly dropped the quote"
                    + " would hide a corrupt row instead of naming it");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("quotedText"));
        }
    }

    @Test
    public void theQuoteIsImmutableLikeTheRestOfThePosition() throws Exception {
        java.lang.reflect.Field quote = Message.class.getDeclaredField("quotedText");
        assertTrue("quotedText must be final", java.lang.reflect.Modifier.isFinal(
                quote.getModifiers()));

        for (java.lang.reflect.Method method : Message.class.getMethods()) {
            if (method.getDeclaringClass() == Object.class) {
                continue;
            }
            assertFalse("a position must expose no setter: " + method.getName(),
                    method.getName().startsWith("set"));
        }
    }
}
