package com.treelearn.data.db.dao;

import android.database.Cursor;
import android.database.sqlite.SQLiteConstraintException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;

import com.treelearn.core.domain.Branch;
import com.treelearn.core.domain.BranchSource;
import com.treelearn.core.domain.GenerationOperation;
import com.treelearn.core.domain.Guard;
import com.treelearn.core.domain.Message;
import com.treelearn.core.domain.Topic;
import com.treelearn.data.db.DatabaseTransactionRunner;
import com.treelearn.data.db.TreeLearnSchema;

/**
 * The first-topic write: five row groups in one transaction, or none at all (技术方案 §8).
 *
 * <p><strong>This is not a DAO.</strong> It is the one cross-table use case of this package, and it
 * owns the transaction boundary for the same reason {@link GenerationStabilizer} does: "there is a
 * topic but no root branch", "there is a branch but no attempt" and "there is a topic but the local
 * operation UUID never landed" are all states the recovery path (T-008) would have to reason about,
 * and §8 removes them by writing {@code topics} + the root {@code branches} row + both
 * {@code messages} positions + the {@code generation_operations} row as a unit. It is the entry point
 * the gateway's accepted callback can wait on synchronously: §8 says the first SSE byte must not be
 * read before this commit and that a failure here is reported as a local persistence error.
 *
 * <p><strong>Order.</strong> Topic, then its root branch, then the learner's question, then the AI
 * position, then the attempt — the order §8 lists, and the order the foreign keys allow (each row
 * references one written before it). Two of those references are circular by design
 * ({@code messages.active_operation_id} → {@code generation_operations.id} and
 * {@code branches.context_snapshot_id} → {@code context_snapshots.id}), so the attempt is written
 * after the positions it belongs to and the caller binds it afterwards through
 * {@link MessageDao#updateActiveReferences}.
 *
 * <p><strong>No snapshot is written, and that is the point of the first topic.</strong> A birth
 * snapshot belongs to a branch that was forked from an existing answer (§5.3), and the first topic
 * has no parent branch to snapshot. Its root branch therefore keeps
 * {@code context_snapshot_id IS NULL} along with the source columns, which the store's CHECK also
 * requires of a root row. The fork path's two-step write (branch → snapshot row → pointer) belongs to
 * T-009/T-2026-09-21-024 and is deliberately absent here: writing a snapshot to make this flow "look
 * complete" would invent provenance the learner never created.
 *
 * <p><strong>The cross-topic source rule has one home: {@link #sourceRefusal}.</strong> T-004 handed
 * this task the rule that a branch's explicit source message and revision must belong to the branch's
 * own topic: {@code branches.source_message_id} and {@code source_revision_id} are {@code ON DELETE
 * CASCADE}, so a row that points at another topic's answer would make "delete topic A" take topic B's
 * branch with it. No CHECK can express a cross-table rule, so it is judged on the write path, inside
 * the same transaction as the write — a check outside the transaction could be overtaken between the
 * read and the insert. The judgement is a public entry point rather than a private helper because the
 * fork-creation path (T-009) must call the same one instead of writing a second copy of the rule.
 *
 * <p><strong>What the first-topic flow can and cannot exercise.</strong> A first topic's branch is
 * its root, and a root branch carries no provenance — the store's CHECK refuses one that does. So the
 * first-topic flow exercises the "no source" arm of the judgement; a branch that does carry
 * provenance can never be committed by this entry point, because its source would have to be inside
 * the topic being created and that topic's rows do not exist yet (this very call inserts them). The
 * judgement still runs on the branch step rather than being assumed away, so a caller that hands in a
 * forked branch is refused with a named reason instead of having its provenance quietly dropped:
 * {@link BranchDao#insert} writes no {@code context_snapshot_id}, so a half-branch is exactly what an
 * unchecked write here would produce.
 *
 * <p><strong>Failures are returned, not thrown.</strong> Every store refusal is reported as an
 * {@link Outcome} carrying a {@link Refusal}, because §8 requires the accepted callback to receive a
 * local persistence error and keep its connection closed rather than to catch an exception from the
 * network layer. Two of the refusals are named facts this write path knows ({@link
 * Refusal#TOPIC_ID_ALREADY_EXISTS}, {@link Refusal#SOURCE_MESSAGE_NOT_IN_TOPIC}); anything else the
 * store refuses becomes {@link Refusal#STORAGE_REFUSED} with SQLite's own message kept verbatim in
 * the detail, which is a transport classification and not a business code dressed onto a caller bug.
 * Argument shape follows the same split as {@link Guard}: a missing or null argument is a caller bug
 * and throws {@link IllegalArgumentException}.
 */
