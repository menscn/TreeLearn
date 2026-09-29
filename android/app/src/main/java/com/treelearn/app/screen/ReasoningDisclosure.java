package com.treelearn.app.screen;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.treelearn.app.R;
import com.treelearn.app.ui.ChatMessage;

/**
 * The reasoning disclosure of one answer (prototype v2.5 `.reasoning-wrap`, T-066 DeepSeek form).
 *
 * <p>Four rules are the component's own, and they are the ones the task file names:
 *
 * <ul>
 *   <li><strong>Open only by intent or by stream.</strong> The body is drawn only in the expanded
 *       state, and which state that is comes from the screen's state ({@code
 *       ChatViewState.isReasoningEffectivelyExpanded}): reasoning that is streaming is open by
 *       default, and a learner's tap overrides that default until the next tap. The view draws the
 *       flag it is handed and decides nothing about <em>when</em> to open.
 *   <li><strong>No empty entry.</strong> An answer with no reasoning, and no attempt in flight, hides
 *       the whole disclosure rather than offering a control that reveals nothing.
 *   <li><strong>Secondary, readable, subordinate.</strong> The body uses the secondary ink, which is
 *       the pair a static check measures at 5.7:1 on the surface and 5.2:1 on the page background -
 *       above the 4.5:1 the readability rule asks for - and sits in a half-folded scroll container
 *       behind a 2dp rule, the way supporting material reads as supporting.
 *   <li><strong>The newest reasoning is the one on screen, until the learner says otherwise.</strong>
 *       The half-folded container shows only part of a long reasoning, so while the model is writing
 *       the body follows its own tail ({@link #followTail}). Scrolling the body up hands the position
 *       to the learner - the same hand-over the conversation's follow-latest makes (T-075) - and only
 *       scrolling back to the end hands it back (2026-09-28 user report: "思考过程没有自动跟随滚动").
 * </ul>
 *
 * <p>A tap is forwarded to the listener, which is the screen, which asks the presenter. That is what
 * keeps the open state in one place.
 */
public final class ReasoningDisclosure extends LinearLayout {

    /** Who is told about a tap. The screen forwards it; the presenter decides. */
    public interface Listener {

        void onReasoningToggled(String messageId);
    }

    private final View toggle;
    private final TextView title;
    private final TextView state;
    private final ScrollView bodyScroll;
    private final TextView body;

    private String messageId = "";
    private Listener listener;
    /**
     * What the last render actually put on screen. {@link #render} is called once per streamed
     * state, which is once per token, and every write it makes costs a measure and a layout pass -
     * so each write is now guarded by "did this value change". Without the guards the reasoning area
     * visibly jitters for the whole time the model is thinking (2026-09-28 user report).
     */
    private CharSequence renderedState = "";
    private CharSequence renderedBody = "";
    private CharSequence renderedDescription = "";
    /** The answer the caches and the applied height belong to. */
    private String renderedMessageId = "";
    /** The height this component last wrote onto {@link #bodyScroll}; see {@link #clampBodyHeight}. */
    private int appliedHeight = Integer.MIN_VALUE;
    /**
     * Whether the body is kept on its own end. It starts true - a stream nobody has touched belongs
     * at its newest words - and a learner who scrolls the body up owns the position from then on
     * until they scroll back to the end. A new answer is a new stream, so it takes the tail back.
     */
    private boolean followingTail = true;

