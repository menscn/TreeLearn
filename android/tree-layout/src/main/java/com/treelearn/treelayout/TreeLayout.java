package com.treelearn.treelayout;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The deterministic tree geometry kernel: plant-shaped layout, canopy label placement and hit
 * regions, all in tree space and all testable on a plain JVM.
 *
 * <p>Inputs are an immutable {@link TreeSnapshot}, the current branch, a {@link Density} tier and
 * the {@link ViewportConstraints}; the output is one {@link LayoutResult}. Nothing here reads a
 * clock, a random source or a canvas, and no pixel is ever persisted: a branch's position is
 * re-derived from the snapshot every time the tree changes.
 *
 * <p>The numeric constants below are the measured values of the high-fidelity prototype
 * ({@code Docs/04-ux-design/知芽-TreeLearn-高保真原型-v1.html}), which the architecture document
 * names as authoritative for the canvas: segment base 120 plus 36 per committed round capped at
 * 300, depth decay 0.82, fan 46 degrees capped at 16 degrees per extra sibling, sprout offset 0.22,
 * leaf 34 by 15, plate 136 by 24 with 30 between rows.
 *
 * <p>The algorithm is the prototype's, with order made explicit: walk the visible tree in pre-order
 * and give every branch one segment whose length comes from its own committed rounds and its depth;
 * fan the children by how many leaves hang under each of them, sprouting each one a little further
 * along its parent so they do not all leave from a single point; hang a leaf blade at every tip that
 * has no visible child; place leaf plates in a bar above the crown, one row at a time; and derive hit
 * regions and bounds from the result.
 *
 * <p>Archived branches and everything under them never enter the layout, and a collapsed branch's
 * descendants are counted and then skipped, which is what turns it into a bud rather than a limb.
 */
public final class TreeLayout {

    /** Segment length at depth 0 with no committed rounds. */
    public static final float SEGMENT_BASE = 120f;
    /** Length added per committed user message (retries and resets add no message). */
    public static final float SEGMENT_STEP = 36f;
    /** Hard cap on one segment, so a long conversation cannot push the tree off the canvas. */
    public static final float SEGMENT_MAX = 300f;
    /** Per-depth decay, which is what makes the trunk dominate. */
    public static final float DEPTH_DECAY = 0.82f;
    /** Half-angle of the widest sibling fan. */
    public static final float FAN_MAX_DEGREES = 46f;
    /** Degrees of fan added per extra sibling. */
    public static final float FAN_STEP_DEGREES = 16f;
    /** How far down the parent segment a child sprouts, as a fraction of that segment. */
    public static final float SPROUT_RATIO = 0.22f;
    /** Leaf blade length; the leaf hangs from the segment tip towards its pointy end. */
    public static final float LEAF_LENGTH = 34f;
    public static final float LEAF_WIDTH = 15f;
    /** Narrowest and widest wood. */
    public static final float LIMB_WIDTH_MIN = 2f;
    public static final float LIMB_WIDTH_MAX = 14f;
    /** Canopy plate size and lane spacing. */
    public static final float LABEL_WIDTH = 136f;
    public static final float LABEL_HEIGHT = 24f;
    /** Extra plate height for the summary first line in the near tier. */
    public static final float LABEL_SUMMARY_HEIGHT = 19f;
    /** Distance between two plate rows when no summary line is shown. */
    public static final float LABEL_LANE_GAP = 30f;
    /** Horizontal clearance a plate keeps from its neighbour in the same row. */
    public static final float LABEL_H_GAP = 8f;
    /** Rows the canopy bar may use; a branch that finds no row keeps its leaf and stays tappable. */
    public static final int LABEL_MAX_ROWS = 4;
    /** Gap the bar keeps above the highest leaf tip. */
    public static final float CROWN_GAP = 14f;
    /** Gap the bar keeps above the highest leaf box, so a plate can never cover a leaf. */
    public static final float LEAF_CLEARANCE = 6f;
    /** Readability window of the fit scale. */
    public static final float MIN_SCALE = 0.32f;
    public static final float MAX_SCALE = 1.25f;
    /** Fraction of the base width left at the tip, so a segment reads as tapered wood. */
    public static final float TAPER = 0.66f;

