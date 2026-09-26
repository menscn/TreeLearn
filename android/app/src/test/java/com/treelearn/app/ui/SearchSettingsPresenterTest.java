package com.treelearn.app.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.treelearn.app.ui.SearchSettingsPresenter.Engine;
import com.treelearn.app.ui.SearchSettingsPresenter.Notice;
import com.treelearn.app.ui.SearchSettingsPresenter.Row;
import com.treelearn.app.ui.SearchSettingsPresenter.State;
import com.treelearn.app.ui.SearchSettingsPresenter.Tier;
import com.treelearn.app.ui.TestDoubles.InlineExecutor;
import com.treelearn.app.ui.TestDoubles.InlineUi;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * The search settings presenter's state machine (T-091, SPEC-080): the master switch, the engine
 * selection, the result tier and the per-engine key handling, each persisted the moment it is
 * chosen — on fakes, off the UI thread, with the key never rendered beyond its mask.
 */
public final class SearchSettingsPresenterTest {

    private RecordingSink sink;
    private InlineExecutor background;
    private FakeStore store;
    private SearchSettingsPresenter presenter;

    @Before
    public void setUp() {
        sink = new RecordingSink();
        background = new InlineExecutor();
        store = new FakeStore();
        presenter = new SearchSettingsPresenter(sink, new InlineUi(), background, store);
    }

    @Test
    public void startingReadsTheWholeStateOffTheUiThread() {
        store.engine = Engine.TAVILY;
        store.tier = Tier.FIVE;
        store.searchEnabled = true;
        store.keys.add(Engine.TAVILY);

        presenter.start();

        assertEquals(1, background.runs);
        assertEquals(Engine.TAVILY, sink.last().engine);
        assertEquals(Tier.FIVE, sink.last().tier);
        assertTrue(sink.last().searchEnabled);
        assertEquals(3, sink.last().rows.size());
        assertTrue(sink.last().rows.get(1).hasKey);
    }

    @Test
    public void theDefaultStateIsOffWithZhipuAndTenResults() {
        presenter.start();

        assertFalse(sink.last().searchEnabled);
        assertEquals(Engine.ZHIPU, sink.last().engine);
        assertEquals(Tier.TEN, sink.last().tier);
    }

    @Test
    public void togglingSearchPersistsIt() {
        presenter.start();

        presenter.onSearchToggled(true);

        assertTrue(store.searchEnabled);
        assertTrue(sink.last().searchEnabled);
    }

    @Test
    public void selectingAnEnginePersistsItAndReloads() {
        presenter.start();

        presenter.onEngineSelected(Engine.BOCHA);

        assertEquals(Engine.BOCHA, store.engine);
        assertEquals(Engine.BOCHA, sink.last().engine);
    }

    @Test
    public void selectingAResultTierPersistsIt() {
        presenter.start();

        presenter.onTierSelected(Tier.THREE);

        assertEquals(Tier.THREE, store.tier);
        assertEquals(Tier.THREE, sink.last().tier);
    }

    @Test
    public void aSelectedEngineWithoutAKeyRaisesTheNokeyNotice() {
        store.searchEnabled = true;

        presenter.start();

        assertEquals(Notice.NOKEY, sink.last().notice);
        assertFalse(sink.last().rows.get(0).hasKey);
    }

    @Test
    public void configuringTheKeyClearsTheNokeyNotice() {
        store.searchEnabled = true;
        presenter.start();

        presenter.onKeySavePressed(Engine.ZHIPU, "tvly-key-123456".toCharArray());

        assertEquals(Notice.NONE, sink.last().notice);
        assertTrue(store.keys.contains(Engine.ZHIPU));
        assertEquals(1, store.saved);
    }

    @Test
    public void aBlankKeyIsRefusedBeforeAnyStoreWork() {
        presenter.start();

        char[] blank = "   ".toCharArray();
        presenter.onKeySavePressed(Engine.ZHIPU, blank);

        assertEquals(Notice.KEY_REQUIRED, sink.last().notice);
        assertEquals(0, store.saved);
        assertTrue(wiped(blank));
    }

    @Test
    public void aRefusedKeySaveKeepsTheOldNoticeAndWipesTheBuffer() {
        store.searchEnabled = true;
        presenter.start();
        store.refuseSave = true;
        char[] candidate = "tvly-key-123456".toCharArray();

        presenter.onKeySavePressed(Engine.ZHIPU, candidate);

        assertEquals(Notice.KEY_SAVE_FAILED, sink.last().notice);
        assertFalse(store.keys.contains(Engine.ZHIPU));
        assertTrue(wiped(candidate));
    }

    @Test
    public void theKeyBufferIsWipedEvenOnASuccessfulSave() {
        presenter.start();
        char[] candidate = "tvly-key-123456".toCharArray();

        presenter.onKeySavePressed(Engine.ZHIPU, candidate);

        assertEquals(1, store.saved);
        assertTrue(wiped(candidate));
    }

    @Test
    public void deletingTheKeyRaisesTheNokeyNoticeAgain() {
        store.keys.add(Engine.ZHIPU);
        store.searchEnabled = true;
        presenter.start();

        presenter.onKeyDeletePressed(Engine.ZHIPU);

        assertEquals(0, store.keys.size());
        assertEquals(Notice.NOKEY, sink.last().notice);
    }

    private static boolean wiped(char[] buffer) {
        for (char c : buffer) {
            if (c != '\0') {
                return false;
            }
        }
        return true;
    }

    private static final class RecordingSink implements SearchSettingsPresenter.Sink {

        final List<State> states = new ArrayList<>();

        @Override
        public void render(State state) {
            states.add(state);
        }

        State last() {
            return states.get(states.size() - 1);
        }
    }

    /** In-memory search settings: the store validates nothing, the presenter owns the rules. */
    private static final class FakeStore implements SearchSettingsPresenter.Store {

        boolean searchEnabled;
        Engine engine = Engine.ZHIPU;
        Tier tier = Tier.TEN;
        boolean refuseSave;
        int saved;
        final List<Engine> keys = new ArrayList<>();

        @Override
        public boolean searchEnabled() {
            return searchEnabled;
        }

        @Override
        public void setSearchEnabled(boolean enabled) {
            searchEnabled = enabled;
        }

        @Override
        public Engine engine() {
            return engine;
        }

        @Override
        public void setEngine(Engine value) {
            engine = value;
        }

        @Override
        public Tier tier() {
            return tier;
        }

        @Override
        public void setTier(Tier value) {
            tier = value;
        }

        @Override
        public boolean hasKey(Engine value) {
            return keys.contains(value);
        }

        @Override
        public KeySnapshot readKey(Engine value) {
            return keys.contains(value)
                    ? KeySnapshot.ofStoredKey("stored-key-material".toCharArray())
                    : KeySnapshot.missing();
        }

        @Override
        public void saveKey(Engine value, char[] secret)
                throws SearchSettingsPresenter.KeyRefused {
            if (refuseSave) {
                throw new SearchSettingsPresenter.KeyRefused("refused by the test");
            }
            saved++;
            keys.add(value);
        }

        @Override
        public void deleteKey(Engine value) {
            keys.remove(value);
        }
    }
}
