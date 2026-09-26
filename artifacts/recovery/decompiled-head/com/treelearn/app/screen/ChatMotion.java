/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  android.content.ContentResolver
 *  android.provider.Settings$Global
 *  android.view.animation.AlphaAnimation
 *  android.view.animation.Animation
 */
package com.treelearn.app.screen;

import android.content.ContentResolver;
import android.provider.Settings;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;

public final class ChatMotion {
    public static final long QUICK_MS = 150L;
    public static final long ENTER_MS = 220L;
    public static final long COLLAPSE_MS = 180L;
    public static final long CARET_STEP_MS = 800L;

    private ChatMotion() {
    }

    public static boolean reduced(ContentResolver resolver) {
        return ChatMotion.isStatic(Settings.Global.getFloat((ContentResolver)resolver, (String)"animator_duration_scale", (float)1.0f));
    }

    public static boolean isStatic(float animatorDurationScale) {
        return animatorDurationScale == 0.0f;
    }

    public static Animation caretBlink() {
        AlphaAnimation blink = new AlphaAnimation(1.0f, 0.15f);
        blink.setDuration(800L);
        blink.setRepeatMode(2);
        blink.setRepeatCount(-1);
        return blink;
    }
}

