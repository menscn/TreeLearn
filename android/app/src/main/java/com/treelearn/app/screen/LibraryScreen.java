package com.treelearn.app.screen;

import android.content.Context;
import android.content.res.ColorStateList;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.treelearn.app.R;
import com.treelearn.app.library.LibraryPresenter;
import com.treelearn.app.library.LibraryViewState;
import com.treelearn.app.search.SearchPage;
import com.treelearn.app.search.SearchViewState;
import com.treelearn.core.domain.SearchHit;

import java.util.function.Consumer;

/**
 * The library page as a view (technical design 12, PRD 6.2.16 point 4).
 *
 * <p>It owns no rule. Whether the store can be listed at all, which phase the page is in, which
 * segment is selected, what every row says and which deletion is waiting for a confirmation all
 * arrive in one {@link LibraryViewState}, and every gesture - a tapped row, a tapped segment, a
 * restore, a delete and its confirmation - is forwarded to {@link LibraryPresenter}. That is why
 * nothing here asks a database, sorts a list, decides that an empty result is an error or counts how
 * many branches a deletion would remove: the presenter answered all of those before this was drawn.
 *
 * <p>The two segments share one container, {@code library_rows}, so a row sits in the same place
 * whichever segment is on screen, and the screen never keeps a list of its own to swap.
 *
 * <p>Since 054 the search box at the top is a real control and the results replace the list in
 * place. What a query means, when it runs, what an empty answer is and which rows survive a tap are
 * all the search stack's decisions arriving in one {@link SearchPage}; this view paints them and
 * forwards taps, and re-decides nothing - least of all the order the store returned.
 */
public final class LibraryScreen extends LinearLayout {

    private final LinearLayout rows;
    private final TextView state;
    private final TextView segmentInProgress;
    private final TextView segmentDone;
    private final TextView searchScope;
    private final EditText searchInput;

    private LibraryPresenter actions;
    private boolean rendering;

