/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  android.content.Context
 *  android.content.res.ColorStateList
 *  android.graphics.drawable.ColorDrawable
 *  android.graphics.drawable.Drawable
 *  android.view.View
 *  android.view.ViewGroup$LayoutParams
 *  android.widget.ImageView
 *  android.widget.LinearLayout
 *  android.widget.LinearLayout$LayoutParams
 *  android.widget.PopupWindow
 *  android.widget.Switch
 *  android.widget.TextView
 *  com.treelearn.app.R$color
 *  com.treelearn.app.R$dimen
 *  com.treelearn.app.R$drawable
 *  com.treelearn.app.R$string
 */
package com.treelearn.app.screen;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.Switch;
import android.widget.TextView;
import com.treelearn.app.R;
import com.treelearn.app.ui.ModelSettingsPresenter;

public final class ModelSettingsScreen
extends LinearLayout
implements ModelSettingsPresenter.Sink {
    private static final String MARK = "\u2713";
    private final String[] roleTitles;
    private final String[] budgetLabels;
    private final ModelSettingsPresenter.Assignment[] roleAssignments = new ModelSettingsPresenter.Assignment[ModelSettingsPresenter.Role.values().length];
    private LinearLayout rows;
    private TextView notice;
    private ModelSettingsPresenter actions;
    private Runnable onBack;
    private ModelSettingsPresenter.State state;
    private PopupWindow popup;

    public ModelSettingsScreen(Context context) {
        super(context);
        this.setOrientation(1);
        this.roleTitles = new String[]{context.getString(R.string.models_role_chat), context.getString(R.string.models_role_fast), context.getString(R.string.models_role_translation)};
        this.budgetLabels = new String[]{context.getString(R.string.models_budget_off), context.getString(R.string.models_budget_auto), context.getString(R.string.models_budget_low), context.getString(R.string.models_budget_mid), context.getString(R.string.models_budget_high)};
        int gutter = this.dimen(R.dimen.page_gutter);
        this.setPadding(gutter, 0, gutter, this.dimen(R.dimen.space_s8));
        LinearLayout header = new LinearLayout(context);
        header.setOrientation(0);
        header.setGravity(16);
        header.setPadding(0, this.dimen(R.dimen.space_s4), 0, 0);
        ImageView back = new ImageView(context);
        back.setImageResource(R.drawable.ic_nav_back);
        back.setImageTintList(ColorStateList.valueOf((int)this.color(R.color.tl_green_deep)));
        back.setContentDescription((CharSequence)context.getString(R.string.cd_models_back));
        back.setLayoutParams((ViewGroup.LayoutParams)new LinearLayout.LayoutParams(this.dimen(R.dimen.touch_min), this.dimen(R.dimen.touch_min)));
        back.setOnClickListener(view -> {
            if (this.onBack != null) {
                this.onBack.run();
            }
        });
        header.addView((View)back);
        TextView title = this.tokenText(R.dimen.text_h1, R.color.tl_ink);
        title.setText(R.string.models_title);
        title.setTypeface(title.getTypeface(), 1);
        header.addView((View)title);
        this.addView((View)header);
        this.notice = this.tokenText(R.dimen.text_sm, R.color.tl_amber);
        this.notice.setVisibility(8);
        this.addView((View)this.notice);
        this.rows = new LinearLayout(context);
        this.rows.setOrientation(1);
        this.addView((View)this.rows);
    }

    public void setActions(ModelSettingsPresenter actions) {
        this.actions = actions;
    }

    public void setOnBackPressed(Runnable onBack) {
        this.onBack = onBack;
    }

    @Override
    public void render(ModelSettingsPresenter.State state) {
        this.state = state;
        this.dismiss();
        if (state.notice == ModelSettingsPresenter.Notice.NONE) {
            this.notice.setVisibility(8);
        } else {
            this.notice.setText(state.notice == ModelSettingsPresenter.Notice.NO_MODELS ? R.string.models_notice_none : R.string.models_notice_fallback);
            this.notice.setVisibility(0);
        }
        this.rows.removeAllViews();
        for (ModelSettingsPresenter.Role role : ModelSettingsPresenter.Role.values()) {
            this.roleAssignments[role.ordinal()] = role == ModelSettingsPresenter.Role.FAST ? state.fast : (role == ModelSettingsPresenter.Role.TRANSLATION ? state.translation : state.chat);
            this.rows.addView(this.roleRow(role));
        }
        this.rows.addView(this.budgetRow());
        this.rows.addView(this.suggestionsCard(state.suggestions));
    }

    private View roleRow(ModelSettingsPresenter.Role role) {
        ModelSettingsPresenter.Assignment assignment = this.roleAssignments[role.ordinal()];
        LinearLayout card = this.card();
        LinearLayout names = new LinearLayout(this.getContext());
        names.setOrientation(1);
        names.setLayoutParams((ViewGroup.LayoutParams)new LinearLayout.LayoutParams(0, -2, 1.0f));
        TextView name = this.tokenText(R.dimen.text_h2, R.color.tl_ink);
        name.setText((CharSequence)this.roleTitles[role.ordinal()]);
        names.addView((View)name);
        TextView value = this.tokenText(R.dimen.text_cap, assignment.fallback == ModelSettingsPresenter.Fallback.EMPTY ? R.color.tl_amber : R.color.tl_ink2);
        value.setText((CharSequence)(assignment.label.isEmpty() ? this.getContext().getString(R.string.models_unselected) : assignment.label));
        names.addView((View)value);
        card.addView((View)names);
        card.addView((View)this.chevron());
        card.setOnClickListener(view -> this.rolePopup(role, assignment, (View)card));
        return card;
    }

    private View budgetRow() {
        LinearLayout card = this.card();
        TextView label = this.tokenText(R.dimen.text_h2, R.color.tl_ink);
        label.setText(R.string.models_budget_label);
        label.setLayoutParams((ViewGroup.LayoutParams)new LinearLayout.LayoutParams(0, -2, 1.0f));
        card.addView((View)label);
        TextView current = this.tokenText(R.dimen.text_cap, R.color.tl_ink2);
        current.setText((CharSequence)this.budgetLabels[this.state.budget.ordinal()]);
        card.addView((View)current);
        card.addView((View)this.chevron());
        card.setOnClickListener(view -> this.budgetPopup((View)card));
        return card;
    }

    private View suggestionsCard(boolean enabled) {
        LinearLayout column = new LinearLayout(this.getContext());
        column.setOrientation(1);
        LinearLayout card = this.card();
        TextView name = this.tokenText(R.dimen.text_h2, R.color.tl_ink);
        name.setText(R.string.models_suggestions);
        name.setLayoutParams((ViewGroup.LayoutParams)new LinearLayout.LayoutParams(0, -2, 1.0f));
        card.addView((View)name);
        Switch toggle = new Switch(this.getContext());
        toggle.setChecked(enabled);
        toggle.setContentDescription((CharSequence)this.getContext().getString(R.string.cd_models_switch));
        toggle.setThumbTintList(ColorStateList.valueOf((int)this.color(enabled ? R.color.tl_green : R.color.tl_ink3)));
        toggle.setTrackTintList(ColorStateList.valueOf((int)this.color(enabled ? R.color.tl_green_soft : R.color.tl_line)));
        toggle.setOnClickListener(view -> {
            if (this.actions != null) {
                this.actions.onSuggestionsToggled(toggle.isChecked());
            }
        });
        card.addView((View)toggle);
        column.addView((View)card);
        TextView sub = this.tokenText(R.dimen.text_cap, R.color.tl_ink3);
        sub.setText(R.string.models_suggestions_sub);
        column.addView((View)sub);
        return column;
    }

    private void rolePopup(ModelSettingsPresenter.Role role, ModelSettingsPresenter.Assignment assignment, View anchor) {
        LinearLayout content = this.popupColumn();
        String lastProvider = null;
        for (ModelSettingsPresenter.Option option : this.state.options) {
            if (!option.providerName.equals(lastProvider)) {
                lastProvider = option.providerName;
                TextView header = this.tokenText(R.dimen.text_cap, R.color.tl_ink3);
                header.setText((CharSequence)option.providerName);
                header.setPadding(this.dimen(R.dimen.space_s3), this.dimen(R.dimen.space_s2), this.dimen(R.dimen.space_s3), this.dimen(R.dimen.space_s1));
                content.addView((View)header);
            }
            content.addView(this.popupRow(option.modelId, option.providerId.equals(assignment.providerId) && option.modelId.equals(assignment.modelId), () -> {
                if (this.actions != null) {
                    this.actions.onModelSelected(role, option);
                }
            }));
        }
        this.showAnchored(anchor, (View)content);
    }

    private void budgetPopup(View anchor) {
        LinearLayout content = this.popupColumn();
        for (ModelSettingsPresenter.Budget budget : ModelSettingsPresenter.Budget.values()) {
            content.addView(this.popupRow(this.budgetLabels[budget.ordinal()], budget == this.state.budget, () -> {
                if (this.actions != null) {
                    this.actions.onBudgetSelected(budget);
                }
            }));
        }
        this.showAnchored(anchor, (View)content);
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this.getContext());
        card.setOrientation(0);
        card.setGravity(16);
        card.setMinimumHeight(this.dimen(R.dimen.touch_min));
        card.setBackgroundResource(R.drawable.bg_card);
        card.setElevation((float)this.dimen(R.dimen.card_elevation));
        int gutter = this.dimen(R.dimen.space_s4);
        card.setPadding(gutter, gutter, gutter, gutter);
        LinearLayout.LayoutParams strip = new LinearLayout.LayoutParams(-1, -2);
        strip.bottomMargin = this.dimen(R.dimen.space_s2);
        card.setLayoutParams((ViewGroup.LayoutParams)strip);
        return card;
    }

    private LinearLayout popupColumn() {
        LinearLayout content = new LinearLayout(this.getContext());
        content.setOrientation(1);
        content.setBackgroundResource(R.drawable.bg_card);
        content.setElevation((float)this.dimen(R.dimen.card_elevation));
        int pad = this.dimen(R.dimen.space_s2);
        content.setPadding(pad, pad, pad, pad);
        return content;
    }

    private View popupRow(String label, boolean current, Runnable pick) {
        LinearLayout row = new LinearLayout(this.getContext());
        row.setOrientation(0);
        row.setGravity(16);
        row.setMinimumHeight(this.dimen(R.dimen.touch_min));
        row.setPadding(this.dimen(R.dimen.space_s3), 0, this.dimen(R.dimen.space_s3), 0);
        TextView text = this.tokenText(R.dimen.text_body, R.color.tl_ink);
        text.setText((CharSequence)label);
        text.setLayoutParams((ViewGroup.LayoutParams)new LinearLayout.LayoutParams(0, -2, 1.0f));
        row.addView((View)text);
        TextView mark = this.tokenText(R.dimen.text_body, R.color.tl_green_deep);
        mark.setText((CharSequence)(current ? MARK : ""));
        row.addView((View)mark);
        row.setOnClickListener(view -> {
            pick.run();
            this.dismiss();
        });
        return row;
    }

    private TextView chevron() {
        TextView chevron = this.tokenText(R.dimen.text_h2, R.color.tl_ink3);
        chevron.setText((CharSequence)"\u203a");
        return chevron;
    }

    private TextView tokenText(int sizeRes, int colorRes) {
        TextView text = new TextView(this.getContext());
        text.setTextSize(0, (float)this.dimen(sizeRes));
        text.setTextColor(this.color(colorRes));
        return text;
    }

    private void showAnchored(View anchor, View content) {
        content.measure(0, 0);
        int height = content.getMeasuredHeight();
        int[] location = new int[2];
        anchor.getLocationOnScreen(location);
        int spaceBelow = this.getResources().getDisplayMetrics().heightPixels - (location[1] + anchor.getHeight());
        this.popup = new PopupWindow(content, -1, -2, true);
        this.popup.setBackgroundDrawable((Drawable)new ColorDrawable(0));
        this.popup.setOutsideTouchable(true);
        this.popup.setElevation((float)this.dimen(R.dimen.card_elevation));
        if (height > spaceBelow) {
            this.popup.showAsDropDown(anchor, 0, -(anchor.getHeight() + height));
        } else {
            this.popup.showAsDropDown(anchor);
        }
    }

    private void dismiss() {
        if (this.popup != null) {
            this.popup.dismiss();
        }
    }

    protected void onDetachedFromWindow() {
        this.dismiss();
        super.onDetachedFromWindow();
    }

    private int dimen(int res) {
        return this.getResources().getDimensionPixelSize(res);
    }

    private int color(int res) {
        return this.getContext().getColor(res);
    }
}

