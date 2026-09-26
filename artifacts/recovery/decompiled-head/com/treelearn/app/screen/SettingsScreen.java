/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  android.content.Context
 *  android.content.res.ColorStateList
 *  android.text.Editable
 *  android.view.LayoutInflater
 *  android.view.ViewGroup
 *  android.widget.Button
 *  android.widget.EditText
 *  android.widget.LinearLayout
 *  android.widget.TextView
 *  com.treelearn.app.R$color
 *  com.treelearn.app.R$id
 *  com.treelearn.app.R$layout
 *  com.treelearn.app.R$string
 */
package com.treelearn.app.screen;

import android.content.Context;
import android.content.res.ColorStateList;
import android.text.Editable;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.treelearn.app.R;
import com.treelearn.app.ui.KeySnapshot;
import com.treelearn.app.ui.SettingsActions;
import com.treelearn.app.ui.SettingsViewState;

public final class SettingsScreen
extends LinearLayout {
    private final EditText keyInput;
    private final TextView keyMask;
    private final TextView keyState;
    private final Button delete;
    private final TextView notice;
    private SettingsActions actions;
    private Runnable onBack;

    public SettingsScreen(Context context) {
        super(context);
        this.setOrientation(1);
        LayoutInflater.from((Context)context).inflate(R.layout.screen_settings, (ViewGroup)this, true);
        this.keyInput = (EditText)this.findViewById(R.id.settings_key_input);
        this.keyMask = (TextView)this.findViewById(R.id.settings_key_mask);
        this.keyState = (TextView)this.findViewById(R.id.settings_key_state);
        this.delete = (Button)this.findViewById(R.id.settings_key_delete);
        this.notice = (TextView)this.findViewById(R.id.settings_notice);
        this.findViewById(R.id.settings_back).setOnClickListener(view -> {
            if (this.onBack != null) {
                this.onBack.run();
            }
        });
        this.findViewById(R.id.settings_key_save).setOnClickListener(view -> this.save());
        this.delete.setOnClickListener(view -> {
            this.clearField();
            if (this.actions != null) {
                this.actions.onDeletePressed();
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
        this.keyMask.setText((CharSequence)state.keyMask);
        switch (state.keyLine) {
            case PRESENT: {
                this.keyState.setText(R.string.settings_key_state_ok);
                this.tint(R.color.tl_green_soft, R.color.tl_green_deep);
                break;
            }
            case UNREADABLE: {
                this.keyState.setText(R.string.settings_key_state_unreadable);
                this.tint(R.color.tl_amber_soft, R.color.tl_amber);
                break;
            }
            default: {
                this.keyState.setText(R.string.settings_key_state_none);
                this.tint(R.color.tl_amber_soft, R.color.tl_amber);
            }
        }
        this.delete.setVisibility(state.keyLine == KeySnapshot.Line.MISSING ? 8 : 0);
        if (state.notice == null) {
            this.notice.setVisibility(8);
            this.notice.setText((CharSequence)"");
        } else {
            this.notice.setText(state.notice == SettingsViewState.Notice.KEY_REQUIRED ? R.string.settings_notice_required : R.string.settings_notice_save_failed);
            this.notice.setVisibility(0);
        }
    }

    private void tint(int backgroundRes, int textRes) {
        this.keyState.setBackgroundTintList(ColorStateList.valueOf((int)this.getContext().getColor(backgroundRes)));
        this.keyState.setTextColor(this.getContext().getColor(textRes));
    }

    private void save() {
        Editable typed = this.keyInput.getText();
        int length = typed.length();
        char[] candidate = new char[length];
        if (length > 0) {
            typed.getChars(0, length, candidate, 0);
        }
        this.clearField();
        if (this.actions != null) {
            this.actions.onSavePressed(candidate);
        }
    }

    private void clearField() {
        this.keyInput.setText((CharSequence)"");
    }

    protected void onDetachedFromWindow() {
        this.clearField();
        super.onDetachedFromWindow();
    }

    protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        if (visibility != 0) {
            this.clearField();
        }
    }
}

