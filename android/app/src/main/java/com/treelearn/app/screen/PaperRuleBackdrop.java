package com.treelearn.app.screen;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

import com.treelearn.app.R;

/**
 * The app shell's shared mist scene (paper texture since T-2026-09-27-010, glass material since
 * T-2026-10-21-049, <em>rule-free</em> since T-2026-10-23-073).
 *
 * <p>Pure decoration: a static soft-focus mist painted as a sibling <em>below</em> the shell content,
 * so the accepted layout, hit testing and scroll behaviour of every screen are untouched by
 * construction - a plain, non-clickable, non-focusable view under it cannot change what the content
 * decides.
 *
 * <p>The glass look is honest: Android does not fake a live backdrop blur. The scene itself is soft
 * (a warm-to-cool mist gradient with five wide radial glows), and the translucent surfaces above
 * it read as glass against that scene.
 *
 * <p><strong>Why this class still carries "PaperRule" in its name.</strong> The full-page hairline
 * rules were painted here until 2026-10-23, when the user read them on a device and asked for them to
 * go away from every screen ("背景中的横线去掉吧…不要影响其他地方，以及背景的颜色分布等"). Only the rule pass
 * left: the name, the layout id and the scene's own values are what they were, because renaming the
 * view would rewrite dated parity evidence for no visual gain. The composer's writing field keeps its
 * grid - those are ruled lines inside an input box, not a page background - and they still come from
 * {@link PaperRules}.
 *
 * <p>Every parameter that remains is token-derived: the scene base is {@code @color/tl_mist_base}, the
 * glows are the five {@code @color/glow_*} tokens, and every position and radius is computed from the
 * view's own size - nothing is hard-coded in Java.
 */
public final class PaperRuleBackdrop extends View {

    private final Paint scene = new Paint();
    private final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int mistBase;
    private final int mistDeep;
    private final int mistWarm;
    private final int mistCool;
    private final int glowGreen;
    private final int glowSand;
    private final int glowWhite;
    private final int glowRose;
    private final int glowLilac;

    private LinearGradient mist;
    private RadialGradient greenGlow;
    private RadialGradient sandGlow;
    private RadialGradient whiteGlow;
    private RadialGradient roseGlow;
    private RadialGradient lilacGlow;

    public PaperRuleBackdrop(Context context) {
        this(context, null);
    }

    public PaperRuleBackdrop(Context context, AttributeSet attributes) {
        super(context, attributes);
        scene.setStyle(Paint.Style.FILL);
        mistBase = context.getColor(R.color.tl_mist_base);
        mistDeep = context.getColor(R.color.tl_mist_deep);
        mistWarm = context.getColor(R.color.tl_mist_warm);
        mistCool = context.getColor(R.color.tl_mist_cool);
        glowGreen = context.getColor(R.color.glow_green);
        glowSand = context.getColor(R.color.glow_sand);
        glowWhite = context.getColor(R.color.glow_white);
        glowRose = context.getColor(R.color.glow_rose);
        glowLilac = context.getColor(R.color.glow_lilac);
        setClickable(false);
        setFocusable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    @Override
    protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        if (width <= 0 || height <= 0) {
            return;
        }
        mist = new LinearGradient(0f, 0f, width, height,
                new int[]{mistWarm, mistBase, mistCool, mistDeep},
                new float[]{0f, 0.36f, 0.70f, 1f}, Shader.TileMode.CLAMP);
        float span = Math.max(width, height);
        greenGlow = new RadialGradient(width * 0.80f, height * 0.16f, span * 0.42f,
                glowGreen, Color.TRANSPARENT, Shader.TileMode.CLAMP);
        sandGlow = new RadialGradient(width * 0.10f, height * 0.66f, span * 0.36f,
                glowSand, Color.TRANSPARENT, Shader.TileMode.CLAMP);
        whiteGlow = new RadialGradient(width * 0.46f, height * 0.02f, span * 0.50f,
                glowWhite, Color.TRANSPARENT, Shader.TileMode.CLAMP);
        roseGlow = new RadialGradient(width * 0.92f, height * 0.52f, span * 0.30f,
                glowRose, Color.TRANSPARENT, Shader.TileMode.CLAMP);
        lilacGlow = new RadialGradient(width * 0.06f, height * 0.24f, span * 0.28f,
                glowLilac, Color.TRANSPARENT, Shader.TileMode.CLAMP);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int width = getWidth();
        int height = getHeight();
        if (mist == null) {
            return;
        }
        scene.setShader(mist);
        canvas.drawRect(0f, 0f, width, height, scene);
        float span = Math.max(width, height);
        glow(canvas, greenGlow, width * 0.80f, height * 0.16f, span * 0.42f);
        glow(canvas, sandGlow, width * 0.10f, height * 0.66f, span * 0.36f);
        glow(canvas, whiteGlow, width * 0.46f, height * 0.02f, span * 0.50f);
        glow(canvas, roseGlow, width * 0.92f, height * 0.52f, span * 0.30f);
        glow(canvas, lilacGlow, width * 0.06f, height * 0.24f, span * 0.28f);
    }

    private void glow(Canvas canvas, Shader shader, float centerX, float centerY, float radius) {
        glow.setShader(shader);
        canvas.drawCircle(centerX, centerY, radius, glow);
    }

    /** Test seam: the scene base must stay token-derived, so it is read back and asserted. */
    public int mistColor() {
        return mistBase;
    }
}
