package com.treelearn.app.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.treelearn.core.domain.GenerationModel;

import org.junit.Test;

import java.util.Collections;

/**
 * T-103: the chat model chip reads the chat-role assignment chain (T-088) instead of the legacy
 * global tier, a fallback lands as a one-time notice, and the chip's own selection stays in the
 * session — it never writes through to the global setting.
 */
public final class ChatModelChipTest {

    @Test
    public void startResolvesTheChatRoleAndSwapsTheTier() {
        TestDoubles.FakeSettingsStore settings = new TestDoubles.FakeSettingsStore();
        ChatTestDoubles.RecordingChatSink sink = new ChatTestDoubles.RecordingChatSink();
        ChatPresenter presenter = presenter(settings, sink,
                assignment("p1", "deepseek-v4-pro",
                        ModelSettingsPresenter.Fallback.NONE));

        presenter.start();

        assertEquals("the resolved chat-role model is the chip's model",
                GenerationModel.PRO, sink.last().model);
        assertNull("a clean resolution tells nothing", sink.last().notice);
    }

    @Test
    public void aFallbackLandsAsAOneTimeNotice() {
        TestDoubles.FakeSettingsStore settings = new TestDoubles.FakeSettingsStore();
        ChatTestDoubles.RecordingChatSink sink = new ChatTestDoubles.RecordingChatSink();
        ChatPresenter presenter = presenter(settings, sink,
                assignment(null, null, ModelSettingsPresenter.Fallback.BUILTIN));

        presenter.start();

        assertEquals("the fallback is told exactly on the entry render",
                HomeViewState.Notice.CHAT_MODEL_FALLBACK, sink.last().notice);

        presenter.onDraftChanged("a new draft");
        assertNull("the notice must not repeat on the next render", sink.last().notice);
    }

    @Test
    public void anEmptyAssignmentGuidesWithoutBreakingTheConversation() {
        TestDoubles.FakeSettingsStore settings = new TestDoubles.FakeSettingsStore();
        ChatTestDoubles.RecordingChatSink sink = new ChatTestDoubles.RecordingChatSink();
        ChatPresenter presenter = presenter(settings, sink,
                ModelSettingsPresenter.Assignment.EMPTY);

        presenter.start();

        assertEquals(HomeViewState.Notice.NO_MODELS, sink.last().notice);
        assertEquals("the legacy tier stays the working default",
                GenerationModel.FLASH, sink.last().model);
    }

    @Test
    public void theChipSelectionStaysInSessionAndNeverWritesThrough() {
        TestDoubles.FakeSettingsStore settings = new TestDoubles.FakeSettingsStore();
        ChatTestDoubles.RecordingChatSink sink = new ChatTestDoubles.RecordingChatSink();
        ChatPresenter presenter = presenter(settings, sink,
                assignment("p1", "deepseek-flash", ModelSettingsPresenter.Fallback.NONE));
        presenter.start();

        presenter.onModelSelected(GenerationModel.PRO);

        assertEquals("the chip's model is the session's model",
                GenerationModel.PRO, sink.last().model);
        assertEquals("the global setting is untouched by the chip",
                GenerationModel.FLASH, settings.model);

        presenter.start();
        assertEquals("re-entering the conversation falls back to the resolved role",
                GenerationModel.FLASH, sink.last().model);
    }

    private static final com.treelearn.app.ui.CopyLabels COPY_LABELS =
            com.treelearn.app.ui.CopyLabels.of("思考过程", "最终回答");

    private ChatPresenter presenter(TestDoubles.FakeSettingsStore settings,
            ChatTestDoubles.RecordingChatSink sink, ModelSettingsPresenter.Assignment assignment) {
        return new ChatPresenter(sink, new TestDoubles.InlineUi(),
                new TestDoubles.InlineExecutor(), key(), new ChatTestDoubles.FakeChatData(),
                new ChatTestDoubles.FakeChatCommands(), settings, COPY_LABELS,
                ChatTestDoubles.BRANCH, new ChatTestDoubles.NoPositionStore(),
                () -> assignment);
    }

    private TestDoubles.FakeKeyVault key() {
        return new TestDoubles.FakeKeyVault(true);
    }

    private static ModelSettingsPresenter.Assignment assignment(String providerId, String modelId,
            ModelSettingsPresenter.Fallback fallback) {
        return new ModelSettingsPresenter.Assignment(providerId, modelId, fallback);
    }
}
