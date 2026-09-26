/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.treelearn.treelayout.BranchGeometry
 *  com.treelearn.treelayout.Density
 *  com.treelearn.treelayout.LayoutNode
 *  com.treelearn.treelayout.LayoutResult
 *  com.treelearn.treelayout.TreeSnapshot
 *  com.treelearn.treelayout.ViewTransform
 *  com.treelearn.treelayout.ViewportConstraints
 */
package com.treelearn.app.ui;

import com.treelearn.app.ui.HomeViewState;
import com.treelearn.app.ui.PopupPlacement;
import com.treelearn.treelayout.BranchGeometry;
import com.treelearn.treelayout.Density;
import com.treelearn.treelayout.LayoutNode;
import com.treelearn.treelayout.LayoutResult;
import com.treelearn.treelayout.TreeSnapshot;
import com.treelearn.treelayout.ViewTransform;
import com.treelearn.treelayout.ViewportConstraints;

public final class TreeCanvasViewState {
    public final Phase phase;
    public final String topicId;
    public final String currentBranchId;
    public final ViewportConstraints viewport;
    public final LayoutResult layout;
    public final ViewTransform transform;
    public final Density density;
    public final int branches;
    public final int limbs;
    public final int leaves;
    public final int buds;
    public final int unreadBuds;
    public final int unreadTotal;
    public final TreeSnapshot snapshot;
    public final PopupPlacement popup;
    public final HomeViewState.Notice notice;

    private TreeCanvasViewState(Phase phase, String topicId, String currentBranchId, ViewportConstraints viewport, LayoutResult layout2, ViewTransform transform, TreeSnapshot snapshot, int branches, int limbs, int leaves, int buds, int unreadBuds, int unreadTotal, PopupPlacement popup, HomeViewState.Notice notice) {
        this.phase = phase;
        this.topicId = topicId;
        this.currentBranchId = currentBranchId;
        this.viewport = viewport;
        this.layout = layout2;
        this.transform = transform;
        this.snapshot = snapshot;
        this.density = Density.forScale((float)transform.scale);
        this.branches = branches;
        this.limbs = limbs;
        this.leaves = leaves;
        this.buds = buds;
        this.unreadBuds = unreadBuds;
        this.unreadTotal = unreadTotal;
        this.popup = popup;
        this.notice = notice;
    }

    public static TreeCanvasViewState initial(String topicId, String currentBranchId, ViewportConstraints viewport) {
        TreeCanvasViewState.require(viewport, "viewport");
        return TreeCanvasViewState.frame(Phase.LOADING, topicId, currentBranchId, viewport, LayoutResult.empty(), ViewTransform.identity(), null, null, null);
    }

    public boolean hasGeometry() {
        return !this.layout.branches().isEmpty();
    }

    public TreeCanvasViewState withPhase(Phase value) {
        return this.copy(value, this.topicId, this.currentBranchId, this.viewport, this.layout, this.transform, this.snapshot, this.popup, this.notice);
    }

    public TreeCanvasViewState withTopic(String value) {
        return this.copy(this.phase, value, this.currentBranchId, this.viewport, this.layout, this.transform, this.snapshot, this.popup, this.notice);
    }

    public TreeCanvasViewState withCurrentBranch(String value) {
        return this.copy(this.phase, this.topicId, value, this.viewport, this.layout, this.transform, this.snapshot, this.popup, this.notice);
    }

    public TreeCanvasViewState withViewport(ViewportConstraints value) {
        return this.copy(this.phase, this.topicId, this.currentBranchId, TreeCanvasViewState.require(value, "viewport"), this.layout, this.transform, this.snapshot, this.popup, this.notice);
    }

    public TreeCanvasViewState withSnapshot(TreeSnapshot value) {
        return this.copy(this.phase, this.topicId, this.currentBranchId, this.viewport, this.layout, this.transform, value, this.popup, this.notice);
    }

    public TreeCanvasViewState withPopup(PopupPlacement value) {
        return this.copy(this.phase, this.topicId, this.currentBranchId, this.viewport, this.layout, this.transform, this.snapshot, value, this.notice);
    }

