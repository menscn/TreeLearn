package com.treelearn.app.ui.math;

/**
 * Text measurement surface used by {@link MathLayout}. Pulled behind an interface so the layout
 * tree can be unit-tested on the JVM, where {@code android.graphics.Paint} is a throwing stub.
 * The Android implementation {@link PaintMetrics} backs the real span.
 *
 * <p>Convention: {@code ascent}/{@code descent} are positive distances from the baseline
 * (up and down respectively), matching how {@link MathLayout.MathBox} stores its geometry.
 */
public interface MathMetrics {

    /** Width of {@code text} rendered at {@code textSize}, slanted when {@code italic}. */
    float measureText(String text, float textSize, boolean italic);

    /** Distance from baseline to the top of the glyphs at {@code textSize} (positive). */
    float ascent(float textSize);

    /** Distance from baseline to the bottom of the glyphs at {@code textSize} (positive). */
    float descent(float textSize);
}
