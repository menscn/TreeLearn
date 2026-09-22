package com.treelearn.app.ui;

/**
 * Everything the settings screen draws.
 *
 * <p>Model and thinking are deliberately absent: V3.1 6.2.18 point 2 puts that switch inside the
 * question input, where it is visible next to the question it applies to, and says the settings page
 * does not offer it. This screen owns the credential and the data-boundary note.
 */
public final class SettingsViewState {

    /** A failure on this screen. */
    public enum Notice {
        /** 保存 was pressed with an empty field. */
        KEY_REQUIRED,
        /** The platform keystore refused the write; the learner can retry. */
        KEY_SAVE_FAILED
    }

    public final KeySnapshot.Line keyLine;
    public final String keyMask;
    public final Notice notice;

    private SettingsViewState(KeySnapshot.Line keyLine, String keyMask, Notice notice) {
        this.keyLine = keyLine;
        this.keyMask = keyMask;
        this.notice = notice;
    }

    public static SettingsViewState of(KeySnapshot key) {
        return new SettingsViewState(key.line, key.mask, null);
    }

    public SettingsViewState withKey(KeySnapshot key) {
        return new SettingsViewState(key.line, key.mask, notice);
    }

    public SettingsViewState withNotice(Notice value) {
        return new SettingsViewState(keyLine, keyMask, value);
    }
}