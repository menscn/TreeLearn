package com.treelearn.app.screen;

import android.content.ContentResolver;
import android.provider.Settings;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;

/**
 * The generation motion's durations and the low-motion switch (073 visual spec §4/§2 S-LOWMOTION).
 *
 * <p><strong>The four values are 073 §9's new token candidates</strong> - motion/quick (150ms),
 * motion/enter (220ms), motion/collapse (180ms) and motion/caret (800ms step) - held here, in this
 * one file, until the token gate collects them into tokens.json; no screen hardcodes a duration.
 *
 * <p><strong>Low motion staticizes everything.</strong> The system animator scale at zero means the
 * carets do not blink, the fades do not fade: every motion jumps straight to its end state, while
 * the state text and the TalkBack announcements stay exactly as they are.
 */
public final class ChatMotion {

    /** 073 §9 token candidate motion/quick: the canceled mark, the action bar's fade-in. */
    public static final long QUICK_MS = 150L;
    /** 073 §9 token candidate motion/enter: the first answer block's fade-in. */
    public static final long ENTER_MS = 220L;
    /** 073 §9 token candidate motion/collapse: the reasoning body's height collapse. */
    public static final long COLLAPSE_MS = 180L;
    /** 073 §9 token candidate motion/caret: the answer caret's blink step. */
    public static final long CARET_STEP_MS = 800L;

    private ChatMotion() {
    }

    /** Reads the system animator scale; zero means the reduced-motion mode. */
    public static boolean reduced(ContentResolver resolver) {
        return isStatic(Settings.Global.getFloat(resolver,
                Settings.Global.ANIMATOR_DURATION_SCALE, 1f));
    }

    /** The judgement without the system read, so the JVM can test it. */
    public static boolean isStatic(float animatorDurationScale) {
        return animatorDurationScale == 0f;
    }

    /** The caret's blink: one fade per {@link #CARET_STEP_MS}, reversing forever. */
    public static Animation caretBlink() {
        AlphaAnimation blink = new AlphaAnimation(1f, 0.15f);
        blink.setDuration(CARET_STEP_MS);
        blink.setRepeatMode(Animation.REVERSE);
        blink.setRepeatCount(Animation.INFINITE);
        return blink;
    }
}
