package com.treelearn.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Instrumentation;
import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.treelearn.app.screen.FileManagerScreen;
import com.treelearn.app.ui.FileManagerPresenter;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * T-096 acceptance evidence for the file manager page, executed on a real device: the page renders
 * the store's rows (missing files marked, never hidden), deletion is confirm-first, and a confirmed
 * delete reaches the store and reloads.
 *
 * <p>The screen is owned by this suite — {@code AppContainer} is a process-wide singleton other
 * suites unlink — so the presenter runs on a fake store and the assertions stay about the page.
 */
@RunWith(AndroidJUnit4.class)
public final class FileManagerScreenInstrumentedTest {

    private final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();

    private FileManagerScreen screen;
    private RecordingStore store;
    private FileManagerPresenter presenter;

    @Before
    public void setUp() {
        store = new RecordingStore();
        screen = new FileManagerScreen(context());
        presenter = new FileManagerPresenter(screen, Runnable::run, Runnable::run, store);
        screen.setActions(presenter);
    }

    @After
    public void tearDown() {
        // Nothing process-wide was touched: the screen and its presenter are suite-owned.
    }

    @Test
    public void thePageRendersEveryRowAndMarksTheMissingOne() {
        presenter.start();

        assertTrue("the page draws its usage line",
                texts().stream().anyMatch(t -> t.startsWith(
                        context().getString(R.string.files_usage).substring(0, 3))));
        List<String> texts = texts();
        assertTrue("the kept file's row is drawn: " + texts, texts.contains("KEEP.txt"));
        assertTrue("the missing file's row is drawn too: " + texts, texts.contains("GONE.txt"));
        assertTrue("and the missing mark is on the page: " + texts,
                texts.contains(context().getString(R.string.files_missing)));
        assertFalse("an empty page is not claimed while rows exist: " + texts,
                texts.contains(context().getString(R.string.files_empty)));
    }

    @Test
    public void deletionIsConfirmFirstAndAConfirmedDeleteReachesTheStoreAndReloads() {
        presenter.start();
        assertFalse("no store call before the confirmation", store.deleted() > 0);

        instrumentation.runOnMainSync(
                () -> screen.findViewById(R.id.files_row_delete).performClick());
        assertEquals("the request only parks the confirmation", 0, store.deleted());

        instrumentation.runOnMainSync(
                () -> screen.findViewById(R.id.files_row_confirm).performClick());

        assertEquals("the confirmation reaches the store", 1, store.deleted());
        assertEquals("and exactly the confirmed record", "a-keep", store.deletedId());
        assertTrue("the page reloaded without the deleted row: " + texts(),
                !texts().contains("KEEP.txt"));
    }

    // ------------------------------------------------------------------
    // Fixtures and drivers.
    // ------------------------------------------------------------------

    private static final class RecordingStore implements FileManagerPresenter.Store {

        private final AtomicInteger listCalls = new AtomicInteger();
        private final List<String> deletedIds = new ArrayList<>();

        @Override
        public List<FileManagerPresenter.Row> list() {
            boolean first = listCalls.incrementAndGet() == 1;
            if (first) {
                return Arrays.asList(
                        new FileManagerPresenter.Row("a-keep", "KEEP.txt", "text/plain", 1_000L,
                                "pending", true, 0),
                        new FileManagerPresenter.Row("a-gone", "GONE.txt", "text/plain", 2_000L,
                                "pending", false, 1));
            }
            return java.util.Collections.singletonList(
                    new FileManagerPresenter.Row("a-gone", "GONE.txt", "text/plain", 2_000L,
                            "pending", false, 1));
        }

        @Override
        public long usageBytes() {
            return 3_000L;
        }

        @Override
        public void delete(String attachmentId) {
            deletedIds.add(attachmentId);
        }

        @Override
        public java.util.Set<String> orphanIds() {
            return new java.util.HashSet<>();
        }

        @Override
        public void cleanupOrphans(java.util.Set<String> candidateIds) {
            // T-098 exercises the presenter's cleanup flow in its own suite; this fixture's
            // scenarios stay delete-only, so the whole-orphan path is a recorded no-op here.
        }

        int deleted() {
            return deletedIds.size();
        }

        String deletedId() {
            return deletedIds.isEmpty() ? null : deletedIds.get(0);
        }
    }

    private List<String> texts() {
        final List<String> found = new ArrayList<>();
        instrumentation.runOnMainSync(() -> collect(screen, found));
        return found;
    }

    private static void collect(View view, List<String> into) {
        if (view instanceof TextView && view.getVisibility() == View.VISIBLE) {
            into.add(((TextView) view).getText().toString());
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                collect(group.getChildAt(i), into);
            }
        }
    }

    private static Context context() {
        return InstrumentationRegistry.getInstrumentation().getTargetContext();
    }
}
