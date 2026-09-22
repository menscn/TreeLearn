package com.treelearn.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.treelearn.core.domain.AnswerRevision;
import com.treelearn.core.domain.Branch;
import com.treelearn.core.domain.BranchAnchor;
import com.treelearn.core.domain.BranchSource;
import com.treelearn.core.domain.DomainResult;
import com.treelearn.core.domain.GenerationCommand;
import com.treelearn.core.domain.GenerationModel;
import com.treelearn.core.domain.GenerationOperation;
import com.treelearn.core.domain.GenerationState;
import com.treelearn.core.domain.Message;
import com.treelearn.core.domain.PromptPurpose;
import com.treelearn.core.domain.Topic;
import com.treelearn.data.db.DatabaseTransactionRunner;
import com.treelearn.data.db.TreeLearnDatabaseHelper;
import com.treelearn.data.db.TreeLearnSchema;
import com.treelearn.data.db.dao.AnswerRevisionDao;
import com.treelearn.data.db.dao.BranchDao;
import com.treelearn.data.db.dao.ContextSnapshotDao;
import com.treelearn.data.db.dao.FirstTopicCreation;
import com.treelearn.data.db.dao.GenerationOperationDao;
import com.treelearn.data.db.dao.GenerationOperationRow;
import com.treelearn.data.db.dao.MessageDao;
import com.treelearn.data.db.dao.TopicDao;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

/**
 * T-025 acceptance evidence for the first-topic write, executed on real Android framework SQLite
 * (API 36): five row groups in one transaction, or none at all.
 *
 * <p>A brand-new database is re-created for every case, so every count below is an absolute number
 * rather than a delta from a previous case.
 *
 * <p>Store refusals are judged by the SQLite extended result code and the constraint that spoke, not
 * by a message substring: the code for a duplicate {@code topics.id} was measured on this device
 * (1555 {@code SQLITE_CONSTRAINT_PRIMARYKEY}, {@code artifacts/evidence/T-025-constraint-code-probe.txt}),
 * and a CHECK or NOT NULL refusal can name the same column.
 *
 * <p>Holds no real user content and no API key: every literal here is synthetic (rules/sql.md).
 */
@RunWith(AndroidJUnit4.class)
public final class FirstTopicCreationInstrumentedTest {

    private static final long T0 = 1_756_000_000_000L;

    private static final String MODEL = "deepseek-flash";

    /** Synthetic policy version owned by {@link PromptPurpose#LEARNING_ANSWER}. */
    private static final String POLICY_VERSION = "tl-learning-answer/1.0.0";

    private static final String TOPIC_A = "aaaaaaaa-0000-4000-8000-000000000001";
    private static final String TOPIC_B = "bbbbbbbb-0000-4000-8000-000000000002";
    private static final String TOPIC_RETRY = "cccccccc-0000-4000-8000-000000000003";

    /** A well-formed id that no row carries, used to force a foreign-key refusal mid-transaction. */
    private static final String ABSENT_MESSAGE_ID = "dddddddd-0000-4000-8000-000000000004";

    /** Synthetic birth-snapshot id for the fork fixture that completes its two-step write. */
    private static final String SNAPSHOT_ID = "eeeeeeee-0000-4000-8000-000000000005";

    private TreeLearnDatabaseHelper helper;
    private DatabaseTransactionRunner runner;
    private FirstTopicCreation creation;

    @Before
    public void setUp() {
        context().deleteDatabase(TreeLearnDatabaseHelper.DATABASE_NAME);
        helper = new TreeLearnDatabaseHelper(context());
        runner = new DatabaseTransactionRunner();
        creation = new FirstTopicCreation(runner);
    }

    @After
    public void tearDown() {
        if (helper != null) {
            helper.close();
        }
        context().deleteDatabase(TreeLearnDatabaseHelper.DATABASE_NAME);
    }

    // ------------------------------------------------------------------
    // 1. Atomicity: all five row groups, or none.
    // ------------------------------------------------------------------

