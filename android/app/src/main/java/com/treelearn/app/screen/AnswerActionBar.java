package com.treelearn.app.screen;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.treelearn.app.R;
import com.treelearn.app.ui.ChatMessage;

/**
 * One answer's action row (prototype v2.5 `.ai-actions`): quote, copy, reset, fork, the child-branch
 * entry and the version chip.
 *
 * <p><strong>Enablement is read, never decided here.</strong> Every button takes its enabled flag from
 * the message's own predicate, which is the same judgement T-010's use case makes before it writes, so
 * a button that looks pressable is a command the store accepts. The row adds nothing to that rule; it
 * only decides what the state cannot show, which is that an answer with no children has no children
 * entry and an answer with one version has no chip.
 *
 * <p>A tap is forwarded with the message id; the screen maps it to the presenter. Nothing here knows
 * what a reset is.
 */
public final class AnswerActionBar extends LinearLayout {

    /** The six gestures the row can report. */
    public interface Listener {

        void onQuotePressed(String messageId);

        void onCopyPressed(String messageId);

        void onResetPressed(String messageId);

        void onForkPressed(String messageId);

        void onChildrenPressed(String messageId);

        void onVersionPressed(String messageId);
    }

    private final Button quote;
    private final Button copy;
    private final Button reset;
    private final Button fork;
    private final Button children;
    private final TextView version;

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
        version = findViewById(R.id.act_version);
        quote.setContentDescription(getContext().getString(R.string.chat_quote_title));
        copy.setContentDescription(getContext().getString(R.string.chat_copy_title));
        reset.setContentDescription(getContext().getString(R.string.chat_reset_title));
        fork.setContentDescription(getContext().getString(R.string.chat_fork_title));
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
    public void render(ChatMessage message) {
        messageId = message.messageId;
        quote.setEnabled(message.canQuote());
        copy.setEnabled(message.canCopy());
        reset.setEnabled(message.canReset());
        fork.setEnabled(message.canFork());
        boolean hasChildren = message.hasChildren();
        children.setVisibility(hasChildren ? VISIBLE : GONE);
        children.setEnabled(hasChildren);
        children.setText(hasChildren
                ? getContext().getString(R.string.chat_act_children_count, message.childBranchCount)
                : getContext().getString(R.string.chat_act_children));
        children.setContentDescription(hasChildren
                ? getContext().getString(R.string.chat_children_title, message.childBranchCount)
                : getContext().getString(R.string.chat_act_children));
        boolean hasVersions = message.hasVersionHistory();
        version.setVisibility(hasVersions ? VISIBLE : GONE);
        version.setText(getContext().getString(R.string.chat_version_chip, message.revisionCount));
        version.setContentDescription(
                getContext().getString(R.string.chat_version_title, message.revisionCount));
    }

    /** The answer the row currently draws; the screen needs it to read a selection. */
    public String messageId() {
        return messageId;
    }

    private void fire(java.util.function.Consumer<Listener> call) {
        if (listener != null && !messageId.isEmpty()) {
            call.accept(listener);
        }
    }
}