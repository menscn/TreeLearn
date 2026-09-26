/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.treelearn.core.domain.ReadingAnchor
 *  com.treelearn.core.domain.SearchHit
 *  com.treelearn.core.domain.SearchQuery
 */
package com.treelearn.app.search;

import com.treelearn.app.search.SearchRepository;
import com.treelearn.app.search.SearchViewState;
import com.treelearn.app.ui.UiThread;
import com.treelearn.core.domain.ReadingAnchor;
import com.treelearn.core.domain.SearchHit;
import com.treelearn.core.domain.SearchQuery;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executor;

public final class SearchPresenter {
    private final Sink sink;
    private final UiThread ui;
    private final Executor background;
    private final SearchRepository repository;
    private SearchViewState state;

    public SearchPresenter(Sink sink, UiThread ui, Executor background, SearchRepository repository) {
        this.sink = SearchPresenter.require(sink, "sink");
        this.ui = SearchPresenter.require(ui, "ui");
        this.background = SearchPresenter.require(background, "background");
        this.repository = SearchPresenter.require(repository, "repository");
        this.state = SearchViewState.initial();
    }

    public SearchViewState state() {
        return this.state;
    }

    public void start() {
        this.render(this.state);
    }

    public void onQueryChanged(String text) {
        String draft = text == null ? "" : text;
        SearchQuery query = SearchQuery.of((String)draft);
        if (query.isEmpty()) {
            this.render(this.state.withDraft(draft).withHits(Collections.emptyList()).withPhase(SearchViewState.Phase.IDLE));
            return;
        }
        this.render(this.state.withDraft(draft).withPhase(SearchViewState.Phase.SEARCHING));
        this.background.execute(() -> this.search(query));
    }

    public void onResumed() {
        SearchQuery query = SearchQuery.of((String)this.state.draft);
        if (query.isEmpty()) {
            this.render(this.state.withPhase(SearchViewState.Phase.IDLE));
            return;
        }
        this.render(this.state.withPhase(SearchViewState.Phase.SEARCHING));
        this.background.execute(() -> this.search(query));
    }

    public void onHitOpened(SearchHit hit) {
        if (hit == null) {
            return;
        }
        this.background.execute(() -> {
            boolean reachable = this.repository.isStillReachable(hit);
            this.ui.post(() -> this.applyTap(hit, reachable));
        });
    }

    private void applyTap(SearchHit hit, boolean reachable) {
        if (!reachable) {
            ArrayList<SearchHit> remaining = new ArrayList<SearchHit>(this.state.hits);
            remaining.remove(hit);
            this.render(this.state.withHits(remaining));
            return;
        }
        this.sink.openAnchor(hit.topicId, hit.branchId, hit.locatorAnchor());
    }

    private void search(SearchQuery query) {
        try {
            List<SearchHit> hits = this.repository.search(query);
            this.ui.post(() -> this.apply(query, hits, null));
        }
        catch (RuntimeException failure) {
            this.ui.post(() -> this.apply(query, null, failure));
        }
    }

    private void apply(SearchQuery query, List<SearchHit> hits, RuntimeException failure) {
        if (!query.normalized().equals(SearchQuery.of((String)this.state.draft).normalized())) {
            return;
        }
        if (failure != null) {
            this.render(this.state.withPhase(SearchViewState.Phase.ERROR));
            return;
        }
        this.render(this.state.withHits(hits).withPhase(SearchViewState.Phase.RESULTS));
    }

    private void render(SearchViewState next) {
        this.state = next;
        this.sink.render(next);
    }

    private static <T> T require(T value, String name) {
        if (value == null) {
            throw new IllegalArgumentException(name + " must not be null");
        }
        return value;
    }

    public static interface Sink {
        public void render(SearchViewState var1);

        public void openAnchor(String var1, String var2, ReadingAnchor var3);
    }
}