    public TreeCanvasViewState withNotice(HomeViewState.Notice value) {
        return this.copy(this.phase, this.topicId, this.currentBranchId, this.viewport, this.layout, this.transform, this.snapshot, this.popup, value);
    }

    public String titleOf(String branchId) {
        LayoutNode node = this.nodeOf(branchId);
        return node == null ? "" : node.title;
    }

    public String summaryOf(String branchId) {
        LayoutNode node = this.nodeOf(branchId);
        return node == null ? "" : node.summary;
    }

    private LayoutNode nodeOf(String branchId) {
        return this.snapshot == null || branchId == null ? null : this.snapshot.node(branchId);
    }

    public TreeCanvasViewState withFrame(LayoutResult nextLayout, ViewTransform nextTransform) {
        return TreeCanvasViewState.frame(this.phase, this.topicId, this.currentBranchId, this.viewport, TreeCanvasViewState.require(nextLayout, "layout"), TreeCanvasViewState.require(nextTransform, "transform"), this.snapshot, this.popup, this.notice);
    }

    public TreeCanvasViewState withTransform(ViewTransform nextTransform) {
        return this.copy(this.phase, this.topicId, this.currentBranchId, this.viewport, this.layout, TreeCanvasViewState.require(nextTransform, "transform"), this.snapshot, this.popup, this.notice);
    }

    private TreeCanvasViewState copy(Phase phase, String topicId, String currentBranchId, ViewportConstraints viewport, LayoutResult layout2, ViewTransform transform, TreeSnapshot snapshot, PopupPlacement popup, HomeViewState.Notice notice) {
        return new TreeCanvasViewState(phase, topicId, currentBranchId, viewport, layout2, transform, snapshot, this.branches, this.limbs, this.leaves, this.buds, this.unreadBuds, this.unreadTotal, popup, notice);
    }

    private static TreeCanvasViewState frame(Phase phase, String topicId, String currentBranchId, ViewportConstraints viewport, LayoutResult layout2, ViewTransform transform, TreeSnapshot snapshot, PopupPlacement popup, HomeViewState.Notice notice) {
        Tally tally = Tally.of(layout2);
        return new TreeCanvasViewState(phase, topicId, currentBranchId, viewport, layout2, transform, snapshot, tally.branches, tally.limbs, tally.leaves, tally.buds, tally.unreadBuds, tally.unreadTotal, popup, notice);
    }

    private static <T> T require(T value, String name) {
        if (value == null) {
            throw new IllegalArgumentException(name + " must not be null");
        }
        return value;
    }

    public static enum Phase {
        LOADING,
        READY,
        EMPTY,
        ERROR;

    }

    private static final class Tally {
        final int branches;
        final int limbs;
        final int leaves;
        final int buds;
        final int unreadBuds;
        final int unreadTotal;

        private Tally(int branches, int limbs, int leaves, int buds, int unreadBuds, int unreadTotal) {
            this.branches = branches;
            this.limbs = limbs;
            this.leaves = leaves;
            this.buds = buds;
            this.unreadBuds = unreadBuds;
            this.unreadTotal = unreadTotal;
        }

        static Tally of(LayoutResult layout2) {
            int limbs = 0;
            int leaves = 0;
            int buds = 0;
            int unreadBuds = 0;
            int unread = 0;
            for (BranchGeometry branch : layout2.branches()) {
                switch (branch.kind) {
                    case LEAF: {
                        ++leaves;
                        break;
                    }
                    case LIMB: {
                        ++limbs;
                        break;
                    }
                    case BUD: {
                        ++buds;
                        if (branch.unreadTotal() <= 0) break;
                        ++unreadBuds;
                        break;
                    }
                    default: {
                        throw new IllegalArgumentException("unmapped branch kind: " + branch.kind);
                    }
                }
                unread += branch.unreadTotal();
            }
            return new Tally(layout2.branches().size(), limbs, leaves, buds, unreadBuds, unread);
        }
    }
}

