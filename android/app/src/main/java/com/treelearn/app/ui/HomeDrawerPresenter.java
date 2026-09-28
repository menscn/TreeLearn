package com.treelearn.app.ui;

import com.treelearn.app.library.LibraryViewState;
import com.treelearn.core.domain.Guard;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

/**
 * The home drawer's presenter (T-106): an open/close machine plus a quick-list window over the
 * library's own query.
 *
 * <p>The quick list never re-asks the database: {@link QuickTopicsSource} is the library store's
 * existing {@code topics()} read through a port, so the drawer and the library cannot grow two
 * answers to "which topics exist" (this repo's failure library bans exactly that twin). The window
 * — the most recent {@value #QUICK_LIMIT} rows — is this presenter's only computation, and every
 * navigation hands off to the same targets the home screen itself uses, then closes the drawer so
 * it never survives the move.
 */
public final class HomeDrawerPresenter {

    /** The fixed drawer history window; the library remains the canonical full list. */
    public static final int QUICK_LIMIT = 3;

    /** The drawer is either shut or shown; the slide animation is a View detail, not a state. */
    public enum Phase {
        CLOSED, OPEN
    }

    /** The read port: the library store's existing query, no second SQL text anywhere. */
    public interface QuickTopicsSource {

        List<LibraryViewState.Row> read();
    }

    public interface StatsSource {

        HomeStats read();
    }

    /** The render and navigation ports; the navigation targets are the host's own room methods. */
    public interface Sink {

        void render(State next);

        void openBranch(String branchId);

        void openStats();
    }

    /** The drawer's whole world: phase, quick rows, and the compact heatmap snapshot. */
    public static final class State {

        public final Phase phase;
        /** The quick rows, already windowed to {@link #QUICK_LIMIT}; empty means draw the empty. */
        public final List<LibraryViewState.Row> rows;
        public final HomeStats stats;

        public State(Phase phase, List<LibraryViewState.Row> rows) {
            this(phase, rows, null);
        }

        public State(Phase phase, List<LibraryViewState.Row> rows, HomeStats stats) {
            this.phase = phase == null ? Phase.CLOSED : phase;
            this.rows = rows == null
                    ? java.util.Collections.<LibraryViewState.Row>emptyList()
                    : java.util.Collections.unmodifiableList(rows);
            this.stats = stats;
        }
    }

    private final Sink sink;
    private final QuickTopicsSource source;
    private final StatsSource statsSource;
    private final UiThread ui;
    private final Executor background;
    private State state;

    public HomeDrawerPresenter(Sink sink, QuickTopicsSource source) {
        this(sink, source, null, null, null);
    }

    public HomeDrawerPresenter(Sink sink, QuickTopicsSource source, UiThread ui,
            Executor background) {
        this(sink, source, null, ui, background);
    }

    public HomeDrawerPresenter(Sink sink, QuickTopicsSource source, StatsSource statsSource,
            UiThread ui, Executor background) {
        this.sink = Guard.object(sink, "sink");
        this.source = Guard.object(source, "source");
        this.statsSource = statsSource;
        this.ui = ui;
        this.background = background;
    }

    /**
     * Loads the quick list once; the drawer opens and closes over the same rows. With the thread
     * ports present the read runs off the main thread — it is a real query — and the first render
     * lands on the main thread; without them (JVM tests) both steps run inline.
     */
    public void start() {
        if (ui != null && background != null) {
            background.execute(() -> {
                List<LibraryViewState.Row> rows = readRows();
                HomeStats stats = readStats();
                ui.post(() -> render(new State(Phase.CLOSED, rows, stats)));
            });
            return;
        }
        render(new State(Phase.CLOSED, readRows(), readStats()));
    }

    public void onDrawerToggled() {
        render(state.phase == Phase.OPEN ? new State(Phase.CLOSED, state.rows, state.stats)
                : new State(Phase.OPEN, state.rows, state.stats));
    }

    public void onTopicTapped(String topicId) {
        Guard.id(topicId, "topicId");
        for (LibraryViewState.Row row : state.rows) {
            if (row.topicId.equals(topicId)) {
                render(new State(Phase.CLOSED, state.rows, state.stats));
                sink.openBranch(row.resumeBranchId);
                return;
            }
        }
        throw new IllegalArgumentException(
                "the quick list only opens its own rows; unknown topicId " + topicId);
    }

    public void onStatsTapped() {
        render(new State(Phase.CLOSED, state.rows, state.stats));
        sink.openStats();
    }

    private List<LibraryViewState.Row> readRows() {
        try {
            List<LibraryViewState.Row> all = source.read();
            return all.size() <= QUICK_LIMIT ? all : new ArrayList<>(all.subList(0, QUICK_LIMIT));
        } catch (RuntimeException broken) {
            // A broken read is an empty quick list, never a crash: the drawer's other duties
            // (settings, stats) do not depend on the list.
            return new ArrayList<>();
        }
    }

    private HomeStats readStats() {
        if (statsSource == null) {
            return null;
        }
        try {
            return statsSource.read();
        } catch (RuntimeException broken) {
            return null;
        }
    }

    private void render(State next) {
        state = Guard.object(next, "next");
        if (ui != null && background != null) {
            ui.post(() -> sink.render(state));
            return;
        }
        sink.render(state);
    }
}
