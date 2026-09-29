package com.treelearn.app.screen;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.LayoutInflater;
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
     * Opens the list under the view that asked for it. The platform's own drop-down placement is used
     * rather than a hand-computed one: it flips above the anchor when the list does not fit below it,
     * which is the one rule this card shares with every other popup in the app.
     *
     * <p>The rows are added <em>before</em> the card is measured. Measuring the empty card - the
     * order this used to carry - returned a near-zero height for a card whose title and scroll box
     * are both {@code match_parent}, so {@code setHeight} pinned the window to that nothing and the
     * tap read as dead: the window opened, but there was nothing in it to see. The width is pinned
     * as well ({@code popup_width}), because an UNSPECIFIED measure of a wrap-content card cannot
     * resolve what its match-parent children want.
     */
    public static ChildBranchPopup show(View anchor, List<ChildBranch> children,
            Listener listener) {
        Context context = anchor.getContext();
        LinearLayout content = (LinearLayout) LayoutInflater.from(context)
                .inflate(R.layout.popup_child_branches, null, false);
        ((TextView) content.findViewById(R.id.child_branches_title)).setText(
                context.getString(R.string.chat_children_popup_title, children.size()));
        LinearLayout list = content.findViewById(R.id.child_branches_list);
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
        int widthPx = context.getResources().getDimensionPixelSize(R.dimen.popup_width);
        int maxPx = context.getResources().getDimensionPixelSize(R.dimen.popup_max_height);
        content.measure(
                View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        popup.setWidth(widthPx);
        popup.setHeight(Math.min(content.getMeasuredHeight(), maxPx));
        popup.showAsDropDown(anchor);
        return shown;
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
