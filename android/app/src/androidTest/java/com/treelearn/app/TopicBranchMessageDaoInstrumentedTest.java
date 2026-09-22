package com.treelearn.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.database.Cursor;
import android.database.sqlite.SQLiteConstraintException;
import android.database.sqlite.SQLiteDatabase;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.treelearn.core.domain.Branch;
import com.treelearn.core.domain.BranchAnchor;
import com.treelearn.core.domain.BranchSource;
import com.treelearn.core.domain.GenerationState;
import com.treelearn.core.domain.Message;
import com.treelearn.core.domain.MessageRole;
import com.treelearn.core.domain.Topic;
import com.treelearn.data.db.ConstraintExtendedCode;
import com.treelearn.data.db.DatabaseTransactionRunner;
import com.treelearn.data.db.TreeLearnDatabaseHelper;
import com.treelearn.data.db.TreeLearnSchema;
import com.treelearn.data.db.dao.BranchDao;
import com.treelearn.data.db.dao.MessageDao;
import com.treelearn.data.db.dao.TopicDao;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

/**
 * T-022 acceptance evidence for {@code topics} / {@code branches} / {@code messages}, executed on
 * real Android framework SQLite (API 36).
 *
 * <p>Rejections are asserted <em>by cause</em> using the SQLite extended result code, for the same
 * reason T-004 moved to them: a message substring can be satisfied by the wording of the constraint
 * rather than by its class. Codes measured in
 * {@code artifacts/evidence/T-004-constraint-code-probe.txt} (API 36) and
 * {@code artifacts/evidence/T-059-constraint-codes.txt} (Android 13), and read through the shared
 * {@link ConstraintExtendedCode} point rather than through a hand-written prefix.
 *
 * <p>Holds no real user content and no API key: every literal is synthetic (rules/sql.md).
 */
@RunWith(AndroidJUnit4.class)
public final class TopicBranchMessageDaoInstrumentedTest {

    private static final long T0 = 1_756_000_000_000L;

    /** Synthetic birth-snapshot id used by the fork fixtures. */
    private static final String SNAPSHOT_ID = "33333333-3333-3333-3333-333333333333";
    private static final String SOURCE_MESSAGE_ID = "11111111-1111-1111-1111-111111111111";
    private static final String SOURCE_REVISION_ID = "22222222-2222-2222-2222-222222222222";

    /** Synthetic model id; never a real credential. */
    private static final String MODEL = "deepseek-flash";

    /** Synthetic policy version; the body of that policy never appears here. */
    private static final String POLICY_VERSION = "tl-learning-answer/1.0.0";

    /**
     * Declared here independently; the reading of the code out of the refusal text is shared with
     * production through {@link ConstraintExtendedCode}. The reader matches the stable
     * {@code code <digits>} fragment rather than a prefix, because Android spells the same refusal as
     * {@code (code 2067 ...)} on API 36 and as {@code (Sqlite code 2067 ...)} on Android 13.
     */
    private static final int CODE_UNIQUE = 2067;
    private static final int CODE_CHECK = 275;
    private static final int CODE_NOT_NULL = 1299;

    private TreeLearnDatabaseHelper helper;
    private DatabaseTransactionRunner runner;

    @Before
    public void setUp() {
        helper = new TreeLearnDatabaseHelper(
                InstrumentationRegistry.getInstrumentation().getTargetContext());
        runner = new DatabaseTransactionRunner();
        context().deleteDatabase(TreeLearnDatabaseHelper.DATABASE_NAME);
    }

    @After
    public void tearDown() {
        if (helper != null) {
            helper.close();
        }
        context().deleteDatabase(TreeLearnDatabaseHelper.DATABASE_NAME);
    }

    private static android.content.Context context() {
        return InstrumentationRegistry.getInstrumentation().getTargetContext();
    }

    // ------------------------------------------------------------------
    // 1. Round trip: write, close, reopen, read back field by field.
    // ------------------------------------------------------------------

