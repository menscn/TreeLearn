package com.treelearn.app.screen;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
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
 *
 * <p><strong>Who is the tap target.</strong> The prototype draws this pill about 22px tall
 * (Docs/04-ux-design/知芽-TreeLearn-高保真原型-v1.html line 356), which is smaller than the
 * {@code @dimen/touch_min} a control has to be. The two are therefore split: the pill
 * ({@code R.layout.view_model_chip}) stays faithful, and this container - which carries the click, the
 * focus and the {@code touch_min} height, and is what TalkBack focuses - is the control. The label
 * inside is hidden from accessibility so the whole chip is announced once, as it was when the pill
 * itself was the target.
 */
public final class ModelThinkingPicker extends LinearLayout {

    /** Where a selection goes; the presenter persists it, this view only reports it. */
    public interface Listener {

        void onModelSelected(GenerationModel model);

        void onThinkingToggled(boolean enabled);
    }

    private static final String MARK = "\u2713";

    private final TextView chipLabel;

    private Listener listener;
    private GenerationModel model = GenerationModel.FLASH;
    private boolean thinkingEnabled;
    private PopupWindow popup;

    public ModelThinkingPicker(Context context, AttributeSet attrs) {
        super(context, attrs);
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        View chip = LayoutInflater.from(context).inflate(R.layout.view_model_chip, this, false);
        chipLabel = chip.findViewById(R.id.model_chip_label);
        addView(chip, new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        setForeground(getContext().getDrawable(R.drawable.fg_pill_ripple));
        setMinimumHeight(getResources().getDimensionPixelSize(R.dimen.touch_min));
        setClickable(true);
        setFocusable(true);
        setOnClickListener(view -> togglePopover());
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
        if (!thinkingEnabled) {
            chipLabel.setText(label);
        } else {
            // The thinking marker is the one part of the chip the prototype colours with the identity
            // green (its .thk rule), so it is spanned rather than the whole label being recoloured.
            String marker = getContext().getString(R.string.model_thinking_suffix);
            SpannableString marked = new SpannableString(label + marker);
            marked.setSpan(new ForegroundColorSpan(getContext().getColor(R.color.tl_green_deep)),
                    label.length(), marked.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            marked.setSpan(new StyleSpan(Typeface.BOLD), label.length(), marked.length(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            chipLabel.setText(marked);
        }
        // The container is the control, so it is also the node TalkBack reads. The label inside is
        // hidden from accessibility on purpose, so the chip is announced exactly once, as "the chip
        // says X, double tap to activate" - the same announcement as when the pill itself was the
        // target, which is what keeps the 44dp split from silently costing the control its name.
        setContentDescription(chipLabel.getText());
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
