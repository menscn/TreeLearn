package com.treelearn.ai.prompt;

import com.treelearn.core.domain.ContextPlan;
import com.treelearn.core.domain.Guard;
import java.util.ArrayList;
import java.util.List;

/**
 * Turns a vendor-neutral {@link ContextPlan} into ordinary chat content (technical design 9.4).
 *
 * <p>DeepSeek documents that a request without tools ignores the dedicated {@code reasoning_content}
 * field on historical messages, while this product requires persisted reasoning to reach later
 * context. The two are reconciled here and only here: a stable version becomes one ordinary
 * <em>assistant</em> message whose content carries the reasoning and the final answer inside
 * {@code <treelearn_reasoning>} and {@code <treelearn_answer>} blocks, so the provider consumes the
 * complete history without any special protocol.
 *
 * <p><strong>This is adapter-internal protocol.</strong> The tags exist in this class and nowhere
 * else: {@code :core} entities, the database and the UI never see them, which is what keeps a future
 * provider a matter of adding an encoder rather than changing the domain. A JVM case in this package
 * scans the {@code :core} production sources for the literals and fails if one appears.
 *
 * <p><strong>Content can never forge a frame.</strong> Every string the plan carries, and every
 * citation {@link #currentInput} frames, is {@link #escape escaped}, so a stored answer containing
 * {@code </treelearn_answer>} cannot close the block early or open a new one: exactly one framing pair
 * survives any input. {@link #unescape} reverses it, so the escaping is a transformation rather than a
 * loss.
 *
 * <p>The same envelope frames the sentence a learner cites. §6.2.19 lets a learner ask about one
 * sentence of an answer, so {@link #currentInput} wraps that sentence in {@code QUOTE_OPEN} /
 * {@code QUOTE_CLOSE} ahead of the question - one current question, refined by what it is about,
 * rather than a second turn.
 *
 * <p>Nothing here emits a system message and the plan it is given is encoded as it stands: no entry is
 * trimmed, reordered or dropped. Assembling the single system prompt with the current question
 * belongs to {@link DeepSeekRequestAssembler}; the one thing this class does for the current turn is
 * {@link #currentInput}, because the framing literals live here and nowhere else.
 */
public final class DeepSeekContextEncoder {

    public static final String REASONING_OPEN = "<treelearn_reasoning>";
    public static final String REASONING_CLOSE = "</treelearn_reasoning>";
    public static final String ANSWER_OPEN = "<treelearn_answer>";
    public static final String ANSWER_CLOSE = "</treelearn_answer>";
    public static final String QUOTE_OPEN = "<treelearn_quote>";
    public static final String QUOTE_CLOSE = "</treelearn_quote>";
    public static final String ATTACH_OPEN = "<treelearn_attachment>";
    public static final String ATTACH_CLOSE = "</treelearn_attachment>";

    /** Every literal {@link #escape} neutralises, in one place so the framing cannot drift apart. */
    public static final String[] TAGS = {REASONING_OPEN, REASONING_CLOSE, ANSWER_OPEN, ANSWER_CLOSE,
            QUOTE_OPEN, QUOTE_CLOSE, ATTACH_OPEN, ATTACH_CLOSE};

    private DeepSeekContextEncoder() {
    }

    /**
     * The request's current input for one learner turn: the sentence the learner cited, when there is
     * one, escaping and framed, and then the question exactly as it was asked.
     *
     * <p>A citation is a refinement of the current question, not a turn of its own, so it travels
     * inside the current input. That keeps prompt design §5's invariant - exactly one current
     * question, and it is last - true, keeps {@code :core}'s {@code ContextPlan.Entry} ignorant of the
     * vendor protocol, and puts the citation inside the length the assembler already reserves for the
     * current input.
     *
     * <p><strong>This is the one place that decides whether a turn has a citation.</strong> Callers
     * forward whatever the stored row carries; {@code null} and blank are interpreted here, once, so
     * the send path and the retry/reset path cannot disagree about the same rows (one rule at two
     * entry points is a defect this repository has met more than once).
     *
     * <p><strong>Only the citation is escaped.</strong> It is application-written envelope content, so
     * escaping is what stops a stored sentence from closing the block early or opening a new one. The
     * question travels exactly as the learner typed it, because the same bytes are stored and drawn on
     * screen; escaping it here would make "what was sent" differ from "what was stored" while the
     * learner could simply rephrase. The residual risk is written down in prompt design §5.1: a question
     * that contains the envelope literal reaches the provider unescaped, which the learner can already
     * see on their own screen.
     *
     * @param question the learner's question, sent verbatim; must not be null
     * @param quote the stored citation, or {@code null}/blank for "this turn cites nothing"
     * @return the framed citation followed by the question, or the question alone when there is none
     * @throws IllegalArgumentException when {@code question} is null: a caller bug
     */
    public static String currentInput(String question, String quote) {
        return currentInput(question, quote, null);
    }