    @Test
    public void topicRoundTripsFieldByFieldAcrossCloseAndReopen() {
        SQLiteDatabase db = helper.getWritableDatabase();
        Topic written = Topic.create(T0);
        TopicDao.insert(db, written);

        Topic readBack = TopicDao.findById(db, written.id);
        assertNotNull("a written topic must be readable", readBack);
        assertEqualTopic(written, readBack);

        // lastVisitedBranchId starts null and is filled by a real navigation. It points at a
        // branch, so the row has to exist before the update — the foreign key rejects a topic that
        // claims to have visited a branch that was never persisted.
        Branch root = Branch.root(written.id, T0);
        BranchDao.insert(db, root);
        Topic visited = written.withLastVisitedBranch(root.id, T0 + 10);
        TopicDao.update(db, visited);
        Topic afterVisit = TopicDao.findById(db, written.id);
        assertNotNull(afterVisit);
        assertEquals("last visited branch survives", root.id, afterVisit.lastVisitedBranchId);
        assertEquals("updatedAt advanced", T0 + 10, afterVisit.updatedAt);
        assertEquals("createdAt never moves", T0, afterVisit.createdAt);

        helper.close();
        helper = new TreeLearnDatabaseHelper(context());
        SQLiteDatabase reopened = helper.getWritableDatabase();
        Topic afterReopen = TopicDao.findById(reopened, written.id);
        assertNotNull("the topic survives a close/reopen", afterReopen);
        assertEqualTopic(visited, afterReopen);
    }

    @Test
    public void branchRoundTripsFieldByFieldIncludingMutableState() {
        SQLiteDatabase db = helper.getWritableDatabase();
        Topic topic = Topic.create(T0);
        TopicDao.insert(db, topic);
        Branch root = Branch.root(topic.id, T0);
        BranchDao.insert(db, root);

        // Mutate every mutable field so the round trip cannot pass by leaving them at defaults.
        root.setTitle("title from the learner", T0 + 1);
        root.setCollapsed(true, T0 + 2);
        root.archive(T0 + 3);
        root.onStableAnswer(T0 + 4);
        root.onStableAnswer(T0 + 5);
        assertEquals(2L, root.latestStableSequence());
        BranchDao.update(db, root);

        Branch readBack = BranchDao.findById(db, root.id);
        assertNotNull("a written branch must be readable", readBack);
        assertEqualBranch(root, readBack);

        helper.close();
        helper = new TreeLearnDatabaseHelper(context());
        SQLiteDatabase reopened = helper.getWritableDatabase();
        Branch afterReopen = BranchDao.findById(reopened, root.id);
        assertNotNull("the branch survives a close/reopen", afterReopen);
        assertEqualBranch(root, afterReopen);
    }

    @Test
    public void messageRoundTripsBothRolesWithActiveReferences() {
        SQLiteDatabase db = helper.getWritableDatabase();
        Topic topic = Topic.create(T0);
        TopicDao.insert(db, topic);
        Branch branch = Branch.root(topic.id, T0);
        BranchDao.insert(db, branch);

        Message user = Message.user(branch.id, 0, "synthetic learner text", T0);
        MessageDao.insert(db, user);
        Message readUser = MessageDao.findById(db, user.id);
        assertNotNull(readUser);
        assertEqualMessage(user, readUser);
        assertNull("a USER row carries no active revision", readUser.activeRevisionId());
        assertNull("a USER row carries no active operation", readUser.activeOperationId());

        Message ai = Message.ai(branch.id, 1, T0 + 1);
        MessageDao.insert(db, ai);
        Message readAi = MessageDao.findById(db, ai.id);
        assertNotNull(readAi);
        assertEqualMessage(ai, readAi);
        assertNull("a fresh AI position has no revision yet", readAi.activeRevisionId());
    }

    // ------------------------------------------------------------------
    // 2. Non-root branch rehydration carries its full provenance (§5.2).
    // ------------------------------------------------------------------

