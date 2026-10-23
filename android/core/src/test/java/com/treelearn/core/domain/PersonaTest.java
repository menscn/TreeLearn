package com.treelearn.core.domain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.HashSet;
import java.util.Set;

import org.junit.Test;

/**
 * The seven built-in answer styles (SPEC-2026-09-21-ai-persona-preference, 2026-10-23 decisions).
 *
 * <p>The wire ids are the only literals that may reach {@code topics.persona},
 * {@code generation_operations.persona} and {@code answer_revisions.persona}, so they are pinned
 * here rather than derived from display names. An unknown stored value is a predictable data state
 * (a downgrade or a leftover row), not a code bug, so it falls back to the default instead of
 * throwing; the enum is the one place that judgement is made.
 */
public class PersonaTest {

    private static final String[] WIRE_IDS = {
            "default",
            "hand-holding",
            "strict-mentor",
            "study-buddy",
            "sarcastic-friend",
            "late-night",
            "learning-repair",
    };

    @Test
    public void theSevenWireIdsAreStableAndExhaustive() {
        assertEquals("the built-in set is fixed at seven", WIRE_IDS.length, Persona.values().length);
        for (int i = 0; i < WIRE_IDS.length; i++) {
            assertEquals(WIRE_IDS[i], Persona.values()[i].wireId);
        }
    }

    @Test
    public void everyWireIdRoundTrips() {
        Set<String> seen = new HashSet<>();
        for (Persona persona : Persona.values()) {
            assertTrue("a wire id must be unique: " + persona.wireId, seen.add(persona.wireId));
            assertEquals(persona, Persona.fromWireId(persona.wireId));
            assertTrue(Persona.isKnown(persona.wireId));
        }
        assertEquals(Persona.values().length, seen.size());
    }

    @Test
    public void unknownValuesFallBackToTheDefault() {
        for (String unknown : new String[] {
                null,
                "",
                "   ",
                "Default",
                "hand_holding",
                "future-persona",
                " default",
        }) {
            assertEquals("an unknown stored value must read as the default: <" + unknown + ">",
                    Persona.DEFAULT, Persona.fromWireId(unknown));
            assertFalse("an unknown value is not known: <" + unknown + ">",
                    Persona.isKnown(unknown));
        }
    }

    @Test
    public void onlyTheDefaultPersonaWearsTheDefaultIdentity() {
        for (Persona persona : Persona.values()) {
            assertEquals(persona == Persona.DEFAULT, persona.isDefault());
        }
    }
}
