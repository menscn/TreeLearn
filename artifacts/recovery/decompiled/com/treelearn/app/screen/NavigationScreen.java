/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  android.app.AlertDialog
 *  android.app.AlertDialog$Builder
 *  android.content.Context
 *  android.util.AttributeSet
 *  android.view.LayoutInflater
 *  android.view.View
 *  android.view.ViewGroup
 *  android.widget.Button
 *  android.widget.LinearLayout
 *  android.widget.PopupMenu
 *  android.widget.TextView
 *  com.treelearn.app.R$id
 *  com.treelearn.app.R$layout
 *  com.treelearn.app.R$string
 */
package com.treelearn.app.screen;

import android.app.AlertDialog;
import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
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

public final class NavigationScreen
extends LinearLayout {
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
    private String shownDelete;
    private AlertDialog dialog;

    public NavigationScreen(Context context) {
        this(context, null);
    }

    public NavigationScreen(Context context, AttributeSet attributes) {
        super(context, attributes);
        this.setOrientation(1);
        LayoutInflater.from((Context)context).inflate(R.layout.screen_navigation, (ViewGroup)this, true);
        this.summaryCard = this.findViewById(R.id.navigation_summary_card);
        this.summaryHead = this.findViewById(R.id.navigation_summary_head);
        this.summaryBody = this.findViewById(R.id.navigation_summary_body);
        this.currentTitle = (TextView)this.findViewById(R.id.navigation_current_title);
        this.currentChip = (TextView)this.findViewById(R.id.navigation_current_chip);
        this.summaryFold = (TextView)this.findViewById(R.id.navigation_summary_fold);
        this.summaryText = (TextView)this.findViewById(R.id.navigation_summary_text);
        this.summaryStale = (TextView)this.findViewById(R.id.navigation_summary_stale);
        this.rowsSection = (TextView)this.findViewById(R.id.navigation_rows_section);
        this.notice = (TextView)this.findViewById(R.id.navigation_notice);
        this.phase = (TextView)this.findViewById(R.id.navigation_phase);
        this.archivedSection = this.findViewById(R.id.navigation_archived_section);
        this.rows = (LinearLayout)this.findViewById(R.id.navigation_rows);
        this.archivedRows = (LinearLayout)this.findViewById(R.id.navigation_archived_rows);
        this.enter = (Button)this.findViewById(R.id.navigation_enter);
        this.canvas = (Button)this.findViewById(R.id.navigation_canvas);
        this.findViewById(R.id.navigation_back).setOnClickListener(view -> this.leave());
        this.summaryHead.setOnClickListener(view -> this.fire(NavigationActions::onSummaryToggled));
        this.enter.setOnClickListener(view -> this.openSelected(false));
        this.canvas.setOnClickListener(view -> this.openSelected(true));
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
            case LOADING: {
                this.showPhase(R.string.navigation_loading);
                break;
            }
            case ERROR: {
                this.showPhase(R.string.navigation_error);
                break;
            }
            case MISSING: {
                this.showPhase(R.string.navigation_missing);
                break;
            }
            default: {
                this.phase.setVisibility(8);
            }
        }
        this.notice.setVisibility(next.notice == null ? 8 : 0);
        if (next.notice != null) {
            this.notice.setText(this.noticeText(next.notice));
        }
        NavigationViewState.Row current = this.currentRow();
        this.summaryCard.setVisibility(ready ? 0 : 8);
        this.rowsSection.setVisibility(ready ? 0 : 8);
        this.currentTitle.setText((CharSequence)(current == null ? "" : current.title));
        this.currentChip.setVisibility(current != null && !current.archivable ? 0 : 8);
        this.summaryFold.setText(next.summaryCollapsed ? R.string.navigation_unfold : R.string.navigation_fold);
        this.summaryBody.setVisibility(next.summaryCollapsed ? 8 : 0);
        this.summaryStale.setVisibility(next.summaryStale ? 0 : 8);
        this.summaryText.setText((CharSequence)this.summary());
        this.enter.setEnabled(next.selectedBranchId != null);
        this.canvas.setEnabled(next.selectedBranchId != null);
        this.renderRows(next.visible, this.rows, false);
        this.renderRows(next.archived, this.archivedRows, true);
        this.archivedSection.setVisibility(next.archived.isEmpty() ? 8 : 0);
        this.renderDelete(next);
    }

    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        this.dismissDialog();
    }

    private NavigationViewState.Row currentRow() {
        if (this.state == null || this.state.selectedBranchId == null) {
            return null;
        }
        NavigationViewState.Row row = NavigationScreen.rowIn(this.state.visible, this.state.selectedBranchId);
        return row != null ? row : NavigationScreen.rowIn(this.state.archived, this.state.selectedBranchId);
    }

    private String summary() {
        if (this.state == null) {
            return "";
        }
        if (this.state.summary == null || this.state.summary.isEmpty()) {
            return this.getContext().getString(R.string.navigation_summary_empty);
        }
        StringBuilder text = new StringBuilder(this.state.summary);
        for (String point : this.state.keyPoints) {
            text.append('\n').append('\u00b7').append(' ').append(point);
        }
        return text.toString();
    }

    private void renderRows(List<NavigationViewState.Row> list, LinearLayout target, boolean archived) {
        target.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from((Context)this.getContext());
        for (NavigationViewState.Row row : list) {
            View item = inflater.inflate(R.layout.item_navigation_row, (ViewGroup)target, false);
            ((TextView)item.findViewById(R.id.navigation_row_title)).setText((CharSequence)row.title);
            ((TextView)item.findViewById(R.id.navigation_row_meta)).setText((CharSequence)this.getContext().getString(row.current ? R.string.navigation_row_current : R.string.navigation_row_children, new Object[]{row.childCount}));
            Button more = (Button)item.findViewById(R.id.navigation_row_more);
            Button restore = (Button)item.findViewById(R.id.navigation_row_restore);
            more.setVisibility(archived ? 8 : 0);
            restore.setVisibility(archived ? 0 : 8);
            if (archived) {
                restore.setOnClickListener(view -> this.fire(actions -> actions.onRestore(row.branchId)));
            } else {
                more.setOnClickListener(view -> this.openMenu((View)more, row));
                item.setOnClickListener(view -> this.fire(actions -> actions.onBranchSelected(row.branchId)));
            }
            target.addView(item);
        }
    }

    private void openMenu(View anchor, NavigationViewState.Row row) {
        boolean folded = this.state != null && this.state.collapsed.contains(row.branchId);
        PopupMenu popup = new PopupMenu(this.getContext(), anchor);
        popup.getMenu().add(0, 1, 0, folded ? R.string.navigation_unfold : R.string.navigation_fold);
        if (row.archivable) {
            popup.getMenu().add(0, 2, 0, R.string.navigation_archive);
            popup.getMenu().add(0, 3, 0, R.string.navigation_delete);
        }
        popup.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case 1: {
                    this.fire(actions -> actions.onGroupToggled(row.branchId));
                    return true;
                }
                case 2: {
                    this.fire(actions -> actions.onArchive(row.branchId));
                    return true;
                }
                case 3: {
                    this.fire(actions -> actions.onDeleteRequested(row.branchId, NavigationActions.DeleteScope.SUBTREE));
                    return true;
                }
            }
            throw new IllegalArgumentException("unmapped row action: " + item.getItemId());
        });
        popup.show();
    }

    private void renderDelete(NavigationViewState next) {
        NavigationData.Plan plan = next.pendingDelete;
        if (plan == null) {
            this.shownDelete = null;
            return;
        }
        String key = plan.targetBranchId + ":" + plan.branchCount();
        if (key.equals(this.shownDelete)) {
            return;
        }
        this.shownDelete = key;
        this.dismissDialog();
        this.dialog = new AlertDialog.Builder(this.getContext()).setTitle(R.string.navigation_delete_title).setMessage((CharSequence)this.getContext().getString(R.string.navigation_delete_body, new Object[]{this.titleOf(plan.targetBranchId), plan.branchCount()})).setNegativeButton(R.string.navigation_cancel, (target, which) -> this.fire(NavigationActions::onDeleteCancelled)).setPositiveButton(R.string.navigation_delete_ok, (target, which) -> this.fire(NavigationActions::onDeleteConfirmed)).setOnCancelListener(target -> this.fire(NavigationActions::onDeleteCancelled)).create();
        this.dialog.show();
    }

    private void dismissDialog() {
        if (this.dialog != null) {
            this.dialog.dismiss();
            this.dialog = null;
        }
    }

    private void showPhase(int text) {
        this.phase.setText(text);
        this.phase.setVisibility(0);
    }

    private void leave() {
        if (this.host != null) {
            this.host.leaveNavigation();
        }
    }

    private void openSelected(boolean asCanvas) {
        if (this.host == null || this.state == null || this.state.selectedBranchId == null) {
            return;
        }
        if (asCanvas) {
            this.host.openOverview(this.state.selectedBranchId);
        } else {
            this.host.openBranch(this.state.selectedBranchId);
        }
    }

    private String titleOf(String branchId) {
        if (this.state == null) {
            return "";
        }
        NavigationViewState.Row row = NavigationScreen.rowIn(this.state.visible, branchId);
        if (row == null) {
            row = NavigationScreen.rowIn(this.state.archived, branchId);
        }
        return row == null ? "" : row.title;
    }

    private static NavigationViewState.Row rowIn(List<NavigationViewState.Row> rows, String branchId) {
        for (NavigationViewState.Row row : rows) {
            if (!row.branchId.equals(branchId)) continue;
            return row;
        }
        return null;
    }

    private int noticeText(NavigationViewState.Notice value) {
        switch (value) {
            case STORE_UNREADABLE: {
                return R.string.navigation_notice_store_unreadable;
            }
            case BRANCH_GONE: {
                return R.string.navigation_notice_branch_gone;
            }
            case ALREADY_ARCHIVED: {
                return R.string.navigation_notice_already_archived;
            }
            case NOT_ARCHIVED: {
                return R.string.navigation_notice_not_archived;
            }
            case SCOPE_UNRESOLVABLE: {
                return R.string.navigation_notice_scope_unresolvable;
            }
        }
        throw new IllegalArgumentException("unmapped navigation notice: " + (Object)((Object)value));
    }

    private void fire(Consumer<NavigationActions> gesture) {
        if (this.actions != null) {
            gesture.accept(this.actions);
        }
    }

    public static interface Host {
        public void leaveNavigation();

        public void openBranch(String var1);

        public void openOverview(String var1);
    }
}

