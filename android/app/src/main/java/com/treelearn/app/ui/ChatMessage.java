package com.treelearn.app.ui;

import com.treelearn.core.domain.Guard;

/**
 * One row of the conversation as the chat screen draws it (technical design 13).
 *
 * <p>A learner row carries its question and, when it was asked from a selected sentence, that
 * sentence. An AI row carries the answer so far, the reasoning so far, which phase it is in and the
 * version it displays. Everything the row can do is a predicate here rather than a decision in the
 * view, and both callers of those predicates - the row and the presenter - read the same answer.
 *
 * <p><strong>The action predicates are the store's judgement, not a second one.</strong>
 * {@link #canRetry}, {@link #canReset} and {@link #canSelectRevision} are the same conditions T-010's
 * {@code PositionCommands} applies before it writes, and {@code ChatPositionAgreementTest} drives one
 * set of inputs through both so they cannot drift. A button that is enabled is therefore a button the
 * use case accepts, and the alternative - letting the view guess - is how a screen ends up offering an
 * action the store refuses.
 *
 * <p>{@link #canCopy}, {@link #canQuote} and {@link #canFork} are different: they say the answer is a
 * finished version rather than a partial one, because a half-generated answer must not become a
 * branch source or be copied as if it were final (PRD 6.2.14).
 *
 * <p>This type holds content and never writes it anywhere: {@link #copyText} composes the clipboard
 * text and returns it, and no method here logs, persists or echoes it.
 */
public final class ChatMessage {

    /** Where an answer is in its attempt. */
    public enum Status {
        /** An attempt is running; the row shows what has arrived so far. */
        GENERATING,
        /** The attempt ended without a stable answer; the partial text stays readable and is not a source. */
        FAILED,
        /** A stored version is displayed. */
        STABLE
    }

    /** Which of the two streams an unfinished attempt is filling. */
    public enum Phase {
        REASONING,
        ANSWER
    }

    /** Canonical UUID of the position. */
    public final String messageId;
    public final boolean ai;
    /** The question (learner row) or the answer so far (answer row). */
    public final String text;
    /** The sentence a learner row was asked from; empty when the question was typed free-hand. */
    public final String quote;
    /** The reasoning so far; empty when this answer has none, which is an ordinary state. */
    public final String reasoning;
    public final Status status;
    /** Read only while {@link #status} is {@link Status#GENERATING}. */
    public final Phase phase;
    /** The version displayed; empty while there is none. */
    public final String activeRevisionId;
    /**
     * T-074: the displayed version's number, the "n" of the switcher's "n/N". Zero when the row
     * carries no version, and zero from the legacy factory that never knew the number.
     */
    public final int activeRevisionNo;
    /** How many versions this position has stored; more than one means a switcher is worth drawing. */
    public final int revisionCount;
    public final int childBranchCount;
    /** Whether this branch has an attempt in flight, which is what blocks a second one. */
    public final boolean attemptRunning;
    /** Epoch millis of the row; the screen formats it in the device's own zone. */
    public final long createdAt;

    private ChatMessage(String messageId, boolean ai, String text, String quote, String reasoning,
            Status status, Phase phase, String activeRevisionId, int activeRevisionNo,
            int revisionCount, int childBranchCount, boolean attemptRunning, long createdAt) {
        this.messageId = Guard.id(messageId, "messageId");
        this.ai = ai;
        this.text = Guard.text(text, "text");
        this.quote = quote == null ? "" : quote.trim();
        this.reasoning = reasoning == null ? "" : reasoning;
        this.status = Guard.object(status, "status");
        this.phase = Guard.object(phase, "phase");
        this.activeRevisionId = activeRevisionId == null || activeRevisionId.isEmpty()
                ? "" : Guard.id(activeRevisionId, "activeRevisionId");
        this.activeRevisionNo = Guard.atLeast(activeRevisionNo, 0, "activeRevisionNo");
        this.revisionCount = Guard.atLeast(revisionCount, 0, "revisionCount");
        this.childBranchCount = Guard.atLeast(childBranchCount, 0, "childBranchCount");
        this.attemptRunning = attemptRunning;
        this.createdAt = Guard.epochMillis(createdAt, "createdAt");
    }

