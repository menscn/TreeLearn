/*
 * Decompiled with CFR 0.152.
 */
package com.treelearn.app.navigation;

import com.treelearn.app.navigation.NavigationActions;
import com.treelearn.app.navigation.NavigationData;
import com.treelearn.app.navigation.NavigationSink;
import com.treelearn.app.navigation.NavigationViewState;
import com.treelearn.app.ui.UiThread;
import java.util.ArrayList;
import java.util.concurrent.Executor;
import java.util.function.UnaryOperator;

public final class NavigationPresenter
implements NavigationActions {
    private final NavigationSink sink;
    private final UiThread ui;
    private final Executor background;
    private final NavigationData data;
    private NavigationViewState state;
    private NavigationData.Snapshot lastSnapshot;
    private long generation;

    public NavigationPresenter(NavigationSink sink, UiThread ui, Executor background, NavigationData data, String topicId, String selectedBranchId) {
        this.sink = NavigationPresenter.require(sink, "sink");
        this.ui = NavigationPresenter.require(ui, "ui");
        this.background = NavigationPresenter.require(background, "background");
        this.data = NavigationPresenter.require(data, "data");
        this.state = NavigationViewState.initial(NavigationPresenter.require(topicId, "topicId"), selectedBranchId);
    }

    public NavigationViewState state() {
        return this.state;
    }

    public String selectedBranchId() {
        return this.state.selectedBranchId;
    }

    public void start() {
        this.render(this.state.withPhase(NavigationViewState.Phase.LOADING));
        this.reload();
    }

    public void onResumed() {
        this.reload();
    }

    @Override
    public void onBranchSelected(String branchId) {
        if (this.rowOf(branchId) == null) {
            this.render(this.state.withNotice(NavigationViewState.Notice.BRANCH_GONE));
            return;
        }
        this.render(this.state.withSelection(branchId).withNotice(null));
        this.readSummary(branchId);
    }

    @Override
    public void onGroupToggled(String branchId) {
        if (branchId == null || branchId.isEmpty()) {
            throw new IllegalArgumentException("branchId must not be blank");
        }
        ArrayList<String> folded = new ArrayList<String>(this.state.collapsed);
        if (!folded.remove(branchId)) {
            folded.add(branchId);
        }
        this.render(this.state.withCollapsed(folded));
    }

    @Override
    public void onSummaryToggled() {
        this.render(this.state.withSummaryCollapsed(!this.state.summaryCollapsed));
    }

    @Override
    public void onArchive(String branchId) {
        this.setArchived(branchId, true);
    }

    @Override
    public void onRestore(String branchId) {
        this.setArchived(branchId, false);
    }

    @Override
    public void onDeleteRequested(String branchId, NavigationActions.DeleteScope scope) {
        NavigationData.Plan plan = NavigationData.plan(this.state.topicId, this.lastSnapshot, branchId, scope == NavigationActions.DeleteScope.TOPIC);
        if (plan == null) {
            this.render(this.state.withNotice(NavigationViewState.Notice.SCOPE_UNRESOLVABLE));
            return;
        }
        this.render(this.state.withPendingDelete(plan).withNotice(null));
    }

    @Override
    public void onDeleteCancelled() {
        this.render(this.state.withPendingDelete(null));
    }

    @Override
    public void onDeleteConfirmed() {
        NavigationData.Plan plan = this.state.pendingDelete;
        if (plan == null) {
            return;
        }
        String fallback = NavigationPresenter.nearestVisibleAncestor(this.lastSnapshot, plan);
        long token = ++this.generation;
        this.render(this.state.withPendingDelete(null).withNotice(null));
        this.background.execute(() -> {
            this.data.cancelInFlight(plan.branchIds);
            this.data.delete(plan);
            if (fallback == null) {
                this.post(token, current -> current.cleared());
                this.postLeave(token);
                return;
            }
            this.loadTree(token, plan.topicId, fallback);
        });
    }

    private void setArchived(String branchId, boolean archived) {
        NavigationViewState.Row row = this.rowOf(branchId);
        if (row == null) {
            this.render(this.state.withNotice(NavigationViewState.Notice.BRANCH_GONE));
            return;
        }
        if (row.archived == archived) {
            this.render(this.state.withNotice(archived ? NavigationViewState.Notice.ALREADY_ARCHIVED : NavigationViewState.Notice.NOT_ARCHIVED));
            return;
        }
        long token = ++this.generation;
        String topic = this.state.topicId;
        String wanted = this.state.selectedBranchId;
        this.background.execute(() -> {
            this.data.setArchived(branchId, archived, System.currentTimeMillis());
            this.loadTree(token, topic, wanted);
        });
    }

    private void reload() {
        long token = ++this.generation;
        String topic = this.state.topicId;
        String wanted = this.state.selectedBranchId;
        this.background.execute(() -> this.loadTree(token, topic, wanted));
    }

    private void readSummary(String branchId) {
        long token = ++this.generation;
        this.background.execute(() -> {
            NavigationData.Summary read = null;
            boolean failed = false;
            try {
                read = this.data.summary(branchId);
            }
            catch (RuntimeException unreadable) {
                failed = true;
            }
            NavigationData.Summary summary = read;
            boolean stale = failed;
            this.post(token, current -> stale ? current.withSummaryFailed() : current.withSummary(summary));
        });
    }

    private void loadTree(long token, String topicId, String wanted) {
        NavigationData.Snapshot tree;
        try {
            tree = this.data.load(topicId);
        }
        catch (RuntimeException unreadable) {
            this.post(token, current -> current.withPhase(NavigationViewState.Phase.ERROR).withNotice(NavigationViewState.Notice.STORE_UNREADABLE));
            return;
        }
        if (tree.nodes.isEmpty()) {
            this.post(token, current -> current.cleared());
            this.postLeave(token);
            return;
        }
        String selected = NavigationPresenter.resolve(tree, wanted);
        NavigationData.Summary read = null;
        boolean failed = false;
        try {
            read = this.data.summary(selected);
        }
        catch (RuntimeException unreadable) {
            failed = true;
        }
        NavigationData.Summary summary = read;
        boolean stale = failed;
        this.post(token, current -> {
            this.lastSnapshot = tree;
            NavigationViewState adopted = current.withSnapshot(tree, selected).withNotice(null);
            return stale ? adopted.withSummaryFailed() : adopted.withSummary(summary);
        });
    }

    private void post(long token, UnaryOperator<NavigationViewState> transition) {
        this.ui.post(() -> {
            if (token == this.generation) {
                this.render((NavigationViewState)transition.apply(this.state));
            }
        });
    }

    private void postLeave(long token) {
        this.ui.post(() -> {
            if (token == this.generation) {
                this.sink.leaveTree();
            }
        });
    }

    private void render(NavigationViewState next) {
        this.state = next;
        this.sink.render(next);
    }

    private NavigationViewState.Row rowOf(String branchId) {
        if (branchId == null) {
            return null;
        }
        for (NavigationViewState.Row row : this.state.visible) {
            if (!row.branchId.equals(branchId)) continue;
            return row;
        }
        for (NavigationViewState.Row row : this.state.archived) {
            if (!row.branchId.equals(branchId)) continue;
            return row;
        }
        return null;
    }

    private static String nearestVisibleAncestor(NavigationData.Snapshot tree, NavigationData.Plan plan) {
        if (tree == null) {
            return null;
        }
        NavigationData.Node node = tree.node(plan.targetBranchId);
        while (node != null && node.parentBranchId != null) {
            NavigationData.Node parent = tree.node(node.parentBranchId);
            if (parent == null) {
                return null;
            }
            if (!parent.archived && !plan.branchIds.contains(parent.branchId)) {
                return parent.branchId;
            }
            node = parent;
        }
        return null;
    }

    private static String resolve(NavigationData.Snapshot tree, String wanted) {
        if (wanted != null && tree.node(wanted) != null) {
            return wanted;
        }
        for (NavigationData.Node node : tree.nodes) {
            if (!node.isRoot()) continue;
            return node.branchId;
        }
        return tree.nodes.get((int)0).branchId;
    }

    private static <T> T require(T value, String name) {
        if (value == null) {
            throw new IllegalArgumentException(name + " must not be null");
        }
        return value;
    }
}

