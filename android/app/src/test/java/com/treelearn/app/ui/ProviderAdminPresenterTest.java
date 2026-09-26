package com.treelearn.app.ui;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.treelearn.app.ui.ProviderAdminPresenter.Editor;
import com.treelearn.app.ui.ProviderAdminPresenter.Fetch;
import com.treelearn.app.ui.ProviderAdminPresenter.Notice;
import com.treelearn.app.ui.ProviderAdminPresenter.Row;
import com.treelearn.app.ui.ProviderAdminPresenter.State;
import com.treelearn.app.ui.TestDoubles.InlineExecutor;
import com.treelearn.app.ui.TestDoubles.InlineUi;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The provider admin presenter's state machine (T-087): list, toggle, create, edit, key handling,
 * model fetch and delete — the acceptance walk for the provider screens, on fakes, off the UI thread.
 */
public final class ProviderAdminPresenterTest {

    private RecordingSink sink;
    private InlineExecutor background;
    private FakeStore store;
    private ProviderAdminPresenter presenter;

    @Before
    public void setUp() {
        sink = new RecordingSink();
        background = new InlineExecutor();
        store = new FakeStore();
        presenter = new ProviderAdminPresenter(sink, new InlineUi(), background, store);
    }

    @Test
    public void startingReadsTheProviderRowsOffTheUiThread() {
        store.rows.add(row("p1", "DeepSeek", true, 2, true));

        presenter.start();

        assertEquals(1, background.runs);
        assertEquals(State.Mode.LIST, sink.last().mode);
        assertEquals(1, sink.last().rows.size());
        assertEquals("DeepSeek", sink.last().rows.get(0).name);
        assertEquals(2, sink.last().rows.get(0).modelCount);
        assertTrue(sink.last().rows.get(0).hasKey);
    }

    @Test
    public void togglingWritesTheEnabledBitThroughTheStoreAndRefreshes() {
        store.rows.add(row("p1", "DeepSeek", true, 2, true));

        presenter.onToggle("p1", false);

        assertEquals(1, store.toggles);
        assertFalse(store.enabledValues.get(0));
        assertEquals(State.Mode.LIST, sink.last().mode);
    }

    @Test
    public void newPressedOpensABlankEditorWithoutTouchingTheStore() {
        presenter.onNewPressed();

        assertEquals(State.Mode.EDITOR, sink.last().mode);
        assertNull(sink.last().editor.providerId);
        assertEquals("", sink.last().editor.name);
        assertEquals("", sink.last().editor.baseUrl);
        assertEquals(KeySnapshot.Line.MISSING, sink.last().editor.key.line);
        assertEquals(0, background.runs);
    }

    @Test
    public void aDuplicateNameIsRefusedInlineAndNothingIsCreated() {
        presenter.onNewPressed();
        store.nameTaken = true;

        presenter.onSavePressed(" DeepSeek ", "https://api.example.com");

        assertEquals(Notice.NAME_TAKEN, sink.last().editor.notice);
        assertEquals(0, store.created);
    }

    @Test
    public void aBlankNameIsRefusedBeforeAnyStoreWork() {
        presenter.onNewPressed();

        presenter.onSavePressed("   ", "https://api.example.com");

        assertEquals(Notice.NAME_REQUIRED, sink.last().editor.notice);
        assertEquals(0, store.created);
    }

    @Test
    public void aValidFormCreatesTheProviderAndReturnsToTheList() {
        presenter.onNewPressed();
        store.rows.add(row("p2", "硅基流动", true, 0, false));

        presenter.onSavePressed(" 硅基流动 ", " https://api.siliconflow.cn ");

        assertEquals(1, store.created);
        assertEquals("硅基流动", store.createdName);
        assertEquals("https://api.siliconflow.cn", store.createdBaseUrl);
        assertEquals(State.Mode.LIST, sink.last().mode);
    }