    /** A learner row. Its content is immutable once created, so it owns no attempt and no version. */
    public static ChatMessage user(String messageId, String text, String quote, long createdAt) {
        return new ChatMessage(messageId, false, text, quote, "", Status.STABLE, Phase.ANSWER, "", 0,
                0, 0, false, createdAt);
    }

    /** An answer row whose displayed version's number is not known by the caller. */
    public static ChatMessage ai(String messageId, String answer, String reasoning, Status status,
            Phase phase, String activeRevisionId, int revisionCount, int childBranchCount,
            boolean attemptRunning, long createdAt) {
        return ai(messageId, answer, reasoning, status, phase, activeRevisionId, 0, revisionCount,
                childBranchCount, attemptRunning, createdAt);
    }

    /** An answer row, in whichever state the store reports, with the displayed version's number. */
    public static ChatMessage ai(String messageId, String answer, String reasoning, Status status,
            Phase phase, String activeRevisionId, int activeRevisionNo, int revisionCount,
            int childBranchCount, boolean attemptRunning, long createdAt) {
        return new ChatMessage(messageId, true, answer, "", reasoning, status, phase, activeRevisionId,
                activeRevisionNo, revisionCount, childBranchCount, attemptRunning, createdAt);
    }

    public boolean isAi() {
        return ai;
    }

    public boolean isStable() {
        return ai && status == Status.STABLE;
    }

    public boolean isFailed() {
        return ai && status == Status.FAILED;
    }

    public boolean isGenerating() {
        return ai && status == Status.GENERATING;
    }

    public Phase phase() {
        return phase;
    }

    /** The "n" of the switcher's "n/N"; zero when the row carries no version or the number is unknown. */
    public int activeRevisionNo() {
        return activeRevisionNo;
    }

    public boolean hasReasoning() {
        return !reasoning.isEmpty();
    }

    /**
     * The disclosure exists when there is something behind it, including the moment before the first
     * reasoning token arrives: an answer that is still reasoning has a disclosure that says so, while a
     * finished answer that never had reasoning has none at all (task file: no empty entry).
     */
    public boolean canExpandReasoning() {
        return ai && (hasReasoning() || isFormingReasoning());
    }

    /** True while the attempt is filling the reasoning stream, whether or not a token has arrived. */
    public boolean isFormingReasoning() {
        return isGenerating() && phase == Phase.REASONING;
    }

    /** Only a finished version may be copied; a partial answer is not "the answer". */
    public boolean canCopy() {
        return isStable();
    }

    public boolean canQuote() {
        return isStable();
    }

    public boolean canFork() {
        return isStable();
    }

    /** Same condition as {@code PositionCommands.retryRefusal}: an AI position with no attempt in flight. */
    public boolean canRetry() {
        return ai && !attemptRunning;
    }

    /** {@link #canRetry} plus a displayed version, which is what a reset regenerates. */
    public boolean canReset() {
        return canRetry() && !activeRevisionId.isEmpty();
    }

    /** The version switch's permission, matching {@code PositionCommands.selectRefusal}. */
    public boolean canSelectRevision(String revisionId) {
        return canRetry() && revisionId != null && !revisionId.equals(activeRevisionId);
    }

    /** Whether a switcher is worth drawing at all. A permission, not a count. */
    public boolean hasVersionHistory() {
        return ai && revisionCount > 1;
    }

    public boolean hasChildren() {
        return childBranchCount > 0;
    }

    /**
     * The clipboard text: the whole reasoning, then the final answer (prototype v2.5,
     * {@code copyMsg}). The headings come from the caller so the two words stay in
     * {@code values/strings.xml} where the rest of the user-visible copy lives.
     */
    public String copyText(CopyLabels labels) {
        Guard.object(labels, "labels");
        if (reasoning.isEmpty()) {
            return text;
        }
        return labels.reasoning + "\n" + reasoning + "\n\n" + labels.answer + "\n" + text;
    }
}