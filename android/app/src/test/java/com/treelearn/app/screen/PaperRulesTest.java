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
 * <p>Expected colour is the design's own composite for the writing rule, taken from the prototype's
 * token block (Docs/04-ux-design/知芽-TreeLearn-高保真原型-v1.html, :root at lines 22-72):
 * {@code --paper-write: rgba(46,125,79,.075)} painted over {@code --surface #FCFBF8} → (237,242,235).
 * The page rule's target is <em>not</em> the prototype's own composite any more: the prototype strength
 * read as too heavy on a device, so the page rule was lightened on 2026-09-28 to (240,237,231), a
 * maximum of four steps from {@code tl_bg} instead of seven. The two colour constants below are the
 * Android copies of the tokens the derivation reads (android/app/src/main/res/values/colors.xml).
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

    /**
     * The page rule's composite target, lightened on 2026-09-28: the prototype's own strength
     * (238,236,229) read as too heavy on a device, so the rule now sits at (240,237,231) - a maximum
     * of four steps from {@code tl_bg} instead of seven.
     */
    private static final int PAGE_RULE_RED = 240;

    private static final int PAGE_RULE_GREEN = 237;
    private static final int PAGE_RULE_BLUE = 231;

    @Test
    public void theRuleDrawnFromTheLineTokenCompositesToTheDesignsRuleColour() {
        int painted = PaperRules.composite(BG, LINE, PaperRules.ruleAlpha());

        assertTrue("red within tolerance: " + PaperRules.red(painted),
                Math.abs(PaperRules.red(painted) - PAGE_RULE_RED) <= TOLERANCE);
        assertTrue("green within tolerance: " + PaperRules.green(painted),
                Math.abs(PaperRules.green(painted) - PAGE_RULE_GREEN) <= TOLERANCE);
        assertTrue("blue within tolerance: " + PaperRules.blue(painted),
                Math.abs(PaperRules.blue(painted) - PAGE_RULE_BLUE) <= TOLERANCE);
    }

    /**
     * The rule has to stay a texture: light enough that the page does not read as ruled paper, dark
     * enough that it is there at all. This is the pin that a later "make it fainter still" pass has to
     * get past, and the reason the 2026-09-28 lightening stopped where it did.
     */
    @Test
    public void theRuleStaysFaintWithoutDisappearingIntoThePaper() {
        int painted = PaperRules.composite(BG, LINE, PaperRules.ruleAlpha());
        int distance = Math.max(Math.max(Math.abs(PaperRules.red(painted) - PaperRules.red(BG)),
                        Math.abs(PaperRules.green(painted) - PaperRules.green(BG))),
                Math.abs(PaperRules.blue(painted) - PaperRules.blue(BG)));

        assertTrue("a rule that composites back to the background would be no texture at all",
                painted != BG);
        assertTrue("the rule must stay visible: at least two steps from the paper, measured "
                + distance, distance >= 2);
        assertTrue("and it must stay light: no more than five steps from the paper, measured "
                + distance, distance <= 5);
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
        assertEquals("alpha keeps the channels",
                LINE, PaperRules.rgbOf(PaperRules.withAlpha(LINE, PaperRules.ruleAlpha())));
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
}
