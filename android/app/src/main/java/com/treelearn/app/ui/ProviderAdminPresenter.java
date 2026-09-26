package com.treelearn.app.ui;

import com.treelearn.app.ui.KeySnapshot;
import com.treelearn.app.ui.UiThread;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executor;

public final class ProviderAdminPresenter {
    private final Sink sink;
    private final UiThread ui;
    private final Executor background;
    private final Store store;
    private State state;
    private String pendingDeleteId;

    public ProviderAdminPresenter(Sink sink, UiThread ui, Executor background, Store store) {
        this.sink = ProviderAdminPresenter.require(sink, "sink");
        this.ui = ProviderAdminPresenter.require(ui, "ui");
        this.background = ProviderAdminPresenter.require(background, "background");
        this.store = ProviderAdminPresenter.require(store, "store");
    }

    public State state() {
        return this.state;
    }

    public void start() {
        this.background.execute(this::reload);
    }

    public void onToggle(String id2, boolean enabled) {
        this.background.execute(() -> {
            this.store.setEnabled(id2, enabled);
            this.reload();
        });
    }

    public void onNewPressed() {
        this.render(this.editor(null, "", "", KeySnapshot.missing(), null, false, false, null));
    }

    public void onRowPressed(String id2) {
        if (this.state == null) {
            return;
        }
        Row row = this.find(id2);
        if (row == null) {
            return;
        }
        this.render(this.editor(row.id, row.name, row.baseUrl, this.store.readKey(row.id), null, false, false, null));
    }

    public void onEditorBackPressed() {
        this.background.execute(this::reload);
    }

    public void onSavePressed(String name, String baseUrl) {
        if (this.state == null || this.state.editor == null) {
            return;
        }
        String trimmedName = name == null ? "" : name.trim();
        String trimmedUrl = baseUrl == null ? "" : baseUrl.trim();
        Notice refusal;
        if (trimmedName.isEmpty()) {
            refusal = Notice.NAME_REQUIRED;
        } else if (trimmedUrl.isEmpty() || !trimmedUrl.startsWith("https://")) {
            refusal = Notice.BASE_REQUIRED;
        } else {
            refusal = null;
        }
        if (refusal != null) {
            this.render(this.editor(this.state.editor.providerId, trimmedName, trimmedUrl, this.state.editor.key, refusal, false, false, null));
            return;
        }
        Editor editing = this.state.editor;
        this.background.execute(() -> {
            if (this.store.nameTaken(trimmedName, editing.providerId)) {
                this.ui.post(() -> this.render(this.editor(editing.providerId, trimmedName, trimmedUrl, editing.key, Notice.NAME_TAKEN, false, false, null)));
                return;
            }
            if (editing.providerId == null) {
                this.store.create(trimmedName, trimmedUrl);
            } else {
                this.store.rename(editing.providerId, trimmedName, trimmedUrl);
            }
            this.reload();
        });
    }

    public void onKeySavePressed(char[] candidate) {
        if (this.state == null || this.state.editor == null) {
            return;
        }
        if (candidate == null || candidate.length == 0) {
            this.render(this.editor(this.state.editor.providerId, this.state.editor.name, this.state.editor.baseUrl, this.state.editor.key, Notice.KEY_REQUIRED, false, false, null));
            return;
        }
        Editor editing = this.state.editor;
        String id2 = editing.providerId;
        this.background.execute(() -> {
            KeySnapshot snapshot;
            Notice failure = null;
            try {
                this.store.saveKey(id2, candidate);
                snapshot = this.store.readKey(id2);
            }
            catch (KeyRefused refused) {
                snapshot = editing.key;
                failure = Notice.KEY_SAVE_FAILED;
            }
            finally {
                Arrays.fill(candidate, '\u0000');
            }
            KeySnapshot shown = snapshot;
            Notice reported = failure;
            this.ui.post(() -> this.render(this.editor(id2, editing.name, editing.baseUrl, shown, reported, false, false, null)));
        });
    }

    public void onKeyDeletePressed() {
        if (this.state == null || this.state.editor == null) {
            return;
        }
        Editor editing = this.state.editor;
        String id2 = editing.providerId;
        this.background.execute(() -> {
            this.store.deleteKey(id2);
            this.ui.post(() -> this.render(this.editor(id2, editing.name, editing.baseUrl, this.store.readKey(id2), null, false, false, null)));
        });
    }

    public void onFetchPressed() {
        if (this.state == null || this.state.editor == null || this.state.editor.fetchBusy || this.state.editor.providerId == null) {
            return;
        }
        Editor editing = this.state.editor;
        String id2 = editing.providerId;
        this.render(this.editor(id2, editing.name, editing.baseUrl, editing.key, editing.notice, true, false, null));
        this.background.execute(() -> {
            Fetch fetch = this.store.fetch(id2);
            this.ui.post(() -> this.render(this.editor(id2, editing.name, editing.baseUrl, editing.key, null, false, fetch.failed, fetch.failed ? null : Integer.valueOf(fetch.added))));
        });
    }

