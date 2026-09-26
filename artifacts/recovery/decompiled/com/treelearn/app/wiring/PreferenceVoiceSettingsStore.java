/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.treelearn.data.settings.SettingsRepository
 */
package com.treelearn.app.wiring;

import com.treelearn.app.ui.VoiceSettingsPresenter;
import com.treelearn.data.settings.SettingsRepository;

public final class PreferenceVoiceSettingsStore
implements VoiceSettingsPresenter.Store {
    private final SettingsRepository preferences;

    public PreferenceVoiceSettingsStore(SettingsRepository preferences) {
        if (preferences == null) {
            throw new IllegalArgumentException("the voice settings store needs its preferences");
        }
        this.preferences = preferences;
    }

    @Override
    public String provider() {
        String stored = this.preferences.preference("tts_provider", "");
        return "system".equals(stored) ? stored : "system";
    }

    @Override
    public void setProvider(String providerId) {
        this.preferences.setPreference("tts_provider", providerId);
    }
}

