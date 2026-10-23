package com.treelearn.app.screen;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.treelearn.app.ui.Recommendation;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * The recommendation strip's fixes, as pins rather than as prose.
 *
 * <p>Three complaints from the 2026-09-28 walkthrough, plus the 2026-10-22 report that the strip had
 * stopped drifting, each pinned at the level the fix actually lives at:
 *
 * <ul>
 *   <li><strong>The strip must not rebuild for a keystroke.</strong> The screen renders the whole
 *       state on every key, so the marquee has to answer "nothing changed" itself. Pinned by value
 *       comparison against the list a render was last given, because that comparison is the fix.
 *   <li><strong>The strip must fade at both ends.</strong> The prototype masks each row, so the mask
 *       is one mask over both rows; pinned as structure (one {@link EdgeFadeLayout} owning the rows,
 *       a {@code DST_IN} gradient, a fade width that is not one of the spacing tokens).
 *   <li><strong>The strip must not crowd the composer.</strong> Pinned in the layout, and pinned as
 *       a departure from the prototype rather than as a fidelity claim, because the prototype's own
 *       value is 4px and is the thing being overridden.
 *   <li><strong>The strip must keep drifting when the system's animations are off.</strong> PRD
 *       6.2.17 point 2 asks for the drift and gives touch as the pause; the earlier gate on the
 *       animator scale left a handset with animations disabled showing a still strip. Pinned by the
 *       absence of that gate and by the survival of the visibility gate that exception 4 allows.
 * </ul>
 *
 * <p>What is deliberately not pinned here is the history list's rebuild on every render: the reload
 * behaviour a learner sees on returning from a conversation is fixed in {@code HomePresenter} (the
 * topic list is posted before the strip is produced), and the list's rebuild is load-bearing for
 * {@code HomeMotionInstrumentedTest#aLaterRedrawDoesNotReplayTheEntrance}, which needs a keystroke to
 * produce a fresh card view.
 */
public final class HomeRecommendationStripTest {

    /** Module-relative roots so the test works regardless of where Gradle runs it. */
    private static final Path RES = Paths.get("src", "main", "res").toAbsolutePath();
    private static final Path SRC = Paths.get("src", "main", "java", "com", "treelearn", "app")
            .toAbsolutePath();

    private static String read(Path path) throws Exception {
        assertTrue("missing file: " + path, Files.exists(path));
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    /** The element that owns the id: from the tag opening it to the end of the open tag. */
    private static String elementAround(String xml, String id) {
        int idIndex = xml.indexOf("android:id=\"@+id/" + id + "\"");
        assertTrue("id " + id + " must exist", idIndex >= 0);
        int elementEnd = xml.indexOf('>', idIndex);
        assertTrue("malformed layout", elementEnd >= 0);
        return xml.substring(Math.max(0, xml.lastIndexOf('<', idIndex)), elementEnd);
    }

    private static String body() throws Exception {
        return read(RES.resolve(Paths.get("layout", "screen_home_body.xml")));
    }

    private static String marquee() throws Exception {
        return read(SRC.resolve(Paths.get("screen", "RecommendationMarquee.java")));
    }

    private static Recommendation chip(String question, Recommendation.Kind kind) {
        return new Recommendation(question, kind);
    }

    private static List<Recommendation> strip() {
        return Arrays.asList(
                chip("从零理解向量检索的原理", Recommendation.Kind.STARTER),
                chip("冷链断链风险有哪些", Recommendation.Kind.STARTER));
    }

    // ------------------------------------------------------------------
    // The fix for "the strip flickers while I type"
    // ------------------------------------------------------------------

    @Test
    public void aRenderThatCarriesTheSameChipsChangesNothing() throws Exception {
        String source = marquee();
        assertTrue("the strip remembers what it drew",
                source.contains("private List<Recommendation> drawn;"));
        assertTrue("and answers equality by value before doing any work",
                source.contains("if (drawn != null && next.equals(drawn)) {"));
        assertTrue("the early return must come before the teardown",
                source.indexOf("if (drawn != null && next.equals(drawn))")
                        < source.indexOf("block.removeAllViews();"));
        assertTrue("null means never drawn, so the first render is never skipped",
                source.contains("private List<Recommendation> drawn;")
                        && !source.contains("drawn = Collections.emptyList()"));
    }

    /**
     * The comparison must not be defeated by the defensive copy {@code HomeViewState} makes: an empty
     * strip that is rendered twice must be recognised the second time, and a strip whose contents
     * merely permute must not be, because the rows are built by index parity and a permutation really
     * does move chips between the two rows.
     *
     * <p>This runs against plain values rather than a view: the property under test is
     * {@link Recommendation}'s own identity-free equality, which is what makes the value comparison
     * possible in the first place. A kind or a question that stopped taking part in equality would
     * turn the early return into a no-op that silently never fires.
     */
    @Test
    public void equalContentsCompareEqualAndAReorderDoesNot() {
        List<Recommendation> first = strip();
        assertEquals("a value-equal list is equal",
                first, new ArrayList<Recommendation>(first));
        assertEquals("and an empty strip is equal to an empty strip",
                Collections.<Recommendation>emptyList(),
                new ArrayList<Recommendation>(Collections.<Recommendation>emptyList()));

        List<Recommendation> swapped = Arrays.asList(first.get(1), first.get(0));
        assertFalse("a reorder moves chips between the two rows, so it is a change",
                swapped.equals(first));

        // Both halves of a chip take part, or a change to one of them would be the flicker again.
        Recommendation same = chip(first.get(0).question, first.get(0).kind);
        assertEquals("the question takes part in equality", first.get(0), same);
        assertFalse("so does the kind",
                chip(first.get(0).question, Recommendation.Kind.FOLLOW_UP).equals(first.get(0)));
    }

    // ------------------------------------------------------------------
    // The fix for "the strip does not scroll any more"
    // ------------------------------------------------------------------

    /**
     * The drift is the recommendation area's behaviour, not an entrance: PRD 6.2.17 point 2
     * requires it and makes touch the pause, while exception 4 only excuses it off screen. The
     * strip used to return early when {@code ANIMATOR_DURATION_SCALE} was zero, so a handset with
     * animations switched off showed a still strip - reported as a defect in the 2026-10-22
     * walkthrough. Entrance motion still honours that setting through {@link ChatMotion}.
     */
    @Test
    public void theDriftDoesNotStopForTheSystemAnimatorScale() throws Exception {
        String source = marquee();
        assertFalse("PRD 6.2.17 point 2 has no reduced-motion exception",
                source.contains("ChatMotion.reduced"));
        assertFalse("and the strip reads no other form of the animator scale",
                source.contains("ANIMATOR_DURATION_SCALE"));
        assertTrue("the visibility gate that exception 4 excuses stays",
                source.contains("getGlobalVisibleRect(visiblePart)"));
        assertTrue("and the touch pause the learner is given stays",
                source.contains("RESUME_DELAY_MILLIS"));
    }

    // ------------------------------------------------------------------
    // The fix for "the strip has no feathering at the sides"
    // ------------------------------------------------------------------

    @Test
    public void theMaskIsOneMaskOverBothRowsRatherThanOnePerRow() throws Exception {
        String strip = marquee();
        assertTrue("the rows are drawn in the fading block",
                strip.contains("private final EdgeFadeLayout block;"));
        assertTrue("and the rows are added to it, not to the strip itself",
                strip.contains("block.addView(row, rowParams);"));
        assertFalse("a second fade on each row would cut the two rows at two heights",
                strip.contains("setFadingEdgeLength") || strip.contains("setHorizontalFadingEdgeEnabled"));

        String fade = read(SRC.resolve(Paths.get("screen", "EdgeFadeLayout.java")));
        assertTrue("the fade masks rather than paints over",
                fade.contains("PorterDuff.Mode.DST_IN"));
        assertTrue("with a linear gradient across the block",
                fade.contains("new LinearGradient("));
        assertTrue("and the children draw into their own layer first",
                fade.contains("saveLayer"));
        // saveLayer's Rect overload is deprecated since API 21 and does not even compile against the
        // modern signature: it must be the RectF one (this assertion exists because a Rect slipped
        // through once and was only caught by javac, not by any test).
        assertTrue("into a RectF bounds, which is the only non-deprecated saveLayer overload",
                fade.contains("private final RectF layerBounds"));
        assertFalse("never the deprecated Rect overload",
                fade.contains("private final Rect layerBounds"));
    }

    /**
     * The prototype's own array is used verbatim, because DST_IN has the same alpha meaning as a CSS
     * mask: {@code dst_in = dst * src_alpha}. Transparent at the ends hides them, #000 through the
     * middle keeps it.
     *
     * <p>This was got wrong once. The first revision erased with DST_OUT and the "complemented"
     * array, reasoning that DST_OUT reads alpha the other way round. On the device it erased the
     * whole band - a drawRect's source alpha comes from the Paint, not from the shader - and the
     * strip came up as two full-width rules with nothing between them. The assertion is on the
     * mechanism (DST_IN, prototype array) rather than on a colour literal so that the next
     * "optimise the compositing mode" edit has to argue with it.
     */
    @Test
    public void theGradientMatchesCssMaskSemanticsUnderDstIn() throws Exception {
        String fade = read(SRC.resolve(Paths.get("screen", "EdgeFadeLayout.java")));
        assertTrue("the mask multiplies rather than erases",
                fade.contains("PorterDuff.Mode.DST_IN"));
        // Only the compositing call is asserted, never the file at large: the javadoc mentions
        // DST_OUT to explain why it is not used, and a whole-file assert would trip on that prose.
        assertFalse("erasing with DST_OUT wiped the whole band on the device",
                fade.contains("new PorterDuffXfermode(PorterDuff.Mode.DST_OUT)"));
        assertTrue("the prototype's array goes in untouched",
                fade.contains("0x00000000, 0xFF000000, 0xFF000000, 0x00000000"));
        assertFalse("and is not complemented",
                fade.contains("0xFF000000, 0x00000000, 0x00000000, 0xFF000000"));
        assertTrue("the reason is written down next to it",
                fade.contains("src_alpha") || fade.contains("dst_in"));
    }

    @Test
    public void theFadeWidthIsAWidthAndNotASpacingToken() throws Exception {
        String dimens = read(RES.resolve(Paths.get("values", "dimens.xml")));
        assertTrue("the fade width is its own token",
                dimens.contains("<dimen name=\"rec_fade_width\">20dp</dimen>"));

        // The prototype spells it as 20px inside a mask-image, which is neither the gutter nor a gap.
        // Sharing a value with page_gutter is a coincidence of the prototype, not a reason to alias it:
        // changing the gutter must not silently move the fade.
        String fade = read(SRC.resolve(Paths.get("screen", "EdgeFadeLayout.java")));
        assertTrue("the layout reads the token",
                fade.contains("R.dimen.rec_fade_width"));
        assertFalse("and never spells the number inline",
                fade.contains("20f") || fade.contains("20dp"));
        assertTrue("nor reads the gutter", !fade.contains("R.dimen.page_gutter"));
    }

    /**
     * The prototype's gradient starts at the physical left ({@code 90deg}, stops written left to
     * right), so the fade is asymmetric under a right-to-left layout. Reproducing it as "leading" and
     * "trailing" would have silently reversed the design's own asymmetry.
     */
    @Test
    public void thePrototypesOwnAsymmetryIsReproducedRatherThanEvened() throws Exception {
        String fade = read(SRC.resolve(Paths.get("screen", "EdgeFadeLayout.java")));
        assertTrue("the flips are handled explicitly",
                fade.contains("mirrored ? new float[] {0f, right, left, 1f} : new float[] {0f, left, right, 1f}"));
        assertTrue("and a locale change rebuilds the stops",
                fade.contains("onConfigurationChanged"));
    }

    /**
     * A block no wider than its two fades is left alone rather than faded into invisibility - the
     * degenerate case a very narrow window would otherwise hit.
     */
    @Test
    public void aStripTooNarrowToFadeKeepsItsEdges() throws Exception {
        String fade = read(SRC.resolve(Paths.get("screen", "EdgeFadeLayout.java")));
        assertTrue("the degenerate width falls back to no fade",
                fade.contains("width <= edge * 2"));
        assertTrue("and the fallback clears the fade rather than keeping a stale one",
                fade.contains("fade = null;"));
        // The fallback hand-off is the early-exit guard at the top of dispatchDraw: a null fade
        // (or a zero-sized block) draws the children plainly and never enters the layer at all.
        // Asserting on a guard-then-plain-draw shape rather than on textual order, because both
        // super.dispatchDraw and the layer path appear more than once and an index comparison
        // between two repeated strings does not describe the branch (it once asserted the inverse
        // of the truth).
        int guard = fade.indexOf("if (fade == null || getWidth() <= 0 || getHeight() <= 0) {");
        assertTrue("the plain-draw guard exists and comes before the layer path", guard > 0);
        int guardDraw = fade.indexOf("super.dispatchDraw(canvas);", guard);
        assertTrue("the guard draws the children directly", guardDraw > guard);
        assertTrue("and returns before any saveLayer",
                fade.indexOf("saveLayer", guard) > guardDraw);
    }

    // ------------------------------------------------------------------
    // The fix for "the strip sits too close to the composer"
    // ------------------------------------------------------------------

    @Test
    public void theStripIsSeparatedFromTheComposerByOneSpaceStep() throws Exception {
        String section = elementAround(body(), "home_recommendations_section");
        assertTrue("the section now carries the separation",
                section.contains("android:layout_marginTop=\"@dimen/space_s4\""));
        String composer = elementAround(body(), "home_composer");
        assertFalse("and the composer was not the one moved, so its own padding is untouched",
                composer.contains("android:layout_marginBottom"));
    }

    /**
     * The gap is a departure from the design, and the markup says so. The prototype's
     * {@code .rec-block} has {@code padding:4px 0 6px} and no margin of its own
     * (Docs/04-ux-design/知芽-TreeLearn-高保真原型-v1.1.html line 360), so a later reader comparing the
     * two would otherwise file this as a fidelity bug and revert it.
     */
    @Test
    public void theDepartureFromThePrototypeIsRecordedWhereItLives() throws Exception {
        String source = body();
        int section = source.indexOf("android:id=\"@+id/home_recommendations_section\"");
        assertTrue("the section exists", section > 0);
        String before = source.substring(0, section);
        int comment = before.lastIndexOf("<!--");
        assertTrue("the section is introduced by a comment", comment > 0);
        String note = before.substring(comment);
        assertTrue("the note names the date it was decided",
                note.contains("2026-09-28"));
        assertTrue("and says it overrides the prototype rather than matching it",
                note.contains("departure") && note.contains("prototype"));
        assertTrue("and quotes the value it overrides",
                note.contains("4px"));
    }
}
