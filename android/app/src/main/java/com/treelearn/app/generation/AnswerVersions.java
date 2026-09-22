package com.treelearn.app.generation;

import android.database.sqlite.SQLiteDatabase;

import com.treelearn.core.domain.AnswerRevision;
import com.treelearn.core.domain.DomainError;
import com.treelearn.core.domain.DomainResult;
import com.treelearn.core.domain.GenerationCommand;
import com.treelearn.core.domain.GenerationModel;
import com.treelearn.core.domain.Guard;
import com.treelearn.core.domain.Message;
import com.treelearn.core.domain.PromptPurpose;
import com.treelearn.data.db.DatabaseTransactionRunner;
import com.treelearn.data.db.dao.AnswerRevisionDao;
import com.treelearn.data.db.dao.GenerationOperationDao;
import com.treelearn.data.db.dao.MessageDao;

import java.util.Collections;
import java.util.List;

/**
 * The store side of retry, reset and version selection (technical design 5.1, 7).
 *
 * <p>Every method here reads the position from the rows rather than from whatever object the caller
 * happens to hold, decides through {@link PositionCommands}, and only then writes. A refusal is
 * unwound out of the transaction rather than returned from inside it, for the reason
 * {@code GenerationStabilizer} states: the runner commits whatever a work body returns, so a refusal
 * that returned would be one statement-order change away from committing partial work.
 *
 * <p><strong>What a refusal costs.</strong> Nothing is written on the refusal path, so a retry or a
 * reset that is refused leaves the operation count, the message rows and the displayed version
 * exactly as they were - which is what lets the UI re-enable the button after the in-flight attempt
 * ends, instead of repairing state.
 */
public final class AnswerVersions {

    /**
     * The usage an answer position runs under. It is a constant because 5.1 defines the conversation
     * position as a learning answer: a replacement of one is the same usage, not a new one.
     */
    private static final PromptPurpose ANSWER_PURPOSE = PromptPurpose.LEARNING_ANSWER;

    private final DatabaseTransactionRunner runner;

    public AnswerVersions(DatabaseTransactionRunner runner) {
        if (runner == null) {
            throw new IllegalArgumentException("runner must not be null");
        }
        this.runner = runner;
    }

    /**
     * Whether - and with which command - the failed attempt at this position may be replaced.
     *
     * <p>Writes nothing: the command is the new attempt, and the coordinator writes its operation row
     * when it submits it. That is what keeps "a retry adds exactly one operation" true by
     * construction instead of by a second insert here.
     */
    public DomainResult<GenerationCommand> retry(SQLiteDatabase database, String messageId,
            GenerationModel model, boolean thinkingEnabled, String policyVersion, long now) {
        return replacement(database, messageId, model, thinkingEnabled, policyVersion, now, false);
    }

    /** As {@link #retry}, but only when the position already displays a stable version. */
    public DomainResult<GenerationCommand> reset(SQLiteDatabase database, String messageId,
            GenerationModel model, boolean thinkingEnabled, String policyVersion, long now) {
        return replacement(database, messageId, model, thinkingEnabled, policyVersion, now, true);
    }

    /**
     * Switches which stored version this position displays.
     *
     * <p>Display pointer only. A branch keeps the version it was forked from in its own
     * {@code source_revision_id} column, which nothing here touches and no foreign key cascades, so a
     * child branch keeps showing the reasoning and answer it was created from after this switch.
     *
     * @return the revision now displayed, or a refusal naming why it was not switched
     */
    public DomainResult<AnswerRevision> selectRevision(SQLiteDatabase database, String messageId,
            String revisionId, long now) {
        Guard.id(messageId, "messageId");
        Guard.id(revisionId, "revisionId");
        Guard.epochMillis(now, "now");
        try {
            return runner.run(database, work -> {
                Message position = MessageDao.findById(work, messageId);
                AnswerRevision target = position == null ? null
                        : AnswerRevisionDao.findById(work, revisionId);
                PositionCommands.Refusal refusal = PositionCommands.selectRefusal(position, revisionId,
                        target == null ? null : target.messageId, attemptRunning(work, position));
                if (refusal != null) {
                    throw new Refused(errorFor(refusal));
                }
                DomainResult<Void> switched = position.activateRevision(revisionId);
                if (switched.isFailure()) {
                    throw new Refused(switched.error());
                }
                MessageDao.updateActiveReferences(work, position);
                return DomainResult.ok(target);
            });
        } catch (Refused refusal) {
            return DomainResult.fail(refusal.error);
        }
    }

