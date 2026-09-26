package com.treelearn.app.ui;

import com.treelearn.app.ui.KeySnapshot;
import com.treelearn.app.ui.UiThread;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executor;

public final class SearchSettingsPresenter {
    private final Sink sink;
    private final UiThread ui;
    private final Executor background;
    private final Store store;
    private State state;

    public SearchSettingsPresenter(Sink sink, UiThread ui, Executor background, Store store) {
        if (sink == null || ui == null || background == null || store == null) {
            throw new IllegalArgumentException("the search settings presenter needs all four parts");
        }
        this.sink = sink;
        this.ui = ui;
        this.background = background;
        this.store = store;
    }

    public State state() {
        return this.state;
    }

    public void start() {
        this.background.execute(this::reload);
    }

    public void onSearchToggled(boolean enabled) {
        this.background.execute(() -> {
            this.store.setSearchEnabled(enabled);
            this.reload();
        });
    }

    public void onEngineSelected(Engine engine) {
        if (engine == null) {
            return;
        }
        this.background.execute(() -> {
            this.store.setEngine(engine);
            this.reload();
        });
    }

    public void onTierSelected(Tier tier) {
        if (tier == null) {
            return;
        }
        this.background.execute(() -> {
            this.store.setTier(tier);
            this.reload();
        });
    }

    public void onKeySavePressed(Engine engine, char[] candidate) {
        if (this.state == null || engine == null) {
            return;
        }
        if (candidate == null || candidate.length == 0 || SearchSettingsPresenter.isBlank(candidate)) {
            if (candidate != null) {
                Arrays.fill(candidate, '\u0000');
            }
            this.render(new State(this.state.searchEnabled, this.state.engine, this.state.tier, this.state.rows, this.state.key, Notice.KEY_REQUIRED));
            return;
        }
        this.background.execute(() -> {
            Notice failure = null;
            try {
                this.store.saveKey(engine, candidate);
            }
            catch (KeyRefused refused) {
                failure = Notice.KEY_SAVE_FAILED;
            }
            finally {
                Arrays.fill(candidate, '\u0000');
            }
            Notice reported = failure;
            this.ui.post(() -> {
                if (reported != null) {
                    this.render(new State(this.state.searchEnabled, this.state.engine, this.state.tier, this.state.rows, this.state.key, reported));
                } else {
                    this.reload();
                }
            });
        });
    }

    public void onKeyDeletePressed(Engine engine) {
        if (this.state == null || engine == null) {
            return;
        }
        this.background.execute(() -> {
            this.store.deleteKey(engine);
            this.reload();
        });
    }

    private void reload() {
        boolean enabled = this.store.searchEnabled();
        Engine engine = this.store.engine();
        Tier tier = this.store.tier();
        ArrayList<Row> rows = new ArrayList<Row>();
        for (Engine candidate : Engine.values()) {
            rows.add(new Row(candidate, this.store.hasKey(candidate)));
        }
        KeySnapshot key = this.store.readKey(engine);
        Notice notice = enabled && !this.store.hasKey(engine) ? Notice.NOKEY : Notice.NONE;
        this.ui.post(() -> this.render(new State(enabled, engine, tier, rows, key, notice)));
    }

    private void render(State next) {
        this.state = next;
        this.sink.render(next);
    }

    private static boolean isBlank(char[] candidate) {
        for (char c : candidate) {
            if (Character.isWhitespace(c)) continue;
            return false;
        }
        return true;
    }

    public static interface Sink {
        public void render(State var1);
    }

    public static interface Store {
        public boolean searchEnabled();

        public void setSearchEnabled(boolean var1);

        public Engine engine();

        public void setEngine(Engine var1);

        public Tier tier();

        public void setTier(Tier var1);

        public boolean hasKey(Engine var1);

        public KeySnapshot readKey(Engine var1);

        public void saveKey(Engine var1, char[] var2) throws KeyRefused;

        public void deleteKey(Engine var1);
    }

    public static final class State {
        public final boolean searchEnabled;
        public final Engine engine;
        public final Tier tier;
        public final List<Row> rows;
        public final KeySnapshot key;
        public final Notice notice;

        State(boolean searchEnabled, Engine engine, Tier tier, List<Row> rows, KeySnapshot key, Notice notice) {
            this.searchEnabled = searchEnabled;
            this.engine = engine;
            this.tier = tier;
            this.rows = rows;
            this.key = key;
            this.notice = notice;
        }
    }

    public static enum Engine {
        ZHIPU,
        TAVILY,
        BOCHA;

    }

    public static enum Tier {
        THREE,
        FIVE,
        TEN;

    }

    public static enum Notice {
        NONE,
        KEY_REQUIRED,
        KEY_SAVE_FAILED,
        NOKEY;

    }

    public static final class Row {
        public final Engine engine;
        public final boolean hasKey;

        public Row(Engine engine, boolean hasKey) {
            this.engine = engine;
            this.hasKey = hasKey;
        }
    }

    public static final class KeyRefused
    extends Exception {
        public KeyRefused(String detail) {
            super(detail);
        }
    }
}

