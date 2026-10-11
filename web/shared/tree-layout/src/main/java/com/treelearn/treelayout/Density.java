package com.treelearn.treelayout;

/**
 * The three information tiers of the canvas, measured in the prototype as
 * {@code cvZoom < 0.72 ? 'far' : cvZoom < 1.05 ? 'mid' : 'near'}.
 *
 * <p>The layout never reads a zoom by itself: the canvas derives the tier from its own matrix and
 * passes it in, which is what keeps the geometry a pure function of its input.
 */
public enum Density {

    /** Far: tree shape only, no label may be placed. */
    FAR(false, false),
    /** Mid: canopy plates for leaves and titles on the current path. */
    MID(true, false),
    /** Near: the same plates plus the summary first line. */
    NEAR(true, true);

    /** Below this zoom the canvas shows no titles at all. */
    public static final float MID_START_SCALE = 0.72f;
    /** From this zoom on the canvas also shows the summary first line. */
    public static final float NEAR_START_SCALE = 1.05f;

    /** Whether titles may be placed at all. */
    public final boolean labels;
    /** Whether a leaf plate may carry the summary first line. */
    public final boolean summaries;

    Density(boolean labels, boolean summaries) {
        this.labels = labels;
        this.summaries = summaries;
    }

    /**
     * Maps a canvas zoom to a tier. The comparison is written so that a non-finite scale lands in
     * the quiet tier instead of the loudest one.
     */
    public static Density forScale(float scale) {
        if (!(scale >= MID_START_SCALE)) {
            return FAR;
        }
        return scale < NEAR_START_SCALE ? MID : NEAR;
    }
}
