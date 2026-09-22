package com.treelearn.data.db.dao;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

/**
 * T-037 acceptance, {@code :data} half: how "no quote" is spelled on each side of the store
 * boundary.
 *
 * <p>The domain has exactly one spelling for "no quote" — the empty string. The store has exactly
 * one — {@code NULL}. Something has to translate between them, and if that translation lived in
 * more than one place the two copies would drift: one column would hold an empty string where
 * another holds NULL, and every reader would have to know which. Both directions therefore live in
 * {@link MessageDao}, and this suite pins them.
 *
 * <p>These are pure functions, so they are provable on the JVM. Whether the stored value survives a
 * real {@code INSERT}/{@code SELECT}, and whether the store's own rule refuses a quote on an AI
 * row, needs the real Android SQLite engine (rules/sql.md) and is deferred with the device rounds
 * (user instruction 2026-09-21).
 */
public final class MessageQuoteColumnTest {

    private static final String QUOTED = "the quoted sentence";

    @Test
    public void noQuoteIsWrittenAsNullRatherThanAnEmptyString() {
        assertNull("an empty quote is an absent quote, and the column has one spelling for it",
                MessageDao.quoteColumnValue(""));
    }

    @Test
    public void aPresentQuoteIsWrittenVerbatim() {
        assertEquals(QUOTED, MessageDao.quoteColumnValue(QUOTED));
    }

    @Test
    public void aNullQuoteIsACallerBugRatherThanNoQuote() {
        try {
            MessageDao.quoteColumnValue(null);
            fail("the domain never hands out a null quote; translating one silently would hide the"
                    + " caller that produced it");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("quotedText"));
        }
    }

    @Test
    public void aStoredNullReadsBackAsAnEmptyStringNeverNull() {
        String read = MessageDao.quoteFromColumn(null);

        assertNotNull("callers must not have to null-check the column", read);
        assertEquals("", read);
    }

    @Test
    public void aStoredQuoteReadsBackVerbatim() {
        assertEquals(QUOTED, MessageDao.quoteFromColumn(QUOTED));
    }

    @Test
    public void theTwoDirectionsOfTheBoundaryAgree() {
        assertEquals(QUOTED, MessageDao.quoteFromColumn(MessageDao.quoteColumnValue(QUOTED)));
        assertEquals("no quote survives a write and a read as no quote",
                "", MessageDao.quoteFromColumn(MessageDao.quoteColumnValue("")));
    }
}
