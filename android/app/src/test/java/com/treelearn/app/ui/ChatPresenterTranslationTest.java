package com.treelearn.app.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.treelearn.core.domain.GenerationModel;
import com.treelearn.app.ui.ChatViewState.Stage;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * T-090: the translation entry, the presentation state and the immutability regression. The
 * translation is a page-session presentation fact - it rides the view state, never the message
 * table - so the judgements under test are: only a stable answer offers it; a press starts a
 * UI-state run and touches no store command (the stable answer's content cannot move); the finished
 * text arrives keyed by message id, expanded by default and foldable; a failure reads as a retryable
 * failure sentence, not as an answer; and the whole state survives a store re-read.
 */
public final class ChatPresenterTranslationTest {

    private static final CopyLabels LABELS = CopyLabels.of("思考过程", "最终回答");

    private ChatTestDoubles.FakeChatData data;
    private ChatTestDoubles.FakeChatCommands commands;
    private ChatTestDoubles.RecordingChatSink sink;
    private ChatTestDoubles.QueueingExecutor background;
    private FakeTranslator translator;
    private ChatPresenter presenter;

    /** The scriptable double standing in for the generation package's MessageTranslator. */
    private static final class FakeTranslator implements ChatPresenter.Translator {

        final List<String> sources = new ArrayList<String>();
        final List<Callback> callbacks = new ArrayList<Callback>();
        GenerationModel lastFallbackModel;

        @Override
        public void translate(GenerationModel fallbackModel, String sourceText, Callback callback) {
            lastFallbackModel = fallbackModel;
            sources.add(sourceText);
            callbacks.add(callback);
        }

        void finish(boolean ok, String translation) {
            callbacks.get(callbacks.size() - 1).onFinished(ok, translation);
        }
    }

    @Before
    public void setUp() {
        data = new ChatTestDoubles.FakeChatData();
        commands = new ChatTestDoubles.FakeChatCommands();
        sink = new ChatTestDoubles.RecordingChatSink();
        background = new ChatTestDoubles.QueueingExecutor();
        translator = new FakeTranslator();
        data.snapshot = ChatTestDoubles.snapshot(ChatTestDoubles.messages(
                ChatTestDoubles.ask(ChatTestDoubles.M_ASK, ChatTestDoubles.QUESTION),
                ChatTestDoubles.stableAnswer(ChatTestDoubles.M_ANSWER, ChatTestDoubles.ANSWER)));
        presenter = new ChatPresenter(sink, new TestDoubles.InlineUi(), background,
                new TestDoubles.FakeKeyVault(true), data, commands,
                new TestDoubles.FakeSettingsStore(), LABELS, ChatTestDoubles.BRANCH,
                new ChatTestDoubles.NoPositionStore(), translator);
        presenter.start();
        background.drain();
    }

    @Test
    public void translatePressedStartsARunAndTouchesNoStoreCommand() {
        presenter.onTranslatePressed(ChatTestDoubles.M_ANSWER);
        background.drain();

        assertEquals(1, translator.sources.size());
        assertEquals(ChatTestDoubles.ANSWER, translator.sources.get(0));
        assertTrue(sink.last().isTranslating(ChatTestDoubles.M_ANSWER));
        assertFalse(commands.called("send"));
        assertFalse(commands.called("retry"));
        assertFalse(commands.called("reset"));
    }

    @Test
    public void finishedTranslationShowsExpandedAndKeepsTheAnswerUntouched() {
        presenter.onTranslatePressed(ChatTestDoubles.M_ANSWER);
        background.drain();
        translator.finish(true, "注意力机制按根号 d 缩放。");

        ChatViewState state = sink.last();
        assertEquals("注意力机制按根号 d 缩放。", state.translationText(ChatTestDoubles.M_ANSWER));
        assertTrue(state.translationExpanded(ChatTestDoubles.M_ANSWER));
        assertFalse(state.isTranslating(ChatTestDoubles.M_ANSWER));
        ChatMessage answer = state.message(ChatTestDoubles.M_ANSWER);
        assertEquals(ChatTestDoubles.ANSWER, answer.text);
        assertEquals(ChatMessage.Status.STABLE, answer.status);
    }

    @Test
    public void tapTogglesTheFoldAndTheAnswerTextNeverMoves() {
        presenter.onTranslatePressed(ChatTestDoubles.M_ANSWER);
        background.drain();
        translator.finish(true, "译文甲");
        presenter.onTranslationToggled(ChatTestDoubles.M_ANSWER);
        assertFalse(sink.last().translationExpanded(ChatTestDoubles.M_ANSWER));
        presenter.onTranslationToggled(ChatTestDoubles.M_ANSWER);
        assertTrue(sink.last().translationExpanded(ChatTestDoubles.M_ANSWER));

        ChatMessage answer = sink.last().message(ChatTestDoubles.M_ANSWER);
        assertEquals(ChatTestDoubles.ANSWER, answer.text);
    }

    @Test
    public void failedTranslationReadsAsRetryableAndNeverAsAnAnswer() {
        presenter.onTranslatePressed(ChatTestDoubles.M_ANSWER);
        background.drain();
        translator.finish(false, "");

        ChatViewState state = sink.last();
        assertTrue(state.translationFailed(ChatTestDoubles.M_ANSWER));
        assertNull(state.translationText(ChatTestDoubles.M_ANSWER));
        assertFalse(state.isTranslating(ChatTestDoubles.M_ANSWER));
    }

    @Test
    public void aSecondPressWhileRunningIsIgnored() {
        presenter.onTranslatePressed(ChatTestDoubles.M_ANSWER);
        background.drain();
        presenter.onTranslatePressed(ChatTestDoubles.M_ANSWER);
        background.drain();
        assertEquals(1, translator.sources.size());
    }

    @Test
    public void retranslatingAfterAFailureAsksAgain() {
        presenter.onTranslatePressed(ChatTestDoubles.M_ANSWER);
        background.drain();
        translator.finish(false, "");
        presenter.onTranslatePressed(ChatTestDoubles.M_ANSWER);
        background.drain();
        assertEquals(2, translator.sources.size());
        assertTrue(sink.last().isTranslating(ChatTestDoubles.M_ANSWER));
    }

    @Test
    public void aGeneratingAnswerOffersNoTranslation() {
        data.snapshot = ChatTestDoubles.snapshot(ChatTestDoubles.messages(
                ChatTestDoubles.formingReasoning(ChatTestDoubles.M_ANSWER, ChatTestDoubles.REASONING)));
        presenter.onResumed();
        background.drain();

        presenter.onTranslatePressed(ChatTestDoubles.M_ANSWER);
        background.drain();
        assertEquals(0, translator.sources.size());
        assertFalse(sink.last().translationVisible(ChatTestDoubles.M_ANSWER));
    }

    @Test
    public void translationSurvivesAStoreRereadWithTheStableAnswerIntact() {
        presenter.onTranslatePressed(ChatTestDoubles.M_ANSWER);
        background.drain();
        translator.finish(true, "译文存续");
        // A read rebuilds the message list; the presentation fact must travel through it, and the
        // answer must come back byte for byte.
        data.snapshot = ChatTestDoubles.snapshot(ChatTestDoubles.messages(
                ChatTestDoubles.ask(ChatTestDoubles.M_ASK, ChatTestDoubles.QUESTION),
                ChatTestDoubles.stableAnswer(ChatTestDoubles.M_ANSWER, ChatTestDoubles.ANSWER)));
        presenter.onResumed();
        background.drain();

        ChatViewState state = sink.last();
        assertEquals("译文存续", state.translationText(ChatTestDoubles.M_ANSWER));
        assertEquals(ChatTestDoubles.ANSWER, state.message(ChatTestDoubles.M_ANSWER).text);
    }

    @Test
    public void noExtraNoticeOrStageChangeIsRaisedByTheTranslationPath() {
        presenter.onTranslatePressed(ChatTestDoubles.M_ANSWER);
        background.drain();
        translator.finish(true, "译文乙");
        assertNull(sink.last().notice);
        assertEquals(Stage.STABLE, sink.last().stage);
    }
}
