package com.treelearn.app.screen;

/**
 * The single judgement behind the ruled writing field (T-2026-09-27-002/004, narrowed 2026-10-23).
 *
 * <p>The prototype paints its composer textarea with a repeating linear gradient - one faint green
 * rule under each row the field shows (Docs/04-ux-design/知芽-TreeLearn-高保真原型-v1.html,
 * {@code .home-input textarea}). Android has no repeating-gradient primitive, so the texture is drawn
 * by code; <em>this class is the only place that decides where those rules land and what colour they
 * take</em>. The one view that paints them ({@link RuleEditText}) calls in here instead of deriving
 * its own - the repository has already been burned once by one rule with two entry points.
 *
 * <p><strong>What used to be here.</strong> Until 2026-10-23 this class also derived a full-page rule
 * colour for {@code PaperRuleBackdrop}, and {@link #lineOffsets} had two consumers. The user read that
 * texture on a device and asked for the background rules to go away on every screen, so the page pass
 * was deleted along with the derivation that no longer feeds anything - this repository does not keep
 * around a value nothing paints.
 *
 * <p><strong>Why the colour is derived.</strong> The prototype's token {@code --paper-write} is
 * {@code rgba(46,125,79,.075)}. It is translucent, so it cannot be copied into {@code colors.xml}
 * (the colour guard accepts the opaque hex tokens only, and adding a resource without adding a
 * {@code :root} token fails it). Instead the rule is derived from the nearest opaque token the design
 * already has: {@code tl_green} at {@link #writeAlpha()} over {@code tl_surface}, which composites to
 * (237,241,235) against the prototype's (237,242,235). {@code PaperRulesTest} pins that composite, so
 * a token edit that would move the texture away from the design turns red instead of silently shipping.
 *
 * <p>Everything here is plain integer arithmetic on purpose: the unit-test source set has JUnit only
 * (no Robolectric), so a judgement that touched {@code android.graphics} could not be regression
 * tested at all.
 */
public final class PaperRules {

    /**
     * {@code tl_green} over {@code tl_surface} at this alpha reproduces the prototype's writing rule.
     */
    private static final int WRITE_ALPHA = 18;

    private PaperRules() {
    }

    /** The writing rule's opacity, as the alpha channel of an 8-bit colour. */
    public static int writeAlpha() {
        return WRITE_ALPHA;
    }

    /**
     * The writing rule's colour: the identity {@code green} at {@link #writeAlpha()} painted over the
     * composer's {@code surface}.
     *
     * <p>Named rather than left to the painter because "which token sits on top of which" <em>is</em>
     * the design decision here - the prototype's ink is the identity green painted over the paper, not
     * the paper over the ink - and a second texture that re-assembles the same two tokens by hand is
     * the drift this repository has already been burned by.
     */
    public static int writingRuleColour(int surface, int green) {
        return composite(surface, green, WRITE_ALPHA);
    }

    /**
     * The y of each rule's top edge, for a view {@code height} tall drawing rules of
     * {@code thickness} every {@code spacing} units.
     *
     * <p>The prototype's gradient paints the <em>last</em> pixel band of every row, so a row's rule
     * sits at {@code row * spacing - thickness}; a row is only drawn when its bottom edge fits inside
     * the height, which is what the prototype's clipped gradient does at the bottom of the field.
     * Degenerate spacing returns nothing rather than looping.
     */
    public static float[] lineOffsets(int height, float spacing, float thickness) {
        if (height <= 0 || spacing <= 0f || thickness < 0f) {
            return new float[0];
        }
        int rows = (int) Math.floor(height / spacing);
        int count = 0;
        for (int row = 1; row <= rows; row++) {
            if (row * spacing - thickness >= 0f) {
                count++;
            }
        }
        float[] offsets = new float[count];
        int index = 0;
        for (int row = 1; row <= rows; row++) {
            float top = row * spacing - thickness;
            if (top >= 0f) {
                offsets[index++] = top;
            }
        }
        return offsets;
    }

    /** {@code src} painted over {@code dst} at {@code alpha}, per channel. */
    public static int composite(int dst, int src, int alpha) {
        if (alpha <= 0) {
            return rgbOf(dst);
        }
        if (alpha >= 255) {
            return rgbOf(src);
        }
        return withAlpha(rgbOf(0xFF000000
                | (blend(red(dst), red(src), alpha) << 16)
                | (blend(green(dst), green(src), alpha) << 8)
                | blend(blue(dst), blue(src), alpha)), 255);
    }

    /** The same colour with a new opacity channel. */
    public static int withAlpha(int color, int alpha) {
        return ((alpha & 0xFF) << 24) | (color & 0xFFFFFF);
    }

    /** The same colour, forced opaque. */
    public static int rgbOf(int color) {
        return 0xFF000000 | (color & 0xFFFFFF);
    }

    public static int red(int color) {
        return (color >> 16) & 0xFF;
    }

    public static int green(int color) {
        return (color >> 8) & 0xFF;
    }

    public static int blue(int color) {
        return color & 0xFF;
    }

    private static int blend(int dst, int src, int alpha) {
        return dst + Math.round((src - dst) * (alpha / 255f));
    }
}