    @Test
    public void firstTopicCommitsAllFiveRowGroupsAndTheyAllAgreeOnOneTopic() {
        Writes writes = firstTopic(TOPIC_A, T0);

        FirstTopicCreation.Outcome outcome = create(writes);

        assertTrue("an accepted first topic must commit: " + outcome, outcome.isCreated());
        assertEquals("the committed topic carries the id the caller supplied", TOPIC_A,
                outcome.topic.id);
        assertRowCounts(writes, 1, 1, 2, 1);

        Branch storedRoot = BranchDao.findRootOfTopic(db(), TOPIC_A);
        assertNotNull("the root branch of the new topic must be readable", storedRoot);
        assertEqualBranch(writes.branch, storedRoot);

        List<Message> positions = MessageDao.findByBranch(db(), writes.branch.id);
        assertEquals("the first topic starts with exactly the question and the answer position", 2,
                positions.size());
        assertEqualMessage(writes.userMessage, positions.get(0));
        assertEqualMessage(writes.aiMessage, positions.get(1));

        GenerationOperationRow operation = GenerationOperationDao.findById(db(), writes.operation.id);
        assertNotNull("the attempt row of the first topic must be readable", operation);
        assertEquals("the attempt belongs to the new branch", writes.branch.id, operation.branchId);
        assertEquals("the attempt belongs to the answer position", writes.aiMessage.id,
                operation.messageId);
        assertEquals("a freshly created attempt is staged, not submitted", GenerationState.STAGED,
                operation.state);
        assertEquals("the version pinned by the command is stored verbatim", POLICY_VERSION,
                operation.promptPolicyVersion);
        assertEquals("thinking mode is part of the attempt", true, operation.thinkingEnabled);

        assertNoDanglingReferences();
    }

    @Test
    public void theRootBranchIsWrittenWithoutABirthSnapshotOrAnySource() {
        Writes writes = firstTopic(TOPIC_A, T0);
        assertTrue(create(writes).isCreated());

        assertEquals("a root branch has no parent",
                null, branchColumn(writes.branch.id, TreeLearnSchema.Branches.PARENT_ID));
        assertEquals("a root branch has no source message",
                null, branchColumn(writes.branch.id, TreeLearnSchema.Branches.SOURCE_MESSAGE_ID));
        assertEquals("a root branch has no source revision",
                null, branchColumn(writes.branch.id, TreeLearnSchema.Branches.SOURCE_REVISION_ID));
        assertEquals("the first topic has no parent branch to snapshot",
                null, branchColumn(writes.branch.id, TreeLearnSchema.Branches.CONTEXT_SNAPSHOT_ID));
        assertEquals("a whole-answer fork stores no anchor start",
                null, branchColumn(writes.branch.id, TreeLearnSchema.Branches.ANCHOR_START_INCLUSIVE));
        assertEquals("a whole-answer fork stores no anchor end",
                null, branchColumn(writes.branch.id, TreeLearnSchema.Branches.ANCHOR_END_EXCLUSIVE));
        assertEquals("a whole-answer fork stores no quote",
                null, branchColumn(writes.branch.id, TreeLearnSchema.Branches.ANCHOR_QUOTE));
        assertEquals("a whole-answer fork stores no hash",
                null, branchColumn(writes.branch.id, TreeLearnSchema.Branches.ANCHOR_HASH));
        assertEquals("the first-topic write must not smuggle in a snapshot row", 0,
                count("SELECT COUNT(*) FROM " + TreeLearnSchema.ContextSnapshots.TABLE));

        // Readable back through the rehydration path, which refuses a non-root branch that has no
        // snapshot: a root branch must therefore not demand one.
        assertNotNull("a root branch is rehydrated without a birth snapshot", storedRoot());
        assertNull("a root branch carries no provenance object", storedRoot().source);
        assertTrue("a root branch reports itself as the root", storedRoot().isRoot());
    }

    @Test
    public void theFirstTopicSurvivesCloseAndReopenFieldByField() {
        Writes writes = firstTopic(TOPIC_A, T0);
        assertTrue(create(writes).isCreated());
        helper.close();

        TreeLearnDatabaseHelper reopened = new TreeLearnDatabaseHelper(context());
        try {
            SQLiteDatabase database = reopened.getWritableDatabase();
            assertEqualTopic(writes.topic, TopicDao.findById(database, TOPIC_A));
            assertEqualBranch(writes.branch, BranchDao.findRootOfTopic(database, TOPIC_A));
            List<Message> positions = MessageDao.findByBranch(database, writes.branch.id);
            assertEquals(2, positions.size());
            assertEqualMessage(writes.userMessage, positions.get(0));
            assertEqualMessage(writes.aiMessage, positions.get(1));
            assertNotNull("the attempt survives the reopen",
                    GenerationOperationDao.findById(database, writes.operation.id));
            try (Cursor cursor = database.rawQuery("PRAGMA foreign_key_check", null)) {
                assertEquals("a reopened database still has no dangling reference", 0,
                        cursor.getCount());
            }
        } finally {
            reopened.close();
        }
        helper = reopened;
    }

