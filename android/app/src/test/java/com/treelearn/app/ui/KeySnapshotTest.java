package com.treelearn.app.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

/**
 * The mask is the only form of the stored key that may leave {@link KeySnapshot}, so it is asserted
 * directly: no middle characters, a bounded length, and a fully hidden short credential.
 */
public final class KeySnapshotTest {

    @Test
    public void aLongKeyKeepsOnlyItsPrefixAndLastFourCharacters() {
        String mask = KeySnapshot.mask("sk-abcdefghijklmnop".toCharArray());

        assertEquals("sk-\u2022\u2022\u2022\u2022\u2022\u2022\u2022\u2022\u2022\u2022mnop", mask);
        assertFalse("the middle of the key must not appear", mask.contains("abcdefghij"));
    }

    @Test
    public void everyNonBulletCharacterOfTheMaskAlsoAppearsInTheKey() {
        char[] key = "sk-1234567890abcdef".toCharArray();

        String mask = KeySnapshot.mask(key);

        for (int i = 0; i < mask.length(); i++) {
            char c = mask.charAt(i);
            if (c == '\u2022') {
                continue;
            }
            assertTrue("mask character " + c + " is not part of the key",
                    new String(key).indexOf(c) >= 0);
        }
    }

    @Test
    public void aShortKeyIsFullyMasked() {
        assertEquals("\u2022\u2022\u2022\u2022\u2022\u2022\u2022\u2022\u2022\u2022",
                KeySnapshot.mask("short".toCharArray()));
    }

    @Test
    public void aKeyWithoutTheProviderPrefixStillGetsAPrefixFreeMask() {
        String mask = KeySnapshot.mask("0123456789abcdef".toCharArray());

        assertEquals("\u2022\u2022\u2022\u2022\u2022\u2022\u2022\u2022\u2022\u2022cdef", mask);
    }

    @Test
    public void theAbsentAndUnreadableStatesCarryNoCharactersAtAll() {
        assertEquals(KeySnapshot.Line.MISSING, KeySnapshot.missing().line);
        assertEquals("", KeySnapshot.missing().mask);
        assertEquals(KeySnapshot.Line.UNREADABLE, KeySnapshot.unreadable().line);
        assertEquals("", KeySnapshot.unreadable().mask);
    }

    @Test
    public void anEmptyBufferIsACallerBug() {
        try {
            KeySnapshot.mask(new char[0]);
            fail("an empty key must not be maskable");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("at least one character"));
        }
    }
}