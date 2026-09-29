package com.treelearn.app.ui.math;

import android.graphics.Paint;
import android.text.TextPaint;

/**
 * {@link MathMetrics} backed by the real {@link TextPaint}. Used by {@link MathSpan} to lay out the
 * formula at the answer text's actual size and font.
 */
public final class PaintMetrics implements MathMetrics {

    private final TextPaint paint = new TextPaint();

    @Override
    public float measureText(String text, float textSize, boolean italic) {
        paint.setTextSize(textSize);
        paint.setTextSkewX(italic ? -0.25f : 0f);
        return paint.measureText(text);
    }

    @Override
    public float ascent(float textSize) {
        paint.setTextSize(textSize);
        paint.setTextSkewX(0f);
        return -paint.ascent();
    }

    @Override
    public float descent(float textSize) {
        paint.setTextSize(textSize);
        return paint.descent();
    }
}
