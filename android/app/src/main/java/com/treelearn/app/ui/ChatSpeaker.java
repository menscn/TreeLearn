package com.treelearn.app.ui;

import com.treelearn.app.ui.UiThread;
import com.treelearn.app.voice.TtsEngine;
import java.util.ArrayList;
import java.util.List;

public final class ChatSpeaker {
    public static final int MAX_SEGMENT = 120;
    private final Sink sink;
    private final UiThread ui;
    private final TtsEngine engine;
    private State state = State.initial();
    private List<String> segments = new ArrayList<String>();
    private int segmentIndex;
    private boolean released;
    private final TtsEngine.Listener listener = new TtsEngine.Listener(){

        @Override
        public void onEngineReady() {
            ChatSpeaker.this.ui.post(() -> {
                if (ChatSpeaker.this.live()) {
                    ChatSpeaker.this.render(new State(true, ChatSpeaker.this.state.speakingMessageId, ChatSpeaker.this.state.phase));
                }
            });
        }

        @Override
        public void onEngineUnavailable() {
            ChatSpeaker.this.ui.post(() -> {
                if (ChatSpeaker.this.live()) {
                    ChatSpeaker.this.render(State.initial());
                }
            });
        }

        @Override
        public void onPlaybackDone() {
            ChatSpeaker.this.ui.post(() -> {
                if (ChatSpeaker.this.live() && ChatSpeaker.this.speaking()) {
                    ChatSpeaker.this.advance();
                }
            });
        }

        @Override
        public void onPlaybackError() {
            ChatSpeaker.this.ui.post(() -> {
                if (ChatSpeaker.this.live() && ChatSpeaker.this.speaking()) {
                    ChatSpeaker.this.render(new State(ChatSpeaker.this.state.engineReady, "", Phase.ERROR));
                }
            });
        }
    };

    public ChatSpeaker(Sink sink, UiThread ui, TtsEngine engine) {
        if (sink == null || ui == null || engine == null) {
            throw new IllegalArgumentException("the chat speaker needs all three parts");
        }
        this.sink = sink;
        this.ui = ui;
        this.engine = engine;
    }

    public void start() {
        this.engine.init(this.listener);
        this.render(this.state);
    }

    public void onSpeakPressed(String messageId, String text) {
        if (!this.live() || !this.state.engineReady || messageId == null || messageId.isEmpty()) {
            return;
        }
        if (this.state.speaking(messageId)) {
            this.stop();
            return;
        }
        this.segments = ChatSpeaker.chunk(text);
        if (this.segments.isEmpty()) {
            return;
        }
        this.segmentIndex = 0;
        this.render(new State(this.state.engineReady, messageId, Phase.SPEAKING));
        this.engine.speak(this.segments.get(0));
    }

    public void interrupt() {
        if (this.live() && this.speaking()) {
            this.stop();
        }
    }

    public void leavePage() {
        if (this.released) {
            return;
        }
        if (this.speaking()) {
            this.engine.stop();
            this.render(new State(this.state.engineReady, "", Phase.STOPPED));
        }
        this.engine.release();
        this.released = true;
    }

    private void stop() {
        this.engine.stop();
        this.render(new State(this.state.engineReady, "", Phase.STOPPED));
    }

    private void advance() {
        ++this.segmentIndex;
        if (this.segmentIndex < this.segments.size()) {
            this.engine.speak(this.segments.get(this.segmentIndex));
            return;
        }
        this.render(new State(this.state.engineReady, "", Phase.IDLE));
    }

    private boolean live() {
        return !this.released;
    }

    private boolean speaking() {
        return !this.state.speakingMessageId.isEmpty();
    }

    private void render(State next) {
        this.state = next;
        this.sink.render(next);
    }

    static String stripMarkdown(String text) {
        if (text == null) {
            return "";
        }
        return text.replaceAll("```[\\s\\S]*?```", " ").replaceAll("`([^`]*)`", "$1").replaceAll("!\\[[^]]*]\\([^)]*\\)", " ").replaceAll("\\[([^]]*)]\\([^)]*\\)", "$1").replaceAll("[#*>_~|]", " ").replaceAll("\\s+", " ").trim();
    }

    static List<String> chunk(String text) {
        ArrayList<String> read = new ArrayList<String>();
        StringBuilder current = new StringBuilder();
        for (String piece : ChatSpeaker.stripMarkdown(text).split("(?<=[\u3002\uff01\uff1f\uff1b!?])")) {
            if (piece.isEmpty()) continue;
            while (piece.length() > 120) {
                if (current.length() > 0) {
                    read.add(current.toString());
                    current.setLength(0);
                }
                read.add(piece.substring(0, 120));
                piece = piece.substring(120);
            }
            if (current.length() + piece.length() > 120) {
                read.add(current.toString());
                current.setLength(0);
            }
            current.append(piece);
        }
        if (current.length() > 0) {
            read.add(current.toString().trim());
        }
        return read;
    }

    public static final class State {
        public final boolean engineReady;
        public final String speakingMessageId;
        public final Phase phase;

        State(boolean engineReady, String speakingMessageId, Phase phase) {
            this.engineReady = engineReady;
            this.speakingMessageId = speakingMessageId == null ? "" : speakingMessageId;
            this.phase = phase;
        }

        public static State initial() {
            return new State(false, "", Phase.IDLE);
        }

        public boolean speaking(String messageId) {
            return messageId != null && messageId.equals(this.speakingMessageId);
        }
    }

    public static interface Sink {
        public void render(State var1);
    }

    public static enum Phase {
        IDLE,
        SPEAKING,
        STOPPED,
        ERROR;

    }
}

