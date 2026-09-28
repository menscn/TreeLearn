package com.treelearn.app.ui;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;
import com.treelearn.app.R;
import com.treelearn.app.ui.HeatmapLayout;

public final class HeatmapView
extends View {
    private static final int DEFAULT_ROWS = 7;
    private static final int DEFAULT_COLUMNS = 14;
    private static final float DEFAULT_CELL_DP = 12.0f;
    private static final float DEFAULT_GAP_DP = 2.0f;
    private final Paint paint = new Paint(1);
    private final int[] levelColors = new int[5];
    private final float cellSize;
    private final float gap;
    private final int emptyColor;
    private final int rows;
    private final int columns;
    private int[] levels = new int[0];
    private boolean[] blank = new boolean[0];

    public HeatmapView(Context context) {
        this(context, null);
    }

    public HeatmapView(Context context, AttributeSet attrs) {
        super(context, attrs);
        float cellDp = this.dp(12.0f);
        float gapDp = this.dp(2.0f);
        int empty = this.getColor(R.color.tl_line);
        this.levelColors[1] = this.getColor(R.color.tl_green_faint);
        this.levelColors[2] = this.getColor(R.color.tl_green_soft);
        this.levelColors[3] = this.getColor(R.color.tl_green);
        this.levelColors[4] = this.getColor(R.color.tl_green_deep);
        int declaredMode = 1;
        int declaredRows = 7;
        int declaredColumns = 14;
        if (attrs != null) {
            TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.HeatmapView);
            declaredMode = a.getInt(R.styleable.HeatmapView_tl_mode, declaredMode);
            declaredRows = a.getInteger(R.styleable.HeatmapView_tl_rows, declaredRows);
            declaredColumns = a.getInteger(R.styleable.HeatmapView_tl_columns, declaredColumns);
            cellDp = a.getDimension(R.styleable.HeatmapView_tl_cellSize, cellDp);
            gapDp = a.getDimension(R.styleable.HeatmapView_tl_gap, gapDp);
            empty = a.getColor(R.styleable.HeatmapView_tl_emptyColor, empty);
            a.recycle();
        }
        this.cellSize = cellDp;
        this.gap = gapDp;
        this.emptyColor = empty;
        this.rows = HeatmapLayout.resolveRows(declaredMode, declaredRows);
        this.columns = HeatmapLayout.resolveColumns(declaredMode, declaredColumns);
        this.paint.setStyle(Paint.Style.FILL);
    }

    public void setIntensities(float[] values) {
        this.levels = HeatmapLayout.levelsFor(values, this.rows * this.columns);
        this.blank = new boolean[this.levels.length];
        if (values != null) {
            int count = Math.min(values.length, this.blank.length);
            for (int i = 0; i < count; ++i) {
                this.blank[i] = values[i] < 0.0f;
            }
        }
        this.invalidate();
    }

    protected void onMeasure(int widthSpec, int heightSpec) {
        int width = HeatmapView.resolveSize((int)((int)(HeatmapLayout.contentWidth(this.columns, this.cellSize, this.gap) + (float)this.getPaddingLeft() + (float)this.getPaddingRight())), (int)widthSpec);
        int height = HeatmapView.resolveSize((int)((int)(HeatmapLayout.contentHeight(this.rows, this.cellSize, this.gap) + (float)this.getPaddingTop() + (float)this.getPaddingBottom())), (int)heightSpec);
        this.setMeasuredDimension(width, height);
    }

    protected void onDraw(Canvas canvas) {
        float corner = this.cellSize / 4.0f;
        for (int i = 0; i < this.levels.length; ++i) {
            // Blanks (month padding, not-yet days) paint the empty placeholder too, so the grid
            // stays a full rectangle (2026-09-28 user call; the prototype leaves them transparent).
            int colorIndex = HeatmapLayout.colorIndexFor(this.levels[i], this.blank[i]);
            this.paint.setColor(colorIndex == 0 ? this.emptyColor : this.levelColors[colorIndex]);
            float x = (float)this.getPaddingLeft() + HeatmapLayout.cellX(i, this.rows, this.cellSize, this.gap);
            float y = (float)this.getPaddingTop() + HeatmapLayout.cellY(i, this.rows, this.cellSize, this.gap);
            canvas.drawRoundRect(x, y, x + this.cellSize, y + this.cellSize, corner, corner, this.paint);
        }
    }

    private int getColor(int id2) {
        return this.getContext().getColor(id2);
    }

    private float dp(float value) {
        return value * this.getResources().getDisplayMetrics().density;
    }
}
