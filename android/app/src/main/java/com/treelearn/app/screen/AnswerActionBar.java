package com.treelearn.app.screen;

import android.content.Context;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.treelearn.app.R;
import com.treelearn.app.ui.ChatMessage;

/**
 * One answer's action row (prototype v2.5 `.ai-actions`, re-drawn on the 2026-10-25 board).
 * 2026-09-28 user decision: the row carries only the prototype's own four leads - copy, reset, fork,
 * the data-driven child entry. The extras it used to offer here (quote, translation, speech) and the
 * hidden version switcher are gone from this row; the user marked them out on the annotated shot.
 * Quote still reaches the learner from the selection menu that opens on the answer text, and the
 * translation card keeps its own header entry, so nothing is lost.
 *
 * <p><strong>The time left this row on 2026-10-25 (T-2026-10-25-101).</strong> It was placed here on
 * 2026-09-27 so the answer's tag line could stay clean, and the reading-area header now shows the
 * branch's time once. Keeping it here as well repeated the same fact after every answer, and the user
 * read the result as a busy row. The date is still shown, once, above the conversation.
 *
 * <p>All four leads are glyphs. The child entry is the tree glyph (the prototype's own ITree, line
 * 1178) with its count in a green badge, not the bare text "子分支 N" it used to be - the user asked
 * for that explicitly ("分支列表 对应的也是图标，不要是文字！"). The count is the only part that
 * varies, so it lives in a badge child; the glyph never changes and never needs re-measuring.
 *
 * <p><strong>Enablement is read, never decided here.</strong> Every lead takes its enabled flag from
 * the message's own predicate, which is the same judgement T-010's use case makes before it writes, so
 * a lead that looks pressable is a command the store accepts. The row adds nothing to that rule; it
 * only decides what the state cannot show: an answer with no children has no child entry.
 *
 * <p>A tap is forwarded with the message id; the screen maps it to the presenter. Nothing here knows
 * what a reset is.
 */
public final class AnswerActionBar extends LinearLayout {

    /** The gestures the row can report. */
    public interface Listener {

        void onCopyPressed(String messageId);

        void onResetPressed(String messageId);

        void onForkPressed(String messageId);

        void onChildrenPressed(String messageId);
    }

    private final View copy;
    private final View reset;
    private final View fork;
    private final FrameLayout children;
    private final TextView childrenCount;

    private String messageId = "";
    private Listener listener;

    public AnswerActionBar(Context context, AttributeSet attributes) {
        super(context, attributes);
        setOrientation(HORIZONTAL);
        LayoutInflater.from(context).inflate(R.layout.view_answer_actions, this, true);
        copy = findViewById(R.id.act_copy);
        reset = findViewById(R.id.act_reset);
        fork = findViewById(R.id.act_fork);
        children = findViewById(R.id.act_children);
        childrenCount = findViewById(R.id.act_children_count);
        copy.setContentDescription(getContext().getString(R.string.chat_copy_title));
        reset.setContentDescription(getContext().getString(R.string.chat_reset_title));
        fork.setContentDescription(getContext().getString(R.string.chat_fork_title));
        copy.setOnClickListener(view -> fire(target -> target.onCopyPressed(messageId)));
        reset.setOnClickListener(view -> fire(target -> target.onResetPressed(messageId)));
        fork.setOnClickListener(view -> fire(target -> target.onForkPressed(messageId)));
        children.setOnClickListener(view -> fire(target -> target.onChildrenPressed(messageId)));
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    /** Draws the row for one answer. A non-answer row is hidden by the caller. */
    public void render(ChatMessage message) {
        messageId = message.messageId;
        copy.setEnabled(message.canCopy());
        reset.setEnabled(message.canReset());
        fork.setEnabled(message.canFork());
        // The child entry is a glyph plus a count badge, not a label (2026-09-28 user decision).
        // The number is the only thing that varies, so the glyph itself never changes; when there
        // are no children the entry is not offered at all rather than shown disabled.
        boolean hasChildren = message.hasChildren();
        children.setVisibility(hasChildren ? VISIBLE : GONE);
        children.setEnabled(hasChildren);
        // 2026-10-16: this row re-renders with every stream batch. A write that changes nothing still
        // re-measures the row, so each of the three texts is written only when it actually differs.
        String count = String.valueOf(message.childBranchCount);
        if (!TextUtils.equals(childrenCount.getText(), count)) {
            childrenCount.setText(count);
        }
        String description = hasChildren
                ? getContext().getString(R.string.chat_children_title, message.childBranchCount)
                : getContext().getString(R.string.chat_act_children);
        if (!TextUtils.equals(children.getContentDescription(), description)) {
            children.setContentDescription(description);
        }
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