    /**
     * The stored versions of one position, oldest first, so a caller can list them without deciding
     * the order itself.
     *
     * <p>Read-only and outside any transaction. An unknown position yields an empty list rather than
     * a failure: "this position has no version yet" and "this position does not exist" are told apart
     * by {@link #retry}, which is where acting on the answer matters.
     */
    public DomainResult<List<AnswerRevision>> versions(SQLiteDatabase database, String messageId) {
        Guard.id(messageId, "messageId");
        return DomainResult.ok(Collections.unmodifiableList(
                AnswerRevisionDao.findByMessage(database, messageId)));
    }

    private DomainResult<GenerationCommand> replacement(SQLiteDatabase database, String messageId,
            GenerationModel model, boolean thinkingEnabled, String policyVersion, long now,
            boolean requiresStableVersion) {
        Guard.id(messageId, "messageId");
        Guard.object(model, "model");
        Guard.epochMillis(now, "now");
        try {
            return runner.run(database, work -> {
                Message position = MessageDao.findById(work, messageId);
                boolean running = attemptRunning(work, position);
                PositionCommands.Refusal refusal = requiresStableVersion
                        ? PositionCommands.resetRefusal(position, running)
                        : PositionCommands.retryRefusal(position, running);
                if (refusal != null) {
                    throw new Refused(errorFor(refusal));
                }
                PositionCommands.Baseline baseline = new PositionCommands.Baseline(position.branchId,
                        position.id, ANSWER_PURPOSE, model, thinkingEnabled);
                DomainResult<GenerationCommand> command = baseline.commandFor(policyVersion, now);
                if (command.isFailure()) {
                    throw new Refused(command.error());
                }
                return command;
            });
        } catch (Refused refusal) {
            return DomainResult.fail(refusal.error);
        }
    }

    /**
     * Whether an attempt is in flight for this position.
     *
     * <p>Asked of the store rather than of the position's own pointer, because the store is where the
     * answer is enforced: a partial unique index allows at most one active row per branch, and 7
     * admits one attempt per branch, so an active attempt anywhere in the branch means this position
     * cannot start one.
     */
    private static boolean attemptRunning(SQLiteDatabase database, Message position) {
        return position != null
                && GenerationOperationDao.findActiveByBranch(database, position.branchId) != null;
    }

    private static DomainError errorFor(PositionCommands.Refusal refusal) {
        return DomainError.of(PositionCommands.codeFor(refusal), detailFor(refusal));
    }

    private static String detailFor(PositionCommands.Refusal refusal) {
        switch (refusal) {
            case MESSAGE_NOT_FOUND:
                return "no messages row carries that id";
            case NOT_AN_AI_POSITION:
                return "only an AI position runs an attempt or displays a version";
            case ATTEMPT_ALREADY_RUNNING:
                return "an attempt is already running in this branch";
            case NO_STABLE_VERSION:
                return "the position displays no stable version, so there is nothing to regenerate";
            case REVISION_NOT_OF_THIS_POSITION:
                return "the named revision is not a version of this position";
            case REVISION_ALREADY_ACTIVE:
                return "the named revision is already the version this position displays";
            default:
                throw new IllegalArgumentException("no detail is defined for refusal " + refusal);
        }
    }

    /**
     * Carries the failure out through the unwinding of the transaction. It deliberately holds the
     * {@link DomainError} rather than a {@code DomainResult}, so each catch site rebuilds a result of
     * its own type without an unchecked cast.
     */
    private static final class Refused extends RuntimeException {

        private final transient DomainError error;

        Refused(DomainError error) {
            super("refused", null, false, false);
            this.error = error;
        }
    }
}