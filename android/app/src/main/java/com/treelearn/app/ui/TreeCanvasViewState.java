package com.treelearn.app.ui;

import com.treelearn.treelayout.BranchGeometry;
import com.treelearn.treelayout.Density;
import com.treelearn.treelayout.LayoutNode;
import com.treelearn.treelayout.LayoutResult;
import com.treelearn.treelayout.TreeSnapshot;
import com.treelearn.treelayout.ViewTransform;
import com.treelearn.treelayout.ViewportConstraints;

/**
 * Everything the canvas draws, as one immutable value.
 *
 * <p>Three invariants are built in rather than left to the screen, because each one is a rule that two
 * places could otherwise disagree about:
 *
 * <ul>
 *   <li><strong>The tier is derived from the zoom, never set beside it.</strong> Changing the transform
 *       re-derives {@link Density}, so a canvas cannot draw near-tier titles at far-tier zoom.
 *   <li><strong>The counts are read off the geometry.</strong> Leaves, wood, buds, unread buds and the
 *       unread total are tallied from the very {@link LayoutResult} that will be painted, so there is
 *       no second number to drift.
 *   <li><strong>The viewport is part of the state.</strong> The popup flip and the rotation both need
 *       the window the last frame was fitted for, and a view that remembered it separately would be
 *       the second copy of it.
 * </ul>
 *
 * <p>The failure vocabulary is {@link HomeViewState.Notice}, the same one the home screen and the
 * conversation use: one mapping from "what went wrong" to "what the learner is told", not one per
 * screen.
 */
public final class TreeCanvasViewState {

    /** Which of the four canvas layouts is on screen. */
    public enum Phase {
        /** The store is being read. */
        LOADING,
        /** A tree was read: the canvas is drawable. */
        READY,
        /** There is no tree to draw - no topic yet, or none left. */
        EMPTY,
        /** The store could not be read; the previous canvas stays on screen. */
        ERROR
    }

    public final Phase phase;
    /** Null while the topic is not known, or when the branch has no topic left. */
    public final String topicId;
    public final String currentBranchId;
    public final ViewportConstraints viewport;
    public final LayoutResult layout;
    public final ViewTransform transform;
    public final Density density;

    /** Visible branch count, read off {@link #layout}. */
    public final int branches;
    /** Visible wood: branches with children. */
    public final int limbs;
    /** Visible leaves: the frontier of the exploration. */
    public final int leaves;
    /** Visible buds: collapsed branches hiding descendants. */
    public final int buds;
    /** Buds that carry at least one unseen answer, hidden or their own. */
    public final int unreadBuds;
    /** Unseen answers the canvas is currently showing a badge for. */
    public final int unreadTotal;

    /**
     * The tree this frame was laid out from: what a plate's title, a summary line and the node card
     * read. The geometry names branches by id only, so the text has to travel beside it - and it
     * travels as the same snapshot the layout consumed, never as a second map that could disagree.
     */
    public final TreeSnapshot snapshot;

    /** The open node popup, or null. */
    public final PopupPlacement popup;
    /** Null when there is nothing to report. */
    public final HomeViewState.Notice notice;

    private TreeCanvasViewState(Phase phase, String topicId, String currentBranchId,
            ViewportConstraints viewport, LayoutResult layout, ViewTransform transform,
            TreeSnapshot snapshot, int branches, int limbs, int leaves, int buds, int unreadBuds,
            int unreadTotal, PopupPlacement popup, HomeViewState.Notice notice) {
        this.phase = phase;
        this.topicId = topicId;
        this.currentBranchId = currentBranchId;
        this.viewport = viewport;
        this.layout = layout;
        this.transform = transform;
        this.snapshot = snapshot;
        this.density = Density.forScale(transform.scale);
        this.branches = branches;
        this.limbs = limbs;
        this.leaves = leaves;
        this.buds = buds;
        this.unreadBuds = unreadBuds;
        this.unreadTotal = unreadTotal;
        this.popup = popup;
        this.notice = notice;
    }

    /** The state before the first read: loading, an empty canvas, a pass-through matrix. */
    public static TreeCanvasViewState initial(String topicId, String currentBranchId,
            ViewportConstraints viewport) {
        require(viewport, "viewport");
        return frame(Phase.LOADING, topicId, currentBranchId, viewport, LayoutResult.empty(),
                ViewTransform.identity(), null, null, null);
    }

    public boolean hasGeometry() {
        return !layout.branches().isEmpty();
    }

    public TreeCanvasViewState withPhase(Phase value) {
        return copy(value, topicId, currentBranchId, viewport, layout, transform, snapshot, popup,
                notice);
    }

    public TreeCanvasViewState withTopic(String value) {
        return copy(phase, value, currentBranchId, viewport, layout, transform, snapshot, popup,
                notice);
    }

