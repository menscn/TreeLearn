package com.treelearn.app.screen;

import android.app.AlertDialog;
import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;

import com.treelearn.app.R;
import com.treelearn.app.navigation.NavigationActions;
import com.treelearn.app.navigation.NavigationData;
import com.treelearn.app.navigation.NavigationViewState;

import java.util.List;
import java.util.function.Consumer;

/**
 * The navigation page as a view (technical design 12, prototype v2.5 {@code renderNav}).
 *
 * <p>It owns no rule. Which branches are visible, which one is selected, what is archived, what the
 * summary says and which deletion is waiting for a confirmation all arrive in one
 * {@link NavigationViewState}, and every gesture is forwarded to {@link NavigationActions}. That is why
 * the root's missing archive control is not computed here either: the state marks the row
 * {@code archivable = false} (BASE-14) and this class draws what it was handed.
 *
 * <p>Two details are worth knowing. The confirmation is opened once per plan - a re-render while it is
 * up must not stack a second dialog - and its text names the number of branches the deletion removes,
 * which is the one number a learner has to see before an irreversible act. And opening another screen is
 * navigation without a decision, so it goes through {@link Host} the same way the settings screen's back
 * does, instead of growing a second presenter contract for a button that only changes the top screen.
 */
public final class NavigationScreen extends LinearLayout {

    /** The moves out of this page that only the host can make. */
    public interface Host {

        /** Leaves the page, back to the conversation it was opened from. */
        void leaveNavigation();

        /** Enters the conversation on one branch. */
        void openBranch(String branchId);

        /** Opens the topic's canvas standing on one branch. */
        void openOverview(String branchId);
    }

    private static final int ACTION_FOLD = 1;
    private static final int ACTION_ARCHIVE = 2;
    private static final int ACTION_DELETE = 3;

    private final View summaryCard;
    private final View summaryHead;
    private final View summaryBody;
    private final TextView currentTitle;
    private final TextView currentChip;
    private final TextView summaryFold;
    private final TextView summaryText;
    private final TextView summaryStale;
    private final TextView rowsSection;
    private final TextView notice;
    private final TextView phase;
    private final View archivedSection;
    private final LinearLayout rows;
    private final LinearLayout archivedRows;
    private final Button enter;
    private final Button canvas;

    private NavigationActions actions;
    private Host host;
    private NavigationViewState state;
    /** The confirmation already on screen, keyed by the plan it was built from. */
    private String shownDelete;
    private AlertDialog dialog;

    public NavigationScreen(Context context) {
        this(context, null);
    }

    public NavigationScreen(Context context, AttributeSet attributes) {
        super(context, attributes);
        setOrientation(VERTICAL);
        LayoutInflater.from(context).inflate(R.layout.screen_navigation, this, true);

        summaryCard = findViewById(R.id.navigation_summary_card);
        summaryHead = findViewById(R.id.navigation_summary_head);
        summaryBody = findViewById(R.id.navigation_summary_body);
        currentTitle = findViewById(R.id.navigation_current_title);
        currentChip = findViewById(R.id.navigation_current_chip);
        summaryFold = findViewById(R.id.navigation_summary_fold);
        summaryText = findViewById(R.id.navigation_summary_text);
        summaryStale = findViewById(R.id.navigation_summary_stale);
        rowsSection = findViewById(R.id.navigation_rows_section);
        notice = findViewById(R.id.navigation_notice);
        phase = findViewById(R.id.navigation_phase);
        archivedSection = findViewById(R.id.navigation_archived_section);
        rows = findViewById(R.id.navigation_rows);
        archivedRows = findViewById(R.id.navigation_archived_rows);
        enter = findViewById(R.id.navigation_enter);
        canvas = findViewById(R.id.navigation_canvas);

        findViewById(R.id.navigation_back).setOnClickListener(view -> leave());
        summaryHead.setOnClickListener(view -> fire(NavigationActions::onSummaryToggled));
        enter.setOnClickListener(view -> openSelected(false));
        canvas.setOnClickListener(view -> openSelected(true));
    }

    public void setActions(NavigationActions value) {
        this.actions = value;
    }

    public void setHost(Host value) {
        this.host = value;
    }

