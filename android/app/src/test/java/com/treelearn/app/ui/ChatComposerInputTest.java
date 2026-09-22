package com.treelearn.app.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.treelearn.core.domain.Branch;
import com.treelearn.core.domain.GenerationModel;

import org.junit.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * T-076: the composer's input capabilities (073 visual spec §5, matrix 3.1/3.2/3.3/3.4/4.1).
 *
 * <p>Three behaviours are pinned here, each one a requirement rather than a rendering choice:
 *
 * <ul>
 *   <li><strong>The send key has three states on one control</strong> - disabled, send, stop - and
 *       the attempt running wins over everything, a missing key or an archived branch disables, and
 *       the learner's words enable. The state is computed from the value, so the screen cannot
 *       disagree with the presenter about which one is showing.
 *   <li><strong>The draft survives a process rebuild.</strong> Every keystroke lands in the store,
 *       an accepted send clears it, and a rebuilt presenter restores the words and the quote. The
 *       stored record carries draft, quote and composer preferences only - never key material,
 *       never a message body.
 *   <li><strong>An archived branch is read-only, and keeps what the learner had.</strong> The
 *       composer disables with its draft and quote intact; a send attempt is refused before any
 *       command runs.
 * </ul>
 */
public final class ChatComposerInputTest {

    private static final CopyLabels LABELS = CopyLabels.of("思考过程", "最终回答");

    private final ChatTestDoubles.RecordingChatSink sink = new ChatTestDoubles.RecordingChatSink();
    private final ChatTestDoubles.FakeChatData data = new ChatTestDoubles.FakeChatData();
    private final ChatTestDoubles.FakeChatCommands commands = new ChatTestDoubles.FakeChatCommands();
    private final TestDoubles.FakeKeyVault key = new TestDoubles.FakeKeyVault(true);
    private final TestDoubles.FakeSettingsStore settings = new TestDoubles.FakeSettingsStore();
    private final FakeDraftStore drafts = new FakeDraftStore();

    private ChatPresenter presenter() {
        return new ChatPresenter(sink, new TestDoubles.InlineUi(), new TestDoubles.InlineExecutor(),
                key, data, commands, settings, LABELS, ChatTestDoubles.BRANCH,
                new ChatTestDoubles.NoPositionStore(), drafts);
    }

    private ChatPresenter deferredPresenter(ChatTestDoubles.QueueingExecutor deferred) {
        return new ChatPresenter(sink, new TestDoubles.InlineUi(), deferred, key, data, commands,
                settings, LABELS, ChatTestDoubles.BRANCH, new ChatTestDoubles.NoPositionStore(),
                drafts);
    }

    // ------------------------------------------------------------------
    // 1. The send key's three states (073 §5).
    // ------------------------------------------------------------------

    @Test
    public void anEmptyDraftShowsTheDisabledState() {
        assertEquals(ChatViewState.SendState.DISABLED,
                initial().sendState());
    }

    @Test
    public void typedWordsWithAKeyShowTheSendState() {
        assertEquals(ChatViewState.SendState.SEND,
                initial().withDraft("why").sendState());
    }

    @Test
    public void anAttemptRunningShowsTheStopStateWhateverElseIsTrue() {
        assertEquals(ChatViewState.SendState.STOP,
                initial().withDraft("why").withSending(true).sendState());
        assertEquals(ChatViewState.SendState.STOP,
                initial().withDraft("why")
                        .withStage(ChatViewState.Stage.REASONING).sendState());
        assertEquals(ChatViewState.SendState.STOP,
                initial().withDraft("why")
                        .withStage(ChatViewState.Stage.ANSWERING).sendState());
    }

    @Test
    public void aMissingKeyDisablesTheSendKey() {
        assertEquals(ChatViewState.SendState.DISABLED,
                initial().withKeyConfigured(false).withDraft("why").sendState());
    }

    @Test
    public void anArchivedBranchDisablesTheSendKeyButKeepsTheDraft() {
        ChatViewState state = initial().withArchived(true)
                .withDraft("why").withQuote("a sentence");

        assertEquals(ChatViewState.SendState.DISABLED, state.sendState());
        assertEquals("archiving is read-only, not destructive", "why", state.draft);
        assertEquals("a sentence", state.quote);
    }

    // ------------------------------------------------------------------
    // 2. The draft survives a process rebuild (073 §5, matrix 3.2).
    // ------------------------------------------------------------------

