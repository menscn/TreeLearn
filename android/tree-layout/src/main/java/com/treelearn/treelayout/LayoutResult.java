package com.treelearn.treelayout;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Everything a canvas needs to draw and to hit-test one frame of the tree, in tree space.
 *
 * <p>The result is a pure function of the snapshot, the current branch, the density tier and the
 * viewport: no time, no randomness and no hash-map iteration order takes part, so the same input
 * always yields bit-identical numbers.
 */
public final class LayoutResult {

    private final List<BranchGeometry> branches;
    private final List<LabelGeometry> labels;
    private final List<HitRegion> hits;
    private final Bounds bounds;
    private final Map<String, BranchGeometry> byId;

    LayoutResult(List<BranchGeometry> branches, List<LabelGeometry> labels, List<HitRegion> hits,
            Bounds bounds) {
        this.branches = Collections.unmodifiableList(branches);
        this.labels = Collections.unmodifiableList(labels);
        this.hits = Collections.unmodifiableList(hits);
        this.bounds = bounds;
        Map<String, BranchGeometry> index = new LinkedHashMap<>();
        for (BranchGeometry branch : branches) {
            index.put(branch.branchId, branch);
        }
        this.byId = Collections.unmodifiableMap(index);
    }

    /** The layout of an empty tree: no geometry, zero-area bounds, a pass-through transform. */
    public static LayoutResult empty() {
        return new LayoutResult(List.of(), List.of(), List.of(), Bounds.empty());
    }

    /** Visible branches in pre-order, which is the order the canvas draws them in. */
    public List<BranchGeometry> branches() {
        return branches;
    }

    public List<LabelGeometry> labels() {
        return labels;
    }

    /** Plates first, then anchors in draw order; the order breaks exact ties in {@link #hitTest}. */
    public List<HitRegion> hits() {
        return hits;
    }

    public Bounds bounds() {
        return bounds;
    }

    /** Null when the branch is not part of this layout (archived, hidden by a collapse, unknown). */
    public BranchGeometry branch(String branchId) {
        return branchId == null ? null : byId.get(branchId);
    }

    /** Null when the branch is not visible here; see {@link BranchGeometry#anchor()}. */
    public Pt anchorOf(String branchId) {
        BranchGeometry branch = branch(branchId);
        return branch == null ? null : branch.anchor();
    }

    /**
     * How far the anchor of {@code branchId} moved between this layout (before) and {@code after}.
     *
     * <p>This is the stable-anchor hook the canvas uses after folding, unfolding or adding a
     * branch: panning by {@code delta * scale} keeps that branch at the same screen position.
     * Returns null when the branch is missing from either side, which is the honest answer: a
     * node that is gone has no position to preserve.
     */
    public Pt anchorDeltaTo(LayoutResult after, String branchId) {
        Pt before = anchorOf(branchId);
        Pt moved = after == null ? null : after.anchorOf(branchId);
        if (before == null || moved == null) {
            return null;
        }
        return new Pt(before.x - moved.x, before.y - moved.y);
    }

    /**
     * The tap target for a point in tree space, or null when nothing is there.
     *
     * <p>Regions overlap by construction — 44 dp squares cannot be laid out on a crown whose leaves
     * are 15 px apart — so among the regions containing the point the one whose centre is nearest
     * wins, and the list order breaks an exact tie. That is what makes a tap on a branch's own
     * anchor always select that branch, and a tap on a plate select the branch it labels, without
     * either rule being a special case in the caller.
     */
    public HitRegion hitTest(float x, float y) {
        HitRegion best = null;
        float bestDistance = Float.MAX_VALUE;
        for (HitRegion region : hits) {
            if (!region.contains(x, y)) {
                continue;
            }
            float dx = x - region.centerX();
            float dy = y - region.centerY();
            float distance = dx * dx + dy * dy;
            if (distance < bestDistance) {
                bestDistance = distance;
                best = region;
            }
        }
        return best;
    }

    /**
     * Fits the whole tree into the viewport, bottom-aligned: the soil band under the root ends one
     * padding above the bottom edge and the tree is centred horizontally.
     *
     * <p>Scaling is clamped to a readable range, so a tree too large to fit is cropped rather than
     * made unreadable, and a degenerate viewport (zero width or height) still yields a finite,
     * drawable transform instead of NaN.
     */
    public ViewTransform fit(ViewportConstraints viewport) {
        if (branches.isEmpty()) {
            return ViewTransform.identity();
        }
        float usableWidth = viewport.width - 2f * viewport.padding;
        float usableHeight = viewport.height - 2f * viewport.padding;
        float width = bounds.width();
        float height = bounds.height();
        float scaleX = usableWidth > 0f && width > 0f ? usableWidth / width : TreeLayout.MAX_SCALE;
        float scaleY = usableHeight > 0f && height > 0f ? usableHeight / height : TreeLayout.MAX_SCALE;
        float scale = Math.min(TreeLayout.MAX_SCALE,
                Math.max(TreeLayout.MIN_SCALE, Math.min(scaleX, scaleY)));
        float translateX = (viewport.width - width * scale) / 2f - bounds.minX * scale;
        float translateY = viewport.height - viewport.padding - bounds.maxY * scale;
        return new ViewTransform(scale, translateX, translateY);
    }

    @Override
    public String toString() {
        return "LayoutResult[branches=" + branches.size() + " labels=" + labels.size()
                + " hits=" + hits.size() + " " + bounds + "]";
    }
}
