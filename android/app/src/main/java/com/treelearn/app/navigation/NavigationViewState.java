package com.treelearn.app.navigation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Everything the navigation view draws, as one immutable value (technical design §12).
 *
 * <p>The screen holds no state of its own: the tree, the current selection, the summary strip and
 * the open confirmation all live here, so a JVM test can assert what the learner would see without
 * inflating a view.
 *
 * <p><strong>Archived rows are kept in their own list.</strong> "Hidden from the default list" and
 * "gone" are different facts, and a single list with a boolean flag would let a renderer forget the
 * difference. Two lists make "the archived branch is still recoverable" a shape rather than a rule
 * someone has to remember.
 *
 * <p><strong>The summary strip is never borrowed.</strong> {@link #summaryFor} names the branch the
 * text belongs to, and {@link #withSummary} drops a result that is not the current branch's own -
 * so a sibling's or a descendant's summary cannot appear under this branch's heading. A failed read
 * keeps the previous text only while it still belongs to the branch that is selected, which is what
 * "a failure keeps the old result" has to mean once the selection can move.
 */
public final class NavigationViewState {

    /** Which of the navigation layouts is on screen. */
    public enum Phase {
        /** The store is being read. */
        LOADING,
        /** A tree is on screen. */
        READY,
        /** The topic is gone: there is nothing left to navigate. */
        MISSING,
        /** The store could not be read; the previous selection is kept. */
        ERROR
    }

    /** A refusal the learner must be told about. Categories, not sentences: the screen owns wording. */
    public enum Notice {
        STORE_UNREADABLE,
        BRANCH_GONE,
        ALREADY_ARCHIVED,
        NOT_ARCHIVED,
        SCOPE_UNRESOLVABLE
    }

    /** One drawable row. */
    public static final class Row {

        public final String branchId;
        public final String title;
        /** True when the branch is archived: off the default list, never gone. */
        public final boolean archived;
        public final boolean current;
        /**
         * False for a topic's single root branch (BASE-14): it is the one branch whose archiving would
         * take the whole tree off the canvas and out of every list with nothing left to restore it
         * from. The screen draws the archive control from this fact and never decides it itself.
         */
        public final boolean archivable;
        /** How many live (non-archived) branches hang directly off this one. */
        public final int childCount;

        Row(String branchId, String title, boolean archived, boolean current, boolean archivable,
                int childCount) {
            this.branchId = branchId;
            this.title = title;
            this.archived = archived;
            this.current = current;
            this.archivable = archivable;
            this.childCount = childCount;
        }
    }

    public final Phase phase;
    public final String topicId;
    /** The branch the learner is on; null when nothing is selected. */
    public final String selectedBranchId;
    /** Every branch that is not archived, in tree order. */
    public final List<Row> visible;
    /** The archived branches, kept apart so "hidden" never means "gone". */
    public final List<Row> archived;
    /** The branch {@link #summary} describes; null when no summary is shown at all. */
    public final String summaryFor;
    public final String summary;
    public final List<String> keyPoints;
    /** True when the last read for the current branch failed and the previous result is still shown. */
    public final boolean summaryStale;
    /** The folded groups' branch ids: their descendants are hidden, the group itself stays. */
    public final List<String> collapsed;
    /** True when the summary strip is folded: the text stays, the strip is not drawn. */
    public final boolean summaryCollapsed;
    /** The open confirmation, or null. Nothing is written while this is set. */
    public final NavigationData.Plan pendingDelete;
    public final Notice notice;

    /** The tree the lists were built from; null until the first successful read. */
    private final NavigationData.Snapshot snapshot;

    private NavigationViewState(Phase phase, NavigationData.Snapshot snapshot, String topicId,
            String selectedBranchId, String summaryFor, String summary, List<String> keyPoints,
            boolean summaryStale, NavigationData.Plan pendingDelete, Notice notice,
            List<String> collapsed, boolean summaryCollapsed) {
        this.phase = phase;
        this.snapshot = snapshot;
        this.topicId = topicId;
        this.selectedBranchId = selectedBranchId;
        this.collapsed = Collections.unmodifiableList(new ArrayList<String>(collapsed));
        this.summaryCollapsed = summaryCollapsed;
        this.visible = rows(snapshot, selectedBranchId, false, this.collapsed);
        this.archived = rows(snapshot, selectedBranchId, true, this.collapsed);
        this.summaryFor = summaryFor;
        this.summary = summary;
        this.keyPoints = Collections.unmodifiableList(new ArrayList<String>(keyPoints));
        this.summaryStale = summaryStale;
        this.pendingDelete = pendingDelete;
        this.notice = notice;
    }

    public static NavigationViewState initial(String topicId, String selectedBranchId) {
        return new NavigationViewState(Phase.LOADING, null, topicId, selectedBranchId, null, "",
                Collections.<String>emptyList(), false, null, null,
                Collections.<String>emptyList(), false);
    }

    public NavigationViewState withPhase(Phase value) {
        return new NavigationViewState(value, snapshot, topicId, selectedBranchId, summaryFor,
                summary, keyPoints, summaryStale, pendingDelete, notice, collapsed, summaryCollapsed);
    }

    /** Adopts a freshly read tree, together with the selection resolved against that tree. */
    public NavigationViewState withSnapshot(NavigationData.Snapshot tree, String selected) {
        return new NavigationViewState(Phase.READY, tree, tree.topicId, selected, summaryFor,
                summary, keyPoints, summaryStale, pendingDelete, notice, collapsed, summaryCollapsed);
    }

    public NavigationViewState withSelection(String branchId) {
        return new NavigationViewState(phase, snapshot, topicId, branchId, summaryFor, summary,
                keyPoints, summaryStale, pendingDelete, notice, collapsed, summaryCollapsed);
    }

    /** Shows {@code read}, unless it is not the current branch's own summary. */
    public NavigationViewState withSummary(NavigationData.Summary read) {
        if (read == null || selectedBranchId == null || !read.branchId.equals(selectedBranchId)) {
            return withSummaryFor(null, "", Collections.<String>emptyList(), summaryStale);
        }
        return withSummaryFor(read.branchId, read.text, read.keyPoints, false);
    }

    /** Keeps the previous result, but only while it still belongs to the branch that is selected. */
    public NavigationViewState withSummaryFailed() {
        boolean stillOurs = summaryFor != null && summaryFor.equals(selectedBranchId);
        return withSummaryFor(stillOurs ? summaryFor : null, stillOurs ? summary : "",
                stillOurs ? keyPoints : Collections.<String>emptyList(), true);
    }

    public NavigationViewState withPendingDelete(NavigationData.Plan plan) {
        return new NavigationViewState(phase, snapshot, topicId, selectedBranchId, summaryFor,
                summary, keyPoints, summaryStale, plan, notice, collapsed, summaryCollapsed);
    }

    public NavigationViewState withNotice(Notice value) {
        return new NavigationViewState(phase, snapshot, topicId, selectedBranchId, summaryFor,
                summary, keyPoints, summaryStale, pendingDelete, value, collapsed, summaryCollapsed);
    }

    /** The state a deleted tree leaves behind: nothing to show, so the view leaves the tree. */
    public NavigationViewState cleared() {
        return new NavigationViewState(Phase.MISSING, null, topicId, null, null, "",
                Collections.<String>emptyList(), false, null, null,
                Collections.<String>emptyList(), false);
    }

    /** Folding is a view fact: the set lives here and is never written to the store. */
    public NavigationViewState withCollapsed(List<String> folded) {
        if (folded == null) {
            throw new IllegalArgumentException("folded must not be null");
        }
        return new NavigationViewState(phase, snapshot, topicId, selectedBranchId, summaryFor,
                summary, keyPoints, summaryStale, pendingDelete, notice, folded, summaryCollapsed);
    }

    public NavigationViewState withSummaryCollapsed(boolean folded) {
        return new NavigationViewState(phase, snapshot, topicId, selectedBranchId, summaryFor,
                summary, keyPoints, summaryStale, pendingDelete, notice, collapsed, folded);
    }

    private NavigationViewState withSummaryFor(String forBranch, String text, List<String> points,
            boolean stale) {
        return new NavigationViewState(phase, snapshot, topicId, selectedBranchId, forBranch, text,
                points, stale, pendingDelete, notice, collapsed, summaryCollapsed);
    }

    private static List<Row> rows(NavigationData.Snapshot tree, String selected, boolean archived,
            List<String> collapsed) {
        if (tree == null) {
            return Collections.emptyList();
        }
        List<Row> rows = new ArrayList<Row>();
        for (NavigationData.Node node : tree.nodes) {
            if (node.archived == archived && !isFoldedAway(tree, node, collapsed)) {
                rows.add(new Row(node.branchId, node.title, node.archived,
                        node.branchId.equals(selected), !node.isRoot(),
                        liveChildren(tree, node.branchId)));
            }
        }
        return Collections.unmodifiableList(rows);
    }

    /**
     * How many branches sit directly under one node <em>on the live tree</em>. An archived child is not
     * counted: it is exactly the kind of descendant the row's own line stops reporting, because restoring
     * it moves it back into the count and that is the visible half of "archiving is reversible".
     */
    private static int liveChildren(NavigationData.Snapshot tree, String branchId) {
        int count = 0;
        for (NavigationData.Node node : tree.nodes) {
            if (branchId.equals(node.parentBranchId) && !node.archived) {
                count++;
            }
        }
        return count;
    }

    /**
     * A node is hidden when one of its <em>ancestors</em> is folded; the folded group itself stays on
     * the list. The walk stops when the chain leaves the snapshot, so a node whose parent is not in
     * the read model stays visible rather than disappearing with nothing to explain it.
     */
    private static boolean isFoldedAway(NavigationData.Snapshot tree, NavigationData.Node node,
            List<String> collapsed) {
        if (collapsed.isEmpty()) {
            return false;
        }
        String parent = node.parentBranchId;
        int remaining = tree.nodes.size();
        while (parent != null && remaining-- > 0) {
            if (collapsed.contains(parent)) {
                return true;
            }
            NavigationData.Node above = nodeOf(tree, parent);
            if (above == null) {
                return false;
            }
            parent = above.parentBranchId;
        }
        return false;
    }

    private static NavigationData.Node nodeOf(NavigationData.Snapshot tree, String branchId) {
        for (NavigationData.Node candidate : tree.nodes) {
            if (candidate.branchId.equals(branchId)) {
                return candidate;
            }
        }
        return null;
    }
}
