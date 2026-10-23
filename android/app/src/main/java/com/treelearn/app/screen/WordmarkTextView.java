package com.treelearn.app.screen;

import android.content.Context;
import android.graphics.Canvas;
import android.text.Layout;
import android.util.AttributeSet;
import android.widget.TextView;

/**
 * The home wordmark, framed at the proposal's {@code line-height: 1} instead of the font's own box.
 *
 * <p>The bundled Noto Serif SC reports a 2.858em CFF bounding box, and Android uses that box as the
 * line's ascent and descent: at 41sp the wordmark measured 381px around 124px of ink, which pushed
 * the subtitle 52dp below it instead of the proposal's 12dp (2026-10-22 walkthrough). The layout
 * gives this view a {@code brand_line_height} frame; drawing the font's own line box centred in
 * that frame keeps the glyphs where the proposal puts them without editing the OFL font or
 * changing the metrics of the library's serif title.
 */
public final class WordmarkTextView extends TextView {

    public WordmarkTextView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        Layout layout = getLayout();
        if (layout == null) {
            super.onDraw(canvas);
            return;
        }
        // TextView clips its layout to the view's height in whatever coordinate space the canvas is
        // in, so translating the canvas before super.onDraw() only exposes the top of the glyphs.
        // Drawing the single line at the centred baseline sidesteps that clip: the baseline comes
        // from the layout, so the glyphs keep the position the font was designed for.
        float baseline = (getHeight() - layout.getHeight()) / 2f + layout.getLineBaseline(0);
        canvas.drawText(layout.getText().toString(), layout.getLineLeft(0), baseline, getPaint());
    }
}
