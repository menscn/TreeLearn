package com.treelearn.data.settings;

import android.content.Context;
import android.content.SharedPreferences;

import com.treelearn.core.domain.GenerationModel;

/**
 * The non-sensitive local preferences: the model tier and the thinking switch (PRD F18).
 *
 * <p>Kept in its own Preferences file, apart from the secret one, so that the API key never shares a
 * file that a future backup rule could forget to exclude. Neither value here is a credential.
 *
 * <p>Defaults are an implementation decision - neither the PRD nor the technical design fixes them:
 * the fast tier with thinking off, so a first question answers quickly. Both switches stay visible
 * in the input bar, and either change only affects answers generated afterwards (PRD F18).
 *
 * <p>The expanded state of 思考过程 is deliberately NOT stored: it is per-screen display state, and
 * silently restoring an expanded panel would surprise the reader rather than help them.
 *
 * <p>Provisional consumer: the settings screen (T-012) is not implemented yet, so nothing reads
 * these values at runtime today. They are persisted state, not a rule, and this gap is recorded in
 * the T-005 handoff notes.
 */
public final class SettingsRepository {

    public static final String PREFERENCES_NAME = "treelearn_settings";

    public static final String PREFERENCE_KEY_MODEL = "generation_model";

    public static final String PREFERENCE_KEY_THINKING = "thinking_enabled";

    private static final GenerationModel DEFAULT_MODEL = GenerationModel.FLASH;

    private static final boolean DEFAULT_THINKING_ENABLED = false;

    private final SharedPreferences preferences;

    public SettingsRepository(Context context) {
        this(context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE));
    }

    SettingsRepository(SharedPreferences preferences) {
        this.preferences = preferences;
    }

    /** Falls back to the default when a stored wire id is unknown, e.g. after a provider rename. */
    public GenerationModel model() {
        String wireId = preferences.getString(PREFERENCE_KEY_MODEL, DEFAULT_MODEL.wireId);
        try {
            return GenerationModel.fromWireId(wireId);
        } catch (IllegalArgumentException renamed) {
            return DEFAULT_MODEL;
        }
    }

    public void setModel(GenerationModel model) {
        if (model == null) {
            throw new IllegalArgumentException("a model tier must not be null");
        }
        preferences.edit().putString(PREFERENCE_KEY_MODEL, model.wireId).apply();
    }

    public boolean thinkingEnabled() {
        return preferences.getBoolean(PREFERENCE_KEY_THINKING, DEFAULT_THINKING_ENABLED);
    }

    public void setThinkingEnabled(boolean enabled) {
        preferences.edit().putBoolean(PREFERENCE_KEY_THINKING, enabled).apply();
    }
}