    public void render(NavigationViewState next) {
        this.state = next;
        boolean ready = next.phase == NavigationViewState.Phase.READY;
        switch (next.phase) {
            case LOADING:
                showPhase(R.string.navigation_loading);
                break;
            case ERROR:
                showPhase(R.string.navigation_error);
                break;
            case MISSING:
                showPhase(R.string.navigation_missing);
                break;
            default:
                phase.setVisibility(GONE);
                break;
        }
        notice.setVisibility(next.notice == null ? GONE : VISIBLE);
        if (next.notice != null) {
            notice.setText(noticeText(next.notice));
        }
        NavigationViewState.Row current = currentRow();
        summaryCard.setVisibility(ready ? VISIBLE : GONE);
        rowsSection.setVisibility(ready ? VISIBLE : GONE);
        currentTitle.setText(current == null ? "" : current.title);
        currentChip.setVisibility(current != null && !current.archivable ? VISIBLE : GONE);
        summaryFold.setText(next.summaryCollapsed ? R.string.navigation_unfold
                : R.string.navigation_fold);
        summaryBody.setVisibility(next.summaryCollapsed ? GONE : VISIBLE);
        summaryStale.setVisibility(next.summaryStale ? VISIBLE : GONE);
        summaryText.setText(summary());
        enter.setEnabled(next.selectedBranchId != null);
        canvas.setEnabled(next.selectedBranchId != null);
        renderRows(next.visible, rows, false);
        renderRows(next.archived, archivedRows, true);
        archivedSection.setVisibility(next.archived.isEmpty() ? GONE : VISIBLE);
        renderDelete(next);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        dismissDialog();
    }

    /** The row the strip describes: the branch the learner selected, live or archived. */
    private NavigationViewState.Row currentRow() {
        if (state == null || state.selectedBranchId == null) {
            return null;
        }
        NavigationViewState.Row row = rowIn(state.visible, state.selectedBranchId);
        return row != null ? row : rowIn(state.archived, state.selectedBranchId);
    }

    /** The branch's own summary and its key points, or the sentence an absent summary gets. */
    private String summary() {
        if (state == null) {
            return "";
        }
        if (state.summary == null || state.summary.isEmpty()) {
            return getContext().getString(R.string.navigation_summary_empty);
        }
        StringBuilder text = new StringBuilder(state.summary);
        for (String point : state.keyPoints) {
            text.append('\n').append('\u00b7').append(' ').append(point);
        }
        return text.toString();
    }

    /**
     * Draws one list. A live row selects its branch and carries the row menu; an archived row offers the
     * restore that puts it back and nothing else, which is what keeps "hidden" from meaning "gone".
     */
    private void renderRows(List<NavigationViewState.Row> list, LinearLayout target,
            boolean archived) {
        target.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(getContext());
        for (final NavigationViewState.Row row : list) {
            View item = inflater.inflate(R.layout.item_navigation_row, target, false);
            ((TextView) item.findViewById(R.id.navigation_row_title)).setText(row.title);
            ((TextView) item.findViewById(R.id.navigation_row_meta)).setText(getContext().getString(
                    row.current ? R.string.navigation_row_current : R.string.navigation_row_children,
                    row.childCount));
            Button more = item.findViewById(R.id.navigation_row_more);
            Button restore = item.findViewById(R.id.navigation_row_restore);
            more.setVisibility(archived ? GONE : VISIBLE);
            restore.setVisibility(archived ? VISIBLE : GONE);
            if (archived) {
                restore.setOnClickListener(view -> fire(actions -> actions.onRestore(row.branchId)));
            } else {
                more.setOnClickListener(view -> openMenu(more, row));
                item.setOnClickListener(view -> fire(
                        actions -> actions.onBranchSelected(row.branchId)));
            }
            target.addView(item);
        }
    }