    public ReasoningDisclosure(Context context, AttributeSet attributes) {
        super(context, attributes);
        setOrientation(VERTICAL);
        LayoutInflater.from(context).inflate(R.layout.view_reasoning, this, true);
        toggle = findViewById(R.id.reasoning_toggle);
        title = findViewById(R.id.reasoning_title);
        state = findViewById(R.id.reasoning_state);
        bodyScroll = findViewById(R.id.reasoning_body_scroll);
        body = findViewById(R.id.reasoning_body);
        toggle.setOnClickListener(view -> {
            if (listener != null && !messageId.isEmpty()) {
                listener.onReasoningToggled(messageId);
            }
        });
        // Every scroll that happens - the learner's and this component's own - restates who owns the
        // position: at the end means following, anywhere above it means the learner moved it.
        bodyScroll.setOnScrollChangeListener((view, x, y, oldX, oldY) -> followingTail = atTail());
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    /** Draws one answer's disclosure, or hides itself when that answer has nothing to disclose. */
    public void render(ChatMessage message, boolean expanded, boolean thinkingEnabled) {
        messageId = message.messageId;
        if (!thinkingEnabled || !message.canExpandReasoning()) {
            setVisibility(GONE);
            // Nothing behind the disclosure; drop the caches so a later answer re-draws from scratch.
            body.setText("");
            renderedBody = "";
            renderedState = "";
            renderedDescription = "";
            appliedHeight = Integer.MIN_VALUE;
            followingTail = true;
            return;
        }
        setVisibility(VISIBLE);
        // Only a new answer resets the clamp: the height belongs to this component's current body,
        // and a different answer is a different body. Same-answer renders keep it so the cap does
        // not get re-decided from scratch on every token. A new answer is also a new stream, which
        // nobody has scrolled yet, so the tail is followed again.
        if (!message.messageId.equals(renderedMessageId)) {
            renderedMessageId = message.messageId;
            appliedHeight = Integer.MIN_VALUE;
            followingTail = true;
        }

        CharSequence nextState = stateText(message, expanded);
        if (!nextState.toString().contentEquals(renderedState)) {
            state.setText(nextState);
            renderedState = nextState;
        }

        CharSequence nextBody = bodyText(message, expanded);
        if (!nextBody.toString().contentEquals(renderedBody)) {
            body.setText(MarkdownRenderer.render(nextBody.toString()), TextView.BufferType.SPANNABLE);
            renderedBody = nextBody;
        }

        int wantedVisibility = expanded ? VISIBLE : GONE;
        if (bodyScroll.getVisibility() != wantedVisibility) {
            bodyScroll.setVisibility(wantedVisibility);
        }

        CharSequence nextDescription = title.getText() + " " + nextState;
        if (!nextDescription.toString().contentEquals(renderedDescription)) {
            toggle.setContentDescription(nextDescription);
            renderedDescription = nextDescription;
        }
        clampBodyHeight();
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

    /**
     * Whether the learner is at the body's end. A line of slop counts as the end: a tail that stops
     * a few pixels short of the bottom is still the tail being read, and without the slop a scroll
     * that ends mid-line would silently detach the follow.
     */
    private boolean atTail() {
        View content = bodyScroll.getChildAt(0);
        if (content == null) {
            return true;
        }
        int viewport = bodyScroll.getHeight()
                - bodyScroll.getPaddingTop() - bodyScroll.getPaddingBottom();
        int maxScroll = Math.max(0, content.getHeight() - viewport);
        int slop = getResources().getDimensionPixelSize(R.dimen.space_s3);
        return bodyScroll.getScrollY() >= maxScroll - slop;
    }

    /**
     * The half-folded read: the scroll container grows with its content up to the cap and then
     * scrolls in place, so a stream that just started is one line tall, not a box with empty space.
     * {@code android:maxHeight} on a TextView carries no scrolling semantics, which is why the cap
     * is applied here, after a layout pass has measured the content.
     *
     * <p><strong>The measurement is the child's, not this container's.</strong> Reading {@code
     * bodyScroll.getHeight()} here was the flicker: that is the <em>previous</em> frame's height,
     * while {@code setText} has already changed the content, so the two disagree for exactly one
     * frame and the decision oscillates - grow, clamp, un-clamp, grow - on every streamed token
     * (2026-09-28 user report). Measuring the content answers the question actually being asked
     * ("is the content taller than the cap?") against the value that changed.
     *
     * <p>The same measurement is what the follow needs, which is why the tail is kept here rather
     * than in a second posted runnable: this is the only place that knows how tall the content the
     * model just wrote is. Past the cap the container shows {@code maxHeight} of it, so the end of
     * the content sits {@code content - maxHeight} pixels down; below the cap the whole content fits
     * and there is nothing to scroll to.
     */
    private void clampBodyHeight() {
        if (bodyScroll.getVisibility() != VISIBLE) {
            return;
        }
        bodyScroll.post(() -> {
            if (bodyScroll.getVisibility() != VISIBLE) {
                return;
            }
            int maxHeight = getResources().getDimensionPixelSize(R.dimen.reasoning_max_height);
            int content = 0;
            for (int i = 0; i < bodyScroll.getChildCount(); i++) {
                content = Math.max(content, bodyScroll.getChildAt(i).getMeasuredHeight());
            }
            // The body's own bottom margin is outside the child's measured height but inside the
            // scroll container, so it counts towards what the container has to hold.
            ViewGroup.MarginLayoutParams bodyParams =
                    (ViewGroup.MarginLayoutParams) body.getLayoutParams();
            content += bodyParams.topMargin + bodyParams.bottomMargin;
            int target = content > maxHeight ? maxHeight : ViewGroup.LayoutParams.WRAP_CONTENT;
            if (appliedHeight != target) {
                appliedHeight = target;
                ViewGroup.LayoutParams params = bodyScroll.getLayoutParams();
                params.height = target;
                bodyScroll.setLayoutParams(params);
            }
            followTail(content, maxHeight);
        });
    }

    /**
     * Puts the newest reasoning on screen, and only while the tail is still being followed.
     *
     * <p>The offset is written only when it differs from the one already applied: the learner's own
     * scroll and this one are the same scroll view, and a write that changes nothing would still
     * cost a redraw on every token - the very thing the dedupe guards exist to avoid.
     */
    private void followTail(int content, int maxHeight) {
        if (!followingTail) {
            return;
        }
        int viewport = content > maxHeight ? maxHeight : content;
        int tail = Math.max(0, content - viewport);
        if (bodyScroll.getScrollY() != tail) {
            bodyScroll.scrollTo(0, tail);
        }
    }
}