    private static final float DEGREES_TO_RADIANS = (float) (Math.PI / 180.0);

    private TreeLayout() {
    }

    /**
     * Lays out the visible tree.
     *
     * @param snapshot the immutable input, in the caller's domain order
     * @param currentBranchId the branch the user is standing on; null or a branch that is not
     *        visible falls back to the root, so an archived current branch still yields a layout
     * @param density the information tier derived from the canvas zoom
     * @param viewport the space to fit into, plus the 44 dp touch floor and the soil band
     */
    public static LayoutResult compute(TreeSnapshot snapshot, String currentBranchId, Density density,
            ViewportConstraints viewport) {
        if (snapshot.isEmpty()) {
            return LayoutResult.empty();
        }
        Tree tree = new Tree(snapshot);
        tree.derive();
        tree.place();
        String current = tree.resolveCurrent(currentBranchId);
        tree.markCurrentPath(current);
        tree.build();
        List<LabelGeometry> labels = placeLabels(tree, density, current);
        List<HitRegion> hitRegions = hitRegions(tree, labels, viewport);
        Bounds bounds = treeBounds(tree, labels, viewport.soil);
        return new LayoutResult(tree.branches(), labels, hitRegions, bounds);
    }

    /** Length of one segment: rounds and depth only, capped so the tree cannot grow without bound. */
    private static float segmentLength(int depth, int rounds) {
        float length = (SEGMENT_BASE + rounds * SEGMENT_STEP) * (float) Math.pow(DEPTH_DECAY, depth);
        return Math.min(SEGMENT_MAX, length);
    }

    /** Wood width: thicker with more leaves below, thinner with depth. */
    private static float limbWidth(int depth, int leavesUnder) {
        float width = (LIMB_WIDTH_MIN + 2.6f * (float) Math.sqrt(leavesUnder)) / (1f + 0.5f * depth);
        return Math.min(LIMB_WIDTH_MAX, Math.max(LIMB_WIDTH_MIN, width));
    }

    /**
     * The leaf blade as an axis-aligned box, used both to keep the canopy bar clear of the leaves
     * and to include the blades in the overall bounds.
     */
    private static float[] leafBox(Slot slot) {
        float forwardX = (float) Math.sin(slot.direction);
        float forwardY = -(float) Math.cos(slot.direction);
        float sideX = (float) Math.cos(slot.direction) * (LEAF_WIDTH / 2f);
        float sideY = (float) Math.sin(slot.direction) * (LEAF_WIDTH / 2f);
        float endX = slot.tipX + forwardX * LEAF_LENGTH;
        float endY = slot.tipY + forwardY * LEAF_LENGTH;
        float minX = Math.min(slot.tipX - sideX, Math.min(slot.tipX + sideX, Math.min(endX - sideX, endX + sideX)));
        float minY = Math.min(slot.tipY - sideY, Math.min(slot.tipY + sideY, Math.min(endY - sideY, endY + sideY)));
        float maxX = Math.max(slot.tipX - sideX, Math.max(slot.tipX + sideX, Math.max(endX - sideX, endX + sideX)));
        float maxY = Math.max(slot.tipY - sideY, Math.max(slot.tipY + sideY, Math.max(endY - sideY, endY + sideY)));
        return new float[] {minX, minY, maxX, maxY};
    }

