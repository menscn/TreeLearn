package com.treelearn.core.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The ordered, vendor-neutral context of one request (technical design 9.2, 9.4).
 *
 * <p>A plan is a sequence of turns — one learner message, one stable answer version (reasoning and
 * final answer together) or one finished summary — in the order they are to be replayed. It carries
 * content and nothing provider-specific: the framing tags, the chat roles and the message list are
 * the DeepSeek adapter's business, and no entity here knows a provider exists.
 *
 * <p><strong>It never contains a system message.</strong> Reserving and placing the versioned system
 * prompt, and the current question after the history, belongs to the request assembler. A plan has no
 * field that could hold one, which makes "the system prompt appears exactly once and first" a shape of
 * the data rather than a rule someone has to remember.
 *
 * <p>{@link #chars()} is an exact count of the content characters carried by the plan, so a caller can
 * reserve capacity against it before assembling a request. It is deliberately <em>not</em> a token
 * estimate and deliberately says nothing about the overhead the encoder adds: a budget that assumed
 * either would be wrong in the caller's favour.
 */
public final class ContextPlan {

    /** What one turn of the plan is. The set is closed and every value has a producer in this package. */
    public enum EntryKind {

        /** A persisted learner message. */
        USER_MESSAGE,

        /** One stable answer version: its reasoning and its final answer travel together. */
        STABLE_REVISION,

        /** A finished summary covering the question, the reasoning and the answer of older turns. */
        SUMMARY
    }

    /** One turn. Only the fields belonging to its {@link EntryKind} are set; the rest are null. */
    public static final class Entry {

        public final EntryKind kind;
        /** The row this turn was read from. */
        public final String refId;
        /** {@link EntryKind#USER_MESSAGE} only. */
        public final String userText;
        /** {@link EntryKind#STABLE_REVISION} only; may be empty when thinking was disabled. */
        public final String reasoningText;
        /** {@link EntryKind#STABLE_REVISION} only. */
        public final String answerText;
        /** {@link EntryKind#SUMMARY} only. */
        public final String summaryText;

        private Entry(EntryKind kind, String refId, String userText, String reasoningText,
                String answerText, String summaryText) {
            this.kind = Guard.object(kind, "kind");
            this.refId = Guard.id(refId, "refId");
            this.userText = userText;
            this.reasoningText = reasoningText;
            this.answerText = answerText;
            this.summaryText = summaryText;
        }

        public static Entry userMessage(String messageId, String text) {
            return new Entry(EntryKind.USER_MESSAGE, messageId, Guard.nonBlank(text, "userText"),
                    null, null, null);
        }

        /** @param reasoning may be empty — thinking can be switched off — but never null */
        public static Entry stableRevision(String revisionId, String reasoning, String answer) {
            return new Entry(EntryKind.STABLE_REVISION, revisionId, null,
                    Guard.text(reasoning, "reasoningText"), Guard.nonBlank(answer, "answerText"), null);
        }

        public static Entry summary(String summaryId, String text) {
            return new Entry(EntryKind.SUMMARY, summaryId, null, null, null,
                    Guard.nonBlank(text, "summaryText"));
        }

        /** The exact number of content characters this turn contributes. */
        int chars() {
            if (kind == EntryKind.USER_MESSAGE) {
                return userText.length();
            }
            if (kind == EntryKind.STABLE_REVISION) {
                return reasoningText.length() + answerText.length();
            }
            return summaryText.length();
        }
    }

    private final List<Entry> entries;
    private final int chars;

    public ContextPlan(List<Entry> entries) {
        List<Entry> checked = new ArrayList<Entry>(Guard.object(entries, "entries"));
        int total = 0;
        for (Entry entry : checked) {
            total += Guard.object(entry, "entry").chars();
        }
        this.entries = Collections.unmodifiableList(checked);
        this.chars = total;
    }

    /** Ordered and immutable; an empty plan means "this request has no history to replay". */
    public List<Entry> entries() {
        return entries;
    }

    /** Exact content characters carried by this plan. */
    public int chars() {
        return chars;
    }
}