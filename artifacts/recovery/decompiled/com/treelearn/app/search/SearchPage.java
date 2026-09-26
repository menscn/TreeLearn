/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.treelearn.core.domain.SearchHit
 */
package com.treelearn.app.search;

import com.treelearn.app.search.BranchTitles;
import com.treelearn.app.search.SearchViewState;
import com.treelearn.core.domain.SearchHit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

public final class SearchPage {
    public final Pane pane;
    public final List<Row> rows;

    private SearchPage(Pane pane, List<Row> rows) {
        this.pane = pane;
        this.rows = Collections.unmodifiableList(new ArrayList<Row>(rows));
    }

    public static SearchPage of(SearchViewState state, BranchTitles titles) {
        SearchPage.require(state, "state");
        SearchPage.require(titles, "titles");
        Pane pane = SearchPage.paneOf(state);
        if (pane != Pane.LIST) {
            return new SearchPage(pane, Collections.emptyList());
        }
        HashMap<String, String> named = new HashMap<String, String>();
        ArrayList<Row> rows = new ArrayList<Row>(state.hits.size());
        for (SearchHit hit : state.hits) {
            String title = (String)named.get(hit.branchId);
            if (title == null) {
                String found = titles.titleOf(hit.branchId);
                title = found == null ? "" : found;
                named.put(hit.branchId, title);
            }
            rows.add(new Row(hit, title));
        }
        return new SearchPage(pane, rows);
    }

    public static Pane paneOf(SearchViewState state) {
        switch (SearchPage.require(state, (String)"state").phase) {
            case IDLE: {
                return Pane.GUIDE;
            }
            case SEARCHING: {
                return Pane.SEARCHING;
            }
            case RESULTS: {
                return state.hits.isEmpty() ? Pane.EMPTY : Pane.LIST;
            }
        }
        return Pane.ERROR;
    }

    private static <T> T require(T value, String name) {
        if (value == null) {
            throw new IllegalArgumentException(name + " must not be null");
        }
        return value;
    }

    public static enum Pane {
        GUIDE,
        SEARCHING,
        LIST,
        EMPTY,
        ERROR;

    }

    public static final class Row {
        public final SearchHit hit;
        public final String branchTitle;

        Row(SearchHit hit, String branchTitle) {
            this.hit = hit;
            this.branchTitle = branchTitle;
        }
    }
}

