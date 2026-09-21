package com.treelearn.core.domain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

/**
 * Identifier and invariant helpers.
 *
 * <p>{@link java.util.UUID#fromString(String)} is lenient: it accepts {@code "1-1-1-1-1"} and short
 * groups, so a single UUID has many spellings. Because domain identifiers are compared and keyed as
 * strings, a second spelling would silently defeat {@code BranchSource.pinsRevision} and produce
 * duplicate primary keys. A round-2-independent audit reproduced exactly that, which is why the
 * canonical form is enforced here.
 */
public class GuardTest {

    @Test
    public void canonicalUuidStringsAreAccepted() {
        String canonical = Guard.newId();
        assertEquals(canonical, Guard.id(canonical, "id"));
        assertEquals("00000000-0000-0000-0000-000000000000",
                Guard.id("00000000-0000-0000-0000-000000000000", "id"));
    }

    @Test
    public void nonCanonicalSpellingsOfAValidUuidAreRefused() {
        for (String value : new String[] {
                "1-1-1-1-1",
                "00000000-0000-0000-0000-00000000000",
                "00000000000000000000000000000001",
                "00000000-0000-0000-0000-00000000000A",
                "00000000-0000-0000-0000-000000000001 ",
        }) {
            try {
                Guard.id(value, "id");
                fail("<" + value + "> must be refused as a non-canonical UUID");
            } catch (IllegalArgumentException expected) {
                assertTrue(expected.getMessage().contains("id"));
            }
        }
    }

    @Test
    public void nullAndEmptyIdentifiersAreRefused() {
        for (String value : new String[] {null, ""}) {
            try {
                Guard.id(value, "id");
                fail("a null or empty identifier must be refused");
            } catch (IllegalArgumentException expected) {
                assertTrue(expected.getMessage().contains("id"));
            }
        }
        assertNull(Guard.idOrNull(null, "optionalId"));
        String canonical = Guard.newId();
        assertEquals(canonical, Guard.idOrNull(canonical, "optionalId"));
    }

    @Test
    public void objectChecksAcceptEnumsAndValueObjectsAndRefuseNull() {
        assertEquals(MessageRole.AI, Guard.object(MessageRole.AI, "role"));
        assertNotNull(Guard.object(BranchAnchor.wholeAnswer(), "anchor"));
        try {
            Guard.object(null, "role");
            fail("null must be refused");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("role"));
        }
    }

    @Test
    public void countersRefuseNegativeValues() {
        assertEquals(0L, Guard.nonNegative(0L, "sequence"));
        try {
            Guard.nonNegative(-1L, "sequence");
            fail("a negative counter must be refused");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("sequence"));
        }
    }

    @Test
    public void newIdProducesCanonicalDistinctUuids() {
        String first = Guard.newId();
        String second = Guard.newId();
        assertEquals(first, Guard.id(first, "id"));
        assertFalse("two generated ids must differ", first.equals(second));
    }
}