    private static List<LabelGeometry> placeLabels(Tree tree, Density density, String current) {
        List<LabelGeometry> labels = new ArrayList<>();
        if (!density.labels) {
            return labels;
        }
        List<Slot> leaves = new ArrayList<>();
        float crownTop = Float.MAX_VALUE;
        float minLeafBoxTop = Float.MAX_VALUE;
        for (Slot slot : tree.visible) {
            if (slot.kind != BranchGeometry.Kind.LEAF) {
                continue;
            }
            leaves.add(slot);
            crownTop = Math.min(crownTop, slot.leafTipY);
            minLeafBoxTop = Math.min(minLeafBoxTop, leafBox(slot)[1]);
        }
        if (leaves.isEmpty()) {
            return labels;
        }
        float barBottom = Math.min(crownTop - CROWN_GAP, minLeafBoxTop - LEAF_CLEARANCE);
        float laneGap = LABEL_LANE_GAP + (density.summaries ? LABEL_SUMMARY_HEIGHT : 0f);
        List<Slot> ordered = new ArrayList<>(leaves);
        ordered.sort(Comparator
                .comparingDouble((Slot slot) -> slot.leafTipX)
                .thenComparing(slot -> slot.node.id));
        Slot pinned = tree.slot(current);
        if (pinned != null && (!pinned.visible || pinned.kind != BranchGeometry.Kind.LEAF)) {
            pinned = null;
        }
        Lane[] lanes = new Lane[LABEL_MAX_ROWS];
        if (pinned != null) {
            addLabel(labels, lanes, pinned, density, barBottom, laneGap, true);
        }
        for (Slot leaf : ordered) {
            if (leaf == pinned) {
                continue;
            }
            addLabel(labels, lanes, leaf, density, barBottom, laneGap, false);
        }
        return labels;
    }

    /**
     * Tries to give one leaf a plate, walking the rows until one has room. A leaf that finds no row
     * is left unlabelled on purpose: the plate is dropped, the leaf and its hit region are not.
     */
    private static void addLabel(List<LabelGeometry> labels, Lane[] lanes, Slot leaf, Density density,
            float barBottom, float laneGap, boolean pinned) {
        boolean showSummary = density.summaries && !leaf.node.summary.isEmpty();
        float height = LABEL_HEIGHT + (showSummary ? LABEL_SUMMARY_HEIGHT : 0f);
        float x1 = leaf.leafTipX - LABEL_WIDTH / 2f;
        float x2 = x1 + LABEL_WIDTH;
        for (int row = 0; row < LABEL_MAX_ROWS; row++) {
            if (lanes[row] == null) {
                lanes[row] = new Lane();
            }
            Lane lane = lanes[row];
            if (lane.conflicts(x1, x2)) {
                continue;
            }
            if (pinned) {
                lane.addPinned(x1, x2);
            } else {
                lane.addStream(x1, x2);
            }
            float laneBottom = barBottom - row * laneGap;
            labels.add(new LabelGeometry(leaf.node.id, x1, laneBottom - height, LABEL_WIDTH, height,
                    row, showSummary, leaf.leafTipX, leaf.leafTipY));
            return;
        }
    }

    private static List<HitRegion> hitRegions(Tree tree, List<LabelGeometry> labels,
            ViewportConstraints viewport) {
        List<HitRegion> regions = new ArrayList<>();
        for (LabelGeometry label : labels) {
            float width = Math.max(viewport.minTouchTarget, label.width);
            float height = Math.max(viewport.minTouchTarget, label.height);
            regions.add(new HitRegion(label.branchId, HitRegion.Source.LABEL,
                    label.x + label.width / 2f - width / 2f, label.y + label.height / 2f - height / 2f,
                    width, height));
        }
        float side = Math.max(viewport.minTouchTarget, LEAF_LENGTH + 10f);
        for (Slot slot : tree.visible) {
            Pt anchor = slot.geometry.anchor();
            regions.add(new HitRegion(slot.node.id, HitRegion.Source.ANCHOR,
                    anchor.x - side / 2f, anchor.y - side / 2f, side, side));
        }
        return regions;
    }

