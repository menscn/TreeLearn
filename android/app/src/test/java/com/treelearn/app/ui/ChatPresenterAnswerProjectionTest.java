package com.treelearn.app.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.treelearn.core.domain.GenerationModel;
import com.treelearn.app.voice.TtsEngine;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ChatPresenterAnswerProjectionTest {

    private static final String FRAMED = "<treelearn_reasoning>private material"
            + "</treelearn_reasoning><treelearn_answer>visible result"
            + "</treelearn_answer>";
    private static final CopyLabels LABELS = CopyLabels.of("思考过程", "最终回答");

    private static final class TranslatorCapture implements ChatPresenter.Translator {

        String source;

        @Override
        public void translate(GenerationModel model, String text, Callback callback) {
            source = text;
        }
    }

    private static final class TtsCapture implements TtsEngine {

        final List<String> spoken = new ArrayList<String>();

        @Override
        public void init(Listener listener) {
            listener.onEngineReady();
        }

        @Override
        public void speak(String text) {
            spoken.add(text);
        }

        @Override
        public void stop() {
        }

        @Override
        public void release() {
        }
    }

    @Test
    public void translationReceivesOnlyTheAnswerProjection() {
        TranslatorCapture translator = new TranslatorCapture();
        ChatPresenter presenter = presenter(FRAMED, false, translator, null);

        presenter.onTranslatePressed(ChatTestDoubles.M_ANSWER);

        assertEquals("visible result", translator.source);
    }

    @Test
    public void speechReceivesOnlyTheAnswerProjection() {
        TtsCapture engine = new TtsCapture();
        ChatSpeaker speaker = new ChatSpeaker(state -> { }, new TestDoubles.InlineUi(), engine);
        speaker.start();
        ChatPresenter presenter = presenter(FRAMED, false, null, speaker);

        presenter.onSpeakPressed(ChatTestDoubles.M_ANSWER);

        assertEquals(Collections.singletonList("visible result"), engine.spoken);
    }

    @Test
    public void wholeAnswerCopyDoesNotRevealFramedReasoningWhenThinkingIsOff() {
        ChatTestDoubles.RecordingChatSink sink = new ChatTestDoubles.RecordingChatSink();
        ChatPresenter presenter = presenter(FRAMED, false, null, null, sink);

        presenter.onCopyPressed(ChatTestDoubles.M_ANSWER);

        assertEquals(Collections.singletonList("visible result"), sink.copied);
    }

    @Test
    public void reasoningOnlyEnvelopeCanBeExpandedByPresenter() {
        String reasoningOnly = "<treelearn_reasoning>private material"
                + "</treelearn_reasoning>";
        ChatPresenter presenter = presenter(reasoningOnly, true, null, null);

        presenter.onReasoningToggled(ChatTestDoubles.M_ANSWER);

        assertTrue(presenter.state().isReasoningExpanded(ChatTestDoubles.M_ANSWER));
    }

    private ChatPresenter presenter(String text, boolean thinking,
            ChatPresenter.Translator translator, ChatSpeaker speaker) {
        return presenter(text, thinking, translator, speaker,
                new ChatTestDoubles.RecordingChatSink());
    }

    private ChatPresenter presenter(String text, boolean thinking,
            ChatPresenter.Translator translator, ChatSpeaker speaker,
            ChatTestDoubles.RecordingChatSink sink) {
        ChatTestDoubles.FakeChatData data = new ChatTestDoubles.FakeChatData();
        data.snapshot = ChatTestDoubles.read("Transformer attention",
                ChatTestDoubles.messages(
                        ChatTestDoubles.ask(ChatTestDoubles.M_ASK, ChatTestDoubles.QUESTION),
                        ChatMessage.ai(ChatTestDoubles.M_ANSWER, text, "",
                                ChatMessage.Status.STABLE, ChatMessage.Phase.ANSWER,
                                ChatTestDoubles.REV_1, 1, 0, false, 1L)),
                Collections.emptyList(), "");
        TestDoubles.FakeSettingsStore settings = new TestDoubles.FakeSettingsStore();
        settings.thinking = thinking;
        TestDoubles.InlineExecutor background = new TestDoubles.InlineExecutor();
        ChatPresenter presenter = new ChatPresenter(sink, new TestDoubles.InlineUi(), background,
                new TestDoubles.FakeKeyVault(true), data, new ChatTestDoubles.FakeChatCommands(),
                settings, LABELS, ChatTestDoubles.BRANCH,
                new ChatTestDoubles.NoPositionStore(), ChatDraftStore.NOOP, translator, null,
                speaker);
        presenter.start();
        return presenter;
    }
}
