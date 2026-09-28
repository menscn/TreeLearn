package com.treelearn.app.screen;

import com.treelearn.app.R;
import com.treelearn.app.ui.Recommendation;
import com.treelearn.core.domain.Guard;

/**
 * The one place a recommendation's category becomes a label and a tint (T-2026-09-27-005).
 *
 * <p>Before this class the label mapping lived inside {@code RecommendationMarquee} with a fallback arm
 * that answered "入门" for any kind it did not recognise. That arm is why the 2026-09-27 review could not
 * tell "all three tags are wired" from "one tag is wired and the other two landed on the fallback": the
 * strip would have looked right with two of the three kinds missing. Here every kind is spelled out, so
 * the compiler - not a reviewer's reading - is what complains when a fourth kind appears.
 *
 * <p><strong>The tint is read off the prototype, not off the tag names.</strong> It is tempting to split
 * three tags into three colours, but {@code renderRec} passes {@code isFresh=true} for the starter chips
 * in the empty state and for the new-direction row once topics exist
 * (Docs/04-ux-design/知芽-TreeLearn-高保真原型-v1.html lines 1649-1658), which leaves exactly one
 * brand-tinted tag. Two colours, two existing tokens:
 *
 * <ul>
 *   <li>brand - {@code tl_green_soft} pill, {@code tl_green_deep} text (the prototype's
 *       {@code --green-soft} / {@code --green-deep}).</li>
 *   <li>neutral - {@code tl_line} pill, {@code tl_ink3} text. The prototype hard-codes {@code #EDEBE2}
 *       for this pill; the token guard does not allow that grey to become a new resource, and the
 *       design-system entry for this row names {@code line} as the neutral tier, so {@code line} is
 *       what is used.</li>
 * </ul>
 *
 * <p>Nothing here touches Android APIs beyond resource ids, so the whole judgement is reachable from the
 * JVM unit tests - the same reason {@code PaperRules} is a plain class.
 */
public final class RecommendationTag {

    private RecommendationTag() {
    }

    /**
     * The tag's text. All three kinds are listed by hand; there is deliberately no fallback arm, so a
     * kind that is added without a label is a compile error rather than a chip that quietly shows 入门.
     */
    public static int labelRes(Recommendation.Kind kind) {
        Guard.object(kind, "kind");
        switch (kind) {
            case STARTER:
                return R.string.home_rec_tag_starter;
            case FOLLOW_UP:
                return R.string.home_rec_tag_follow;
            case NEW_DIRECTION:
                return R.string.home_rec_tag_fresh;
        }
        throw new IllegalArgumentException("unmapped recommendation kind: " + kind);
    }

    /** Whether the tag wears the brand tint. Only the personalised tag does. */
    public static boolean brandTinted(Recommendation.Kind kind) {
        Guard.object(kind, "kind");
        return kind == Recommendation.Kind.FOLLOW_UP;
    }

    /** The pill's background colour resource. */
    public static int backgroundRes(Recommendation.Kind kind) {
        return brandTinted(kind) ? R.color.tl_green_soft : R.color.tl_line;
    }

    /** The tag's text colour resource. */
    public static int textColorRes(Recommendation.Kind kind) {
        return brandTinted(kind) ? R.color.tl_green_deep : R.color.tl_ink3;
    }
}
