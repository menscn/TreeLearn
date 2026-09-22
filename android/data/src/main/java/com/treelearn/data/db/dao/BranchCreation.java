package com.treelearn.data.db.dao;

import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;

import com.treelearn.core.domain.Branch;
import com.treelearn.core.domain.Guard;
import com.treelearn.data.db.DatabaseTransactionRunner;

/**
 * The one creation entry point for a non-root branch: its row, its immutable birth snapshot and the
 * pointer between them, written in a single transaction or not at all (技术方案 §5.2/§5.3/§8).
 *
 * <p><strong>Why this class exists instead of a caller making two DAO calls.</strong>
 * {@code branches.context_snapshot_id} references {@code context_snapshots} while
 * {@code context_snapshots.branch_id} references {@code branches}, so no ordering of two separate
 * writes is complete: {@link BranchDao#insert} deliberately leaves the pointer NULL, and
 * {@link BranchDao#setContextSnapshot} can only run once the snapshot row exists. A caller that
 * remembers two of the three steps leaves a row the read path refuses to rehydrate — a state the
 * database accepts and the application cannot read. This entry point is what takes "remember to
 * finish the write" away from the caller; {@link BranchDao#insert} and
 * {@link BranchDao#setContextSnapshot} stay public because the acceptance suites drive them directly,
 * but they are not how a fork is created.
 *
 * <p><strong>What enforces the invariant, and where it stops.</strong> Two things, neither of them
 * the DDL. (1) This entry point: the only production writer of a non-root branch row, and it always
 * performs all three writes inside one {@link DatabaseTransactionRunner} transaction, so a refusal
 * anywhere leaves no branch, no snapshot and no pointer. (2) The read path
 * ({@link BranchDao#findById}), which throws on a non-root row whose pointer is NULL instead of
 * handing back a provenance-less branch. The store cannot express "a non-root branch has a snapshot":
 * SQLite CHECK constraints are immediate and cannot be deferred, the snapshot row must reference the
 * branch being inserted, so at the moment the branch row is written there is nothing to point at yet.
 * A deferred foreign key or a new column would be a schema change owned by T-004, and it was
 * considered and deliberately not taken here.
 *
 * <p><strong>Two judgements this class does not make.</strong> The source-message rule ("the source
 * must belong to the branch's own topic") is judged by {@link FirstTopicCreation#sourceRefusal}, and
 * the anchor rules are judged by {@link com.treelearn.core.domain.BranchSource} /
 * {@link com.treelearn.core.domain.BranchAnchor} on the way in. Neither is re-implemented here; a
 * second copy of a judgement is how the same input ends up with two answers.
 *
 * <p>Error channel follows {@link Guard}: a missing argument, a root branch, or a snapshot row that
 * disagrees with the branch it is handed alongside is a caller bug and throws
 * {@link IllegalArgumentException}; a store refusal is a business outcome and is returned as a
 * {@link Refusal}.
 */
public final class BranchCreation {

    /** Why a fork was refused. Every value has a producer in this class. */
    public enum Refusal {

        /**
         * The branch's explicit source message or revision does not belong to the branch's own topic,
         * as judged by {@link FirstTopicCreation#sourceRefusal}. Nothing is written when this is
         * reported: the judgement runs before the first insert.
         */
        SOURCE_NOT_IN_THIS_TOPIC,

        /**
         * The store refused a write this entry point has no rule of its own for — an id already
         * taken, a missing topic, a malformed anchor. The transaction is rolled back, so no row of the
         * refused fork survives, and {@link Outcome#detail} keeps SQLite's own cause.
         */
        STORAGE_REFUSED
    }

    /** The committed fork, or the classified refusal. Exactly one of the two is non-null. */
    public static final class Outcome {

        /** The committed fork; null when refused. */
        public final Branch branch;

        /** Why the write was refused; null when committed. */
        public final Refusal refusal;

        /** What went wrong, in the terms the caller can act on; null when committed. */
        public final String detail;

        private Outcome(Branch branch, Refusal refusal, String detail) {
            this.branch = branch;
            this.refusal = refusal;
            this.detail = detail;
        }

        public static Outcome created(Branch branch) {
            return new Outcome(Guard.object(branch, "branch"), null, null);
        }

        public static Outcome refused(Refusal refusal, String detail) {
            return new Outcome(null, Guard.object(refusal, "refusal"), Guard.nonBlank(detail, "detail"));
        }

        /** @return true when the branch row, its snapshot row and the pointer are all committed */
        public boolean isCreated() {
            return refusal == null;
        }

        public boolean isRefused() {
            return refusal != null;
        }

        @Override
        public String toString() {
            return isCreated() ? "Outcome.created(" + branch.id + ")"
                    : "Outcome.refused(" + refusal + ": " + detail + ")";
        }
    }

    private final DatabaseTransactionRunner runner;

    public BranchCreation(DatabaseTransactionRunner runner) {
        this.runner = Guard.object(runner, "runner");
    }

