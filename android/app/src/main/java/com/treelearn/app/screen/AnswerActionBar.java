package com.treelearn.app.screen;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.treelearn.app.R;
import com.treelearn.app.ui.ChatMessage;

/**
 * One answer's action row (prototype v2.5 `.ai-actions`). 2026-09-27 user decision: the row leads
 * with the prototype's own set - copy, reset, fork, the data-driven child entry - and the time
 * rides at its end (prototype `.ai-time`, line 877), while the app's extras (quote, translation,
 * speech) keep their state-driven slots and the version switcher stays a hidden compatibility
 * entry.
 *
 * <p><strong>Enablement is read, never decided here.</strong> Every button takes its enabled flag from
 * the message's own predicate, which is the same judgement T-010's use case makes before it writes, so
 * a button that looks pressable is a command the store accepts. The row adds nothing to that rule; it
 * only decides what the state cannot show: an answer with no children has no child entry.
 *
 * <p>A tap is forwarded with the message id; the screen maps it to the presenter. Nothing here knows
 * what a reset is.
 */
public final class AnswerActionBar extends LinearLayout {

    /** The gestures the row can report. */
    public interface Listener {

        void onQuotePressed(String messageId);

        void onCopyPressed(String messageId);

        void onResetPressed(String messageId);

        void onForkPressed(String messageId);

        void onChildrenPressed(String messageId);

        void onVersionPressed(String messageId);

        void onTranslatePressed(String messageId);

        void onSpeakPressed(String messageId);
    }

    private final Button quote;
    private final Button copy;
    private final Button reset;
    private final Button fork;
    private final Button children;
    private final Button translate;
    private final Button speak;
    private final TextView version;
    private final TextView time;

    private String messageId = "";
    private Listener listener;

    public AnswerActionBar(Context context, AttributeSet attributes) {
        super(context, attributes);
        setOrientation(HORIZONTAL);
        LayoutInflater.from(context).inflate(R.layout.view_answer_actions, this, true);
        quote = findViewById(R.id.act_quote);
        copy = findViewById(R.id.act_copy);
        reset = findViewById(R.id.act_reset);
        fork = findViewById(R.id.act_fork);
        children = findViewById(R.id.act_children);
        translate = findViewById(R.id.act_translate);
        speak = findViewById(R.id.act_speak);
        version = findViewById(R.id.act_version);
        time = findViewById(R.id.act_time);
        quote.setContentDescription(getContext().getString(R.string.chat_quote_title));
        copy.setContentDescription(getContext().getString(R.string.chat_copy_title));
        reset.setContentDescription(getContext().getString(R.string.chat_reset_title));
        fork.setContentDescription(getContext().getString(R.string.chat_fork_title));
        translate.setContentDescription(getContext().getString(R.string.cd_act_translate));
        quote.setOnClickListener(view -> fire(target -> target.onQuotePressed(messageId)));
        copy.setOnClickListener(view -> fire(target -> target.onCopyPressed(messageId)));
        reset.setOnClickListener(view -> fire(target -> target.onResetPressed(messageId)));
        fork.setOnClickListener(view -> fire(target -> target.onForkPressed(messageId)));
        children.setOnClickListener(view -> fire(target -> target.onChildrenPressed(messageId)));
        version.setOnClickListener(view -> fire(target -> target.onVersionPressed(messageId)));
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    /** Draws the row for one answer. A non-answer row is hidden by the caller. */
    public void render(ChatMessage message, boolean speechAvailable, boolean speaking) {
        messageId = message.messageId;
        quote.setEnabled(message.canQuote());
        copy.setEnabled(message.canCopy());
        reset.setEnabled(message.canReset());
        fork.setEnabled(message.canFork());
        translate.setEnabled(message.canTranslate());
        renderSpeakFace(speechAvailable, speaking);
        speak.setEnabled(message.canSpeak());
        boolean hasChildren = message.hasChildren();
        children.setVisibility(hasChildren ? VISIBLE : GONE);
        children.setEnabled(hasChildren);
        children.setText(hasChildren
                ? getContext().getString(R.string.chat_act_children_count, message.childBranchCount)
                : getContext().getString(R.string.chat_act_children));
        children.setContentDescription(hasChildren
                ? getContext().getString(R.string.chat_children_title, message.childBranchCount)
                : getContext().getString(R.string.chat_act_children));
        version.setVisibility(GONE);
        // 073 §3.5: the switcher reads "n/N" - which version is displayed, out of how many are stored.
        version.setText(getContext().getString(R.string.chat_version_chip,
                message.activeRevisionNo(), message.revisionCount));
        version.setContentDescription(getContext().getString(R.string.chat_version_title,
                message.activeRevisionNo(), message.revisionCount));
    }

    /** The answer the row currently draws; the screen needs it to read a selection. */
    public String messageId() {
        return messageId;
    }

    /**
     * Paints the row's trailing time (prototype `.ai-time`). The row is hidden while the answer
     * streams, so the time appears exactly when the prototype shows it: on a stable answer.
     */
    public void renderTime(long createdAtMs) {
        time.setText(android.text.format.DateUtils.getRelativeTimeSpanString(createdAtMs,
                System.currentTimeMillis(), android.text.format.DateUtils.MINUTE_IN_MILLIS));
    }

    /** Paints the speak button's face (icon + label) for the current speech state. */
    public void renderSpeakFace(boolean speechAvailable, boolean speaking) {
        speak.setVisibility(speechAvailable ? VISIBLE : GONE);
        speak.setContentDescription(getContext().getString(
                speaking ? R.string.cd_act_speak_stop : R.string.cd_act_speak));
        Drawable icon = getResources().getDrawable(
                speaking ? R.drawable.ic_chat_stop : R.drawable.ic_action_speak,
                getContext().getTheme());
        icon.setTint(getContext().getColor(R.color.tl_ink3));
        speak.setCompoundDrawablesWithIntrinsicBounds(icon, null, null, null);
    }

    private void fire(java.util.function.Consumer<Listener> call) {
        if (listener != null && !messageId.isEmpty()) {
            call.accept(listener);
        }
    }
}
