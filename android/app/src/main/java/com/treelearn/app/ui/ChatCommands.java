package com.treelearn.app.ui;

import com.treelearn.core.domain.AnswerRevision;
import com.treelearn.core.domain.GenerationModel;

import java.util.List;

/**
 * The write path of the conversation, as the screen sees it: five commands and one list.
 *
 * <p><strong>Retry, reset and version switching are T-010's use cases and nothing else.</strong> The
 * screen may choose which of the three a position permits - that is what {@link ChatMessage}'s
 * predicates are, and a test compares them against {@code PositionCommands} - but it cannot perform
 * any of them: a replacement is a new attempt at the same position, and a version switch moves a
 * display pointer, so both are decisions about stored rows. Writing either one from the UI layer would
 * be the "rewrite a revision in place" the whole provenance design exists to prevent.
 *
 * <p>Every command returns rather than throws for a business outcome: a refusal is an ordinary answer
 * that the presenter renders, and only a caller bug (a malformed identifier, a null model) raises.
 */
public interface ChatCommands {

    /**
     * Asks a follow-up in this branch. {@code quote} is the sentence it was asked from, or empty;
     * {@code attachmentId} is the composer's parsed attachment (T-097), or null for "this turn
     * carries no attachment".
     */
    ChatCommandResult send(String branchId, String text, String quote, String attachmentId,
            GenerationModel model, boolean thinkingEnabled);

    /** Replaces a failed attempt at this position (T-010 {@code AnswerVersions.retry}). */
    ChatCommandResult retry(String messageId, GenerationModel model, boolean thinkingEnabled, long now);

    /** Regenerates the version this position displays (T-010 {@code AnswerVersions.reset}). */
    ChatCommandResult reset(String messageId, GenerationModel model, boolean thinkingEnabled, long now);

    /** Switches which stored version the position displays (T-010 {@code AnswerVersions.selectRevision}). */
    ChatCommandResult selectRevision(String messageId, String revisionId, long now);

    /**
     * Opens a branch from this answer; {@code question} prefills it when the fork copies a
     * suggestion (T-065).
     *
     * <p>{@code quote} with its offsets anchors the sentence the fork was made from: quote and
     * offsets must describe the same range, and a fork whose quote is empty anchors the whole
     * answer, as it always has. The suggestion fork carries the question and no quote; the
     * selection fork carries the quote and no question.
     */
    ChatCommandResult fork(String messageId, String question, String quote, int startInclusive,
            int endExclusive);

    /** The stored versions of one position, oldest first. Never null; empty when there is none. */
    List<AnswerRevision> versions(String messageId);
}