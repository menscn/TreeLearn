package com.treelearn.web.storage;

import com.treelearn.core.domain.AnswerRevision;
import com.treelearn.core.domain.DomainErrorCode;
import com.treelearn.core.domain.DomainResult;
import com.treelearn.core.domain.GenerationState;
import com.treelearn.core.domain.Guard;
import com.treelearn.core.domain.Message;
import java.sql.Connection;

/**
 * Freezes a normally finished attempt into a stable revision, as one atomic act (technical design §7,
 * §9.3; task T-2026-10-10-024).
 *
 * <p>Semantic reference — the Android {@code com.treelearn.data.db.dao.GenerationStabilizer} it
 * mirrors. <strong>This is not a DAO.</strong> It is the one cross-table use case that owns the
 * transaction boundary, because the four writes it performs are only meaningful together: the immutable
 * revision row, the attempt moved to {@code STABLE}, the position repointed at that revision, and the
 * branch's stable sequence advanced. Leaving the boundary to a caller would make "STABLE with no
 * revision", "a revision no position displays" and "an unread gap with no answer" the caller's bug to
 * avoid instead of this entry point's job to prevent.
 *
 * <p><strong>The pinned policy version and the content come from the stored attempt row.</strong> They
 * are read, never re-derived from a live registry or "the current version"; the revision must record
 * what actually produced it (prompt design §3.2), and only a row-level read can promise that.
 *
 * <p><strong>Caller obligation: the deltas must already be persisted.</strong> The revision is built
 * from the stored row, so an in-memory delta that has not been flushed would be frozen as a truncated
 * answer. The flush belongs to the coordinator's batching window (T-024) and is its obligation.
 *
 * <p><strong>{@code STABLE} is written only through {@link OperationStore#markStable(Connection, String,
 * long)}</strong>, which re-guards "reachable only from {@code STREAMING_ANSWER}" and "the final answer
 * is non-empty" and is the sole storage entry to that state. A refusal is returned as a
 * {@link DomainResult} failure rather than thrown through the gateway, so the coordinator reports it as
 * a failed attempt instead of a crash. The store's own {@code CHECK (length(answer_content) > 0)} and
 * the terminal-immutability trigger are the last backstops.
 */
public final class GenerationStabilizer {

    private final JdbcSupport jdbc;
    private final OperationStore operations;
    private final AnswerRevisionStore revisions;
    private final MessageStore messages;
    private final BranchStore branches;

    public GenerationStabilizer(JdbcSupport jdbc, OperationStore operations,
            AnswerRevisionStore revisions, MessageStore messages, BranchStore branches) {
        this.jdbc = Guard.object(jdbc, "jdbc");
        this.operations = Guard.object(operations, "operations");
        this.revisions = Guard.object(revisions, "revisions");
        this.messages = Guard.object(messages, "messages");
        this.branches = Guard.object(branches, "branches");
    }

    /**
     * Writes the revision, moves the attempt to {@code STABLE}, repoints the position and advances the
     * branch — or none of them.
     *
     * @return the new revision, or a failure: {@code OPERATION_NOT_COMPLETABLE} when no row carries
     *         {@code operationId} or the final answer is empty, {@code ILLEGAL_STATE_TRANSITION} when
     *         the attempt is already terminal or never reached the answer phase, or whatever
     *         {@link AnswerRevision#create} refuses about the row's version
     */
    public DomainResult<AnswerRevision> stabilize(String operationId, long now) {
        Guard.id(operationId, "operationId");
        Guard.epochMillis(now, "createdAt");
        try {
            return jdbc.runInTransaction(connection -> stabilizeInside(connection, operationId, now));
        } catch (Refused refused) {
            // The unwinding is the point: returning a failure from inside the work body would let the
            // transaction commit a half-applied stabilization.
            return refused.result;
        }
    }

    private DomainResult<AnswerRevision> stabilizeInside(Connection connection, String operationId,
            long now) {
        OperationStore.Operation operation = operations.byId(connection, operationId).orElseThrow(
                () -> refuse(DomainResult.fail(DomainErrorCode.OPERATION_NOT_COMPLETABLE,
                        "no generation_operations row carries id " + operationId + "; the attempt is"
                                + " gone, because deleting its branch or message cascades")));
        if (!operation.state.canTransitionTo(GenerationState.STABLE)) {
            throw refuse(DomainResult.fail(DomainErrorCode.ILLEGAL_STATE_TRANSITION,
                    operation.state.isTerminal()
                            ? "the attempt already ended as " + operation.state + "; a late "
                                    + "stabilization must not rewrite an outcome already reported"
                            : "the attempt is still " + operation.state + "; only an attempt that has"
                                    + " reached the answer phase can be frozen into a revision"));
        }
        if (operation.partialAnswer.trim().isEmpty()) {
            throw refuse(DomainResult.fail(DomainErrorCode.OPERATION_NOT_COMPLETABLE,
                    "the attempt has no final answer to freeze (" + operation.partialAnswer.length()
                            + " character(s) in the row); §9.3 requires a non-empty final answer, and"
                            + " the partial content is kept so the attempt can be retried in place"));
        }
        int revisionNo = revisions.nextRevisionNo(connection, operation.messageId);
        DomainResult<AnswerRevision> created = AnswerRevision.create(operation.messageId, revisionNo,
                operation.partialReasoning, operation.partialAnswer, operation.model,
                operation.thinkingEnabled, operation.promptPolicyVersion, now);
        if (created.isFailure()) {
            throw refuse(created);
        }
        AnswerRevision revision = created.value();
        // State-first: STABLE only once the revision beside it is about to be written, and it re-guards.
        operations.markStable(connection, operationId, now);
        revisions.insertRevision(connection, revision);
        activate(connection, operation, revision.id, now);
        return DomainResult.ok(revision);
    }

    /**
     * Repoints the position at the new revision and advances the branch's stable sequence, in the same
     * transaction: the position stops showing the attempt and starts showing the answer, and the unread
     * gap counts it, as one visible change.
     */
    private void activate(Connection connection, OperationStore.Operation operation, String revisionId,
            long now) {
        Message position = messages.get(connection, operation.messageId).orElseThrow(
                () -> refuse(DomainResult.fail(DomainErrorCode.OPERATION_NOT_COMPLETABLE,
                        "the message " + operation.messageId + " the attempt answers into is gone;"
                                + " there is nothing to repoint at the new revision")));
        position.clearActiveOperation();
        DomainResult<Void> activated = position.activateRevision(revisionId);
        if (activated.isFailure()) {
            throw refuse(DomainResult.fail(activated.error()));
        }
        messages.persistActiveReferences(connection, position);
        branches.incrementLatestStable(connection, operation.branchId, now);
    }

    private static Refused refuse(DomainResult<AnswerRevision> result) {
        return new Refused(result);
    }

    /**
     * Unwinds the transaction so that a refused stabilization commits nothing. Never observed by a
     * caller: {@link #stabilize} catches it and returns the {@link DomainResult} it carries. A
     * RuntimeException so the work body can stay a lambda, with its stack trace suppressed because it is
     * control flow rather than an error.
     */
    private static final class Refused extends RuntimeException {

        private final transient DomainResult<AnswerRevision> result;

        Refused(DomainResult<AnswerRevision> result) {
            super("stabilization refused", null, false, false);
            this.result = result;
        }
    }
}
