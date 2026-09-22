package com.treelearn.app.ui;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Before;
import org.junit.Test;

/**
 * The settings presenter: read the key's state, replace it, delete it.
 *
 * <p>The acceptance criterion behind these tests is the credential one - save, replace and delete all
 * work, and nothing anywhere echoes the full key (PRD 6.2.13 points 6-7, technical design 14).
 */
public final class SettingsPresenterTest {

    private TestDoubles.RecordingSettingsSink sink;
    private TestDoubles.InlineExecutor background;
    private TestDoubles.FakeSettingsStore store;

    private SettingsPresenter presenter;

    @Before
    public void setUp() {
        sink = new TestDoubles.RecordingSettingsSink();
        background = new TestDoubles.InlineExecutor();
        store = new TestDoubles.FakeSettingsStore();
        presenter = new SettingsPresenter(sink, new TestDoubles.InlineUi(), background, store);
    }

    @Test
    public void openingTheScreenReadsTheStoredKeyOffTheUiThread() {
        store.key = KeySnapshot.ofStoredKey("sk-abcdefghijklmnop".toCharArray());

        presenter.start();

        assertEquals(1, background.runs);
        assertEquals(KeySnapshot.Line.PRESENT, sink.last().keyLine);
    }

    @Test
    public void anEmptyFieldIsRefusedBeforeAnythingIsWritten() {
        presenter.start();

        presenter.onSavePressed(new char[0]);

        assertEquals(SettingsViewState.Notice.KEY_REQUIRED, sink.last().notice);
        assertNull(store.lastSaved);
    }

    @Test
    public void savingStoresTheKeyShowsItsMaskAndWipesTheBuffer() {
        presenter.start();
        char[] typed = "sk-abcdefghijklmnop".toCharArray();

        presenter.onSavePressed(typed);

        assertEquals(KeySnapshot.Line.PRESENT, sink.last().keyLine);
        assertNull(sink.last().notice);
        assertArrayEquals("the buffer must be wiped once the presenter owns it",
                new char[typed.length], typed);
        assertEquals(KeySnapshot.mask("sk-abcdefghijklmnop".toCharArray()), sink.last().keyMask);
        assertEquals("the mask must not contain the key",
                -1, sink.last().keyMask.indexOf("abcdefghijkl"));
    }

    @Test
    public void replacingAnExistingKeyKeepsTheFieldFreeAndReportsTheNewMask() {
        store.key = KeySnapshot.ofStoredKey("sk-0000000000000000".toCharArray());
        presenter.start();
        char[] replacement = "sk-zzzzzzzzzzzzzzzz".toCharArray();

        presenter.onSavePressed(replacement);

        assertEquals(KeySnapshot.Line.PRESENT, sink.last().keyLine);
        assertEquals(KeySnapshot.mask("sk-zzzzzzzzzzzzzzzz".toCharArray()), sink.last().keyMask);
    }

    @Test
    public void aRefusedWriteIsReportedAndLeavesTheStoredKeyAlone() {
        store.key = KeySnapshot.ofStoredKey("sk-abcdefghijklmnop".toCharArray());
        presenter.start();
        String before = sink.last().keyMask;
        store.refuseSave = true;
        char[] typed = "sk-abcdefghijklmnop".toCharArray();

        presenter.onSavePressed(typed);

        assertEquals(SettingsViewState.Notice.KEY_SAVE_FAILED, sink.last().notice);
        assertEquals("a failed save must not change what is stored", before, sink.last().keyMask);
        assertArrayEquals(new char[typed.length], typed);
    }

    @Test
    public void deletingRemovesTheKeyAndOffersTheFieldAgain() {
        store.key = KeySnapshot.ofStoredKey("sk-abcdefghijklmnop".toCharArray());
        presenter.start();

        presenter.onDeletePressed();

        assertEquals(1, store.deleted);
        assertEquals(KeySnapshot.Line.MISSING, sink.last().keyLine);
        assertEquals("", sink.last().keyMask);
    }

    @Test
    public void anUnreadableStoredKeyIsItsOwnStateNotAMissingOne() {
        store.key = KeySnapshot.unreadable();

        presenter.start();

        assertEquals(KeySnapshot.Line.UNREADABLE, sink.last().keyLine);
    }
}