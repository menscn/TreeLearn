package com.treelearn.app.ui;

/**
 * Where the node popup lands, decided in pixels but without a window to measure.
 *
 * <p>A {@code PopupWindow} shows where it is told to and does not flip itself, and the window it must
 * stay inside is only known once it is attached - which would put the flip rule on the far side of a
 * device. Measuring it here instead keeps the rule assertable: the anchor and the window are numbers
 * the caller already has, and the answer is fully determined by them.
 *
 * <p>The popup opens above its anchor, because the anchor is a node in a tree that grows upwards and
 * the space above a node is the space that was just made for it. When there is no room there it opens
 * below, and it is then clamped inside the window margin on both axes, so a node near an edge still
 * gets the whole popup rather than half of one.
 */
public final class PopupPlacement {

    /** Gap kept between the popup and the window edge, and between it and its anchor. */
    public static final float MARGIN = 8f;

    public final String branchId;
    public final float x;
    public final float y;
    public final float width;
    public final float height;
    /** True when centring on the anchor would have crossed a side edge. */
    public final boolean flippedX;
    /** True when there was no room above and the popup opened downwards instead. */
    public final boolean flippedY;

    public PopupPlacement(String branchId, float x, float y, float width, float height,
            boolean flippedX, boolean flippedY) {
        if (branchId == null || branchId.isEmpty()) {
            throw new IllegalArgumentException("branchId must not be blank");
        }
        this.branchId = branchId;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.flippedX = flippedX;
        this.flippedY = flippedY;
    }

    /**
     * @param anchorX the anchor's x in screen pixels
     * @param anchorY the anchor's y in screen pixels
     * @param windowWidth the width of the window the popup must stay inside
     */
    public static PopupPlacement place(String branchId, float anchorX, float anchorY, float width,
            float height, float windowWidth, float windowHeight) {
        float centred = anchorX - width / 2f;
        boolean flippedX = centred < MARGIN || centred + width > windowWidth - MARGIN;
        boolean flippedY = anchorY - height - MARGIN < MARGIN;
        return new PopupPlacement(branchId,
                clamp(centred, MARGIN, windowWidth - width - MARGIN),
                clamp(flippedY ? anchorY + MARGIN : anchorY - height - MARGIN, MARGIN,
                        windowHeight - height - MARGIN),
                width, height, flippedX, flippedY);
    }

    /** True when the whole popup, margin included, is inside the window. */
    public boolean fitsInside(float windowWidth, float windowHeight) {
        return x >= MARGIN && x + width <= windowWidth - MARGIN
                && y >= MARGIN && y + height <= windowHeight - MARGIN;
    }

    /**
     * A popup larger than the window cannot be clamped inside it; it then sits at the margin rather
     * than at a negative coordinate, which is the one answer that keeps it on screen at all.
     */
    private static float clamp(float value, float minimum, float maximum) {
        if (maximum < minimum) {
            return minimum;
        }
        return Math.max(minimum, Math.min(maximum, value));
    }

    @Override
    public String toString() {
        return "PopupPlacement[" + branchId + " " + width + "x" + height + " at " + x + "," + y
                + (flippedX ? " flippedX" : "") + (flippedY ? " flippedY" : "") + "]";
    }
}