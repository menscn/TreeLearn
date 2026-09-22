package com.treelearn.app.library;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.treelearn.app.navigation.NavigationData;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.Executor;

/**
 * The library presenter's decisions, asserted on the states a screen would draw.
 *
 * <p>The acceptance criteria this covers: the list is whatever the port holds with no second opinion,
 * an empty store is its own phase rather than a listed blank page, an unreadable store is the error
 * phase rather than a silent empty one, a tapped row resumes on the branch the store named, and coming
 * back to the tab re-reads without flashing the loading pane.
 *
 * <p>The two doubles that matter are the manual executor and the fake port: the read is dispatched
 * rather than taken on the calling thread, so a test can decide exactly when it lands.
 */
public final class LibraryPresenterTest {

    private static final String TOPIC = "11111111-1111-4111-8111-111111111111";
    private static final String RESUME = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa";

    private FakeData data;
    private RecordingSink sink;
    private ManualExecutor background;
    private LibraryPresenter presenter;

    @Before
    public void setUp() {
        data = new FakeData();
        sink = new RecordingSink();
        background = new ManualExecutor();
        presenter = new LibraryPresenter(sink, Runnable::run, background, data);
    }

    @Test
    public void startShowsLoadingFirstAndReadsTheStoreOffTheCallingThread() {
        data.rows = Collections.singletonList(row(TOPIC, RESUME));

        presenter.start();

        assertEquals("the loading pane is drawn before the read is dispatched",
                LibraryViewState.Phase.LOADING, sink.states.get(0).phase);
        assertEquals("the read must be dispatched, not taken on the calling thread",
                1, background.size());

        background.drain();

        assertEquals(LibraryViewState.Phase.READY, sink.last().phase);
        assertEquals(1, sink.last().rows.size());
        assertEquals(RESUME, sink.last().rows.get(0).resumeBranchId);
    }

    @Test
    public void anEmptyStoreIsTheEmptyPhaseRatherThanAnEmptyListOnScreen() {
        data.rows = Collections.emptyList();

        presenter.start();
        background.drain();

        assertEquals("a store with no topic is its own pane, so the guidance can be drawn",
                LibraryViewState.Phase.EMPTY, sink.last().phase);
        assertTrue(sink.last().rows.isEmpty());
    }

    @Test
    public void anUnreadableStoreIsTheErrorPhaseAndNeverSilentlyEmpty() {
        data.failure = new IllegalStateException("SYNTHETIC-UNREADABLE-STORE");

        presenter.start();
        background.drain();

        assertEquals("a refused read must not read as 'you have nothing'",
                LibraryViewState.Phase.ERROR, sink.last().phase);
    }

    @Test
    public void aTappedRowResumesOnTheBranchTheStoreNamed() {
        data.rows = Collections.singletonList(row(TOPIC, RESUME));
        presenter.start();
        background.drain();

        presenter.onTopicTapped(sink.last().rows.get(0));

        assertEquals("the tap opens the branch, exactly as a tapped home card does",
                Collections.singletonList(RESUME), sink.opened);
    }

    @Test
    public void comingBackToTheTabReReadsWithoutFlashingTheLoadingPane() {
        data.rows = Collections.singletonList(row(TOPIC, RESUME));
        presenter.start();
        background.drain();
        data.rows = Arrays.asList(row(TOPIC, RESUME), row(RESUME, RESUME));

        presenter.onResumed();
        assertEquals("a return must not blank the list it is about to redraw",
                LibraryViewState.Phase.READY, sink.last().phase);
        background.drain();

        assertEquals(LibraryViewState.Phase.READY, sink.last().phase);
        assertEquals(2, sink.last().rows.size());
    }

    private static LibraryViewState.Row row(String topicId, String resumeBranchId) {
        return new LibraryViewState.Row(topicId, resumeBranchId, "SYNTHETIC-TITLE", 1, 0L, 1_756_000_000_000L);
    }

    private static final class FakeData implements LibraryData {

        List<LibraryViewState.Row> rows = Collections.emptyList();
        List<LibraryViewState.ArchiveRow> archived = Collections.emptyList();
        RuntimeException failure;

        @Override
        public List<LibraryViewState.Row> topics() {
            if (failure != null) {
                throw failure;
            }
            return rows;
        }

        @Override
        public List<LibraryViewState.ArchiveRow> archived() {
            if (failure != null) {
                throw failure;
            }
            return archived;
        }

        @Override
        public NavigationData.Snapshot snapshot(String topicId) {
            throw new UnsupportedOperationException("no case in this suite asks for a delete scope");
        }

        @Override
        public void restore(String branchId, long now) {
            throw new UnsupportedOperationException("no case in this suite restores a branch");
        }

        @Override
        public int cancelInFlight(List<String> branchIds) {
            throw new UnsupportedOperationException("no case in this suite deletes a branch");
        }

        @Override
        public void delete(NavigationData.Plan plan) {
            throw new UnsupportedOperationException("no case in this suite deletes a branch");
        }
    }

    private static final class RecordingSink implements LibrarySink {

        final List<LibraryViewState> states = new ArrayList<LibraryViewState>();
        final List<String> opened = new ArrayList<String>();

        @Override
        public void render(LibraryViewState state) {
            states.add(state);
        }

        @Override
        public void openBranch(String branchId) {
            opened.add(branchId);
        }

        LibraryViewState last() {
            return states.get(states.size() - 1);
        }
    }

    private static final class ManualExecutor implements Executor {

        private final Deque<Runnable> queued = new ArrayDeque<Runnable>();

        @Override
        public void execute(Runnable command) {
            queued.add(command);
        }

        int size() {
            return queued.size();
        }

        void drain() {
            while (!queued.isEmpty()) {
                queued.poll().run();
            }
        }
    }
}
