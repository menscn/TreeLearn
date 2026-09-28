package com.treelearn.app.screen;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Pins the one paper-rule judgement (T-2026-09-27-002).
 *
 * <p>The prototype draws its paper as a full-page horizontal rule texture, and the Android side has
 * no repeating-gradient primitive, so the judgement has to live in code. The point of this test is
 * that the judgement is <em>one</em> judgement: the offset schedule and the derived colour are both
 * asserted here, so a later change to a token cannot silently move the texture away from the design
 * without turning red.
 *
 * <p>Expected colour is the prototype's own composite, taken from its token block
 * (Docs/04-ux-design/知芽-TreeLearn-高保真原型-v1.html, :root at lines 22-72):
 * {@code --paper-rule: rgba(30,43,35,.028)} painted over {@code --bg #F4F1EB} → (238,236,229).
 * The two colour constants below are the Android copies of the tokens the derivation reads
 * (android/app/src/main/res/values/colors.xml).
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
     * the {@code line} token, so exact equality is not the claim - "reads as the same texture" is.
     */
    private static final int TOLERANCE = 2;

    @Test
    public void theRuleDrawnFromTheLineTokenCompositesToThePrototypesRuleColour() {
        int painted = PaperRules.composite(BG, LINE, PaperRules.ruleAlpha());

        assertTrue("red within tolerance: " + PaperRules.red(painted),
                Math.abs(PaperRules.red(painted) - 238) <= TOLERANCE);
        assertTrue("green within tolerance: " + PaperRules.green(painted),
                Math.abs(PaperRules.green(painted) - 236) <= TOLERANCE);
        assertTrue("blue within tolerance: " + PaperRules.blue(painted),
                Math.abs(PaperRules.blue(painted) - 229) <= TOLERANCE);
    }

    @Test
    public void theRuleIsFaintRatherThanFullStrengthInk() {
        int painted = PaperRules.composite(BG, LINE, PaperRules.ruleAlpha());

        assertTrue("a rule that composites back to the background would be no texture at all",
                painted != BG);
        assertTrue("the rule must stay well under half strength, or the page reads as ruled paper",
                PaperRules.ruleAlpha() > 0 && PaperRules.ruleAlpha() < 128);
        assertTrue("and it must stay lighter than the separator colour it is derived from",
                luminance(painted) > luminance(LINE));
    }

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
        assertEquals("alpha keeps the channels", LINE, PaperRules.rgbOf(PaperRules.withAlpha(LINE, 79)));
    }

    // ---- T-2026-09-27-004: the composer's writing rule ----

    /**
     * The second texture: the prototype's {@code --paper-write: rgba(46,125,79,.075)} over
     * {@code --surface #FCFBF8} → (237,242,235). The Android copy blends {@code tl_green} over
     * {@code tl_surface} instead, because {@code tl_green} is the token the design already has, so
     * the same one-step-per-channel tolerance applies.
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
     * The two rules must not collapse into one texture drawn twice: the prototype's page rule is
     * neutral ink ({@code rgba(30,43,35,…)}, green below red) while the writing rule is the identity
     * green ({@code rgba(46,125,79,…)}, green above red). Asserting the relationship rather than just
     * the two values is what keeps a later "simplification" from merging them into one colour.
     */
    @Test
    public void theTwoRulesAreDifferentInksRatherThanOneTextureDrawnTwice() {
        int page = PaperRules.pageRuleColour(BG, LINE);
        int writing = PaperRules.writingRuleColour(SURFACE, GREEN);

        assertTrue("a writing rule that composites back to the surface would draw nothing",
                writing != SURFACE);
        assertTrue("the page rule reads as neutral ink: its green channel sits below its red one",
                PaperRules.green(page) < PaperRules.red(page));
        assertTrue("the writing rule reads as the identity green: its green channel leads",
                PaperRules.green(writing) > PaperRules.red(writing));
    }

    /**
     * Naming a rule must not change what was already on screen: the page rule the backdrop paints and
     * the writing rule the composer paints are the same composites they were before they were named,
     * which is what keeps the naming a refactor instead of a redesign.
     */
    @Test
    public void namingTheTwoRulesDoesNotChangeWhatWasAlreadyPainted() {
        assertEquals("the page rule must stay the composite the backdrop painted before it was named",
                PaperRules.composite(BG, LINE, PaperRules.ruleAlpha()),
                PaperRules.pageRuleColour(BG, LINE));
        assertEquals("and the writing rule must be the composite its own alpha describes",
                PaperRules.composite(SURFACE, GREEN, PaperRules.writeAlpha()),
                PaperRules.writingRuleColour(SURFACE, GREEN));
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

    /** Channel accessors are plain arithmetic on purpose: this class must stay JVM-testable. */
    private static double luminance(int color) {
        return 0.2126 * PaperRules.red(color) + 0.7152 * PaperRules.green(color)
                + 0.0722 * PaperRules.blue(color);
    }
}
