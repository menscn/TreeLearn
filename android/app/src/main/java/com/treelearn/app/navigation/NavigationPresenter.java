package com.treelearn.app.navigation;

import com.treelearn.app.ui.UiThread;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.function.UnaryOperator;

/**
 * The navigation screen's decisions, with no Android type in sight (technical design §12).
 *
 * <p>Five rules live here, and each is a requirement rather than a rendering choice:
 *
 * <ul>
 *   <li><strong>Archiving is not deleting.</strong> It flips one flag, never cancels a running
 *       attempt and never removes a row; an archived branch keeps its own history and can be
 *       restored. The branch's own descendants are untouched.
 *   <li><strong>Deleting is confirmed, scoped and ordered.</strong> The confirmation carries the
 *       exact set the store will remove - counted from a tree, and refused outright when the tree
 *       does not describe itself, because a number nobody can compute must never be shown as if it
 *       were one. On confirmation the scoped attempts are cancelled <em>first</em> and the removal
 *       is one call, so a stream that is still open is stopped before its rows disappear.
 *   <li><strong>A late answer is dropped.</strong> Every mutation bumps a generation, and a read
 *       that lands carrying an older one is discarded. Without that, a list read dispatched before a
 *       deletion would put the deleted branch back on screen.
 *   <li><strong>The selection falls back, or the view leaves.</strong> When the branch on screen is
 *       removed, the nearest ancestor that is neither removed nor archived is selected; if there is
 *       none, the navigation view closes rather than showing a stale branch id.
 *   <li><strong>Only the current branch's summary is fetched.</strong> The presenter never asks about
 *       a sibling or a descendant, and a failed read keeps the previous result instead of blanking
 *       the strip.
 * </ul>
 *
 * <p>The only thing a rotation has to carry is the topic id and the selected branch id - two UUIDs.
 * The presenter never holds a view, and the state it rebuilds from those two ids is the same state a
 * fresh construction produces.
 */
public final class NavigationPresenter implements NavigationActions {

    private final NavigationSink sink;
    private final UiThread ui;
    private final Executor background;
    private final NavigationData data;

    private NavigationViewState state;
    /** The last tree the store handed back: the only input a deletion scope is computed from. */
    private NavigationData.Snapshot lastSnapshot;
    /** Bumped by every mutation. A background answer carrying an older value is dropped. */
    private long generation;

    public NavigationPresenter(NavigationSink sink, UiThread ui, Executor background,
            NavigationData data, String topicId, String selectedBranchId) {
        this.sink = require(sink, "sink");
        this.ui = require(ui, "ui");
        this.background = require(background, "background");
        this.data = require(data, "data");
        this.state = NavigationViewState.initial(require(topicId, "topicId"), selectedBranchId);
    }

    /** The last state handed to the sink; the tests read it, the screen does not. */
    public NavigationViewState state() {
        return state;
    }

    /** The only thing a rotation has to keep: a branch id, never an object or a snapshot. */
    public String selectedBranchId() {
        return state.selectedBranchId;
    }

    public void start() {
        render(state.withPhase(NavigationViewState.Phase.LOADING));
        reload();
    }

    /** Coming back to the screen: the tree may have changed while it was away. */
    public void onResumed() {
        reload();
    }

    @Override
    public void onBranchSelected(String branchId) {
        if (rowOf(branchId) == null) {
            render(state.withNotice(NavigationViewState.Notice.BRANCH_GONE));
            return;
        }
        render(state.withSelection(branchId).withNotice(null));
        readSummary(branchId);
    }

    @Override
    public void onGroupToggled(String branchId) {
        if (branchId == null || branchId.isEmpty()) {
            throw new IllegalArgumentException("branchId must not be blank");
        }
        // Fold state lives in the view state only: toggling it writes nothing, so a fold cannot be
        // mistaken for an archive and cannot survive a reload as if it were stored.
        List<String> folded = new ArrayList<String>(state.collapsed);
        if (!folded.remove(branchId)) {
            folded.add(branchId);
        }
        render(state.withCollapsed(folded));
    }

    @Override
    public void onSummaryToggled() {
        render(state.withSummaryCollapsed(!state.summaryCollapsed));
    }

    @Override
    public void onArchive(String branchId) {
        setArchived(branchId, true);
    }

    @Override
    public void onRestore(String branchId) {
        setArchived(branchId, false);
    }

    @Override
    public void onDeleteRequested(String branchId, DeleteScope scope) {
        NavigationData.Plan plan = plan(state.topicId, lastSnapshot, branchId,
                scope == DeleteScope.TOPIC);
        if (plan == null) {
            render(state.withNotice(NavigationViewState.Notice.SCOPE_UNRESOLVABLE));
            return;
        }
        render(state.withPendingDelete(plan).withNotice(null));
    }

    @Override
    public void onDeleteCancelled() {
        render(state.withPendingDelete(null));
    }

    @Override
    public void onDeleteConfirmed() {
        final NavigationData.Plan plan = state.pendingDelete;
        if (plan == null) {
            return;
        }
        final String fallback = nearestVisibleAncestor(lastSnapshot, plan);
        final long token = ++generation;
        render(state.withPendingDelete(null).withNotice(null));
        background.execute(() -> {
            data.cancelInFlight(plan.branchIds);
            data.delete(plan);
            if (fallback == null) {
                post(token, current -> current.cleared());
                postLeave(token);
                return;
            }
            loadTree(token, plan.topicId, fallback);
        });
    }

