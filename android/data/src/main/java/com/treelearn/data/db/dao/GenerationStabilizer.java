package com.treelearn.data.db.dao;

import android.database.sqlite.SQLiteConstraintException;
import android.database.sqlite.SQLiteDatabase;

import com.treelearn.core.domain.AnswerRevision;
import com.treelearn.core.domain.DomainErrorCode;
import com.treelearn.core.domain.DomainResult;
import com.treelearn.core.domain.GenerationState;
import com.treelearn.core.domain.Guard;
import com.treelearn.data.db.DatabaseTransactionRunner;

/**
 * Freezes a finished attempt into a stable revision, as one atomic act.
 *
 * <p><strong>This is not a DAO.</strong> It is the one cross-table use case in the
 * {@code ...db.dao} package, and it owns the transaction boundary rather than asking a caller to
 * arrange one — the pair it writes (a revision row and the attempt moved to {@code STABLE}) is only
 * meaningful together, so leaving the boundary to the caller would make the intermediate state the
 * caller's bug to avoid instead of the entry point's job to prevent. It is the entry point the
 * gateway's accepted callback can wait on synchronously: the attempt is only {@code STABLE} once the
 * revision is durable, and any refusal is reported as a {@link DomainResult} failure rather than as an
 * exception thrown through the network layer.
 *
 * <p><strong>The pinned policy version is copied from the row.</strong> Both the version and the
 * content of the revision come from the stored operation row, and the version is read — never
 * re-derived from the command, a live registry or "the current version for this purpose". Those
 * alternatives produce the same string while the row is intact, which is exactly why the fixture that
 * pins this behaviour has to tamper the row: prompt design §3.2 makes an answer reproducible only if
 * it records the version that actually produced it, and a re-derived version would silently rewrite
 * history the first time the registry moved on.
 *
 * <p><strong>Caller obligation: the attempt's deltas must already be persisted.</strong> The revision
 * is built from the row, so a delta still sitting in memory would be frozen as a truncated answer.
 * The flush that has to precede this call belongs to the coordinator's batching window (T-008) and
 * cannot be checked here: the row is the only view this entry point has, and nothing in it
 * distinguishes a complete answer from one whose tail has not been written yet.
 *
 * <p><strong>The non-empty-answer rule is judged twice, and the second judgement is declared rather
 * than smuggled in.</strong> The domain decides it in {@code GenerationOperation.stabilize}, where the
 * buffers the stream filled are the authority. This entry point works from the persisted row and has
 * to decide it again, because a row whose answer buffer is empty cannot become a revision —
 * {@code AnswerRevision.create} would throw a caller-bug {@link IllegalArgumentException} for what is
 * really a condition of the row, and letting that cross the network layer is precisely what "returns
 * an explicit error instead of a bare exception" forbids. The two judgements agree for every input
 * (same code, {@link DomainErrorCode#OPERATION_NOT_COMPLETABLE}, same trigger), so no caller can
 * observe two different outcomes; the store's {@code CHECK (length(answer_content) > 0)} is the last
 * backstop, for writers that bypass both.
 *
 * <p><strong>Arguments follow the {@link Guard} error-channel rule</strong>, because a malformed call
 * here would otherwise be reported as a business refusal by a store that simply found no row: a
 * non-UUID id, a revision number below 1 or a negative timestamp throws.
 */
public final class GenerationStabilizer {

    private final DatabaseTransactionRunner runner;

    public GenerationStabilizer(DatabaseTransactionRunner runner) {
        if (runner == null) {
            throw new IllegalArgumentException("runner must not be null");
        }
        this.runner = runner;
    }

    /**
     * Writes the revision and moves the attempt to {@code STABLE}, or neither.
     *
     * @param operationId the attempt to freeze
     * @param revisionNo the position's next revision number, read from
     *        {@link AnswerRevisionDao#nextRevisionNo} rather than remembered: an attempt cannot know
     *        its position's sequence. A number that is already used is refused by the unique index and
     *        reported as {@link DomainErrorCode#SEQUENCE_OUT_OF_ORDER} — the caller takes the next free
     *        number instead of the entry point overwriting a revision somebody has already seen
     * @param now the completion time
     * @return the new revision, or a failure: {@code OPERATION_NOT_COMPLETABLE} when no row carries
     *         {@code operationId} or it has no final answer yet, {@code ILLEGAL_STATE_TRANSITION} when
     *         the attempt is already terminal or has not reached a state that may become
     *         {@code STABLE}, {@code SEQUENCE_OUT_OF_ORDER} when the number is taken, or whatever
     *         {@link AnswerRevision#create} refuses about the row's version.
     */
    public DomainResult<AnswerRevision> stabilize(SQLiteDatabase database, String operationId,
                                                  int revisionNo, long now) {
        Guard.id(operationId, "operationId");
        Guard.atLeast(revisionNo, 1, "revisionNo");
        Guard.epochMillis(now, "createdAt");
        try {
            return runner.run(database,
                    work -> stabilizeInside(work, operationId, revisionNo, now));
        } catch (Refused refused) {
            // The unwinding is the point. Returning the failure from inside the work body would
            // *commit* the transaction — DatabaseTransactionRunner commits whatever a work body
            // returns — and a refusal that committed is one statement-order change away from leaving
            // a revision beside an attempt that is not STABLE.
            return refused.result;
        }
    }

