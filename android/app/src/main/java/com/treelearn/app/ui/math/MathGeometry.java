package com.treelearn.app.ui.math;

import android.graphics.Canvas;
import android.graphics.Paint;

/**
 * One laid-out formula, plus the single rule that turns a host text size into a drawing scale.
 *
 * <p>A formula is drawn by two different hosts: {@link MathSpan} paints one inside a line of prose,
 * {@link MathView} paints a display formula as a block of its own. Both need the same geometry, the
 * same size rule and the same paint handling, so all three live here instead of in either host -
 * a second copy of a scale rule is two answers to one question, and they drift.
 *
 * <p>Color is never hard-coded: the caller's paint supplies it, so a formula matches the text around
 * it and any theme. All size ratios are named constants; nothing writes a bare pixel value. The
 * layout is computed once, not on every draw, because an inline answer refreshes often while
 * streaming.
 */
public final class MathGeometry {

    private static final float BLOCK_SCALE = 1.15f;
    private static final float INLINE_SCALE = 1.0f;
    private static final float BASE_TEXT_SIZE = 16f;

    private final boolean block;
    private final MathLayout.MathBox box;
    private final float textScale;

    private MathGeometry(boolean block, MathLayout.MathBox box, float textScale) {
        this.block = block;
        this.box = box;
        this.textScale = textScale;
    }

    /** Lays {@code node} out once; {@code block} lifts the size of a standalone formula. */
    public static MathGeometry of(MathNode node, boolean block) {
        float base = BASE_TEXT_SIZE * (block ? BLOCK_SCALE : INLINE_SCALE);
        MathMetrics metrics = new PaintMetrics();
        return new MathGeometry(block, MathLayout.layout(node, base, metrics), base);
    }

    /** An empty geometry, so a host can be built before it has anything to show. */
    public static MathGeometry empty() {
        return of(null, true);
    }

    /** The laid-out geometry, exposed for sizing tests / instrumentation. */
    public MathLayout.MathBox box() {
        return box;
    }

    /** The size the geometry was laid out at, which {@link #scaleFor} converts from. */
    public float baseTextSize() {
        return textScale;
    }

    /** The factor that makes this formula match a host drawing at {@code hostTextSize}. */
    public float scaleFor(float hostTextSize) {
        float host = hostTextSize > 0f ? hostTextSize : BASE_TEXT_SIZE;
        return host * (block ? BLOCK_SCALE : INLINE_SCALE) / textScale;
    }

    /** The factor that makes this formula match the paint it is drawn with. */
    public float scaleFor(Paint paint) {
        return scaleFor(paint == null ? BASE_TEXT_SIZE : paint.getTextSize());
    }

    public float width(float scale) {
        return box.width * scale;
    }

    public float height(float scale) {
        return (box.ascent + box.descent) * scale;
    }

    /**
     * Paints the formula with its left edge at {@code x} and its top edge at {@code topY}. Both hosts
     * pass a top, because {@link MathLayout} measures every glyph baseline down from the box top: a
     * caller that passed its own baseline instead would add the ascent twice and push the formula out
     * of the space measured for it. The caller's paint is restored, because a rule thickness or an
     * italic skew left on a shared paint colours whatever draws next.
     */
    public void draw(Canvas canvas, Paint paint, float x, float topY, float scale) {
        int argb = paint.getColor();
        float strokeWidth = paint.getStrokeWidth();
        float textSize = paint.getTextSize();
        float skew = paint.getTextSkewX();
        try {
            for (MathLayout.HLine line : box.lines) {
                paint.setStrokeWidth(line.thickness * scale);
                paint.setColor(argb);
                float lineY = topY + line.y * scale;
                canvas.drawLine(x + line.x * scale, lineY,
                        x + (line.x + line.width) * scale, lineY, paint);
            }
            for (MathLayout.Glyph glyph : box.glyphs) {
                paint.setTextSkewX(glyph.italic ? -0.25f : 0f);
                paint.setTextSize(glyph.textSize * scale);
                paint.setColor(argb);
                canvas.drawText(glyph.text, x + glyph.x * scale,
                        topY + glyph.baselineY * scale, paint);
            }
        } finally {
            paint.setStrokeWidth(strokeWidth);
            paint.setTextSize(textSize);
            paint.setTextSkewX(skew);
            paint.setColor(argb);
        }
    }
}