    public LibraryScreen(Context context) {
        super(context);
        setOrientation(VERTICAL);
        LayoutInflater.from(context).inflate(R.layout.screen_library, this, true);

        rows = findViewById(R.id.library_rows);
        state = findViewById(R.id.library_state);
        segmentInProgress = findViewById(R.id.library_segment_in_progress);
        segmentDone = findViewById(R.id.library_segment_done);
        searchScope = findViewById(R.id.library_search_scope);
        searchInput = findViewById(R.id.library_search_input);
        segmentInProgress.setOnClickListener(view -> onSegment(LibraryViewState.Segment.IN_PROGRESS));
        segmentDone.setOnClickListener(view -> onSegment(LibraryViewState.Segment.DONE));
        searchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence text, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence text, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable text) {
                if (!rendering && actions != null) {
                    actions.onSearchQueryChanged(text.toString());
                }
            }
        });
    }

    public void setActions(LibraryPresenter value) {
        this.actions = value;
    }

    public void render(LibraryViewState next) {
        rendering = true;
        try {
            boolean done = next.segment == LibraryViewState.Segment.DONE;
            searchScope.setVisibility(GONE);
            drawSegments(next, done);
            boolean listed = next.phase == LibraryViewState.Phase.READY
                    && !(done ? next.archived.isEmpty() : next.rows.isEmpty());
            rows.setVisibility(listed ? VISIBLE : GONE);
            state.setVisibility(listed ? GONE : VISIBLE);
            if (!listed) {
                state.setText(idleText(next, done));
            }
            rows.removeAllViews();
            if (done) {
                renderArchived(next);
            } else {
                renderTopics(next);
            }
        } finally {
            rendering = false;
        }
    }

    /**
     * Draws one search page in place of the library's own list (T-054).
     *
     * <p>The state is here for one thing only: the empty state echoes the query that found nothing.
     * Which pane is on screen, what each row says and in what order the rows come were all decided
     * before this was called. The two segment pills stay put - tapping one is how the learner walks
     * out of the search state and back to the library's own lists.
     */
    public void showSearch(SearchViewState searchState, SearchPage page) {
        rendering = true;
        try {
            searchScope.setVisibility(VISIBLE);
            rows.removeAllViews();
            boolean listed = page.pane == SearchPage.Pane.LIST;
            rows.setVisibility(listed ? VISIBLE : GONE);
            state.setVisibility(listed ? GONE : VISIBLE);
            if (!listed) {
                state.setText(searchStateText(searchState, page.pane));
            }
            if (listed) {
                renderHits(page);
            }
        } finally {
            rendering = false;
        }
    }

    /** Leaving the search state clears the box, so the library comes back without a stale query. */
    public void leaveSearch() {
        searchInput.setText("");
    }

    private CharSequence searchStateText(SearchViewState searchState, SearchPage.Pane pane) {
        Context context = getContext();
        switch (pane) {
            case SEARCHING:
                return context.getString(R.string.search_searching);
            case EMPTY:
                return context.getString(R.string.search_empty, searchState.draft);
            case ERROR:
                return context.getString(R.string.search_error);
            case LIST:
            case GUIDE:
            default:
                return context.getString(R.string.search_guide);
        }
    }

    /** The one result-row layout, extended in place - never a second set of rows (T-054). */
    private void renderHits(SearchPage page) {
        LayoutInflater inflater = LayoutInflater.from(getContext());
        for (final SearchPage.Row row : page.rows) {
            View view = inflater.inflate(R.layout.item_search_hit, rows, false);
            TextView title = view.findViewById(R.id.hit_branch_title);
            title.setText(row.branchTitle);
            title.setVisibility(row.branchTitle.isEmpty() ? GONE : VISIBLE);
            ((TextView) view.findViewById(R.id.hit_field)).setText(fieldLabel(row.hit.field));
            ((TextView) view.findViewById(R.id.hit_source_path)).setText(row.sourcePath);
            view.findViewById(R.id.hit_source_path)
                    .setVisibility(row.sourcePath.isEmpty() ? GONE : VISIBLE);
            TextView mark = view.findViewById(R.id.hit_archived_mark);
            mark.setVisibility(row.archived ? VISIBLE : GONE);
            if (row.archived) {
                mark.setText(R.string.done_archived_mark);
            }
            ((TextView) view.findViewById(R.id.hit_snippet)).setText(row.hit.snippet);
            view.setOnClickListener(tapped -> {
                if (actions != null) {
                    actions.onSearchHitOpened(row.hit);
                }
            });
            rows.addView(view);
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

    /** The selected pill is the coloured one; the other keeps the page's own neutral fill. */
    private void drawSegments(LibraryViewState next, boolean done) {
        segmentInProgress.setText(getContext().getString(R.string.library_segment_count,
                getContext().getString(R.string.library_segment_in_progress), next.rows.size()));
        segmentDone.setText(getContext().getString(R.string.library_segment_count,
                getContext().getString(R.string.library_segment_done), next.archived.size()));
        tint(segmentInProgress, !done);
        tint(segmentDone, done);
    }

    private void tint(TextView pill, boolean selected) {
        Context context = getContext();
        pill.setBackgroundTintList(ColorStateList.valueOf(context.getColor(
                selected ? R.color.tl_green_soft : R.color.tl_bg)));
        pill.setTextColor(context.getColor(selected ? R.color.tl_green_deep : R.color.tl_ink3));
    }

    private void renderTopics(LibraryViewState next) {
        LayoutInflater inflater = LayoutInflater.from(getContext());
        for (final LibraryViewState.Row row : next.rows) {
            View view = inflater.inflate(R.layout.item_library_topic, rows, false);
            ((TextView) view.findViewById(R.id.library_topic_title)).setText(row.title);
            // The summary draws only when the topic has one: absent means one line fewer, never a
            // placeholder (the same rule the completed segment's rows follow).
            boolean summarised = row.summary != null && !row.summary.trim().isEmpty();
            TextView summary = view.findViewById(R.id.library_topic_summary);
            summary.setVisibility(summarised ? VISIBLE : GONE);
            if (summarised) {
                summary.setText(row.summary);
            }
            ((TextView) view.findViewById(R.id.library_topic_meta)).setText(meta(row));
            view.findViewById(R.id.library_topic_unread)
                    .setVisibility(row.unreadCount > 0 ? VISIBLE : GONE);
            view.setOnClickListener(tapped -> {
                if (actions != null) {
                    actions.onTopicTapped(row);
                }
            });
            rows.addView(view);
        }
    }

    /**
     * One archived branch per row: its title, where it came from, the archived mark, and - only when
     * the branch has one - its own summary. The confirmation is inline and names the number the
     * presenter counted; while no confirmation is pending the row draws two fewer lines and the
     * buttons that would carry it out are not drawn at all.
     */
    private void renderArchived(LibraryViewState next) {
        LayoutInflater inflater = LayoutInflater.from(getContext());
        for (final LibraryViewState.ArchiveRow row : next.archived) {
            View view = inflater.inflate(R.layout.item_library_archived, rows, false);
            ((TextView) view.findViewById(R.id.library_done_title)).setText(row.title);
            ((TextView) view.findViewById(R.id.library_done_mark))
                    .setText(R.string.done_archived_mark);
            ((TextView) view.findViewById(R.id.library_done_meta)).setText(row.sourcePath);
            boolean summarised = row.summary != null && !row.summary.trim().isEmpty();
            TextView summary = view.findViewById(R.id.library_done_summary);
            summary.setVisibility(summarised ? VISIBLE : GONE);
            if (summarised) {
                summary.setText(row.summary);
            }
            boolean pending = next.pendingDelete != null
                    && next.pendingDelete.targetBranchId.equals(row.branchId);
            TextView prompt = view.findViewById(R.id.library_done_prompt);
            prompt.setVisibility(pending ? VISIBLE : GONE);
            view.findViewById(R.id.library_done_actions).setVisibility(pending ? VISIBLE : GONE);
            if (pending) {
                prompt.setText(getContext().getString(R.string.done_delete_prompt, row.title,
                        next.pendingDelete.branchCount()));
            }
            view.findViewById(R.id.library_done_restore).setOnClickListener(
                    tapped -> fire(presenter -> presenter.onRestoreRequested(row)));
            view.findViewById(R.id.library_done_delete).setOnClickListener(
                    tapped -> fire(presenter -> presenter.onDeleteRequested(row)));
            view.findViewById(R.id.library_done_cancel).setOnClickListener(
                    tapped -> fire(LibraryPresenter::onDeleteCancelled));
            view.findViewById(R.id.library_done_confirm).setOnClickListener(
                    tapped -> fire(LibraryPresenter::onDeleteConfirmed));
            rows.addView(view);
        }
    }

    /** The last use is formatted by the platform, so it follows the device's own zone and locale. */
    private CharSequence meta(LibraryViewState.Row row) {
        CharSequence when = DateUtils.getRelativeTimeSpanString(row.lastUsedAt,
                System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS);
        CharSequence line = getContext().getString(R.string.library_topic_meta, row.branchCount, when);
        return row.unreadCount == 0 ? line : line + " \u00b7 "
                + getContext().getString(R.string.library_topic_unread, row.unreadCount);
    }

    /**
     * One sentence per non-listed moment. The two empty states carry different words and different
     * next steps, because "no topic yet" and "nothing archived" are not the same fact.
     */
    private CharSequence idleText(LibraryViewState next, boolean done) {
        switch (next.phase) {
            case ERROR:
                return getContext().getString(R.string.library_error);
            case LOADING:
                return getContext().getString(R.string.library_loading);
            default:
                return done ? getContext().getString(R.string.done_empty) + "\n"
                        + getContext().getString(R.string.done_empty_guide)
                        : getContext().getString(R.string.library_empty) + "\n"
                                + getContext().getString(R.string.library_empty_guide);
        }
    }

    private void onSegment(LibraryViewState.Segment segment) {
        fire(presenter -> presenter.onSegmentSelected(segment));
    }

    private void fire(Consumer<LibraryPresenter> gesture) {
        if (actions != null) {
            gesture.accept(actions);
        }
    }
}
