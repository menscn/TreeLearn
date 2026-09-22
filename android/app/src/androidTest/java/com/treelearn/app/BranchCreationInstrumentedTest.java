package com.treelearn.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.treelearn.core.domain.AnswerRevision;
import com.treelearn.core.domain.Branch;
import com.treelearn.core.domain.BranchAnchor;
import com.treelearn.core.domain.BranchSource;
import com.treelearn.core.domain.DomainResult;
import com.treelearn.core.domain.GenerationModel;
import com.treelearn.core.domain.GenerationState;
import com.treelearn.core.domain.Message;
import com.treelearn.core.domain.Topic;
import com.treelearn.data.db.DatabaseTransactionRunner;
import com.treelearn.data.db.TreeLearnDatabaseHelper;
import com.treelearn.data.db.TreeLearnSchema;
import com.treelearn.data.db.dao.AnswerRevisionDao;
import com.treelearn.data.db.dao.BranchCreation;
import com.treelearn.data.db.dao.BranchDao;
import com.treelearn.data.db.dao.ContextSnapshotDao;
import com.treelearn.data.db.dao.MessageDao;
import com.treelearn.data.db.dao.TopicDao;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * T-009 acceptance evidence for creating a non-root branch, executed on real Android framework SQLite
 * (API 36): the branch row, its immutable birth snapshot and the pointer between them, in one
 * transaction or not at all.
 *
 * <p>This suite is the acceptance evidence for the defect a reviewer raised against the fork path:
 * {@code branches.context_snapshot_id} and {@code context_snapshots.branch_id} reference each other,
 * so the store admits a non-root branch whose pointer is NULL, while the read path refuses exactly that
 * row. The database accepts a state the application cannot read. The invariant is now enforced in two
 * halves and both are asserted here: the entry point {@link BranchCreation} never commits a fork that
 * is missing one of its three writes (a refusal anywhere rolls all of them back), and
 * {@link BranchDao#findById} still fails loudly, naming the broken contract, if such a row ever
 * reaches the table by another route.
 *
 * <p>A brand-new database is created for every case, so every count below is an absolute number rather
 * than a delta from a previous case.
 *
 * <p>Holds no real user content and no API key: every literal here is synthetic (rules/sql.md). The
 * snapshot payload is deliberately opaque — this layer stores it verbatim and never parses it.
 */
@RunWith(AndroidJUnit4.class)
public final class BranchCreationInstrumentedTest {

    private static final long T0 = 1_756_000_000_000L;

    /** Synthetic model id; never a real credential. */
    private static final String MODEL = "deepseek-flash";

    /** Synthetic policy version owned by the learning-answer purpose. */
    private static final String POLICY_VERSION = "tl-learning-answer/1.0.0";

    private static final String TOPIC_A = "11111111-1111-4111-8111-000000000001";
    private static final String TOPIC_B = "22222222-2222-4222-8222-000000000002";
    private static final String SNAPSHOT_ID = "33333333-3333-4333-8333-000000000003";
    private static final String OTHER_SNAPSHOT_ID = "44444444-4444-4444-8444-000000000004";

    /** A row planted with raw SQL to stand for "only half of the write happened". */
    private static final String HALF_WRITTEN_BRANCH_ID = "55555555-5555-4555-8555-000000000005";

    private static final String PAYLOAD = "{\"schema\":1,\"entries\":[]}";
    private static final String DIGEST = "sha256-of-the-synthetic-context";

    private TreeLearnDatabaseHelper helper;
    private DatabaseTransactionRunner runner;
    private BranchCreation creation;

    @Before
    public void setUp() {
        context().deleteDatabase(TreeLearnDatabaseHelper.DATABASE_NAME);
        helper = new TreeLearnDatabaseHelper(context());
        runner = new DatabaseTransactionRunner();
        creation = new BranchCreation(runner);
    }

    @After
    public void tearDown() {
        if (helper != null) {
            helper.close();
        }
        context().deleteDatabase(TreeLearnDatabaseHelper.DATABASE_NAME);
    }

    // ------------------------------------------------------------------
    // 1. What the entry point commits: a fork whose three writes all landed.
    // ------------------------------------------------------------------

    @Test
    public void aForkCreatedThroughTheEntryPointIsReadableWithItsFullProvenance() {
        Seeded source = seedTopic(TOPIC_A, T0);
        Branch fork = forkOf(source, SNAPSHOT_ID, BranchAnchor.wholeAnswer(), T0 + 1_000L);

        BranchCreation.Outcome outcome =
                creation.create(db(), fork, snapshotRow(SNAPSHOT_ID, fork.id, T0 + 1_000L));

        assertTrue("a well-formed fork must commit: " + outcome, outcome.isCreated());
        assertEquals("the committed fork is the one the caller built", fork.id, outcome.branch.id);

        // The read path is the other half of the invariant -- it refuses a non-root row with no
        // pointer -- so a row it rehydrates is proof that the pointer landed, not just a hope.
        Branch stored = BranchDao.findById(db(), fork.id);
        assertNotNull("the fork must be readable, not left as a row the app cannot rehydrate", stored);
        assertEquals("parent branch", source.root.id, stored.parentBranchId);
        assertEquals("source message", source.aiMessage.id, stored.source.sourceMessageId);
        assertEquals("the pinned revision is the stable answer the fork was made from",
                source.revision.id, stored.source.sourceRevisionId);
        assertTrue("a whole-answer fork carries no offsets", stored.source.anchor.isWholeAnswer());
        assertEquals("the provenance names the snapshot written in the same transaction",
                SNAPSHOT_ID, stored.source.contextSnapshotId);
        assertEquals("and the pointer column itself is not NULL", SNAPSHOT_ID,
                branchColumn(fork.id, TreeLearnSchema.Branches.CONTEXT_SNAPSHOT_ID));

        ContextSnapshotDao.Row storedSnapshot = ContextSnapshotDao.findByBranchId(db(), fork.id);
        assertNotNull("the birth snapshot of the fork must be readable", storedSnapshot);
        assertEquals("snapshot id", SNAPSHOT_ID, storedSnapshot.id);
        assertEquals("snapshot branch", fork.id, storedSnapshot.branchId);
        assertEquals("schema version", 1, storedSnapshot.schemaVersion);
        assertEquals("digest", DIGEST, storedSnapshot.digest);
        assertEquals("the opaque payload is stored and read back verbatim", PAYLOAD,
                storedSnapshot.payload);
        assertEquals("createdAt (UTC epoch millis)", T0 + 1_000L, storedSnapshot.createdAt);
        assertNoDanglingReferences();
    }

    @Test
    public void aRangedForkKeepsBothLocatorsAndItsSnapshot() {
        Seeded source = seedTopic(TOPIC_A, T0);
        BranchAnchor anchor = BranchAnchor.ofRange(0, 4, "abcd", "hash-of-abcd").value();
        Branch fork = forkOf(source, SNAPSHOT_ID, anchor, T0 + 1_000L);

        assertTrue(creation.create(db(), fork, snapshotRow(SNAPSHOT_ID, fork.id, T0 + 1_000L))
                .isCreated());

        Branch stored = BranchDao.findById(db(), fork.id);
        assertFalse("a ranged fork is not a whole-answer fork", stored.source.anchor.isWholeAnswer());
        assertEquals("anchor start", Integer.valueOf(0), stored.source.anchor.startInclusive);
        assertEquals("anchor end", Integer.valueOf(4), stored.source.anchor.endExclusive);
        assertEquals("anchor quote", "abcd", stored.source.anchor.quote);
        assertEquals("anchor hash", "hash-of-abcd", stored.source.anchor.hash);
        assertEquals("a ranged fork still pins its revision", source.revision.id,
                stored.source.sourceRevisionId);
        assertNotNull("and it still gets its birth snapshot",
                ContextSnapshotDao.findByBranchId(db(), fork.id));
        assertNoDanglingReferences();
    }

    // ------------------------------------------------------------------
    // 2. A refusal anywhere leaves no branch, no snapshot and no pointer.
    // ------------------------------------------------------------------

    @Test
    public void aSnapshotIdAlreadyTakenRollsBackTheBranchTheSnapshotAndThePointer() {
        Seeded source = seedTopic(TOPIC_A, T0);
        Branch existing = forkOf(source, SNAPSHOT_ID, BranchAnchor.wholeAnswer(), T0 + 1_000L);
        assertTrue("the fixture's first fork must commit", creation
                .create(db(), existing, snapshotRow(SNAPSHOT_ID, existing.id, T0 + 1_000L))
                .isCreated());

        // The same snapshot id is handed in for a second branch, so the store refuses the *second* of
        // the three writes: by then this fork's branch row is already in the transaction. That is the
        // failure a caller cannot see coming and the one the rollback has to clean up without being
        // able to name the row.
        Branch second = forkOf(source, SNAPSHOT_ID, BranchAnchor.wholeAnswer(), T0 + 2_000L);
        BranchCreation.Outcome outcome =
                creation.create(db(), second, snapshotRow(SNAPSHOT_ID, second.id, T0 + 2_000L));

        assertTrue("a store refusal must come back as an outcome: " + outcome, outcome.isRefused());
        assertEquals(BranchCreation.Refusal.STORAGE_REFUSED, outcome.refusal);
        assertTrue("the refusal must name the write the store refused, which is the snapshot one: "
                        + outcome.detail,
                outcome.detail.contains(TreeLearnSchema.ContextSnapshots.TABLE));
        assertEquals("no branch row of the refused fork may survive", 0, countBranch(second.id));
        assertEquals("no snapshot row of the refused fork may survive", 0, countSnapshotOf(second.id));
        assertEquals("the id that was already taken still belongs to exactly one row", 1,
                count("SELECT COUNT(*) FROM " + TreeLearnSchema.ContextSnapshots.TABLE + " WHERE "
                        + TreeLearnSchema.ContextSnapshots.ID + "=?", SNAPSHOT_ID));
        assertEquals("the first fork still points at its own snapshot", SNAPSHOT_ID,
                branchColumn(existing.id, TreeLearnSchema.Branches.CONTEXT_SNAPSHOT_ID));
        assertNoDanglingReferences();
    }

    @Test
    public void aSourceOutsideTheTargetTopicIsRefusedBeforeAnythingIsWritten() {
        Seeded source = seedTopic(TOPIC_A, T0);
        seedTopic(TOPIC_B, T0);
        Branch crossTopic = Branch
                .child(TOPIC_B, provenance(source, SNAPSHOT_ID, BranchAnchor.wholeAnswer()),
                        T0 + 1_000L)
                .value();

        BranchCreation.Outcome outcome =
                creation.create(db(), crossTopic, snapshotRow(SNAPSHOT_ID, crossTopic.id, T0 + 1_000L));

        assertTrue("a source that lives in another topic must be refused: " + outcome,
                outcome.isRefused());
        assertEquals(BranchCreation.Refusal.SOURCE_NOT_IN_THIS_TOPIC, outcome.refusal);
        assertTrue("the refusal must name the topic the fork belongs to: " + outcome.detail,
                outcome.detail.contains(TOPIC_B));
        assertEquals("a refused fork leaves no branch row", 0, countBranch(crossTopic.id));
        assertEquals("a refused fork leaves no snapshot row", 0, countSnapshotOf(crossTopic.id));
        assertEquals("and it leaves the source topic untouched", 1, countBranch(source.root.id));
        assertNoDanglingReferences();
    }

    @Test
    public void aRepeatedCreationOfTheSameForkIsRefusedAndLeavesExactlyOneOfEachRow() {
        Seeded source = seedTopic(TOPIC_A, T0);
        Branch fork = forkOf(source, SNAPSHOT_ID, BranchAnchor.wholeAnswer(), T0 + 1_000L);
        assertTrue(creation.create(db(), fork, snapshotRow(SNAPSHOT_ID, fork.id, T0 + 1_000L))
                .isCreated());

        BranchCreation.Outcome again =
                creation.create(db(), fork, snapshotRow(SNAPSHOT_ID, fork.id, T0 + 2_000L));

        assertTrue("a second creation of the same fork must be refused, not duplicated: " + again,
                again.isRefused());
        assertEquals(BranchCreation.Refusal.STORAGE_REFUSED, again.refusal);
        assertEquals("still exactly one branch row for this fork", 1, countBranch(fork.id));
        assertEquals("still exactly one snapshot row for this branch", 1, countSnapshotOf(fork.id));
        assertEquals("and the pointer still names it", SNAPSHOT_ID,
                branchColumn(fork.id, TreeLearnSchema.Branches.CONTEXT_SNAPSHOT_ID));
        assertNoDanglingReferences();
    }

    // ------------------------------------------------------------------
    // 3. The read path is the second half, and it fails loudly.
    // ------------------------------------------------------------------

    @Test
    public void aNonRootRowWithoutItsSnapshotIsRefusedByTheReadPath() {
        Seeded source = seedTopic(TOPIC_A, T0);

        // The store admits this row: §5.2 requires the source references of a non-root branch and says
        // nothing about the pointer, because at insert time there is nothing to point at yet. Planting
        // it with raw SQL is what "the database accepts what the app cannot read" looks like from
        // inside :data -- a future migration, a hand-run sqlite3 session or a restored backup does the
        // same thing.
        db().execSQL("INSERT INTO " + TreeLearnSchema.Branches.TABLE + "("
                        + TreeLearnSchema.Branches.ID + ","
                        + TreeLearnSchema.Branches.TOPIC_ID + ","
                        + TreeLearnSchema.Branches.PARENT_ID + ","
                        + TreeLearnSchema.Branches.TITLE + ","
                        + TreeLearnSchema.Branches.CREATED_AT + ","
                        + TreeLearnSchema.Branches.UPDATED_AT + ","
                        + TreeLearnSchema.Branches.COLLAPSED + ","
                        + TreeLearnSchema.Branches.LATEST_STABLE_SEQUENCE + ","
                        + TreeLearnSchema.Branches.LAST_SEEN_STABLE_SEQUENCE + ","
                        + TreeLearnSchema.Branches.SOURCE_MESSAGE_ID + ","
                        + TreeLearnSchema.Branches.SOURCE_REVISION_ID + ","
                        + TreeLearnSchema.Branches.CONTEXT_SNAPSHOT_ID
                        + ") VALUES(?,?,?,?,?,?,0,0,0,?,?,NULL)",
                new Object[]{HALF_WRITTEN_BRANCH_ID, TOPIC_A, source.root.id, "half-written fork",
                        T0, T0, source.aiMessage.id, source.revision.id});
        assertEquals("the planted row must really be in the store, or this case would pass on an "
                + "empty table", 1, countBranch(HALF_WRITTEN_BRANCH_ID));

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> BranchDao.findById(db(), HALF_WRITTEN_BRANCH_ID));

        assertTrue("the read must say which contract was broken instead of failing quietly: "
                + failure.getMessage(), failure.getMessage().contains("no birth snapshot"));
        assertNoDanglingReferences();
    }

    // ------------------------------------------------------------------
    // 4. Caller bugs are refused before the transaction opens.
    // ------------------------------------------------------------------

    @Test
    public void aSnapshotRowThatDisagreesWithTheBranchIsRefusedBeforeTheTransactionOpens() {
        Seeded source = seedTopic(TOPIC_A, T0);
        Branch fork = forkOf(source, SNAPSHOT_ID, BranchAnchor.wholeAnswer(), T0 + 1_000L);

        IllegalArgumentException wrongBranch = assertThrows(IllegalArgumentException.class,
                () -> creation.create(db(), fork,
                        snapshotRow(SNAPSHOT_ID, source.root.id, T0 + 1_000L)));
        assertTrue("the refusal must name the branch the snapshot row belongs to: "
                + wrongBranch.getMessage(), wrongBranch.getMessage().contains(source.root.id));

        IllegalArgumentException wrongSnapshot = assertThrows(IllegalArgumentException.class,
                () -> creation.create(db(), fork,
                        snapshotRow(OTHER_SNAPSHOT_ID, fork.id, T0 + 1_000L)));
        assertTrue("the refusal must name the snapshot id the provenance pins: "
                + wrongSnapshot.getMessage(), wrongSnapshot.getMessage().contains(SNAPSHOT_ID));

        assertEquals("a refused call must not have written the branch row", 0, countBranch(fork.id));
        assertEquals("nor a snapshot row", 0, countSnapshotOf(fork.id));
        assertNoDanglingReferences();
    }

    @Test
    public void aRootBranchIsRefusedAsACallerBug() {
        Branch root = Branch.root(TOPIC_A, T0);

        assertThrows(IllegalArgumentException.class,
                () -> creation.create(db(), root, snapshotRow(SNAPSHOT_ID, root.id, T0)));

        assertEquals("the root branch of a topic is FirstTopicCreation's write, not this one's", 0,
                countBranch(root.id));
    }

    @Test
    public void theEntryPointRefusesANestedTransactionAndCommitsNothing() {
        Seeded source = seedTopic(TOPIC_A, T0);
        Branch fork = forkOf(source, SNAPSHOT_ID, BranchAnchor.wholeAnswer(), T0 + 1_000L);

        IllegalStateException nested = assertThrows(IllegalStateException.class,
                () -> runner.run(db(), database -> creation.create(database, fork,
                        snapshotRow(SNAPSHOT_ID, fork.id, T0 + 1_000L))));

        assertTrue("the refusal must come from the shared runner: " + nested.getMessage(),
                nested.getMessage().contains("nested transaction"));
        assertEquals("no branch row may survive", 0, countBranch(fork.id));
        assertEquals("no snapshot row may survive", 0, countSnapshotOf(fork.id));
    }

    // ------------------------------------------------------------------
    // 5. The fork keeps the version it was born from.
    // ------------------------------------------------------------------

    @Test
    public void aForkKeepsItsPinnedRevisionAndSnapshotAfterTheParentAnswerMovesOn() {
        Seeded source = seedTopic(TOPIC_A, T0);
        Branch fork = forkOf(source, SNAPSHOT_ID, BranchAnchor.wholeAnswer(), T0 + 1_000L);
        assertTrue(creation.create(db(), fork, snapshotRow(SNAPSHOT_ID, fork.id, T0 + 1_000L))
                .isCreated());

        // The parent answer moves on: a second stable revision is recorded and the AI position is
        // repointed at it. §5.1/§5.2 -- an existing child branch pins sourceRevisionId, so it must not
        // follow the parent's active revision, and its birth snapshot must not be rewritten.
        AnswerRevision second =
                stableRevision(source.aiMessage.id, 2, "推理二：同一位置上的新版本。",
                        "回答二：这是父分支后来的稳定回答。", T0 + 3_000L);
        Message parent = MessageDao.findById(db(), source.aiMessage.id);
        DomainResult<Void> switched = parent.activateRevision(second.id);
        assertTrue("the parent position must accept the new revision: "
                + (switched.isOk() ? "" : switched.error()), switched.isOk());
        MessageDao.updateActiveReferences(db(), parent);

        Branch stored = BranchDao.findById(db(), fork.id);
        assertEquals("the child still pins the revision it was forked from", source.revision.id,
                stored.source.sourceRevisionId);
        assertFalse("which is no longer the revision the parent displays", second.id.equals(
                stored.source.sourceRevisionId));
        assertEquals("and the child still names its own birth snapshot", SNAPSHOT_ID,
                stored.source.contextSnapshotId);
        assertEquals("the parent's displayed revision really did move", second.id,
                MessageDao.findById(db(), source.aiMessage.id).activeRevisionId());
        ContextSnapshotDao.Row snapshot = ContextSnapshotDao.findByBranchId(db(), fork.id);
        assertEquals("the snapshot payload was not rewritten by the parent's later answer", PAYLOAD,
                snapshot.payload);
        assertNoDanglingReferences();
    }

    // ------------------------------------------------------------------
    // Fixtures.
    // ------------------------------------------------------------------

    /** A topic with its root branch, the learner's question, the answer position and one stable answer. */
    private static final class Seeded {

        final String topicId;
        final Branch root;
        final Message aiMessage;
        final AnswerRevision revision;

        Seeded(String topicId, Branch root, Message aiMessage, AnswerRevision revision) {
            this.topicId = topicId;
            this.root = root;
            this.aiMessage = aiMessage;
            this.revision = revision;
        }
    }

    private Seeded seedTopic(String topicId, long now) {
        TopicDao.insert(db(), new Topic(topicId, null, now, now));
        Branch root = Branch.root(topicId, now);
        BranchDao.insert(db(), root);
        MessageDao.insert(db(), Message.user(root.id, 0, "为什么天空是蓝色的？", now));
        Message aiMessage = Message.ai(root.id, 1, now);
        MessageDao.insert(db(), aiMessage);
        AnswerRevision revision = stableRevision(aiMessage.id, 1, "推理：瑞利散射让短波更易被散射。",
                "因为大气分子对蓝光的散射更强。", now);
        DomainResult<Void> activated = aiMessage.activateRevision(revision.id);
        assertTrue("fixture: the answer position must be able to display its revision: "
                + (activated.isOk() ? "" : activated.error()), activated.isOk());
        MessageDao.updateActiveReferences(db(), aiMessage);
        return new Seeded(topicId, root, aiMessage, revision);
    }

    private AnswerRevision stableRevision(String messageId, int revisionNo, String reasoning,
                                          String answer, long now) {
        DomainResult<AnswerRevision> created = AnswerRevision.create(messageId, revisionNo, reasoning,
                answer, GenerationModel.fromWireId(MODEL), true, POLICY_VERSION, now);
        assertTrue("fixture: the revision must be accepted: "
                + (created.isOk() ? "" : created.error()), created.isOk());
        AnswerRevisionDao.insert(db(), created.value());
        return created.value();
    }

    private static BranchSource provenance(Seeded source, String snapshotId, BranchAnchor anchor) {
        DomainResult<BranchSource> built = BranchSource.fromOperation(source.root.id,
                source.aiMessage.id, snapshotId, GenerationState.STABLE, source.revision.id, anchor);
        assertTrue("fixture: the provenance must be accepted: "
                + (built.isOk() ? "" : built.error()), built.isOk());
        return built.value();
    }

    private static Branch forkOf(Seeded source, String snapshotId, BranchAnchor anchor, long now) {
        DomainResult<Branch> fork = Branch.child(source.topicId, provenance(source, snapshotId, anchor),
                now);
        assertTrue("fixture: the fork must be accepted: " + (fork.isOk() ? "" : fork.error()),
                fork.isOk());
        return fork.value();
    }

    private static ContextSnapshotDao.Row snapshotRow(String snapshotId, String branchId, long now) {
        return new ContextSnapshotDao.Row(snapshotId, branchId, 1, DIGEST, PAYLOAD, now);
    }

    // ------------------------------------------------------------------
    // Raw-read helpers.
    // ------------------------------------------------------------------

    private SQLiteDatabase db() {
        return helper.getWritableDatabase();
    }

    private static android.content.Context context() {
        return InstrumentationRegistry.getInstrumentation().getTargetContext();
    }

    private int count(String sql, String... args) {
        try (Cursor cursor = db().rawQuery(sql, args)) {
            return cursor.moveToFirst() ? cursor.getInt(0) : Integer.MIN_VALUE;
        }
    }

    private int countBranch(String branchId) {
        return count("SELECT COUNT(*) FROM " + TreeLearnSchema.Branches.TABLE + " WHERE "
                + TreeLearnSchema.Branches.ID + "=?", branchId);
    }

    private int countSnapshotOf(String branchId) {
        return count("SELECT COUNT(*) FROM " + TreeLearnSchema.ContextSnapshots.TABLE + " WHERE "
                + TreeLearnSchema.ContextSnapshots.BRANCH_ID + "=?", branchId);
    }

    private String branchColumn(String branchId, String column) {
        try (Cursor cursor = db().rawQuery("SELECT " + column + " FROM "
                        + TreeLearnSchema.Branches.TABLE + " WHERE " + TreeLearnSchema.Branches.ID
                        + "=?", new String[]{branchId})) {
            return cursor.moveToFirst() ? cursor.getString(0) : null;
        }
    }

    private void assertNoDanglingReferences() {
        try (Cursor cursor = db().rawQuery("PRAGMA foreign_key_check", null)) {
            assertEquals("PRAGMA foreign_key_check must report no dangling reference", 0,
                    cursor.getCount());
        }
    }
}