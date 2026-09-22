package com.treelearn.app.screen;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.treelearn.app.R;
import com.treelearn.app.ui.ChatMessage;

/**
 * The reasoning disclosure of one answer (prototype v2.5 `.reasoning-wrap`).
 *
 * <p>Three rules are the component's own, and they are the three the task file names:
 *
 * <ul>
 *   <li><strong>Collapsed unless opened.</strong> The body is drawn only in the expanded state; the
 *       expanded set lives in the screen's state, keyed by message id, so this view is stateless and a
 *       rebuilt row cannot inherit another answer's flag.
 *   <li><strong>No empty entry.</strong> An answer with no reasoning, and no attempt in flight, hides
 *       the whole disclosure rather than offering a control that reveals nothing.
 *   <li><strong>Secondary, readable.</strong> The body uses the secondary ink, which is the pair a
 *       static check measures at 5.7:1 on the surface and 5.2:1 on the page background - above the
 *       4.5:1 the readability rule asks for, unlike the faintest ink the palette also has.
 * </ul>
 *
 * <p>It decides nothing about <em>when</em> to open: a tap is forwarded to the listener, which is the
 * screen, which asks the presenter. That is what keeps the open state in one place.
 */
public final class ReasoningDisclosure extends LinearLayout {

    /** Who is told about a tap. The screen forwards it; the presenter decides. */
    public interface Listener {

        void onReasoningToggled(String messageId);
    }

    private final View toggle;
    private final TextView title;
    private final TextView state;
    private final TextView body;

    private String messageId = "";
    private Listener listener;

    public ReasoningDisclosure(Context context, AttributeSet attributes) {
        super(context, attributes);
        setOrientation(VERTICAL);
        LayoutInflater.from(context).inflate(R.layout.view_reasoning, this, true);
        toggle = findViewById(R.id.reasoning_toggle);
        title = findViewById(R.id.reasoning_title);
        state = findViewById(R.id.reasoning_state);
        body = findViewById(R.id.reasoning_body);
        toggle.setOnClickListener(view -> {
            if (listener != null && !messageId.isEmpty()) {
                listener.onReasoningToggled(messageId);
            }
        });
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    /** Draws one answer's disclosure, or hides itself when that answer has nothing to disclose. */
    public void render(ChatMessage message, boolean expanded) {
        render(message, expanded, true);
    }

    public void render(ChatMessage message, boolean expanded, boolean thinkingEnabled) {
        messageId = message.messageId;
        if (!thinkingEnabled || !message.canExpandReasoning()) {
            setVisibility(GONE);
            body.setText("");
            return;
        }
        setVisibility(VISIBLE);
        state.setText(stateText(message, expanded));
        body.setText(MarkdownRenderer.render(bodyText(message, expanded).toString()),
                TextView.BufferType.SPANNABLE);
        body.setVisibility(expanded ? VISIBLE : GONE);
        toggle.setContentDescription(title.getText() + " " + state.getText());
    }

    /** The three words the prototype puts on the right of the toggle. */
    private CharSequence stateText(ChatMessage message, boolean expanded) {
        if (message.isFormingReasoning()) {
            return getContext().getString(R.string.chat_reasoning_forming);
        }
        return getContext().getString(expanded
                ? R.string.chat_reasoning_collapse : R.string.chat_reasoning_expand);
    }

    /** Before the first reasoning token the body says what is happening instead of staying blank. */
    private CharSequence bodyText(ChatMessage message, boolean expanded) {
        if (!expanded) {
            return "";
        }
        if (message.reasoning.isEmpty()) {
            return getContext().getString(R.string.chat_reasoning_pending);
        }
        return message.reasoning;
    }
}
