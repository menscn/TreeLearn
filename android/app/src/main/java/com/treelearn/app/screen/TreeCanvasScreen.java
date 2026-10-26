package com.treelearn.app.screen;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;

import com.treelearn.app.R;
import com.treelearn.app.ui.PopupPlacement;
import com.treelearn.app.ui.TreeCanvasActions;
import com.treelearn.app.ui.TreeCanvasViewState;
import com.treelearn.treelayout.BranchGeometry;

import java.util.function.Consumer;

/**
 * The canvas screen: the drawable, the four phases, the control bar and the node card.
 *
 * <p>It owns no decision. The phase picks the overlay, the selected branch decides what the fold
 * control says, and the card's position arrives already computed - the view only places it at the pixel
 * the state named, which is what let the flip rule be settled without a window to measure.
 *
 * <p>The two-column variant is a resource, not a branch in code:
 * {@code layout-sw600dp/screen_tree.xml} places the reserved branch-list host beside the canvas and
 * includes the same column body the phone uses, so the two configurations cannot drift.
 * {@link #navigationHost()} is where that list is attached once its own slice provides one; a phone has
 * no such host and the method answers null.
 */
public final class TreeCanvasScreen extends FrameLayout {

    private final TreeCanvasView canvas;
    private final TextView notice;
    private final View loading;
    private final View empty;
    private final View emptyAction;
    private final View error;
    /** The fold control as an icon (058); its image and description follow the selected branch. */
    private final ImageButton fold;
    private final View open;
    private final View navHost;
    private final TextView canvasTitle;
    private final TextView canvasSubtitle;
    private final View zoomIn;
    private final View zoomOut;
    private final View center;

    private TreeCanvasActions actions;
    private TreeCanvasViewState state;
    private PopupWindow popup;
    private TextView popupTitle;
    private TextView popupSummary;
    private TextView popupUnread;
    private Button popupFold;
    /** True while the screen itself closes the card, so the dismiss listener stays silent. */
    private boolean closingPopup;

    public TreeCanvasScreen(Context context) {
        this(context, null);
    }

    public TreeCanvasScreen(Context context, AttributeSet attributes) {
        super(context, attributes);
        LayoutInflater.from(context).inflate(R.layout.screen_tree, this, true);
        canvas = findViewById(R.id.tree_canvas);
        notice = findViewById(R.id.tree_notice);
        loading = findViewById(R.id.tree_loading);
        empty = findViewById(R.id.tree_empty);
        emptyAction = findViewById(R.id.tree_empty_action);
        error = findViewById(R.id.tree_error);
        fold = findViewById(R.id.tree_fold);
        open = findViewById(R.id.tree_open);
        navHost = findViewById(R.id.tree_nav_host);
        canvasTitle = findViewById(R.id.tree_title);
        canvasSubtitle = findViewById(R.id.tree_subtitle);
        zoomIn = findViewById(R.id.tree_zoom_in);
        zoomOut = findViewById(R.id.tree_zoom_out);
        center = findViewById(R.id.tree_center);

        findViewById(R.id.tree_back).setOnClickListener(view -> fire(TreeCanvasActions::onBackPressed));
        emptyAction.setOnClickListener(view -> fire(TreeCanvasActions::onBackPressed));
        findViewById(R.id.tree_fit).setOnClickListener(view -> fire(TreeCanvasActions::onFit));
        findViewById(R.id.tree_fit_tool).setOnClickListener(view -> fire(TreeCanvasActions::onFit));
        zoomIn.setOnClickListener(view -> fire(actions -> actions.onZoom(1.18f,
                canvas.getWidth() / 2f, canvas.getHeight() / 2f)));
        zoomOut.setOnClickListener(view -> fire(actions -> actions.onZoom(0.85f,
                canvas.getWidth() / 2f, canvas.getHeight() / 2f)));
        center.setOnClickListener(view -> fire(TreeCanvasActions::onCenterCurrent));
        fold.setOnClickListener(view -> fire(actions -> actions.onCollapseToggled(
                state == null ? null : state.currentBranchId)));
        open.setOnClickListener(view -> fire(TreeCanvasActions::onOpenCurrent));
        findViewById(R.id.tree_nav)
                .setOnClickListener(view -> fire(TreeCanvasActions::onNavigationPressed));
    }

    public void setActions(TreeCanvasActions value) {
        this.actions = value;
        canvas.setActions(value);
    }

    /** The reserved branch-list column on a tablet; null on a phone, which has a single column. */
    public View navigationHost() {
        return navHost;
    }

    /**
     * T-017 device-test seam: whether the node popup card is currently on screen. The screen does
     * not decide anything here — the field's own lifecycle (render places it, render takes it
     * down) is the single source of truth this method reads back.
     */
    public boolean isPopupShowing() {
        return popup != null && popup.isShowing();
    }