    public TreeCanvasViewState withCurrentBranch(String value) {
        return copy(phase, topicId, value, viewport, layout, transform, snapshot, popup, notice);
    }

    public TreeCanvasViewState withViewport(ViewportConstraints value) {
        return copy(phase, topicId, currentBranchId, require(value, "viewport"), layout, transform,
                snapshot, popup, notice);
    }

    public TreeCanvasViewState withSnapshot(TreeSnapshot value) {
        return copy(phase, topicId, currentBranchId, viewport, layout, transform, value, popup,
                notice);
    }

    public TreeCanvasViewState withPopup(PopupPlacement value) {
        return copy(phase, topicId, currentBranchId, viewport, layout, transform, snapshot, value,
                notice);
    }

    public TreeCanvasViewState withNotice(HomeViewState.Notice value) {
        return copy(phase, topicId, currentBranchId, viewport, layout, transform, snapshot, popup,
                value);
    }

    /** The branch's title, or the empty string when this frame does not hold it. */
    public String titleOf(String branchId) {
        LayoutNode node = nodeOf(branchId);
        return node == null ? "" : node.title;
    }

    /** The branch's summary first line, or the empty string when it has none. */
    public String summaryOf(String branchId) {
        LayoutNode node = nodeOf(branchId);
        return node == null ? "" : node.summary;
    }

    private LayoutNode nodeOf(String branchId) {
        return snapshot == null || branchId == null ? null : snapshot.node(branchId);
    }

    /**
     * A new geometry and the matrix it will be drawn with, in one step: setting one without the other
     * would leave a frame whose badges and whose positions describe two different trees.
     */
    public TreeCanvasViewState withFrame(LayoutResult nextLayout, ViewTransform nextTransform) {
        return frame(phase, topicId, currentBranchId, viewport, require(nextLayout, "layout"),
                require(nextTransform, "transform"), snapshot, popup, notice);
    }

    /** Pan and zoom: the geometry is unchanged, the tier is re-derived from the new scale. */
    public TreeCanvasViewState withTransform(ViewTransform nextTransform) {
        return copy(phase, topicId, currentBranchId, viewport, layout,
                require(nextTransform, "transform"), snapshot, popup, notice);
    }

    private TreeCanvasViewState copy(Phase phase, String topicId, String currentBranchId,
            ViewportConstraints viewport, LayoutResult layout, ViewTransform transform,
            TreeSnapshot snapshot, PopupPlacement popup, HomeViewState.Notice notice) {
        return new TreeCanvasViewState(phase, topicId, currentBranchId, viewport, layout, transform,
                snapshot, branches, limbs, leaves, buds, unreadBuds, unreadTotal, popup, notice);
    }

    private static TreeCanvasViewState frame(Phase phase, String topicId, String currentBranchId,
            ViewportConstraints viewport, LayoutResult layout, ViewTransform transform,
            TreeSnapshot snapshot, PopupPlacement popup, HomeViewState.Notice notice) {
        Tally tally = Tally.of(layout);
        return new TreeCanvasViewState(phase, topicId, currentBranchId, viewport, layout, transform,
                snapshot, tally.branches, tally.limbs, tally.leaves, tally.buds, tally.unreadBuds,
                tally.unreadTotal, popup, notice);
    }

    private static <T> T require(T value, String name) {
        if (value == null) {
            throw new IllegalArgumentException(name + " must not be null");
        }
        return value;
    }

    /** The counts, computed once from the geometry they describe. */
    private static final class Tally {

        final int branches;
        final int limbs;
        final int leaves;
        final int buds;
        final int unreadBuds;
        final int unreadTotal;

        private Tally(int branches, int limbs, int leaves, int buds, int unreadBuds,
                int unreadTotal) {
            this.branches = branches;
            this.limbs = limbs;
            this.leaves = leaves;
            this.buds = buds;
            this.unreadBuds = unreadBuds;
            this.unreadTotal = unreadTotal;
        }

        static Tally of(LayoutResult layout) {
            int limbs = 0;
            int leaves = 0;
            int buds = 0;
            int unreadBuds = 0;
            int unread = 0;
            for (BranchGeometry branch : layout.branches()) {
                switch (branch.kind) {
                    case LEAF:
                        leaves++;
                        break;
                    case LIMB:
                        limbs++;
                        break;
                    case BUD:
                        buds++;
                        if (branch.unreadTotal() > 0) {
                            unreadBuds++;
                        }
                        break;
                    default:
                        throw new IllegalArgumentException("unmapped branch kind: " + branch.kind);
                }
                unread += branch.unreadTotal();
            }
            return new Tally(layout.branches().size(), limbs, leaves, buds, unreadBuds, unread);
        }
    }
}