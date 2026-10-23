package com.treelearn.app.generation;

import com.treelearn.core.domain.DomainErrorCode;
import com.treelearn.core.domain.DomainResult;
import com.treelearn.core.domain.GenerationCommand;
import com.treelearn.core.domain.GenerationModel;
import com.treelearn.core.domain.Guard;
import com.treelearn.core.domain.Message;
import com.treelearn.core.domain.Persona;
import com.treelearn.core.domain.PromptPurpose;

/**
 * Whether a position may be retried, reset or switched to another version (technical design 5.1, 7).
 *
 * <p>A retry and a reset are new attempts at the <em>same</em> position: both reuse the existing AI
 * message row, so neither duplicates a learner message nor adds a turn. They differ only in what they
 * require - a retry may replace a failed attempt that produced nothing, a reset replaces a version the
 * learner is actually looking at - which is why they share {@link #positionRefusal} and then add one
 * condition each.
 *
 * <p><strong>Why this pre-check exists, and why it decides nothing of its own.</strong> A refusal has
 * to be reached before an operation row is written or a call opened, while the domain's guards run on
 * the position afterwards. Two places judging one question is exactly how one input ends up answered
 * two ways, so every judgement here is the domain's own: {@link Message#bindOperation} and
 * {@link Message#activateRevision} stay the authority, and the acceptance suite compares the two entry
 * points on the same inputs. This class classifies; it never invents a rule the domain does not have.
 *
 * <p>Shape checks are deliberately absent. A malformed identifier is a caller bug that throws from the
 * domain guard, so this pre-check only ever sees well-formed values.
 */
public final class PositionCommands {

    /** Why a retry, a reset or a version selection is refused. {@code null} is never a refusal. */
    public enum Refusal {
        /** No message row carries the id; deleting its branch cascades, so nothing is left to act on. */
        MESSAGE_NOT_FOUND,
        /** The position holds learner text: it has no attempt to replace and no version to display. */
        NOT_AN_AI_POSITION,
        /** An attempt is in flight in this branch, so a second one would break the ordering of 7. */
        ATTEMPT_ALREADY_RUNNING,
        /** The position displays no stable version yet, so there is nothing to re-generate. */
        NO_STABLE_VERSION,
        /** The named revision is not a version of this position, or does not exist at all. */
        REVISION_NOT_OF_THIS_POSITION,
        /** The named revision is already the version this position displays. */
        REVISION_ALREADY_ACTIVE
    }

    private PositionCommands() {
    }

    /** @return the refusal, or {@code null} when a retry of this position is allowed */
    public static Refusal retryRefusal(Message position, boolean attemptRunning) {
        return positionRefusal(position, attemptRunning);
    }

    /** @return the refusal, or {@code null} when a reset of this position is allowed */
    public static Refusal resetRefusal(Message position, boolean attemptRunning) {
        Refusal base = positionRefusal(position, attemptRunning);
        if (base != null) {
            return base;
        }
        return position.activeRevisionId() == null ? Refusal.NO_STABLE_VERSION : null;
    }

    /**
     * @param owningMessageId the position the named revision belongs to, or {@code null} when no
     *                        revision carries that id
     * @return the refusal, or {@code null} when this position may display the named revision
     */
    public static Refusal selectRefusal(Message position, String revisionId, String owningMessageId,
            boolean attemptRunning) {
        Refusal base = positionRefusal(position, attemptRunning);
        if (base != null) {
            return base;
        }
        if (owningMessageId == null || !owningMessageId.equals(position.id)) {
            return Refusal.REVISION_NOT_OF_THIS_POSITION;
        }
        return revisionId.equals(position.activeRevisionId())
                ? Refusal.REVISION_ALREADY_ACTIVE : null;
    }

    /**
     * The judgement all three commands share, made in the order the domain's own guards make it: a
     * missing position first, then the role, then whether an attempt is in flight.
     */
    private static Refusal positionRefusal(Message position, boolean attemptRunning) {
        if (position == null) {
            return Refusal.MESSAGE_NOT_FOUND;
        }
        if (!position.isAi()) {
            return Refusal.NOT_AN_AI_POSITION;
        }
        return attemptRunning ? Refusal.ATTEMPT_ALREADY_RUNNING : null;
    }

