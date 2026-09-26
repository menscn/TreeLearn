package com.treelearn.app.ui;

import com.treelearn.app.ui.UiThread;
import com.treelearn.app.voice.TtsEngine;
import java.util.concurrent.Executor;

public final class VoiceSettingsPresenter {
    public static final String SYSTEM_PROVIDER = "system";
    private final Sink sink;
    private final UiThread ui;
    private final Executor background;
    private final Store store;
    private final TtsEngine engine;
    private final String auditionText;
    private State state;
    private boolean released;
    private final TtsEngine.Listener listener = new TtsEngine.Listener(){

        @Override
        public void onEngineReady() {
            VoiceSettingsPresenter.this.ui.post(() -> {
                if (VoiceSettingsPresenter.this.live()) {
                    VoiceSettingsPresenter.this.render(new State(VoiceSettingsPresenter.this.state.provider, true, VoiceSettingsPresenter.this.state.phase, Notice.NONE));
                }
            });
        }

        @Override
        public void onEngineUnavailable() {
            VoiceSettingsPresenter.this.ui.post(() -> {
                if (VoiceSettingsPresenter.this.live()) {
                    VoiceSettingsPresenter.this.render(new State(VoiceSettingsPresenter.this.state.provider, false, VoiceSettingsPresenter.this.state.phase, Notice.NO_ENGINE));
                }
            });
        }

        @Override
        public void onPlaybackDone() {
            VoiceSettingsPresenter.this.ui.post(() -> {
                if (VoiceSettingsPresenter.this.live() && VoiceSettingsPresenter.this.state.phase == Phase.SPEAKING) {
                    VoiceSettingsPresenter.this.render(new State(VoiceSettingsPresenter.this.state.provider, VoiceSettingsPresenter.this.state.engineReady, Phase.IDLE, Notice.NONE));
                }
            });
        }

        @Override
        public void onPlaybackError() {
            VoiceSettingsPresenter.this.ui.post(() -> {
                if (VoiceSettingsPresenter.this.live() && VoiceSettingsPresenter.this.state.phase == Phase.SPEAKING) {
                    VoiceSettingsPresenter.this.render(new State(VoiceSettingsPresenter.this.state.provider, VoiceSettingsPresenter.this.state.engineReady, Phase.ERROR, Notice.PLAYBACK_ERROR));
                }
            });
        }
    };

    public VoiceSettingsPresenter(Sink sink, UiThread ui, Executor background, Store store, TtsEngine engine, String auditionText) {
        if (sink == null || ui == null || background == null || store == null || engine == null || auditionText == null) {
            throw new IllegalArgumentException("the voice settings presenter needs all six parts");
        }
        this.sink = sink;
        this.ui = ui;
        this.background = background;
        this.store = store;
        this.engine = engine;
        this.auditionText = auditionText;
    }

    public void start() {
        this.background.execute(() -> {
            String provider = VoiceSettingsPresenter.normalize(this.store.provider());
            this.ui.post(() -> {
                if (this.released) {
                    return;
                }
                this.engine.init(this.listener);
                this.render(new State(provider, false, Phase.IDLE, Notice.NONE));
            });
        });
    }

    public void onAuditionPressed() {
        if (this.state == null || this.released || !this.state.engineReady) {
            return;
        }
        if (this.state.phase == Phase.SPEAKING) {
            this.engine.stop();
            this.render(new State(this.state.provider, this.state.engineReady, Phase.STOPPED, this.state.notice));
            return;
        }
        this.engine.speak(this.auditionText);
        this.render(new State(this.state.provider, this.state.engineReady, Phase.SPEAKING, Notice.NONE));
    }

    public void onProviderSelected(String providerId) {
        if (providerId == null || !SYSTEM_PROVIDER.equals(providerId)) {
            return;
        }
        this.background.execute(() -> {
            this.store.setProvider(providerId);
            this.ui.post(() -> {
                if (this.live()) {
                    this.render(new State(providerId, this.state.engineReady, this.state.phase, this.state.notice));
                }
            });
        });
    }

    public void leavePage() {
        if (this.released || this.state == null) {
            return;
        }
        if (this.state.phase == Phase.SPEAKING) {
            this.engine.stop();
            this.render(new State(this.state.provider, this.state.engineReady, Phase.STOPPED, this.state.notice));
        }
        this.engine.release();
        this.released = true;
    }

    private boolean live() {
        return !this.released && this.state != null;
    }

    private void render(State next) {
        this.state = next;
        this.sink.render(next);
    }

    private static String normalize(String stored) {
        return SYSTEM_PROVIDER.equals(stored) ? stored : SYSTEM_PROVIDER;
    }

    public static interface Sink {
        public void render(State var1);
    }

    public static interface Store {
        public String provider();

        public void setProvider(String var1);
    }

    public static final class State {
        public final String provider;
        public final boolean engineReady;
        public final Phase phase;
        public final Notice notice;

        State(String provider, boolean engineReady, Phase phase, Notice notice) {
            this.provider = provider;
            this.engineReady = engineReady;
            this.phase = phase;
            this.notice = notice;
        }
    }

    public static enum Phase {
        IDLE,
        SPEAKING,
        STOPPED,
        ERROR;

    }

    public static enum Notice {
        NONE,
        NO_ENGINE,
        PLAYBACK_ERROR;

    }
}

