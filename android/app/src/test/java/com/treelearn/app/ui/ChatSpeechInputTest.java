package com.treelearn.app.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * T-095: the speech input's decisions, tested without a View. The append rule is SPEC-081's
 * 「追加进草稿、不直接发送」; the policy owns the key's visibility and the permission-denied
 * degradation (灰键 + 一次性开启引导).
 */
public class ChatSpeechInputTest {

    // ---- presenter: recognized text lands in the draft, never over it ----

    @Test
    public void recognizedTextAppendsToAnExistingDraft() {
        ChatTestDoubles.RecordingChatSink sink = new ChatTestDoubles.RecordingChatSink();
        ChatPresenter presenter = presenterWithDraft(sink, "已有草稿");

        presenter.onSpeechRecognized("新问题");

        assertEquals("已有草稿新问题", sink.last().draft);
    }

    @Test
    public void recognizedTextBecomesTheDraftWhenTheDraftIsEmpty() {
        ChatTestDoubles.RecordingChatSink sink = new ChatTestDoubles.RecordingChatSink();
        ChatPresenter presenter = presenterWithDraft(sink, "");

        presenter.onSpeechRecognized("新问题");

        assertEquals("新问题", sink.last().draft);
    }

    @Test
    public void aBlankRecognitionLeavesTheDraftUntouched() {
        // SPEC-081 [OFF]: complete silence falls back quietly - no error, no draft damage.
        ChatTestDoubles.RecordingChatSink sink = new ChatTestDoubles.RecordingChatSink();
        ChatPresenter presenter = presenterWithDraft(sink, "已有草稿");

        presenter.onSpeechRecognized("   ");

        assertEquals("已有草稿", sink.last().draft);
    }

    // ---- policy: key visibility and the permission-denied degradation ----

    @Test
    public void theKeyIsNotRenderedWhenRecognitionIsUnavailable() {
        assertFalse(SpeechInputPolicy.keyVisible(false));
        assertTrue(SpeechInputPolicy.keyVisible(true));
    }

    @Test
    public void aDeniedPermissionGreysTheKeyAndAsksOnceForGuidance() {
        SpeechInputPolicy policy = new SpeechInputPolicy();
        assertTrue("a fresh key is enabled", policy.enabled());

        policy.markPermissionDenied();
        assertFalse("the denied key greys out", policy.enabled());
        assertTrue("the first tap asks for guidance", policy.guidanceOnTap());
        assertFalse("the second tap stays quiet", policy.guidanceOnTap());

        policy.markPermissionGranted();
        assertTrue("a later grant re-enables the key", policy.enabled());
    }

    // ---- fixture ----

    /** A started presenter whose composer carries the given draft; everything else is default. */
    private static ChatPresenter presenterWithDraft(ChatTestDoubles.RecordingChatSink sink,
            String draft) {
        ChatPresenter presenter = new ChatPresenter(sink, new TestDoubles.InlineUi(),
                new TestDoubles.InlineExecutor(), new TestDoubles.FakeKeyVault(true),
                new ChatTestDoubles.FakeChatData(), new ChatTestDoubles.FakeChatCommands(),
                new TestDoubles.FakeSettingsStore(), CopyLabels.of("思考过程", "最终回答"),
                ChatTestDoubles.BRANCH, new ChatTestDoubles.NoPositionStore(),
                ChatDraftStore.NOOP, null, null);
        presenter.start();
        presenter.onDraftChanged(draft);
        return presenter;
    }
}
