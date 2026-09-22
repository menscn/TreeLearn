package com.treelearn.treelayout;

/**
 * The space the tree may be fitted into, plus the two numbers the geometry itself needs: the
 * minimum touch target (§13 of the architecture: every clickable target is at least 44 dp) and the
 * soil band drawn under the root.
 *
 * <p>Width and height only affect {@link LayoutResult#fit(ViewportConstraints)}. The tree-space
 * geometry, the label bar and the hit regions deliberately do not depend on them, so the same
 * snapshot lays out identically on a phone and on a tablet and only the matrix differs.
 */
public final class ViewportConstraints {

    /** The 44 dp interaction floor from the architecture document. */
    public static final float DEFAULT_MIN_TOUCH_TARGET = 44f;
    /** Prototype fit padding. */
    public static final float DEFAULT_PADDING = 26f;
    /** Prototype soil band under the root. */
    public static final float DEFAULT_SOIL = 44f;

    public final float width;
    public final float height;
    public final float minTouchTarget;
    public final float padding;
    public final float soil;

    public static ViewportConstraints of(float width, float height) {
        return new ViewportConstraints(width, height, DEFAULT_MIN_TOUCH_TARGET, DEFAULT_PADDING,
                DEFAULT_SOIL);
    }

    public ViewportConstraints(float width, float height, float minTouchTarget, float padding,
            float soil) {
        this.width = requireFiniteNonNegative(width, "width");
        this.height = requireFiniteNonNegative(height, "height");
        this.minTouchTarget = requireFiniteNonNegative(minTouchTarget, "minTouchTarget");
        this.padding = requireFiniteNonNegative(padding, "padding");
        this.soil = requireFiniteNonNegative(soil, "soil");
        if (this.minTouchTarget <= 0f) {
            throw new IllegalArgumentException("minTouchTarget must be positive");
        }
    }

    private static float requireFiniteNonNegative(float value, String name) {
        if (!Float.isFinite(value) || value < 0f) {
            throw new IllegalArgumentException(name + " must be finite and non-negative: " + value);
        }
        return value;
    }

    @Override
    public String toString() {
        return "ViewportConstraints[" + width + "x" + height + " touch>=" + minTouchTarget
                + " padding=" + padding + " soil=" + soil + "]";
    }
}