public final class FirstTopicCreation {

    /**
     * {@code SQLITE_CONSTRAINT_PRIMARYKEY}, measured on API 36 rather than recalled.
     *
     * <p>{@link ConstraintFailures} declares the codes its own call sites name; this one belongs to
     * this call site, and the raw probe is
     * {@code artifacts/evidence/T-025-constraint-code-probe.txt}: inserting a second row with an
     * existing {@code topics.id} failed with {@code UNIQUE constraint failed: topics.id (code 1555
     * SQLITE_CONSTRAINT_PRIMARYKEY)}. A primary-key refusal is <em>not</em> the unique-index code
     * ({@code 2067}) that the partial indexes of this schema produce, which is why the two are
     * distinguished here instead of sharing one constant.
     */
    private static final int CODE_PRIMARY_KEY = 1555;

    /**
     * The constraint identity SQLite names when a second row claims an existing topic id, assembled
     * from the schema constants so a renamed column cannot leave this match pointing at a name that
     * no longer exists. Matched together with {@link #CODE_PRIMARY_KEY}: a NOT NULL or CHECK refusal
     * can name the same column, and reporting one of those as "this topic already exists" would tell
     * the caller to stop retrying when the row is simply malformed.
     *
     * <p>The prefix is spelled the way <em>SQLite</em> spells it, lower case and all: Android reports
     * the refusal as {@code UNIQUE constraint failed: topics.id (code 1555 ...)}, measured rather
     * than recalled ({@code artifacts/evidence/T-025-constraint-code-probe.txt}). The match is
     * case-sensitive on purpose: an upper-cased copy of this line compiles, reads correctly and never
     * matches, which silently downgrades every duplicate-topic refusal to {@link
     * Refusal#STORAGE_REFUSED} - the exact defect the duplicate-id case caught.
     */
    private static final String TOPICS_ID_UNIQUE =
            "UNIQUE constraint failed: " + TreeLearnSchema.Topics.TABLE + "."
                    + TreeLearnSchema.Topics.ID;

    /** Why a first-topic write was refused. Every value has a producer in this class. */
    public enum Refusal {

        /** A row already carries this topic id: §8 forbids forming a second root for one command. */
        TOPIC_ID_ALREADY_EXISTS,

        /**
         * The branch's explicit source message or revision does not belong to the target topic: the
         * cross-topic rule T-004 handed to this write path.
         */
        SOURCE_MESSAGE_NOT_IN_TOPIC,

        /**
         * The store refused a write this entry point has no business rule for. §8 calls this a local
         * persistence error; the detail keeps SQLite's own message.
         */
        STORAGE_REFUSED
    }

    /** The committed topic, or the classified refusal. Exactly one of the two is non-null. */
    public static final class Outcome {

        /** The committed topic; null when refused. Its id is what the caller hands to the UI. */
        public final Topic topic;

        /** Why the write was refused; null when committed. */
        public final Refusal refusal;

        /** What went wrong, in the terms the caller can act on; null when committed. */
        public final String detail;

        private Outcome(Topic topic, Refusal refusal, String detail) {
            this.topic = topic;
            this.refusal = refusal;
            this.detail = detail;
        }

        public static Outcome created(Topic topic) {
            return new Outcome(Guard.object(topic, "topic"), null, null);
        }

        public static Outcome refused(Refusal refusal, String detail) {
            return new Outcome(null, Guard.object(refusal, "refusal"),
                    Guard.nonBlank(detail, "detail"));
        }

        /** @return true when the five row groups are committed */
        public boolean isCreated() {
            return refusal == null;
        }

        public boolean isRefused() {
            return refusal != null;
        }

        @Override
        public String toString() {
            return isCreated() ? "Outcome.created(" + topic.id + ")"
                    : "Outcome.refused(" + refusal + ": " + detail + ")";
        }
    }

    private final DatabaseTransactionRunner runner;

    public FirstTopicCreation(DatabaseTransactionRunner runner) {
        this.runner = Guard.object(runner, "runner");
    }

