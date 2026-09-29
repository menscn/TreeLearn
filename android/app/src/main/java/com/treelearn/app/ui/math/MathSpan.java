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
 * <p>Color is never hard-coded - the paint's own color is used, so the formula matches the answer
 * text and any theme. All size ratios are named constants; nothing writes a bare pixel value. The
 * layout is computed once at construction (inline answers refresh often while streaming), not on
 * every draw.
 */
public final class MathSpan extends ReplacementSpan implements LineHeightSpan.WithDensity {

    private static final float BLOCK_SCALE = 1.15f;
    private static final float INLINE_SCALE = 1.0f;
    private static final float BASE_TEXT_SIZE = 16f;

    private final boolean block;
    private final MathLayout.MathBox box;
    private final float textScale;

    private MathSpan(boolean block, MathLayout.MathBox box, float textScale) {
        this.block = block;
        this.box = box;
        this.textScale = textScale;
    }

    /** Builds a span for {@code node}; {@code block} lifts the size for a standalone formula line. */
    public static MathSpan create(MathNode node, boolean block) {
        float base = BASE_TEXT_SIZE * (block ? BLOCK_SCALE : INLINE_SCALE);
        MathMetrics metrics = new PaintMetrics();
        MathLayout.MathBox box = MathLayout.layout(node, base, metrics);
        return new MathSpan(block, box, base);
    }

    /** The laid-out geometry, exposed for sizing tests / instrumentation. */
    public MathLayout.MathBox geometry() {
        return box;
    }

    public float baseTextSize() {
        return textScale;
    }

    @Override
    public int getSize(Paint paint, CharSequence text, int start, int end, Paint.FontMetricsInt fm) {
        float scale = scaleFor(paint);
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
        float scale = scaleFor(paint);
        float baseY = y - box.ascent * scale;
        int argb = paint.getColor();
        float strokeWidth = paint.getStrokeWidth();
        float textSize = paint.getTextSize();
        float skew = paint.getTextSkewX();
        try {
            for (MathLayout.HLine line : box.lines) {
                paint.setStrokeWidth(line.thickness * scale);
                paint.setColor(argb);
                float lineY = baseY + line.y * scale;
                canvas.drawLine(x + line.x * scale, lineY,
                        x + (line.x + line.width) * scale, lineY, paint);
            }
            for (MathLayout.Glyph glyph : box.glyphs) {
                paint.setTextSkewX(glyph.italic ? -0.25f : 0f);
                paint.setTextSize(glyph.textSize * scale);
                paint.setColor(argb);
                canvas.drawText(glyph.text, x + glyph.x * scale,
                        baseY + glyph.baselineY * scale, paint);
            }
        } finally {
            paint.setStrokeWidth(strokeWidth);
            paint.setTextSize(textSize);
            paint.setTextSkewX(skew);
            paint.setColor(argb);
        }
    }

    @Override
    public void chooseHeight(CharSequence text, int start, int end, int spanstartv, int lineHeight,
            Paint.FontMetricsInt fm) {
        chooseHeight(text, start, end, spanstartv, lineHeight, fm, null);
    }

    @Override
    public void chooseHeight(CharSequence text, int start, int end, int spanstartv, int lineHeight,
            Paint.FontMetricsInt fm, TextPaint paint) {
        float needed = (box.ascent + box.descent) * scaleFor(paint);
        float have = fm.descent - fm.ascent;
        if (needed > have) {
            float extra = needed - have;
            fm.ascent -= extra / 2f;
            fm.top = fm.ascent;
            fm.descent += extra / 2f;
            fm.bottom = fm.descent;
        }
    }

    private float scaleFor(Paint paint) {
        float hostTextSize = paint == null ? BASE_TEXT_SIZE : paint.getTextSize();
        if (hostTextSize <= 0f) {
            hostTextSize = BASE_TEXT_SIZE;
        }
        float targetTextSize = hostTextSize * (block ? BLOCK_SCALE : INLINE_SCALE);
        return targetTextSize / textScale;
    }
}