    public void render(TreeCanvasViewState next) {
        this.state = next;
        boolean ready = next.phase == TreeCanvasViewState.Phase.READY;
        loading.setVisibility(next.phase == TreeCanvasViewState.Phase.LOADING ? VISIBLE : GONE);
        empty.setVisibility(next.phase == TreeCanvasViewState.Phase.EMPTY ? VISIBLE : GONE);
        emptyAction.setVisibility(next.phase == TreeCanvasViewState.Phase.EMPTY ? VISIBLE : GONE);
        error.setVisibility(next.phase == TreeCanvasViewState.Phase.ERROR ? VISIBLE : GONE);
        notice.setVisibility(next.notice == null ? GONE : VISIBLE);
        if (next.notice != null) {
            notice.setText(R.string.tree_storage_failed);
        }
        canvasTitle.setText(R.string.tree_canvas_title);
        String rootTitle = next.snapshot == null ? "" : next.titleOf(next.snapshot.rootId());
        canvasSubtitle.setText(rootTitle);
        canvasSubtitle.setVisibility(rootTitle.isEmpty() ? GONE : VISIBLE);
        BranchGeometry current = next.layout.branch(next.currentBranchId);
        fold.setEnabled(ready && current != null);
        renderFoldControl(current);
        open.setEnabled(ready && next.currentBranchId != null);
        canvas.render(next);
        placePopup(next);
    }

    /**
     * The fold control's icon follows the branch's state: a bud unfolds, everything else folds - the
     * same rule the control's text used to spell out, now carried by the icon pair and spoken by the
     * content description instead.
     */
    private void renderFoldControl(BranchGeometry branch) {
        boolean unfold = branch != null && branch.kind == BranchGeometry.Kind.BUD;
        fold.setImageResource(unfold ? R.drawable.ic_tree_unfold : R.drawable.ic_tree_fold);
        fold.setContentDescription(getContext().getString(
                unfold ? R.string.cd_tree_unfold : R.string.cd_tree_fold));
    }

    /**
     * What the node card's fold control says for its branch (the card keeps its text carrier; only
     * the bar switched to icons): a bud unfolds, everything else folds.
     */
    private int foldable(BranchGeometry branch) {
        return branch != null && branch.kind == BranchGeometry.Kind.BUD ? R.string.tree_unfold
                : R.string.tree_fold;
    }

    /**
     * Places the card at the pixel the state computed. The card wraps its own content, so text at a large
     * font scale is never cropped; the design height is what the flip was decided with, and a card that
     * grows past it is shifted up rather than pushed off the bottom edge.
     */
    private void placePopup(TreeCanvasViewState frame) {
        PopupPlacement placement = frame.popup;
        if (placement == null) {
            closePopup();
            return;
        }
        if (popup == null) {
            buildPopup();
        }
        popupTitle.setText(frame.titleOf(placement.branchId));
        String summary = frame.summaryOf(placement.branchId);
        popupSummary.setText(summary);
        popupSummary.setVisibility(summary.isEmpty() ? GONE : VISIBLE);
        BranchGeometry branch = frame.layout.branch(placement.branchId);
        int unread = branch == null ? 0 : branch.unreadTotal();
        popupUnread.setText(getResources().getString(R.string.tree_node_unread, unread));
        popupUnread.setVisibility(unread > 0 ? VISIBLE : GONE);
        popupFold.setText(foldable(branch));

        int[] origin = new int[2];
        canvas.getLocationInWindow(origin);
        int width = Math.round(placement.width);
        int x = origin[0] + Math.round(placement.x);
        int y = origin[1] + Math.round(placement.y);
        if (popup.isShowing()) {
            popup.update(x, y, width, LinearLayout.LayoutParams.WRAP_CONTENT);
        } else {
            popup.setWidth(width);
            popup.showAtLocation(canvas, Gravity.NO_GRAVITY, x, y);
        }
    }

    /** Built once from a layout, so the card's text follows the system font scale like every other view. */
    private void buildPopup() {
        LinearLayout content = (LinearLayout) LayoutInflater.from(getContext())
                .inflate(R.layout.view_node_popup, null, false);
        popupTitle = content.findViewById(R.id.node_popup_title);
        popupSummary = content.findViewById(R.id.node_popup_summary);
        popupUnread = content.findViewById(R.id.node_popup_unread);
        popupFold = content.findViewById(R.id.node_popup_fold);
        popupFold.setOnClickListener(view -> fire(actions -> actions.onCollapseToggled(
                state == null || state.popup == null ? null : state.popup.branchId)));
        content.findViewById(R.id.node_popup_open)
                .setOnClickListener(view -> fire(TreeCanvasActions::onOpenCurrent));

        popup = new PopupWindow(content, LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT, true);
        popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popup.setOutsideTouchable(true);
        popup.setOnDismissListener(() -> {
            if (!closingPopup) {
                fire(TreeCanvasActions::onDismissPopup);
            }
        });
    }

    private void closePopup() {
        if (popup != null && popup.isShowing()) {
            closingPopup = true;
            popup.dismiss();
            closingPopup = false;
        }
    }

    private void fire(Consumer<TreeCanvasActions> gesture) {
        if (actions != null) {
            gesture.accept(actions);
        }
    }
}