    // ------------------------------------------------------------------
    // 2. All-fail: a refusal anywhere unwinds everything.
    // ------------------------------------------------------------------

    @Test
    public void aStoreRefusalInTheMiddleOfTheTransactionRollsBackEveryRowGroup() {
        Writes writes = firstTopic(TOPIC_A, T0);
        // The last write names a message row that does not exist, so the store refuses it after the
        // topic, the root branch and both messages have already been written in this transaction.
        GenerationOperation orphan = operationFor(writes.branch, writes.aiMessage, ABSENT_MESSAGE_ID, T0);

        FirstTopicCreation.Outcome outcome = creation.create(db(), writes.topic, writes.branch,
                writes.userMessage, writes.aiMessage, orphan);

        assertTrue("a store refusal must be reported to the caller as an outcome: " + outcome,
                outcome.isRefused());
        assertEquals(FirstTopicCreation.Refusal.STORAGE_REFUSED, outcome.refusal);
        assertNotNull("a refusal must say what the store refused", outcome.detail);
        assertTrue("the refusal keeps SQLite's own cause (foreign key 787) but " + outcome.detail,
                outcome.detail.contains("code 787"));
        assertNoRowsFor(writes);
        assertNoDanglingReferences();
    }

    @Test
    public void aRetryAfterARolledBackAttemptCreatesTheSameTopic() {
        Writes failed = firstTopic(TOPIC_RETRY, T0);
        GenerationOperation orphan = operationFor(failed.branch, failed.aiMessage, ABSENT_MESSAGE_ID,
                T0);
        assertTrue(creation.create(db(), failed.topic, failed.branch, failed.userMessage,
                failed.aiMessage, orphan).isRefused());
        assertNoRowsFor(failed);

        Writes retry = firstTopic(TOPIC_RETRY, T0 + 5_000L);
        FirstTopicCreation.Outcome outcome = create(retry);

        assertTrue("a rolled-back attempt must leave no dirty state behind: " + outcome,
                outcome.isCreated());
        assertRowCounts(retry, 1, 1, 2, 1);
        assertNoDanglingReferences();
    }

    @Test
    public void aSecondCreationForTheSameTopicIdIsRefusedAndChangesNothing() {
        Writes first = firstTopic(TOPIC_A, T0);
        assertTrue(create(first).isCreated());

        Writes second = firstTopic(TOPIC_A, T0 + 1_000L);
        FirstTopicCreation.Outcome outcome = create(second);

        assertTrue("a duplicate local write must be refused, not silently absorbed: " + outcome,
                outcome.isRefused());
        assertEquals(FirstTopicCreation.Refusal.TOPIC_ID_ALREADY_EXISTS, outcome.refusal);
        assertTrue("the refusal keeps SQLite's own cause (primary key 1555): " + outcome.detail,
                outcome.detail.contains("code 1555"));
        assertRowCounts(first, 1, 1, 2, 1);
        assertEquals("the second attempt left no branch row", 0,
                count("SELECT COUNT(*) FROM " + TreeLearnSchema.Branches.TABLE + " WHERE "
                        + TreeLearnSchema.Branches.ID + "=?", second.branch.id));
        assertNoDanglingReferences();
    }

    @Test
    public void createRefusesToJoinAnOuterTransactionAndCommitsNothing() {
        Writes writes = firstTopic(TOPIC_A, T0);

        try {
            runner.run(db(), database -> creation.create(database, writes.topic, writes.branch,
                    writes.userMessage, writes.aiMessage, writes.operation));
            fail("a nested transaction must be refused by the shared runner, not silently joined");
        } catch (IllegalStateException expected) {
            assertTrue("the refusal must come from DatabaseTransactionRunner: " + expected.getMessage(),
                    expected.getMessage().contains("nested transaction"));
        }

        assertNoRowsFor(writes);
    }

    // ------------------------------------------------------------------
    // 3. The cross-topic source rule: one judgement point, both directions.
    // ------------------------------------------------------------------

