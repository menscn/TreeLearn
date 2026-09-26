/*
 * Decompiled with CFR 0.152.
 */
package com.treelearn.app.navigation;

import com.treelearn.app.navigation.NavigationData;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class NavigationViewState {
    public final Phase phase;
    public final String topicId;
    public final String selectedBranchId;
    public final List<Row> visible;
    public final List<Row> archived;
    public final String summaryFor;
    public final String summary;
    public final List<String> keyPoints;
    public final boolean summaryStale;
    public final List<String> collapsed;
    public final boolean summaryCollapsed;
    public final NavigationData.Plan pendingDelete;
    public final Notice notice;
    private final NavigationData.Snapshot snapshot;

    private NavigationViewState(Phase phase, NavigationData.Snapshot snapshot, String topicId, String selectedBranchId, String summaryFor, String summary, List<String> keyPoints, boolean summaryStale, NavigationData.Plan pendingDelete, Notice notice, List<String> collapsed, boolean summaryCollapsed) {
        this.phase = phase;
        this.snapshot = snapshot;
        this.topicId = topicId;
        this.selectedBranchId = selectedBranchId;
        this.collapsed = Collections.unmodifiableList(new ArrayList<String>(collapsed));
        this.summaryCollapsed = summaryCollapsed;
        this.visible = NavigationViewState.rows(snapshot, selectedBranchId, false, this.collapsed);
        this.archived = NavigationViewState.rows(snapshot, selectedBranchId, true, this.collapsed);
        this.summaryFor = summaryFor;
        this.summary = summary;
        this.keyPoints = Collections.unmodifiableList(new ArrayList<String>(keyPoints));
        this.summaryStale = summaryStale;
        this.pendingDelete = pendingDelete;
        this.notice = notice;
    }

    public static NavigationViewState initial(String topicId, String selectedBranchId) {
        return new NavigationViewState(Phase.LOADING, null, topicId, selectedBranchId, null, "", Collections.emptyList(), false, null, null, Collections.emptyList(), false);
    }

    public NavigationViewState withPhase(Phase value) {
        return new NavigationViewState(value, this.snapshot, this.topicId, this.selectedBranchId, this.summaryFor, this.summary, this.keyPoints, this.summaryStale, this.pendingDelete, this.notice, this.collapsed, this.summaryCollapsed);
    }

    public NavigationViewState withSnapshot(NavigationData.Snapshot tree, String selected) {
        return new NavigationViewState(Phase.READY, tree, tree.topicId, selected, this.summaryFor, this.summary, this.keyPoints, this.summaryStale, this.pendingDelete, this.notice, this.collapsed, this.summaryCollapsed);
    }

    public NavigationViewState withSelection(String branchId) {
        return new NavigationViewState(this.phase, this.snapshot, this.topicId, branchId, this.summaryFor, this.summary, this.keyPoints, this.summaryStale, this.pendingDelete, this.notice, this.collapsed, this.summaryCollapsed);
    }

    public NavigationViewState withSummary(NavigationData.Summary read) {
        if (read == null || this.selectedBranchId == null || !read.branchId.equals(this.selectedBranchId)) {
            return this.withSummaryFor(null, "", Collections.emptyList(), this.summaryStale);
        }
        return this.withSummaryFor(read.branchId, read.text, read.keyPoints, false);
    }

    public NavigationViewState withSummaryFailed() {
        boolean stillOurs = this.summaryFor != null && this.summaryFor.equals(this.selectedBranchId);
        return this.withSummaryFor(stillOurs ? this.summaryFor : null, stillOurs ? this.summary : "", stillOurs ? this.keyPoints : Collections.emptyList(), true);
    }

    public NavigationViewState withPendingDelete(NavigationData.Plan plan) {
        return new NavigationViewState(this.phase, this.snapshot, this.topicId, this.selectedBranchId, this.summaryFor, this.summary, this.keyPoints, this.summaryStale, plan, this.notice, this.collapsed, this.summaryCollapsed);
    }

    public NavigationViewState withNotice(Notice value) {
        return new NavigationViewState(this.phase, this.snapshot, this.topicId, this.selectedBranchId, this.summaryFor, this.summary, this.keyPoints, this.summaryStale, this.pendingDelete, value, this.collapsed, this.summaryCollapsed);
    }

    public NavigationViewState cleared() {
        return new NavigationViewState(Phase.MISSING, null, this.topicId, null, null, "", Collections.emptyList(), false, null, null, Collections.emptyList(), false);
    }

    public NavigationViewState withCollapsed(List<String> folded) {
        if (folded == null) {
            throw new IllegalArgumentException("folded must not be null");
        }
        return new NavigationViewState(this.phase, this.snapshot, this.topicId, this.selectedBranchId, this.summaryFor, this.summary, this.keyPoints, this.summaryStale, this.pendingDelete, this.notice, folded, this.summaryCollapsed);
    }

    public NavigationViewState withSummaryCollapsed(boolean folded) {
        return new NavigationViewState(this.phase, this.snapshot, this.topicId, this.selectedBranchId, this.summaryFor, this.summary, this.keyPoints, this.summaryStale, this.pendingDelete, this.notice, this.collapsed, folded);
    }

    private NavigationViewState withSummaryFor(String forBranch, String text, List<String> points, boolean stale) {
        return new NavigationViewState(this.phase, this.snapshot, this.topicId, this.selectedBranchId, forBranch, text, points, stale, this.pendingDelete, this.notice, this.collapsed, this.summaryCollapsed);
    }

    private static List<Row> rows(NavigationData.Snapshot tree, String selected, boolean archived, List<String> collapsed) {
        if (tree == null) {
            return Collections.emptyList();
        }
        ArrayList<Row> rows = new ArrayList<Row>();
        for (NavigationData.Node node : tree.nodes) {
            if (node.archived != archived || NavigationViewState.isFoldedAway(tree, node, collapsed)) continue;
            rows.add(new Row(node.branchId, node.title, node.archived, node.branchId.equals(selected), !node.isRoot(), NavigationViewState.liveChildren(tree, node.branchId)));
        }
        return Collections.unmodifiableList(rows);
    }

    private static int liveChildren(NavigationData.Snapshot tree, String branchId) {
        int count = 0;
        for (NavigationData.Node node : tree.nodes) {
            if (!branchId.equals(node.parentBranchId) || node.archived) continue;
            ++count;
        }
        return count;
    }

    private static boolean isFoldedAway(NavigationData.Snapshot tree, NavigationData.Node node, List<String> collapsed) {
        if (collapsed.isEmpty()) {
            return false;
        }
        String parent = node.parentBranchId;
        int remaining = tree.nodes.size();
        while (parent != null && remaining-- > 0) {
            if (collapsed.contains(parent)) {
                return true;
            }
            NavigationData.Node above = NavigationViewState.nodeOf(tree, parent);
            if (above == null) {
                return false;
            }
            parent = above.parentBranchId;
        }
        return false;
    }

    private static NavigationData.Node nodeOf(NavigationData.Snapshot tree, String branchId) {
        for (NavigationData.Node candidate : tree.nodes) {
            if (!candidate.branchId.equals(branchId)) continue;
            return candidate;
        }
        return null;
    }

    public static enum Phase {
        LOADING,
        READY,
        MISSING,
        ERROR;

    }

    public static enum Notice {
        STORE_UNREADABLE,
        BRANCH_GONE,
        ALREADY_ARCHIVED,
        NOT_ARCHIVED,
        SCOPE_UNRESOLVABLE;

    }

    public static final class Row {
        public final String branchId;
        public final String title;
        public final boolean archived;
        public final boolean current;
        public final boolean archivable;
        public final int childCount;

        Row(String branchId, String title, boolean archived, boolean current, boolean archivable, int childCount) {
            this.branchId = branchId;
            this.title = title;
            this.archived = archived;
            this.current = current;
            this.archivable = archivable;
            this.childCount = childCount;
        }
    }
}

