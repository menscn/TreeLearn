package com.treelearn.app.screen;

import android.content.Context;
import android.graphics.Typeface;
import android.text.Editable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextWatcher;
import android.text.format.DateUtils;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
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
    private final View segmentGroup;
    private final TextView segmentSummary;
    private final TextView segmentInProgress;
    private final TextView segmentDone;
    private final TextView searchScope;
    private final EditText searchInput;
    private final View note;

    private LibraryPresenter actions;
    private Runnable onBack;
    private boolean rendering;

    public LibraryScreen(Context context) {
        super(context);
        setOrientation(VERTICAL);
        LayoutInflater.from(context).inflate(R.layout.screen_library, this, true);

        rows = findViewById(R.id.library_rows);
        state = findViewById(R.id.library_state);
        segmentGroup = findViewById(R.id.library_segment);
        segmentSummary = findViewById(R.id.library_segment_summary);
        segmentInProgress = findViewById(R.id.library_segment_in_progress);
        segmentDone = findViewById(R.id.library_segment_done);
        searchScope = findViewById(R.id.library_search_scope);
        searchInput = findViewById(R.id.library_search_input);
        note = findViewById(R.id.library_note);
        findViewById(R.id.library_back).setOnClickListener(view -> {
            if (onBack != null) {
                onBack.run();
            }
        });
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

    /**
     * Wires the page's own back arrow. The library is drawn from both a tab and (on the tablet) the
     * same column, so the screen does not decide what back means - it reports the tap and lets
     * {@code MainActivity}'s single back decision answer it, exactly as the system gesture does.
     */
    public void setOnBackPressed(Runnable onBack) {
        this.onBack = onBack;
    }

    public void render(LibraryViewState next) {
        rendering = true;
        try {
            boolean done = shownSegment(next) == LibraryViewState.Segment.DONE;
            searchScope.setVisibility(GONE);
            note.setVisibility(VISIBLE);
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
     * before this was called. The capsule keeps the visibility the last library render gave it -
     * tapping a half is one way out of the search state, and that way survives whenever the capsule
     * exists. The one-sided summary line is hidden here: it names a count of rows that are not on
     * screen, and the search field already says where the learner is. When the capsule is hidden
     * (only one segment has rows) the way out is back, which clears the query.
     *
     * <p>An empty query is not a search page: the guide pane is the prototype's search <em>screen</em>,
     * and this implementation inlines search into the library, so there is nowhere for the guide to
     * stand. The library's own lists are the one thing an empty box can be showing, so this paints
     * nothing and leaves them in place - which is what makes clearing the box land on the lists
     * rather than on a guide that has no screen to belong to.
     */
    public void showSearch(SearchViewState searchState, SearchPage page) {
        if (page.pane == SearchPage.Pane.GUIDE) {
            return;
        }
        rendering = true;
        try {
            searchScope.setVisibility(VISIBLE);
            segmentSummary.setVisibility(GONE);
            note.setVisibility(GONE);
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

    /**
     * Leaving the search state clears the box, so the library comes back without a stale query.
     *
     * <p>Returns whether there was a query to clear. That answer is what tells back's two cases
     * apart - a live query is what back consumes first, and only a library already showing its own
     * lists steps out to the home tab - and it is read from the field that actually holds the text
     * rather than asked of the search stack a second time.
     */
    public boolean leaveSearch() {
        Editable typed = searchInput.getText();
        boolean hadQuery = typed != null && typed.length() > 0;
        searchInput.setText("");
        return hadQuery;
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

    /**
     * The segment the page can actually show. While both sides have rows the presenter's stored
     * selection decides; when only one side has rows {@link #drawSegments} hides the capsule and
     * names that side, so the list follows it too. A label that names 进行中 over a page that says
     * 已完成里还没有 is worse than ignoring a switch the learner can no longer see - and this is the
     * path a deletion takes when it empties the segment that was on screen.
     */
    private static LibraryViewState.Segment shownSegment(LibraryViewState next) {
        if (next.phase != LibraryViewState.Phase.READY) {
            return next.segment;
        }
        if (next.rows.isEmpty() && !next.archived.isEmpty()) {
            return LibraryViewState.Segment.DONE;
        }
        if (next.archived.isEmpty() && !next.rows.isEmpty()) {
            return LibraryViewState.Segment.IN_PROGRESS;
        }
        return next.segment;
    }

    /**
     * The selected half wears Scheme A's bright glass face; the other is transparent inside the same
     * capsule. The count is a smaller span of the same text rather than a second view, so the two
     * halves keep one text contract ("进行中 2") for readers and for the instrumented tests.
     *
     * <p><strong>One side with rows means no capsule.</strong> Since T-2026-10-25-102 the capsule is
     * drawn only when both segments have something to show. A two-half control whose other half is
     * empty switches to nothing, and it costs 44dp above the first row; the board replaces it with one
     * line naming the live segment and its count. That line is a label, not a control, so it is not
     * focusable and carries no tap. Both halves are still measured and drawn when the capsule is
     * hidden, so switching back costs no new state - it is the same two views.
     */
    private void drawSegments(LibraryViewState next, boolean done) {
        segmentInProgress.setText(segment(R.string.library_segment_in_progress,
                next.rows.size(), !done));
        segmentDone.setText(segment(R.string.library_segment_done,
                next.archived.size(), done));
        face(segmentInProgress, !done);
        face(segmentDone, done);

        boolean inProgress = !next.rows.isEmpty();
        boolean archived = !next.archived.isEmpty();
        boolean both = inProgress && archived;
        segmentGroup.setVisibility(both ? VISIBLE : GONE);
        // While the page is loading, failing, or empty on both sides there is nothing to name: the
        // one line would either read "0 个主题" or invent a count the store has not answered yet.
        boolean sole = next.phase == LibraryViewState.Phase.READY && inProgress != archived;
        segmentSummary.setVisibility(sole ? VISIBLE : GONE);
        if (sole) {
            segmentSummary.setText(soleSegment(inProgress
                    ? R.string.library_segment_in_progress : R.string.library_segment_done,
                    inProgress ? next.rows.size() : next.archived.size(),
                    inProgress ? R.string.library_unit_topics : R.string.library_unit_branches));
        }
    }

    /** The board's .lstate: the segment's name, then its count in the smaller secondary band. */
    private CharSequence soleSegment(int name, int count, int unit) {
        Context context = getContext();
        String label = context.getString(name);
        String text = label + " " + context.getString(unit, count);
        SpannableStringBuilder span = new SpannableStringBuilder(text);
        int start = label.length() + 1;
        float ratio = context.getResources().getDimension(R.dimen.text_library_segment_num)
                / context.getResources().getDimension(R.dimen.text_library_segment);
        span.setSpan(new RelativeSizeSpan(ratio), start, text.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        span.setSpan(new StyleSpan(Typeface.NORMAL), start, text.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        span.setSpan(new ForegroundColorSpan(context.getColor(R.color.tl_ink3)),
                start, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return span;
    }

    private CharSequence segment(int name, int count, boolean selected) {
        Context context = getContext();
        String text = context.getString(R.string.library_segment_count,
                context.getString(name), count);
        SpannableStringBuilder span = new SpannableStringBuilder(text);
        int start = text.lastIndexOf(' ') + 1;
        float ratio = context.getResources().getDimension(R.dimen.text_library_segment_num)
                / context.getResources().getDimension(R.dimen.text_library_segment);
        int base = context.getColor(selected ? R.color.tl_green_deep : R.color.tl_ink3);
        span.setSpan(new RelativeSizeSpan(ratio), start, text.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        span.setSpan(new ForegroundColorSpan((base & 0x00FFFFFF) | 0xB3000000),
                start, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return span;
    }

    private void face(TextView pill, boolean selected) {
        Context context = getContext();
        pill.setBackgroundResource(selected ? R.drawable.bg_segment_active : 0);
        pill.setTextColor(context.getColor(selected ? R.color.tl_green_deep : R.color.tl_ink3));
        pill.setTypeface(null, selected ? Typeface.BOLD : Typeface.NORMAL);
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
            view.findViewById(R.id.library_done_confirm_row).setVisibility(pending ? VISIBLE : GONE);
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
