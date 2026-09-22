package com.treelearn.app.screen;

import android.content.Context;
import android.content.res.ColorStateList;
import android.text.Editable;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.treelearn.app.R;
import com.treelearn.app.ui.KeySnapshot;
import com.treelearn.app.ui.SettingsActions;
import com.treelearn.app.ui.SettingsViewState;

/**
 * The settings screen: the credential's state, replace and delete, plus the data-boundary note.
 *
 * <p>Two rules shape this class, both from PRD 6.2.13 and the technical design's secret handling:
 *
 * <ul>
 *   <li>The key never becomes a {@code String}. The field's characters are copied straight into the
 *       buffer the presenter owns and wipes, and the field is emptied at the same moment, so nothing
 *       immutable holds the secret and nothing keeps it in memory after the screen is done with it.
 *   <li>The stored key is only ever shown as the mask the presenter produced. There is no path in
 *       this class that can put the full value on screen, in a log or in a saved instance state.
 * </ul>
 */
public final class SettingsScreen extends LinearLayout {

    private final EditText keyInput;
    private final TextView keyMask;
    private final TextView keyState;
    private final Button delete;
    private final TextView notice;

    private SettingsActions actions;
    private Runnable onBack;

    public SettingsScreen(Context context) {
        super(context);
        setOrientation(VERTICAL);
        LayoutInflater.from(context).inflate(R.layout.screen_settings, this, true);

        keyInput = findViewById(R.id.settings_key_input);
        keyMask = findViewById(R.id.settings_key_mask);
        keyState = findViewById(R.id.settings_key_state);
        delete = findViewById(R.id.settings_key_delete);
        notice = findViewById(R.id.settings_notice);

        findViewById(R.id.settings_back).setOnClickListener(view -> {
            if (onBack != null) {
                onBack.run();
            }
        });
        findViewById(R.id.settings_key_save).setOnClickListener(view -> save());
        delete.setOnClickListener(view -> {
            clearField();
            if (actions != null) {
                actions.onDeletePressed();
            }
        });
    }

    public void setActions(SettingsActions actions) {
        this.actions = actions;
    }

    public void setOnBackPressed(Runnable onBack) {
        this.onBack = onBack;
    }

    public void render(SettingsViewState state) {
        keyMask.setText(state.keyMask);
        switch (state.keyLine) {
            case PRESENT:
                keyState.setText(R.string.settings_key_state_ok);
                tint(R.color.tl_green_soft, R.color.tl_green_deep);
                break;
            case UNREADABLE:
                keyState.setText(R.string.settings_key_state_unreadable);
                tint(R.color.tl_amber_soft, R.color.tl_amber);
                break;
            case MISSING:
            default:
                keyState.setText(R.string.settings_key_state_none);
                tint(R.color.tl_amber_soft, R.color.tl_amber);
                break;
        }
        delete.setVisibility(state.keyLine == KeySnapshot.Line.MISSING ? GONE : VISIBLE);
        if (state.notice == null) {
            notice.setVisibility(GONE);
            notice.setText("");
        } else {
            notice.setText(state.notice == SettingsViewState.Notice.KEY_REQUIRED
                    ? R.string.settings_notice_required
                    : R.string.settings_notice_save_failed);
            notice.setVisibility(VISIBLE);
        }
    }

    private void tint(int backgroundRes, int textRes) {
        keyState.setBackgroundTintList(ColorStateList.valueOf(getContext().getColor(backgroundRes)));
        keyState.setTextColor(getContext().getColor(textRes));
    }

    /** Hands the field's characters to the presenter as a wipeable buffer, then empties the field. */
    private void save() {
        Editable typed = keyInput.getText();
        int length = typed.length();
        char[] candidate = new char[length];
        if (length > 0) {
            typed.getChars(0, length, candidate, 0);
        }
        clearField();
        if (actions != null) {
            actions.onSavePressed(candidate);
        }
    }

    private void clearField() {
        keyInput.setText("");
    }

    @Override
    protected void onDetachedFromWindow() {
        clearField();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        if (visibility != View.VISIBLE) {
            clearField();
        }
    }
}