package com.treelearn.app.ui;

import com.treelearn.app.wiring.HomeStore;
import com.treelearn.data.stats.StatsAggregator;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.Executor;

/**
 * The statistics detail page (T-104): the full heatmap and the three totals, read through the SAME
 * aggregation entry point as the home strip — {@link HomeStore#statsRaw()} feeding
 * {@link StatsAggregator} — so the two surfaces cannot grow two different verdicts about "was this
 * day active". The page maps the ten-week window for the grid and passes the totals through
 * untouched; it never recounts anything.
 */
public final class StatsDetailPresenter {

    /** The detail grid covers ten weeks, one column per day, today on the last column. */
    public static final int WINDOW_DAYS = 70;

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
        /** One intensity per day, oldest first, today last; 1.0 active, 0.0 not. */
        public final float[] intensities;
        public final long totalQuestions;
        public final long totalMessages;
        public final int streakDays;

        private State(boolean empty, boolean failed, float[] intensities, long totalQuestions,
                long totalMessages, int streakDays) {
            this.empty = empty;
            this.failed = failed;
            this.intensities = intensities;
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
                next = new State(false, true, new float[WINDOW_DAYS], 0L, 0L, 0);
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
        if (result.isEmpty()) {
            return new State(true, false, new float[WINDOW_DAYS], 0L, 0L, 0);
        }
        List<LocalDate> activeDays = result.activeDays();
        float[] intensities = new float[WINDOW_DAYS];
        for (int i = 0; i < WINDOW_DAYS; ++i) {
            intensities[i] = activeDays.contains(today.minusDays(WINDOW_DAYS - 1 - i)) ? 1.0f : 0.0f;
        }
        return new State(false, false, intensities, result.totalQuestions(),
                result.totalMessages(), result.currentStreak());
    }
}
