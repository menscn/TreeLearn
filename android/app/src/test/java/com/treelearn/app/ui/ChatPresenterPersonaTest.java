package com.treelearn.app.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.treelearn.core.domain.Persona;

import org.junit.Test;

import java.util.Collections;

/**
 * T-2026-10-23-066: the conversation's answer style is the topic's, read through the snapshot and
 * written back through one narrow command.
 *
 * <p>The assertions pin the four decisions the screen makes: the chip shows what the read carried, a
 * choice is a write rather than a session flag, a refused write puts the old style back instead of
 * showing a value the store does not hold, and an unknown stored value is announced once per visit.
 */
public final class ChatPresenterPersonaTest {

    private static final CopyLabels LABELS = CopyLabels.of("思考过程", "最终回答");

    private final ChatTestDoubles.RecordingChatSink sink = new ChatTestDoubles.RecordingChatSink();
    private final ChatTestDoubles.FakeChatData data = new ChatTestDoubles.FakeChatData();
    private final ChatTestDoubles.FakeChatCommands commands = new ChatTestDoubles.FakeChatCommands();
    private final TestDoubles.FakeKeyVault key = new TestDoubles.FakeKeyVault(true);
    private final TestDoubles.FakeSettingsStore settings = new TestDoubles.FakeSettingsStore();

    @Test
    public void theReadCarriesTheTopicStyleIntoTheChip() {
        data.snapshot = withPersona(Persona.STUDY_BUDDY, false);

        presenter().start();

        assertEquals(Persona.STUDY_BUDDY, sink.last().persona);
        assertEquals("a known row is never announced", 0, sink.personaFallbacks);
    }

    @Test
    public void aReadWithoutAStyleStaysOnTheDefault() {
        presenter().start();

        assertEquals(Persona.DEFAULT, sink.last().persona);
    }

    @Test
    public void anUnknownStoredStyleFallsBackAndIsAnnouncedOncePerVisit() {
        data.snapshot = withPersona(Persona.DEFAULT, true);

        ChatPresenter presenter = presenter();
        presenter.start();
        assertEquals("the default is what the chip shows", Persona.DEFAULT, sink.last().persona);
        assertEquals("the bad row is told", 1, sink.personaFallbacks);

        presenter.onResumed();
        assertEquals("the next read of the same row must not repeat it", 1, sink.personaFallbacks);
    }

    @Test
    public void choosingAStyleWritesTheTopicAndTellsTheLearner() {
        ChatPresenter presenter = presenter();
        presenter.start();

        presenter.onPersonaSelected(Persona.STRICT_MENTOR);

        assertTrue("the choice reaches the store", commands.called("setPersona"));
        assertEquals(Persona.STRICT_MENTOR, commands.lastPersona);
        assertEquals(Persona.STRICT_MENTOR, sink.last().persona);
        assertEquals("one confirmation toast", Persona.STRICT_MENTOR,
                sink.personaSwitches.get(0));
    }

    @Test
    public void choosingTheCurrentStyleWritesNothing() {
        ChatPresenter presenter = presenter();
        presenter.start();

        presenter.onPersonaSelected(Persona.DEFAULT);

        assertFalse("the store must not see a no-op", commands.called("setPersona"));
        assertTrue(sink.personaSwitches.isEmpty());
    }

    @Test
    public void aRefusedWritePutsTheOldStyleBackAndShowsStorage() {
        data.snapshot = withPersona(Persona.LATE_NIGHT, false);
        commands.setPersonaResult = ChatCommandResult.refused(HomeViewState.Notice.STORAGE);

        ChatPresenter presenter = presenter();
        presenter.start();
        presenter.onPersonaSelected(Persona.SARCASTIC_FRIEND);

        assertEquals("the chip must not show a value the store refused",
                Persona.LATE_NIGHT, sink.last().persona);
        assertEquals(HomeViewState.Notice.STORAGE, sink.last().notice);
        assertTrue("a refused write is not announced as a switch", sink.personaSwitches.isEmpty());
    }

    @Test
    public void theIntroRoadIsNavigationAndNeverAWrite() {
        ChatPresenter presenter = presenter();
        presenter.start();

        presenter.onPersonaIntroPressed();

        assertEquals("the screen is asked to open the introduction once", 1,
                sink.personaIntrosOpened);
        assertFalse("reading the styles must not touch the stored one",
                commands.called("setPersona"));
        assertTrue("no style change is announced", sink.personaSwitches.isEmpty());
    }

    private ChatPresenter presenter() {
        return new ChatPresenter(sink, new TestDoubles.InlineUi(),
                new TestDoubles.InlineExecutor(), key, data, commands, settings, LABELS,
                ChatTestDoubles.BRANCH, new ChatTestDoubles.NoPositionStore());
    }

    private static ChatData.Snapshot withPersona(Persona persona, boolean fellBack) {
        return ChatData.Snapshot.of("Transformer attention",
                Collections.<ChatMessage>emptyList(), Collections.<Recommendation>emptyList(), "",
                null, Collections.<com.treelearn.core.domain.Message>emptyList(), null, null,
                persona, fellBack);
    }
}
