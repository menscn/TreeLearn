package com.treelearn.app.screen;

import android.content.Context;
import android.graphics.drawable.ColorDrawable;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;

import com.treelearn.app.R;
import com.treelearn.core.domain.Persona;

/**
 * The composer's answer-style chip and the seven-row popover it opens
 * (SPEC-2026-09-21-ai-persona-preference).
 *
 * <p>The chip is the visible copy of the topic's stored style; this view never writes or decides -
 * a tap reports {@link Listener#onPersonaSelected} and the presenter owns the store write and the
 * optimistic rollback. Rows follow {@link Persona#values()}, which is the wire-id order and therefore
 * the one order the picker can have without a second list to drift from.
 *
 * <p>Same target split as the model chip: the green-soft pill is the visual, this 44dp container is
 * the control and the accessibility node. The popover is anchored and flips above the chip when the
 * window has no room below, so it never covers the control that opened it.
 */
public final class PersonaPicker extends LinearLayout {

    /** Where a choice goes; the presenter writes it and renders the result back through {@link #render}. */
    public interface Listener {

        void onPersonaSelected(Persona persona);

        /** The last row asks for the static introduction; the host owns that navigation. */
        void onIntroRequested();
    }

    private final TextView chipLabel;

    private Listener listener;
    private Persona persona = Persona.DEFAULT;
    private PopupWindow popup;

    public PersonaPicker(Context context, AttributeSet attrs) {
        super(context, attrs);
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        View chip = LayoutInflater.from(context).inflate(R.layout.view_persona_chip, this, false);
        chipLabel = chip.findViewById(R.id.persona_chip_label);
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
    public void render(Persona value) {
        this.persona = value == null ? Persona.DEFAULT : value;
        renderChip();
        if (popup != null && popup.isShowing()) {
            popup.dismiss();
        }
    }

    private void renderChip() {
        String name = getContext().getString(PersonaUi.nameRes(persona));
        chipLabel.setText(name);
        // The container is the control, so it is the node TalkBack reads; the label inside is hidden
        // from accessibility so the chip is announced once, with its current style in the sentence.
        setContentDescription(getContext().getString(R.string.cd_persona_chip, name));
    }

    private void togglePopover() {
        if (popup != null && popup.isShowing()) {
            popup.dismiss();
            return;
        }
        LinearLayout content = new LinearLayout(getContext());
        content.setOrientation(VERTICAL);
        content.setBackgroundResource(R.drawable.bg_popover);
        content.setElevation(getResources().getDimension(R.dimen.card_elevation));
        content.setMinimumWidth(getResources().getDimensionPixelSize(R.dimen.picker_min_width));
        int pad = getResources().getDimensionPixelSize(R.dimen.space_s2);
        content.setPadding(pad, pad, pad, pad);
        for (Persona persona : Persona.values()) {
            View row = LayoutInflater.from(getContext())
                    .inflate(R.layout.view_persona_option, content, false);
            row.setMinimumHeight(getResources().getDimensionPixelSize(R.dimen.touch_min));
            bindRow(row, persona);
            content.addView(row);
        }
        addIntroRow(content);
        popup = new PopupWindow(content, LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT,
                true);
        popup.setBackgroundDrawable(new ColorDrawable(0));
        popup.setOutsideTouchable(true);
        popup.setElevation(getResources().getDimension(R.dimen.card_elevation));
        showAnchored(content);
    }

    private void bindRow(View row, Persona value) {
        TextView name = row.findViewById(R.id.persona_option_name);
        TextView tag = row.findViewById(R.id.persona_option_tag);
        ImageView check = row.findViewById(R.id.persona_option_check);
        name.setText(PersonaUi.nameRes(value));
        tag.setText(PersonaUi.tagRes(value));
        boolean current = value == this.persona;
        row.setSelected(current);
        check.setVisibility(current ? VISIBLE : INVISIBLE);
        row.setOnClickListener(view -> {
            select(value);
            dismiss();
        });
    }

    /** The separator and last row from the prototype: a road out of choosing, not an eighth choice. */
    private void addIntroRow(LinearLayout content) {
        View separator = new View(getContext());
        separator.setBackgroundColor(getContext().getColor(R.color.tl_line));
        LinearLayout.LayoutParams separatorParams = new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT,
                getResources().getDimensionPixelSize(R.dimen.hairline));
        int gap = getResources().getDimensionPixelSize(R.dimen.space_s1);
        separatorParams.topMargin = gap;
        separatorParams.bottomMargin = gap;
        content.addView(separator, separatorParams);
        View more = LayoutInflater.from(getContext())
                .inflate(R.layout.view_persona_more, content, false);
        more.setOnClickListener(view -> {
            dismiss();
            if (listener != null) {
                listener.onIntroRequested();
            }
        });
        content.addView(more);
    }

    private void select(Persona selected) {
        render(selected);
        if (listener != null) {
            listener.onPersonaSelected(selected);
        }
    }

    /**
     * Anchored below the chip, or above the whole dock when the popover would leave the window.
     *
     * <p>Flipping against the chip alone would end the list exactly at the chip's top, which is
     * inside the dock's input line - the draft would sit under the last rows. The dock card is
     * therefore the flip anchor, so the list floats over the conversation and the composer stays
     * readable. The chip remains the below-anchor, where there is room for it.
     */
    private void showAnchored(View content) {
        content.measure(MeasureSpec.UNSPECIFIED, MeasureSpec.UNSPECIFIED);
        int popoverHeight = content.getMeasuredHeight();
        int[] location = new int[2];
        getLocationOnScreen(location);
        int spaceBelow = getResources().getDisplayMetrics().heightPixels
                - (location[1] + getHeight());
        if (popoverHeight > spaceBelow) {
            View dock = dockAncestor();
            popup.showAsDropDown(dock, 0, -(dock.getHeight() + popoverHeight));
        } else {
            popup.showAsDropDown(this);
        }
    }

    /** The glass card that hosts the tools line, or this chip when the picker stands alone. */
    private View dockAncestor() {
        View cursor = this;
        while (cursor.getParent() instanceof View) {
            cursor = (View) cursor.getParent();
            if (cursor.getId() == R.id.chat_composer_card) {
                return cursor;
            }
        }
        return this;
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
}