    /**
     * Writes the first topic's five row groups, or nothing.
     *
     * @param topic the new topic; the caller has already built it through the {@code :core} factory
     * @param branch the topic's root branch, as built by {@code Branch.root(topicId, now)}
     * @param userMessage the learner's question, at the first ordinal of that branch
     * @param aiMessage the answer position the attempt is for
     * @param operation the staged attempt, built from the command that pinned its policy version
     * @return the committed topic, or a classified refusal. A refusal means no row of this topic
     *         survives, so the caller may retry the same topic id with a corrected request.
     */
    public Outcome create(SQLiteDatabase database, Topic topic, Branch branch, Message userMessage,
                          Message aiMessage, GenerationOperation operation) {
        requireArguments(database, topic, branch, userMessage, aiMessage, operation);
        try {
            return runner.run(database, db -> createInside(db, topic, branch, userMessage, aiMessage,
                    operation));
        } catch (Refused refused) {
            return refused.outcome;
        }
    }

    /**
     * Judges whether a branch's explicit source may be written into {@code topicId}.
     *
     * <p><strong>Caller obligation, which this method cannot observe:</strong> it must be called
     * inside the same transaction as the branch write. The judgement is a read; run outside the
     * transaction that inserts the row, two writers can pass it concurrently and the losing row still
     * lands. Nothing in the signature can enforce that, which is why the only production caller today
     * ({@link #create}) reaches it from inside its work body and T-009 must do the same.
     *
     * @param source the provenance the caller explicitly provided, or null for a branch without one
     * @return null when the source may be written, otherwise the refusal. A source that no row
     *         carries is refused too: "not in this topic" is the honest answer for it, and letting it
     *         through would only move the failure to the foreign key, whose message names no rule.
     */
    public static Refusal sourceRefusal(SQLiteDatabase database, String topicId, BranchSource source) {
        Guard.object(database, "database");
        Guard.nonBlank(topicId, "topicId");
        if (source == null) {
            return null;
        }
        return decideSourceRefusal(topicId,
                owningTopicOfMessage(database, source.sourceMessageId),
                owningTopicOfRevision(database, source.sourceRevisionId));
    }

    /**
     * The cross-topic rule itself, as a function of the three facts the lookups can produce.
     *
     * <p>Split out of {@link #sourceRefusal} because the rule is the only defence of something no
     * CHECK can express, so it must be reachable without a database. {@code source_message_id} and
     * {@code source_revision_id} cascade; a row pointing at another topic's answer therefore lets
     * "delete topic A" take topic B's branch with it, and the foreign key cannot stand in for this
     * judgement: it is satisfied by <em>any</em> existing message, including one of another topic.
     * The decision is pinned off-device by {@code FirstTopicCreationRulesTest}.
     *
     * @param owningMessageTopic the topic owning the source message, or null when no row carries it
     * @param owningRevisionTopic the topic owning the source revision, or null when no row carries it
     * @return null when the source may be written, otherwise the refusal. An owner that no row
     *         carries is refused rather than deferred to the foreign key, whose message names no rule.
     */
    static Refusal decideSourceRefusal(String topicId, String owningMessageTopic,
                                       String owningRevisionTopic) {
        Guard.nonBlank(topicId, "topicId");
        boolean messageInsideTopic = topicId.equals(owningMessageTopic);
        boolean revisionInsideTopic = topicId.equals(owningRevisionTopic);
        return messageInsideTopic && revisionInsideTopic ? null
                : Refusal.SOURCE_MESSAGE_NOT_IN_TOPIC;
    }

    private static Outcome createInside(SQLiteDatabase database, Topic topic, Branch branch,
                                        Message userMessage, Message aiMessage,
                                        GenerationOperation operation) {
        try {
            TopicDao.insert(database, topic);
            Refusal sourceRefusal = sourceRefusal(database, topic.id, branch.source);
            if (sourceRefusal != null) {
                throw refuse(Outcome.refused(sourceRefusal, sourceDetail(topic, branch)));
            }
            BranchDao.insert(database, branch);
            MessageDao.insert(database, userMessage);
            MessageDao.insert(database, aiMessage);
            GenerationOperationDao.insert(database, operation);
            return Outcome.created(topic);
        } catch (SQLiteException refusedByStore) {
            // Every row group is written through the store, so this one catch closes the whole write.
            // Nothing is swallowed: the refusal names the constraint and SQLite's own message travels
            // on to the caller in the detail.
            throw refuse(Outcome.refused(classify(refusedByStore), message(refusedByStore)));
        }
    }

    /** Separates "this topic id is taken" from refusals this entry point cannot name. */
    private static Refusal classify(SQLiteException refusedByStore) {
        if (!(refusedByStore instanceof SQLiteConstraintException)) {
            return Refusal.STORAGE_REFUSED;
        }
        return classifyStorageRefusal(
                ConstraintFailures.extendedCode((SQLiteConstraintException) refusedByStore),
                message(refusedByStore));
    }

