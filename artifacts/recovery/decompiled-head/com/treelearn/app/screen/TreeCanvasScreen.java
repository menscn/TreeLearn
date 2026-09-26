/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  android.content.Context
 *  android.graphics.drawable.ColorDrawable
 *  android.graphics.drawable.Drawable
 *  android.util.AttributeSet
 *  android.view.LayoutInflater
 *  android.view.View
 *  android.view.ViewGroup
 *  android.widget.Button
 *  android.widget.FrameLayout
 *  android.widget.LinearLayout
 *  android.widget.PopupWindow
 *  android.widget.TextView
 *  com.treelearn.app.R$id
 *  com.treelearn.app.R$layout
 *  com.treelearn.app.R$string
 *  com.treelearn.treelayout.BranchGeometry
 *  com.treelearn.treelayout.BranchGeometry$Kind
 */
package com.treelearn.app.screen;

import android.content.Context;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;
import com.treelearn.app.R;
import com.treelearn.app.screen.TreeCanvasView;
import com.treelearn.app.ui.PopupPlacement;
import com.treelearn.app.ui.TreeCanvasActions;
import com.treelearn.app.ui.TreeCanvasViewState;
import com.treelearn.treelayout.BranchGeometry;
import java.util.function.Consumer;

public final class TreeCanvasScreen
extends FrameLayout {
    private final TreeCanvasView canvas;
    private final TextView notice;
    private final View loading;
    private final View empty;
    private final View error;
    private final Button fold;
    private final Button open;
    private final View navHost;
    private TreeCanvasActions actions;
    private TreeCanvasViewState state;
    private PopupWindow popup;
    private TextView popupTitle;
    private TextView popupUnread;
    private Button popupFold;
    private boolean closingPopup;

    public TreeCanvasScreen(Context context) {
        this(context, null);
    }

    public TreeCanvasScreen(Context context, AttributeSet attributes) {
        super(context, attributes);
        LayoutInflater.from((Context)context).inflate(R.layout.screen_tree, (ViewGroup)this, true);
        this.canvas = (TreeCanvasView)this.findViewById(R.id.tree_canvas);
        this.notice = (TextView)this.findViewById(R.id.tree_notice);
        this.loading = this.findViewById(R.id.tree_loading);
        this.empty = this.findViewById(R.id.tree_empty);
        this.error = this.findViewById(R.id.tree_error);
        this.fold = (Button)this.findViewById(R.id.tree_fold);
        this.open = (Button)this.findViewById(R.id.tree_open);
        this.navHost = this.findViewById(R.id.tree_nav_host);
        this.findViewById(R.id.tree_back).setOnClickListener(view -> this.fire(TreeCanvasActions::onBackPressed));
        this.findViewById(R.id.tree_fit).setOnClickListener(view -> this.fire(TreeCanvasActions::onFit));
        this.fold.setOnClickListener(view -> this.fire(actions -> actions.onCollapseToggled(this.state == null ? null : this.state.currentBranchId)));
        this.open.setOnClickListener(view -> this.fire(TreeCanvasActions::onOpenCurrent));
        this.findViewById(R.id.tree_nav).setOnClickListener(view -> this.fire(TreeCanvasActions::onNavigationPressed));
    }

    public void setActions(TreeCanvasActions value) {
        this.actions = value;
        this.canvas.setActions(value);
    }

    public View navigationHost() {
        return this.navHost;
    }

    public void render(TreeCanvasViewState next) {
        this.state = next;
        boolean ready = next.phase == TreeCanvasViewState.Phase.READY;
        this.loading.setVisibility(next.phase == TreeCanvasViewState.Phase.LOADING ? 0 : 8);
        this.empty.setVisibility(next.phase == TreeCanvasViewState.Phase.EMPTY ? 0 : 8);
        this.error.setVisibility(next.phase == TreeCanvasViewState.Phase.ERROR ? 0 : 8);
        this.notice.setVisibility(next.notice == null ? 8 : 0);
        if (next.notice != null) {
            this.notice.setText(R.string.tree_storage_failed);
        }
        BranchGeometry current = next.layout.branch(next.currentBranchId);
        this.fold.setEnabled(ready && current != null);
        this.fold.setText(this.foldable(current));
        this.open.setEnabled(ready && next.currentBranchId != null);
        this.canvas.render(next);
        this.placePopup(next);
    }

    private int foldable(BranchGeometry branch) {
        return branch != null && branch.kind == BranchGeometry.Kind.BUD ? R.string.tree_unfold : R.string.tree_fold;
    }

    private void placePopup(TreeCanvasViewState frame) {
        PopupPlacement placement = frame.popup;
        if (placement == null) {
            this.closePopup();
            return;
        }
        if (this.popup == null) {
            this.buildPopup();
        }
        this.popupTitle.setText((CharSequence)frame.titleOf(placement.branchId));
        BranchGeometry branch = frame.layout.branch(placement.branchId);
        int unread = branch == null ? 0 : branch.unreadTotal();
        this.popupUnread.setText((CharSequence)this.getResources().getString(R.string.tree_node_unread, new Object[]{unread}));
        this.popupUnread.setVisibility(unread > 0 ? 0 : 8);
        this.popupFold.setText(this.foldable(branch));
        int[] origin = new int[2];
        this.canvas.getLocationInWindow(origin);
        int width = Math.round(placement.width);
        int x = origin[0] + Math.round(placement.x);
        int y = origin[1] + Math.round(placement.y);
        if (this.popup.isShowing()) {
            this.popup.update(x, y, width, -2);
        } else {
            this.popup.setWidth(width);
            this.popup.showAtLocation((View)this.canvas, 0, x, y);
        }
    }

    private void buildPopup() {
        LinearLayout content = (LinearLayout)LayoutInflater.from((Context)this.getContext()).inflate(R.layout.view_node_popup, null, false);
        this.popupTitle = (TextView)content.findViewById(R.id.node_popup_title);
        this.popupUnread = (TextView)content.findViewById(R.id.node_popup_unread);
        this.popupFold = (Button)content.findViewById(R.id.node_popup_fold);
        this.popupFold.setOnClickListener(view -> this.fire(actions -> actions.onCollapseToggled(this.state == null || this.state.popup == null ? null : this.state.popup.branchId)));
        content.findViewById(R.id.node_popup_open).setOnClickListener(view -> this.fire(TreeCanvasActions::onOpenCurrent));
        this.popup = new PopupWindow((View)content, -2, -2, true);
        this.popup.setBackgroundDrawable((Drawable)new ColorDrawable(0));
        this.popup.setOutsideTouchable(true);
        this.popup.setOnDismissListener(() -> {
            if (!this.closingPopup) {
                this.fire(TreeCanvasActions::onDismissPopup);
            }
        });
    }

    private void closePopup() {
        if (this.popup != null && this.popup.isShowing()) {
            this.closingPopup = true;
            this.popup.dismiss();
            this.closingPopup = false;
        }
    }

    private void fire(Consumer<TreeCanvasActions> gesture) {
        if (this.actions != null) {
            gesture.accept(this.actions);
        }
    }
}

