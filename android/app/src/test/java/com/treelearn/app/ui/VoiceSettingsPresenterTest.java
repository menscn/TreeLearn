package com.treelearn.app.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.treelearn.app.ui.TestDoubles.InlineExecutor;
import com.treelearn.app.ui.TestDoubles.InlineUi;
import com.treelearn.app.voice.TtsEngine;

import org.junit.Before;
import org.junit.Test;

/**
 * The voice settings page's state machine (T-093, SPEC-2026-09-23): the system-TTS provider row,
 * the audition trigger and its play/stop two states, the engine-missing degradation and the
 * page-leave lifecycle — all on a fake engine, off the UI thread.
 */
public final class VoiceSettingsPresenterTest {

    private RecordingSink sink;
    private InlineExecutor background;
    private FakeEngine engine;
    private FakeStore store;
    private VoiceSettingsPresenter presenter;

    @Before
    public void setUp() {
        sink = new RecordingSink();
        background = new InlineExecutor();
        engine = new FakeEngine();
        store = new FakeStore();
        presenter = new VoiceSettingsPresenter(sink, new InlineUi(), background, store, engine,
                "试听文案");
    }

    @Test
    public void startingReadsTheProviderOffTheUiThread() {
        presenter.start();

        assertEquals(1, background.runs);
        assertEquals("system", sink.last().provider);
        assertEquals(VoiceSettingsPresenter.Phase.IDLE, sink.last().phase);
    }

    @Test
    public void anUnknownStoredProviderFallsBackToSystem() {
        store.provider = "cloud_x";

        presenter.start();

        assertEquals("system", sink.last().provider);
    }

    @Test
    public void theAuditionButtonStaysHiddenUntilTheEngineIsReady() {
        presenter.start();

        assertFalse(sink.last().engineReady);
    }

    @Test
    public void aReadyEngineShowsTheAuditionButton() {
        presenter.start();

        engine.ready();

        assertTrue(sink.last().engineReady);
        assertEquals(VoiceSettingsPresenter.Notice.NONE, sink.last().notice);
    }

    @Test
    public void anUnavailableEngineRaisesTheNoEngineDegradation() {
        presenter.start();

        engine.unavailable();

        assertFalse(sink.last().engineReady);
        assertEquals(VoiceSettingsPresenter.Notice.NO_ENGINE, sink.last().notice);
    }

    @Test
    public void pressingAuditionWithoutAnEngineDoesNothing() {
        presenter.start();
        engine.unavailable();

        presenter.onAuditionPressed();

        assertEquals(0, engine.spoken);
        assertEquals(VoiceSettingsPresenter.Notice.NO_ENGINE, sink.last().notice);
    }

    @Test
    public void pressingAuditionSpeaksAndTurnsSpeaking() {
        presenter.start();
        engine.ready();

        presenter.onAuditionPressed();

        assertEquals(1, engine.spoken);
        assertEquals("试听文案", engine.lastText);
        assertEquals(VoiceSettingsPresenter.Phase.SPEAKING, sink.last().phase);
    }

    @Test
    public void playbackDoneReturnsToIdle() {
        presenter.start();
        engine.ready();
        presenter.onAuditionPressed();

        engine.done();

        assertEquals(VoiceSettingsPresenter.Phase.IDLE, sink.last().phase);
    }

    @Test
    public void aPlaybackErrorRaisesTheErrorStateAndTheNextPressRetries() {
        presenter.start();
        engine.ready();
        presenter.onAuditionPressed();

        engine.error();

        assertEquals(VoiceSettingsPresenter.Phase.ERROR, sink.last().phase);
        assertEquals(VoiceSettingsPresenter.Notice.PLAYBACK_ERROR, sink.last().notice);

        presenter.onAuditionPressed();

        assertEquals(2, engine.spoken);
        assertEquals(VoiceSettingsPresenter.Phase.SPEAKING, sink.last().phase);
        assertEquals(VoiceSettingsPresenter.Notice.NONE, sink.last().notice);
    }

    @Test
    public void pressingStopStopsTheEngineAndTurnsStopped() {
        presenter.start();
        engine.ready();
        presenter.onAuditionPressed();

        presenter.onAuditionPressed();

        assertEquals(1, engine.stops);
        assertEquals(VoiceSettingsPresenter.Phase.STOPPED, sink.last().phase);
    }

    @Test
    public void selectingTheProviderPersistsIt() {
        presenter.start();

        presenter.onProviderSelected("system");

        assertEquals("system", store.provider);
        assertEquals("system", sink.last().provider);
    }

    @Test
    public void leavingThePageStopsThenReleasesAndGoesQuiet() {
        presenter.start();
        engine.ready();
        presenter.onAuditionPressed();

        presenter.leavePage();
        // GREEN note (T-093): the original assertion demanded an absolute count of 2 rendered
        // states for this whole sequence, which contradicts the sibling tests — startingReads…
        // requires start() to render, aReadyEngine… requires the ready callback to render, and
        // pressingAudition… requires the audition press to render. Any presenter satisfying those
        // three renders four states here (idle, ready, speaking, stopped), so the absolute count
        // was a test-authoring defect, not a weaker behavior bar: the release-quietness intent is
        // kept as the relative assertion below, and every behavioral assertion is unchanged.
        final int renderedByThePage = sink.states.size();

        engine.done();

        assertTrue(engine.stopBeforeRelease);
        assertTrue(engine.released);
        assertEquals(1, engine.stops);
        assertEquals(VoiceSettingsPresenter.Phase.STOPPED, sink.last().phase);
        assertEquals(renderedByThePage, sink.states.size());
    }

    private static final class RecordingSink implements VoiceSettingsPresenter.Sink {

        final java.util.List<VoiceSettingsPresenter.State> states =
                new java.util.ArrayList<VoiceSettingsPresenter.State>();

        @Override
        public void render(VoiceSettingsPresenter.State state) {
            states.add(state);
        }

        VoiceSettingsPresenter.State last() {
            return states.get(states.size() - 1);
        }
    }

    /** The fake engine: records the calls and hands the listener back to the test to fire. */
    private static final class FakeEngine implements TtsEngine {

        Listener listener;
        int spoken;
        int stops;
        boolean released;
        boolean stopBeforeRelease;
        String lastText;

        @Override
        public void init(Listener listener) {
            this.listener = listener;
        }

        @Override
        public void speak(String text) {
            spoken++;
            lastText = text;
        }

        @Override
        public void stop() {
            stops++;
            stopBeforeRelease = !released;
        }

        @Override
        public void release() {
            released = true;
        }

        void ready() {
            listener.onEngineReady();
        }

        void unavailable() {
            listener.onEngineUnavailable();
        }

        void done() {
            listener.onPlaybackDone();
        }

        void error() {
            listener.onPlaybackError();
        }
    }

    private static final class FakeStore implements VoiceSettingsPresenter.Store {

        String provider = "system";

        @Override
        public String provider() {
            return provider;
        }

        @Override
        public void setProvider(String providerId) {
            provider = providerId;
        }
    }
}
