package com.treelearn.app.wiring;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.treelearn.app.ui.ChatCommandResult;
import com.treelearn.app.ui.ChatData;
import com.treelearn.app.ui.HomeViewState;
import com.treelearn.core.domain.Persona;

import org.junit.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;

/** T-2026-10-23-066: the wiring's half of the topic style - read it, and write only it. */
public final class ChatPersonaWiringTest {

    private final WiringDoubles.Key key = new WiringDoubles.Key();
    private final WiringDoubles.Chats store = new WiringDoubles.Chats();
    private final WiringDoubles.Generations generation = new WiringDoubles.Generations();

    private ChatWiring wiring() {
        return new ChatWiring(store, generation, key,
                Clock.fixed(Instant.ofEpochMilli(WiringDoubles.NOW), ZoneOffset.UTC),
                PromptLimits.budget());
    }

    @Test
    public void theReadCarriesTheTopicStyleAndItsFallbackFlag() {
        store.conversation = new ChatStore.Conversation("a branch title",
                Collections.<ChatStore.Position>emptyList(), null,
                Collections.<com.treelearn.core.domain.Message>emptyList(), Persona.LATE_NIGHT,
                false);

        ChatData.Snapshot snapshot = wiring().read(WiringDoubles.BRANCH_ID);

        assertEquals(Persona.LATE_NIGHT, snapshot.persona);
        assertFalse(snapshot.personaFellBack);
    }

    @Test
    public void aWriteReachesTheStoreAndSucceeds() {
        ChatCommandResult result = wiring().setPersona(WiringDoubles.BRANCH_ID,
                Persona.LEARNING_REPAIR);

        assertTrue(result.isAccepted());
        assertEquals(Persona.LEARNING_REPAIR, store.writtenPersona);
    }

    @Test
    public void aRefusedWriteIsReportedAsStorage() {
        store.personaWriteFails = true;

        ChatCommandResult result = wiring().setPersona(WiringDoubles.BRANCH_ID,
                Persona.STUDY_BUDDY);

        assertFalse(result.isAccepted());
        assertEquals(HomeViewState.Notice.STORAGE, result.notice());
    }
}
