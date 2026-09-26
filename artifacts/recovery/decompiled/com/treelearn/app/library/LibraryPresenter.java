/*
 * Decompiled with CFR 0.152.
 */
package com.treelearn.app.library;

import com.treelearn.app.library.LibraryData;
import com.treelearn.app.library.LibrarySink;
import com.treelearn.app.library.LibraryViewState;
import com.treelearn.app.navigation.NavigationData;
import com.treelearn.app.ui.UiThread;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.function.UnaryOperator;

public final class LibraryPresenter {
    private final LibrarySink sink;
    private final UiThread ui;
    private final Executor background;
    private final LibraryData data;
    private LibraryViewState state;
    private long generation;

    public LibraryPresenter(LibrarySink sink, UiThread ui, Executor background, LibraryData data) {
        this.sink = LibraryPresenter.require(sink, "sink");
        this.ui = LibraryPresenter.require(ui, "ui");
        this.background = LibraryPresenter.require(background, "background");
        this.data = LibraryPresenter.require(data, "data");
        this.state = LibraryViewState.initial();
    }

    public LibraryViewState state() {
        return this.state;
    }

    public void start() {
        this.render(this.state.withPhase(LibraryViewState.Phase.LOADING));
        this.reload();
    }

    public void onResumed() {
        this.reload();
    }

    public void onTopicTapped(LibraryViewState.Row row) {
        this.sink.openBranch(LibraryPresenter.require(row, (String)"row").resumeBranchId);
    }

    public void onSegmentSelected(LibraryViewState.Segment segment) {
        this.render(this.state.withSegment(LibraryPresenter.require(segment, "segment")));
    }

    public void onRestoreRequested(LibraryViewState.ArchiveRow row) {
        String branchId = LibraryPresenter.require(row, (String)"row").branchId;
        long token = ++this.generation;
        this.background.execute(() -> {
            this.data.restore(branchId, System.currentTimeMillis());
            this.load(token);
        });
    }

    public void onDeleteRequested(LibraryViewState.ArchiveRow row) {
        String topicId = LibraryPresenter.require(row, (String)"row").topicId;
        String branchId = row.branchId;
        long token = ++this.generation;
        this.background.execute(() -> {
            NavigationData.Plan plan;
            try {
                plan = NavigationData.plan(topicId, this.data.snapshot(topicId), branchId, false);
            }
            catch (RuntimeException unreadable) {
                this.post(token, current -> current.withPhase(LibraryViewState.Phase.ERROR));
                return;
            }
            if (plan == null) {
                return;
            }
            this.post(token, current -> current.withPendingDelete(plan));
        });
    }

    public void onDeleteCancelled() {
        this.render(this.state.withPendingDelete(null));
    }

    public void onDeleteConfirmed() {
        NavigationData.Plan plan = this.state.pendingDelete;
        if (plan == null) {
            return;
        }
        long token = ++this.generation;
        this.render(this.state.withPendingDelete(null));
        this.background.execute(() -> {
            this.data.cancelInFlight(plan.branchIds);
            this.data.delete(plan);
            this.load(token);
        });
    }

    private void reload() {
        long token = ++this.generation;
        this.background.execute(() -> this.load(token));
    }

    private void load(long token) {
        List<LibraryViewState.ArchiveRow> archived;
        List<LibraryViewState.Row> topics;
        try {
            topics = this.data.topics();
            archived = this.data.archived();
        }
        catch (RuntimeException unreadable) {
            this.post(token, current -> current.withPhase(LibraryViewState.Phase.ERROR));
            return;
        }
        this.post(token, current -> current.withTopics(topics).withArchived(archived));
    }

    private void post(long token, UnaryOperator<LibraryViewState> transition) {
        this.ui.post(() -> {
            if (token == this.generation) {
                this.render((LibraryViewState)transition.apply(this.state));
            }
        });
    }

    private void render(LibraryViewState next) {
        this.state = next;
        this.sink.render(next);
    }

    private static <T> T require(T value, String name) {
        if (value == null) {
            throw new IllegalArgumentException(name + " must not be null");
        }
        return value;
    }
}