    @Test
    public void openingARowPrefillsTheEditorAndShowsOnlyTheKeyMask() {
        store.rows.add(row("p1", "DeepSeek", true, 2, true));
        presenter.start();
        store.key = KeySnapshot.ofStoredKey("sk-abcdefghijklmnop".toCharArray());

        presenter.onRowPressed("p1");

        assertEquals(State.Mode.EDITOR, sink.last().mode);
        assertEquals("p1", sink.last().editor.providerId);
        assertEquals("DeepSeek", sink.last().editor.name);
        assertEquals("https://api.deepseek.com", sink.last().editor.baseUrl);
        assertEquals(KeySnapshot.Line.PRESENT, sink.last().editor.key.line);
        assertTrue("the editor may only ever see the mask",
                sink.last().editor.key.mask.contains("•"));
        assertFalse(sink.last().editor.key.mask.contains("abcdefghijkl"));
    }

    @Test
    public void savingAKeyTransfersTheBufferWipesItAndShowsTheMask() {
        store.rows.add(row("p1", "DeepSeek", true, 2, true));
        presenter.start();
        presenter.onRowPressed("p1");
        char[] typed = "sk-zyxwvutsrqponml".toCharArray();

        presenter.onKeySavePressed(typed);

        assertEquals(1, store.keySaves);
        assertArrayEquals("the buffer must be wiped once the presenter owns it",
                new char[typed.length], typed);
        assertEquals(KeySnapshot.Line.PRESENT, sink.last().editor.key.line);
        assertNull(sink.last().editor.notice);
    }

    @Test
    public void anEmptyKeyFieldIsRefusedBeforeAnyWrite() {
        store.rows.add(row("p1", "DeepSeek", true, 2, true));
        presenter.start();
        presenter.onRowPressed("p1");

        presenter.onKeySavePressed(new char[0]);

        assertEquals(Notice.KEY_REQUIRED, sink.last().editor.notice);
        assertEquals(0, store.keySaves);
    }

    @Test
    public void aRefusedKeyWriteIsReportedAndKeepsTheOldMask() {
        store.rows.add(row("p1", "DeepSeek", true, 2, true));
        presenter.start();
        presenter.onRowPressed("p1");
        KeySnapshot before = sink.last().editor.key;
        store.refuseSave = true;

        presenter.onKeySavePressed("sk-abcdefghijklmnop".toCharArray());

        assertEquals(Notice.KEY_SAVE_FAILED, sink.last().editor.notice);
        assertEquals(before.line, sink.last().editor.key.line);
    }

    @Test
    public void deletingTheKeyClearsItInTheEditorState() {
        store.rows.add(row("p1", "DeepSeek", true, 2, true));
        presenter.start();
        presenter.onRowPressed("p1");

        presenter.onKeyDeletePressed();

        assertEquals(1, store.keyDeletes);
        assertEquals(KeySnapshot.Line.MISSING, sink.last().editor.key.line);
    }

    @Test
    public void aFailedFetchKeepsTheEditorAndReportsTheInlineFailure() {
        store.rows.add(row("p1", "DeepSeek", true, 2, true));
        presenter.start();
        presenter.onRowPressed("p1");
        store.nextFetch = new Fetch(true, 0);

        presenter.onFetchPressed();

        assertEquals(State.Mode.EDITOR, sink.last().mode);
        assertTrue(sink.last().editor.fetchFailed);
        assertEquals(1, store.fetches);
    }

    @Test
    public void aSuccessfulFetchReportsTheAddedCountAndClearsTheFailure() {
        store.rows.add(row("p1", "DeepSeek", true, 2, true));
        presenter.start();
        presenter.onRowPressed("p1");
        store.nextFetch = new Fetch(false, 5);

        presenter.onFetchPressed();

        assertFalse(sink.last().editor.fetchFailed);
        assertEquals(Integer.valueOf(5), sink.last().editor.fetchAdded);
    }