    @Test
    public void nonRootBranchRehydratesWithProvenanceAndWholeAnswerAnchor() {
        SQLiteDatabase db = helper.getWritableDatabase();
        Topic topic = Topic.create(T0);
        TopicDao.insert(db, topic);
        Branch root = Branch.root(topic.id, T0);
        BranchDao.insert(db, root);

        // Whole-answer fork: offsets are null, but the version references are not (§5.2).
        seedForkSource(db, root.id);
        // Built through the creation entry point, not through restore: these cases exercise a real
        // fork, and restore is reserved for the read path (BranchDao.readSource). Using restore here
        // would both weaken acceptance condition 7 — which states that :data's production code holds
        // the only restore — and skip the STABLE rule that fromOperation is the sole enforcement of.
        BranchSource source = BranchSource.fromOperation(root.id, SOURCE_MESSAGE_ID, SNAPSHOT_ID,
                GenerationState.STABLE, SOURCE_REVISION_ID, BranchAnchor.wholeAnswer()).value();
        Branch child = Branch.child(topic.id, source, T0 + 1).value();
        // Two-step write, as the DAO documents: the snapshot row references the branch, so the
        // branch goes in first and points back at the snapshot afterwards. The snapshot row itself
        // is inserted directly because its DAO belongs to T-2026-09-21-024.
        BranchDao.insert(db, child);
        insertSnapshotRow(db, SNAPSHOT_ID, child.id);
        BranchDao.setContextSnapshot(db, child.id, SNAPSHOT_ID);

        Branch readBack = BranchDao.findById(db, child.id);
        assertNotNull("a written child branch must be readable", readBack);
        assertEqualBranch(child, readBack);

        BranchSource readSource = readBack.source;
        assertNotNull("a non-root branch must carry provenance (§5.2)", readSource);
        assertEquals("parent", source.parentBranchId, readSource.parentBranchId);
        assertEquals("source message", source.sourceMessageId, readSource.sourceMessageId);
        assertEquals("source revision", source.sourceRevisionId, readSource.sourceRevisionId);
        assertEquals("context snapshot", source.contextSnapshotId, readSource.contextSnapshotId);
        assertNotNull("the anchor itself", readSource.anchor);
        assertTrue("a whole-answer fork keeps an empty offset range",
                readSource.anchor.isWholeAnswer());
        assertNull("start offset is null for a whole-answer fork",
                readSource.anchor.startInclusive);
        assertNull("end offset is null for a whole-answer fork",
                readSource.anchor.endExclusive);
        // A whole-answer fork has no quote, hence no hash: there is no sub-range to fall back to.
        // The schema must not require anchor_hash, or this fork could never be written at all.
        assertNull("a whole-answer fork carries no quote and therefore no hash",
                readSource.anchor.hash);
        assertNull("and no quote", readSource.anchor.quote);
    }

    @Test
    public void rangedForkRoundTripsBothLocators() {
        SQLiteDatabase db = helper.getWritableDatabase();
        Topic topic = Topic.create(T0);
        TopicDao.insert(db, topic);
        Branch root = Branch.root(topic.id, T0);
        BranchDao.insert(db, root);
        seedForkSource(db, root.id);

        // The other legal anchor shape: offsets and the quote+hash fallback, all four present.
        // Offsets span exactly the quote, which is what BranchAnchor.ofRange enforces.
        BranchAnchor anchor = BranchAnchor.ofRange(0, 4, "abcd", "hash-of-abcd").value();
        BranchSource source = BranchSource.fromOperation(root.id, SOURCE_MESSAGE_ID, SNAPSHOT_ID,
                GenerationState.STABLE, SOURCE_REVISION_ID, anchor).value();
        Branch child = Branch.child(topic.id, source, T0 + 1).value();
        BranchDao.insert(db, child);
        insertSnapshotRow(db, SNAPSHOT_ID, child.id);
        BranchDao.setContextSnapshot(db, child.id, SNAPSHOT_ID);

        Branch readBack = BranchDao.findById(db, child.id);
        assertNotNull(readBack);
        assertEqualBranch(child, readBack);
        BranchAnchor readAnchor = readBack.source.anchor;
        assertFalse("a ranged fork is not a whole-answer fork", readAnchor.isWholeAnswer());
        assertEquals(Integer.valueOf(0), readAnchor.startInclusive);
        assertEquals(Integer.valueOf(4), readAnchor.endExclusive);
        assertEquals("the primary locator survives", "abcd", readAnchor.quote);
        assertEquals("the fallback locator survives", "hash-of-abcd", readAnchor.hash);
    }

