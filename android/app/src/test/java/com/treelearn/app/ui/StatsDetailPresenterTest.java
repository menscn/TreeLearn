package com.treelearn.app.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.treelearn.app.wiring.HomeStore;
import org.junit.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * T-104's presenter contract: the detail page reads the SAME aggregation entry point as the home
 * strip (T-100's raw counts through {@code StatsAggregator}), maps the current month into a
 * seven-column calendar,
 * passes the totals through without recomputing them, degrades a failed read to a visible
 * placeholder, and recovers on a later successful read.
 */
public final class StatsDetailPresenterTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final LocalDate TODAY = LocalDate.of(2026, 9, 25);
    private static final Clock CLOCK = Clock.fixed(TODAY.atStartOfDay(ZONE).toInstant(), ZONE);

    @Test
    public void theCurrentMonthCalendarMapsActiveDaysAndKeepsTheMetricOrder() {
        Instant todayHit = TODAY.atStartOfDay(ZONE).plusHours(9).toInstant();
        Instant yesterdayHit = TODAY.minusDays(1).atStartOfDay(ZONE).plusHours(9).toInstant();
        StatsDetailPresenter presenter = presenter(Arrays.asList(todayHit.toEpochMilli(),
                yesterdayHit.toEpochMilli()), 3L, 11L, 2);

        presenter.start();
        StatsDetailPresenter.State state = presenter.state();

        assertEquals("the calendar is a fixed seven-column six-row grid", 42,
                state.monthIntensities.length);
        assertEquals("2026-09-01 is the second column when Monday is first", 1,
                state.monthFirstWeekday);
        assertEquals("September has thirty days", 30, state.monthDays);
        assertEquals("today lands on the 25th cell after the leading Monday slot", 1.0f,
                state.monthIntensities[25], 0f);
        assertEquals("yesterday is active in the adjacent cell", 1.0f,
                state.monthIntensities[24], 0f);
        assertEquals("a future date remains transparent, not an inactive day", -1.0f,
                state.monthIntensities[26], 0f);
        assertEquals("month-end padding remains transparent", -1.0f,
                state.monthIntensities[31], 0f);
        assertEquals("totals pass through untouched", 3L, state.totalQuestions);
        assertEquals(11L, state.totalMessages);
        assertEquals("the streak is the aggregator's verdict, never a page-side recount", 2,
                state.streakDays);
        assertTrue("real data is not the empty state", !state.empty);
    }

    @Test
    public void anEmptyStoreRendersTheEmptyStateInsteadOfAFakeZeroDay() {
        StatsDetailPresenter presenter = presenter(Arrays.asList(), 0L, 0L, 0);

        presenter.start();
        StatsDetailPresenter.State state = presenter.state();

        assertTrue("no rows at all means the empty state", state.empty);
        assertEquals(0L, state.totalQuestions);
        assertEquals(42, state.monthIntensities.length);
    }

    @Test
    public void aLeapFebruaryKeepsAdjacentMonthDaysOutOfTheVisibleCalendar() {
        LocalDate leapDay = LocalDate.of(2028, 2, 29);
        Clock leapClock = Clock.fixed(leapDay.atStartOfDay(ZONE).toInstant(), ZONE);
        StatsDetailPresenter presenter = presenter(Arrays.asList(
                leapDay.atStartOfDay(ZONE).plusHours(9).toInstant().toEpochMilli(),
                leapDay.minusDays(28).atStartOfDay(ZONE).plusHours(9).toInstant().toEpochMilli(),
                leapDay.plusDays(1).atStartOfDay(ZONE).plusHours(9).toInstant().toEpochMilli()),
                2L, 4L, leapClock);

        presenter.start();
        StatsDetailPresenter.State state = presenter.state();

        assertEquals("2028-02-01 is Wednesday in a Monday-first calendar", 1,
                state.monthFirstWeekday);
        assertEquals(29, state.monthDays);
        assertEquals(1.0f, state.monthIntensities[29], 0f);
        assertEquals("March 1 is not shown as a February activity", -1.0f,
                state.monthIntensities[30], 0f);
    }

    @Test
    public void aFailedReadDegradesToAVisiblePlaceholderWithoutCrashing() {
        StatsDetailPresenter presenter = new StatsDetailPresenter(new RecordingSink(),
                new TestDoubles.InlineUi(), new TestDoubles.InlineExecutor(),
                new StatsDetailPresenter.StatsSource() {
                    @Override
                    public HomeStore.StatsRaw read() {
                        throw new IllegalStateException("db locked");
                    }
                }, CLOCK);

        presenter.start();
        StatsDetailPresenter.State state = presenter.state();

        assertTrue("the failure is a visible state, not a crash", state.failed);
        assertTrue("degraded keeps the empty grid", !state.empty);
    }

    @Test
    public void aRetryAfterAFailureRecoversTheRealData() {
        List<Instant> hits = new ArrayList<Instant>();
        hits.add(TODAY.atStartOfDay(ZONE).plusHours(9).toInstant());
        FlakySource source = new FlakySource(hits);
        StatsDetailPresenter presenter = new StatsDetailPresenter(new RecordingSink(),
                new TestDoubles.InlineUi(), new TestDoubles.InlineExecutor(), source, CLOCK);

        presenter.start();
        assertTrue("first read fails", presenter.state().failed);

        sourceHealthy(source);
        presenter.refresh();
        assertTrue("the retry clears the failure", !presenter.state().failed);
        assertEquals("and today's calendar cell fills", 1.0f,
                presenter.state().monthIntensities[25], 0f);
    }

    private void sourceHealthy(FlakySource source) {
        source.healthy = true;
    }

    private StatsDetailPresenter presenter(final List<Long> timestamps, final long questions,
            final long messages, final int streak) {
        return presenter(timestamps, questions, messages, CLOCK);
    }

    private StatsDetailPresenter presenter(final List<Long> timestamps, final long questions,
            final long messages, final Clock clock) {
        return new StatsDetailPresenter(new RecordingSink(), new TestDoubles.InlineUi(),
                new TestDoubles.InlineExecutor(), new StatsDetailPresenter.StatsSource() {
                    @Override
                    public HomeStore.StatsRaw read() {
                        return new HomeStore.StatsRaw(new ArrayList<Long>(timestamps), questions,
                                messages);
                    }
                }, clock);
    }

    /** One failing read, then healthy: the recovery path needs a source that can do both. */
    private static final class FlakySource implements StatsDetailPresenter.StatsSource {

        private final List<Instant> hits;
        private boolean healthy;

        private FlakySource(List<Instant> hits) {
            this.hits = hits;
        }

        @Override
        public HomeStore.StatsRaw read() {
            if (!healthy) {
                throw new IllegalStateException("first read fails");
            }
            List<Long> raw = new ArrayList<Long>();
            for (Instant hit : hits) {
                raw.add(hit.toEpochMilli());
            }
            return new HomeStore.StatsRaw(raw, 1L, 2L);
        }
    }

    private static final class RecordingSink implements StatsDetailPresenter.Sink {

        StatsDetailPresenter.State last;

        @Override
        public void render(StatsDetailPresenter.State next) {
            last = next;
        }
    }
}