    private static DomainResult<AnswerRevision> stabilizeInside(SQLiteDatabase database,
                                                                String operationId, int revisionNo,
                                                                long now) {
        GenerationOperationRow row = GenerationOperationDao.findById(database, operationId);
        if (row == null) {
            throw refuse(DomainResult.<AnswerRevision>fail(DomainErrorCode.OPERATION_NOT_COMPLETABLE,
                    "no generation_operations row carries id " + operationId + "; the attempt is gone, "
                            + "because deleting its branch or message cascades, so there is nothing "
                            + "left to freeze"));
        }
        // canTransitionTo is the domain's own predicate, not a copy of it: STABLE is reachable only
        // from STREAMING_ANSWER, and terminal states accept nothing at all — which covers "already
        // finished" and "never reached the provider" in one judgement.
        if (!row.state.canTransitionTo(GenerationState.STABLE)) {
            throw refuse(DomainResult.<AnswerRevision>fail(
                    DomainErrorCode.ILLEGAL_STATE_TRANSITION, row.state.isTerminal()
                            ? "the attempt already ended as " + row.state + "; a late stabilization "
                                    + "must not rewrite an outcome that has already been reported"
                            : "the attempt is still " + row.state + "; only an attempt that has "
                                    + "reached the answer phase can be frozen into a revision"));
        }
        if (row.partialAnswer.trim().isEmpty()) {
            throw refuse(DomainResult.<AnswerRevision>fail(
                    DomainErrorCode.OPERATION_NOT_COMPLETABLE,
                    "the attempt has no final answer to freeze (" + row.partialAnswer.length()
                            + " character(s) in the row); §9.3 requires a non-empty final answer, and "
                            + "the partial content is kept so the attempt can be retried in place"));
        }
        DomainResult<AnswerRevision> created = AnswerRevision.create(row.messageId, revisionNo,
                row.partialReasoning, row.partialAnswer, row.model, row.thinkingEnabled,
                row.promptPolicyVersion, now);
        if (created.isFailure()) {
            throw refuse(created);
        }
        AnswerRevision revision = created.value();
        // The two writes are ordered state-first, and the reason is worth stating because the
        // opposite order looks equally reasonable: inside a transaction that unwinds on refusal the
        // two are equivalent, so the order is chosen for what it makes *observable*. If this entry
        // point ever went back to reporting a refusal by returning from the work body — which
        // commits, since DatabaseTransactionRunner commits whatever a work body returns — then a
        // taken revision number would leave the attempt marked STABLE with no revision beside it.
        // Written in the other order, the same defect would be invisible: nothing would have been
        // written at all. The acceptance suite's taken-number case pins exactly this, so the pairing
        // of order and unwinding is tested rather than asserted.
        GenerationOperationDao.markStable(database, operationId, now);
        try {
            AnswerRevisionDao.insert(database, revision);
        } catch (SQLiteConstraintException refusedByStore) {
            if (ConstraintFailures.isUniqueOn(refusedByStore,
                    ConstraintFailures.REVISIONS_MESSAGE_NO)) {
                throw refuse(DomainResult.<AnswerRevision>fail(
                        ConstraintFailures.revisionNumberTaken(row.messageId, revisionNo)));
            }
            // Not a refusal this entry point can name. Rethrown so it stays the caller's exception
            // instead of being reported as a business outcome it is not. No such refusal is
            // reachable from this schema today — the CHECK on answer_content cannot fire because the
            // blank-answer refusal above is strictly stronger, and the message foreign key cannot be
            // violated because the operation row's own foreign key already guarantees the message
            // exists — so this branch is defensive, not dead: it is what keeps a schema change from
            // being silently reported as "that revision number was taken".
            throw refusedByStore;
        }
        return DomainResult.ok(revision);
    }

    private static Refused refuse(DomainResult<AnswerRevision> result) {
        return new Refused(result);
    }

    /**
     * Unwinds the transaction so that a refused stabilization commits nothing.
     *
     * <p>Never observed by a caller: {@link #stabilize} catches it and returns the {@link DomainResult}
     * it carries. It is a RuntimeException rather than a checked one so that the work body can stay a
     * lambda, and its stack trace is suppressed because it is control flow rather than an error.
     */
    private static final class Refused extends RuntimeException {

        private final transient DomainResult<AnswerRevision> result;

        Refused(DomainResult<AnswerRevision> result) {
            super("stabilization refused", null, false, false);
            this.result = result;
        }
    }
}
