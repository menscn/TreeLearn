package com.treelearn.app.ui;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

public final class HomeStats {
    public static final int WINDOW_DAYS = 14;
    public static final int MONTH_ROWS = 6;
    public static final int MONTH_COLUMNS = 7;
    public static final int MONTH_CELL_COUNT = MONTH_ROWS * MONTH_COLUMNS;
    public final boolean empty;
    public final float[] intensities;
    public final float[] monthIntensities;
    public final String monthLabel;
    public final int streakDays;

    private HomeStats(boolean empty, float[] intensities, float[] monthIntensities,
            String monthLabel, int streakDays) {
        this.empty = empty;
        this.intensities = intensities;
        this.monthIntensities = monthIntensities;
        this.monthLabel = monthLabel;
        this.streakDays = streakDays;
    }

    public static HomeStats empty() {
        return empty(LocalDate.now());
    }

    public static HomeStats empty(LocalDate today) {
        return new HomeStats(true, new float[0], blankMonthCells(),
                monthLabel(today), 0);
    }

    public static HomeStats of(List<LocalDate> activeDays, LocalDate today, int streakDays) {
        float[] cells = new float[14];
        for (int i = 0; i < 14; ++i) {
            cells[i] = activeDays.contains(today.minusDays(13 - i)) ? 1.0f : 0.0f;
        }
        float[] monthCells = blankMonthCells();
        LocalDate firstDay = today.withDayOfMonth(1);
        int firstDayOffset = firstDay.getDayOfWeek().getValue() - 1;
        int daysInMonth = firstDay.lengthOfMonth();
        int visibleDays = Math.min(daysInMonth, today.getDayOfMonth());
        for (int day = 0; day < visibleDays; ++day) {
            LocalDate date = firstDay.plusDays(day);
            int cell = firstDayOffset + day;
            monthCells[cell] = activeDays.contains(date) ? 1.0f : 0.0f;
        }
        return new HomeStats(false, cells, monthCells, monthLabel(today), streakDays);
    }

    private static String monthLabel(LocalDate today) {
        return today.getMonthValue() + "\u6708";
    }

    private static float[] blankMonthCells() {
        float[] cells = new float[MONTH_CELL_COUNT];
        Arrays.fill(cells, -1.0f);
        return cells;
    }
}
