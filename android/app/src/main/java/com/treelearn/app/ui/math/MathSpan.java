package com.treelearn.app.ui.math;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.text.TextPaint;
import android.text.style.LineHeightSpan;
import android.text.style.ReplacementSpan;

/**
 * Draws a laid-out {@link MathNode} tree inline. It is both a {@link ReplacementSpan} (replacing the
 * placeholder character with the painted formula) and a {@link LineHeightSpan.WithDensity}: a tall
 * formula would otherwise be clipped to the line's font metrics, so {@link #chooseHeight} widens the
 * line to fit.
 *
 * <p>The geometry, the size rule and the painting itself belong to {@link MathGeometry}, because a
 * display formula is drawn by {@link MathView} as a block of its own and both hosts must agree on
 * how wide a formula is and what ink it uses.
 */
public final class MathSpan extends ReplacementSpan implements LineHeightSpan.WithDensity {

    private final MathGeometry geometry;

    private MathSpan(MathGeometry geometry) {
        this.geometry = geometry;
    }

    /** Builds a span for {@code node}; {@code block} lifts the size for a standalone formula line. */
    public static MathSpan create(MathNode node, boolean block) {
        return new MathSpan(MathGeometry.of(node, block));
    }

    /** The laid-out geometry, exposed for sizing tests / instrumentation. */
    public MathLayout.MathBox geometry() {
        return geometry.box();
    }

    public float baseTextSize() {
        return geometry.baseTextSize();
    }

    @Override
    public int getSize(Paint paint, CharSequence text, int start, int end, Paint.FontMetricsInt fm) {
        float scale = geometry.scaleFor(paint);
        MathLayout.MathBox box = geometry.box();
        if (fm != null) {
            int ascent = (int) Math.ceil(box.ascent * scale);
            int descent = (int) Math.ceil(box.descent * scale);
            fm.ascent = -ascent;
            fm.top = -ascent;
            fm.descent = descent;
            fm.bottom = descent;
        }
        return (int) Math.ceil(box.width * scale);
    }

    @Override
    public void draw(Canvas canvas, CharSequence text, int start, int end, float x, int top, int y,
            int bottom, Paint paint) {
        // The formula's own top sits at the line baseline minus its ascent; the painted baseline of
        // each glyph is therefore y - box.ascent + glyph.baselineY.
        float scale = geometry.scaleFor(paint);
        geometry.draw(canvas, paint, x, y - geometry.box().ascent * scale, scale);
    }

    @Override
    public void chooseHeight(CharSequence text, int start, int end, int spanstartv, int lineHeight,
            Paint.FontMetricsInt fm) {
        chooseHeight(text, start, end, spanstartv, lineHeight, fm, null);
    }

    @Override
    public void chooseHeight(CharSequence text, int start, int end, int spanstartv, int lineHeight,
            Paint.FontMetricsInt fm, TextPaint paint) {
        float needed = geometry.height(geometry.scaleFor(paint));
        float have = fm.descent - fm.ascent;
        if (needed > have) {
            float extra = needed - have;
            fm.ascent -= extra / 2f;
            fm.top = fm.ascent;
            fm.descent += extra / 2f;
            fm.bottom = fm.descent;
        }
    }
}