    @Test
    public void aBranchWhoseSourceMessageIsInAnotherTopicIsRefusedAndLeavesNoBranchRow() {
        Writes sourceTopic = firstTopic(TOPIC_A, T0);
        assertTrue("the source topic must exist before it can be forked", create(sourceTopic).isCreated());
        AnswerRevision revision = insertStableRevision(sourceTopic.aiMessage, T0 + 1_000L);
        BranchSource crossTopic = BranchSource.fromOperation(sourceTopic.branch.id,
                sourceTopic.aiMessage.id, SNAPSHOT_ID, GenerationState.STABLE, revision.id,
                BranchAnchor.wholeAnswer()).value();

        Writes target = firstTopic(TOPIC_B, T0);
        Branch forked = Branch.child(TOPIC_B, crossTopic, T0).value();
        FirstTopicCreation.Outcome outcome = creation.create(db(), target.topic, forked,
                target.userMessage, target.aiMessage, target.operation);

        assertTrue("a cross-topic source must be refused, not written: " + outcome,
                outcome.isRefused());
        assertEquals(FirstTopicCreation.Refusal.SOURCE_MESSAGE_NOT_IN_TOPIC, outcome.refusal);
        assertEquals("the refused transaction leaves no chapter row", 0,
                count("SELECT COUNT(*) FROM " + TreeLearnSchema.Topics.TABLE + " WHERE "
                        + TreeLearnSchema.Topics.ID + "=?", TOPIC_B));
        assertEquals("the refused transaction leaves no branch row", 0,
                count("SELECT COUNT(*) FROM " + TreeLearnSchema.Branches.TABLE + " WHERE "
                        + TreeLearnSchema.Branches.ID + "=?", forked.id));
        assertEquals("deleting the source topic must not be able to take another topic with it", 1,
                count("SELECT COUNT(*) FROM " + TreeLearnSchema.Branches.TABLE + " WHERE "
                        + TreeLearnSchema.Branches.ID + "=?", sourceTopic.branch.id));
        assertNoDanglingReferences();
    }

    @Test
    public void aSourceMessageInsideTheTargetTopicIsAcceptedAndTheBranchIsWritten() {
        Writes sourceTopic = firstTopic(TOPIC_A, T0);
        assertTrue(create(sourceTopic).isCreated());
        AnswerRevision revision = insertStableRevision(sourceTopic.aiMessage, T0 + 1_000L);
        BranchSource sameTopic = BranchSource.fromOperation(sourceTopic.branch.id,
                sourceTopic.aiMessage.id, SNAPSHOT_ID, GenerationState.STABLE, revision.id,
                BranchAnchor.wholeAnswer()).value();
        Branch forked = Branch.child(TOPIC_A, sameTopic, T0 + 2_000L).value();

        // The shape a fork-creation write path has: judge the explicit source inside the very
        // transaction that writes the branch, then complete the branch's two-step write
        // (branch -> snapshot row -> pointer). The production fork entry point is T-009's; this task
        // supplies the judgement it has to call, so the fixture drives it exactly as that path will.
        runner.run(db(), database -> {
            assertNull("a source inside the target topic must be accepted",
                    FirstTopicCreation.sourceRefusal(database, TOPIC_A, sameTopic));
            BranchDao.insert(database, forked);
            ContextSnapshotDao.insert(database, new ContextSnapshotDao.Row(SNAPSHOT_ID, forked.id, 1,
                    "sha256-of-the-synthetic-context", "{\"schema\":1,\"entries\":[]}",
                    T0 + 2_000L));
            BranchDao.setContextSnapshot(database, forked.id, SNAPSHOT_ID);
            return null;
        });

        assertEquals("the accepted branch row is written", 1,
                count("SELECT COUNT(*) FROM " + TreeLearnSchema.Branches.TABLE + " WHERE "
                        + TreeLearnSchema.Branches.ID + "=?", forked.id));
        Branch stored = BranchDao.findById(db(), forked.id);
        assertNotNull("the forked branch is readable once its snapshot is complete", stored);
        assertEquals("the fork keeps its parent", sourceTopic.branch.id, stored.parentBranchId);
        assertEquals("the fork pins the revision it was forked from", revision.id,
                stored.source.sourceRevisionId);
        assertEquals("the fork pins the source message", sourceTopic.aiMessage.id,
                stored.source.sourceMessageId);
        assertTrue("the fork stores the whole answer as its anchor", stored.source.anchor.isWholeAnswer());
        assertEquals("the source topic still has exactly its own root",
                sourceTopic.branch.id, BranchDao.findRootOfTopic(db(), TOPIC_A).id);
        assertNoDanglingReferences();
    }

