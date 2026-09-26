package com.treelearn.app.search;

import com.treelearn.app.ui.UiThread;
import com.treelearn.core.domain.ReadingAnchor;
import com.treelearn.core.domain.SearchHit;
import com.treelearn.core.domain.SearchQuery;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executor;

/**
 * The search screen's decisions, with no Android type in sight.
 *
 * <p>Three rules live here, and each one is a requirement rather than a rendering choice:
 *
 * <ul>
 *   <li><strong>A blank query never reaches the store.</strong> The pattern for "nothing typed" would
 *       match every row in the tree, so the screen shows its idle state and asks nothing.
 *   <li><strong>A failure keeps the draft.</strong> The question stays where it was typed, so a
 *       store-side error costs a retry rather than a retyping.
 *   <li><strong>A stale answer is dropped, not rendered.</strong> Typing on while a query is in
 *       flight must not be answered with the results of a question the learner has already replaced.
 * </ul>
 *
 * <p>Store reads happen on the caller-supplied background executor; every state is handed to the sink
 * on the UI thread. Transitions apply to the <em>current</em> state rather than to a copy captured
 * before the read, so a keystroke that arrived while the search ran is not overwritten by its result.
 */
public final class SearchPresenter {

    /** What the screen is asked to do besides drawing. */
    public interface Sink {

        void render(SearchViewState state);

        /**
         * Moves to the branch, optionally at a semantic position.
         *
         * <p>{@code anchor} is null for a hit that has no position of its own — a title match — and
         * the shell opens the branch where it was last left. Nothing here decides how the shell
         * navigates; the port exists so the decision of <em>when</em> to move is testable.
         */
        void openAnchor(String topicId, String branchId, ReadingAnchor anchor);

        /**
         * A reachable hit was tapped: the shell moves at the hit's position (T-054).
         *
         * <p>The hit itself travels with the move so a host can act on more than the ids. The
         * default keeps every existing sink working unchanged: opening a hit is still opening its
         * branch, at its anchor when it has one.
         */
        default void openHit(SearchHit hit) {
            openAnchor(hit.topicId, hit.branchId, hit.locatorAnchor());
        }
    }

    private final Sink sink;
    private final UiThread ui;
    private final Executor background;
    private final SearchRepository repository;

    private SearchViewState state;

    public SearchPresenter(Sink sink, UiThread ui, Executor background, SearchRepository repository) {
        this.sink = require(sink, "sink");
        this.ui = require(ui, "ui");
        this.background = require(background, "background");
        this.repository = require(repository, "repository");
        this.state = SearchViewState.initial();
    }

    /** The last state handed to the sink; the tests read it, the screen does not. */
    public SearchViewState state() {
        return state;
    }

    /** Opening the screen: it starts in its idle phase. */
    public void start() {
        render(state);
    }

    /** A keystroke, a paste or a cleared field. */
    public void onQueryChanged(String text) {
        String draft = text == null ? "" : text;
        SearchQuery query = SearchQuery.of(draft);
        if (query.isEmpty()) {
            render(state.withDraft(draft).withHits(Collections.<SearchHit>emptyList())
                    .withPhase(SearchViewState.Phase.IDLE));
            return;
        }
        // The previous results stay on screen while the new query runs: a list that blanks on every
        // keystroke is unreadable, and the state is replaced when the answer arrives.
        render(state.withDraft(draft).withPhase(SearchViewState.Phase.SEARCHING));
        background.execute(() -> search(query));
    }

    /** Coming back to the screen: the tree may have changed, so the same question is asked again. */
    public void onResumed() {
        SearchQuery query = SearchQuery.of(state.draft);
        if (query.isEmpty()) {
            render(state.withPhase(SearchViewState.Phase.IDLE));
            return;
        }
        render(state.withPhase(SearchViewState.Phase.SEARCHING));
        background.execute(() -> search(query));
    }

    /**
     * A result was tapped.
     *
     * <p>A hit whose branch is gone is dropped from the list instead of opening something that is not
     * there: the result list is a snapshot, and archiving or deleting a branch between the query and
     * the tap is an ordinary thing for the learner to have done.
     */
    public void onHitOpened(SearchHit hit) {
        if (hit == null) {
            return;
        }
        // Reachability is a store read, so it runs off the UI thread like every other read here; only
        // the verdict comes back to the UI thread, to move the screen or to drop the row.
        background.execute(() -> {
            final boolean reachable = repository.isStillReachable(hit);
            ui.post(() -> applyTap(hit, reachable));
        });
    }

    private void applyTap(SearchHit hit, boolean reachable) {
        if (!reachable) {
            List<SearchHit> remaining = new ArrayList<SearchHit>(state.hits);
            remaining.remove(hit);
            render(state.withHits(remaining));
            return;
        }
        sink.openHit(hit);
    }

    private void search(SearchQuery query) {
        try {
            List<SearchHit> hits = repository.search(query);
            ui.post(() -> apply(query, hits, null));
        } catch (RuntimeException failure) {
            // A store failure is a state, not a crash: the presenter reports it and the draft stays.
            ui.post(() -> apply(query, null, failure));
        }
    }

    private void apply(SearchQuery query, List<SearchHit> hits, RuntimeException failure) {
        if (!query.normalized().equals(SearchQuery.of(state.draft).normalized())) {
            // The draft moved on while this query ran. Rendering it would answer a question the
            // learner has already replaced, so the result is dropped rather than shown.
            return;
        }
        if (failure != null) {
            render(state.withPhase(SearchViewState.Phase.ERROR));
            return;
        }
        render(state.withHits(hits).withPhase(SearchViewState.Phase.RESULTS));
    }

    private void render(SearchViewState next) {
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
