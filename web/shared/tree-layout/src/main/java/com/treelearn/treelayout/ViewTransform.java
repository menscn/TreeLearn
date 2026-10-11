package com.treelearn.treelayout;

/**
 * The canvas matrix as three numbers: {@code screen = tree * scale + translate}.
 *
 * <p>The engine never stores a matrix object of its own and never persists pixels; a canvas keeps
 * this value across configuration changes and rebuilds the {@code android.graphics.Matrix} from it,
 * which is why this module can stay free of {@code android.*}.
 */
public final class ViewTransform {

    public final float scale;
    public final float translateX;
    public final float translateY;

    /**
     * @throws IllegalArgumentException if the scale is not finite and positive — {@link #toTree}
     *         divides by it, so a zero or NaN scale would turn every screen point into NaN instead
     *         of failing loudly
     */
    public ViewTransform(float scale, float translateX, float translateY) {
        if (!Float.isFinite(scale) || scale <= 0f) {
            throw new IllegalArgumentException("scale must be finite and positive: " + scale);
        }
        if (!Float.isFinite(translateX) || !Float.isFinite(translateY)) {
            throw new IllegalArgumentException(
                    "translation must be finite: " + translateX + "," + translateY);
        }
        this.scale = scale;
        this.translateX = translateX;
        this.translateY = translateY;
    }

    /** The pass-through matrix, used when there is nothing to fit. */
    public static ViewTransform identity() {
        return new ViewTransform(1f, 0f, 0f);
    }

    public Pt toScreen(float treeX, float treeY) {
        return new Pt(treeX * scale + translateX, treeY * scale + translateY);
    }

    /** The inverse, used to turn a finger position back into tree space before hit testing. */
    public Pt toTree(float screenX, float screenY) {
        return new Pt((screenX - translateX) / scale, (screenY - translateY) / scale);
    }

    @Override
    public String toString() {
        return "ViewTransform[scale=" + scale + " translate=" + translateX + "," + translateY + "]";
    }
}