    /**
     * The same turn with its attachment text (T-097): the attachment block rides between the
     * citation block and the question, as the sibling mechanism SPEC-082 names. The attachment
     * text is application-written envelope content, so it is escaped like the citation; the
     * null/blank decision is made here, once, so the send path and the rebuilt history cannot
     * disagree about the same attachment.
     *
     * @param question the learner's question, sent verbatim; must not be null
     * @param quote the stored citation, or {@code null}/blank for "this turn cites nothing"
     * @param attachmentText the parsed attachment text, or {@code null}/blank for "this turn
     *                       carries no attachment"
     * @return the framed citation, then the framed attachment, then the question
     * @throws IllegalArgumentException when {@code question} is null: a caller bug
     */
    public static String currentInput(String question, String quote, String attachmentText) {
        String asked = Guard.text(question, "question");
        String cited = quote == null ? "" : quote.trim();
        String attached = attachmentText == null ? "" : attachmentText.trim();
        String head = cited.isEmpty() ? "" : QUOTE_OPEN + "\n" + escape(cited) + "\n"
                + QUOTE_CLOSE + "\n";
        if (!attached.isEmpty()) {
            head = head + ATTACH_OPEN + "\n" + escape(attached) + "\n" + ATTACH_CLOSE + "\n";
        }
        return head.isEmpty() ? asked : head + asked;
    }

    /**
     * The block one attachment contributes to a rebuilt history turn (T-097): the same framing
     * {@link #currentInput} writes on the send path, so a forked branch's replayed question and
     * the original turn read identically to the model. Null/blank yields the empty string —
     * a turn without attachments rebuilds exactly as it always has.
     *
     * @param text the cached {@code extracted_text}, already ceiling-checked by the caller
     */
    public static String attachmentBlock(String text) {
        String attached = text == null ? "" : text.trim();
        if (attached.isEmpty()) {
            return "";
        }
        return ATTACH_OPEN + "\n" + escape(attached) + "\n" + ATTACH_CLOSE;
    }

    /**
     * @param plan the ordered, already validated context; the plan's order is the message order
     * @return one message per plan entry — user turns as user messages, stable versions and summaries
     *         as assistant messages. An empty plan encodes to an empty list, never to an empty
     *         message. No entry can produce a system message.
     * @throws IllegalArgumentException when {@code plan} is null: a caller bug
     */
    public static List<DeepSeekMessage> encode(ContextPlan plan) {
        List<ContextPlan.Entry> entries = Guard.object(plan, "plan").entries();
        List<DeepSeekMessage> messages = new ArrayList<DeepSeekMessage>(entries.size());
        for (ContextPlan.Entry entry : entries) {
            if (entry.kind == ContextPlan.EntryKind.USER_MESSAGE) {
                messages.add(new DeepSeekMessage(DeepSeekMessage.Role.USER, escape(entry.userText)));
            } else if (entry.kind == ContextPlan.EntryKind.STABLE_REVISION) {
                messages.add(new DeepSeekMessage(DeepSeekMessage.Role.ASSISTANT,
                        stableRevisionBlock(entry)));
            } else {
                messages.add(new DeepSeekMessage(DeepSeekMessage.Role.ASSISTANT,
                        escape(entry.summaryText)));
            }
        }
        return messages;
    }

    /**
     * Neutralises the framing literals and the backslash itself, so no plan content can imitate a
     * block boundary and {@link #unescape} can always recover the original text exactly.
     */
    public static String escape(String text) {
        String checked = Guard.text(text, "text");
        StringBuilder out = new StringBuilder(checked.length());
        int at = 0;
        while (at < checked.length()) {
            String tag = tagAt(checked, at);
            if (tag != null) {
                out.append('\\').append(tag);
                at += tag.length();
                continue;
            }
            char current = checked.charAt(at++);
            if (current == '\\') {
                out.append('\\');
            }
            out.append(current);
        }
        return out.toString();
    }

    /**
     * Reverses {@link #escape}.
     *
     * @throws IllegalArgumentException when a backslash introduces neither another backslash nor one of
     *         the framing literals — that text was never produced by {@link #escape}, so guessing what
     *         it meant would be inventing content
     */
    public static String unescape(String text) {
        String checked = Guard.text(text, "text");
        StringBuilder out = new StringBuilder(checked.length());
        int at = 0;
        while (at < checked.length()) {
            char current = checked.charAt(at);
            if (current != '\\') {
                out.append(current);
                at++;
                continue;
            }
            if (at + 1 < checked.length() && checked.charAt(at + 1) == '\\') {
                out.append('\\');
                at += 2;
                continue;
            }
            String tag = at + 1 < checked.length() ? tagAt(checked, at + 1) : null;
            if (tag == null) {
                throw new IllegalArgumentException(
                        "the text carries an escape that this encoder never writes");
            }
            out.append(tag);
            at += tag.length() + 1;
        }
        return out.toString();
    }

    private static String stableRevisionBlock(ContextPlan.Entry entry) {
        return REASONING_OPEN + "\n" + escape(entry.reasoningText) + "\n" + REASONING_CLOSE + "\n"
                + ANSWER_OPEN + "\n" + escape(entry.answerText) + "\n" + ANSWER_CLOSE;
    }

    private static String tagAt(String text, int at) {
        for (String tag : TAGS) {
            if (text.startsWith(tag, at)) {
                return tag;
            }
        }
        return null;
    }
}
