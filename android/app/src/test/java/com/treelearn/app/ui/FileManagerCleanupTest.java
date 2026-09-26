package com.treelearn.app.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * T-098's presenter flow: the cleanup button parks the scan's answer for one confirmation, only
 * the confirmation reaches the store, and a failed cleanup surfaces {@code CLEANUP_FAILED} while
 * keeping the marks for a retry (SPEC-082's confirm-first and retry rules).
 */
public final class FileManagerCleanupTest {

    @Test
    public void cleanupRequiresItsConfirmationBeforeTheStoreHearsAboutIt() {
        RecordingStore store = new RecordingStore(Arrays.asList("orphan-a", "orphan-b"));
        FileManagerPresenter presenter = new FileManagerPresenter(new RecordingSink(),
                new TestDoubles.InlineUi(), new TestDoubles.InlineExecutor(), store);
        presenter.start();

        presenter.onCleanupRequested();
        assertTrue("the bar parks for confirmation", presenter.state().pendingCleanupAll);
        assertEquals("no cleanup ran yet", 0, store.cleanupCalls);

        presenter.onCleanupCancelled();
        assertFalse("cancelling disarms the bar", presenter.state().pendingCleanupAll);
        assertEquals("and still nothing ran", 0, store.cleanupCalls);

        presenter.onCleanupRequested();
        presenter.onCleanupConfirmed();
        assertEquals("only the confirmed pass reaches the store", 1, store.cleanupCalls);
        assertEquals("and it carries exactly the scan's ids",
                new HashSet<>(Arrays.asList("orphan-a", "orphan-b")), store.cleanedIds);
    }

    @Test
    public void aFailedCleanupKeepsTheMarksForARetry() {
        RecordingStore store = new RecordingStore(Arrays.asList("orphan-a"));
        store.failCleanup = true;
        FileManagerPresenter presenter = new FileManagerPresenter(new RecordingSink(),
                new TestDoubles.InlineUi(), new TestDoubles.InlineExecutor(), store);
        presenter.start();

        presenter.onCleanupRequested();
        presenter.onCleanupConfirmed();

        assertEquals(FileManagerPresenter.Notice.CLEANUP_FAILED, presenter.state().notice);
        // The confirmation bar disarms (a stuck confirm state would trap the page), but the orphan
        // marks survive — the retry is one more tap on the cleanup button (SPEC-082 可重试).
        assertFalse("the confirmation bar disarms after the failure",
                presenter.state().pendingCleanupAll);
        assertEquals("the marks survive for a retry", 1, presenter.state().cleanableIds.size());
    }

    private static final class RecordingSink implements FileManagerPresenter.Sink {

        final List<FileManagerPresenter.State> states = new ArrayList<>();

        @Override
        public void render(FileManagerPresenter.State next) {
            states.add(next);
        }
    }

    private static final class RecordingStore implements FileManagerPresenter.Store {

        private final List<String> orphans;
        final Set<String> cleanedIds = new HashSet<>();
        int cleanupCalls;
        boolean failCleanup;

        RecordingStore(List<String> orphans) {
            this.orphans = orphans;
        }

        @Override
        public List<FileManagerPresenter.Row> list() {
            return java.util.Collections.emptyList();
        }

        @Override
        public long usageBytes() {
            return 0L;
        }

        @Override
        public void delete(String attachmentId) {
            throw new UnsupportedOperationException("cleanup-only fixture");
        }

        @Override
        public Set<String> orphanIds() {
            return new HashSet<>(orphans);
        }

        @Override
        public void cleanupOrphans(Set<String> candidateIds) {
            cleanupCalls++;
            if (failCleanup) {
                throw new IllegalStateException("synthetic cleanup failure");
            }
            cleanedIds.addAll(candidateIds);
        }
    }
}
