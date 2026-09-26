package com.treelearn.data.stats;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * T-2026-09-23-100 acceptance, JVM half: the day aggregation and the streak are pure decisions,
 * so they are proven here, without a device.
 *
 * <p>Timestamps stored in the store are UTC epoch millis (rules/sql.md); "which day is this" is a
 * business-timezone question asked on top of them. Both halves are pinned: the same instant must
 * land on different calendar days in different zones, and the streak must break on exactly the day
 * with no activity. Real SQL execution of the DAO feeding this aggregator stays with the device
 * rounds, the same split the search statement suite uses.
 */
public final class StatsAggregatorTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final LocalDate TODAY = LocalDate.of(2026, 9, 23);

    /** UTC epoch millis for a wall-clock time in the test zone. */
    private static long at(LocalDate day, int hour, int minute) {
        return ZonedDateTime.of(day.getYear(), day.getMonthValue(), day.getDayOfMonth(),
                hour, minute, 0, 0, ZONE).toInstant().toEpochMilli();
    }

    private static StatsAggregator.StatsResult aggregate(List<Long> answerTimes,
                                                         long questions, long messages) {
        return StatsAggregator.aggregate(answerTimes, questions, messages, ZONE, TODAY);
    }

    @Test
    public void anEmptyHistoryYieldsTheEmptyStateRatherThanMisleadingZeros() {
        StatsAggregator.StatsResult result = aggregate(Collections.<Long>emptyList(), 0, 0);

        assertTrue("no rows at all is an empty state the UI must recognise, not a quiet zero",
                result.isEmpty());
        assertTrue(result.activeDays().isEmpty());
        assertEquals(0, result.currentStreak());
        assertEquals(0, result.totalQuestions());
        assertEquals(0, result.totalMessages());
    }

    @Test
    public void aQuestionOnlyHistoryIsNotTheEmptyState() {
        StatsAggregator.StatsResult result = aggregate(Collections.<Long>emptyList(), 5, 9);

        assertFalse("questions exist, so the learner has data even without answers",
                result.isEmpty());
        assertTrue("the default activity predicate is \"has an answer\": a day without one"
                        + " is not an active day",
                result.activeDays().isEmpty());
        assertEquals(5, result.totalQuestions());
        assertEquals(9, result.totalMessages());
    }

    @Test
    public void aDayWithSeveralAnswersAggregatesIntoOneActiveDay() {
        StatsAggregator.StatsResult result = aggregate(Arrays.asList(
                at(LocalDate.of(2026, 9, 22), 9, 0),
                at(LocalDate.of(2026, 9, 22), 21, 30),
                at(LocalDate.of(2026, 9, 23), 8, 0)), 2, 5);

        assertEquals(Arrays.asList(
                        LocalDate.of(2026, 9, 22),
                        LocalDate.of(2026, 9, 23)),
                result.activeDays());
    }

    @Test
    public void activeDaysAreSortedAscendingEvenWhenTheInputIsNot() {
        StatsAggregator.StatsResult result = aggregate(Arrays.asList(
                at(LocalDate.of(2026, 9, 23), 8, 0),
                at(LocalDate.of(2026, 9, 21), 8, 0)), 1, 3);

        assertEquals(Arrays.asList(
                        LocalDate.of(2026, 9, 21),
                        LocalDate.of(2026, 9, 23)),
                result.activeDays());
    }

    @Test
    public void activityAggregatesAcrossAMonthBoundary() {
        // The measured "today" is Sep 1, so the run of Aug 31 -> Sep 1 is the live streak.
        StatsAggregator.StatsResult result = StatsAggregator.aggregate(Arrays.asList(
                at(LocalDate.of(2026, 8, 31), 22, 0),
                at(LocalDate.of(2026, 9, 1), 7, 0)), 1, 4,
                ZONE, LocalDate.of(2026, 9, 1));

        assertTrue(result.activeDays().contains(LocalDate.of(2026, 8, 31)));
        assertTrue(result.activeDays().contains(LocalDate.of(2026, 9, 1)));
        assertEquals("the streak is a count of days, not of months",
                2, result.currentStreak());
    }

    @Test
    public void anIdleDayBetweenActiveDaysBreaksTheStreak() {
        StatsAggregator.StatsResult result = aggregate(Arrays.asList(
                at(LocalDate.of(2026, 9, 20), 9, 0),
                at(LocalDate.of(2026, 9, 21), 9, 0),
                at(LocalDate.of(2026, 9, 23), 9, 0)), 2, 6);

        assertEquals("the heatmap keeps every active day including the idle one's neighbours",
                3, result.activeDays().size());
        assertEquals("the gap on Sep 22 breaks the run: only today's run counts",
                1, result.currentStreak());
    }

    @Test
    public void todayNotYetActiveKeepsTheStreakAliveThroughYesterday() {
        StatsAggregator.StatsResult result = aggregate(Arrays.asList(
                at(LocalDate.of(2026, 9, 21), 9, 0),
                at(LocalDate.of(2026, 9, 22), 9, 0)), 1, 5);

        assertEquals("the learner has simply not opened the app yet today",
                2, result.currentStreak());
    }

    @Test
    public void theStreakIsBrokenWhenTheLastActiveDayIsBeforeYesterday() {
        StatsAggregator.StatsResult result = aggregate(Arrays.asList(
                at(LocalDate.of(2026, 9, 21), 9, 0)), 1, 2);

        assertEquals(0, result.currentStreak());
        assertEquals("an expired streak is still a history the heatmap must show",
                Collections.singletonList(LocalDate.of(2026, 9, 21)), result.activeDays());
    }

    @Test
    public void theDayBoundaryFollowsTheBusinessTimeZoneRatherThanUtc() {
        // 2026-09-23T00:30+08:00 == 2026-09-22T16:30Z: one instant, two calendar days.
        long lateEveningUtc = at(LocalDate.of(2026, 9, 23), 0, 30);

        StatsAggregator.StatsResult inShanghai =
                StatsAggregator.aggregate(Collections.singletonList(lateEveningUtc),
                        1, 2, ZONE, TODAY);
        StatsAggregator.StatsResult inUtc =
                StatsAggregator.aggregate(Collections.singletonList(lateEveningUtc),
                        1, 2, ZoneId.of("UTC"), LocalDate.of(2026, 9, 22));

        assertEquals(Collections.singletonList(LocalDate.of(2026, 9, 23)),
                inShanghai.activeDays());
        assertEquals(Collections.singletonList(LocalDate.of(2026, 9, 22)),
                inUtc.activeDays());
        assertEquals("both zones must agree on how long the run is", 1,
                inShanghai.currentStreak());
    }

    @Test
    public void theActivityPredicateIsTheSingleReplaceablePolicy() {
        StatsAggregator.ActivityPredicate twoOrMoreAnswers = new StatsAggregator.ActivityPredicate() {
            @Override
            public boolean isActive(long answerCount) {
                return answerCount >= 2;
            }
        };

        StatsAggregator.StatsResult result = StatsAggregator.aggregate(
                Arrays.asList(
                        at(LocalDate.of(2026, 9, 22), 9, 0),
                        at(LocalDate.of(2026, 9, 22), 21, 0),
                        at(LocalDate.of(2026, 9, 23), 9, 0)),
                2, 5, ZONE, TODAY, twoOrMoreAnswers);

        assertEquals("Sep 22 had two answers and Sep 23 only one",
                Collections.singletonList(LocalDate.of(2026, 9, 22)), result.activeDays());
        assertEquals(1, result.currentStreak());
    }
}