    // ------------------------------------------------------------------
    // Fixtures.
    // ------------------------------------------------------------------

    /** The five row groups one first-topic write consists of. */
    private static final class Writes {

        final Topic topic;
        final Branch branch;
        final Message userMessage;
        final Message aiMessage;
        final GenerationOperation operation;

        Writes(Topic topic, Branch branch, Message userMessage, Message aiMessage,
               GenerationOperation operation) {
            this.topic = topic;
            this.branch = branch;
            this.userMessage = userMessage;
            this.aiMessage = aiMessage;
            this.operation = operation;
        }
    }

    private static Writes firstTopic(String topicId, long now) {
        Topic topic = new Topic(topicId, null, now, now);
        Branch root = Branch.root(topic.id, now);
        Message userMessage = Message.user(root.id, 0, "为什么天空是蓝色的？", now);
        Message aiMessage = Message.ai(root.id, 1, now);
        return new Writes(topic, root, userMessage, aiMessage,
                operationFor(root, aiMessage, aiMessage.id, now));
    }

    private static GenerationOperation operationFor(Branch branch, Message aiMessage, String messageId,
                                                    long now) {
        DomainResult<GenerationCommand> command = GenerationCommand.create(branch.id, messageId,
                PromptPurpose.LEARNING_ANSWER, POLICY_VERSION, GenerationModel.fromWireId(MODEL), true,
                now);
        assertTrue("fixture command must be accepted: "
                + (command.isOk() ? "" : command.error()), command.isOk());
        return GenerationOperation.start(command.value(), now);
    }

    private AnswerRevision insertStableRevision(Message aiMessage, long now) {
        DomainResult<AnswerRevision> created = AnswerRevision.create(aiMessage.id, 1,
                "推理：瑞利散射让短波更易被散射。", "因为大气中的分子把蓝光散射得更多。",
                GenerationModel.fromWireId(MODEL), true, POLICY_VERSION, now);
        assertTrue("fixture revision must be accepted: "
                + (created.isOk() ? "" : created.error()), created.isOk());
        AnswerRevisionDao.insert(db(), created.value());
        return created.value();
    }

    private FirstTopicCreation.Outcome create(Writes writes) {
        return creation.create(db(), writes.topic, writes.branch, writes.userMessage, writes.aiMessage,
                writes.operation);
    }

    // ------------------------------------------------------------------
    // Assertion and raw-read helpers.
    // ------------------------------------------------------------------

    private SQLiteDatabase db() {
        return helper.getWritableDatabase();
    }

    private static android.content.Context context() {
        return InstrumentationRegistry.getInstrumentation().getTargetContext();
    }

    private Branch storedRoot() {
        return BranchDao.findRootOfTopic(db(), TOPIC_A);
    }

    private int count(String sql, String... args) {
        try (Cursor cursor = db().rawQuery(sql, args)) {
            return cursor.moveToFirst() ? cursor.getInt(0) : Integer.MIN_VALUE;
        }
    }

    private String branchColumn(String branchId, String column) {
        try (Cursor cursor = db().rawQuery("SELECT " + column + " FROM "
                + TreeLearnSchema.Branches.TABLE + " WHERE " + TreeLearnSchema.Branches.ID + "=?",
                new String[]{branchId})) {
            return cursor.moveToFirst() ? cursor.getString(0) : null;
        }
    }

    private void assertNoDanglingReferences() {
        try (Cursor cursor = db().rawQuery("PRAGMA foreign_key_check", null)) {
            assertEquals("PRAGMA foreign_key_check must report no dangling reference", 0,
                    cursor.getCount());
        }
    }

