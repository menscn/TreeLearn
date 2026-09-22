package com.treelearn.app.screen;

import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.treelearn.app.R;
import com.treelearn.app.search.SearchPage;
import com.treelearn.app.search.SearchPresenter;
import com.treelearn.app.search.SearchViewState;
import com.treelearn.core.domain.SearchHit;

import java.util.List;

/**
 * The search page as a view (PRD 6.2.8).
 *
 * <p>It owns no decisions. Every keystroke and every tap is forwarded to {@link SearchPresenter}, and
 * every pixel is painted from a {@link SearchPage} the host built: which of the five panes is on
 * screen, what each row says, and in what order the rows come are all answered elsewhere. In
 * particular the screen never re-filters or re-sorts a result list - the store decided what a hit is
 * and the page object kept that order, and a third opinion here is the drift the design forbids.
 *
 * <p>The one piece of local state is the "echo" guard the home composer also uses: the screen does not
 * push text back into the field while the field is the thing that just changed it, so a keystroke
 * never moves the cursor.
 */
public final class SearchScreen extends LinearLayout {

    /** The one thing the screen asks the shell for: leave the page. */
    public interface Host {

        void leaveSearch();
    }

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
        setOrientation(VERTICAL);
        LayoutInflater.from(context).inflate(R.layout.screen_search, this, true);

        query = findViewById(R.id.search_query);
        guide = findViewById(R.id.search_guide);
        searching = findViewById(R.id.search_searching);
        empty = findViewById(R.id.search_empty);
        error = findViewById(R.id.search_error);
        results = findViewById(R.id.search_results);

        query.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence text, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence text, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable text) {
                if (!rendering && actions != null) {
                    actions.onQueryChanged(text.toString());
                }
            }
        });
        findViewById(R.id.search_back).setOnClickListener(view -> {
            if (host != null) {
                host.leaveSearch();
            }
        });
    }

    public void setActions(SearchPresenter actions) {
        this.actions = actions;
    }

    public void setHost(Host host) {
        this.host = host;
    }

    /**
     * Draws one page.
     *
     * <p>The state is here for one thing only: the empty state echoes the query that found nothing, so
     * the learner can see what was actually asked. Everything else comes from the page.
     */
    public void show(SearchViewState state, SearchPage page) {
        guide.setVisibility(page.pane == SearchPage.Pane.GUIDE ? VISIBLE : GONE);
        searching.setVisibility(page.pane == SearchPage.Pane.SEARCHING ? VISIBLE : GONE);
        error.setVisibility(page.pane == SearchPage.Pane.ERROR ? VISIBLE : GONE);
        empty.setVisibility(page.pane == SearchPage.Pane.EMPTY ? VISIBLE : GONE);
        results.setVisibility(page.pane == SearchPage.Pane.LIST ? VISIBLE : GONE);
        if (page.pane == SearchPage.Pane.EMPTY) {
            empty.setText(getContext().getString(R.string.search_empty, state.draft));
        }
        renderRows(page.rows);
    }

    private void renderRows(List<SearchPage.Row> rows) {
        results.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(getContext());
        for (final SearchPage.Row row : rows) {
            View view = inflater.inflate(R.layout.item_search_hit, results, false);
            TextView title = view.findViewById(R.id.hit_branch_title);
            title.setText(row.branchTitle);
            title.setVisibility(row.branchTitle.isEmpty() ? GONE : VISIBLE);
            ((TextView) view.findViewById(R.id.hit_field)).setText(fieldLabel(row.hit.field));
            ((TextView) view.findViewById(R.id.hit_snippet)).setText(row.hit.snippet);
            view.setOnClickListener(tapped -> {
                if (actions != null) {
                    actions.onHitOpened(row.hit);
                }
            });
            results.addView(view);
        }
    }

    /** The chip's word for one matched field: the enum is the store's, the words are the screen's. */
    private String fieldLabel(SearchHit.Field field) {
        switch (field) {
            case TITLE:
                return getContext().getString(R.string.search_field_title);
            case QUESTION:
                return getContext().getString(R.string.search_field_question);
            case ANCHOR:
                return getContext().getString(R.string.search_field_anchor);
            case REASONING:
                return getContext().getString(R.string.search_field_reasoning);
            case PARTIAL_ANSWER:
                return getContext().getString(R.string.search_field_partial_answer);
            case PARTIAL_REASONING:
                return getContext().getString(R.string.search_field_partial_reasoning);
            case ANSWER:
            default:
                return getContext().getString(R.string.search_field_answer);
        }
    }
}