    public void onDeletePressed(String id2) {
        if (this.state == null) {
            return;
        }
        Row row = this.find(id2);
        if (row == null) {
            return;
        }
        long enabledCount = this.state.rows.stream().filter(item -> item.enabled).count();
        boolean lastEnabled = row.enabled && enabledCount == 1L;
        this.pendingDeleteId = id2;
        this.render(new State(State.Mode.LIST, this.state.rows, null, true, lastEnabled));
    }

    public void onDeleteCanceled() {
        if (this.state != null) {
            this.render(new State(State.Mode.LIST, this.state.rows, null, false, false));
        }
    }

    public void onDeleteConfirmed() {
        if (this.state == null || !this.state.deletePending) {
            return;
        }
        this.background.execute(() -> {
            this.store.delete(this.pendingDeleteId);
            this.reload();
        });
    }

    private void reload() {
        ArrayList<Row> rows = new ArrayList<Row>(this.store.list());
        this.ui.post(() -> this.render(new State(State.Mode.LIST, rows, null, false, false)));
    }

    private State editor(String providerId, String name, String baseUrl, KeySnapshot key, Notice notice, boolean fetchBusy, boolean fetchFailed, Integer fetchAdded) {
        Editor value = new Editor(providerId, name, baseUrl, key, notice, fetchBusy, fetchFailed, fetchAdded);
        return new State(State.Mode.EDITOR, this.state == null ? List.of() : this.state.rows, value, false, false);
    }

    private void render(State next) {
        this.state = next;
        this.sink.render(next);
    }

    private Row find(String id2) {
        for (Row row : this.state.rows) {
            if (!row.id.equals(id2)) continue;
            return row;
        }
        return null;
    }

    private static <T> T require(T value, String name) {
        if (value == null) {
            throw new IllegalArgumentException(name + " must not be null");
        }
        return value;
    }

    public static interface Sink {
        public void render(State var1);
    }

    public static interface Store {
        public List<Row> list();

        public boolean nameTaken(String var1, String var2);

        public Row create(String var1, String var2);

        public void rename(String var1, String var2, String var3);

        public void setEnabled(String var1, boolean var2);

        public void delete(String var1);

        public KeySnapshot readKey(String var1);

        public void saveKey(String var1, char[] var2) throws KeyRefused;

        public void deleteKey(String var1);

        public Fetch fetch(String var1);
    }

    public static final class State {
        public final Mode mode;
        public final List<Row> rows;
        public final Editor editor;
        public final boolean deletePending;
        public final boolean deleteWarning;

        State(Mode mode, List<Row> rows, Editor editor, boolean deletePending, boolean deleteWarning) {
            this.mode = mode;
            this.rows = rows;
            this.editor = editor;
            this.deletePending = deletePending;
            this.deleteWarning = deleteWarning;
        }

        public static enum Mode {
            LIST,
            EDITOR;

        }
    }

    public static enum Notice {
        NAME_REQUIRED,
        BASE_REQUIRED,
        NAME_TAKEN,
        KEY_REQUIRED,
        KEY_SAVE_FAILED;

    }

    public static final class Row {
        public final String id;
        public final String name;
        public final String baseUrl;
        public final boolean enabled;
        public final int modelCount;
        public final boolean hasKey;

        public Row(String id2, String name, String baseUrl, boolean enabled, int modelCount, boolean hasKey) {
            this.id = id2;
            this.name = name;
            this.baseUrl = baseUrl;
            this.enabled = enabled;
            this.modelCount = modelCount;
            this.hasKey = hasKey;
        }
    }

    public static final class Editor {
        public final String providerId;
        public final String name;
        public final String baseUrl;
        public final KeySnapshot key;
        public final Notice notice;
        public final boolean fetchBusy;
        public final boolean fetchFailed;
        public final Integer fetchAdded;

        public Editor(String providerId, String name, String baseUrl, KeySnapshot key, Notice notice, boolean fetchBusy, boolean fetchFailed, Integer fetchAdded) {
            this.providerId = providerId;
            this.name = name;
            this.baseUrl = baseUrl;
            this.key = key;
            this.notice = notice;
            this.fetchBusy = fetchBusy;
            this.fetchFailed = fetchFailed;
            this.fetchAdded = fetchAdded;
        }
    }

    public static final class Fetch {
        public final boolean failed;
        public final int added;

        public Fetch(boolean failed, int added) {
            this.failed = failed;
            this.added = added;
        }
    }

    public static final class KeyRefused
    extends Exception {
        public KeyRefused(String detail) {
            super(detail);
        }
    }

    public static interface Actions {
    }
}

