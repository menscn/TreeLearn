package com.treelearn.treelayout;

/**
 * The geometry and the derived state of one visible branch.
 *
 * <p>Instances are only built by {@link TreeLayout}: the geometry is derived, never hand-made, so
 * the canvas cannot invent a node position that the layout did not compute.
 *
 * <p>Y grows downwards (screen convention): the root sits at {@code y = 0} and the tree grows
 * towards negative y, which is what "root at the bottom, branches growing up" means numerically.
 */
public final class BranchGeometry {

    /** The plant role of this branch. The trunk is identified by {@code depth == 0}, not by a kind. */
    public enum Kind {
        /** No visible children: the frontier of the exploration. Draws a leaf at the tip. */
        LEAF,
        /** Has visible children: wood. Draws a tapered segment and a wood knot at the tip. */
        LIMB,
        /** Collapsed with hidden descendants: draws a bud with the hidden descendant count. */
        BUD
    }

    public final String branchId;
    public final Kind kind;
    /** Distance from the root along the parent chain; 0 for the trunk. */
    public final int depth;
    /** Committed user messages, echoed from the input because the segment length derives from it. */
    public final int rounds;
    /** This branch's own unseen stable answers. */
    public final int ownUnread;
    /** Visible descendants hidden by this branch's collapse; 0 unless {@link Kind#BUD}. */
    public final int hiddenDescendants;
    /** Sum of {@code ownUnread} over those hidden descendants; 0 unless {@link Kind#BUD}. */
    public final int hiddenUnread;
    public final float baseX;
    public final float baseY;
    public final float tipX;
    public final float tipY;
    /** Widest point of the segment, at the base. */
    public final float baseWidth;
    /** Narrowest point of the segment, at the tip. */
    public final float tipWidth;
    public final float length;
    /** Radians; 0 points straight up, negative leans left. */
    public final float direction;
    /** Root-to-current-branch path membership, flagged by the engine so the canvas need not walk the tree. */
    public final boolean onCurrentPath;

    BranchGeometry(String branchId, Kind kind, int depth, int rounds, int ownUnread,
            int hiddenDescendants, int hiddenUnread, float baseX, float baseY, float tipX, float tipY,
            float baseWidth, float tipWidth, float length, float direction, boolean onCurrentPath) {
        this.branchId = branchId;
        this.kind = kind;
        this.depth = depth;
        this.rounds = rounds;
        this.ownUnread = ownUnread;
        this.hiddenDescendants = hiddenDescendants;
        this.hiddenUnread = hiddenUnread;
        this.baseX = baseX;
        this.baseY = baseY;
        this.tipX = tipX;
        this.tipY = tipY;
        this.baseWidth = baseWidth;
        this.tipWidth = tipWidth;
        this.length = length;
        this.direction = direction;
        this.onCurrentPath = onCurrentPath;
    }

    /**
     * The one point a tap must land on to select this branch: the leaf's centre for a leaf (so the
     * 44 dp square around it covers the whole leaf shape) and the tip for wood and buds.
     */
    public Pt anchor() {
        if (kind != Kind.LEAF) {
            return new Pt(tipX, tipY);
        }
        float half = TreeLayout.LEAF_LENGTH / 2f;
        return new Pt(tipX + (float) Math.sin(direction) * half,
                tipY - (float) Math.cos(direction) * half);
    }

    /**
     * The badge count for this branch: its own unseen answers plus, for a bud, those of the
     * descendants it hides. The two parts stay separate fields on purpose so the aggregate can be
     * audited against the visible tree.
     */
    public int unreadTotal() {
        return ownUnread + hiddenUnread;
    }

    @Override
    public String toString() {
        return "BranchGeometry[" + branchId + " " + kind + " depth=" + depth + " tip=" + tipX + ","
                + tipY + " len=" + length + "]";
    }
}
