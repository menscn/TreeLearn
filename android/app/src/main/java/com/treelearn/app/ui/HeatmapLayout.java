package com.treelearn.app.ui;

public final class HeatmapLayout {
    public static final int MODE_STRIP = 0;
    public static final int MODE_GRID = 1;
    public static final int LEVEL_EMPTY = 0;
    public static final int LEVEL_MAX = 4;

    private HeatmapLayout() {
    }

    public static int levelFor(float intensity) {
        if (Float.isNaN(intensity) || intensity <= 0.0f) {
            return 0;
        }
        int level = (int)Math.ceil(intensity * 4.0f);
        if (level < 1) {
            level = 1;
        }
        if (level > 4) {
            level = 4;
        }
        return level;
    }

    public static int[] levelsFor(float[] values, int cellCount) {
        int[] levels = new int[Math.max(0, cellCount)];
        if (values == null) {
            return levels;
        }
        int count = Math.min(values.length, levels.length);
        for (int i = 0; i < count; ++i) {
            levels[i] = HeatmapLayout.levelFor(values[i]);
        }
        return levels;
    }

    /**
     * The color row a cell paints with: {@code 0} is the empty placeholder, {@code 1..4} the
     * green ramp. A blank cell — month padding or a day that has not arrived — paints the empty
     * placeholder too, by user call on 2026-09-28: the calendar reads as one full rectangle,
     * where the prototype leaves those slots transparent. This is the single judgment point;
     * both painting views route through it.
     */
    public static int colorIndexFor(int level, boolean blank) {
        if (blank || level <= 0) {
            return 0;
        }
        return Math.min(level, LEVEL_MAX);
    }

    public static int resolveRows(int mode, int rows) {
        if (mode == 0) {
            return 1;
        }
        return Math.max(1, rows);
    }

    public static int resolveColumns(int mode, int columns) {
        return Math.max(1, columns);
    }

    public static float cellX(int index, int rows, float cellSize, float gap) {
        int column = index / Math.max(1, rows);
        return (float)column * (cellSize + gap);
    }

    public static float cellY(int index, int rows, float cellSize, float gap) {
        int row = index % Math.max(1, rows);
        return (float)row * (cellSize + gap);
    }

    public static float contentWidth(int columns, float cellSize, float gap) {
        return columns <= 0 ? 0.0f : (float)columns * cellSize + (float)(columns - 1) * gap;
    }

    public static float contentHeight(int rows, float cellSize, float gap) {
        return rows <= 0 ? 0.0f : (float)rows * cellSize + (float)(rows - 1) * gap;
    }
}