    /** A row's own menu. The archive and delete arms exist only where the state allows them. */
    private void openMenu(View anchor, final NavigationViewState.Row row) {
        boolean folded = state != null && state.collapsed.contains(row.branchId);
        PopupMenu popup = new PopupMenu(getContext(), anchor);
        popup.getMenu().add(Menu.NONE, ACTION_FOLD, Menu.NONE,
                folded ? R.string.navigation_unfold : R.string.navigation_fold);
        if (row.archivable) {
            popup.getMenu().add(Menu.NONE, ACTION_ARCHIVE, Menu.NONE, R.string.navigation_archive);
            popup.getMenu().add(Menu.NONE, ACTION_DELETE, Menu.NONE, R.string.navigation_delete);
        }
        popup.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case ACTION_FOLD:
                    fire(actions -> actions.onGroupToggled(row.branchId));
                    return true;
                case ACTION_ARCHIVE:
                    fire(actions -> actions.onArchive(row.branchId));
                    return true;
                case ACTION_DELETE:
                    fire(actions -> actions.onDeleteRequested(row.branchId,
                            NavigationActions.DeleteScope.SUBTREE));
                    return true;
                default:
                    throw new IllegalArgumentException("unmapped row action: " + item.getItemId());
            }
        });
        popup.show();
    }

    /**
     * The confirmation, opened once per plan. Its text names the branch and the exact number of branches
     * the deletion removes - the scope the presenter counted before offering it - and nothing is written
     * until the learner confirms.
     */
    private void renderDelete(NavigationViewState next) {
        NavigationData.Plan plan = next.pendingDelete;
        if (plan == null) {
            shownDelete = null;
            return;
        }
        String key = plan.targetBranchId + ":" + plan.branchCount();
        if (key.equals(shownDelete)) {
            return;
        }
        shownDelete = key;
        dismissDialog();
        dialog = new AlertDialog.Builder(getContext())
                .setTitle(R.string.navigation_delete_title)
                .setMessage(getContext().getString(R.string.navigation_delete_body,
                        titleOf(plan.targetBranchId), plan.branchCount()))
                .setNegativeButton(R.string.navigation_cancel,
                        (target, which) -> fire(NavigationActions::onDeleteCancelled))
                .setPositiveButton(R.string.navigation_delete_ok,
                        (target, which) -> fire(NavigationActions::onDeleteConfirmed))
                .setOnCancelListener(target -> fire(NavigationActions::onDeleteCancelled))
                .create();
        dialog.show();
    }

    private void dismissDialog() {
        if (dialog != null) {
            dialog.dismiss();
            dialog = null;
        }
    }

    private void showPhase(int text) {
        phase.setText(text);
        phase.setVisibility(VISIBLE);
    }

    private void leave() {
        if (host != null) {
            host.leaveNavigation();
        }
    }

    private void openSelected(boolean asCanvas) {
        if (host == null || state == null || state.selectedBranchId == null) {
            return;
        }
        if (asCanvas) {
            host.openOverview(state.selectedBranchId);
        } else {
            host.openBranch(state.selectedBranchId);
        }
    }

    private String titleOf(String branchId) {
        if (state == null) {
            return "";
        }
        NavigationViewState.Row row = rowIn(state.visible, branchId);
        if (row == null) {
            row = rowIn(state.archived, branchId);
        }
        return row == null ? "" : row.title;
    }

    private static NavigationViewState.Row rowIn(List<NavigationViewState.Row> rows,
            String branchId) {
        for (NavigationViewState.Row row : rows) {
            if (row.branchId.equals(branchId)) {
                return row;
            }
        }
        return null;
    }

    /** One sentence per refusal category, and a refusal for a category this screen has no words for. */
    private int noticeText(NavigationViewState.Notice value) {
        switch (value) {
            case STORE_UNREADABLE:
                return R.string.navigation_notice_store_unreadable;
            case BRANCH_GONE:
                return R.string.navigation_notice_branch_gone;
            case ALREADY_ARCHIVED:
                return R.string.navigation_notice_already_archived;
            case NOT_ARCHIVED:
                return R.string.navigation_notice_not_archived;
            case SCOPE_UNRESOLVABLE:
                return R.string.navigation_notice_scope_unresolvable;
            default:
                throw new IllegalArgumentException("unmapped navigation notice: " + value);
        }
    }

    private void fire(Consumer<NavigationActions> gesture) {
        if (actions != null) {
            gesture.accept(actions);
        }
    }
}
