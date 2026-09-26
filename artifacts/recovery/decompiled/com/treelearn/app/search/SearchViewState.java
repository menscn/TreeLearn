/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.treelearn.core.domain.SearchHit
 */
package com.treelearn.app.search;

import com.treelearn.core.domain.SearchHit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class SearchViewState {
    public final Phase phase;
    public final String draft;
    public final List<SearchHit> hits;

    private SearchViewState(Phase phase, String draft, List<SearchHit> hits) {
        this.phase = phase;
        this.draft = draft;
        this.hits = Collections.unmodifiableList(new ArrayList<SearchHit>(hits));
    }

    public static SearchViewState initial() {
        return new SearchViewState(Phase.IDLE, "", Collections.emptyList());
    }

    public SearchViewState withPhase(Phase next) {
        return new SearchViewState(next, this.draft, this.hits);
    }

    public SearchViewState withDraft(String next) {
        return new SearchViewState(this.phase, next == null ? "" : next, this.hits);
    }

    public SearchViewState withHits(List<SearchHit> next) {
        return new SearchViewState(this.phase, this.draft, next);
    }

    public boolean showsResults() {
        return this.phase == Phase.RESULTS && !this.hits.isEmpty();
    }

    public String toString() {
        return "SearchViewState[" + (Object)((Object)this.phase) + " draft=" + this.draft.length() + " chars, " + this.hits.size() + " hit(s)]";
    }

    public static enum Phase {
        IDLE,
        SEARCHING,
        RESULTS,
        ERROR;

    }
}

