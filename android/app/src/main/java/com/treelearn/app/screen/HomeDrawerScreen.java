package com.treelearn.app.screen;

import android.content.Context;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.treelearn.app.R;
import com.treelearn.app.library.LibraryViewState;
import com.treelearn.app.ui.HomeDrawerPresenter;
import com.treelearn.app.ui.HeatmapView;

/**
 * The home drawer (T-106): a self-drawn overlay — no DrawerLayout dependency — with a scrim, a
 * left panel carrying a titled calendar heatmap and a fixed quick-topic list.
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
    private final TextView emptyRecent;
    private final View heatmapCard;
    private final View heatmapWeekdays;
    private final TextView heatmapEmpty;
    private final HeatmapView heatmapCells;
    private final TextView heatmapMonth;
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
        panel.setPadding(gutter, getResources().getDimensionPixelSize(R.dimen.space_s4), gutter,
                getResources().getDimensionPixelSize(R.dimen.space_s4));
        // 78% of the screen, computed here so no width token is minted for a presentation value.
        int width = (int) (getResources().getDisplayMetrics().widthPixels * 0.78f);
        LayoutParams panelLayout = new LayoutParams(width, ViewGroup.LayoutParams.MATCH_PARENT);
        panelLayout.gravity = Gravity.START;
        panel.setLayoutParams(panelLayout);
        addView(panel);

        panel.addView(drawerHeader(context), headerLayoutParams());

        View heatmap = LayoutInflater.from(context).inflate(R.layout.drawer_heatmap, panel, false);
        heatmapCard = heatmap;
        heatmapWeekdays = heatmap.findViewById(R.id.drawer_heatmap_weekdays);
        heatmapEmpty = heatmap.findViewById(R.id.drawer_heatmap_empty);
        heatmapCells = heatmap.findViewById(R.id.drawer_heatmap_cells);
        heatmapMonth = heatmap.findViewById(R.id.drawer_heatmap_month);
        heatmapCard.setOnClickListener(view -> {
            if (actions != null) {
                actions.onStatsTapped();
            }
        });
        panel.addView(heatmapCard, heatmapLayoutParams());

        TextView title = new TextView(context);
        title.setText(R.string.drawer_quick_title);
        title.setTextColor(context.getColor(R.color.tl_ink));
        title.setTextSize(0, getResources().getDimensionPixelSize(R.dimen.text_h2));
        title.setTypeface(title.getTypeface(), android.graphics.Typeface.BOLD);
        panel.addView(title, titleLayoutParams());

        quickRows = new LinearLayout(context);
        quickRows.setOrientation(LinearLayout.VERTICAL);
        panel.addView(quickRows);

        emptyRecent = new TextView(context);
        emptyRecent.setText(R.string.drawer_empty);
        emptyRecent.setTextColor(context.getColor(R.color.tl_ink3));
        emptyRecent.setTextSize(0, getResources().getDimensionPixelSize(R.dimen.text_sm));
        emptyRecent.setGravity(Gravity.CENTER_VERTICAL);
        emptyRecent.setPadding(0, getResources().getDimensionPixelSize(R.dimen.space_s3), 0,
                getResources().getDimensionPixelSize(R.dimen.space_s3));
        panel.addView(emptyRecent);
    }

    /** The host owns the drawer's visibility; the render decides what the open panel draws. */
    public void render(HomeDrawerPresenter.State next) {
        setVisibility(next.phase == HomeDrawerPresenter.Phase.OPEN ? VISIBLE : GONE);
        boolean hasStats = next.stats != null;
        heatmapCard.setVisibility(hasStats ? VISIBLE : GONE);
        if (hasStats) {
            heatmapMonth.setText(next.stats.monthLabel);
            boolean showCalendar = !next.stats.empty;
            heatmapEmpty.setVisibility(showCalendar ? GONE : VISIBLE);
            heatmapWeekdays.setVisibility(showCalendar ? VISIBLE : GONE);
            heatmapCells.setVisibility(showCalendar ? VISIBLE : GONE);
            if (showCalendar) {
                heatmapCells.setIntensities(next.stats.monthIntensities);
            }
        }
        quickRows.setVisibility(next.rows.isEmpty() ? GONE : VISIBLE);
        emptyRecent.setVisibility(next.rows.isEmpty() ? VISIBLE : GONE);
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
        rowView.setMaxLines(2);
        rowView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        rowView.setBackgroundResource(R.drawable.bg_card);
        rowView.setElevation(getResources().getDimension(R.dimen.card_elevation));
        int rowPadding = getResources().getDimensionPixelSize(R.dimen.space_s3);
        rowView.setPadding(rowPadding, getResources().getDimensionPixelSize(R.dimen.space_s1),
                rowPadding, getResources().getDimensionPixelSize(R.dimen.space_s1));
        rowView.setMinHeight(getResources().getDimensionPixelSize(R.dimen.touch_min));
        rowView.setOnClickListener(view -> {
            if (actions != null) {
                actions.onTopicTapped(row.topicId);
            }
        });
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = getResources().getDimensionPixelSize(R.dimen.space_s1);
        rowView.setLayoutParams(params);
        return rowView;
    }

    private View drawerHeader(Context context) {
        LinearLayout header = new LinearLayout(context);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setOrientation(LinearLayout.HORIZONTAL);

        TextView title = new TextView(context);
        title.setText(R.string.drawer_title);
        title.setTextColor(context.getColor(R.color.tl_ink));
        title.setTextSize(0, getResources().getDimensionPixelSize(R.dimen.text_h1));
        title.setTypeface(title.getTypeface(), android.graphics.Typeface.BOLD);
        header.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        ImageButton button = new ImageButton(context);
        button.setImageResource(R.drawable.ic_chat_close);
        button.setImageTintList(android.content.res.ColorStateList.valueOf(
                context.getColor(R.color.tl_ink2)));
        button.setBackground(null);
        button.setContentDescription(context.getString(R.string.cd_drawer_close));
        button.setMinimumWidth(getResources().getDimensionPixelSize(R.dimen.touch_min));
        button.setMinimumHeight(getResources().getDimensionPixelSize(R.dimen.touch_min));
        button.setOnClickListener(view -> {
            if (actions != null) {
                actions.onDrawerToggled();
            }
        });
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                getResources().getDimensionPixelSize(R.dimen.touch_min),
                getResources().getDimensionPixelSize(R.dimen.touch_min));
        params.gravity = Gravity.END;
        button.setLayoutParams(params);
        header.addView(button);
        return header;
    }

    private LinearLayout.LayoutParams headerLayoutParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = getResources().getDimensionPixelSize(R.dimen.space_s4);
        return params;
    }

    private LinearLayout.LayoutParams heatmapLayoutParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = getResources().getDimensionPixelSize(R.dimen.space_s3);
        return params;
    }

    private LinearLayout.LayoutParams titleLayoutParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = getResources().getDimensionPixelSize(R.dimen.space_s1);
        return params;
    }

    public void setActions(HomeDrawerPresenter actions) {
        this.actions = actions;
    }
}