    private void setArchived(String branchId, boolean archived) {
        final NavigationViewState.Row row = rowOf(branchId);
        if (row == null) {
            render(state.withNotice(NavigationViewState.Notice.BRANCH_GONE));
            return;
        }
        if (row.archived == archived) {
            render(state.withNotice(archived ? NavigationViewState.Notice.ALREADY_ARCHIVED
                    : NavigationViewState.Notice.NOT_ARCHIVED));
            return;
        }
        final long token = ++generation;
        final String topic = state.topicId;
        final String wanted = state.selectedBranchId;
        background.execute(() -> {
            data.setArchived(branchId, archived, System.currentTimeMillis());
            loadTree(token, topic, wanted);
        });
    }

    private void reload() {
        final long token = ++generation;
        final String topic = state.topicId;
        final String wanted = state.selectedBranchId;
        background.execute(() -> loadTree(token, topic, wanted));
    }

    private void readSummary(String branchId) {
        final long token = ++generation;
        background.execute(() -> {
            NavigationData.Summary read = null;
            boolean failed = false;
            try {
                read = data.summary(branchId);
            } catch (RuntimeException unreadable) {
                failed = true;
            }
            final NavigationData.Summary summary = read;
            final boolean stale = failed;
            post(token, current -> stale ? current.withSummaryFailed() : current.withSummary(summary));
        });
    }

    private void loadTree(long token, String topicId, String wanted) {
        final NavigationData.Snapshot tree;
        try {
            tree = data.load(topicId);
        } catch (RuntimeException unreadable) {
            post(token, current -> current.withPhase(NavigationViewState.Phase.ERROR)
                    .withNotice(NavigationViewState.Notice.STORE_UNREADABLE));
            return;
        }
        if (tree.nodes.isEmpty()) {
            post(token, current -> current.cleared());
            postLeave(token);
            return;
        }
        final String selected = resolve(tree, wanted);
        NavigationData.Summary read = null;
        boolean failed = false;
        try {
            read = data.summary(selected);
        } catch (RuntimeException unreadable) {
            failed = true;
        }
        final NavigationData.Summary summary = read;
        final boolean stale = failed;
        post(token, current -> {
            lastSnapshot = tree;
            NavigationViewState adopted = current.withSnapshot(tree, selected).withNotice(null);
            return stale ? adopted.withSummaryFailed() : adopted.withSummary(summary);
        });
    }

    private void post(long token, UnaryOperator<NavigationViewState> transition) {
        ui.post(() -> {
            if (token == generation) {
                render(transition.apply(state));
            }
        });
    }

    private void postLeave(long token) {
        ui.post(() -> {
            if (token == generation) {
                sink.leaveTree();
            }
        });
    }

    private void render(NavigationViewState next) {
        state = next;
        sink.render(next);
    }

    private NavigationViewState.Row rowOf(String branchId) {
        if (branchId == null) {
            return null;
        }
        for (NavigationViewState.Row row : state.visible) {
            if (row.branchId.equals(branchId)) {
                return row;
            }
        }
        for (NavigationViewState.Row row : state.archived) {
            if (row.branchId.equals(branchId)) {
                return row;
            }
        }
        return null;
    }

    /**
     * @return the exact set a confirmation would cover, or null when it cannot be counted - the
     *         target is not in the last tree, no tree has been read, or a node names a parent the
     *         tree does not hold. The last case is the reason this fails closed: a tree that does
     *         not describe itself cannot answer "how many branches will this remove", and a wrong
     *         number under a permanent-delete button is worse than no button.
     */
    private static NavigationData.Plan plan(String topicId, NavigationData.Snapshot tree,
            String target, boolean wholeTopic) {
        if (topicId == null || tree == null || target == null || tree.node(target) == null) {
            return null;
        }
        for (NavigationData.Node node : tree.nodes) {
            if (node.parentBranchId != null && tree.node(node.parentBranchId) == null) {
                return null;
            }
        }
        List<String> ids = new ArrayList<String>();
        if (wholeTopic) {
            for (NavigationData.Node node : tree.nodes) {
                ids.add(node.branchId);
            }
            return new NavigationData.Plan(topicId, target, ids, true);
        }
        ids.add(target);
        for (int i = 0; i < ids.size(); i++) {
            for (NavigationData.Node node : tree.nodes) {
                if (ids.get(i).equals(node.parentBranchId) && !ids.contains(node.branchId)) {
                    ids.add(node.branchId);
                }
            }
        }
        return new NavigationData.Plan(topicId, target, ids, false);
    }

    /**
     * @return the nearest ancestor that is neither removed by {@code plan} nor archived, or null
     *         when there is none - which is the "otherwise the home page" half of the rule.
     */
    private static String nearestVisibleAncestor(NavigationData.Snapshot tree,
            NavigationData.Plan plan) {
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

    /**
     * @return the branch to select: the wanted one when the tree still holds it, otherwise the
     *         topic's root - never the id of a branch that is no longer there
     */
    private static String resolve(NavigationData.Snapshot tree, String wanted) {
        if (wanted != null && tree.node(wanted) != null) {
            return wanted;
        }
        for (NavigationData.Node node : tree.nodes) {
            if (node.isRoot()) {
                return node.branchId;
            }
        }
        return tree.nodes.get(0).branchId;
    }

    private static <T> T require(T value, String name) {
        if (value == null) {
            throw new IllegalArgumentException(name + " must not be null");
        }
        return value;
    }
}
