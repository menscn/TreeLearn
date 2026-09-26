package com.treelearn.app.library;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.treelearn.app.navigation.NavigationData;
import com.treelearn.app.search.SearchPresenter;
import com.treelearn.app.search.SearchRepository;
import com.treelearn.app.search.SearchViewState;
import com.treelearn.app.ui.UiThread;
import com.treelearn.core.domain.SearchHit;
import com.treelearn.core.domain.SearchQuery;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executor;

/**
 * T-054: the library's search entry inherits the one property the retired home entry held —
 * search is reachable before any topic exists (HomeSearchEntryTest's core case, moved to the new
 * host), plus the two behaviours that must survive the move untouched: an empty draft never
 * touches the repository, and an opened hit asks the shell to move at the hit's position.
 *
 * <p>The decisions live in the existing {@link SearchPresenter}; the library's presenter forwards
 * to it and owns no second state machine. What these cases pin is exactly that forwarding: with a
 * library that has nothing in it, a query still flows through the library's presenter to the
 * search stack and a hit still flows back out to the shell.
 */
public class LibrarySearchEntryTest {

    private final RecordingSearchSink searchSink = new RecordingSearchSink();
    private final CountingRepository repository = new CountingRepository();
    private final SearchPresenter search =
            new SearchPresenter(searchSink, new InlineUi(), new InlineExecutor(), repository);

    private LibraryPresenter presenter() {
        return new LibraryPresenter(new RecordingLibrarySink(), new InlineUi(),
                new InlineExecutor(), new EmptyData(), search);
    }

    @Test
    public void theLibrarySearchEntryIsReachableWithNoTopicsAtAll() {
        LibraryPresenter presenter = presenter();
        presenter.start();

        presenter.onSearchQueryChanged("anything at all");

        assertTrue("search spans every branch: an empty library must still search",
                searchSink.lastState != null);
        assertEquals(SearchViewState.Phase.RESULTS, searchSink.lastState.phase);
        assertEquals(1, repository.queries);
    }

    @Test
    public void anEmptyDraftNeverTouchesTheRepository() {
        LibraryPresenter presenter = presenter();
        presenter.start();

        presenter.onSearchQueryChanged("");

        assertEquals("a blank query must not reach the store", 0, repository.queries);
        assertEquals(SearchViewState.Phase.IDLE, searchSink.lastState.phase);
    }

    @Test
    public void anOpenedHitAsksTheShellToMoveToTheBranchAtItsPosition() {
        LibraryPresenter presenter = presenter();
        presenter.start();

        SearchHit hit = SearchHit.of(SearchHit.Field.QUESTION,
                "11111111-1111-4111-8111-111111111111",
                "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb",
                "cccccccc-cccc-4ccc-8ccc-cccccccccccc",
                0, null, 0, "snippet");
        presenter.onSearchHitOpened(hit);

        assertEquals(hit, searchSink.opened);
    }

    // ---------------------------------------------------------------- fakes

    private static final class RecordingSearchSink implements SearchPresenter.Sink {

        SearchViewState lastState;
        SearchHit opened;

        @Override
        public void render(SearchViewState state) {
            lastState = state;
        }

        @Override
        public void openAnchor(String topicId, String branchId,
                com.treelearn.core.domain.ReadingAnchor anchor) {
            opened = null; // the shell move is MainActivity's; the presenter forwards the hit
        }

        @Override
        public void openHit(SearchHit hit) {
            opened = hit; // the hit itself is what the library's presenter must hand over
        }
    }

    private static final class RecordingLibrarySink implements LibrarySink {

        @Override
        public void render(LibraryViewState state) {
        }

        @Override
        public void openBranch(String branchId) {
        }
    }

    /** Answers nothing and counts who asked: the empty-library case is the point of this test. */
    private static final class CountingRepository implements SearchRepository {

        int queries;

        @Override
        public List<SearchHit> search(SearchQuery query) {
            queries++;
            return new ArrayList<SearchHit>();
        }

        @Override
        public boolean isStillReachable(SearchHit hit) {
            return true;
        }
    }

    private static final class EmptyData implements LibraryData {

        @Override
        public List<LibraryViewState.Row> topics() {
            return Collections.emptyList();
        }

        @Override
        public List<LibraryViewState.ArchiveRow> archived() {
            return Collections.emptyList();
        }

        @Override
        public NavigationData.Snapshot snapshot(String topicId) {
            throw new IllegalStateException("an empty library has no snapshot to read");
        }

        @Override
        public void restore(String branchId, long now) {
        }

        @Override
        public int cancelInFlight(List<String> branchIds) {
            return 0;
        }

        @Override
        public void delete(NavigationData.Plan plan) {
        }
    }

    private static final class InlineUi implements UiThread {

        @Override
        public void post(Runnable runnable) {
            runnable.run();
        }
    }

    private static final class InlineExecutor implements Executor {

        @Override
        public void execute(Runnable command) {
            command.run();
        }
    }
}
