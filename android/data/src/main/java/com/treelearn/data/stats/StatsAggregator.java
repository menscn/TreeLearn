package com.treelearn.data.stats;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * The pure half of the statistics entry point (T-2026-09-23-100): turning UTC epoch-millis
 * activity timestamps into per-day facts — which calendar days were active, and how long the
 * unbroken run into today is.
 *
 * <p>Rules the SQL layer deliberately does not decide:
 * <ul>
 *   <li>Stored timestamps are UTC epoch millis (rules/sql.md); "which day is this" is asked in an
 *       explicit business zone, converted from the instant — never by shifting stored values.</li>
 *   <li>Whether a day counts as active is one replaceable predicate, judged on that day's answer
 *       count. The default is the confirmed 口径 "有回答即活跃"; a design change to the 口径 is a
 *       one-line swap, not a new query.</li>
 *   <li>The streak walks back over consecutive active days ending today, or ending yesterday —
 *       a learner who has simply not opened the app yet today has not broken it. The first idle
 *       day breaks it, and the history the heatmap shows is never cut by that break.</li>
 *   <li>A history with no rows at all is an empty state ({@link StatsResult#isEmpty()}), so the UI
 *       can show it honestly instead of "0 天" reading as a broken feature.</li>
 * </ul>
 *
 * <p>Depends on nothing from the Android framework, so all of it is provable on the JVM; the DAO
 * feeding it and the views drawing from it stay thin.
 */
public final class StatsAggregator {

    /** The single replaceable activity policy, judged on one calendar day's answer count. */
    public interface ActivityPredicate {

        boolean isActive(long answerCount);
    }

    /** The confirmed default 口径: a day with at least one answer is an active day. */
    public static final ActivityPredicate HAS_ANY_ANSWER = new ActivityPredicate() {
        @Override
        public boolean isActive(long answerCount) {
            return answerCount > 0;
        }
    };

    private StatsAggregator() {
    }

    /** Aggregates with the default 口径 {@link #HAS_ANY_ANSWER}. */
    public static StatsResult aggregate(List<Long> answerTimestamps, long totalQuestions,
                                        long totalMessages, ZoneId zone, LocalDate today) {
        return aggregate(answerTimestamps, totalQuestions, totalMessages, zone, today,
                HAS_ANY_ANSWER);
    }

    /**
     * Aggregates the raw counts into per-day facts.
     *
     * @param answerTimestamps UTC epoch millis of every answer revision, unordered, unfiltered
     * @param totalQuestions   the caller's question total (USER message rows)
     * @param totalMessages    the caller's message total (every message row)
     * @param zone             the business zone the calendar day is cut in
     * @param today            the day the streak is measured against
     * @param predicate        the activity policy, applied per day
     */
    public static StatsResult aggregate(List<Long> answerTimestamps, long totalQuestions,
                                        long totalMessages, ZoneId zone, LocalDate today,
                                        ActivityPredicate predicate) {
        if (answerTimestamps == null || zone == null || today == null || predicate == null) {
            throw new IllegalArgumentException("aggregate inputs must not be null");
        }
        Map<LocalDate, Long> answersPerDay = new TreeMap<>();
        for (Long timestamp : answerTimestamps) {
            if (timestamp == null) {
                throw new IllegalArgumentException("a null timestamp is a caller bug, not a gap");
            }
            LocalDate day = Instant.ofEpochMilli(timestamp).atZone(zone).toLocalDate();
            Long previous = answersPerDay.get(day);
            answersPerDay.put(day, previous == null ? 1L : previous + 1);
        }

        List<LocalDate> activeDays = new ArrayList<>();
        for (Map.Entry<LocalDate, Long> day : answersPerDay.entrySet()) {
            if (predicate.isActive(day.getValue())) {
                activeDays.add(day.getKey());
            }
        }
        return new StatsResult(totalMessages == 0 && answerTimestamps.isEmpty(),
                Collections.unmodifiableList(activeDays), totalQuestions, totalMessages,
                currentStreak(activeDays, today));
    }

    /**
     * Consecutive active days ending today, or ending yesterday when today has no activity yet;
     * the first missing day in the walk breaks the count. The same walk is correct across month
     * and year boundaries because {@link LocalDate#minusDays} carries the arithmetic.
     */
    private static int currentStreak(List<LocalDate> activeDays, LocalDate today) {
        if (activeDays.isEmpty()) {
            return 0;
        }
        int lastIndex = activeDays.size() - 1;
        LocalDate last = activeDays.get(lastIndex);
        if (last.isAfter(today) || last.isBefore(today.minusDays(1))) {
            return 0;
        }
        int streak = 1;
        for (int i = lastIndex - 1; i >= 0; i--) {
            if (!activeDays.get(i).equals(activeDays.get(i + 1).minusDays(1))) {
                break;
            }
            streak++;
        }
        return streak;
    }

    /** The immutable aggregate result, including the empty-state flag. */
    public static final class StatsResult {

        private final boolean empty;
        private final List<LocalDate> activeDays;
        private final long totalQuestions;
        private final long totalMessages;
        private final int currentStreak;

        private StatsResult(boolean empty, List<LocalDate> activeDays, long totalQuestions,
                            long totalMessages, int currentStreak) {
            this.empty = empty;
            this.activeDays = activeDays;
            this.totalQuestions = totalQuestions;
            this.totalMessages = totalMessages;
            this.currentStreak = currentStreak;
        }

        /** True only when the store holds no rows at all; totals alone never set it. */
        public boolean isEmpty() {
            return empty;
        }

        /** Every active day, ascending, unmodifiable — the heatmap's day set. */
        public List<LocalDate> activeDays() {
            return activeDays;
        }

        public long totalQuestions() {
            return totalQuestions;
        }

        public long totalMessages() {
            return totalMessages;
        }

        /** Days in the unbroken run into today, 0 when the run is broken or history is empty. */
        public int currentStreak() {
            return currentStreak;
        }
    }
}
