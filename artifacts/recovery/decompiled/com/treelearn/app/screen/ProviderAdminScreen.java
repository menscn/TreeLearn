/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  android.app.AlertDialog$Builder
 *  android.content.Context
 *  android.content.res.ColorStateList
 *  android.text.Editable
 *  android.view.LayoutInflater
 *  android.view.View
 *  android.view.ViewGroup
 *  android.view.ViewGroup$LayoutParams
 *  android.widget.Button
 *  android.widget.EditText
 *  android.widget.LinearLayout
 *  android.widget.LinearLayout$LayoutParams
 *  android.widget.Switch
 *  android.widget.TextView
 *  com.treelearn.app.R$color
 *  com.treelearn.app.R$dimen
 *  com.treelearn.app.R$drawable
 *  com.treelearn.app.R$id
 *  com.treelearn.app.R$layout
 *  com.treelearn.app.R$string
 */
package com.treelearn.app.screen;

import android.app.AlertDialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.text.Editable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import com.treelearn.app.R;
import com.treelearn.app.ui.KeySnapshot;
import com.treelearn.app.ui.ProviderAdminPresenter;

public final class ProviderAdminScreen
extends LinearLayout
implements ProviderAdminPresenter.Sink {
    private View emptyArea;
    private View editorArea;
    private LinearLayout rows;
    private TextView fetchNotice;
    private TextView notice;
    private EditText nameInput;
    private EditText baseInput;
    private EditText keyInput;
    private TextView keyMask;
    private Button fetchButton;
    private Button deleteButton;
    private String editorId;
    private ProviderAdminPresenter actions;
    private Runnable onBack;

    public ProviderAdminScreen(Context context) {
        super(context);
        this.setOrientation(1);
        LayoutInflater.from((Context)context).inflate(R.layout.screen_provider_admin, (ViewGroup)this, true);
        this.rows = (LinearLayout)this.findViewById(R.id.providers_list);
        this.emptyArea = this.findViewById(R.id.providers_empty);
        this.editorArea = this.findViewById(R.id.provider_editor);
        this.nameInput = (EditText)this.findViewById(R.id.provider_name_input);
        this.baseInput = (EditText)this.findViewById(R.id.provider_base_input);
        this.keyInput = (EditText)this.findViewById(R.id.provider_key_input);
        this.keyMask = (TextView)this.findViewById(R.id.provider_key_mask);
        this.fetchNotice = (TextView)this.findViewById(R.id.provider_fetch_notice);
        this.notice = (TextView)this.findViewById(R.id.provider_notice);
        this.fetchButton = (Button)this.findViewById(R.id.provider_fetch);
        this.deleteButton = (Button)this.findViewById(R.id.provider_delete);
        this.findViewById(R.id.providers_back).setOnClickListener(view -> {
            if (this.onBack != null) {
                this.onBack.run();
            }
        });
        this.findViewById(R.id.providers_new).setOnClickListener(view -> {
            if (this.actions != null) {
                this.actions.onNewPressed();
            }
        });
        this.findViewById(R.id.provider_save).setOnClickListener(view -> this.save());
        this.findViewById(R.id.provider_key_save).setOnClickListener(view -> this.saveKey());
        this.findViewById(R.id.provider_key_delete).setOnClickListener(view -> {
            this.clearField();
            if (this.actions != null) {
                this.actions.onKeyDeletePressed();
            }
        });
        this.fetchButton.setOnClickListener(view -> {
            if (this.actions != null) {
                this.actions.onFetchPressed();
            }
        });
        this.deleteButton.setOnClickListener(view -> {
            if (this.actions != null) {
                this.actions.onDeletePressed(this.currentProviderId());
            }
        });
    }

    public void setActions(ProviderAdminPresenter actions) {
        this.actions = actions;
    }

    public void setOnBackPressed(Runnable onBack) {
        this.onBack = onBack;
    }

    @Override
    public void render(ProviderAdminPresenter.State state) {
        boolean editor = state.mode == ProviderAdminPresenter.State.Mode.EDITOR;
        this.editorArea.setVisibility(editor ? 0 : 8);
        this.rows.setVisibility(editor ? 8 : 0);
        if (editor) {
            this.renderEditor(state.editor);
        } else {
            this.renderList(state);
        }
    }

    private void renderList(ProviderAdminPresenter.State state) {
        this.rows.removeAllViews();
        this.emptyArea.setVisibility(state.rows.isEmpty() ? 0 : 8);
        for (ProviderAdminPresenter.Row row : state.rows) {
            this.rows.addView(this.rowView(row));
        }
        if (state.deletePending) {
            this.confirmDelete(state.deleteWarning);
        }
    }

    private View rowView(ProviderAdminPresenter.Row row) {
        LinearLayout card = new LinearLayout(this.getContext());
        card.setOrientation(0);
        card.setGravity(16);
        card.setMinimumHeight(this.getResources().getDimensionPixelSize(R.dimen.touch_min));
        card.setBackgroundResource(R.drawable.bg_card);
        card.setElevation((float)this.getResources().getDimensionPixelSize(R.dimen.card_elevation));
        int gutter = this.getResources().getDimensionPixelSize(R.dimen.space_s4);
        card.setPadding(gutter, gutter, gutter, gutter);
        LinearLayout.LayoutParams strip = new LinearLayout.LayoutParams(-1, -2);
        strip.bottomMargin = this.getResources().getDimensionPixelSize(R.dimen.space_s2);
        card.setLayoutParams((ViewGroup.LayoutParams)strip);
        LinearLayout names = new LinearLayout(this.getContext());
        names.setOrientation(1);
        names.setLayoutParams((ViewGroup.LayoutParams)new LinearLayout.LayoutParams(0, -2, 1.0f));
        TextView name = new TextView(this.getContext());
        name.setText((CharSequence)row.name);
        name.setTextSize(0, (float)this.getResources().getDimensionPixelSize(R.dimen.text_h2));
        name.setTextColor(this.color(row.enabled ? R.color.tl_ink : R.color.tl_ink3));
        names.addView((View)name);
        TextView base = new TextView(this.getContext());
        base.setText((CharSequence)(row.hasKey ? row.baseUrl : this.getContext().getString(R.string.providers_row_nokey) + " \u00b7 " + row.baseUrl));
        base.setTextSize(0, (float)this.getResources().getDimensionPixelSize(R.dimen.text_cap));
        base.setTextColor(this.color(R.color.tl_ink3));
        names.addView((View)base);
        card.addView((View)names);
        TextView pill = new TextView(this.getContext());
        pill.setText((CharSequence)this.getContext().getString(R.string.providers_row_models, new Object[]{row.modelCount}));
        pill.setBackgroundResource(R.drawable.bg_pill);
        int pad = this.getResources().getDimensionPixelSize(R.dimen.space_s2);
        pill.setPadding(pad, pad / 2, pad, pad / 2);
        pill.setTextSize(0, (float)this.getResources().getDimensionPixelSize(R.dimen.text_cap));
        pill.setBackgroundTintList(ColorStateList.valueOf((int)this.color(row.enabled ? R.color.tl_green_soft : R.color.tl_line)));
        pill.setTextColor(this.color(row.enabled ? R.color.tl_green_deep : R.color.tl_ink3));
        card.addView((View)pill);
        Switch enable = new Switch(this.getContext());
        enable.setChecked(row.enabled);
        enable.setContentDescription((CharSequence)this.getContext().getString(R.string.cd_providers_switch));
        enable.setThumbTintList(ColorStateList.valueOf((int)this.color(row.enabled ? R.color.tl_green : R.color.tl_ink3)));
        enable.setTrackTintList(ColorStateList.valueOf((int)this.color(row.enabled ? R.color.tl_green_soft : R.color.tl_line)));
        enable.setOnClickListener(view -> {
            if (this.actions != null) {
                this.actions.onToggle(row.id, enable.isChecked());
            }
        });
        card.addView((View)enable);
        card.setOnClickListener(view -> {
            if (this.actions != null) {
                this.actions.onRowPressed(row.id);
            }
        });
        return card;
    }

    private void renderEditor(ProviderAdminPresenter.Editor editor) {
        this.editorId = editor.providerId;
        this.nameInput.setText((CharSequence)editor.name);
        this.baseInput.setText((CharSequence)editor.baseUrl);
        this.keyMask.setText((CharSequence)(editor.key.line == KeySnapshot.Line.MISSING ? this.getContext().getString(R.string.editor_key_none) : editor.key.mask));
        this.fetchButton.setEnabled(!editor.fetchBusy);
        this.fetchButton.setText(editor.fetchBusy ? R.string.editor_fetching : R.string.editor_fetch);
        if (editor.fetchFailed) {
            this.fetchNotice.setText(R.string.editor_fetch_failed);
            this.fetchNotice.setTextColor(this.color(R.color.tl_amber));
            this.fetchNotice.setVisibility(0);
        } else if (editor.fetchAdded != null) {
            this.fetchNotice.setText((CharSequence)this.getContext().getString(R.string.editor_fetch_added, new Object[]{editor.fetchAdded}));
            this.fetchNotice.setTextColor(this.color(R.color.tl_green_deep));
            this.fetchNotice.setVisibility(0);
        } else {
            this.fetchNotice.setVisibility(8);
        }
        if (editor.notice == null) {
            this.notice.setVisibility(8);
        } else {
            this.notice.setText(this.noticeText(editor.notice));
            this.notice.setVisibility(0);
        }
    }

    private int noticeText(ProviderAdminPresenter.Notice value) {
        switch (value) {
            case NAME_TAKEN: {
                return R.string.editor_notice_name_taken;
            }
            case BASE_REQUIRED: {
                return R.string.editor_notice_base_required;
            }
            case KEY_REQUIRED: {
                return R.string.editor_notice_key_required;
            }
            case KEY_SAVE_FAILED: {
                return R.string.editor_notice_key_save_failed;
            }
        }
        return R.string.editor_notice_name_required;
    }

    private void confirmDelete(boolean lastEnabled) {
        if (this.actions == null) {
            return;
        }
        String name = this.currentName();
        new AlertDialog.Builder(this.getContext()).setTitle(R.string.providers_delete_title).setMessage((CharSequence)(this.getContext().getString(R.string.providers_delete_body, new Object[]{name}) + (lastEnabled ? "\n\n" + this.getContext().getString(R.string.providers_delete_last) : ""))).setPositiveButton(R.string.providers_delete_ok, (dialog, which) -> this.actions.onDeleteConfirmed()).setNegativeButton(R.string.navigation_cancel, (dialog, which) -> this.actions.onDeleteCanceled()).show();
    }

    private String currentProviderId() {
        return this.editorId;
    }

    private String currentName() {
        return this.nameInput.getText() == null ? "" : this.nameInput.getText().toString();
    }

    private void save() {
        if (this.actions != null) {
            this.actions.onSavePressed(this.currentName(), this.baseInput.getText() == null ? "" : this.baseInput.getText().toString());
        }
    }

    private void saveKey() {
        Editable typed = this.keyInput.getText();
        int length = typed == null ? 0 : typed.length();
        char[] candidate = new char[length];
        if (length > 0) {
            typed.getChars(0, length, candidate, 0);
        }
        this.clearField();
        if (this.actions != null) {
            this.actions.onKeySavePressed(candidate);
        }
    }

    private void clearField() {
        this.keyInput.setText((CharSequence)"");
    }

    private int color(int res) {
        return this.getContext().getColor(res);
    }
}

