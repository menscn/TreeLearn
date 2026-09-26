/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  android.content.Context
 *  android.graphics.drawable.ColorDrawable
 *  android.graphics.drawable.Drawable
 *  android.view.LayoutInflater
 *  android.view.View
 *  android.view.ViewGroup
 *  android.widget.LinearLayout
 *  android.widget.PopupWindow
 *  android.widget.TextView
 *  com.treelearn.app.R$dimen
 *  com.treelearn.app.R$id
 *  com.treelearn.app.R$layout
 *  com.treelearn.app.R$string
 */
package com.treelearn.app.screen;

import android.content.Context;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;
import com.treelearn.app.R;
import com.treelearn.app.ui.ChildBranch;
import java.util.List;

public final class ChildBranchPopup {
    private final PopupWindow popup;

    private ChildBranchPopup(PopupWindow popup) {
        this.popup = popup;
    }

    public static ChildBranchPopup show(View anchor, List<ChildBranch> children, Listener listener) {
        Context context = anchor.getContext();
        LinearLayout content = (LinearLayout)LayoutInflater.from((Context)context).inflate(R.layout.popup_child_branches, null, false);
        ((TextView)content.findViewById(R.id.child_branches_title)).setText((CharSequence)context.getString(R.string.chat_children_popup_title, new Object[]{children.size()}));
        LinearLayout list = (LinearLayout)content.findViewById(R.id.child_branches_list);
        PopupWindow popup = new PopupWindow((View)content, -2, -2, true);
        popup.setBackgroundDrawable((Drawable)new ColorDrawable(0));
        popup.setOutsideTouchable(true);
        content.measure(0, 0);
        int maxPx = context.getResources().getDimensionPixelSize(R.dimen.popup_max_height);
        popup.setHeight(Math.min(content.getMeasuredHeight(), maxPx));
        popup.setElevation(context.getResources().getDimension(R.dimen.card_elevation));
        ChildBranchPopup shown = new ChildBranchPopup(popup);
        LayoutInflater inflater = LayoutInflater.from((Context)context);
        for (ChildBranch child : children) {
            list.addView(ChildBranchPopup.row(inflater, list, context, child, shown, listener));
        }
        popup.showAsDropDown(anchor);
        return shown;
    }

    public void close() {
        if (this.popup.isShowing()) {
            this.popup.dismiss();
        }
    }

    private static View row(LayoutInflater inflater, LinearLayout list, Context context, ChildBranch child, ChildBranchPopup shown, Listener listener) {
        View row = inflater.inflate(R.layout.item_child_branch, (ViewGroup)list, false);
        ((TextView)row.findViewById(R.id.child_branch_title)).setText((CharSequence)child.title);
        TextView unread = (TextView)row.findViewById(R.id.child_branch_unread);
        unread.setText((CharSequence)context.getString(R.string.chat_children_unread, new Object[]{child.unreadCount}));
        unread.setVisibility(child.unreadCount > 0L ? 0 : 8);
        row.setOnClickListener(view -> {
            shown.close();
            listener.onBranchChosen(child.branchId);
        });
        return row;
    }

    public static interface Listener {
        public void onBranchChosen(String var1);
    }
}

