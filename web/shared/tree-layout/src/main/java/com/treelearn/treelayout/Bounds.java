package com.treelearn.treelayout;

/**
 * An immutable axis-aligned rectangle in tree space.
 *
 * <p>The overall bounds the layout reports include the soil band below the root, because the fit
 * rule aligns {@code maxY} to the bottom padding: aligning the raw tips instead would leave the
 * soil half off-screen.
 */
public final class Bounds {

    public final float minX;
    public final float minY;
    public final float maxX;
    public final float maxY;

    public Bounds(float minX, float minY, float maxX, float maxY) {
        this.minX = minX;
        this.minY = minY;
        this.maxX = maxX;
        this.maxY = maxY;
    }

    /** A zero-area box; what an empty tree reports. */
    public static Bounds empty() {
        return new Bounds(0f, 0f, 0f, 0f);
    }

    public float width() {
        return maxX - minX;
    }

    public float height() {
        return maxY - minY;
    }

    public boolean isEmpty() {
        return width() <= 0f && height() <= 0f;
    }

    public boolean contains(float x, float y) {
        return x >= minX && x <= maxX && y >= minY && y <= maxY;
    }

    @Override
    public String toString() {
        return "Bounds[" + minX + ", " + minY + " .. " + maxX + ", " + maxY + "]";
    }
}