    @Test
    public void deletingTheLastEnabledProviderCarriesTheExtraWarning() {
        store.rows.add(row("p1", "DeepSeek", true, 2, true));
        presenter.start();

        presenter.onDeletePressed("p1");

        assertTrue(sink.last().deletePending);
        assertTrue(sink.last().deleteWarning);
    }

    @Test
    public void deletingOneOfSeveralEnabledProvidersUsesThePlainConfirmation() {
        store.rows.add(row("p1", "DeepSeek", true, 2, true));
        store.rows.add(row("p2", "另一家", true, 1, true));
        presenter.start();

        presenter.onDeletePressed("p1");

        assertTrue(sink.last().deletePending);
        assertFalse(sink.last().deleteWarning);
    }

    @Test
    public void confirmingTheDeleteRemovesTheRowAndRefreshesTheList() {
        store.rows.add(row("p1", "DeepSeek", true, 2, true));
        presenter.start();
        presenter.onDeletePressed("p1");

        presenter.onDeleteConfirmed();

        assertEquals(1, store.deletes);
        assertEquals("p1", store.deletedId);
        assertEquals(State.Mode.LIST, sink.last().mode);
        assertFalse(sink.last().deletePending);
    }

    @Test
    public void editorBackReturnsToTheListAndRefreshesTheRows() {
        store.rows.add(row("p1", "DeepSeek", true, 2, true));
        presenter.start();
        presenter.onRowPressed("p1");

        presenter.onEditorBackPressed();

        assertEquals(State.Mode.LIST, sink.last().mode);
        assertEquals(2, background.runs);
    }

    private static Row row(String id, String name, boolean enabled, int modelCount,
            boolean hasKey) {
        return new Row(id, name, "https://api.deepseek.com", enabled, modelCount, hasKey);
    }

    private static final class RecordingSink implements ProviderAdminPresenter.Sink {

        final List<State> states = new ArrayList<State>();

        State last() {
            return states.get(states.size() - 1);
        }

        @Override
        public void render(State state) {
            states.add(state);
        }
    }

    private static final class FakeStore implements ProviderAdminPresenter.Store {

        final List<Row> rows = new ArrayList<Row>();
        final List<Boolean> enabledValues = new ArrayList<Boolean>();
        KeySnapshot key = KeySnapshot.missing();
        boolean nameTaken;
        boolean refuseSave;
        Fetch nextFetch = new Fetch(false, 0);
        int created, renamed, toggles, deletes, keySaves, keyDeletes, fetches;
        String createdName, createdBaseUrl, deletedId;

        @Override
        public List<Row> list() {
            return rows;
        }

        @Override
        public boolean nameTaken(String name, String ignoreId) {
            return nameTaken;
        }

        @Override
        public Row create(String name, String baseUrl) {
            created++;
            createdName = name;
            createdBaseUrl = baseUrl;
            return new Row("new", name, baseUrl, true, 0, false);
        }

        @Override
        public void rename(String id, String name, String baseUrl) {
            renamed++;
        }

        @Override
        public void setEnabled(String id, boolean enabled) {
            toggles++;
            enabledValues.add(enabled);
        }

        @Override
        public void delete(String id) {
            deletes++;
            deletedId = id;
        }

        @Override
        public KeySnapshot readKey(String providerId) {
            return key;
        }

        @Override
        public void saveKey(String providerId, char[] secret)
                throws ProviderAdminPresenter.KeyRefused {
            Arrays.fill(secret, '\0');
            if (refuseSave) {
                throw new ProviderAdminPresenter.KeyRefused("refused");
            }
            keySaves++;
            key = KeySnapshot.ofStoredKey("sk-abcdefghijklmnop".toCharArray());
        }

        @Override
        public void deleteKey(String providerId) {
            keyDeletes++;
            key = KeySnapshot.missing();
        }

        @Override
        public Fetch fetch(String providerId) {
            fetches++;
            return nextFetch;
        }
    }
}