    /**
     * Overall extent in tree space, soil band included. Segment endpoints, leaf blades and plates are
     * all covered; the wood stroke is not, because its half width (at most 7) stays well inside the
     * fit padding the canvas keeps around the content.
     */
    private static Bounds treeBounds(Tree tree, List<LabelGeometry> labels, float soil) {
        float minX = Float.MAX_VALUE;
        float minY = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE;
        float maxY = -Float.MAX_VALUE;
        for (Slot slot : tree.visible) {
            minX = Math.min(minX, Math.min(slot.baseX, slot.tipX));
            maxX = Math.max(maxX, Math.max(slot.baseX, slot.tipX));
            minY = Math.min(minY, Math.min(slot.baseY, slot.tipY));
            maxY = Math.max(maxY, Math.max(slot.baseY, slot.tipY));
            if (slot.kind == BranchGeometry.Kind.LEAF) {
                float[] box = leafBox(slot);
                minX = Math.min(minX, box[0]);
                minY = Math.min(minY, box[1]);
                maxX = Math.max(maxX, box[2]);
                maxY = Math.max(maxY, box[3]);
            }
        }
        for (LabelGeometry label : labels) {
            minX = Math.min(minX, label.x);
            minY = Math.min(minY, label.y);
            maxX = Math.max(maxX, label.x + label.width);
            maxY = Math.max(maxY, label.y + label.height);
        }
        return new Bounds(minX, minY, maxX, maxY + soil);
    }

    /**
     * One canopy row. Plates are added left to right, so a row only has to remember its rightmost
     * plate; the current branch's plate is pinned first and can sit anywhere in the row, so it is
     * remembered separately.
     */
    private static final class Lane {
        private boolean hasPinned;
        private float pinnedX1;
        private float pinnedX2;
        private boolean hasStream;
        private float streamX1;
        private float streamX2;

        boolean conflicts(float x1, float x2) {
            if (hasPinned && x1 < pinnedX2 + LABEL_H_GAP && x2 > pinnedX1 - LABEL_H_GAP) {
                return true;
            }
            return hasStream && x1 < streamX2 + LABEL_H_GAP && x2 > streamX1 - LABEL_H_GAP;
        }

        void addPinned(float x1, float x2) {
            hasPinned = true;
            pinnedX1 = x1;
            pinnedX2 = x2;
        }

        void addStream(float x1, float x2) {
            hasStream = true;
            streamX1 = x1;
            streamX2 = x2;
        }
    }

    /** One visible branch plus everything the geometry needs to derive from it. */
    private static final class Slot {
        final LayoutNode node;
        final List<Slot> children = new ArrayList<>();
        Slot parent;
        int depth;
        int leavesUnder;
        int descendants;
        int subtreeUnread;
        boolean onCurrentPath;
        boolean visible;
        BranchGeometry.Kind kind;
        float baseX;
        float baseY;
        float tipX;
        float tipY;
        float baseWidth;
        float tipWidth;
        float length;
        float direction;
        float leafTipX;
        float leafTipY;
        BranchGeometry geometry;

        Slot(LayoutNode node) {
            this.node = node;
        }
    }

    /** The visible tree: reachable slots in pre-order, with maps used only for lookups. */
    private static final class Tree {
        private final List<Slot> order = new ArrayList<>();
        private final List<Slot> visible = new ArrayList<>();
        private final Map<String, Slot> byId = new HashMap<>();
        private Slot root;

        Tree(TreeSnapshot snapshot) {
            Map<String, Slot> candidates = new HashMap<>();
            for (LayoutNode node : snapshot.nodes()) {
                if (!node.archived) {
                    candidates.put(node.id, new Slot(node));
                }
            }
            for (LayoutNode node : snapshot.nodes()) {
                if (node.archived) {
                    continue;
                }
                Slot slot = candidates.get(node.id);
                Slot parent = node.parentId == null ? null : candidates.get(node.parentId);
                if (node.parentId != null && parent == null) {
                    continue;
                }
                slot.parent = parent;
                if (parent == null) {
                    root = slot;
                } else {
                    parent.children.add(slot);
                }
            }
            collect(root, 0);
        }

        private void collect(Slot slot, int depth) {
            slot.depth = depth;
            byId.put(slot.node.id, slot);
            order.add(slot);
            for (Slot child : slot.children) {
                collect(child, depth + 1);
            }
        }

        Slot slot(String id) {
            return id == null ? null : byId.get(id);
        }

        /**
         * The branch the user is standing on, or the root when the request names a branch that is
         * not on screen — an archived branch, one hidden under a collapsed ancestor, or an unknown
         * id all mean the same thing to the geometry, and falling back to the root keeps the canvas
         * drawable instead of blank.
         */
        String resolveCurrent(String requested) {
            Slot slot = slot(requested);
            return slot != null && slot.visible ? requested : byId.isEmpty() ? null : root.node.id;
        }

