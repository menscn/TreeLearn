package com.treelearn.treelayout;

/**
 * A point in tree space (before the canvas matrix) or in screen space (after it).
 *
 * <p>Coordinates are compared with a tolerance rather than with {@code equals}: the same logical
 * point can be reached through different orders of float operations, and pretending otherwise
 * would make the geometry tests lie.
 */
public final class Pt {

    public final float x;
    public final float y;

    public Pt(float x, float y) {
        this.x = x;
        this.y = y;
    }

    @Override
    public String toString() {
        return "(" + x + ", " + y + ")";
    }
}