    /** Every row group of a refused write must be absent, not just the one the refusal named. */
    private void assertNoRowsFor(Writes writes) {
        assertEquals("no topic row may survive a refused write", 0,
                count("SELECT COUNT(*) FROM " + TreeLearnSchema.Topics.TABLE + " WHERE "
                        + TreeLearnSchema.Topics.ID + "=?", writes.topic.id));
        assertEquals("no branch row may survive a refused write", 0,
                count("SELECT COUNT(*) FROM " + TreeLearnSchema.Branches.TABLE + " WHERE "
                        + TreeLearnSchema.Branches.ID + "=?", writes.branch.id));
        assertEquals("no message row may survive a refused write", 0,
                count("SELECT COUNT(*) FROM " + TreeLearnSchema.Messages.TABLE + " WHERE "
                        + TreeLearnSchema.Messages.BRANCH_ID + "=?", writes.branch.id));
        assertEquals("no attempt row may survive a refused write", 0,
                count("SELECT COUNT(*) FROM " + TreeLearnSchema.GenerationOperations.TABLE + " WHERE "
                        + TreeLearnSchema.GenerationOperations.BRANCH_ID + "=?", writes.branch.id));
    }

    private void assertRowCounts(Writes writes, int topics, int branches, int messages,
                                 int operations) {
        assertEquals("topics rows", topics, count("SELECT COUNT(*) FROM " + TreeLearnSchema.Topics.TABLE
                + " WHERE " + TreeLearnSchema.Topics.ID + "=?", writes.topic.id));
        assertEquals("branches rows", branches, count("SELECT COUNT(*) FROM "
                + TreeLearnSchema.Branches.TABLE + " WHERE " + TreeLearnSchema.Branches.TOPIC_ID + "=?",
                writes.topic.id));
        assertEquals("messages rows", messages, count("SELECT COUNT(*) FROM "
                + TreeLearnSchema.Messages.TABLE + " WHERE " + TreeLearnSchema.Messages.BRANCH_ID + "=?",
                writes.branch.id));
        assertEquals("generation_operations rows", operations, count("SELECT COUNT(*) FROM "
                + TreeLearnSchema.GenerationOperations.TABLE + " WHERE "
                + TreeLearnSchema.GenerationOperations.BRANCH_ID + "=?", writes.branch.id));
    }

    private static void assertEqualTopic(Topic expected, Topic actual) {
        assertNotNull("a written topic must be readable", actual);
        assertEquals("topic id", expected.id, actual.id);
        assertEquals("topic last visited branch", expected.lastVisitedBranchId,
                actual.lastVisitedBranchId);
        assertEquals("topic createdAt (UTC epoch millis)", expected.createdAt, actual.createdAt);
        assertEquals("topic updatedAt (UTC epoch millis)", expected.updatedAt, actual.updatedAt);
    }

    private static void assertEqualBranch(Branch expected, Branch actual) {
        assertEquals("branch id", expected.id, actual.id);
        assertEquals("branch topicId", expected.topicId, actual.topicId);
        assertEquals("branch parentBranchId", expected.parentBranchId, actual.parentBranchId);
        assertEquals("branch is the root", expected.isRoot(), actual.isRoot());
        assertEquals("branch title", expected.title(), actual.title());
        assertEquals("branch createdAt (UTC epoch millis)", expected.createdAt, actual.createdAt);
        assertEquals("branch updatedAt (UTC epoch millis)", expected.updatedAt(), actual.updatedAt());
        assertEquals("branch latestStableSequence", expected.latestStableSequence(),
                actual.latestStableSequence());
        assertEquals("branch lastSeenStableSequence", expected.lastSeenStableSequence(),
                actual.lastSeenStableSequence());
        assertEquals("branch collapsed", expected.isCollapsed(), actual.isCollapsed());
        assertEquals("branch archived", expected.isArchived(), actual.isArchived());
        assertEquals("branch provenance", expected.source, actual.source);
    }

    private static void assertEqualMessage(Message expected, Message actual) {
        assertEquals("message id", expected.id, actual.id);
        assertEquals("message branchId", expected.branchId, actual.branchId);
        assertEquals("message role", expected.role, actual.role);
        assertEquals("message ordinal", expected.ordinal, actual.ordinal);
        assertEquals("message userContent", expected.userContent, actual.userContent);
        assertEquals("message createdAt (UTC epoch millis)", expected.createdAt, actual.createdAt);
        assertEquals("message activeRevisionId", expected.activeRevisionId(),
                actual.activeRevisionId());
        assertEquals("message activeOperationId", expected.activeOperationId(),
                actual.activeOperationId());
    }
}
