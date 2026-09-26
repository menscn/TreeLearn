/*
 * Decompiled with CFR 0.152.
 */
package com.treelearn.app.ui;

import com.treelearn.app.ui.KeySnapshot;

public final class SettingsViewState {
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
        return new SettingsViewState(key.line, key.mask, this.notice);
    }

    public SettingsViewState withNotice(Notice value) {
        return new SettingsViewState(this.keyLine, this.keyMask, value);
    }

    public static enum Notice {
        KEY_REQUIRED,
        KEY_SAVE_FAILED;

    }
}