    @Test
    public void aStoreRecordRoundTripsAllFourFields() {
        ChatDraftStore.ChatDraft written = new ChatDraftStore.ChatDraft(
                "a half typed question", "a quoted sentence", GenerationModel.PRO, true);

        drafts.save(ChatTestDoubles.BRANCH, written);
        ChatDraftStore.ChatDraft read = drafts.load(ChatTestDoubles.BRANCH);

        assertEquals(written.text, read.text);
        assertEquals(written.quote, read.quote);
        assertEquals(written.model, read.model);
        assertEquals(written.thinkingEnabled, read.thinkingEnabled);
    }

    @Test
    public void everyKeystrokeLandsInTheStoreAndAnAcceptedSendClearsIt() {
        data.snapshot = ChatTestDoubles.snapshot(Collections.<ChatMessage>emptyList());
        ChatTestDoubles.QueueingExecutor deferred = new ChatTestDoubles.QueueingExecutor();
        ChatPresenter presenter = deferredPresenter(deferred);

        presenter.onDraftChanged("a half typed question");
        assertEquals("a half typed question", drafts.lastSaved.text);

        presenter.onSend();
        deferred.drain();

        assertTrue("an accepted send is the one thing that clears the stored draft",
                drafts.cleared);
        assertEquals("", sink.last().draft);
    }

    @Test
    public void aRebuiltPresenterRestoresTheWordsAndTheQuote() {
        drafts.save(ChatTestDoubles.BRANCH, new ChatDraftStore.ChatDraft(
                "a half typed question", "a quoted sentence", GenerationModel.FLASH, false));

        presenter().start();

        assertEquals("a half typed question", sink.last().draft);
        assertEquals("a quoted sentence", sink.last().quote);
    }

    @Test
    public void aRefusedSendKeepsTheStoredDraft() {
        data.snapshot = ChatTestDoubles.snapshot(Collections.<ChatMessage>emptyList());
        ChatTestDoubles.QueueingExecutor deferred = new ChatTestDoubles.QueueingExecutor();
        ChatPresenter presenter = deferredPresenter(deferred);
        commands.sendResult = ChatCommandResult.refused(HomeViewState.Notice.RATE_LIMITED);

        presenter.onDraftChanged("a half typed question");
        presenter.onSend();
        deferred.drain();

        assertFalse("the draft outlives the refusal", drafts.cleared);
        assertEquals("a half typed question", sink.last().draft);
    }

    // ------------------------------------------------------------------
    // 3. Archived and key-less composer behaviour (073 §5, matrix 3.4).
    // ------------------------------------------------------------------

    @Test
    public void anArchivedBranchDisablesTheComposerAndRefusesTheSend() {
        Branch archived = ChatTestDoubles.childBranch("a quoted sentence");
        archived.archive(1_700_000_000_000L);
        data.snapshot = ChatTestDoubles.withBranch(
                ChatTestDoubles.messages(
                        ChatTestDoubles.ask(ChatTestDoubles.M_ASK, ChatTestDoubles.QUESTION)),
                archived, null);

        ChatPresenter presenter = presenter();
        presenter.onDraftChanged("a follow up");
        presenter.onResumed();

        assertTrue(sink.last().archived);
        assertEquals(ChatViewState.SendState.DISABLED, sink.last().sendState());
        assertEquals("the archived branch keeps the learner's draft", "a follow up",
                sink.last().draft);

        presenter.onSend();
        assertFalse("an archived branch never sends", commands.called("send"));
    }

    @Test
    public void aMissingKeyDisablesTheComposerWithoutClearingTheDraft() {
        key.configured = false;
        data.snapshot = ChatTestDoubles.snapshot(Collections.<ChatMessage>emptyList());

        ChatPresenter presenter = presenter();
        presenter.onDraftChanged("a follow up");
        presenter.onResumed();

        assertFalse(sink.last().keyConfigured);
        assertEquals(ChatViewState.SendState.DISABLED, sink.last().sendState());
        assertEquals("a follow up", sink.last().draft);
    }

    private static ChatViewState initial() {
        return ChatViewState.initial(ChatTestDoubles.BRANCH, GenerationModel.FLASH, false, true);
    }

    /** Records what the presenter asked the store to do, so the tests can judge the store's use. */
    static final class FakeDraftStore implements ChatDraftStore {

        final Map<String, ChatDraft> rows = new HashMap<String, ChatDraft>();
        ChatDraft lastSaved;
        boolean cleared;

        @Override
        public void save(String branchId, ChatDraft draft) {
            lastSaved = draft;
            rows.put(branchId, draft);
        }

        @Override
        public ChatDraft load(String branchId) {
            return rows.get(branchId);
        }

        @Override
        public void clear(String branchId) {
            cleared = true;
            rows.remove(branchId);
        }
    }
}
