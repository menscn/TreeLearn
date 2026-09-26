package com.treelearn.app.ui;

import com.treelearn.core.domain.Guard;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executor;

/**
 * Every decision the file manager page makes (T-096): what the list holds, what deletion is waiting
 * for its confirmation, and what the last delete answered. The screen draws and does not decide.
 *
 * <p><strong>Deletion is confirm-first and message-blind.</strong> A delete request parks an id in
 * {@code pendingDeleteId}; only the confirmation reaches the store. The store's own rule — records
 * and files go, reference rows survive as the missing annotation — lives below this presenter, so
 * a message's text can never be touched from here.
 */
public final class FileManagerPresenter {

    private final Sink sink;
    private final UiThread ui;
    private final Executor background;
    private final Store store;
    private State state;

    public FileManagerPresenter(Sink sink, UiThread ui, Executor background, Store store) {
        if (sink == null || ui == null || background == null || store == null) {
            throw new IllegalArgumentException("the file manager presenter needs all four parts");
        }
        this.sink = sink;
        this.ui = ui;
        this.background = background;
        this.store = store;
    }

    public State state() {
        return state;
    }

    public void start() {
        background.execute(this::reload);
    }

    /** Re-reads on every resume: files may have been added or system-cleaned since last time. */
    public void onResumed() {
        background.execute(this::reload);
    }

    public void onDeleteRequested(String attachmentId) {
        if (state == null || attachmentId == null) {
            return;
        }
        render(new State(state.rows, state.usageBytes, attachmentId, Notice.NONE,
                state.cleanableIds, state.pendingCleanupAll));
    }

    public void onDeleteCancelled() {
        if (state == null) {
            return;
        }
        render(new State(state.rows, state.usageBytes, null, Notice.NONE,
                state.cleanableIds, state.pendingCleanupAll));
    }

    public void onDeleteConfirmed(String attachmentId) {
        if (state == null || attachmentId == null) {
            return;
        }
        background.execute(() -> {
            Notice notice = Notice.NONE;
            try {
                store.delete(attachmentId);
            } catch (RuntimeException failed) {
                notice = Notice.DELETE_FAILED;
            }
            List<Row> rows = readRows();
            render(new State(rows, store.usageBytes(), null, notice, readOrphans(), false));
        });
    }

    /**
     * T-098: the cleanup button parks the whole orphan set for one confirmation. The set itself
     * is the store's orphan scan, not a presenter recomputation — the page never re-decides what
     * an orphan is.
     */
    public void onCleanupRequested() {
        if (state == null) {
            return;
        }
        render(new State(state.rows, state.usageBytes, state.pendingDeleteId, Notice.NONE,
                state.cleanableIds, true));
    }

    public void onCleanupCancelled() {
        if (state == null) {
            return;
        }
        render(new State(state.rows, state.usageBytes, state.pendingDeleteId, Notice.NONE,
                state.cleanableIds, false));
    }

    public void onCleanupConfirmed() {
        if (state == null || state.cleanableIds.isEmpty()) {
            return;
        }
        background.execute(() -> {
            Notice notice = Notice.NONE;
            try {
                store.cleanupOrphans(state.cleanableIds);
            } catch (RuntimeException failed) {
                notice = Notice.CLEANUP_FAILED;
            }
            List<Row> rows = readRows();
            render(new State(rows, store.usageBytes(), null, notice, readOrphans(), false));
        });
    }

    private void reload() {
        List<Row> rows = readRows();
        render(new State(rows, store.usageBytes(), null, Notice.NONE, readOrphans(), false));
    }

    /** The orphan scan runs on the caller's (background) thread; failures read as nothing to clean. */
    private Set<String> readOrphans() {
        try {
            return new HashSet<>(store.orphanIds());
        } catch (RuntimeException unreadable) {
            return java.util.Collections.emptySet();
        }
    }

    private List<Row> readRows() {
        try {
            return store.list();
        } catch (RuntimeException unreadable) {
            // An unreadable store is the empty phase with the failure notice, never a crash: the
            // page must stay reachable so the learner can see and retry.
            return java.util.Collections.emptyList();
        }
    }

    private void render(State next) {
        state = next;
        ui.post(() -> sink.render(next));
    }

    /** One drawable row: the record's facts plus what is physically and referentially true. */
    public static final class Row {

        public final String attachmentId;
        public final String displayName;
        public final String mimeType;
        public final long sizeBytes;
        public final String parseState;
        public final boolean present;
        public final int referenceCount;

        public Row(String attachmentId, String displayName, String mimeType, long sizeBytes,
                String parseState, boolean present, int referenceCount) {
            this.attachmentId = attachmentId;
            this.displayName = displayName;
            this.mimeType = mimeType;
            this.sizeBytes = sizeBytes;
            this.parseState = parseState;
            this.present = present;
            this.referenceCount = referenceCount;
        }
    }

    public enum Notice {
        NONE, DELETE_FAILED, CLEANUP_FAILED
    }

    public static final class State {

        public final List<Row> rows;
        public final long usageBytes;
        /** The delete waiting for its confirmation, or null while none is. */
        public final String pendingDeleteId;
        public final Notice notice;
        /** The orphan scan's answer (T-098); the page renders it, it never re-derives it. */
        public final Set<String> cleanableIds;
        /** The whole-orphan cleanup waiting for its confirmation (T-098). */
        public final boolean pendingCleanupAll;

        public State(List<Row> rows, long usageBytes, String pendingDeleteId, Notice notice,
                Set<String> cleanableIds, boolean pendingCleanupAll) {
            this.rows = java.util.Collections.unmodifiableList(
                    new java.util.ArrayList<>(Guard.object(rows, "rows")));
            this.usageBytes = usageBytes;
            this.pendingDeleteId = pendingDeleteId;
            this.notice = notice == null ? Notice.NONE : notice;
            this.cleanableIds = java.util.Collections.unmodifiableSet(
                    new java.util.HashSet<>(Guard.object(cleanableIds, "cleanableIds")));
            this.pendingCleanupAll = pendingCleanupAll;
        }
    }

    /** The render port; the screen draws the state it is given and decides nothing. */
    public interface Sink {

        void render(State next);
    }

    /** The read/delete port; the reference rows' survival is the implementation's own rule. */
    public interface Store {

        List<Row> list();

        long usageBytes();

        void delete(String attachmentId);

        /** The orphan scan's answer (T-098); its judgement lives in the DAO, not here. */
        Set<String> orphanIds();

        /** Idempotent record+body removal for orphan candidates (T-098). */
        void cleanupOrphans(Set<String> candidateIds);
    }
}
