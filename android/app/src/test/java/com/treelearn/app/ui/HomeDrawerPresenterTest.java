package com.treelearn.app.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.treelearn.app.library.LibraryViewState;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

/**
 * T-106: the home drawer's state machine and its quick-list contract, judged without a View.
 *
 * <p>The quick list reuses the library's own query through the {@code QuickTopicsSource} port; the
 * presenter's only computation is the "most recent 10" window and the open/close machine. A tapped
 * topic must route through the very same {@code openBranch} target the home screen uses — a second
 * navigation path is exactly the "one rule, two texts" defect this repo's failure library bans.
 */
public final class HomeDrawerPresenterTest {

    /** Domain ids are UUIDs; the tag only makes each one deterministic and readable. */
    private static String id(String tag) {
        return java.util.UUID.nameUUIDFromBytes(tag.getBytes()).toString();
    }

    private static LibraryViewState.Row row(String tag) {
        return new LibraryViewState.Row(id(tag), id("branch-" + tag), "TOPIC-" + tag,
                "SYNTHETIC-SUMMARY", 3, 1L, 1_756_000_000_000L);
    }

    private static final class RecordingSink implements HomeDrawerPresenter.Sink {
        HomeDrawerPresenter.State last;
        final List<String> openedBranches = new ArrayList<>();
        int settingsOpens;
        int statsOpens;

        @Override
        public void render(HomeDrawerPresenter.State next) {
            last = next;
        }

        @Override
        public void openBranch(String branchId) {
            openedBranches.add(branchId);
        }

        @Override
        public void openSettings() {
            settingsOpens++;
        }

        @Override
        public void openStats() {
            statsOpens++;
        }
    }

    private static final class FixedSource implements HomeDrawerPresenter.QuickTopicsSource {
        private final List<LibraryViewState.Row> rows;
        private final RuntimeException failure;

        private FixedSource(List<LibraryViewState.Row> rows) {
            this(rows, null);
        }

        private FixedSource(List<LibraryViewState.Row> rows, RuntimeException failure) {
            this.rows = rows;
            this.failure = failure;
        }

        @Override
        public List<LibraryViewState.Row> read() {
            if (failure != null) {
                throw failure;
            }
            return rows;
        }
    }

    @Test
    public void startRendersClosedWithTheMostRecentTenRows() {
        List<LibraryViewState.Row> eleven = new ArrayList<>();
        for (int i = 0; i < 11; i++) {
            eleven.add(row("t" + i));
        }
        RecordingSink sink = new RecordingSink();
        HomeDrawerPresenter presenter = new HomeDrawerPresenter(sink, new FixedSource(eleven));

        presenter.start();

        assertEquals(HomeDrawerPresenter.Phase.CLOSED, sink.last.phase);
        assertEquals("the quick list is a window, never a second full list", 10,
                sink.last.rows.size());
        assertEquals(id("t0"), sink.last.rows.get(0).topicId);
    }

    @Test
    public void togglingMovesBetweenClosedAndOpen() {
        RecordingSink sink = new RecordingSink();
        HomeDrawerPresenter presenter = new HomeDrawerPresenter(sink,
                new FixedSource(java.util.Collections.singletonList(row("t0"))));
        presenter.start();

        presenter.onDrawerToggled();
        assertEquals(HomeDrawerPresenter.Phase.OPEN, sink.last.phase);
        presenter.onDrawerToggled();
        assertEquals(HomeDrawerPresenter.Phase.CLOSED, sink.last.phase);
    }

    @Test
    public void tappingATopicOpensItsResumeBranchAndClosesTheDrawer() {
        RecordingSink sink = new RecordingSink();
        HomeDrawerPresenter presenter = new HomeDrawerPresenter(sink,
                new FixedSource(java.util.Collections.singletonList(row("t0"))));
        presenter.start();
        presenter.onDrawerToggled();

        presenter.onTopicTapped(id("t0"));

        assertEquals("the opened branch is the row's own resume branch",
                id("branch-t0"), sink.openedBranches.get(0));
        assertEquals("the drawer never survives navigation", HomeDrawerPresenter.Phase.CLOSED,
                sink.last.phase);
    }

    @Test
    public void settingAndStatsEntriesCloseTheDrawerFirst() {
        RecordingSink sink = new RecordingSink();
        HomeDrawerPresenter presenter = new HomeDrawerPresenter(sink,
                new FixedSource(java.util.Collections.singletonList(row("t0"))));
        presenter.start();
        presenter.onDrawerToggled();

        presenter.onSettingsTapped();
        assertSame(HomeDrawerPresenter.Phase.CLOSED, sink.last.phase);
        assertEquals(1, sink.settingsOpens);

        presenter.onDrawerToggled();
        presenter.onStatsTapped();
        assertEquals(1, sink.statsOpens);
        assertEquals(HomeDrawerPresenter.Phase.CLOSED, sink.last.phase);
    }

    @Test
    public void anEmptyOrBrokenSourceDegradesToAnEmptyListWithoutCrashing() {
        RecordingSink sink = new RecordingSink();
        HomeDrawerPresenter empty = new HomeDrawerPresenter(sink,
                new FixedSource(new ArrayList<>()));
        empty.start();
        assertTrue(sink.last.rows.isEmpty());

        RecordingSink broken = new RecordingSink();
        HomeDrawerPresenter presenter = new HomeDrawerPresenter(broken,
                new FixedSource(null, new IllegalStateException("SYNTHETIC-BROKEN-SOURCE")));
        presenter.start();
        presenter.onDrawerToggled();
        assertTrue("a broken read is an empty quick list, not a crash", broken.last.rows.isEmpty());
        assertEquals(HomeDrawerPresenter.Phase.OPEN, broken.last.phase);
    }
}
