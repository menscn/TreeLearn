package com.treelearn.app.screen;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.widget.LinearLayout;

import com.treelearn.app.R;

/**
 * A vertical container that fades its own left and right edges out: the prototype's
 * {@code mask-image:linear-gradient(90deg,transparent,#000 20px,#000 calc(100% - 20px),transparent)}.
 *
 * <p><strong>Why a wrapper and not a fading edge on each row.</strong>
 * {@code setHorizontalFadingEdgeEnabled} is not this: it fades to the background colour, and only
 * while the content is actually scrollable, so a strip that fits would keep hard edges and a strip
 * that does not would fade to a colour rather than to nothing. The prototype fades to
 * <em>transparent</em>, always, by the same 20px at both ends.
 *
 * <p>Drawing it once for both rows is the other reason, and here this class deliberately departs
 * from the prototype: the prototype hangs its mask on each {@code .rec-row}, while this class hangs
 * one mask on the block containing both rows. The rows are two independently scrolling lines; one
 * block-level mask fades the whole band as a single object with a single offscreen layer and cannot
 * leave a seam between the rows, whereas two per-row masks cut at two heights and fade twice.
 *
 * <p><strong>The gradient is the prototype's array, and DST_IN is why.</strong> CSS's
 * {@code mask-image} multiplies the element by the mask's alpha; {@code DST_IN} does the same
 * ({@code dst_in = dst * src_alpha}). So {@code transparent} at the ends hides them and {@code #000}
 * through the middle keeps it, exactly as written, with no complementing needed.
 *
 * <p><strong>Not DST_OUT.</strong> An earlier revision erased with {@code DST_OUT} and a
 * "complemented" array, on the theory that {@code DST_OUT} reads alpha the other way round. On the
 * device that erased the entire band: a {@code drawRect}'s source alpha comes from the {@code Paint}
 * (255 by default), not from the shader, so the gradient never modulated the erase. Only the strip's
 * own border pixels outside the masked block survived, which read as two full-width rules with
 * nothing between them.
 *
 * <p>The two ends are not symmetric. The prototype's gradient starts at the box's <em>left</em> edge
 * ({@code 90deg} runs left to right, and its stops are not written in logical order), so the fade is
 * the same 20px on the physical left in both layout directions, while the right fade moves to the
 * other side when the layout direction flips. This class reproduces that as written; reading "left"
 * as "leading" would have silently reversed the prototype's own asymmetry.
 */
public final class EdgeFadeLayout extends LinearLayout {

    /** Null while there is nothing to fade with, or nothing worth fading. */
    private Paint fade;

    /** Reused across draws so a frame does not allocate. RectF, not Rect: saveLayer's Rect overload is deprecated since API 21. */
    private final RectF layerBounds = new RectF();

    public EdgeFadeLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        setOrientation(VERTICAL);
    }

    /**
     * Rebuilt on every size change: the stop positions are a function of the width. The gradient is
     * always built left-to-right in physical coordinates, so it is the stops that move when the
     * layout direction flips, never the shader.
     */
    private void rebuildFade() {
        int width = getWidth();
        int edge = getResources().getDimensionPixelSize(R.dimen.rec_fade_width);
        // A block no wider than its two fades has nothing left to show, so it keeps hard edges rather
        // than becoming an invisible band.
        if (width <= 0 || edge <= 0 || width <= edge * 2) {
            fade = null;
            return;
        }
        boolean mirrored = getLayoutDirection() == LAYOUT_DIRECTION_RTL;
        float left = (float) edge / width;
        float right = (float) (width - edge) / width;
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        // The prototype's array, verbatim. DST_IN multiplies the layer by the source alpha
        // (dst_in = dst * src_alpha), which is exactly what CSS mask-image does, so the array needs
        // no complementing here: transparent at both ends hides them, #000 through the middle keeps
        // it. (An earlier revision used DST_OUT with the "complemented" array on the theory that
        // DST_OUT's alpha reads inverted; on the device the whole band came out erased - a
        // drawRect's source alpha comes from the Paint, not the shader, so every pixel of the rect
        // erased rather than the gradient modulating it.)
        paint.setShader(new LinearGradient(0f, 0f, width, 0f,
                new int[] {0x00000000, 0xFF000000, 0xFF000000, 0x00000000},
                mirrored ? new float[] {0f, right, left, 1f} : new float[] {0f, left, right, 1f},
                Shader.TileMode.CLAMP));
        paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
        fade = paint;
    }

    @Override
    protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        rebuildFade();
    }

    @Override
    protected void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        // A locale change can flip the layout direction under a view that never changes size.
        rebuildFade();
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        if (fade == null || getWidth() <= 0 || getHeight() <= 0) {
            super.dispatchDraw(canvas);
            return;
        }
        // The children draw into an offscreen layer, so the gradient erases the strip's own pixels
        // and nothing underneath them. Without the layer, DST_OUT would punch a hole through to
        // whatever the parent drew first.
        layerBounds.set(0, 0, getWidth(), getHeight());
        int save = canvas.saveLayer(layerBounds, null);
        super.dispatchDraw(canvas);
        canvas.drawRect(layerBounds, fade);
        canvas.restoreToCount(save);
    }
}