        void markCurrentPath(String current) {
            for (Slot slot = slot(current); slot != null; slot = slot.parent) {
                slot.onCurrentPath = true;
            }
        }

        /** Bottom-up pass: leaf weights, descendant counts, unread totals and the plant role. */
        void derive() {
            for (int i = order.size() - 1; i >= 0; i--) {
                Slot slot = order.get(i);
                int leaves = 0;
                int descendants = 0;
                int unread = 0;
                for (Slot child : slot.children) {
                    leaves += child.leavesUnder;
                    descendants += 1 + child.descendants;
                    unread += child.subtreeUnread;
                }
                slot.leavesUnder = slot.children.isEmpty() ? 1 : leaves;
                slot.descendants = descendants;
                slot.subtreeUnread = slot.node.ownUnread + unread;
                if (slot.children.isEmpty()) {
                    slot.kind = BranchGeometry.Kind.LEAF;
                } else {
                    slot.kind = slot.node.collapsed ? BranchGeometry.Kind.BUD : BranchGeometry.Kind.LIMB;
                }
            }
        }

        void place() {
            place(root, 0f, 0f, 0f);
        }

        private void place(Slot slot, float baseX, float baseY, float direction) {
            slot.visible = true;
            visible.add(slot);
            slot.baseX = baseX;
            slot.baseY = baseY;
            slot.direction = direction;
            slot.length = segmentLength(slot.depth, slot.node.rounds);
            float forwardX = (float) Math.sin(direction);
            float forwardY = -(float) Math.cos(direction);
            slot.tipX = baseX + forwardX * slot.length;
            slot.tipY = baseY + forwardY * slot.length;
            slot.baseWidth = limbWidth(slot.depth, slot.leavesUnder);
            slot.tipWidth = slot.baseWidth * TAPER;
            if (slot.kind == BranchGeometry.Kind.LEAF) {
                slot.leafTipX = slot.tipX + forwardX * LEAF_LENGTH;
                slot.leafTipY = slot.tipY + forwardY * LEAF_LENGTH;
            }
            List<Slot> kids = slot.node.collapsed ? List.of() : slot.children;
            if (kids.isEmpty()) {
                return;
            }
            float fan = Math.min(FAN_MAX_DEGREES, (kids.size() - 1) * FAN_STEP_DEGREES)
                    * DEGREES_TO_RADIANS;
            float total = 0f;
            for (Slot kid : kids) {
                total += kid.leavesUnder;
            }
            float accumulated = 0f;
            for (int i = 0; i < kids.size(); i++) {
                Slot kid = kids.get(i);
                float start = accumulated / total;
                accumulated += kid.leavesUnder;
                float middle = (start + accumulated / total) / 2f;
                float back = kids.size() > 1
                        ? (1f - (float) i / (float) (kids.size() - 1)) * SPROUT_RATIO * slot.length
                        : 0f;
                place(kid, slot.tipX - forwardX * back, slot.tipY - forwardY * back,
                        direction + (middle - 0.5f) * 2f * fan);
            }
        }

        void build() {
            for (Slot slot : visible) {
                boolean bud = slot.kind == BranchGeometry.Kind.BUD;
                slot.geometry = new BranchGeometry(slot.node.id, slot.kind, slot.depth, slot.node.rounds,
                        slot.node.ownUnread,
                        bud ? slot.descendants : 0,
                        bud ? slot.subtreeUnread - slot.node.ownUnread : 0,
                        slot.baseX, slot.baseY, slot.tipX, slot.tipY, slot.baseWidth, slot.tipWidth,
                        slot.length, slot.direction, slot.onCurrentPath);
            }
        }

        List<BranchGeometry> branches() {
            List<BranchGeometry> branches = new ArrayList<>(visible.size());
            for (Slot slot : visible) {
                branches.add(slot.geometry);
            }
            return branches;
        }
    }
}
