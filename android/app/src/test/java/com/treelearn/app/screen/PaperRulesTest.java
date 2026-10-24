package com.treelearn.app.screen;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Pins the one paper-rule judgement that is left (T-2026-09-27-002/004, narrowed 2026-10-23).
 *
 * <p>The prototype draws its composer's writing field as a ruled surface, and the Android side has no
 * repeating-gradient primitive, so the judgement has to live in code. The point of this test is that
 * the judgement is <em>one</em> judgement: the offset schedule and the derived colour are both asserted
 * here, so a later change to a token cannot silently move the texture away from the design without
 * turning red.
 *
 * <p>Expected colour is the design's own composite for the writing rule, taken from the prototype's
 * token block (Docs/04-ux-design/知芽-TreeLearn-高保真原型-v1.html, :root at lines 22-113):
 * {@code --paper-write: rgba(46,125,79,.075)} painted over {@code --surface #FCFBF8} → (237,242,235).
 * The colour constants below are the Android copies of the tokens the derivation reads
 * (android/app/src/main/res/values/colors.xml).
 *
 * <p>The full-page rule texture that these cases used to cover twice (a neutral page rule and the
 * relationship between the two inks) was removed on 2026-10-23 when the user asked for the background
 * lines to go away on every screen; {@code GlassSurfaceLayoutTest} is where "the page paints no rules
 * any more" is pinned, because that is a statement about a painter, not about this arithmetic.
 */
public final class PaperRulesTest {

    /** @color/tl_bg */
    private static final int BG = 0xFFF4F1EB;
    /** @color/tl_line */
    private static final int LINE = 0xFFE2DFD6;
    /** @color/tl_surface */
    private static final int SURFACE = 0xFFFCFBF8;
    /** @color/tl_green */
    private static final int GREEN = 0xFF2B6E45;

    /**
     * One unit of slack per channel: the prototype blends its own ink while the Android side blends
     * the {@code green} token, so exact equality is not the claim - "reads as the same texture" is.
     */
    private static final int TOLERANCE = 2;

    @Test
    public void linesSitOnTheLastPixelsOfEveryRowOfTheSpacingGrid() {
        assertArrayEquals(new float[] {30f, 62f, 94f},
                PaperRules.lineOffsets(100, 32f, 2f), 0.001f);
    }

    @Test
    public void aRowIsDrawnOnlyWhenItFitsCompletely() {
        assertArrayEquals("a row that does not fit inside the height draws no line",
                new float[] {}, PaperRules.lineOffsets(31, 32f, 2f), 0.001f);
        assertArrayEquals("a row whose bottom edge lands exactly on the height still draws",
                new float[] {30f}, PaperRules.lineOffsets(32, 32f, 2f), 0.001f);
    }

    @Test
    public void aDegenerateSpacingDrawsNothingInsteadOfLoopingForever() {
        assertArrayEquals(new float[] {}, PaperRules.lineOffsets(100, 0f, 2f), 0.001f);
        assertArrayEquals(new float[] {}, PaperRules.lineOffsets(100, -8f, 2f), 0.001f);
    }

    @Test
    public void opacityIsAppliedPerChannelAndSaturatesAtTheEnds() {
        assertEquals("full alpha replaces the destination", LINE, PaperRules.composite(BG, LINE, 255));
        assertEquals("zero alpha leaves the destination alone", BG, PaperRules.composite(BG, LINE, 0));
        assertEquals("an opaque colour is returned unchanged", LINE, PaperRules.withAlpha(LINE, 255));
        assertEquals("alpha keeps the channels",
                LINE, PaperRules.rgbOf(PaperRules.withAlpha(LINE, PaperRules.writeAlpha())));
    }

    /**
     * The prototype's {@code --paper-write: rgba(46,125,79,.075)} over {@code --surface #FCFBF8} →
     * (237,242,235). The Android copy blends {@code tl_green} over {@code tl_surface} instead, because
     * {@code tl_green} is the token the design already has, so the same one-step-per-channel tolerance
     * applies.
     */
    @Test
    public void theWritingRuleDerivedFromTheGreenTokenCompositesToThePrototypesWritingColour() {
        int painted = PaperRules.writingRuleColour(SURFACE, GREEN);

        assertTrue("red within tolerance: " + PaperRules.red(painted),
                Math.abs(PaperRules.red(painted) - 237) <= TOLERANCE);
        assertTrue("green within tolerance: " + PaperRules.green(painted),
                Math.abs(PaperRules.green(painted) - 242) <= TOLERANCE);
        assertTrue("blue within tolerance: " + PaperRules.blue(painted),
                Math.abs(PaperRules.blue(painted) - 235) <= TOLERANCE);
    }

    /**
     * Naming the rule must not change what was already on screen: the writing rule the composer paints
     * is the same composite it was before it was named, which is what keeps the naming a refactor
     * instead of a redesign.
     */
    @Test
    public void namingTheRuleDoesNotChangeWhatWasAlreadyPainted() {
        assertEquals("the writing rule must be the composite its own alpha describes",
                PaperRules.composite(SURFACE, GREEN, PaperRules.writeAlpha()),
                PaperRules.writingRuleColour(SURFACE, GREEN));
    }

    /**
     * The writing rule has to stay the identity green rather than collapsing into the neutral paper
     * tokens it is painted on: green above red is what makes it read as the product's ink instead of a
     * grey ruling, and it is the one relationship a later "just reuse the line token" pass would break.
     */
    @Test
    public void theWritingRuleIsTheIdentityGreenRatherThanGenericInk() {
        int writing = PaperRules.writingRuleColour(SURFACE, GREEN);

        assertTrue("a writing rule that composites back to the surface would draw nothing",
                writing != SURFACE);
        assertTrue("the rule reads as the identity green: its green channel leads",
                PaperRules.green(writing) > PaperRules.red(writing));
    }

    /**
     * The phase the composer's grid starts on: the prototype's gradient paints the last pixel band of
     * every row, so a two-row field (the prototype's {@code min-height:64px}) shows its first rule at
     * 31 and its second at 63 - one rule under each row it shows, none half-drawn at the top.
     */
    @Test
    public void theWritingGridStartsOnTheFirstRowsUnderline() {
        assertArrayEquals("a two-row field draws one rule under each row",
                new float[] {31f, 63f}, PaperRules.lineOffsets(64, 32f, 1f), 0.001f);
    }
}
