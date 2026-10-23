package com.treelearn.app.screen;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.Test;

import com.treelearn.app.R;
import com.treelearn.app.ui.Recommendation;

/**
 * Pins the one place a recommendation's category becomes a label and a tint (T-2026-09-27-005).
 *
 * <p>Why this is a class of its own rather than a switch inside {@code RecommendationMarquee}: the
 * 2026-09-27 review (M7) asked for all three tags to be present, and the pre-slice strip held the only
 * mapping, with a {@code default:} arm that quietly answered "入门" for anything it did not recognise.
 * A mapping that can answer a question it was never asked cannot be reviewed, so the judgement moved
 * here, every kind is spelled out, and there is deliberately no default arm to fall into.
 *
 * <p>The tint is the prototype's, read off its own source rather than guessed from the tag names:
 * {@code renderRec} passes {@code isFresh=true} for the starter chips in the empty state and for the
 * new-direction row in the populated state
 * (Docs/04-ux-design/知芽-TreeLearn-高保真原型-v1.html lines 1649-1658), so only "顺着你学的" wears the
 * brand tint. Only two colours are used and both are existing tokens - the prototype's neutral pill is
 * a hard-coded grey, which this repository's token guard does not allow to become a new resource.
 */
public final class RecommendationTagTest {

    /** Module-relative roots so the test works regardless of where Gradle runs it. */
    private static final Path RES = Paths.get("src", "main", "res").toAbsolutePath();
    private static final Path SRC = Paths.get("src", "main", "java", "com", "treelearn", "app")
            .toAbsolutePath();

    private static String read(Path path) throws Exception {
        assertTrue("missing file: " + path, Files.exists(path));
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    @Test
    public void everyKindIsNamedAndNothingFallsThroughToADefault() throws Exception {
        assertEquals("入门", R.string.home_rec_tag_starter, RecommendationTag.labelRes(
                Recommendation.Kind.STARTER));
        assertEquals("顺着你学的", R.string.home_rec_tag_follow, RecommendationTag.labelRes(
                Recommendation.Kind.FOLLOW_UP));
        assertEquals("换个方向", R.string.home_rec_tag_fresh, RecommendationTag.labelRes(
                Recommendation.Kind.NEW_DIRECTION));

        // Three kinds, three different labels: a future fourth kind that reuses an existing label would
        // show up here even though every per-kind assertion above still passed.
        int starter = RecommendationTag.labelRes(Recommendation.Kind.STARTER);
        int follow = RecommendationTag.labelRes(Recommendation.Kind.FOLLOW_UP);
        int fresh = RecommendationTag.labelRes(Recommendation.Kind.NEW_DIRECTION);
        assertNotEquals(starter, follow);
        assertNotEquals(follow, fresh);
        assertNotEquals(starter, fresh);

        // The judgement must be exhaustive by construction, not by luck: no default arm may exist, so a
        // kind added to the enum without a label fails to compile instead of silently showing 入门.
        String source = read(SRC.resolve(Paths.get("screen", "RecommendationTag.java")));
        assertFalse("the mapping must not carry a default arm", source.contains("default:"));
    }

    @Test
    public void theTwoTintsAreThePrototypesRatherThanAnEvenSplit() {
        assertTrue("顺着你学的 is the brand-tinted tag", RecommendationTag.brandTinted(
                Recommendation.Kind.FOLLOW_UP));
        assertFalse("入门 is the neutral tag in the empty state", RecommendationTag.brandTinted(
                Recommendation.Kind.STARTER));
        assertFalse("换个方向 is the neutral tag", RecommendationTag.brandTinted(
                Recommendation.Kind.NEW_DIRECTION));

        assertEquals(R.color.tl_green_soft, RecommendationTag.backgroundRes(
                Recommendation.Kind.FOLLOW_UP));
        assertEquals(R.color.tl_green_deep, RecommendationTag.textColorRes(
                Recommendation.Kind.FOLLOW_UP));
        assertEquals(R.color.tl_line, RecommendationTag.backgroundRes(
                Recommendation.Kind.STARTER));
        assertEquals(R.color.tl_ink3, RecommendationTag.textColorRes(
                Recommendation.Kind.STARTER));
        assertEquals(R.color.tl_line, RecommendationTag.backgroundRes(
                Recommendation.Kind.NEW_DIRECTION));
        assertEquals(R.color.tl_ink3, RecommendationTag.textColorRes(
                Recommendation.Kind.NEW_DIRECTION));
    }

    @Test
    public void theStripReadsTheJudgementInsteadOfCarryingItsOwn() throws Exception {
        String marquee = read(SRC.resolve(Paths.get("screen", "RecommendationMarquee.java")));
        assertTrue("the strip must ask the judgement for the label",
                marquee.contains("RecommendationTag."));
        assertFalse("the strip must not map labels itself",
                marquee.contains("R.string.home_rec_tag_"));
        assertFalse("the old private mapping must be gone", marquee.contains("tagLabelRes"));
    }

    @Test
    public void theChipCarriesTheCategoryTheIconAndTheQuestionText()
            throws Exception {
        String chip = read(RES.resolve(Paths.get("layout", "item_recommendation.xml")));
        assertTrue("the category pill", chip.contains("android:id=\"@+id/rec_tag\""));
        assertTrue("the question text", chip.contains("android:id=\"@+id/rec_text\""));
        // 2026-10-22 Scheme A draws the 232dp card with a leading sparkle and no trailing chevron;
        // the card is tapped as a whole, so the chevron was decoration that implied a second action.
        assertTrue("the card's leading sparkle", chip.contains("android:id=\"@+id/rec_icon\""));
        assertTrue("the sparkle is the prototype's", chip.contains("@drawable/ic_sparkle"));
        assertFalse("no trailing chevron", chip.contains("rec_chevron"));
        assertFalse("and no right-pointing glyph at all",
                chip.contains("@drawable/ic_chevron_right"));

        // The tint is per-kind now, so a single hard-coded tint in the markup would pin every tag to one
        // tier and quietly undo the judgement above.
        assertFalse("the tag's tint must not be pinned in markup",
                chip.contains("backgroundTint=\"@color/tl_green_soft\""));
        assertFalse("the tag's text colour must not be pinned in markup",
                chip.contains("textColor=\"@color/tl_green_deep\""));
    }

    @Test
    public void theStripStillSaysWhichOfTheTwoHeadingsItIsShowing() throws Exception {
        String layout = read(RES.resolve(Paths.get("layout", "screen_home_body.xml")));
        assertTrue("the prototype's sparkle heads the strip",
                layout.contains("@drawable/ic_sparkle"));

        // The heading is 「试试这些起点」 with no topics and 「猜你想学」 once there are some - the same
        // judgement the hero reads, not a second copy of "are there topics".
        String screen = read(SRC.resolve(Paths.get("screen", "HomeScreen.java")));
        assertTrue(screen.contains("R.string.home_rec_starter"));
        assertTrue(screen.contains("R.string.home_rec_history"));
        assertFalse("the superseded static heading must be gone",
                screen.contains("R.string.home_recommendations_title"));
        assertFalse("the superseded static heading must not remain in the layout",
                layout.contains("@string/home_recommendations_title"));
    }
}