    @Test
    public void readSourceFailsLoudOnARowTheCheckWouldHaveRejected() {
        SQLiteDatabase db = helper.getWritableDatabase();
        Topic topic = Topic.create(T0);
        TopicDao.insert(db, topic);
        Branch root = Branch.root(topic.id, T0);
        BranchDao.insert(db, root);
        seedForkSource(db, root.id);

        // The anchor CHECK makes these rows unwritable in normal use, so the read path is the
        // *second* line of defence and has to be provoked deliberately. Turning the CHECK off for a
        // single statement is what a future migration, a hand-run sqlite3 session, or a restored
        // backup looks like from inside :data — the branch table is shared with all of them.
        //
        // Two of the three rows need that, and they are exactly the two the CHECK would have refused
        // outright. The worse of them is the fallback row: start is null, so an earlier readSource
        // took the wholeAnswer() branch and dropped the quote and hash without a word, turning a
        // ranged anchor into an unrestorable one.
        //
        // The third row is the interesting one, and it is deliberately NOT an inverted range and
        // deliberately NOT planted with the CHECK off. A row with start >= end is refused by the
        // CHECK, so it would prove nothing about the read path's second half. This row is one the
        // store genuinely admits — all four columns present, start < end, which is all the CHECK
        // asks — and only BranchAnchor.ofRange refuses it, because the quote is not as long as the
        // range it claims to cover. That subset — "the store lets it through, the domain will not
        // have it" — is where the division of labour between the two is actually testable, and it
        // must surface the domain's own code rather than a bare "failure has no value".
        //
        // Planting that row with the CHECK *on* is what makes the first half of that claim device
        // evidence instead of a reading of the DDL: plantFork inserts through execSQL, so if the
        // store did reject this shape the statement below would throw SQLiteConstraintException and
        // the case would fail right here rather than pass on an assumption.
        String halfStart = "b-half-start";
        String halfFallback = "b-half-fallback";
        String contentRefused = "b-content-refused";
        try {
            db.execSQL("PRAGMA ignore_check_constraints=ON");
            plantFork(db, topic.id, root.id, halfStart, "0", null, null, null);
            plantFork(db, topic.id, root.id, halfFallback, null, null, "abcd", "hash-of-abcd");
        } finally {
            db.execSQL("PRAGMA ignore_check_constraints=OFF");
        }
        plantFork(db, topic.id, root.id, contentRefused, "0", "4", "ab", "hash-of-abcd");
        assertEquals("the planted rows must really be in the store, otherwise this case would pass"
                        + " on an empty table", 3,
                countWhere(db, TreeLearnSchema.Branches.TABLE,
                        "topic_id='" + topic.id + "' AND id<>'" + root.id + "'"));

        for (String id : new String[]{halfStart, halfFallback}) {
            IllegalStateException failure = assertThrows(IllegalStateException.class,
                    () -> BranchDao.findById(db, id));
            assertTrue(id + ": the read must name the half-filled anchor, not just fail: "
                            + failure.getMessage(),
                    failure.getMessage().contains("half-filled anchor"));
        }

        // A grouped anchor whose *content* the domain refuses: ofRange requires the quote to be
        // exactly as long as the range, and the read path must carry that refusal through with its
        // code instead of letting DomainResult.value() report a bare state failure.
        IllegalStateException refused = assertThrows(IllegalStateException.class,
                () -> BranchDao.findById(db, contentRefused));
        assertTrue("the read must surface the domain's own code instead of a bare state failure: "
                        + refused.getMessage(),
                refused.getMessage().contains("ANCHOR_INVALID"));
    }

