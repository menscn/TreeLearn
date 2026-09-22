package com.treelearn.app.screen;

import android.content.Context;
import android.graphics.drawable.ColorDrawable;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;

import com.treelearn.app.R;
import com.treelearn.core.domain.GenerationModel;

/**
 * The composer's model chip and the popover it opens (PRD 6.2.18 points 2-3).
 *
 * <p>The chip is the only place the current tier and the thinking switch are shown, which is what the
 * PRD asks for: the choice sits next to the question it applies to, is visible before sending, and
 * never retroactively changes an answer that already exists.
 *
 * <p>The popover is anchored to the chip and flips above it when there is not enough room below, so it
 * never covers the control that opened it.
 */
public final class ModelThinkingPicker extends LinearLayout {

    /** Where a selection goes; the presenter persists it, this view only reports it. */
    public interface Listener {

        void onModelSelected(GenerationModel model);

        void onThinkingToggled(boolean enabled);
    }

    private static final String MARK = "\u2713";

    private final TextView chip;

    private Listener listener;
    private GenerationModel model = GenerationModel.FLASH;
    private boolean thinkingEnabled;
    private PopupWindow popup;

    public ModelThinkingPicker(Context context, AttributeSet attrs) {
        super(context, attrs);
        setOrientation(HORIZONTAL);
        chip = (TextView) LayoutInflater.from(context).inflate(R.layout.view_model_chip, this, false);
        addView(chip);
        chip.setOnClickListener(view -> togglePopover());
        renderChip();
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    /** Repaints the chip from the state the presenter rendered; never decides anything itself. */
    public void render(GenerationModel model, boolean thinkingEnabled) {
        this.model = model;
        this.thinkingEnabled = thinkingEnabled;
        renderChip();
        if (popup != null && popup.isShowing()) {
            popup.dismiss();
        }
    }

    private void renderChip() {
        String label = getContext().getString(shortLabelRes(model));
        chip.setText(thinkingEnabled
                ? label + getContext().getString(R.string.model_thinking_suffix)
                : label);
    }

    private void togglePopover() {
        if (popup != null && popup.isShowing()) {
            popup.dismiss();
            return;
        }
        View content = LayoutInflater.from(getContext())
                .inflate(R.layout.popup_model_picker, this, false);
        bindRow(content, R.id.picker_flash, GenerationModel.FLASH, R.id.picker_flash_mark);
        bindRow(content, R.id.picker_pro, GenerationModel.PRO, R.id.picker_pro_mark);
        TextView thinkingState = content.findViewById(R.id.picker_thinking_state);
        thinkingState.setText(thinkingEnabled ? R.string.model_thinking_on
                : R.string.model_thinking_off);
        content.findViewById(R.id.picker_thinking).setOnClickListener(view -> {
            toggleThinking();
            dismiss();
        });
        popup = new PopupWindow(content, LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT,
                true);
        popup.setBackgroundDrawable(new ColorDrawable(0));
        popup.setOutsideTouchable(true);
        popup.setElevation(getResources().getDimension(R.dimen.card_elevation));
        showAnchored(content);
    }

    private void bindRow(View content, int rowId, GenerationModel rowModel, int markId) {
        TextView mark = content.findViewById(markId);
        mark.setText(model == rowModel ? MARK : "");
        content.findViewById(rowId).setOnClickListener(view -> {
            select(rowModel);
            dismiss();
        });
    }

    private void select(GenerationModel selected) {
        model = selected;
        renderChip();
        if (listener != null) {
            listener.onModelSelected(selected);
        }
    }

    private void toggleThinking() {
        thinkingEnabled = !thinkingEnabled;
        renderChip();
        if (listener != null) {
            listener.onThinkingToggled(thinkingEnabled);
        }
    }

    /**
     * Anchored below the chip, or above it when the popover would leave the window - the PRD requires
     * the popover to stay fully visible and to keep the chip visible.
     */
    private void showAnchored(View content) {
        content.measure(MeasureSpec.UNSPECIFIED, MeasureSpec.UNSPECIFIED);
        int popoverHeight = content.getMeasuredHeight();
        int[] location = new int[2];
        getLocationOnScreen(location);
        int spaceBelow = getResources().getDisplayMetrics().heightPixels
                - (location[1] + getHeight());
        if (popoverHeight > spaceBelow) {
            popup.showAsDropDown(this, 0, -(getHeight() + popoverHeight));
        } else {
            popup.showAsDropDown(this);
        }
    }

    private void dismiss() {
        if (popup != null) {
            popup.dismiss();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        dismiss();
        super.onDetachedFromWindow();
    }

    private static int shortLabelRes(GenerationModel model) {
        return model == GenerationModel.PRO ? R.string.model_pro_short : R.string.model_flash_short;
    }
}