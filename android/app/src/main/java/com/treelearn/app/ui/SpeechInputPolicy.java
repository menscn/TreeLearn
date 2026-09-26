package com.treelearn.app.ui;

/**
 * T-095: the speech key's decisions, kept out of the View (SPEC-081 语音识别输入节).
 *
 * <p>Three facts meet here once: whether the device has a recognition service at all, whether the
 * learner has granted the microphone, and whether the one-time guidance after a denial has been
 * spent. The key's whole lifecycle is:</p>
 *
 * <ul>
 *   <li>no recognition service → the key is not rendered (no placeholder, no dead button);</li>
 *   <li>permission denied → the key greys out, and the first tap on it gives the
 *       open-in-settings guidance exactly once — no repeated popups, no fake entry;</li>
 *   <li>a later grant re-enables the key without restarting anything.</li>
 * </ul>
 */
public final class SpeechInputPolicy {

    private boolean denied;
    private boolean guidanceSpent;

    /** Whether the key should be drawn at all: no recognition service, no key (SPEC-081). */
    public static boolean keyVisible(boolean recognitionAvailable) {
        return recognitionAvailable;
    }

    /** Whether the key accepts a long-press right now (granted, or not yet denied). */
    public boolean enabled() {
        return !denied;
    }

    /** Records a permission denial; the key greys out from the next render on. */
    public void markPermissionDenied() {
        denied = true;
    }

    /** Records a permission grant; a previously denied key becomes usable again. */
    public void markPermissionGranted() {
        denied = false;
    }

    /**
     * The tap on a greyed key: true exactly once — the first tap asks for the guidance, later
     * taps stay quiet (SPEC-081: 不再重复弹窗).
     */
    public boolean guidanceOnTap() {
        if (guidanceSpent) {
            return false;
        }
        guidanceSpent = true;
        return true;
    }
}