    /**
     * Inserts a fork row exactly as given, bypassing the anchor CHECK's grouping rule.
     *
     * <p>The birth snapshot is attached with the same two-step write the production path owes, so
     * that the only thing wrong with the row is the anchor — otherwise the snapshot guard would be
     * what throws and the assertion would no longer be about the anchor.
     */
    private static void plantFork(SQLiteDatabase db, String topicId, String parentId, String id,
                                 String start, String end, String quote, String hash) {
        db.execSQL("INSERT INTO branches(id, topic_id, parent_id, title, created_at, updated_at,"
                        + " collapsed, source_message_id, source_revision_id, anchor_start_inclusive,"
                        + " anchor_end_exclusive, anchor_quote, anchor_hash, context_snapshot_id)"
                        + " VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                new Object[]{id, topicId, parentId, "", T0, T0, 0, SOURCE_MESSAGE_ID,
                        SOURCE_REVISION_ID, start, end, quote, hash, null});
        insertSnapshotRow(db, "s-" + id, id);
        db.execSQL("UPDATE branches SET context_snapshot_id=? WHERE id=?",
                new Object[]{"s-" + id, id});
    }

    // ------------------------------------------------------------------
    // 3. Constraints really bite, and a rejected write leaves nothing behind.
    // ------------------------------------------------------------------

    @Test
    public void duplicateBranchOrdinalIsRejectedAndLeavesNoPartialRow() {
        SQLiteDatabase db = helper.getWritableDatabase();
        Topic topic = Topic.create(T0);
        TopicDao.insert(db, topic);
        Branch branch = Branch.root(topic.id, T0);
        BranchDao.insert(db, branch);
        Message first = Message.user(branch.id, 3, "first", T0);
        MessageDao.insert(db, first);

        Message duplicate = Message.user(branch.id, 3, "second", T0 + 1);
        SQLiteConstraintException failure = assertThrows(SQLiteConstraintException.class,
                () -> MessageDao.insert(db, duplicate));
        assertCause("(branch_id, ordinal) must be unique", failure.getMessage(), CODE_UNIQUE,
                "messages.branch_id, messages.ordinal");

        assertEquals("the rejected insert left no row behind", 1,
                countWhere(db, "messages", "branch_id='" + branch.id + "'"));
        assertNull("the duplicate id was never persisted",
                MessageDao.findById(db, duplicate.id));
    }

    @Test
    public void secondRootBranchOfATopicIsRejected() {
        SQLiteDatabase db = helper.getWritableDatabase();
        Topic topic = Topic.create(T0);
        TopicDao.insert(db, topic);
        BranchDao.insert(db, Branch.root(topic.id, T0));

        Branch secondRoot = Branch.root(topic.id, T0 + 1);
        SQLiteConstraintException failure = assertThrows(SQLiteConstraintException.class,
                () -> BranchDao.insert(db, secondRoot));
        assertCause("a topic has exactly one root branch", failure.getMessage(), CODE_UNIQUE,
                "branches.topic_id");

        assertEquals("the rejected root left no row behind", 1,
                countWhere(db, "branches", "topic_id='" + topic.id + "'"));
    }

    // ------------------------------------------------------------------
    // 4. Deletion is carried by foreign keys, not by hand-written recursion.
    // ------------------------------------------------------------------

    @Test
    public void deletingATopicCascadesBranchesAndMessages() {
        SQLiteDatabase db = helper.getWritableDatabase();
        Topic topic = Topic.create(T0);
        TopicDao.insert(db, topic);
        Branch root = Branch.root(topic.id, T0);
        BranchDao.insert(db, root);
        MessageDao.insert(db, Message.user(root.id, 0, "text", T0));
        MessageDao.insert(db, Message.ai(root.id, 1, T0 + 1));

        assertEquals("two messages before the delete", 2, countWhere(db, "messages", "1=1"));

        TopicDao.delete(db, topic.id);

        assertEquals("branches are gone", 0, countWhere(db, "branches", "1=1"));
        assertEquals("messages are gone", 0, countWhere(db, "messages", "1=1"));
        assertEquals("no dangling references after the cascade", 0,
                rawRows(db, "PRAGMA foreign_key_check"));
        assertNull("the topic itself is gone", TopicDao.findById(db, topic.id));
    }

    @Test
    public void deletingABranchCascadesOnlyItsOwnSubtree() {
        SQLiteDatabase db = helper.getWritableDatabase();
        Topic topic = Topic.create(T0);
        TopicDao.insert(db, topic);
        Branch root = Branch.root(topic.id, T0);
        BranchDao.insert(db, root);
        seedForkSource(db, root.id);
        // Built through the creation entry point, not through restore: these cases exercise a real
        // fork, and restore is reserved for the read path (BranchDao.readSource). Using restore here
        // would both weaken acceptance condition 7 — which states that :data's production code holds
        // the only restore — and skip the STABLE rule that fromOperation is the sole enforcement of.
        BranchSource source = BranchSource.fromOperation(root.id, SOURCE_MESSAGE_ID, SNAPSHOT_ID,
                GenerationState.STABLE, SOURCE_REVISION_ID, BranchAnchor.wholeAnswer()).value();
        Branch child = Branch.child(topic.id, source, T0 + 1).value();
        BranchDao.insert(db, child);
        MessageDao.insert(db, Message.user(child.id, 0, "in the child", T0 + 2));
        MessageDao.insert(db, Message.user(root.id, 0, "in the root", T0 + 3));

        BranchDao.delete(db, child.id);

        assertNull("the child is gone", BranchDao.findById(db, child.id));
        assertNotNull("the root survives a subtree delete", BranchDao.findById(db, root.id));
        // Asserted per branch rather than as a total: the root hosts the fork source as well as its
        // own learner message, so a bare total would hide which side actually lost rows.
        assertEquals("the child's message went with the child", 0,
                countWhere(db, "messages", "branch_id='" + child.id + "'"));
        assertEquals("the root keeps both its own message and the fork source it hosts", 2,
                countWhere(db, "messages", "branch_id='" + root.id + "'"));
        assertEquals("the surviving learner message is the root's", "in the root",
                MessageDao.findByBranch(db, root.id).get(0).userContent);
        assertEquals("no dangling references", 0, rawRows(db, "PRAGMA foreign_key_check"));
    }

    // ------------------------------------------------------------------
    // 5. Queries used by the UI, and the transaction boundary.
    // ------------------------------------------------------------------

    @Test
    public void messagesOfABranchComeBackInOrdinalOrder() {
        SQLiteDatabase db = helper.getWritableDatabase();
        Topic topic = Topic.create(T0);
        TopicDao.insert(db, topic);
        Branch branch = Branch.root(topic.id, T0);
        BranchDao.insert(db, branch);
        MessageDao.insert(db, Message.user(branch.id, 2, "third", T0));
        MessageDao.insert(db, Message.user(branch.id, 0, "first", T0));
        MessageDao.insert(db, Message.ai(branch.id, 1, T0));

        List<Message> messages = MessageDao.findByBranch(db, branch.id);
        assertEquals("all three rows come back", 3, messages.size());
        assertEquals(0, messages.get(0).ordinal);
        assertEquals(1, messages.get(1).ordinal);
        assertEquals(2, messages.get(2).ordinal);
        assertEquals(MessageRole.AI, messages.get(1).role);
    }

    @Test
    public void rootBranchOfATopicIsFoundWithoutScanningChildren() {
        SQLiteDatabase db = helper.getWritableDatabase();
        Topic topic = Topic.create(T0);
        TopicDao.insert(db, topic);
        Branch root = Branch.root(topic.id, T0);
        BranchDao.insert(db, root);
        seedForkSource(db, root.id);
        // Built through the creation entry point, not through restore: these cases exercise a real
        // fork, and restore is reserved for the read path (BranchDao.readSource). Using restore here
        // would both weaken acceptance condition 7 — which states that :data's production code holds
        // the only restore — and skip the STABLE rule that fromOperation is the sole enforcement of.
        BranchSource source = BranchSource.fromOperation(root.id, SOURCE_MESSAGE_ID, SNAPSHOT_ID,
                GenerationState.STABLE, SOURCE_REVISION_ID, BranchAnchor.wholeAnswer()).value();
        Branch child = Branch.child(topic.id, source, T0 + 1).value();
        BranchDao.insert(db, child);
        insertSnapshotRow(db, SNAPSHOT_ID, child.id);
        BranchDao.setContextSnapshot(db, child.id, SNAPSHOT_ID);

        Branch found = BranchDao.findRootOfTopic(db, topic.id);
        assertNotNull("the root of the topic is findable", found);
        assertEquals("and it is the root, not a child", root.id, found.id);
        assertTrue("the root carries no provenance", found.isRoot());

        List<Branch> all = BranchDao.findByTopic(db, topic.id);
        assertEquals("the topic has both branches", 2, all.size());
    }

    @Test
    public void aFailedMultiRowWriteLeavesNothingBehind() {
        SQLiteDatabase db = helper.getWritableDatabase();
        Topic topic = Topic.create(T0);
        TopicDao.insert(db, topic);
        Branch branch = Branch.root(topic.id, T0);
        BranchDao.insert(db, branch);

        // The first message is legal; the second collides on (branch_id, ordinal). Both are written
        // inside one runner transaction, so neither may survive.
        assertThrows(SQLiteConstraintException.class, () -> runner.run(db, work -> {
            MessageDao.insert(work, Message.user(branch.id, 0, "first", T0));
            MessageDao.insert(work, Message.user(branch.id, 0, "collides", T0 + 1));
            return null;
        }));

        assertEquals("the whole unit was rolled back, not half of it", 0,
                countWhere(db, "messages", "1=1"));
    }

    // ------------------------------------------------------------------
    // 6. Schema invariants the DAO must not be able to bypass.
    // ------------------------------------------------------------------

    /**
     * A null identity would be a data-loss bug, not a business outcome, and the store is what stops
     * it. The assertion is deliberately made through the store rather than through a DAO: the DAO's
     * own guards reject a null id earlier, so routing this through a DAO would prove nothing about
     * the NOT NULL column it names.
     *
     * <p>It is <em>not</em> claimed here that the DAOs emit no DDL. That is a property of their
     * source text, and it is enforced by the Java-only guard and by the one-off DDL-location search
     * in {@code artifacts/evidence/T-004-ddl-location.txt}; a behavioural test cannot witness it.
     */
    @Test
    public void theStoreRejectsANullIdentityOnANotNullColumn() {
        SQLiteDatabase db = helper.getWritableDatabase();
        Topic topic = Topic.create(T0);
        TopicDao.insert(db, topic);
        Branch branch = Branch.root(topic.id, T0);
        BranchDao.insert(db, branch);
        MessageDao.insert(db, Message.user(branch.id, 0, "text", T0));

        // A null identity on a NOT NULL column would be a data-loss bug, not a business outcome.
        SQLiteConstraintException failure = assertThrows(SQLiteConstraintException.class,
                () -> db.execSQL("INSERT INTO messages(id, branch_id, ordinal, role, user_content,"
                                + " created_at) VALUES(?,?,?,?,?,?)",
                        new Object[]{"m-null-branch", null, 9, "USER", "x", T0}));
        assertCause("branch_id is mandatory", failure.getMessage(), CODE_NOT_NULL,
                "messages.branch_id");
    }

    /**
     * The four reading-position columns exist and are declared as INTEGER offsets.
     *
     * <p><strong>Known coverage gap, deliberate:</strong> nothing reads or writes these columns yet.
     * A grep over the whole repository finds only the DDL, T-004's column-type assertion and this
     * case. Reading position belongs to T-009, and {@code Branch} has no field for any of them, so
     * there is nothing to round-trip here — an earlier version of this case was named
     * {@code …ArePersistedAsOffsetsNotAsScrollState}, which promised a persistence behaviour it never
     * exercised. The name now states what it checks, and the gap is recorded in the task's Gaps
     * section instead of being implied away by a test name.
     */
    @Test
    public void readingPositionColumnsExistAsIntegerOffsets() {
        SQLiteDatabase db = helper.getWritableDatabase();

        for (String column : new String[]{TreeLearnSchema.Branches.READING_ANCHOR_MESSAGE_ID,
                TreeLearnSchema.Branches.READING_ANCHOR_REVISION_ID,
                TreeLearnSchema.Branches.READING_LIST_ITEM_OFFSET,
                TreeLearnSchema.Branches.READING_VIEWPORT_OFFSET}) {
            assertTrue("branches must expose " + column, branchHasColumn(db, column));
        }
        // §11 stores offsets as integers so a position is comparable and compact, never as an opaque
        // scroll blob. A declared type of TEXT here would break that without failing any other case.
        assertTrue("the list-item offset must be an integer offset",
                branchColumnIsInteger(db, TreeLearnSchema.Branches.READING_LIST_ITEM_OFFSET));
        assertTrue("the viewport offset must be an integer offset",
                branchColumnIsInteger(db, TreeLearnSchema.Branches.READING_VIEWPORT_OFFSET));
    }

    /**
     * Creates the message and stable revision a fork anchors on.
     *
     * <p>They must really exist: branches.source_message_id and branches.source_revision_id
     * carry foreign keys, so a provenance record pointing at ids that were never persisted is
     * rejected. The rows are written directly because their DAOs belong to T-2026-09-21-023.
     */
    private static void seedForkSource(SQLiteDatabase db, String branchId) {
        db.execSQL("INSERT INTO messages(id, branch_id, ordinal, role, user_content,"
                        + " created_at) VALUES(?,?,?,?,?,?)",
                // ordinal 99 keeps the source row out of the way of the cases' own messages,
                // which legitimately start at 0 — (branch_id, ordinal) is unique.
                new Object[]{SOURCE_MESSAGE_ID, branchId, 99, "AI", null, T0});
        db.execSQL("INSERT INTO answer_revisions(id, message_id, revision_no,"
                        + " reasoning_content, answer_content, model, thinking_enabled,"
                        + " prompt_policy_version, created_at) VALUES(?,?,?,?,?,?,?,?,?)",
                new Object[]{SOURCE_REVISION_ID, SOURCE_MESSAGE_ID, 1, "", "answer", MODEL, 1,
                        POLICY_VERSION, T0});
    }

    /** Inserts a birth-snapshot row; its DAO belongs to T-2026-09-21-024. */
    private static void insertSnapshotRow(SQLiteDatabase db, String id, String branchId) {
        db.execSQL("INSERT INTO context_snapshots(id, branch_id, schema_version, digest,"
                        + " payload, created_at) VALUES(?,?,?,?,?,?)",
                new Object[]{id, branchId, 1, "digest-" + id, "[]", T0});
    }

    // ------------------------------------------------------------------
    // Assertion helpers.
    // ------------------------------------------------------------------

    private static void assertEqualTopic(Topic expected, Topic actual) {
        assertEquals("topic id", expected.id, actual.id);
        assertEquals("topic last visited branch", expected.lastVisitedBranchId,
                actual.lastVisitedBranchId);
        assertEquals("topic createdAt", expected.createdAt, actual.createdAt);
        assertEquals("topic updatedAt", expected.updatedAt, actual.updatedAt);
    }

    private static void assertEqualBranch(Branch expected, Branch actual) {
        assertEquals("branch id", expected.id, actual.id);
        assertEquals("branch topicId", expected.topicId, actual.topicId);
        assertEquals("branch parentBranchId", expected.parentBranchId, actual.parentBranchId);
        assertEquals("branch title", expected.title(), actual.title());
        assertEquals("branch createdAt", expected.createdAt, actual.createdAt);
        assertEquals("branch updatedAt", expected.updatedAt(), actual.updatedAt());
        assertEquals("branch latestStableSequence", expected.latestStableSequence(),
                actual.latestStableSequence());
        assertEquals("branch lastSeenStableSequence", expected.lastSeenStableSequence(),
                actual.lastSeenStableSequence());
        assertEquals("branch unreadCount is derived, never stored", expected.unreadCount(),
                actual.unreadCount());
        assertEquals("branch collapsed", expected.isCollapsed(), actual.isCollapsed());
        assertEquals("branch archived", expected.isArchived(), actual.isArchived());
        assertEquals("branch archivedAt", expected.archivedAt(), actual.archivedAt());
        if (expected.isRoot()) {
            assertNull("a root branch carries no provenance", actual.source);
        } else {
            assertNotNull("a non-root branch carries provenance", actual.source);
        }
    }

    private static void assertEqualMessage(Message expected, Message actual) {
        assertEquals("message id", expected.id, actual.id);
        assertEquals("message branchId", expected.branchId, actual.branchId);
        assertEquals("message role", expected.role, actual.role);
        assertEquals("message ordinal", expected.ordinal, actual.ordinal);
        assertEquals("message userContent", expected.userContent, actual.userContent);
        assertEquals("message createdAt", expected.createdAt, actual.createdAt);
        assertEquals("message activeRevisionId", expected.activeRevisionId(),
                actual.activeRevisionId());
        assertEquals("message activeOperationId", expected.activeOperationId(),
                actual.activeOperationId());
    }

    private static void assertCause(String scenario, String message, int expectedCode,
                                    String requiredText) {
        int actualCode = ConstraintExtendedCode.of(message);
        assertTrue(scenario + ": expected code " + expectedCode + " but got code " + actualCode
                        + " in: " + message,
                actualCode == expectedCode);
        if (requiredText != null) {
            assertTrue(scenario + ": the failure must name " + requiredText + " but was " + message,
                    message.contains(requiredText));
        }
    }

    private static int countWhere(SQLiteDatabase db, String table, String where) {
        return rawInt(db, "SELECT COUNT(*) FROM " + table + " WHERE " + where);
    }

    private static int rawInt(SQLiteDatabase db, String sql) {
        try (Cursor cursor = db.rawQuery(sql, null)) {
            return cursor.moveToFirst() ? cursor.getInt(0) : Integer.MIN_VALUE;
        }
    }

    private static int rawRows(SQLiteDatabase db, String sql) {
        try (Cursor cursor = db.rawQuery(sql, null)) {
            return cursor.getCount();
        }
    }

    private static boolean branchHasColumn(SQLiteDatabase db, String column) {
        try (Cursor cursor = db.rawQuery("PRAGMA table_info(" + TreeLearnSchema.Branches.TABLE + ")",
                null)) {
            while (cursor.moveToNext()) {
                if (column.equals(cursor.getString(cursor.getColumnIndexOrThrow("name")))) {
                    return true;
                }
            }
        }
        return false;
    }

    /** @return whether the column is declared with SQLite's INTEGER affinity (§11 offsets) */
    private static boolean branchColumnIsInteger(SQLiteDatabase db, String column) {
        try (Cursor cursor = db.rawQuery("PRAGMA table_info(" + TreeLearnSchema.Branches.TABLE + ")",
                null)) {
            while (cursor.moveToNext()) {
                if (column.equals(cursor.getString(cursor.getColumnIndexOrThrow("name")))) {
                    return "INTEGER".equalsIgnoreCase(
                            cursor.getString(cursor.getColumnIndexOrThrow("type")));
                }
            }
        }
        return false;
    }
}
