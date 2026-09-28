package com.treelearn.app.ui;

import com.treelearn.app.wiring.HomeStore;
import com.treelearn.data.stats.StatsAggregator;

import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executor;

/**
 * The statistics detail page (T-104): the current-month calendar and the three totals, read through the SAME
 * aggregation entry point as the home strip — {@link HomeStore#statsRaw()} feeding
 * {@link StatsAggregator} — so the two surfaces cannot grow two different verdicts about "was this
 * day active". The page maps only the current month for display and passes the totals through
 * untouched; it never recounts anything.
 */
public final class StatsDetailPresenter {

    /** The detail calendar is a fixed seven-column by six-row month grid. */
    public static final int MONTH_CELL_COUNT = 42;

    /** The render port; the screen draws the state it is given and decides nothing. */
    public interface Sink {

        void render(State next);
    }

    /**
     * The read port. The reference implementation is the home store's own {@code statsRaw()} —
     * the same rows, the same queries, zero second sources (T-100's rule).
     */
    public interface StatsSource {

        HomeStore.StatsRaw read();
    }

    /** What the page may draw. {@code failed} rides alongside a healthy-shaped state. */
    public static final class State {

        public final boolean empty;
        public final boolean failed;
        /** One intensity per Monday-first calendar cell; -1.0 means transparent blank. */
        public final float[] monthIntensities;
        /** Zero-based Monday-first column for the first day of the month. */
        public final int monthFirstWeekday;
        public final int monthDays;
        public final String monthLabel;
        public final long totalQuestions;
        public final long totalMessages;
        public final int streakDays;

        private State(boolean empty, boolean failed, float[] monthIntensities,
                int monthFirstWeekday, int monthDays, String monthLabel, long totalQuestions,
                long totalMessages, int streakDays) {
            this.empty = empty;
            this.failed = failed;
            this.monthIntensities = monthIntensities;
            this.monthFirstWeekday = monthFirstWeekday;
            this.monthDays = monthDays;
            this.monthLabel = monthLabel;
            this.totalQuestions = totalQuestions;
            this.totalMessages = totalMessages;
            this.streakDays = streakDays;
        }
    }

    private final Sink sink;
    private final com.treelearn.app.ui.UiThread ui;
    private final Executor background;
    private final StatsSource source;
    private final Clock clock;
    private State state;

    public StatsDetailPresenter(Sink sink, com.treelearn.app.ui.UiThread ui, Executor background,
            StatsSource source, Clock clock) {
        this.sink = sink;
        this.ui = ui;
        this.background = background;
        this.source = source;
        this.clock = clock;
    }

    public State state() {
        return state;
    }

    /** First load. A failed read degrades to the visible placeholder, never a crash. */
    public void start() {
        refresh();
    }

    /** Re-reads (the drawer's re-entry or an explicit retry); a success clears {@code failed}. */
    public void refresh() {
        background.execute(() -> {
            State next;
            try {
                next = read();
            } catch (RuntimeException failed) {
                next = failedState();
            }
            final State frozen = next;
            ui.post(() -> {
                state = frozen;
                sink.render(frozen);
            });
        });
    }

    private State read() {
        HomeStore.StatsRaw raw = source.read();
        LocalDate today = LocalDate.now(clock);
        StatsAggregator.StatsResult result = StatsAggregator.aggregate(raw.answerTimestamps,
                raw.totalQuestions, raw.totalMessages, clock.getZone(), today);
        YearMonth month = YearMonth.from(today);
        return new State(result.isEmpty(), false, monthValues(month, today, result.activeDays()),
                firstWeekday(month), month.lengthOfMonth(), monthLabel(month),
                result.totalQuestions(), result.totalMessages(), result.currentStreak());
    }

    private State failedState() {
        LocalDate today = LocalDate.now(clock);
        YearMonth month = YearMonth.from(today);
        return new State(false, true, monthValues(month, today, java.util.Collections.<LocalDate>emptyList()),
                firstWeekday(month), month.lengthOfMonth(), monthLabel(month), 0L, 0L, 0);
    }

    private static float[] monthValues(YearMonth month, LocalDate today, List<LocalDate> activeDays) {
        float[] values = new float[MONTH_CELL_COUNT];
        int first = firstWeekday(month);
        int days = month.lengthOfMonth();
        for (int cell = 0; cell < values.length; ++cell) {
            int day = cell - first + 1;
            if (day < 1 || day > days || day > today.getDayOfMonth()) {
                values[cell] = -1.0f;
            } else {
                values[cell] = activeDays.contains(month.atDay(day)) ? 1.0f : 0.0f;
            }
        }
        return values;
    }

    private static int firstWeekday(YearMonth month) {
        return month.atDay(1).getDayOfWeek().getValue() - 1;
    }

    private static String monthLabel(YearMonth month) {
        return String.format(Locale.ROOT, "%d 年 %d 月", month.getYear(), month.getMonthValue());
    }
}