    /**
     * The classification itself, as a function of the two things a store refusal carries.
     *
     * <p>Both halves are needed and neither is enough. The extended code alone is not a name: this
     * schema produces {@code 2067} both for a taken revision number and for a second in-flight
     * attempt, and {@code 1555} for every primary key in the database. The sentence alone is exactly
     * how an earlier revision of this class went wrong - it matched an upper-cased copy of SQLite's
     * lower-case text, which compiled, read correctly and never matched, so every duplicate topic
     * reached the caller as an unnamed storage failure. Pinned off-device by
     * {@code FirstTopicCreationRulesTest}.
     *
     * @param extendedCode what {@link ConstraintFailures#extendedCode} read, or 0 for "carried none"
     * @param storeMessage SQLite's refusal message, verbatim
     */
    static Refusal classifyStorageRefusal(int extendedCode, String storeMessage) {
        Guard.object(storeMessage, "storeMessage");
        if (extendedCode == CODE_PRIMARY_KEY && storeMessage.contains(TOPICS_ID_UNIQUE)) {
            return Refusal.TOPIC_ID_ALREADY_EXISTS;
        }
        return Refusal.STORAGE_REFUSED;
    }

    private static String sourceDetail(Topic topic, Branch branch) {
        return "the branch declares source message " + branch.source.sourceMessageId + " and revision "
                + branch.source.sourceRevisionId + ", and neither belongs to topic " + topic.id
                + "; a fork may only reference an answer of its own topic, because the source columns"
                + " cascade and a cross-topic pointer would let deleting one topic delete another's"
                + " branch";
    }

    /** @return the topic owning this message position, or null when no row carries it */
    private static String owningTopicOfMessage(SQLiteDatabase database, String messageId) {
        return rawText(database,
                "SELECT b." + TreeLearnSchema.Branches.TOPIC_ID
                        + " FROM " + TreeLearnSchema.Messages.TABLE + " m"
                        + " JOIN " + TreeLearnSchema.Branches.TABLE + " b ON m."
                        + TreeLearnSchema.Messages.BRANCH_ID + "=b." + TreeLearnSchema.Branches.ID
                        + " WHERE m." + TreeLearnSchema.Messages.ID + "=?",
                messageId);
    }

    /** @return the topic owning the message this revision was recorded for, or null */
    private static String owningTopicOfRevision(SQLiteDatabase database, String revisionId) {
        return rawText(database,
                "SELECT b." + TreeLearnSchema.Branches.TOPIC_ID
                        + " FROM " + TreeLearnSchema.AnswerRevisions.TABLE + " r"
                        + " JOIN " + TreeLearnSchema.Messages.TABLE + " m ON r."
                        + TreeLearnSchema.AnswerRevisions.MESSAGE_ID + "=m."
                        + TreeLearnSchema.Messages.ID
                        + " JOIN " + TreeLearnSchema.Branches.TABLE + " b ON m."
                        + TreeLearnSchema.Messages.BRANCH_ID + "=b." + TreeLearnSchema.Branches.ID
                        + " WHERE r." + TreeLearnSchema.AnswerRevisions.ID + "=?",
                revisionId);
    }

    /** Runs one parameterised single-value query; null means "no row carries this id". */
    private static String rawText(SQLiteDatabase database, String sql, String argument) {
        try (Cursor cursor = database.rawQuery(sql, new String[]{argument})) {
            return cursor.moveToFirst() ? cursor.getString(0) : null;
        }
    }

    private static String message(SQLiteException failure) {
        return failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
    }

    private static void requireArguments(SQLiteDatabase database, Topic topic, Branch branch,
                                         Message userMessage, Message aiMessage,
                                         GenerationOperation operation) {
        Guard.object(database, "database");
        Guard.object(topic, "topic");
        Guard.object(branch, "branch");
        Guard.object(userMessage, "userMessage");
        Guard.object(aiMessage, "aiMessage");
        Guard.object(operation, "operation");
    }

    private static Refused refuse(Outcome outcome) {
        return new Refused(outcome);
    }

    /**
     * Unwinds the transaction so that a refused write commits nothing.
     *
     * <p>Never observed by a caller: {@link #create} catches it and returns the {@link Outcome} it
     * carries. Same shape as {@link GenerationStabilizer}'s own control flow — a RuntimeException so
     * the work body can stay a lambda, with its stack trace suppressed because it is not an error.
     */
    private static final class Refused extends RuntimeException {

        private final transient Outcome outcome;

        Refused(Outcome outcome) {
            super("first-topic creation refused", null, false, false);
            this.outcome = outcome;
        }
    }
}
