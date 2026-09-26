package com.treelearn.app.search;

import com.treelearn.core.domain.SearchHit;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * What the search page draws, decided once and without an Android type in sight.
 *
 * <p>It exists for the same reason {@link SearchViewState} does: the difference between "nothing typed"
 * and "nothing found" is a product decision, not a rendering detail, and a decision that only lives
 * inside a {@code View} is a decision no test can reach. The screen paints what this describes and
 * decides nothing itself.
 *
 * <p><strong>The rows are the state's hits, in the state's order.</strong> Nothing is dropped for
 * looking uninteresting and nothing is moved for looking out of place: the store owns what a result is
 * and in what order it arrives, and a page that re-filtered would be a second answer to a question
 * already answered - the drift the design forbids.
 */
public final class SearchPage {

    /** The five things the learner can be looking at. */
    public enum Pane {
        /** Nothing typed yet: the page says what it searches. */
        GUIDE,
        /** A query is in flight; the answer that was already on screen stays there. */
        SEARCHING,
        /** An answer with rows. */
        LIST,
        /** An answer with no rows - a state of its own, not an empty list. */
        EMPTY,
        /** The store could not be read; the draft is still in the field. */
        ERROR
    }

    public final Pane pane;
    /** Never null; empty unless {@link #pane} is {@link Pane#LIST}. */
    public final List<Row> rows;

    private SearchPage(Pane pane, List<Row> rows) {
        this.pane = pane;
        this.rows = Collections.unmodifiableList(new ArrayList<Row>(rows));
    }

    /**
     * The page for one state, naming each row's branch through {@code titles}.
     *
     * <p>Each distinct branch is asked for once: a query that matched four answers in one branch is one
     * question, not four. A hit whose branch cannot be named keeps its row with an empty title rather
     * than disappearing - "I forgot which branch" is answered worse by a hole in the list than by a row
     * that only names its snippet.
     */
    public static SearchPage of(SearchViewState state, BranchTitles titles) {
        require(state, "state");
        require(titles, "titles");
        Pane pane = paneOf(state);
        if (pane != Pane.LIST) {
            return new SearchPage(pane, Collections.<Row>emptyList());
        }
        Map<String, String> named = new HashMap<String, String>();
        Map<String, BranchTitles.Badge> sited = new HashMap<String, BranchTitles.Badge>();
        List<Row> rows = new ArrayList<Row>(state.hits.size());
        for (SearchHit hit : state.hits) {
            String title = named.get(hit.branchId);
            if (title == null) {
                String found = titles.titleOf(hit.branchId);
                title = found == null ? "" : found;
                named.put(hit.branchId, title);
            }
            BranchTitles.Badge badge = sited.get(hit.branchId);
            if (badge == null && !sited.containsKey(hit.branchId)) {
                badge = titles.badgeOf(hit.branchId);
                sited.put(hit.branchId, badge);
            }
            rows.add(new Row(hit, title, badge));
        }
        return new SearchPage(pane, rows);
    }

    /** Which of the five things one state is; the screen switches on this and on nothing else. */
    public static Pane paneOf(SearchViewState state) {
        switch (require(state, "state").phase) {
            case IDLE:
                return Pane.GUIDE;
            case SEARCHING:
                return Pane.SEARCHING;
            case RESULTS:
                return state.hits.isEmpty() ? Pane.EMPTY : Pane.LIST;
            case ERROR:
            default:
                return Pane.ERROR;
        }
    }

    /** One listed hit, with the name of the branch it came from and where that branch sits. */
    public static final class Row {

        public final SearchHit hit;
        /** Never null; empty when no branch row carries the id. */
        public final String branchTitle;
        /** The branch's ancestor path, or empty when it cannot be named (T-054). */
        public final String sourcePath;
        /** Whether the branch the hit came from is currently archived (T-054). */
        public final boolean archived;

        Row(SearchHit hit, String branchTitle, BranchTitles.Badge badge) {
            this.hit = hit;
            this.branchTitle = branchTitle;
            this.sourcePath = badge == null ? "" : badge.path;
            this.archived = badge != null && badge.archived;
        }
    }

    private static <T> T require(T value, String name) {
        if (value == null) {
            throw new IllegalArgumentException(name + " must not be null");
        }
        return value;
    }
}
