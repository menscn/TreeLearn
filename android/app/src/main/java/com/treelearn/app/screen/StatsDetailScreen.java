package com.treelearn.app.screen;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.treelearn.app.R;
import com.treelearn.app.ui.HeatmapView;
import com.treelearn.app.ui.StatsDetailPresenter;

/**
 * The statistics detail page (T-104): the full heatmap, the three totals and the empty/failed
 * placeholders. The screen only draws the state its presenter hands over — every verdict (empty,
 * failed, streak) was made once, in the presenter, from the same aggregation entry the home strip
 * reads. The back key and the drawer entry (T-106) are the host's wiring.
 */
public final class StatsDetailScreen extends LinearLayout implements
        StatsDetailPresenter.Sink {

    private final View failed;
    private final View empty;
    private final View heatBlock;
    private final View metricRow;
    private final HeatmapView heatCells;
    private final TextView questions;
    private final TextView messages;
    private final TextView streak;

    public StatsDetailScreen(Context context) {
        super(context);
        setOrientation(VERTICAL);
        LayoutInflater.from(context).inflate(R.layout.screen_stats_detail, this, true);

        failed = findViewById(R.id.stats_detail_failed);
        empty = findViewById(R.id.stats_detail_empty);
        heatBlock = findViewById(R.id.stats_heat_scroll);
        metricRow = findViewById(R.id.stats_metric_row);
        heatCells = (HeatmapView) findViewById(R.id.stats_heat_cells);
        questions = findViewById(R.id.stats_metric_questions);
        messages = findViewById(R.id.stats_metric_messages);
        streak = findViewById(R.id.stats_metric_streak);
    }

    /** The host owns the navigation stack; the screen only reports the back key. */
    public void setOnBackPressed(Runnable onBackPressed) {
        findViewById(R.id.stats_detail_back).setOnClickListener(
                view -> {
                    if (onBackPressed != null) {
                        onBackPressed.run();
                    }
                });
    }

    @Override
    public void render(StatsDetailPresenter.State next) {
        failed.setVisibility(next.failed ? VISIBLE : GONE);
        boolean showEmpty = next.empty && !next.failed;
        empty.setVisibility(showEmpty ? VISIBLE : GONE);
        boolean showData = !next.empty;
        heatBlock.setVisibility(showData ? VISIBLE : GONE);
        metricRow.setVisibility(next.failed ? GONE : VISIBLE);
        if (showData) {
            heatCells.setIntensities(next.intensities);
        }
        questions.setText(String.valueOf(next.totalQuestions));
        messages.setText(String.valueOf(next.totalMessages));
        streak.setText(String.valueOf(next.streakDays));
    }
}
