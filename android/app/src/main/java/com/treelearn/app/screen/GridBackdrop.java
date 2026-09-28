package com.treelearn.app.screen;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

import com.treelearn.app.R;

/**
 * The canvas stage's dotted-grid backdrop (058): pure decoration, drawn as a sibling <em>below</em>
 * {@link TreeCanvasView} so the accepted layout, geometry and hit testing of the canvas itself are
 * untouched by construction - a plain, non-clickable, non-focusable view under it cannot change what
 * the canvas decides.
 *
 * <p>Every parameter is an existing token, not a new measure: the dot spacing is
 * {@code @dimen/space_s6}, the dot radius is {@code @dimen/hairline} and the dot color is
 * {@code @color/tl_line} - the same ink the design already uses for the faintest separators, so the
 * grid reads as paper grain rather than a chart. The grid starts one spacing in from the top-left
 * corner and simply runs off the right and bottom edges; no parameter depends on the view's size.
 */
public final class GridBackdrop extends View {

    private final Paint dots;
    private final Paint floor;
    private final float spacing;
    private final float radius;

    public GridBackdrop(Context context) {
        this(context, null);
    }

    public GridBackdrop(Context context, AttributeSet attributes) {
        super(context, attributes);
        dots = new Paint(Paint.ANTI_ALIAS_FLAG);
        dots.setColor(getContext().getColor(R.color.tl_line));
        dots.setStyle(Paint.Style.FILL);
        floor = new Paint(Paint.ANTI_ALIAS_FLAG);
        floor.setStyle(Paint.Style.FILL);
        spacing = getResources().getDimensionPixelSize(R.dimen.space_s6);
        radius = getResources().getDimensionPixelSize(R.dimen.hairline);
        setClickable(false);
        setFocusable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (getHeight() > 0 && getWidth() > 0) {
            int ink = getContext().getColor(R.color.tl_ink);
            int start = Color.argb(19, Color.red(ink), Color.green(ink), Color.blue(ink));
            floor.setShader(new LinearGradient(0f, Math.max(0f, getHeight() - 170f),
                    0f, getHeight(), start, Color.TRANSPARENT, Shader.TileMode.CLAMP));
            canvas.drawRect(0f, 0f, getWidth(), getHeight(), floor);
        }
        for (float x = spacing; x < getWidth(); x += spacing) {
            for (float y = spacing; y < getHeight(); y += spacing) {
                canvas.drawCircle(x, y, radius, dots);
            }
        }
    }

    /** Test seams: the values must stay token-derived, so they are read back and asserted. */
    public float spacingPx() {
        return spacing;
    }

    public float dotRadiusPx() {
        return radius;
    }

    public int dotColor() {
        return dots.getColor();
    }
}
