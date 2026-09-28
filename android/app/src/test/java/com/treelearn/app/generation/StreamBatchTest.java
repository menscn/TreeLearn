package com.treelearn.app.generation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Plain-JVM evidence for the write window: 80 ms or 64 accumulated characters, whichever arrives
 * first.
 *
 * <p>The window is expressed as a pure function of an injected "now", so no test sleeps and the
 * boundary values (63 vs 64 characters, 79 vs 80 ms) are both exercised.
 */
public final class StreamBatchTest {

    private static final long T0 = 1_756_000_000_000L;

    @Test
    public void sixtyFourCharactersAreDueAndSixtyThreeAreNot() {
        StreamBatch batch = new StreamBatch();
        batch.append("", repeat('a', StreamBatch.MAX_CHARS - 1), T0);

        assertFalse("one character short of the threshold must stay in memory",
                batch.isDue(T0));
        assertEquals(StreamBatch.MAX_CHARS - 1, batch.pendingChars());

        batch.append("", "a", T0);

        assertTrue("the character threshold is 64", batch.isDue(T0));
    }

    @Test
    public void eightyMillisecondsAreDueAndSeventyNineAreNot() {
        StreamBatch batch = new StreamBatch();
        batch.append("reasoning", "", T0);

        assertFalse(batch.isDue(T0 + 79));
        assertTrue("the window is 80 ms", batch.isDue(T0 + 80));
    }

    @Test
    public void theWindowStartsAtTheFirstUnflushedDeltaNotAtConstruction() {
        StreamBatch batch = new StreamBatch();

        assertFalse("an empty batch is never due, however much time passes",
                batch.isDue(T0 + 60_000));

        batch.append("", "x", T0 + 60_000);

        assertFalse("the window opened with that delta, not with the batch object",
                batch.isDue(T0 + 60_000 + 79));
        assertTrue(batch.isDue(T0 + 60_000 + 80));
    }

    @Test
    public void flushingDrainsBothChannelsAndRestartsTheWindow() {
        StreamBatch batch = new StreamBatch();
        batch.append("why", "answer", T0);

        assertEquals("why", batch.takeReasoning());
        assertEquals("answer", batch.takeAnswer());
        batch.onFlushed(T0 + 10);

        assertTrue(batch.isEmpty());
        assertEquals(0, batch.pendingChars());
        assertFalse("a flushed batch does not flush again on the next tick",
                batch.isDue(T0 + 10));
        assertEquals("", batch.takeReasoning());
        assertEquals("", batch.takeAnswer());
    }

    @Test
    public void theTwoChannelsAreCountedTogetherAndKeptApart() {
        StreamBatch batch = new StreamBatch();
        batch.append(repeat('r', 24), repeat('c', 40), T0);

        assertTrue("characters on either channel count toward the same write window",
                batch.isDue(T0));
        assertEquals(64, batch.pendingChars());
        assertEquals(24, batch.takeReasoning().length());
        assertEquals(40, batch.takeAnswer().length());
    }

    private static String repeat(char value, int count) {
        StringBuilder text = new StringBuilder(count);
        for (int index = 0; index < count; index++) {
            text.append(value);
        }
        return text.toString();
    }
}
