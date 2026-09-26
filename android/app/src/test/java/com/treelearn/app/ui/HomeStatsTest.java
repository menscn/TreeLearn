package com.treelearn.app.ui;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;

/**
 * The 14-day mapping behind the home mini heat strip (T-2026-09-23-102): which of the last
 * fourteen calendar days lights up, oldest cell first, straight from T-100's active-day set.
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
        HomeStats stats = HomeStats.empty();

        assertEquals(true, stats.empty);
        assertEquals(0, stats.streakDays);
    }
}
