/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  android.content.Context
 *  android.graphics.drawable.ColorDrawable
 *  android.graphics.drawable.Drawable
 *  android.util.AttributeSet
 *  android.view.LayoutInflater
 *  android.view.View
 *  android.view.ViewGroup
 *  android.widget.LinearLayout
 *  android.widget.PopupWindow
 *  android.widget.TextView
 *  com.treelearn.app.R$dimen
 *  com.treelearn.app.R$id
 *  com.treelearn.app.R$layout
 *  com.treelearn.app.R$string
 *  com.treelearn.core.domain.GenerationModel
 */
package com.treelearn.app.screen;

import android.content.Context;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;
import com.treelearn.app.R;
import com.treelearn.core.domain.GenerationModel;

public final class ModelThinkingPicker
extends LinearLayout {
    private static final String MARK = "\u2713";
    private final TextView chip;
    private Listener listener;
    private GenerationModel model = GenerationModel.FLASH;
    private boolean thinkingEnabled;
    private PopupWindow popup;

    public ModelThinkingPicker(Context context, AttributeSet attrs) {
        super(context, attrs);
        this.setOrientation(0);
        this.chip = (TextView)LayoutInflater.from((Context)context).inflate(R.layout.view_model_chip, (ViewGroup)this, false);
        this.addView((View)this.chip);
        this.chip.setOnClickListener(view -> this.togglePopover());
        this.renderChip();
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public void render(GenerationModel model, boolean thinkingEnabled) {
        this.model = model;
        this.thinkingEnabled = thinkingEnabled;
        this.renderChip();
        if (this.popup != null && this.popup.isShowing()) {
            this.popup.dismiss();
        }
    }

    private void renderChip() {
        String label = this.getContext().getString(ModelThinkingPicker.shortLabelRes(this.model));
        this.chip.setText((CharSequence)(this.thinkingEnabled ? label + this.getContext().getString(R.string.model_thinking_suffix) : label));
    }

    private void togglePopover() {
        if (this.popup != null && this.popup.isShowing()) {
            this.popup.dismiss();
            return;
        }
        View content = LayoutInflater.from((Context)this.getContext()).inflate(R.layout.popup_model_picker, (ViewGroup)this, false);
        this.bindRow(content, R.id.picker_flash, GenerationModel.FLASH, R.id.picker_flash_mark);
        this.bindRow(content, R.id.picker_pro, GenerationModel.PRO, R.id.picker_pro_mark);
        TextView thinkingState = (TextView)content.findViewById(R.id.picker_thinking_state);
        thinkingState.setText(this.thinkingEnabled ? R.string.model_thinking_on : R.string.model_thinking_off);
        content.findViewById(R.id.picker_thinking).setOnClickListener(view -> {
            this.toggleThinking();
            this.dismiss();
        });
        this.popup = new PopupWindow(content, -2, -2, true);
        this.popup.setBackgroundDrawable((Drawable)new ColorDrawable(0));
        this.popup.setOutsideTouchable(true);
        this.popup.setElevation(this.getResources().getDimension(R.dimen.card_elevation));
        this.showAnchored(content);
    }

    private void bindRow(View content, int rowId, GenerationModel rowModel, int markId) {
        TextView mark = (TextView)content.findViewById(markId);
        mark.setText((CharSequence)(this.model == rowModel ? MARK : ""));
        content.findViewById(rowId).setOnClickListener(view -> {
            this.select(rowModel);
            this.dismiss();
        });
    }

    private void select(GenerationModel selected) {
        this.model = selected;
        this.renderChip();
        if (this.listener != null) {
            this.listener.onModelSelected(selected);
        }
    }

    private void toggleThinking() {
        this.thinkingEnabled = !this.thinkingEnabled;
        this.renderChip();
        if (this.listener != null) {
            this.listener.onThinkingToggled(this.thinkingEnabled);
        }
    }

    private void showAnchored(View content) {
        content.measure(0, 0);
        int popoverHeight = content.getMeasuredHeight();
        int[] location = new int[2];
        this.getLocationOnScreen(location);
        int spaceBelow = this.getResources().getDisplayMetrics().heightPixels - (location[1] + this.getHeight());
        if (popoverHeight > spaceBelow) {
            this.popup.showAsDropDown((View)this, 0, -(this.getHeight() + popoverHeight));
        } else {
            this.popup.showAsDropDown((View)this);
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

    private static int shortLabelRes(GenerationModel model) {
        return model == GenerationModel.PRO ? R.string.model_pro_short : R.string.model_flash_short;
    }

    public static interface Listener {
        public void onModelSelected(GenerationModel var1);

        public void onThinkingToggled(boolean var1);
    }
}

