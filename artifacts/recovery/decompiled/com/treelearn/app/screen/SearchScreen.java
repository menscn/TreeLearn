/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  android.content.Context
 *  android.text.Editable
 *  android.text.TextWatcher
 *  android.view.LayoutInflater
 *  android.view.View
 *  android.view.ViewGroup
 *  android.widget.EditText
 *  android.widget.LinearLayout
 *  android.widget.TextView
 *  com.treelearn.app.R$id
 *  com.treelearn.app.R$layout
 *  com.treelearn.app.R$string
 *  com.treelearn.core.domain.SearchHit$Field
 */
package com.treelearn.app.screen;

import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.treelearn.app.R;
import com.treelearn.app.search.SearchPage;
import com.treelearn.app.search.SearchPresenter;
import com.treelearn.app.search.SearchViewState;
import com.treelearn.core.domain.SearchHit;
import java.util.List;

public final class SearchScreen
extends LinearLayout {
    private final EditText query;
    private final TextView guide;
    private final TextView searching;
    private final TextView empty;
    private final TextView error;
    private final LinearLayout results;
    private SearchPresenter actions;
    private Host host;
    private boolean rendering;

    public SearchScreen(Context context) {
        super(context);
        this.setOrientation(1);
        LayoutInflater.from((Context)context).inflate(R.layout.screen_search, (ViewGroup)this, true);
        this.query = (EditText)this.findViewById(R.id.search_query);
        this.guide = (TextView)this.findViewById(R.id.search_guide);
        this.searching = (TextView)this.findViewById(R.id.search_searching);
        this.empty = (TextView)this.findViewById(R.id.search_empty);
        this.error = (TextView)this.findViewById(R.id.search_error);
        this.results = (LinearLayout)this.findViewById(R.id.search_results);
        this.query.addTextChangedListener(new TextWatcher(){

            public void beforeTextChanged(CharSequence text, int start, int count, int after) {
            }

            public void onTextChanged(CharSequence text, int start, int before, int count) {
            }

            public void afterTextChanged(Editable text) {
                if (!SearchScreen.this.rendering && SearchScreen.this.actions != null) {
                    SearchScreen.this.actions.onQueryChanged(text.toString());
                }
            }
        });
        this.findViewById(R.id.search_back).setOnClickListener(view -> {
            if (this.host != null) {
                this.host.leaveSearch();
            }
        });
    }

    public void setActions(SearchPresenter actions) {
        this.actions = actions;
    }

    public void setHost(Host host) {
        this.host = host;
    }

    public void show(SearchViewState state, SearchPage page) {
        this.guide.setVisibility(page.pane == SearchPage.Pane.GUIDE ? 0 : 8);
        this.searching.setVisibility(page.pane == SearchPage.Pane.SEARCHING ? 0 : 8);
        this.error.setVisibility(page.pane == SearchPage.Pane.ERROR ? 0 : 8);
        this.empty.setVisibility(page.pane == SearchPage.Pane.EMPTY ? 0 : 8);
        this.results.setVisibility(page.pane == SearchPage.Pane.LIST ? 0 : 8);
        if (page.pane == SearchPage.Pane.EMPTY) {
            this.empty.setText((CharSequence)this.getContext().getString(R.string.search_empty, new Object[]{state.draft}));
        }
        this.renderRows(page.rows);
    }

    private void renderRows(List<SearchPage.Row> rows) {
        this.results.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from((Context)this.getContext());
        for (SearchPage.Row row : rows) {
            View view = inflater.inflate(R.layout.item_search_hit, (ViewGroup)this.results, false);
            TextView title = (TextView)view.findViewById(R.id.hit_branch_title);
            title.setText((CharSequence)row.branchTitle);
            title.setVisibility(row.branchTitle.isEmpty() ? 8 : 0);
            ((TextView)view.findViewById(R.id.hit_field)).setText((CharSequence)this.fieldLabel(row.hit.field));
            ((TextView)view.findViewById(R.id.hit_snippet)).setText((CharSequence)row.hit.snippet);
            view.setOnClickListener(tapped -> {
                if (this.actions != null) {
                    this.actions.onHitOpened(row.hit);
                }
            });
            this.results.addView(view);
        }
    }

    private String fieldLabel(SearchHit.Field field) {
        switch (field) {
            case TITLE: {
                return this.getContext().getString(R.string.search_field_title);
            }
            case QUESTION: {
                return this.getContext().getString(R.string.search_field_question);
            }
            case ANCHOR: {
                return this.getContext().getString(R.string.search_field_anchor);
            }
            case REASONING: {
                return this.getContext().getString(R.string.search_field_reasoning);
            }
            case PARTIAL_ANSWER: {
                return this.getContext().getString(R.string.search_field_partial_answer);
            }
            case PARTIAL_REASONING: {
                return this.getContext().getString(R.string.search_field_partial_reasoning);
            }
        }
        return this.getContext().getString(R.string.search_field_answer);
    }

    public static interface Host {
        public void leaveSearch();
    }
}