    /**
     * The one definition of the error code a refusal is reported as.
     *
     * <p>It lives beside the refusals so the mapping has a single definition; putting it in the
     * store-side use case would make it a second judgement about the same question. Four of the six
     * codes are the ones the domain's own guards already return for the identical judgement.
     * {@code MESSAGE_NOT_FOUND} and {@code REVISION_NOT_OF_THIS_POSITION} share
     * {@code OPERATION_NOT_COMPLETABLE}, because this build has no code that separates "the position
     * is gone" from "no such version of this position"; adding one means changing the domain module,
     * which another lane owns.
     *
     * @throws IllegalArgumentException when a refusal this build cannot name is passed
     */
    public static DomainErrorCode codeFor(Refusal refusal) {
        switch (refusal) {
            case MESSAGE_NOT_FOUND:
            case REVISION_NOT_OF_THIS_POSITION:
                return DomainErrorCode.OPERATION_NOT_COMPLETABLE;
            case NOT_AN_AI_POSITION:
                return DomainErrorCode.MESSAGE_ROLE_MISMATCH;
            case ATTEMPT_ALREADY_RUNNING:
                return DomainErrorCode.ILLEGAL_STATE_TRANSITION;
            case NO_STABLE_VERSION:
                return DomainErrorCode.OPERATION_NOT_STABLE;
            case REVISION_ALREADY_ACTIVE:
                return DomainErrorCode.REVISION_IMMUTABLE;
            default:
                throw new IllegalArgumentException("no error code is defined for refusal " + refusal);
        }
    }

    /**
     * The inputs a replacement attempt is admitted with, fixed at admission and reused by every retry
     * and reset of that attempt.
     *
     * <p>The point of carrying them is that a replacement must not be assembled from whatever the
     * settings say at the moment the learner presses the button: an answer produced with thinking on,
     * re-generated with it off, would differ for a reason that has nothing to do with the model, and
     * the difference would be invisible in the history.
     */
    public static final class Baseline {

        public final String branchId;
        public final String messageId;
        public final PromptPurpose purpose;
        public final GenerationModel model;
        public final boolean thinkingEnabled;
        /**
         * The style the replacement runs with. Read from the topic row inside the retry/reset
         * transaction, so a switch made after the original answer still applies to the next
         * attempt — which is exactly the confirmed rule "a switch affects future answers".
         */
        public final Persona persona;

        /** The pre-persona shape; kept so existing callers keep meaning the default style. */
        public Baseline(String branchId, String messageId, PromptPurpose purpose,
                GenerationModel model, boolean thinkingEnabled) {
            this(branchId, messageId, purpose, model, thinkingEnabled, Persona.DEFAULT);
        }

        /**
         * @throws IllegalArgumentException if an id is not a canonical UUID or the purpose, model or
         *                                  persona is missing, per the error-channel rule in
         *                                  {@link Guard}
         */
        public Baseline(String branchId, String messageId, PromptPurpose purpose,
                GenerationModel model, boolean thinkingEnabled, Persona persona) {
            this.branchId = Guard.id(branchId, "branchId");
            this.messageId = Guard.id(messageId, "messageId");
            this.purpose = Guard.object(purpose, "purpose");
            this.model = Guard.object(model, "model");
            this.thinkingEnabled = thinkingEnabled;
            this.persona = Guard.object(persona, "persona");
        }

        /**
         * A new attempt at this position under the given policy version.
         *
         * <p>The new command carries a fresh id, so a replacement is a new attempt rather than a
         * re-run of the old one, and the attempt it replaces keeps its own outcome and partial
         * content. The version check is the domain's ({@link GenerationCommand#create}), not a
         * second copy of it.
         */
        public DomainResult<GenerationCommand> commandFor(String policyVersion, long now) {
            return GenerationCommand.create(branchId, messageId, purpose, policyVersion, model,
                    thinkingEnabled, persona, now);
        }
    }
}