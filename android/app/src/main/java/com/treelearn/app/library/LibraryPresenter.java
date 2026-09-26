package com.treelearn.app.library;

import com.treelearn.app.navigation.NavigationData;
import com.treelearn.app.search.SearchPresenter;
import com.treelearn.app.ui.UiThread;
import com.treelearn.core.domain.SearchHit;

import java.util.List;
import java.util.concurrent.Executor;
import java.util.function.UnaryOperator;

/**
 * The library screen's decisions, with no Android type in sight.
 *
 * <p>Three rules live here, and each is a requirement rather than a rendering choice:
 *
 * <ul>
 *   <li><strong>The list is whatever the store holds.</strong> Nothing is filtered, re-sorted or
 *       re-derived here: the port answers with rows already ordered, and a second opinion about what
 *       belongs in the library is how the list and the store would come to disagree.
 *   <li><strong>A failed read is a phase, not an exception.</strong> The store's refusal becomes the
 *       error pane, so the page tells the learner the truth instead of showing a list that happens to
 *       be empty (PRD 6.2.16 exception 3 keeps the list scrolling rather than paging).
 *   <li><strong>Restoring and deleting are the navigation port's acts, not this screen's.</strong>
 *       Restore is one {@code setArchived} call, so it flips the archived bit and re-attaches no
 *       descendant. Delete is offered only after the branch's subtree has been counted by
 *       {@link NavigationData#plan} from one snapshot, and confirmed only after the in-flight
 *       attempts of that set have been cancelled - the same two calls, in the same order, the
 *       navigation page makes. This presenter never walks a tree of its own.
 * </ul>
 *
 * <p>A late answer is dropped. The working list had no mutation that could overtake a read; the
 * completed segment's restore and delete do, so every mutation bumps a generation and a read carrying
 * an older one is discarded. Without that, a list read dispatched before a deletion would put the
 * deleted branch back on screen.
 */
public final class LibraryPresenter {

    private final LibrarySink sink;
    private final UiThread ui;
    private final Executor background;
    private final LibraryData data;
    /** The search stack, inherited whole: this presenter forwards and owns no second state machine. */
    private final SearchPresenter search;

    private LibraryViewState state;
    /** Bumped by every mutation. A background answer carrying an older value is dropped. */
    private long generation;

    public LibraryPresenter(LibrarySink sink, UiThread ui, Executor background, LibraryData data) {
        this(sink, ui, background, data, null);
    }

    public LibraryPresenter(LibrarySink sink, UiThread ui, Executor background, LibraryData data,
            SearchPresenter search) {
        this.sink = require(sink, "sink");
        this.ui = require(ui, "ui");
        this.background = require(background, "background");
        this.data = require(data, "data");
        this.search = search;
        this.state = LibraryViewState.initial();
    }

    /** The last state handed to the sink; the tests read it, the screen does not. */
    public LibraryViewState state() {
        return state;
    }

    /** First load: the loading phase is shown while the store is read. */
    public void start() {
        render(state.withPhase(LibraryViewState.Phase.LOADING));
        reload();
    }

    /** Coming back to the tab: a topic may have been created, archived or deleted while it was away. */
    public void onResumed() {
        reload();
    }

    /** A tapped row resumes on the branch the store named, exactly as a tapped home card does. */
    public void onTopicTapped(LibraryViewState.Row row) {
        sink.openBranch(require(row, "row").resumeBranchId);
    }

    /** A tapped segment. Both lists were read together, so switching between them re-reads nothing. */
    public void onSegmentSelected(LibraryViewState.Segment segment) {
        render(state.withSegment(require(segment, "segment")));
    }

    /**
     * A keystroke in the library's search box (T-054). Forwarded whole to the search presenter: the
     * four-state machine, the empty-query guard and the stale-answer drop are its decisions, and a
     * second copy of any of them here is a second answer to the same question.
     */
    public void onSearchQueryChanged(String text) {
        if (search != null) {
            search.onQueryChanged(text);
        }
    }

    /** A tapped result row. The hit travels to the search stack, reachability and all. */
    public void onSearchHitOpened(SearchHit hit) {
        if (search != null) {
            search.onHitOpened(hit);
        }
    }

    /** Puts an archived branch back: one flag, and no descendant is moved anywhere. */
    public void onRestoreRequested(LibraryViewState.ArchiveRow row) {
        final String branchId = require(row, "row").branchId;
        final long token = ++generation;
        background.execute(() -> {
            data.restore(branchId, System.currentTimeMillis());
            load(token);
        });
    }

    /**
     * Asks for the confirmation of one archived branch's subtree. The scope is counted from the
     * topic's own snapshot by the one implementation both screens share; when it cannot be counted, no
     * confirmation is offered rather than one naming a number nobody computed.
     */
    public void onDeleteRequested(LibraryViewState.ArchiveRow row) {
        final String topicId = require(row, "row").topicId;
        final String branchId = row.branchId;
        final long token = ++generation;
        background.execute(() -> {
            final NavigationData.Plan plan;
            try {
                plan = NavigationData.plan(topicId, data.snapshot(topicId), branchId, false);
            } catch (RuntimeException unreadable) {
                post(token, current -> current.withPhase(LibraryViewState.Phase.ERROR));
                return;
            }
            if (plan == null) {
                return;
            }
            post(token, current -> current.withPendingDelete(plan));
        });
    }

    /** The confirmation was refused: nothing was cancelled and nothing was written. */
    public void onDeleteCancelled() {
        render(state.withPendingDelete(null));
    }

    /** The confirmation was given: cancel what is running, then remove exactly the plan's set. */
    public void onDeleteConfirmed() {
        final NavigationData.Plan plan = state.pendingDelete;
        if (plan == null) {
            return;
        }
        final long token = ++generation;
        render(state.withPendingDelete(null));
        background.execute(() -> {
            data.cancelInFlight(plan.branchIds);
            data.delete(plan);
            load(token);
        });
    }

    /** Both segments, read together: one store round, one render, no half-drawn page. */
    private void reload() {
        final long token = ++generation;
        background.execute(() -> load(token));
    }

    private void load(long token) {
        final List<LibraryViewState.Row> topics;
        final List<LibraryViewState.ArchiveRow> archived;
        try {
            topics = data.topics();
            archived = data.archived();
        } catch (RuntimeException unreadable) {
            post(token, current -> current.withPhase(LibraryViewState.Phase.ERROR));
            return;
        }
        post(token, current -> current.withTopics(topics).withArchived(archived));
    }

    private void post(long token, UnaryOperator<LibraryViewState> transition) {
        ui.post(() -> {
            if (token == generation) {
                render(transition.apply(state));
            }
        });
    }

    private void render(LibraryViewState next) {
        state = next;
        sink.render(next);
    }

    private static <T> T require(T value, String name) {
        if (value == null) {
            throw new IllegalArgumentException(name + " must not be null");
        }
        return value;
    }
}
