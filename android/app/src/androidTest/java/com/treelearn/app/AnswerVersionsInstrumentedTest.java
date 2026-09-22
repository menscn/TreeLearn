package com.treelearn.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.treelearn.app.generation.AnswerVersions;
import com.treelearn.core.domain.AnswerRevision;
import com.treelearn.core.domain.Branch;
import com.treelearn.core.domain.BranchAnchor;
import com.treelearn.core.domain.BranchSource;
import com.treelearn.core.domain.DomainErrorCode;
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
import com.treelearn.data.db.dao.AnswerRevisionDao;
import com.treelearn.data.db.dao.BranchCreation;
import com.treelearn.data.db.dao.BranchDao;
import com.treelearn.data.db.dao.ContextSnapshotDao;
import com.treelearn.data.db.dao.GenerationOperationDao;
import com.treelearn.data.db.dao.MessageDao;
import com.treelearn.data.db.dao.TopicDao;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

/**
 * T-010 acceptance evidence for retry, reset and version selection, on real Android framework SQLite.
 *
 * <p>What the JVM suite cannot reach is the part that makes these commands safe rather than merely
 * legal: that a refused command writes <em>nothing</em> (no operation row, no moved pointer), that a
 * retry adds no learner message and consumes no turn, and that switching the displayed version leaves
 * a child branch on the revision it was forked from. Those are properties of the transaction around
 * the rows, so they are asserted here against the real store.
 *
 * <p><strong>Source-only this round.</strong> Per the repository owner's 2026-09-21 instruction the
 * emulator is not used, so nothing in this class was executed; it is kept so the coverage exists and
 * is compiled by {@code :app:assembleDebugAndroidTest}. Every device-only acceptance point of T-010 is
 * recorded as deferred, not as a pass.
 *
 * <p>Holds no real user content and no API key: every literal is synthetic.
 */
@RunWith(AndroidJUnit4.class)
public final class AnswerVersionsInstrumentedTest {

    private static final long T0 = 1_756_000_000_000L;

    /** Synthetic policy version, owned by {@link PromptPurpose#LEARNING_ANSWER}. */
    private static final String POLICY_VERSION = "tl-learning-answer/1.0.0";

    /** Synthetic question and answers; never real learner content. */
    private static final String QUESTION = "synthetic question: why is the sky blue?";
    private static final String ANSWER_ONE = "synthetic answer one, the first stable version";
    private static final String ANSWER_TWO = "synthetic answer two, generated later";
    private static final String ANSWER_ELSEWHERE = "synthetic answer of a different position";

    private static final String SNAPSHOT_ID = "33333333-3333-4333-8333-000000000003";
    private static final String PAYLOAD = "{\"schema\":1,\"entries\":[]}";
    private static final String DIGEST = "sha256-of-the-synthetic-context";

    /** A well-formed UUID that no row carries, so "not found" is tested rather than "malformed". */
    private static final String MISSING_MESSAGE_ID = "99999999-9999-4999-8999-000000000009";

    private TreeLearnDatabaseHelper helper;
    private DatabaseTransactionRunner runner;
    private BranchCreation creation;
    private AnswerVersions versions;

