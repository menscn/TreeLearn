package com.treelearn.app.screen;

import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.AnimationSet;
import android.view.animation.Interpolator;
import android.view.animation.PathInterpolator;
import android.view.animation.TranslateAnimation;

/**
 * The home's entrance: the prototype's numbers, the stagger, and the one place an entrance is built.
 *
 * <p><strong>Every number is the prototype's</strong>, read from
 * {@code Docs/04-ux-design/知芽-TreeLearn-高保真原型-v1.html}: line 117 gives the screen entrance
 * ({@code screenIn}, 280ms) and line 118 its transform ({@code translateX(14px)} from opacity 0);
 * line 167 gives the card entrance ({@code cardIn}, 350ms) and line 168 its transform
 * ({@code translateY(10px)} from opacity 0); line 1730 sets a card's wait to {@code i * 0.06s}.
 * The easing both share is the prototype's {@code cubic-bezier(.2,.8,.2,1)}.
 *
 * <p><strong>One builder, two callers.</strong> The screen entrance and the card entrance are the
 * same fade plus the same shift with different numbers and one waits first, so the technique lives
 * here once and the two screens never grow their own copies of it.
 *
 * <p><strong>Why it is a view {@link Animation} and not a property animation.</strong> A view
 * animation is applied to the matrix the view is drawn through, so it changes no view property, and
 * therefore cannot cause a measurement pass, cannot move a touch target and cannot leave a view
 * translucent if it never runs. The prototype's CSS transform has exactly that character. The
 * entrance is therefore also invisible to anything that inspects view state, which is what makes
 * the "the cards are usable while they enter" and "a skipped entrance leaves nothing half-drawn"
 * claims checkable rather than a matter of watching.
 *
 * <p><strong>Two platform details this rests on</strong>, both read from the SDK sources rather
 * than assumed: {@link AnimationSet#setDuration} sets the duration of every child and
 * {@link AnimationSet#setStartOffset} adds its offset to every child's at
 * {@code AnimationSet.initialize()} (Android SDK {@code AnimationSet.java}), so setting both on the
 * set is enough; and an animation with a start offset holds its first frame during that offset
 * (Android SDK {@code Animation.java} clamps the normalized time to zero while the fill is not
 * enabled), which is the prototype's {@code backwards} fill - the card is not drawn before its turn
 * rather than being drawn and then blinking away.
 *
 * <p><strong>The low-motion switch is deliberately not declared here.</strong> {@link ChatMotion} is
 * this app's one reader of the system animator scale; a second copy of that judgement is exactly the
 * kind of drift this repository has recorded once already.
 */
public final class HomeMotion {

    /** Prototype line 117: how long the home takes to slide in. */
    public static final long SCREEN_ENTER_MS = 280L;
    /** Prototype line 118: how far to the right the home starts, in dp. */
    public static final int SCREEN_SHIFT_DP = 14;
    /** Prototype line 167: how long a card takes to enter. */
    public static final long CARD_ENTER_MS = 350L;
    /** Prototype line 168: how far below its place a card starts, in dp. */
    public static final int CARD_SHIFT_DP = 10;
    /** Prototype line 1730: how much later than the card before it a card starts. */
    public static final long CARD_STAGGER_MS = 60L;
    /**
     * How many steps the wait may span.
     *
     * <p>The prototype's delay grows with the list and never stops, which is right for a page whose
     * tail you can scroll away. The home draws every topic the read returned, so a learner with a
     * long history would otherwise watch the last card arrive half a second after the first - a wait
     * on a card nobody is looking at yet. This cap stops the delay from growing past that point.
     */
    public static final int CARD_STAGGER_STEPS_MAX = 5;

    /** The prototype's {@code cubic-bezier(.2,.8,.2,1)}, shared by both entrances. */
    private static final Interpolator EASE = new PathInterpolator(0.2f, 0.8f, 0.2f, 1f);

    private HomeMotion() {
    }

    /**
     * How long the card at this place in the list waits before it starts, in milliseconds.
     *
     * <p>The first card starts at once and each next one one {@link #CARD_STAGGER_MS} later, up to
     * {@link #CARD_STAGGER_STEPS_MAX} steps. A place below the first is not a wait at all - it
     * starts at once rather than "already finished", so a caller that miscounts its list can never
     * make a card wait for a negative time.
     */
    public static long cardDelayMs(int order) {
        if (order <= 0) {
            return 0L;
        }
        return Math.min(order, CARD_STAGGER_STEPS_MAX) * CARD_STAGGER_MS;
    }

    /** The home sliding in from the right and fading up. */
    public static Animation screenEntrance(float density) {
        return entrance(SCREEN_ENTER_MS, SCREEN_SHIFT_DP, true, 0L, density);
    }

    /** The card at this place in the list, waiting its turn first. */
    public static Animation cardEntrance(int order, float density) {
        return entrance(CARD_ENTER_MS, CARD_SHIFT_DP, false, cardDelayMs(order), density);
    }

    /**
     * The entrance itself: opacity from nothing to full plus a shift towards the place the content
     * belongs, over the given duration and after the given wait.
     *
     * <p>The two halves share one interpolator and one timeline, which is what the prototype's
     * single {@code animation} declaration with two properties does.
     */
    private static Animation entrance(long durationMs, int shiftDp, boolean alongX, long waitMs,
            float density) {
        float shift = shiftDp * density;
        AnimationSet entrance = new AnimationSet(true);
        entrance.addAnimation(alongX
                ? new TranslateAnimation(shift, 0f, 0f, 0f)
                : new TranslateAnimation(0f, 0f, shift, 0f));
        entrance.addAnimation(new AlphaAnimation(0f, 1f));
        entrance.setDuration(durationMs);
        entrance.setStartOffset(waitMs);
        entrance.setInterpolator(EASE);
        return entrance;
    }
}
