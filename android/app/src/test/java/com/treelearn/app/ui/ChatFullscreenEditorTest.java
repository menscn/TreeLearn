package com.treelearn.app.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.treelearn.core.domain.GenerationModel;

import org.junit.Test;

import java.util.Collections;

/**
 * T-077: the fullscreen editor (073 visual spec §5.1, matrix 3.7).
 *
 * <p>Three behaviours are pinned here, each one a requirement rather than a rendering choice:
 *
 * <ul>
 *   <li><strong>The entry key greys out exactly when the composer cannot help.</strong> An empty
 *       draft, an archived branch and a missing key each disable it; the learner's words enable it.
 *       The judgement is computed from the value, so the view cannot disagree with the state.
 *   <li><strong>Leaving the overlay keeps the draft, whichever way it is left.</strong> Done,
 *       cancel and the system back all land on the one close action, and that action saves and
 *       closes - it never clears, because a draft has no destructive act.
 *   <li><strong>The overlay's edits ride the same write path as the composer's.</strong> Every
 *       keystroke the overlay makes is an {@code onDraftChanged}, so the store always holds the
 *       whole composer and a rebuilt presenter restores what the overlay wrote.
 * </ul>
 */
public final class ChatFullscreenEditorTest {

    private static final CopyLabels LABELS = CopyLabels.of("思考过程", "最终回答");

    private final ChatTestDoubles.RecordingChatSink sink = new ChatTestDoubles.RecordingChatSink();
    private final ChatTestDoubles.FakeChatData data = new ChatTestDoubles.FakeChatData();
    private final ChatTestDoubles.FakeChatCommands commands = new ChatTestDoubles.FakeChatCommands();
    private final TestDoubles.FakeKeyVault key = new TestDoubles.FakeKeyVault(true);
    private final TestDoubles.FakeSettingsStore settings = new TestDoubles.FakeSettingsStore();
    private final ChatComposerInputTest.FakeDraftStore drafts = new ChatComposerInputTest.FakeDraftStore();

    private ChatPresenter presenter() {
        return new ChatPresenter(sink, new TestDoubles.InlineUi(), new TestDoubles.InlineExecutor(),
                key, data, commands, settings, LABELS, ChatTestDoubles.BRANCH,
                new ChatTestDoubles.NoPositionStore(), drafts);
    }

    // ------------------------------------------------------------------
    // 1. The entry key's availability (073 §5.1).
    // ------------------------------------------------------------------

    @Test
    public void anEmptyDraftGreysTheEntryOut() {
        assertFalse(initial().fullscreenEditEntryAvailable());
    }

    @Test
    public void typedWordsEnableTheEntry() {
        assertTrue(initial().withDraft("why").fullscreenEditEntryAvailable());
    }

    @Test
    public void anArchivedBranchGreysTheEntryOut() {
        assertFalse(initial().withArchived(true).withDraft("why")
                .fullscreenEditEntryAvailable());
    }

    @Test
    public void aMissingKeyGreysTheEntryOut() {
        assertFalse(initial().withKeyConfigured(false).withDraft("why")
                .fullscreenEditEntryAvailable());
    }

    // ------------------------------------------------------------------
    // 2. Leaving the overlay saves and closes (073 §5.1).
    // ------------------------------------------------------------------

    @Test
    public void theOverlayIsAClosedByDefaultAndReadsAsAnOpenSnapshot() {
        assertFalse(initial().fullscreenEditing);
        assertTrue("an opened overlay is a read-only flag on the state, nothing more",
                initial().withDraft("why").withFullscreenEditing(true).fullscreenEditing);
    }

    @Test
    public void openingThenClosingKeepsTheDraftAndTheStoredRecord() {
        data.snapshot = ChatTestDoubles.snapshot(Collections.<ChatMessage>emptyList());
        ChatPresenter presenter = presenter();
        presenter.onDraftChanged("a half typed question");
        presenter.onFullscreenEditOpened();
        assertTrue(sink.last().fullscreenEditing);

        presenter.onFullscreenEditClosed();

        assertFalse("closing - by done, by cancel, by system back - is one action",
                sink.last().fullscreenEditing);
        assertEquals("the draft outlives the close", "a half typed question",
                sink.last().draft);
        assertEquals("the store keeps what the keystrokes saved", "a half typed question",
                drafts.load(ChatTestDoubles.BRANCH).text);
        assertFalse("a close never clears; only an accepted send may",
                drafts.cleared);
    }

    @Test
    public void theFullscreenFlagTravelsThroughTheComposerReads() {
        assertTrue("a read that lands while the overlay is open cannot close it behind the "
                        + "learner's back",
                initial().withFullscreenEditing(true).withDraft("grew").fullscreenEditing);
    }

    // ------------------------------------------------------------------
    // 3. The overlay's edits write through (073 §5.1).
    // ------------------------------------------------------------------

    @Test
    public void anOverlayKeystrokeLandsInTheStoreWhileTheOverlayIsOpen() {
        data.snapshot = ChatTestDoubles.snapshot(Collections.<ChatMessage>emptyList());
        ChatPresenter presenter = presenter();
        presenter.onFullscreenEditOpened();
        assertTrue(sink.last().fullscreenEditing);

        presenter.onDraftChanged("typed on the big box");

        assertEquals("the overlay writes through the composer's one path",
                "typed on the big box", drafts.load(ChatTestDoubles.BRANCH).text);
        assertEquals("typed on the big box", sink.last().draft);
    }

    @Test
    public void aRebuiltPresenterRestoresWhatTheOverlayWrote() {
        drafts.save(ChatTestDoubles.BRANCH, new ChatDraftStore.ChatDraft(
                "typed on the big box", "", GenerationModel.FLASH, false));

        presenter().start();

        assertEquals("typed on the big box", sink.last().draft);
    }

    private static ChatViewState initial() {
        return ChatViewState.initial(ChatTestDoubles.BRANCH, GenerationModel.FLASH, false, true);
    }
}
