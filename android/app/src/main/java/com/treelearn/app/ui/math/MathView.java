package com.treelearn.app.ui.math;

import android.content.Context;
import android.graphics.Canvas;
import android.text.TextPaint;
import android.util.AttributeSet;
import android.view.View;

/**
 * A display formula as a view rather than as a character inside a paragraph.
 *
 * <p>The reason is width. A formula is one unwrapped line - {@code \frac}, a radical or a big
 * parenthesis has no place to break - and the answer's text is {@code match_parent}, so a formula
 * wider than the phone used to lose both ends while looking like it had rendered. Inside a {@code
 * HorizontalScrollView} this view reports its natural width, and the over-wide part becomes
 * scrollable instead of clipped.
 *
 * <p>It keeps the prose behaviour where it can: while the formula fits, the view is as wide as its
 * box and the scroll view's {@code fillViewport} stretches it to the measure, so {@link #onDraw}
 * centres the ink and a short formula still reads centred. Past that, the ink sits flush left,
 * because a centred start would hide the left end of a formula nobody can scroll back far enough to
 * read.
 */
public final class MathView extends View {

    private final TextPaint paint = new TextPaint();
    private MathGeometry geometry = MathGeometry.empty();
    private float hostTextSize;

    public MathView(Context context) {
        this(context, null);
    }

    public MathView(Context context, AttributeSet attrs) {
        super(context, attrs);
        paint.setAntiAlias(true);
    }

    /**
     * Shows {@code node} as a block formula sized and coloured to match the prose beside it.
     *
     * @param hostTextSize the answer text's size in pixels, so the formula scales with the reader's
     *                     font setting rather than with a size the renderer picked
     * @param color        the answer text's colour, so the formula follows the theme
     */
    public void setFormula(MathNode node, float hostTextSize, int color) {
        geometry = MathGeometry.of(node, true);
        this.hostTextSize = hostTextSize;
        paint.setColor(color);
        requestLayout();
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        float scale = geometry.scaleFor(hostTextSize);
        setMeasuredDimension(measureWidth((int) Math.ceil(geometry.width(scale)), widthMeasureSpec),
                resolveSize((int) Math.ceil(geometry.height(scale)), heightMeasureSpec));
    }

    /**
     * The width to report for a formula of {@code natural} pixels.
     *
     * <p>An at-most constraint is declined, not obeyed: this view's parent is a scroll view, so
     * clamping to the viewport is exactly the clipping the view exists to avoid, and the over-wide
     * remainder would have nothing left to scroll. An exactly constraint is obeyed, because that is
     * the scroll view stretching a formula that fits up to the measure so {@link #onDraw} can centre
     * it.
     */
    private int measureWidth(int natural, int widthMeasureSpec) {
        switch (MeasureSpec.getMode(widthMeasureSpec)) {
            case MeasureSpec.EXACTLY:
                return MeasureSpec.getSize(widthMeasureSpec);
            default:
                return natural;
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float scale = geometry.scaleFor(hostTextSize);
        float width = geometry.width(scale);
        float height = geometry.height(scale);
        // MathGeometry.draw takes the box's top edge, because every glyph baseline it holds is
        // measured from that top. Adding the ascent here would push the formula below its own box.
        geometry.draw(canvas, paint, centeredOffset(getWidth(), width),
                centeredOffset(getHeight(), height), scale);
    }

    /**
     * The offset that centres {@code content} inside {@code extent}, and 0 once it does not fit.
     *
     * <p>Centring is what the old centred paragraph did, so a formula that fits still reads as one.
     * Flush against the start edge is what a formula that does not fit has to do, because a centred
     * start would hide its left end from anyone who cannot scroll back far enough to read it.
     */
    public static float centeredOffset(float extent, float content) {
        return Math.max(0f, (extent - content) / 2f);
    }
}
