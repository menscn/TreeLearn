package com.treelearn.app.screen;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import com.treelearn.app.R;

/**
 * The app shell's paper texture (T-2026-09-27-010).
 *
 * <p>Pure decoration: a full-page schedule of hairline rules, painted as a sibling <em>below</em> the
 * shell content, so the accepted layout, hit testing and scroll behaviour of every screen are untouched
 * by construction - a plain, non-clickable, non-focusable view under it cannot change what the content
 * decides. This is the same relationship {@link GridBackdrop} has to the canvas.
 *
 * <p>Every parameter is token-derived and the judgement itself lives in {@link PaperRules}: the
 * spacing is {@code @dimen/space_s8} (the prototype's page rule repeats every second spacing step),
 * the thickness is {@code @dimen/hairline}, and the colour is
 * {@link PaperRules#pageRuleColour(int, int)} - the {@code tl_line} token over {@code tl_bg} at
 * {@link PaperRules#ruleAlpha()} - computed once here, because the page background is that same
 * {@code tl_bg} and a pre-composited opaque colour needs no per-frame blend.
 *
 * <p>The texture is fixed to the viewport rather than scrolled with the content, which is what the
 * prototype does too: its rules belong to the screen container, not to the scrolling body inside it.
 */
public final class PaperRuleBackdrop extends View {

    private final Paint rules = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float spacing;
    private final float thickness;

    public PaperRuleBackdrop(Context context) {
        this(context, null);
    }

    public PaperRuleBackdrop(Context context, AttributeSet attributes) {
        super(context, attributes);
        rules.setStyle(Paint.Style.FILL);
        rules.setColor(PaperRules.pageRuleColour(context.getColor(R.color.tl_bg),
                context.getColor(R.color.tl_line)));
        spacing = getResources().getDimensionPixelSize(R.dimen.space_s8);
        thickness = getResources().getDimensionPixelSize(R.dimen.hairline);
        setClickable(false);
        setFocusable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        for (float top : PaperRules.lineOffsets(getHeight(), spacing, thickness)) {
            canvas.drawRect(0f, top, getWidth(), top + thickness, rules);
        }
    }

    /** Test seams: the values must stay token-derived, so they are read back and asserted. */
    public int ruleColor() {
        return rules.getColor();
    }

    public float spacingPx() {
        return spacing;
    }

    public float thicknessPx() {
        return thickness;
    }
}
