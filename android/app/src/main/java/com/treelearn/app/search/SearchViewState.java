package com.treelearn.app.search;

import com.treelearn.core.domain.SearchHit;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * What the search screen is asked to draw.
 *
 * <p>Four phases, and each one is a decision rather than a rendering detail: nothing typed yet, a
 * query in flight, results, and a store that could not be read. The draft is part of the state and
 * survives all four, because a failure must leave the learner's text where it was — retyping a
 * question because the store hiccuped is the behaviour this shape exists to prevent.
 */
public final class SearchViewState {

    public enum Phase {
        IDLE,
        SEARCHING,
        RESULTS,
        ERROR
    }

    public final Phase phase;
    public final String draft;
    /** Never null; unmodifiable. Ordered by {@link SearchHit#DISPLAY_ORDER}. */
    public final List<SearchHit> hits;

    private SearchViewState(Phase phase, String draft, List<SearchHit> hits) {
        this.phase = phase;
        this.draft = draft;
        this.hits = Collections.unmodifiableList(new ArrayList<SearchHit>(hits));
    }

    public static SearchViewState initial() {
        return new SearchViewState(Phase.IDLE, "", Collections.<SearchHit>emptyList());
    }

    public SearchViewState withPhase(Phase next) {
        return new SearchViewState(next, draft, hits);
    }

    public SearchViewState withDraft(String next) {
        return new SearchViewState(phase, next == null ? "" : next, hits);
    }

    public SearchViewState withHits(List<SearchHit> next) {
        return new SearchViewState(phase, draft, next);
    }

    /** True only when the screen has something to list; an empty result is not a result list. */
    public boolean showsResults() {
        return phase == Phase.RESULTS && !hits.isEmpty();
    }

    @Override
    public String toString() {
        return "SearchViewState[" + phase + " draft=" + draft.length() + " chars, " + hits.size()
                + " hit(s)]";
    }
}