package com.treelearn.app.screen;

/**
 * The single judgement behind the paper texture (T-2026-09-27-002).
 *
 * <p>The prototype paints its paper with a repeating linear gradient - a full-page horizontal rule
 * every second spacing step, and a fainter green rule under the home composer's writing field
 * (Docs/04-ux-design/知芽-TreeLearn-高保真原型-v1.html, {@code .screen} at lines 112-115 and
 * {@code .home-input textarea} at lines 152-155). Android has no repeating-gradient primitive, so
 * both textures are drawn by code; <em>this class is the only place that decides where the rules
 * land and what colour they take</em>. The two views that paint them
 * ({@link PaperRuleBackdrop} for the page, {@link RuleEditText} for the composer's writing field)
 * call in here instead of each deriving their own - the repository has already been burned once by one
 * rule with two entry points.
 *
 * <p><strong>Why the colour is derived.</strong> The prototype's tokens are
 * {@code --paper-rule: rgba(30,43,35,.028)} and {@code --paper-write: rgba(46,125,79,.075)}. Both are
 * translucent, so they cannot be copied into {@code colors.xml} (the colour guard accepts the opaque
 * hex tokens only, and adding a resource without adding a {@code :root} token fails it). Instead each
 * rule is derived from the nearest opaque token the design already has:
 * <ul>
 *   <li>page rule = {@code tl_line} at {@link #ruleAlpha()} over {@code tl_bg}: composites to
 *       (240,237,231) against the prototype's (238,236,229) - a deliberately lighter rule, because
 *       the user read the prototype-strength one on a device and called it too heavy (2026-09-28);</li>
 *   <li>writing rule = {@code tl_green} at {@link #writeAlpha()} over {@code tl_surface}: composites
 *       to (237,241,235) against the prototype's (237,242,235).</li>
 * </ul>
 * {@code PaperRulesTest} pins both composites, so a token edit that would move the texture away from
 * the design turns red instead of silently shipping.
 *
 * <p>Everything here is plain integer arithmetic on purpose: the unit-test source set has JUnit only
 * (no Robolectric), so a judgement that touched {@code android.graphics} could not be regression
 * tested at all.
 */
public final class PaperRules {

    /**
     * {@code tl_line} over {@code tl_bg} at this alpha reproduces the prototype's page rule, lightened
     * on 2026-09-28.
     *
     * <p>The value is the one that minimises the per-channel distance to the prototype composite
     * (2026-09-27: {@code RULE_ALPHA = 79} → (238,235,228)) - but the user read that composite on a
     * device and called the rules too heavy, so the alpha now sits at just over a third off the
     * prototype's strength: (240,237,231), a maximum of four steps from the paper it is painted on
     * rather than seven. The tolerance in the test is what keeps it honest in both directions: too
     * light and the rule stops reading as a texture at all.
     */
    private static final int RULE_ALPHA = 50;

    /**
     * {@code tl_green} over {@code tl_surface} at this alpha reproduces the prototype's writing rule.
     */
    private static final int WRITE_ALPHA = 18;

    private PaperRules() {
    }

    /** The page rule's opacity, as the alpha channel of an 8-bit colour. */
    public static int ruleAlpha() {
        return RULE_ALPHA;
    }

    /** The writing rule's opacity, as the alpha channel of an 8-bit colour. */
    public static int writeAlpha() {
        return WRITE_ALPHA;
    }

    /**
     * The page rule's colour: the {@code line} token at {@link #ruleAlpha()} painted over the page's
     * own background.
     *
     * <p>Named rather than left to the painter because "which token sits on top of which" <em>is</em>
     * the design decision here - the prototype's ink is a neutral grey-green over the paper, not the
     * paper over the ink - and a second texture that re-assembles the same two tokens by hand is the
     * drift this repository has already been burned by. The backdrop reads this instead.
     */
    public static int pageRuleColour(int background, int line) {
        return composite(background, line, RULE_ALPHA);
    }

    /**
     * The writing rule's colour: the identity {@code green} at {@link #writeAlpha()} painted over the
     * composer's {@code surface}.
     *
     * <p>Same reasoning as {@link #pageRuleColour}: the two rules are two different inks (the page
     * rule reads as neutral ink, this one as the identity green), and only this class decides which
     * token is the paint and which is the paper. The composer's field reads this, so its rules cannot
     * drift away from the page's texture without both moving together.
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
     * the height, which is what the prototype's clipped gradient does at the bottom of the page.
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
