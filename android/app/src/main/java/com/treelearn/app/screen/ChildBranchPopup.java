package com.treelearn.app.screen;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.LayoutInflater;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;

import com.treelearn.app.R;
import com.treelearn.app.ui.ChildBranch;

import java.util.List;

/**
 * The child-branch list of one answer (PRD 6.2.20 point 7): the branches a fork already created from
 * it, and the way into one of them.
 *
 * <p><strong>The rows are handed in, not looked up.</strong> Which branches are a child of this answer
 * and which of them are archived is a read the presenter does off the UI thread, so this class draws
 * the list it was given and reports the tap; it never asks the store anything.
 *
 * <p>A tap is the only thing that navigates. The popup closes itself as it reports, so a dismissal -
 * a tap outside, or the back gesture - closes the list without opening any branch.
 */
public final class ChildBranchPopup {

    /** Where a tapped row goes; opening the branch is the host's job, not this popup's. */
    public interface Listener {

        void onBranchChosen(String branchId);
    }

    private final PopupWindow popup;

    private ChildBranchPopup(PopupWindow popup) {
        this.popup = popup;
    }

    /**
     * Opens the list around the view that asked for it. The card is measured first and placed in the
     * host window, preferring the space above the anchor and clamping to a safe margin when neither
     * side has enough room.
     *
     * <p>The rows are added <em>before</em> the card is measured. Measuring the empty card - the
     * order this used to carry - returned a near-zero height for a card whose title and scroll box
     * are both {@code match_parent}, so {@code setHeight} pinned the window to that nothing and the
     * tap read as dead: the window opened, but there was nothing in it to see. The width is pinned
     * as well ({@code popup_width}), because an UNSPECIFIED measure of a wrap-content card cannot
     * resolve what its match-parent children want. Placement is then calculated in the host window,
     * because a bottom action row must open upward rather than underneath the composer.
     */
    public static ChildBranchPopup show(View anchor, List<ChildBranch> children,
            Listener listener) {
        Context context = anchor.getContext();
        LinearLayout content = (LinearLayout) LayoutInflater.from(context)
                .inflate(R.layout.popup_child_branches, null, false);
        ((TextView) content.findViewById(R.id.child_branches_title)).setText(
                context.getString(R.string.chat_children_popup_title, children.size()));
        LinearLayout list = content.findViewById(R.id.child_branches_list);
        list.setDividerDrawable(new ColorDrawable(context.getColor(R.color.tl_line)));
        list.setShowDividers(LinearLayout.SHOW_DIVIDER_MIDDLE);
        list.setDividerPadding(context.getResources().getDimensionPixelSize(R.dimen.space_s2));
        PopupWindow popup = new PopupWindow(content, LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT, true);
        popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popup.setOutsideTouchable(true);
        popup.setElevation(context.getResources().getDimension(R.dimen.card_elevation));
        ChildBranchPopup shown = new ChildBranchPopup(popup);
        LayoutInflater inflater = LayoutInflater.from(context);
        for (ChildBranch child : children) {
            list.addView(row(inflater, list, context, child, shown, listener));
        }
        View root = anchor.getRootView();
        int marginPx = context.getResources().getDimensionPixelSize(R.dimen.popup_margin);
        int desiredWidthPx = context.getResources().getDimensionPixelSize(R.dimen.popup_width);
        int widthPx = Math.max(1, Math.min(desiredWidthPx, root.getWidth() - marginPx * 2));
        int maxPx = context.getResources().getDimensionPixelSize(R.dimen.popup_max_height);
        content.measure(
                View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(maxPx, View.MeasureSpec.AT_MOST));
        popup.setWidth(widthPx);
        popup.setHeight(content.getMeasuredHeight());
        int[] anchorLocation = new int[2];
        anchor.getLocationInWindow(anchorLocation);
        int x = leftFor(anchorLocation[0], widthPx, root.getWidth(), marginPx);
        int y = topFor(anchorLocation[1], anchorLocation[1] + anchor.getHeight(),
                content.getMeasuredHeight(), root.getHeight(), marginPx);
        popup.showAtLocation(anchor, Gravity.NO_GRAVITY, x, y);
        return shown;
    }

    static int topFor(int anchorTop, int anchorBottom, int popupHeight, int windowHeight,
            int margin) {
        int above = anchorTop - popupHeight - margin;
        int below = anchorBottom + margin;
        int preferred = above >= margin ? above : below;
        return clamp(preferred, margin, windowHeight - popupHeight - margin);
    }

    static int leftFor(int anchorLeft, int popupWidth, int windowWidth, int margin) {
        return clamp(anchorLeft, margin, windowWidth - popupWidth - margin);
    }

    private static int clamp(int value, int minimum, int maximum) {
        if (maximum < minimum) {
            return minimum;
        }
        return Math.max(minimum, Math.min(maximum, value));
    }

    /** Closes the list; a list that is already gone stays closed. */
    public void close() {
        if (popup.isShowing()) {
            popup.dismiss();
        }
    }

    private static View row(LayoutInflater inflater, LinearLayout list, Context context,
            ChildBranch child, ChildBranchPopup shown, Listener listener) {
        View row = inflater.inflate(R.layout.item_child_branch, list, false);
        ((TextView) row.findViewById(R.id.child_branch_title)).setText(child.title);
        TextView unread = row.findViewById(R.id.child_branch_unread);
        unread.setText(context.getString(R.string.chat_children_unread, child.unreadCount));
        unread.setVisibility(child.unreadCount > 0 ? View.VISIBLE : View.GONE);
        row.setOnClickListener(view -> {
            shown.close();
            listener.onBranchChosen(child.branchId);
        });
        return row;
    }
}
