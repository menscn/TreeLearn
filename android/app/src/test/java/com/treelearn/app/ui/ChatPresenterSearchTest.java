package com.treelearn.app.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.treelearn.app.search.web.ChatSearchStep;
import com.treelearn.app.search.web.SearchTestDoubles;
import com.treelearn.app.wiring.PromptLimits;

import org.junit.Test;

/**
 * T-092: the conversation screen's search switch and the degrade notices, as the presenter decides
 * them:
 *
 * <ul>
 *   <li>the stored switch state is rendered from the settings, so the input-bar chip and the search
 *       settings page can never disagree (the {@code SettingsStore} rule);</li>
 *   <li>[NOKEY] enabling without the selected engine's key is intercepted: the notice is shown, the
 *       settings page opens, and nothing is persisted;</li>
 *   <li>enabling with a key (and disabling, which needs no key) persists through the store and
 *       re-renders with the selected engine's label;</li>
 *   <li>an accepted send can now carry a post-send degrade notice ([OFF]/[PART]); the draft rule is
 *       untouched — an accepted send still clears it;</li>
 *   <li>a plain accepted send still renders with no notice, exactly as before.</li>
 * </ul>
 */
public class ChatPresenterSearchTest {

    private static final CopyLabels LABELS = CopyLabels.of("思考过程", "最终回答");

    private final ChatTestDoubles.RecordingChatSink sink = new ChatTestDoubles.RecordingChatSink();
    private final ChatTestDoubles.FakeChatData data = new ChatTestDoubles.FakeChatData();
    private final ChatTestDoubles.FakeChatCommands commands = new ChatTestDoubles.FakeChatCommands();
    private final TestDoubles.FakeKeyVault key = new TestDoubles.FakeKeyVault(true);
    private final TestDoubles.FakeSettingsStore settings = new TestDoubles.FakeSettingsStore();
    private final SearchTestDoubles.FakeStore searchStore = new SearchTestDoubles.FakeStore();
    private final SearchTestDoubles.FakeCredentials credentials = new SearchTestDoubles.FakeCredentials();
    private final SearchTestDoubles.FakeGateway gateway = new SearchTestDoubles.FakeGateway();

    private ChatPresenter presenter() {
        ChatSearchStep step = new ChatSearchStep(searchStore, credentials, gateway);
        return new ChatPresenter(sink, new TestDoubles.InlineUi(), Runnable::run, key, data,
                commands, settings, LABELS, ChatTestDoubles.BRANCH,
                new ChatTestDoubles.NoPositionStore(), ChatDraftStore.NOOP, null, step);
    }

    @Test
    public void theStoredSwitchStateIsRenderedFromTheSettings() {
        settings.searchEnabled = true;

        assertTrue(presenter().state().searchEnabled);
        assertEquals("智谱", presenter().state().searchEngineLabel);
    }

    @Test
    public void enablingWithoutTheSelectedEnginesKeyIsInterceptedAndJumpsToSettings() {
        searchStore.hasKey = false;

        ChatPresenter presenter = presenter();
        presenter.onSearchToggled(true);

        assertEquals(HomeViewState.Notice.SEARCH_KEY_MISSING, sink.last().notice);
        assertEquals("the [NOKEY] intercept takes the learner to the key's own page", 1,
                sink.settingsOpened);
        assertFalse("a [NOKEY] enable must not be persisted", settings.searchEnabled);
        assertFalse(sink.last().searchEnabled);
    }

    @Test
    public void enablingWithAKeyPersistsAndRendersTheEngineLabel() {
        searchStore.hasKey = true;

        ChatPresenter presenter = presenter();
        presenter.onSearchToggled(true);

        assertTrue(settings.searchEnabled);
        assertTrue(sink.last().searchEnabled);
        assertNull(sink.last().notice);
        assertEquals(0, sink.settingsOpened);
    }

    @Test
    public void disablingPersistsAndNeverAsksForAKey() {
        settings.searchEnabled = true;
        searchStore.searchEnabled = true;
        searchStore.hasKey = false;

        ChatPresenter presenter = presenter();
        presenter.onSearchToggled(false);

        assertFalse(settings.searchEnabled);
        assertFalse(sink.last().searchEnabled);
        assertEquals(0, sink.settingsOpened);
    }

    @Test
    public void anAcceptedSendCarriesTheDegradationNoticeInlineAndStillClearsTheDraft() {
        commands.sendResult = ChatCommandResult.ranWithNotice(ChatTestDoubles.OP,
                HomeViewState.Notice.SEARCH_FAILED);

        ChatPresenter presenter = presenter();
        presenter.start();
        presenter.onDraftChanged("a question");
        presenter.onSend();

        assertEquals(HomeViewState.Notice.SEARCH_FAILED, sink.last().notice);
        assertEquals("an accepted send clears the draft, notice or not", "",
                sink.last().draft);
    }

    @Test
    public void aPlainAcceptedSendStillRendersWithoutANotice() {
        commands.sendResult = ChatCommandResult.ran(ChatTestDoubles.OP);

        ChatPresenter presenter = presenter();
        presenter.start();
        presenter.onDraftChanged("a question");
        presenter.onSend();

        assertNull(sink.last().notice);
        assertEquals("", sink.last().draft);
    }

    @Test
    public void aReadKeepsTheSwitchStateLikeEveryComposerFact() {
        settings.searchEnabled = true;
        searchStore.searchEnabled = true;
        searchStore.hasKey = true;

        ChatPresenter presenter = presenter();
        presenter.start();
        presenter.onSearchToggled(true);
        presenter.onResumed();

        assertTrue("a read must not reset the chip", sink.last().searchEnabled);
    }
}
