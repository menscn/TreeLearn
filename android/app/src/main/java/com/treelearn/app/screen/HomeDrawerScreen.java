package com.treelearn.app.screen;

import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.treelearn.app.R;
import com.treelearn.app.library.LibraryViewState;
import com.treelearn.app.ui.HomeDrawerPresenter;

/**
 * The home drawer (T-106): a self-drawn overlay — no DrawerLayout dependency — with a scrim, a
 * left panel carrying the quick-topic list, the settings and stats shortcuts, and the extension
 * slot's skeleton.
 *
 * <p>The screen draws only; every decision (open/close, which branch a tap opens) belongs to the
 * presenter, exactly the way every other screen takes its presenter directly. One rule with one
 * text: this list is a shortcut surface, and the library stays the canonical list — so there is no
 * search, no assistant switch, and no second full list here.
 *
 * <p>The authoritative statement of that boundary lives in one place only: the V3.2 PRD's
 * §6.2.16 "V3.2 增量" section. This comment points at it and does not restate it.
 */
public final class HomeDrawerScreen extends FrameLayout {

    private final LinearLayout panel;
    private final View scrim;
    private final LinearLayout quickRows;
    private final TextView empty;
    private HomeDrawerPresenter actions;

    public HomeDrawerScreen(Context context) {
        super(context);
        setVisibility(GONE);
        setClickable(true);

        scrim = new View(context);
        scrim.setBackgroundColor(0x66000000);
        scrim.setLayoutParams(new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        scrim.setOnClickListener(view -> {
            if (actions != null) {
                actions.onDrawerToggled();
            }
        });
        addView(scrim);

        int gutter = getResources().getDimensionPixelSize(R.dimen.page_gutter);
        panel = new LinearLayout(context);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackgroundColor(context.getColor(R.color.tl_surface));
        panel.setPadding(gutter, getResources().getDimensionPixelSize(R.dimen.space_s6), gutter,
                getResources().getDimensionPixelSize(R.dimen.space_s6));
        // 78% of the screen, computed here so no width token is minted for a presentation value.
        int width = (int) (getResources().getDisplayMetrics().widthPixels * 0.78f);
        LayoutParams panelLayout = new LayoutParams(width, ViewGroup.LayoutParams.MATCH_PARENT);
        panelLayout.gravity = Gravity.START;
        panel.setLayoutParams(panelLayout);
        addView(panel);

        TextView title = new TextView(context);
        title.setText(R.string.drawer_quick_title);
        title.setTextColor(context.getColor(R.color.tl_ink));
        title.setTextSize(0, getResources().getDimensionPixelSize(R.dimen.text_h2));
        title.setTypeface(title.getTypeface(), Typeface.BOLD);
        panel.addView(title);

        empty = new TextView(context);
        empty.setText(R.string.drawer_empty);
        empty.setTextColor(context.getColor(R.color.tl_ink3));
        empty.setTextSize(0, getResources().getDimensionPixelSize(R.dimen.text_sm));
        empty.setPadding(0, getResources().getDimensionPixelSize(R.dimen.space_s4), 0, 0);
        panel.addView(empty);

        ScrollView listScroller = new ScrollView(context);
        quickRows = new LinearLayout(context);
        quickRows.setOrientation(LinearLayout.VERTICAL);
        listScroller.addView(quickRows);
        panel.addView(listScroller, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        panel.addView(entryRow(R.string.drawer_settings, () -> {
            if (actions != null) {
                actions.onSettingsTapped();
            }
        }));
        panel.addView(entryRow(R.string.drawer_stats, () -> {
            if (actions != null) {
                actions.onStatsTapped();
            }
        }));

        // The extension slot's skeleton (T-106): a titled, empty, GONE container the persona page
        // will fill later. Its presence documents the slot; its GONE keeps the promise unmade.
        TextView extension = new TextView(context);
        extension.setText(R.string.drawer_extension_title);
        extension.setTextColor(context.getColor(R.color.tl_ink3));
        extension.setTextSize(0, getResources().getDimensionPixelSize(R.dimen.text_cap));
        extension.setPadding(0, getResources().getDimensionPixelSize(R.dimen.space_s4), 0, 0);
        extension.setVisibility(GONE);
        panel.addView(extension);
    }

    /** The host owns the drawer's visibility; the render decides what the open panel draws. */
    public void render(HomeDrawerPresenter.State next) {
        setVisibility(next.phase == HomeDrawerPresenter.Phase.OPEN ? VISIBLE : GONE);
        empty.setVisibility(next.rows.isEmpty() ? VISIBLE : GONE);
        quickRows.removeAllViews();
        for (LibraryViewState.Row row : next.rows) {
            quickRows.addView(quickRow(row));
        }
    }

    private View quickRow(LibraryViewState.Row row) {
        TextView rowView = new TextView(getContext());
        rowView.setText(row.title);
        rowView.setTextColor(getContext().getColor(R.color.tl_ink));
        rowView.setTextSize(0, getResources().getDimensionPixelSize(R.dimen.text_sm));
        rowView.setGravity(Gravity.CENTER_VERTICAL);
        rowView.setMinHeight(getResources().getDimensionPixelSize(R.dimen.touch_min));
        rowView.setOnClickListener(view -> {
            if (actions != null) {
                actions.onTopicTapped(row.topicId);
            }
        });
        return rowView;
    }

    private View entryRow(int label, Runnable action) {
        TextView rowView = new TextView(getContext());
        rowView.setText(label);
        rowView.setTextColor(getContext().getColor(R.color.tl_ink2));
        rowView.setTextSize(0, getResources().getDimensionPixelSize(R.dimen.text_sm));
        rowView.setGravity(Gravity.CENTER_VERTICAL);
        rowView.setMinHeight(getResources().getDimensionPixelSize(R.dimen.touch_min));
        rowView.setOnClickListener(view -> action.run());
        return rowView;
    }

    public void setActions(HomeDrawerPresenter actions) {
        this.actions = actions;
    }
}
