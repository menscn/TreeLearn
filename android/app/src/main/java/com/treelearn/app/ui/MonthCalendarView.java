package com.treelearn.app.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import com.treelearn.app.R;

/** Seven-column, six-row month heatmap matching the prototype calendar card. */
public final class MonthCalendarView extends View {

    private static final int COLUMNS = 7;
    private static final int ROWS = 6;
    private static final int CELL_COUNT = COLUMNS * ROWS;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int[] levels = new int[CELL_COUNT];
    private final boolean[] blank = new boolean[CELL_COUNT];
    private final int[] levelColors = new int[5];
    private final float gap;
    private final int emptyColor;
    private float cellSize;

    public MonthCalendarView(Context context) {
        this(context, null);
    }

    public MonthCalendarView(Context context, AttributeSet attrs) {
        super(context, attrs);
        gap = getResources().getDimension(R.dimen.space_s1);
        emptyColor = getContext().getColor(R.color.tl_line);
        levelColors[1] = getContext().getColor(R.color.tl_green_faint);
        levelColors[2] = getContext().getColor(R.color.tl_green_soft);
        levelColors[3] = getContext().getColor(R.color.tl_green);
        levelColors[4] = getContext().getColor(R.color.tl_green_deep);
        paint.setStyle(Paint.Style.FILL);
    }

    public void setIntensities(float[] values) {
        for (int i = 0; i < CELL_COUNT; ++i) {
            float value = values != null && i < values.length ? values[i] : 0.0f;
            blank[i] = value < 0.0f;
            levels[i] = HeatmapLayout.levelFor(value);
        }
        invalidate();
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        int width = MeasureSpec.getSize(widthSpec);
        if (MeasureSpec.getMode(widthSpec) != MeasureSpec.EXACTLY) {
            width = Math.max(width, (int) dp(280.0f));
            width = resolveSize(width, widthSpec);
        }
        float available = Math.max(1.0f, width - getPaddingLeft() - getPaddingRight()
                - gap * (COLUMNS - 1));
        cellSize = available / COLUMNS;
        int desiredHeight = (int) Math.ceil(cellSize * ROWS + gap * (ROWS - 1)
                + getPaddingTop() + getPaddingBottom());
        int height = resolveSize(desiredHeight, heightSpec);
        setMeasuredDimension(width, height);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float corner = Math.min(dp(3.0f), cellSize / 4.0f);
        for (int i = 0; i < CELL_COUNT; ++i) {
            if (blank[i]) {
                continue;
            }
            int column = i % COLUMNS;
            int row = i / COLUMNS;
            paint.setColor(levels[i] == 0 ? emptyColor : levelColors[levels[i]]);
            float left = getPaddingLeft() + column * (cellSize + gap);
            float top = getPaddingTop() + row * (cellSize + gap);
            canvas.drawRoundRect(left, top, left + cellSize, top + cellSize, corner, corner, paint);
        }
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }
}
