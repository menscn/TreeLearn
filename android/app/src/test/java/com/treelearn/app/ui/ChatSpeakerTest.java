package com.treelearn.app.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.treelearn.app.ui.TestDoubles.InlineUi;
import com.treelearn.app.voice.TtsEngine;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * The conversation's speak action (T-094, SPEC-2026-09-23): the per-row朗读 state machine, the
 * new-request interruption, the two-state press, the segmented queue for long answers, the
 * engine-missing degradation, the send-time interrupt and the page-leave lifecycle that stops
 * before releasing and stays quiet afterwards — all on a fake engine, off the UI thread.
 */
public final class ChatSpeakerTest {

    private RecordingSink sink;
    private FakeEngine engine;
    private ChatSpeaker speaker;

    @Before
    public void setUp() {
        sink = new RecordingSink();
        engine = new FakeEngine();
        speaker = new ChatSpeaker(sink, new InlineUi(), engine);
        speaker.start();
        // The engine initializes before the learner presses; the degradation test turns it off.
        engine.ready();
    }

    @Test
    public void aPressSpeaksAndTurnsSpeakingForThatRow() {
        speaker.onSpeakPressed("m1", "第一句。第二句。");

        assertEquals(1, engine.utterances.size());
        assertTrue(engine.utterances.get(0).contains("第一句"));
        assertEquals("m1", sink.last().speakingMessageId);
        assertEquals(ChatSpeaker.Phase.SPEAKING, sink.last().phase);
    }

    @Test
    public void aNewRequestInterruptsTheOldOne() {
        speaker.onSpeakPressed("m1", "旧回答。");
        engine.ready();

        speaker.onSpeakPressed("m2", "新回答。");

        assertEquals(2, engine.utterances.size());
        assertTrue(engine.utterances.get(1).contains("新回答"));
        assertEquals("m2", sink.last().speakingMessageId);
    }

    @Test
    public void pressingTheSpeakingRowAgainStopsIt() {
        speaker.onSpeakPressed("m1", "回答。");

        speaker.onSpeakPressed("m1", "回答。");

        assertEquals(1, engine.stops);
        assertEquals("", sink.last().speakingMessageId);
        assertEquals(ChatSpeaker.Phase.STOPPED, sink.last().phase);
    }

    @Test
    public void aLongAnswerIsSpokenSegmentBySegment() {
        speaker.onSpeakPressed("m1", longAnswer());

        // The long answer must not be handed over in one piece: the first press speaks the
        // first segment only, and each done() advances the queue.
        assertEquals(1, engine.utterances.size());
        engine.done();
        assertEquals(2, engine.utterances.size());
        engine.done();
        assertEquals("", sink.last().speakingMessageId);
        assertEquals(ChatSpeaker.Phase.IDLE, sink.last().phase);
    }

    @Test
    public void everySegmentStaysInsideTheLengthCap() {
        for (String segment : ChatSpeaker.chunk(longAnswer())) {
            assertTrue(segment.length() <= ChatSpeaker.MAX_SEGMENT);
        }
        assertTrue(ChatSpeaker.chunk(longAnswer()).size() >= 2);
    }

    @Test
    public void chunkingStripsMarkdownMarks() {
        List<String> segments = ChatSpeaker.chunk("# 标题\n正文 *强调* `代码` [链接](https://x.y) 尾句。");

        assertEquals(1, segments.size());
        String spoken = segments.get(0);
        for (String mark : new String[]{"#", "*", "`", "[", "]", "(", ")", "https"}) {
            assertTrue("spoken text must not carry " + mark, !spoken.contains(mark));
        }
        assertTrue(spoken.contains("正文"));
        assertTrue(spoken.contains("尾句"));
    }

    @Test
    public void aPlaybackErrorTurnsErrorAndTheNextPressRetries() {
        speaker.onSpeakPressed("m1", "回答。");

        engine.error();

        assertEquals(ChatSpeaker.Phase.ERROR, sink.last().phase);
        assertEquals("", sink.last().speakingMessageId);

        speaker.onSpeakPressed("m1", "回答。");
        assertEquals(2, engine.utterances.size());
        assertEquals(ChatSpeaker.Phase.SPEAKING, sink.last().phase);
    }

    @Test
    public void withoutAReadyEngineAPressDoesNothing() {
        engine.unavailable();

        speaker.onSpeakPressed("m1", "回答。");

        assertEquals(0, engine.utterances.size());
    }

    @Test
    public void interruptOnlyStopsASpeakingRow() {
        speaker.interrupt();
        assertEquals(0, engine.stops);

        speaker.onSpeakPressed("m1", "回答。");
        speaker.interrupt();

        assertEquals(1, engine.stops);
        assertEquals("", sink.last().speakingMessageId);
    }

    @Test
    public void leavingThePageStopsThenReleasesAndGoesQuiet() {
        speaker.onSpeakPressed("m1", "回答。");

        speaker.leavePage();
        final int renderedByThePage = sink.states.size();

        engine.done();

        assertTrue(engine.stopBeforeRelease);
        assertTrue(engine.released);
        assertEquals(ChatSpeaker.Phase.STOPPED, sink.last().phase);
        assertEquals(renderedByThePage, sink.states.size());
    }

    /** A long single-spelled answer: eight 26-char sentences, 208 chars, chunked into 2 segments. */
    private static String longAnswer() {
        StringBuilder text = new StringBuilder();
        for (int i = 1; i <= 8; i++) {
            text.append("这是第").append(i)
                    .append("句足够长的回答内容，用来把正文顶过分段上限。");
        }
        return text.toString();
    }

    private static final class RecordingSink implements ChatSpeaker.Sink {

        final List<ChatSpeaker.State> states = new ArrayList<ChatSpeaker.State>();

        @Override
        public void render(ChatSpeaker.State state) {
            states.add(state);
        }

        ChatSpeaker.State last() {
            return states.get(states.size() - 1);
        }
    }

    /** The fake engine: records the calls and hands the listener back to the test to fire. */
    private static final class FakeEngine implements TtsEngine {

        Listener listener;
        final List<String> utterances = new ArrayList<String>();
        int stops;
        boolean released;
        boolean stopBeforeRelease;

        @Override
        public void init(Listener listener) {
            this.listener = listener;
        }

        @Override
        public void speak(String text) {
            utterances.add(text);
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
}