    @Before
    public void setUp() {
        context().deleteDatabase(TreeLearnDatabaseHelper.DATABASE_NAME);
        helper = new TreeLearnDatabaseHelper(context());
        runner = new DatabaseTransactionRunner();
        creation = new BranchCreation(runner);
        versions = new AnswerVersions(runner);
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
    // 1. A retry is a new attempt at the same position, and nothing else.
    // ------------------------------------------------------------------

    @Test
    public void aRetryAddsNoMessageAndConsumesNoTurn() {
        SQLiteDatabase db = database();
        Seeded seeded = seedConversation(db);
        int messagesBefore = count(db, "messages");

        DomainResult<GenerationCommand> retried = versions.retry(db, seeded.answer.id,
                GenerationModel.FLASH, true, POLICY_VERSION, T0 + 10);

        assertTrue("a position with no attempt in flight must be retryable: " + retried,
                retried.isOk());
        GenerationCommand command = retried.value();
        assertEquals("same branch", seeded.root.id, command.branchId);
        assertEquals("same position: a retry adds no learner message and no turn",
                seeded.answer.id, command.messageId);
        assertEquals("same usage", PromptPurpose.LEARNING_ANSWER, command.purpose);
        assertEquals("the model the attempt is admitted with", GenerationModel.FLASH, command.model);
        assertTrue("the thinking flag is the caller's, not re-read from a later revision",
                command.thinkingEnabled);
        assertEquals("the version is pinned on the command", POLICY_VERSION,
                command.promptPolicyVersion);

        GenerationCommand again = versions.retry(db, seeded.answer.id, GenerationModel.FLASH, true,
                POLICY_VERSION, T0 + 11).value();
        assertNotEquals("a retry is a new attempt, not a re-run of the same command",
                command.id, again.id);

        assertEquals("no learner message was duplicated", messagesBefore, count(db, "messages"));
        assertEquals("a retry adds no turn, so the position keeps ordinal 1", 1,
                MessageDao.findById(db, seeded.answer.id).ordinal);
        assertEquals("this use case writes no operation row; the coordinator writes the one attempt",
                0, count(db, "generation_operations"));
    }

    @Test
    public void aRetryWhileAnAttemptIsInFlightIsRefusedAndWritesNothing() {
        SQLiteDatabase db = database();
        Seeded seeded = seedConversation(db);
        GenerationCommand running = commandFor(seeded, T0);
        GenerationOperationDao.insert(db, GenerationOperation.start(running, T0));
        int operationsBefore = count(db, "generation_operations");

        DomainResult<GenerationCommand> refused = versions.retry(db, seeded.answer.id,
                GenerationModel.PRO, false, POLICY_VERSION, T0 + 10);

        assertTrue("a second attempt in the same branch must be refused: " + refused,
                refused.isFailure());
        assertEquals(DomainErrorCode.ILLEGAL_STATE_TRANSITION, refused.error().code);
        assertEquals("a refused retry writes nothing", operationsBefore,
                count(db, "generation_operations"));
        assertNull("and it leaves the displayed version alone",
                MessageDao.findById(db, seeded.answer.id).activeRevisionId());
        assertEquals("no dangling references", 0, rawRows(db, "PRAGMA foreign_key_check"));
    }

    @Test
    public void aRetryOfAMissingPositionIsRefusedAsAGonePosition() {
        SQLiteDatabase db = database();
        seedConversation(db);

        DomainResult<GenerationCommand> refused = versions.retry(db, MISSING_MESSAGE_ID,
                GenerationModel.FLASH, false, POLICY_VERSION, T0);

        assertTrue("a position that is gone cannot be retried: " + refused, refused.isFailure());
        assertEquals(DomainErrorCode.OPERATION_NOT_COMPLETABLE, refused.error().code);
        assertEquals("nothing was written", 0, count(db, "generation_operations"));
    }

    // ------------------------------------------------------------------
    // 2. A reset needs something to replace.
    // ------------------------------------------------------------------

    @Test
    public void aResetWithoutAStableVersionIsRefusedAndChangesNothing() {
        SQLiteDatabase db = database();
        Seeded seeded = seedConversation(db);

        DomainResult<GenerationCommand> refused = versions.reset(db, seeded.answer.id,
                GenerationModel.FLASH, false, POLICY_VERSION, T0 + 10);

        assertTrue("there is nothing to regenerate yet: " + refused, refused.isFailure());
        assertEquals(DomainErrorCode.OPERATION_NOT_STABLE, refused.error().code);
        assertEquals("nothing was written", 0, count(db, "generation_operations"));
        assertEquals("nor was a version invented", 0,
                AnswerRevisionDao.findByMessage(db, seeded.answer.id).size());
    }

    @Test
    public void aResetOfAShownStableVersionIsAllowedAndAddsNoTurn() {
        SQLiteDatabase db = database();
        Seeded seeded = seedConversation(db);
        show(db, seeded.answer, stableRevision(db, seeded.answer, 1, ANSWER_ONE, T0 + 1).id);
        int messagesBefore = count(db, "messages");

        DomainResult<GenerationCommand> reset = versions.reset(db, seeded.answer.id,
                GenerationModel.PRO, true, POLICY_VERSION, T0 + 10);

        assertTrue("a displayed stable version may be regenerated: " + reset, reset.isOk());
        assertEquals("still the same position", seeded.answer.id, reset.value().messageId);
        assertEquals("the learner's turn was not duplicated", messagesBefore, count(db, "messages"));
        assertEquals("and the old version is still there to switch back to", 1,
                AnswerRevisionDao.findByMessage(db, seeded.answer.id).size());
    }

    // ------------------------------------------------------------------
    // 3. Selecting a version moves the pointer and nothing else.
    // ------------------------------------------------------------------

    @Test
    public void selectingAnOlderVersionMovesOnlyTheDisplayPointer() {
        SQLiteDatabase db = database();
        Seeded seeded = seedConversation(db);
        AnswerRevision first = stableRevision(db, seeded.answer, 1, ANSWER_ONE, T0 + 1);
        show(db, seeded.answer, first.id);
        AnswerRevision second = stableRevision(db, seeded.answer, 2, ANSWER_TWO, T0 + 2);
        show(db, seeded.answer, second.id);

        DomainResult<AnswerRevision> switched = versions.selectRevision(db, seeded.answer.id,
                first.id, T0 + 3);

        assertTrue("an older version of the same position may be shown: " + switched,
                switched.isOk());
        assertEquals("the caller is told which version is now displayed", first.id,
                switched.value().id);
        assertEquals("the pointer really moved", first.id,
                MessageDao.findById(db, seeded.answer.id).activeRevisionId());
        assertEquals("both versions are still stored, so the switch overwrote nothing", 2,
                AnswerRevisionDao.findByMessage(db, seeded.answer.id).size());
        assertEquals("the newer version is untouched", ANSWER_TWO,
                AnswerRevisionDao.findById(db, second.id).answerContent);
        assertEquals("and the position is still one turn", 1,
                MessageDao.findById(db, seeded.answer.id).ordinal);
    }

    @Test
    public void selectingTheVersionAlreadyShownIsRefusedAndMovesNothing() {
        SQLiteDatabase db = database();
        Seeded seeded = seedConversation(db);
        AnswerRevision first = stableRevision(db, seeded.answer, 1, ANSWER_ONE, T0 + 1);
        show(db, seeded.answer, first.id);

        DomainResult<AnswerRevision> refused = versions.selectRevision(db, seeded.answer.id,
                first.id, T0 + 3);

        assertTrue("re-selecting the shown version is not a change: " + refused, refused.isFailure());
        assertEquals(DomainErrorCode.REVISION_IMMUTABLE, refused.error().code);
        assertEquals("the pointer did not move", first.id,
                MessageDao.findById(db, seeded.answer.id).activeRevisionId());
    }

    @Test
    public void selectingAVersionOfAnotherPositionIsRefused() {
        SQLiteDatabase db = database();
        Seeded seeded = seedConversation(db);
        Message elsewhere = Message.ai(seeded.root.id, 2, T0);
        MessageDao.insert(db, elsewhere);
        AnswerRevision foreign = stableRevision(db, elsewhere, 1, ANSWER_ELSEWHERE, T0 + 2);

        DomainResult<AnswerRevision> refused = versions.selectRevision(db, seeded.answer.id,
                foreign.id, T0 + 3);

        assertTrue("another position's answer must not be shown here: " + refused,
                refused.isFailure());
        assertEquals(DomainErrorCode.OPERATION_NOT_COMPLETABLE, refused.error().code);
        assertNull("the refused switch left this position showing nothing",
                MessageDao.findById(db, seeded.answer.id).activeRevisionId());
        assertEquals("and the other position was not touched either",
                MessageDao.findById(db, elsewhere.id).ordinal, elsewhere.ordinal);
    }

    @Test
    public void versionHistoryComesBackOldestFirstAndIsNotTheCallersToMutate() {
        SQLiteDatabase db = database();
        Seeded seeded = seedConversation(db);
        stableRevision(db, seeded.answer, 1, ANSWER_ONE, T0 + 1);
        stableRevision(db, seeded.answer, 2, ANSWER_TWO, T0 + 2);

        DomainResult<List<AnswerRevision>> history = versions.versions(db, seeded.answer.id);

        assertTrue("the history of a position is readable: " + history, history.isOk());
        assertEquals("both versions are listed", 2, history.value().size());
        assertEquals("oldest first", 1, history.value().get(0).revisionNo);
        assertEquals("oldest first", 2, history.value().get(1).revisionNo);
        assertThrows(UnsupportedOperationException.class,
                () -> history.value().add(null));
    }

    // ------------------------------------------------------------------
    // 4. A child branch keeps the version it was forked from.
    // ------------------------------------------------------------------

    @Test
    public void aChildBranchKeepsItsPinnedRevisionAndTextAfterTheParentSwitchesBack() {
        SQLiteDatabase db = database();
        Seeded seeded = seedConversation(db);
        AnswerRevision first = stableRevision(db, seeded.answer, 1, ANSWER_ONE, T0 + 1);
        show(db, seeded.answer, first.id);
        AnswerRevision second = stableRevision(db, seeded.answer, 2, ANSWER_TWO, T0 + 2);
        show(db, seeded.answer, second.id);

        Branch child = forkFrom(db, seeded, first.id, T0 + 4);
        assertEquals("fixture: the fork pins the first version", first.id,
                BranchDao.findById(db, child.id).source.sourceRevisionId);

        DomainResult<AnswerRevision> switchedBack = versions.selectRevision(db, seeded.answer.id,
                first.id, T0 + 5);

        assertTrue("the parent may switch back to the version the child was forked from: "
                + switchedBack, switchedBack.isOk());
        assertEquals("the child still pins the revision it was forked from", first.id,
                BranchDao.findById(db, child.id).source.sourceRevisionId);
        assertFalse("which is not the version the parent displayed a moment ago",
                second.id.equals(BranchDao.findById(db, child.id).source.sourceRevisionId));
        assertEquals("the parent really did move", first.id,
                MessageDao.findById(db, seeded.answer.id).activeRevisionId());
        assertEquals("and the pinned revision still holds the text it was frozen with", ANSWER_ONE,
                AnswerRevisionDao.findById(db, first.id).answerContent);
        assertEquals("no dangling references", 0, rawRows(db, "PRAGMA foreign_key_check"));
    }

    // ------------------------------------------------------------------
    // Fixtures.
    // ------------------------------------------------------------------

    private static final class Seeded {

        final Topic topic;
        final Branch root;
        final Message question;
        final Message answer;

        Seeded(Topic topic, Branch root, Message question, Message answer) {
            this.topic = topic;
            this.root = root;
            this.question = question;
            this.answer = answer;
        }
    }

    private SQLiteDatabase database() {
        return helper.getWritableDatabase();
    }

    /** A topic with its root branch, the learner's question and the AI answer position. */
    private static Seeded seedConversation(SQLiteDatabase db) {
        Topic topic = Topic.create(T0);
        TopicDao.insert(db, topic);
        Branch root = Branch.root(topic.id, T0);
        BranchDao.insert(db, root);
        Message question = Message.user(root.id, 0, QUESTION, T0);
        MessageDao.insert(db, question);
        Message answer = Message.ai(root.id, 1, T0);
        MessageDao.insert(db, answer);
        return new Seeded(topic, root, question, answer);
    }

    private static GenerationCommand commandFor(Seeded seeded, long now) {
        return GenerationCommand.create(seeded.root.id, seeded.answer.id,
                PromptPurpose.LEARNING_ANSWER, POLICY_VERSION, GenerationModel.FLASH, true, now)
                .value();
    }

    /** Freezes one stable version of a position, as the stabilizer would. */
    private static AnswerRevision stableRevision(SQLiteDatabase db, Message position, int revisionNo,
            String answer, long now) {
        DomainResult<AnswerRevision> created = AnswerRevision.create(position.id, revisionNo,
                "synthetic reasoning " + revisionNo, answer, GenerationModel.FLASH, true,
                POLICY_VERSION, now);
        assertTrue("fixture: the revision must be creatable: " + created, created.isOk());
        AnswerRevisionDao.insert(db, created.value());
        return created.value();
    }

    /** Points the position at a revision the way the coordinator does, so fixtures stay reachable. */
    private static void show(SQLiteDatabase db, Message position, String revisionId) {
        Message reloaded = MessageDao.findById(db, position.id);
        DomainResult<Void> switched = reloaded.activateRevision(revisionId);
        assertTrue("fixture: the position must be able to display " + revisionId + ": " + switched,
                switched.isOk());
        MessageDao.updateActiveReferences(db, reloaded);
    }

    /**
     * Creates a real child branch through {@link BranchCreation}, so the fixture is a state the
     * production path produces rather than a hand-written row.
     */
    private Branch forkFrom(SQLiteDatabase db, Seeded seeded, String revisionId, long now) {
        BranchSource provenance = BranchSource.fromOperation(seeded.root.id, seeded.answer.id,
                SNAPSHOT_ID, GenerationState.STABLE, revisionId, BranchAnchor.wholeAnswer()).value();
        Branch child = Branch.child(seeded.topic.id, provenance, now).value();
        BranchCreation.Outcome outcome = creation.create(db, child,
                new ContextSnapshotDao.Row(SNAPSHOT_ID, child.id, 1, DIGEST, PAYLOAD, now));
        assertTrue("fixture: the fork must commit: " + outcome, outcome.isCreated());
        return outcome.branch;
    }

    // ------------------------------------------------------------------
    // Raw-read helpers.
    // ------------------------------------------------------------------

    private static int count(SQLiteDatabase db, String table) {
        return rawInt(db, "SELECT COUNT(*) FROM " + table);
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
}