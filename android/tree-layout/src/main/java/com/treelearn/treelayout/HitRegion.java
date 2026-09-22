package com.treelearn.treelayout;

/**
 * A tap target, never smaller than the 44 dp floor from the architecture document.
 *
 * <p>A leaf normally has two: the plate in the canopy bar and the square around the leaf itself.
 * Both name the same branch, so a tap on either selects the branch. Regions from different branches
 * overlap whenever the crown is denser than 44 dp per leaf, which is why
 * {@link LayoutResult#hitTest(float, float)} breaks the tie by the nearest centre rather than by
 * paint order.
 */
public final class HitRegion {

    public enum Source {
        /** The title plate in the canopy bar. */
        LABEL,
        /** The square around the branch's interaction anchor. */
        ANCHOR
    }

    public final String branchId;
    public final Source source;
    public final float x;
    public final float y;
    public final float width;
    public final float height;

    HitRegion(String branchId, Source source, float x, float y, float width, float height) {
        this.branchId = branchId;
        this.source = source;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    public boolean contains(float pointX, float pointY) {
        return pointX >= x && pointX <= x + width && pointY >= y && pointY <= y + height;
    }

    public float centerX() {
        return x + width / 2f;
    }

    public float centerY() {
        return y + height / 2f;
    }

    @Override
    public String toString() {
        return "HitRegion[" + branchId + " " + source + " " + width + "x" + height + "]";
    }
}
