package com.treelearn.app.ui;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;

/**
 * The home heatmap mappings: recent cells stay available for existing callers, while the drawer
 * uses a fixed six-week calendar grid for the current month.
 */
public final class HomeStatsTest {

    @Test
    public void mapsTheLastFourteenDaysOldestCellFirst() {
        LocalDate today = LocalDate.of(2026, 9, 23);

        HomeStats stats = HomeStats.of(Arrays.asList(today, today.minusDays(1)), today, 2);

        assertEquals(14, stats.intensities.length);
        assertEquals(0f, stats.intensities[0], 0f);
        assertEquals(0f, stats.intensities[11], 0f);
        assertEquals(1f, stats.intensities[12], 0f);
        assertEquals(1f, stats.intensities[13], 0f);
        assertEquals(HomeStats.MONTH_CELL_COUNT, stats.monthIntensities.length);
        assertEquals(1f, stats.monthIntensities[23], 0f);
        assertEquals(1f, stats.monthIntensities[22], 0f);
        assertEquals("future dates stay -1 in data; the views paint them as the placeholder",
                -1f, stats.monthIntensities[24], 0f);
        assertEquals("month-end padding stays -1 in data; the views paint it as the placeholder",
                -1f, stats.monthIntensities[31], 0f);
        assertEquals("9\u6708", stats.monthLabel);
        assertEquals(2, stats.streakDays);
        assertEquals(false, stats.empty);
    }

    @Test
    public void anActivityOutsideTheWindowLeavesEveryCellEmpty() {
        LocalDate today = LocalDate.of(2026, 9, 23);

        HomeStats stats = HomeStats.of(Collections.singletonList(today.minusDays(20)), today, 0);

        assertEquals(0f, stats.intensities[0], 0f);
        assertEquals(0f, stats.intensities[13], 0f);
    }

    @Test
    public void theEmptyFactoryIsTheHonestEmptyState() {
        HomeStats stats = HomeStats.empty(LocalDate.of(2026, 9, 23));

        assertEquals(true, stats.empty);
        assertEquals(HomeStats.MONTH_CELL_COUNT, stats.monthIntensities.length);
        assertEquals(0, stats.streakDays);
    }
}