    /**
     * Writes a non-root branch, its birth snapshot and the pointer between them, or nothing at all.
     *
     * @param branch   the fork, as built by {@code Branch.child(...)} from a completed attempt
     * @param snapshot the birth snapshot row; its {@code id} must be the id the branch's provenance
     *                 already pins and its {@code branchId} must be this branch
     * @return the committed fork, or a classified refusal. A refusal means the store holds neither the
     *         branch row, nor its snapshot, nor a pointer, so the caller may retry the same fork.
     */
    public Outcome create(SQLiteDatabase database, Branch branch, ContextSnapshotDao.Row snapshot) {
        Guard.object(database, "database");
        requireForkable(branch, snapshot);
        try {
            return runner.run(database, db -> createInside(db, branch, snapshot));
        } catch (Refused refused) {
            return refused.outcome;
        }
    }

    /**
     * The work body: one transaction, three writes, in the only order the foreign keys allow.
     *
     * <p>The cross-topic judgement runs first and inside this transaction, so a refused source writes
     * nothing at all. Judging it outside the transaction would let two writers both pass a rule that
     * only one of them still satisfies by the time the row lands — the reason
     * {@link FirstTopicCreation#sourceRefusal} documents the same caller obligation.
     */
    private static Outcome createInside(SQLiteDatabase database, Branch branch,
                                        ContextSnapshotDao.Row snapshot) {
        try {
            FirstTopicCreation.Refusal crossTopic =
                    FirstTopicCreation.sourceRefusal(database, branch.topicId, branch.source);
            if (crossTopic != null) {
                throw refuse(Outcome.refused(Refusal.SOURCE_NOT_IN_THIS_TOPIC,
                        crossTopicDetail(branch)));
            }
            BranchDao.insert(database, branch);
            ContextSnapshotDao.insert(database, snapshot);
            BranchDao.setContextSnapshot(database, branch.id, snapshot.id);
            return Outcome.created(branch);
        } catch (SQLiteException refusedByStore) {
            // One catch closes the whole write: every row of this fork went through the store, so the
            // refusal names the constraint and SQLite's own message travels on in the detail.
            throw refuse(Outcome.refused(Refusal.STORAGE_REFUSED, message(refusedByStore)));
        }
    }

    private static String crossTopicDetail(Branch branch) {
        return "the fork declares source message " + branch.source.sourceMessageId + " and revision "
                + branch.source.sourceRevisionId + ", and neither belongs to topic " + branch.topicId
                + "; a fork may only reference an answer of its own topic, because the source columns"
                + " cascade and a cross-topic pointer would let deleting one topic delete another's"
                + " branch";
    }

    private static String message(SQLiteException failure) {
        return failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
    }

    private static Refused refuse(Outcome outcome) {
        return new Refused(outcome);
    }

    /**
     * Unwinds the transaction so that a refused write commits nothing.
     *
     * <p>Never observed by a caller: {@link #create} catches it and returns the {@link Outcome} it
     * carries. It is thrown rather than returned on purpose -- {@link DatabaseTransactionRunner}
     * commits whatever a work body returns, so a refusal that came back as a value would be committed.
     * Same shape as {@link GenerationStabilizer}'s control flow, with its stack trace suppressed
     * because it is not an error.
     */
    private static final class Refused extends RuntimeException {

        private final transient Outcome outcome;

        Refused(Outcome outcome) {
            super("branch creation refused", null, false, false);
            this.outcome = outcome;
        }
    }

    /**
     * The three caller bugs refused before a transaction is opened, all of them facts the caller can
     * see and this class cannot repair.
     *
     * <p>The requirement that the snapshot id match the one the provenance pins is the quiet one:
     * {@code BranchSource} is built with a snapshot id, while the pointer column is written from a
     * second argument. If the two disagreed, the entity would claim one snapshot while the row points
     * at another, and the read path could not tell — it restores the id from the column.
     */
    private static void requireForkable(Branch branch, ContextSnapshotDao.Row snapshot) {
        Guard.object(branch, "branch");
        Guard.object(snapshot, "snapshot");
        if (branch.source == null) {
            throw new IllegalArgumentException(
                    "a root branch has no provenance and no birth snapshot; the root branch of a topic"
                            + " is written by FirstTopicCreation, not by this entry point");
        }
        if (!branch.id.equals(snapshot.branchId)) {
            throw new IllegalArgumentException("the snapshot row belongs to branch " + snapshot.branchId
                    + " but the branch handed in is " + branch.id + "; the row would name a branch this"
                    + " write never creates");
        }
        if (!snapshot.id.equals(branch.source.contextSnapshotId)) {
            throw new IllegalArgumentException("the branch's provenance pins snapshot "
                    + branch.source.contextSnapshotId + " but the snapshot row carries id "
                    + snapshot.id + "; the pointer column and the provenance would disagree, and the"
                    + " read path restores the id from the column so it could not tell");
        }
    }
}