package com.treelearn.app.screen;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.treelearn.app.R;
import com.treelearn.app.ui.KeySnapshot;
import com.treelearn.app.ui.SettingsViewState;

/**
 * The settings tab's root page: a grouped navigation list (settings redesign 2026-09-28, scheme C).
 * Every form lives one level deeper, the credential's room included, so this class only renders the
 * entries and their subtitles. The key entry's subtitle carries the credential's live state, the
 * same health read PRD 6.2.14's guard leans on, so the learner can see a missing key without
 * entering the room.
 */
public final class SettingsScreen extends LinearLayout {

    private final TextView keySubtitle;

    private Runnable onBack;
    private Runnable onProvidersPressed;

    public SettingsScreen(Context context) {
        super(context);
        setOrientation(VERTICAL);
        LayoutInflater.from(context).inflate(R.layout.screen_settings, this, true);

        keySubtitle = findViewById(R.id.settings_key_sub);

        findViewById(R.id.settings_back).setOnClickListener(view -> {
            if (onBack != null) {
                onBack.run();
            }
        });
    }

    public void setOnBackPressed(Runnable onBack) {
        this.onBack = onBack;
    }

    /** Opens the API KEY room; its subtitle is fed by {@link #render}. */
    public void setOnKeyPressed(Runnable onKeyPressed) {
        findViewById(R.id.settings_key_entry).setOnClickListener(
                view -> {
                    if (onKeyPressed != null) {
                        onKeyPressed.run();
                    }
                });
    }

    /** Wires the provider room entry; {@code onProvidersPressed} is kept for its subtitle. */
    public void setOnProvidersPressed(Runnable onProvidersPressed) {
        this.onProvidersPressed = onProvidersPressed;
        findViewById(R.id.settings_providers_entry).setOnClickListener(
                view -> {
                    if (onProvidersPressed != null) {
                        onProvidersPressed.run();
                    }
                });
    }

    public void setOnModelsPressed(Runnable onModelsPressed) {
        findViewById(R.id.settings_models_entry).setOnClickListener(
                view -> {
                    if (onModelsPressed != null) {
                        onModelsPressed.run();
                    }
                });
    }

    public void setOnSearchPressed(Runnable onSearchPressed) {
        findViewById(R.id.settings_search_entry).setOnClickListener(
                view -> {
                    if (onSearchPressed != null) {
                        onSearchPressed.run();
                    }
                });
    }

    public void setOnVoicePressed(Runnable onVoicePressed) {
        findViewById(R.id.settings_voice_entry).setOnClickListener(
                view -> {
                    if (onVoicePressed != null) {
                        onVoicePressed.run();
                    }
                });
    }

    public void setOnFilesPressed(Runnable onFilesPressed) {
        findViewById(R.id.settings_files_entry).setOnClickListener(
                view -> {
                    if (onFilesPressed != null) {
                        onFilesPressed.run();
                    }
                });
    }

    public void setModelsSubtitle(String text) {
        ((TextView) findViewById(R.id.settings_models_sub)).setText(text);
    }

    public void setProvidersSubtitle(String text) {
        ((TextView) findViewById(R.id.settings_providers_sub)).setText(text);
    }

    /**
     * Draws the credential's state into the key entry's subtitle: the mask plus the verdict while
     * the credential reads back, the full verdict sentence otherwise. Nothing here can see the
     * full key, only the mask the presenter produced.
     */
    public void render(SettingsViewState state) {
        if (state.keyLine == KeySnapshot.Line.PRESENT) {
            keySubtitle.setText(getContext().getString(R.string.settings_key_entry_ok, state.keyMask));
        } else if (state.keyLine == KeySnapshot.Line.UNREADABLE) {
            keySubtitle.setText(R.string.settings_key_state_unreadable);
        } else {
            keySubtitle.setText(R.string.settings_key_state_none);
        }
    }
}
