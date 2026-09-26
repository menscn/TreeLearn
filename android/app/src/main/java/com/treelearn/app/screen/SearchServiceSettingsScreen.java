package com.treelearn.app.screen;

import android.content.Context;
import android.graphics.Typeface;
import android.content.res.ColorStateList;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.text.Editable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.Switch;
import android.widget.TextView;
import com.treelearn.app.R;
import com.treelearn.app.ui.KeySnapshot;
import com.treelearn.app.ui.SearchSettingsPresenter;

public final class SearchServiceSettingsScreen
extends LinearLayout
implements SearchSettingsPresenter.Sink {
    private static final String MARK = "\u2713";
    private final String[] engineNames;
    private final String[] tierLabels;
    private LinearLayout rows;
    private TextView notice;
    private EditText keyInput;
    private SearchSettingsPresenter actions;
    private Runnable onBack;
    private SearchSettingsPresenter.State state;
    private PopupWindow popup;

    public SearchServiceSettingsScreen(Context context) {
        super(context);
        this.setOrientation(LinearLayout.VERTICAL);
        this.engineNames = new String[]{context.getString(R.string.search_engine_zhipu), context.getString(R.string.search_engine_tavily), context.getString(R.string.search_engine_bocha)};
        this.tierLabels = new String[]{context.getString(R.string.search_tier_three), context.getString(R.string.search_tier_five), context.getString(R.string.search_tier_ten)};
        int gutter = this.dimen(R.dimen.page_gutter);
        this.setPadding(gutter, 0, gutter, this.dimen(R.dimen.space_s8));
        LinearLayout header = new LinearLayout(context);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(16);
        header.setPadding(0, this.dimen(R.dimen.space_s4), 0, 0);
        ImageView back = new ImageView(context);
        back.setImageResource(R.drawable.ic_nav_back);
        back.setImageTintList(ColorStateList.valueOf((int)this.color(R.color.tl_green_deep)));
        back.setContentDescription((CharSequence)context.getString(R.string.cd_search_back));
        back.setLayoutParams((ViewGroup.LayoutParams)new LinearLayout.LayoutParams(this.dimen(R.dimen.touch_min), this.dimen(R.dimen.touch_min)));
        back.setOnClickListener(view -> {
            if (this.onBack != null) {
                this.onBack.run();
            }
        });
        header.addView((View)back);
        TextView title = this.tokenText(R.dimen.text_h1, R.color.tl_ink);
        title.setText(R.string.search_title);
        title.setTypeface(title.getTypeface(), Typeface.BOLD);
        header.addView((View)title);
        this.addView((View)header);
        this.notice = this.tokenText(R.dimen.text_sm, R.color.tl_amber);
        this.notice.setVisibility(View.GONE);
        this.addView((View)this.notice);
        this.rows = new LinearLayout(context);
        this.rows.setOrientation(LinearLayout.VERTICAL);
        this.addView((View)this.rows);
    }

    public void setActions(SearchSettingsPresenter actions) {
        this.actions = actions;
    }

    public void setOnBackPressed(Runnable onBack) {
        this.onBack = onBack;
    }

    @Override
    public void render(SearchSettingsPresenter.State state) {
        this.state = state;
        this.dismiss();
        this.clearField();
        if (state.notice == SearchSettingsPresenter.Notice.NONE) {
            this.notice.setVisibility(View.GONE);
        } else {
            this.notice.setText(state.notice == SearchSettingsPresenter.Notice.NOKEY ? R.string.search_notice_nokey : (state.notice == SearchSettingsPresenter.Notice.KEY_REQUIRED ? R.string.search_notice_key_required : R.string.search_notice_key_save_failed));
            this.notice.setVisibility(View.VISIBLE);
        }
        this.rows.removeAllViews();
        this.rows.addView(this.masterCard(state.searchEnabled));
        for (SearchSettingsPresenter.Row row : state.rows) {
            this.rows.addView(this.engineCard(row));
            if (row.engine != state.engine) continue;
            this.rows.addView(this.keyPanel(row, state.key));
        }
        this.rows.addView(this.tierCard());
    }

    private View masterCard(boolean enabled) {
        LinearLayout card = this.card();
        LinearLayout names = new LinearLayout(this.getContext());
        names.setOrientation(LinearLayout.VERTICAL);
        names.setLayoutParams((ViewGroup.LayoutParams)new LinearLayout.LayoutParams(0, -2, 1.0f));
        TextView name = this.tokenText(R.dimen.text_h2, R.color.tl_ink);
        name.setText(R.string.search_master);
        names.addView((View)name);
        TextView sub = this.tokenText(R.dimen.text_cap, R.color.tl_ink3);
        sub.setText(R.string.search_master_sub);
        names.addView((View)sub);
        card.addView((View)names);
        Switch toggle = new Switch(this.getContext());
        toggle.setChecked(enabled);
        toggle.setContentDescription((CharSequence)this.getContext().getString(R.string.cd_search_switch));
        toggle.setThumbTintList(ColorStateList.valueOf((int)this.color(enabled ? R.color.tl_green : R.color.tl_ink3)));
        toggle.setTrackTintList(ColorStateList.valueOf((int)this.color(enabled ? R.color.tl_green_soft : R.color.tl_line)));
        toggle.setOnClickListener(view -> {
            if (this.actions != null) {
                this.actions.onSearchToggled(toggle.isChecked());
            }
        });
        card.addView((View)toggle);
        return card;
    }

    private View engineCard(SearchSettingsPresenter.Row row) {
        boolean selected = this.state.engine == row.engine;
        LinearLayout card = this.card();
        LinearLayout names = new LinearLayout(this.getContext());
        names.setOrientation(LinearLayout.VERTICAL);
        names.setLayoutParams((ViewGroup.LayoutParams)new LinearLayout.LayoutParams(0, -2, 1.0f));
        TextView name = this.tokenText(R.dimen.text_h2, R.color.tl_ink);
        name.setText((CharSequence)this.engineNames[row.engine.ordinal()]);
        names.addView((View)name);
        TextView pill = this.tokenText(R.dimen.text_cap, row.hasKey ? R.color.tl_green_deep : R.color.tl_amber);
        pill.setText(row.hasKey ? R.string.search_key_configured : R.string.search_key_missing);
        pill.setBackgroundResource(R.drawable.bg_pill);
        pill.setBackgroundTintList(ColorStateList.valueOf((int)this.color(row.hasKey ? R.color.tl_green_soft : R.color.tl_amber_soft)));
        names.addView((View)pill);
        card.addView((View)names);
        TextView mark = this.tokenText(R.dimen.text_h2, R.color.tl_green_deep);
        mark.setText((CharSequence)(selected ? MARK : ""));
        card.addView((View)mark);
        card.setOnClickListener(view -> {
            if (this.actions != null && !selected) {
                this.actions.onEngineSelected(row.engine);
            }
        });
        return card;
    }

    private View keyPanel(SearchSettingsPresenter.Row row, KeySnapshot key) {
        LinearLayout panel = new LinearLayout(this.getContext());
        panel.setOrientation(LinearLayout.VERTICAL);
        int pad = this.dimen(R.dimen.space_s3);
        panel.setPadding(pad, 0, pad, pad);
        TextView mask = this.tokenText(R.dimen.text_cap, R.color.tl_ink2);
        mask.setText((CharSequence)(key.line == KeySnapshot.Line.MISSING ? this.getContext().getString(R.string.search_key_none) : key.mask));
        panel.addView((View)mask);
        this.keyInput = new EditText(this.getContext());
        this.keyInput.setHint(R.string.search_key_input_hint);
        this.keyInput.setInputType(129);
        this.keyInput.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
        this.keyInput.setMaxLines(1);
        this.keyInput.setImeOptions(6);
        this.keyInput.setBackgroundResource(R.drawable.bg_field);
        this.keyInput.setTextColor(this.color(R.color.tl_ink));
        this.keyInput.setHintTextColor(this.color(R.color.tl_ink3));
        this.keyInput.setTextSize(0, (float)this.dimen(R.dimen.text_sm));
        this.keyInput.setMinimumHeight(this.dimen(R.dimen.touch_min));
        panel.addView((View)this.keyInput);
        LinearLayout buttons = new LinearLayout(this.getContext());
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setGravity(16);
        Button save = new Button(this.getContext());
        save.setText(R.string.search_key_save);
        save.setBackgroundResource(R.drawable.bg_button_primary);
        save.setTextColor(this.color(R.color.tl_surface));
        save.setMinimumHeight(this.dimen(R.dimen.touch_min));
        save.setOnClickListener(view -> this.saveKey(row.engine));
        buttons.addView((View)save);
        if (row.hasKey) {
            Button delete = new Button(this.getContext());
            delete.setText(R.string.search_key_delete);
            delete.setBackgroundResource(R.drawable.bg_button_danger);
            delete.setTextColor(this.color(R.color.tl_red));
            delete.setMinimumHeight(this.dimen(R.dimen.touch_min));
            LinearLayout.LayoutParams deleteLayout = new LinearLayout.LayoutParams(-2, -2);
            deleteLayout.leftMargin = this.dimen(R.dimen.space_s2);
            delete.setLayoutParams((ViewGroup.LayoutParams)deleteLayout);
            delete.setOnClickListener(view -> {
                this.clearField();
                if (this.actions != null) {
                    this.actions.onKeyDeletePressed(row.engine);
                }
            });
            buttons.addView((View)delete);
        }
        panel.addView((View)buttons);
        return panel;
    }

    private View tierCard() {
        LinearLayout card = this.card();
        TextView label = this.tokenText(R.dimen.text_h2, R.color.tl_ink);
        label.setText(R.string.search_tier_label);
        label.setLayoutParams((ViewGroup.LayoutParams)new LinearLayout.LayoutParams(0, -2, 1.0f));
        card.addView((View)label);
        TextView current = this.tokenText(R.dimen.text_cap, R.color.tl_ink2);
        current.setText((CharSequence)this.tierLabels[this.state.tier.ordinal()]);
        card.addView((View)current);
        TextView chevron = this.tokenText(R.dimen.text_h2, R.color.tl_ink3);
        chevron.setText((CharSequence)"\u203a");
        card.addView((View)chevron);
        card.setOnClickListener(view -> this.tierPopup((View)card));
        return card;
    }

    private void tierPopup(View anchor) {
        LinearLayout content = new LinearLayout(this.getContext());
        content.setOrientation(LinearLayout.VERTICAL);
        content.setBackgroundResource(R.drawable.bg_card);
        content.setElevation((float)this.dimen(R.dimen.card_elevation));
        int pad = this.dimen(R.dimen.space_s2);
        content.setPadding(pad, pad, pad, pad);
        for (SearchSettingsPresenter.Tier tier : SearchSettingsPresenter.Tier.values()) {
            LinearLayout row = new LinearLayout(this.getContext());
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(16);
            row.setMinimumHeight(this.dimen(R.dimen.touch_min));
            row.setPadding(this.dimen(R.dimen.space_s3), 0, this.dimen(R.dimen.space_s3), 0);
            TextView text = this.tokenText(R.dimen.text_body, R.color.tl_ink);
            text.setText((CharSequence)this.tierLabels[tier.ordinal()]);
            text.setLayoutParams((ViewGroup.LayoutParams)new LinearLayout.LayoutParams(0, -2, 1.0f));
            row.addView((View)text);
            TextView mark = this.tokenText(R.dimen.text_body, R.color.tl_green_deep);
            mark.setText((CharSequence)(tier == this.state.tier ? MARK : ""));
            row.addView((View)mark);
            row.setOnClickListener(view -> {
                if (this.actions != null) {
                    this.actions.onTierSelected(tier);
                }
                this.dismiss();
            });
            content.addView((View)row);
        }
        this.showAnchored(anchor, (View)content);
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this.getContext());
        card.setOrientation(LinearLayout.HORIZONTAL);
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

    private TextView tokenText(int sizeRes, int colorRes) {
        TextView text = new TextView(this.getContext());
        text.setTextSize(0, (float)this.dimen(sizeRes));
        text.setTextColor(this.color(colorRes));
        return text;
    }

    private void saveKey(SearchSettingsPresenter.Engine engine) {
        if (this.keyInput == null) {
            return;
        }
        Editable typed = this.keyInput.getText();
        int length = typed == null ? 0 : typed.length();
        char[] candidate = new char[length];
        if (length > 0) {
            typed.getChars(0, length, candidate, 0);
        }
        this.clearField();
        if (this.actions != null) {
            this.actions.onKeySavePressed(engine, candidate);
        }
    }

    private void clearField() {
        if (this.keyInput != null) {
            this.keyInput.setText((CharSequence)"");
        }
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
        this.clearField();
        super.onDetachedFromWindow();
    }

    private int dimen(int res) {
        return this.getResources().getDimensionPixelSize(res);
    }

    private int color(int res) {
        return this.getContext().getColor(res);
    }
}

