package com.treelearn.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import android.database.Cursor;
import android.database.sqlite.SQLiteConstraintException;
import android.database.sqlite.SQLiteDatabase;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.treelearn.core.domain.AnswerRevision;
import com.treelearn.core.domain.Branch;
import com.treelearn.core.domain.DomainErrorCode;
import com.treelearn.core.domain.DomainResult;
import com.treelearn.core.domain.GenerationCommand;
import com.treelearn.core.domain.GenerationModel;
import com.treelearn.core.domain.GenerationOperation;
import com.treelearn.core.domain.GenerationState;
import com.treelearn.core.domain.Message;
import com.treelearn.core.domain.PromptPurpose;
import com.treelearn.core.domain.Topic;
import com.treelearn.data.db.ConstraintExtendedCode;
import com.treelearn.data.db.DatabaseTransactionRunner;
import com.treelearn.data.db.TreeLearnDatabaseHelper;
import com.treelearn.data.db.TreeLearnSchema;
import com.treelearn.data.db.dao.AnswerRevisionDao;
import com.treelearn.data.db.dao.BranchDao;
import com.treelearn.data.db.dao.GenerationOperationDao;
import com.treelearn.data.db.dao.GenerationOperationRow;
import com.treelearn.data.db.dao.GenerationStabilizer;
import com.treelearn.data.db.dao.MessageDao;
import com.treelearn.data.db.dao.TopicDao;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * T-023 slice A acceptance evidence for {@code answer_revisions} / {@code generation_operations},
 * executed on real Android framework SQLite (API 36).
 *
 * <p>Three properties get the most attention here because they are the ones a plausible-looking
 * implementation gets wrong:
 *
 * <ul>
 *   <li><strong>The pinned policy version is copied from the operation row.</strong> Reading it from
 *       the row and re-deriving it from the command produce the <em>same</em> string in every legal
 *       case, so only a deliberately tampered row can tell the two implementations apart. See
 *       {@link #stabilizeCopiesThePinnedVersionFromTheRowVerbatim}.
 *   <li><strong>Stabilization owns its transaction.</strong> The pair (revision row, operation moved
 *       to {@code STABLE}) is all-or-nothing, and the boundary is the entry point's, not the caller's
 *       — asserted by forcing a refusal inside an outer transaction and by forcing a mid-transaction
 *       store refusal.
 *   <li><strong>Frozen attempts stay frozen.</strong> A late delta after {@code STABLE} is refused by
 *       the <em>store</em>, not by the DAO's good manners.
 * </ul>
 *
 * <p>Rejections are asserted <em>by cause</em> using the SQLite extended result code, for the same
 * reason T-004 and T-022 moved to them: a message substring can be satisfied by the wording of the
 * constraint rather than by its class. Codes measured in
 * {@code artifacts/evidence/T-004-constraint-code-probe.txt} (API 36) and
 * {@code artifacts/evidence/T-059-constraint-codes.txt} (Android 13), and read through the shared
 * {@link ConstraintExtendedCode} point rather than through a hand-written prefix.
 *
 * <p>Holds no real user content and no API key: every literal is synthetic (rules/sql.md), and the
 * "prompt body" sentinel exists precisely so that its absence from storage can be scanned for.
 */
@RunWith(AndroidJUnit4.class)
public final class AnswerRevisionGenerationOperationDaoInstrumentedTest {

    private static final long T0 = 1_756_000_000_000L;

    /** Synthetic model id; never a real credential. */
    private static final String MODEL = "deepseek-flash";
    /** Synthetic model id of the other tier, so a round trip cannot pass by leaving it at default. */
    private static final String PRO_MODEL = "deepseek-v4-pro";

    /** Synthetic policy version, owned by {@link PromptPurpose#LEARNING_ANSWER}. */
    private static final String POLICY_VERSION = "tl-learning-answer/1.0.0";
    /**
     * A legal version owned by a <em>different</em> purpose. The tampered-row case needs a version
     * the domain accepts but the command never pinned — an illegal string would be refused by
     * {@code AnswerRevision.create} and would prove nothing about where the value came from.
     */
    private static final String FOREIGN_POLICY_VERSION = "tl-branch-title/1.0.0";

    /**
     * A prompt-body sentinel. It is not an input any write path in this task accepts, so finding it
     * in either table would mean a column or a DAO had started persisting prompt text.
     */
    private static final String PROMPT_BODY_SENTINEL = "tl-system-prompt-body-do-not-persist";

    /** A well-formed UUID that no row carries, so "not found" is tested rather than "malformed". */
    private static final String MISSING_OPERATION_ID = "99999999-9999-9999-9999-999999999999";

    /**
     * The codes are declared here independently, while the <em>reading</em> of the code out of the
     * refusal text is shared with production through {@link ConstraintExtendedCode}: sharing the
     * reader keeps the two from drifting, sharing the value would make the assertion a mirror of the
     * implementation. The reader matches the stable {@code code <digits>} fragment rather than a
     * prefix, because Android spells the same refusal as {@code (code 2067 ...)} on API 36 and as
     * {@code (Sqlite code 2067 ...)} on Android 13.
     */
    private static final int CODE_UNIQUE = 2067;
    private static final int CODE_TRIGGER = 1811;

    private TreeLearnDatabaseHelper helper;
    private DatabaseTransactionRunner runner;
    private GenerationStabilizer stabilizer;

    @Before
    public void setUp() {
        helper = new TreeLearnDatabaseHelper(
                InstrumentationRegistry.getInstrumentation().getTargetContext());
        runner = new DatabaseTransactionRunner();
        stabilizer = new GenerationStabilizer(runner);
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
    // 1. A stable revision is a row like any other, until it is not.
    // ------------------------------------------------------------------

    @Test
    public void answerRevisionRoundTripsFieldByFieldAcrossCloseAndReopen() {
        SQLiteDatabase db = helper.getWritableDatabase();
        Positions positions = seedAiPosition(db);

        // Higher revision_no first, so the ordering assertion below cannot pass by insertion order.
        AnswerRevision third = createRevision(positions.message.id, 3, "reasoning three",
                "answer three", GenerationModel.PRO, false, T0 + 3);
        AnswerRevision first = createRevision(positions.message.id, 1, "reasoning one",
                "answer one", GenerationModel.FLASH, true, T0 + 1);
        AnswerRevisionDao.insert(db, third);
        AnswerRevisionDao.insert(db, first);

        AnswerRevision readBack = AnswerRevisionDao.findById(db, third.id);
        assertNotNull("a written revision must be readable", readBack);
        assertEqualRevision(third, readBack);

        List<AnswerRevision> ofMessage = AnswerRevisionDao.findByMessage(db, positions.message.id);
        assertEquals("both revisions of the position come back", 2, ofMessage.size());
        assertEquals("and they come back in revision order", 1, ofMessage.get(0).revisionNo);
        assertEquals("and they come back in revision order", 3, ofMessage.get(1).revisionNo);
        assertEquals("the second one is the one that was written second", third.id,
                ofMessage.get(1).id);

        // The next number is a repository fact: it is what a caller passes to stabilize, so it has
        // to be derived from what is stored rather than from a counter in memory.
        assertEquals("the next number follows the highest already stored", 4,
                AnswerRevisionDao.nextRevisionNo(db, positions.message.id));
        assertEquals("a position with no revision starts at one", 1,
                AnswerRevisionDao.nextRevisionNo(db, seedAiPosition(db).message.id));

        helper.close();
        helper = new TreeLearnDatabaseHelper(context());
        SQLiteDatabase reopened = helper.getWritableDatabase();
        assertEqualRevision(third, AnswerRevisionDao.findById(reopened, third.id));
        assertEquals("no dangling references after the round trip", 0,
                rawRows(reopened, "PRAGMA foreign_key_check"));
    }

    @Test
    public void theSameRevisionNumberOnOneMessageIsRefusedByTheUniqueIndex() {
        SQLiteDatabase db = helper.getWritableDatabase();
        Positions positions = seedAiPosition(db);
        AnswerRevisionDao.insert(db, createRevision(positions.message.id, 1, "", "the real answer",
                GenerationModel.FLASH, true, T0));

        AnswerRevision duplicate = createRevision(positions.message.id, 1, "", "the impostor",
                GenerationModel.FLASH, true, T0 + 1);
        SQLiteConstraintException failure = assertThrows(SQLiteConstraintException.class,
                () -> AnswerRevisionDao.insert(db, duplicate));
        assertCause("(message_id, revision_no) must be unique", failure.getMessage(), CODE_UNIQUE,
                "answer_revisions.message_id");

        assertEquals("the refused insert left no row behind", 1,
                countWhere(db, "answer_revisions",
                        "message_id='" + positions.message.id + "'"));
        assertNull("the duplicate id was never persisted",
                AnswerRevisionDao.findById(db, duplicate.id));
    }

    @Test
    public void aStableRevisionRefusesUpdateAndStandaloneDeleteButFollowsItsMessage() {
        SQLiteDatabase db = helper.getWritableDatabase();
        Positions positions = seedAiPosition(db);
        AnswerRevision revision = createRevision(positions.message.id, 1, "", "frozen answer",
                GenerationModel.FLASH, true, T0);
        AnswerRevisionDao.insert(db, revision);

        // Asserted through raw SQL on purpose: the guard being tested is the store's, and this DAO
        // offers no method that would try either write. A behavioural test cannot witness the
        // absence of a method — that is a property of the source text — so what is pinned here is
        // that the *store* refuses a writer that bypasses the DAO entirely (a migration, a repair by
        // hand, a future DAO).
        SQLiteConstraintException rewritten = assertThrows(SQLiteConstraintException.class,
                () -> db.execSQL("UPDATE answer_revisions SET answer_content=? WHERE id=?",
                        new Object[]{"rewritten after the fact", revision.id}));
        assertCause("a stable revision is immutable", rewritten.getMessage(), CODE_TRIGGER,
                "immutable");
        SQLiteConstraintException deleted = assertThrows(SQLiteConstraintException.class,
                () -> db.execSQL("DELETE FROM answer_revisions WHERE id=?",
                        new Object[]{revision.id}));
        assertCause("a stable revision is deleted only together with its message",
                deleted.getMessage(), CODE_TRIGGER, "never on its own");
        assertNotNull("the row survives both refusals", AnswerRevisionDao.findById(db, revision.id));

        // The legitimate route still works: the owner goes first and the revision follows, because
        // the no-direct-delete trigger only aborts while the owning message still exists.
        TopicDao.delete(db, positions.topic.id);
        assertNull("the revision followed its message", AnswerRevisionDao.findById(db, revision.id));
        assertEquals("the table is empty", 0, countWhere(db, "answer_revisions", "1=1"));
        assertEquals("deleting the topic left no dangling row", 0,
                rawRows(db, "PRAGMA foreign_key_check"));
    }

    // ------------------------------------------------------------------
    // 2. generation_operations is a row, and it is where recovery reads from.
    // ------------------------------------------------------------------

    @Test
    public void aSecondActiveOperationOnOneBranchIsRefusedAndAStableOneFreesTheBranch() {
        SQLiteDatabase db = helper.getWritableDatabase();
        Positions positions = seedAiPosition(db);
        GenerationOperation firstAttempt = seedStreamingOperation(db, positions);

        GenerationOperation otherAttempt = GenerationOperation.start(
                commandFor(positions, T0 + 1), T0 + 1);
        SQLiteConstraintException failure = assertThrows(SQLiteConstraintException.class,
                () -> GenerationOperationDao.insert(db, otherAttempt));
        assertCause("a branch hosts at most one in-flight attempt (§7)", failure.getMessage(),
                CODE_UNIQUE, "generation_operations.branch_id");
        assertEquals("the refused attempt left no row behind", 1,
                countWhere(db, "generation_operations", "branch_id='" + positions.branch.id + "'"));
        assertNull("and its id was never persisted",
                GenerationOperationDao.findById(db, otherAttempt.id));

        // The index is partial: terminal rows must stay insertable, otherwise a branch could only
        // ever be generated into once.
        GenerationOperationDao.markTerminal(db, firstAttempt.id, GenerationState.CANCELED, null,
                T0 + 2);
        GenerationOperationDao.insert(db, otherAttempt);

        GenerationOperationRow active = GenerationOperationDao
                .findActiveByBranch(db, positions.branch.id);
        assertNotNull("the new attempt is the active one of the branch", active);
        assertEquals(otherAttempt.id, active.id);
        assertEquals(GenerationState.STAGED, active.state);

        // Recovery reads rows, and only the ones a restart cannot leave behind (§7).
        List<GenerationOperationRow> recoverable = GenerationOperationDao.findRecoverable(db);
        assertEquals("only the unfinished attempt is recoverable", 1, recoverable.size());
        assertEquals(otherAttempt.id, recoverable.get(0).id);
    }

    @Test
    public void deltasAreAppendedByTheStoreInOrderAndOneCallMovesTheState() {
        SQLiteDatabase db = helper.getWritableDatabase();
        Positions positions = seedAiPosition(db);
        GenerationOperation operation = GenerationOperation.start(commandFor(positions, T0), T0);
        GenerationOperationDao.insert(db, operation);

        GenerationOperationDao.recordProgress(db, operation.id, "", "", GenerationState.SUBMITTING,
                T0 + 1);
        GenerationOperationRow submitting = GenerationOperationDao.findById(db, operation.id);
        assertNotNull(submitting);
        assertEquals("a state may move on its own", GenerationState.SUBMITTING, submitting.state);
        assertEquals("with nothing appended yet", "", submitting.partialReasoning);
        assertEquals("and updatedAt follows the call that moved it", T0 + 1, submitting.updatedAt);

        GenerationOperationDao.recordProgress(db, operation.id, "first ", "",
                GenerationState.STREAMING_REASONING, T0 + 2);
        GenerationOperationDao.recordProgress(db, operation.id, "second", "a-1",
                GenerationState.STREAMING_ANSWER, T0 + 3);
        GenerationOperationDao.recordProgress(db, operation.id, "", "a-2",
                GenerationState.STREAMING_ANSWER, T0 + 4);

        GenerationOperationRow row = GenerationOperationDao.findById(db, operation.id);
        assertNotNull(row);
        assertEquals("deltas accumulate in arrival order", "first second", row.partialReasoning);
        assertEquals("both channels are carried by one call", "a-1a-2", row.partialAnswer);
        assertEquals(GenerationState.STREAMING_ANSWER, row.state);
        assertEquals("createdAt never moves", T0, row.createdAt);
        assertEquals("updatedAt tracks the last write", T0 + 4, row.updatedAt);

        // Metacharacters survive verbatim and nothing is executed as SQL: the append is a
        // parameterised statement, not text interpolated into one. The string is deliberately free of
        // DDL keywords so that a repository-wide search for DDL cannot trip over a test fixture —
        // this repository has already paid once for a search that reported noise as a finding.
        String hostile = "quote ' and \" and % and ?1 and 1=1 --";
        GenerationOperationDao.recordProgress(db, operation.id, hostile, "",
                GenerationState.STREAMING_ANSWER, T0 + 5);
        assertEquals("the delta is stored exactly as it arrived", "first second" + hostile,
                GenerationOperationDao.findById(db, operation.id).partialReasoning);
        assertEquals("the messages table is untouched: no fragment of the delta ran as SQL", 1,
                countWhere(db, "messages", "1=1"));
    }

    @Test
    public void aTerminalOperationRefusesEveryLateWrite() {
        SQLiteDatabase db = helper.getWritableDatabase();
        Positions positions = seedAiPosition(db);
        GenerationOperation operation = seedStreamingOperation(db, positions);

        DomainResult<AnswerRevision> stabilized = stabilizer.stabilize(db, operation.id, 1, T0 + 10);
        assertTrue("the fixture must stabilize, otherwise this case tests nothing: "
                + stabilized, stabilized.isOk());

        // §7's "a late callback must not write back over an outcome the user has seen" is enforced
        // by the store, so a delta that arrives after completion fails loudly at the write itself.
        SQLiteConstraintException lateDelta = assertThrows(SQLiteConstraintException.class,
                () -> GenerationOperationDao.recordProgress(db, operation.id, "late reasoning", "",
                        GenerationState.STREAMING_ANSWER, T0 + 11));
        assertCause("a frozen attempt never moves again", lateDelta.getMessage(), CODE_TRIGGER,
                "final");
        SQLiteConstraintException rewritten = assertThrows(SQLiteConstraintException.class,
                () -> GenerationOperationDao.markTerminal(db, operation.id, GenerationState.FAILED,
                        "net-closed", T0 + 12));
        assertCause("and a second outcome cannot replace the first", rewritten.getMessage(),
                CODE_TRIGGER, "final");

        GenerationOperationRow row = GenerationOperationDao.findById(db, operation.id);
        assertNotNull(row);
        assertEquals(GenerationState.STABLE, row.state);
        assertEquals("the late delta was not written", "reasoning-from-the-model",
                row.partialReasoning);
        assertNull("a stable attempt carries no error code", row.errorCode);
        assertEquals("the frozen row is still the one that was updated", T0 + 10, row.updatedAt);
    }

    // ------------------------------------------------------------------
    // 3. Stabilization: one transaction, one pinned version, all or nothing.
    // ------------------------------------------------------------------

    @Test
    public void stabilizeCopiesThePinnedVersionFromTheRowVerbatim() {
        SQLiteDatabase db = helper.getWritableDatabase();
        Positions positions = seedAiPosition(db);
        GenerationOperation operation = seedStreamingOperation(db, positions);

        // The row is tampered the way a future migration, a hand-run sqlite3 session or a repair
        // would leave it. Only this fixture can distinguish "copied the version the row pinned" from
        // "re-derived the version from the command": both produce the same string while the row is
        // intact, so an intact fixture would pass for either implementation.
        db.execSQL("UPDATE generation_operations SET prompt_policy_version=? WHERE id=?",
                new Object[]{FOREIGN_POLICY_VERSION, operation.id});
        assertEquals("fixture precondition: the row really carries the foreign version",
                FOREIGN_POLICY_VERSION,
                textColumn(db, TreeLearnSchema.GenerationOperations.TABLE,
                        TreeLearnSchema.GenerationOperations.PROMPT_POLICY_VERSION,
                        TreeLearnSchema.GenerationOperations.ID, operation.id));

        DomainResult<AnswerRevision> result = stabilizer.stabilize(db, operation.id, 1, T0 + 10);
        assertTrue("stabilizing a streaming attempt with a final answer must succeed: " + result,
                result.isOk());
        AnswerRevision revision = result.value();

        assertEquals("the revision records the version the operation row pinned",
                FOREIGN_POLICY_VERSION, revision.promptPolicyVersion);
        assertNotEquals("not the one the command pinned", POLICY_VERSION,
                revision.promptPolicyVersion);
        assertEquals("the copied version is what the store holds",
                FOREIGN_POLICY_VERSION, AnswerRevisionDao.findById(db, revision.id).promptPolicyVersion);
        assertEquals("and the operation row keeps its own string, unrewritten",
                FOREIGN_POLICY_VERSION,
                textColumn(db, TreeLearnSchema.GenerationOperations.TABLE,
                        TreeLearnSchema.GenerationOperations.PROMPT_POLICY_VERSION,
                        TreeLearnSchema.GenerationOperations.ID, operation.id));

        assertEquals("the revision is built from the row's content, not from a re-read command",
                "reasoning-from-the-model", revision.reasoningContent);
        assertEquals("and the frozen answer is the one the row accumulated",
                "answer-from-the-model", revision.answerContent);
        assertEquals("the operation is now STABLE", GenerationState.STABLE,
                GenerationOperationDao.findById(db, operation.id).state);
    }

    @Test
    public void stabilizeOwnsTheTransactionBoundaryAndRefusesAnOuterOne() {
        SQLiteDatabase db = helper.getWritableDatabase();
        Positions positions = seedAiPosition(db);
        GenerationOperation operation = seedStreamingOperation(db, positions);

        // Two writes in one transaction is the whole point of the entry point, so the boundary has
        // to be the entry point's: asking it to run inside a caller's transaction must be refused
        // rather than silently joined (a joined inner failure would be committed by an outer
        // success — the reason DatabaseTransactionRunner refuses nesting at all).
        IllegalStateException nested = assertThrows(IllegalStateException.class,
                () -> runner.run(db, work -> stabilizer.stabilize(work, operation.id, 1, T0 + 10)));
        assertTrue("the refusal must name the nesting: " + nested.getMessage(),
                nested.getMessage().contains("nested"));
        assertEquals("a refused nesting committed nothing", 0,
                countWhere(db, "answer_revisions", "1=1"));
        assertEquals("and left the attempt where it was", GenerationState.STREAMING_ANSWER,
                GenerationOperationDao.findById(db, operation.id).state);
    }

    @Test
    public void stabilizeRollsBackBothWritesWhenTheRevisionNumberIsAlreadyTaken() {
        SQLiteDatabase db = helper.getWritableDatabase();
        Positions positions = seedAiPosition(db);
        GenerationOperation operation = seedStreamingOperation(db, positions);

        // The number the caller asks for is already used by another writer — the race a retry
        // produces. The refusal must unwind the whole transaction: a revision stored beside an
        // attempt that is still streaming is exactly the intermediate state the entry point exists
        // to make impossible.
        AnswerRevision taken = createRevision(positions.message.id, 1, "", "written first",
                GenerationModel.FLASH, true, T0);
        AnswerRevisionDao.insert(db, taken);

        DomainResult<AnswerRevision> result = stabilizer.stabilize(db, operation.id, 1, T0 + 10);
        assertTrue("a taken revision number must be reported, not thrown: " + result,
                result.isFailure());
        assertEquals(DomainErrorCode.SEQUENCE_OUT_OF_ORDER, result.error().code);

        List<AnswerRevision> stored = AnswerRevisionDao.findByMessage(db, positions.message.id);
        assertEquals("no second revision row appeared", 1, stored.size());
        assertEquals("the row that exists is the one that was already there", taken.id,
                stored.get(0).id);
        assertEquals("and it was not rewritten", "written first", stored.get(0).answerContent);
        assertEquals("the attempt is still streaming, not stranded in STABLE",
                GenerationState.STREAMING_ANSWER,
                GenerationOperationDao.findById(db, operation.id).state);
        assertEquals("no dangling references after the rollback", 0,
                rawRows(db, "PRAGMA foreign_key_check"));
    }

    @Test
    public void stabilizeRefusesAMissingOrUnstabilizableOperationWithACode() {
        SQLiteDatabase db = helper.getWritableDatabase();
        Positions positions = seedAiPosition(db);

        DomainResult<AnswerRevision> missing = stabilizer.stabilize(db, MISSING_OPERATION_ID, 1, T0);
        assertTrue("an operation that is not there is a business refusal, not an exception: "
                + missing, missing.isFailure());
        assertTrue("the refusal must name the id it could not find: " + missing.error().detail,
                missing.error().detail.contains(MISSING_OPERATION_ID));

        // An attempt that has not reached the provider has no answer to freeze, and the state graph
        // allows no edge from STAGED to STABLE.
        GenerationOperation staged = GenerationOperation.start(commandFor(positions, T0), T0);
        GenerationOperationDao.insert(db, staged);
        DomainResult<AnswerRevision> tooEarly = stabilizer.stabilize(db, staged.id, 1, T0 + 1);
        assertTrue("a staged attempt cannot be stabilized: " + tooEarly, tooEarly.isFailure());
        assertEquals(DomainErrorCode.ILLEGAL_STATE_TRANSITION, tooEarly.error().code);

        // A second stabilization of an attempt that already ended must be refused for the same
        // reason GenerationOperation.stabilize refuses it, and must not touch the frozen pair.
        GenerationOperation finished = seedStreamingOperation(db, seedAiPosition(db));
        GenerationOperationDao.markTerminal(db, finished.id, GenerationState.FAILED, "net-closed",
                T0 + 2);
        DomainResult<AnswerRevision> tooLate = stabilizer.stabilize(db, finished.id, 1, T0 + 3);
        assertTrue("a finished attempt cannot be stabilized: " + tooLate, tooLate.isFailure());
        assertEquals(DomainErrorCode.ILLEGAL_STATE_TRANSITION, tooLate.error().code);

        assertEquals("none of the three refusals wrote a revision", 0,
                countWhere(db, "answer_revisions", "1=1"));
        assertEquals("and no attempt was moved to STABLE", 0,
                countWhere(db, "generation_operations",
                        TreeLearnSchema.GenerationOperations.STATE + "='STABLE'"));
    }

    @Test
    public void stabilizeRefusesARowWithoutAFinalAnswer() {
        SQLiteDatabase db = helper.getWritableDatabase();
        Positions positions = seedAiPosition(db);
        GenerationOperation operation = seedStreamingOperation(db, positions);

        // A row in STREAMING_ANSWER with nothing in the answer buffer. No code path in this task can
        // produce it, which is the point: it is what a lagging flush or a hand-edited row looks
        // like. §9.3 requires a non-empty final answer, so the entry point must refuse instead of
        // letting AnswerRevision.create throw a caller-bug exception out through the network layer.
        db.execSQL("UPDATE generation_operations SET partial_answer='' WHERE id=?",
                new Object[]{operation.id});

        DomainResult<AnswerRevision> result = stabilizer.stabilize(db, operation.id, 1, T0 + 10);
        assertTrue("an attempt with no final answer must be refused: " + result, result.isFailure());
        assertEquals(DomainErrorCode.OPERATION_NOT_COMPLETABLE, result.error().code);
        assertEquals("and nothing was written", 0, countWhere(db, "answer_revisions", "1=1"));
        assertEquals("the attempt was not moved to STABLE", GenerationState.STREAMING_ANSWER,
                GenerationOperationDao.findById(db, operation.id).state);
    }

    // ------------------------------------------------------------------
    // 4. Only the stable purpose/version pair may reach storage.
    // ------------------------------------------------------------------

    /**
     * Scans both tables after a full stabilization and asserts that the only prompt-related values
     * stored are enumerable: a purpose this build knows and a version some purpose owns.
     *
     * <p><strong>Evidence strength, stated honestly.</strong> The purpose/version assertions have
     * real content — a DAO that wrote a prompt body, or a version pulled from a live registry, would
     * fail them. The sentinel scan is a <em>tripwire</em> rather than a proof: no write path in this
     * task accepts a prompt body, so its absence is expected and its value lies entirely in catching
     * a future column or DAO that starts storing one.
     */
    @Test
    public void onlyAnEnumeratedPurposeAndAnOwnedVersionReachTheTwoTables() {
        SQLiteDatabase db = helper.getWritableDatabase();
        Positions positions = seedAiPosition(db);
        GenerationOperation operation = seedStreamingOperation(db, positions);
        assertTrue(stabilizer.stabilize(db, operation.id, 1, T0 + 10).isOk());

        for (String purpose : distinctTextValues(db, TreeLearnSchema.GenerationOperations.TABLE,
                TreeLearnSchema.GenerationOperations.PROMPT_PURPOSE)) {
            assertNotNull("a stored prompt purpose must be one this build knows, but found <"
                            + purpose + ">",
                    PromptPurpose.fromPrefix(purpose));
        }
        Set<String> versions = new TreeSet<>();
        versions.addAll(distinctTextValues(db, TreeLearnSchema.GenerationOperations.TABLE,
                TreeLearnSchema.GenerationOperations.PROMPT_POLICY_VERSION));
        versions.addAll(distinctTextValues(db, TreeLearnSchema.AnswerRevisions.TABLE,
                TreeLearnSchema.AnswerRevisions.PROMPT_POLICY_VERSION));
        assertEquals("the fixture must have stored versions to scan", 1, versions.size());
        for (String version : versions) {
            assertNotNull("only a version some purpose owns may be persisted, but found <"
                    + version + ">", PromptPurpose.ownerOf(version));
        }

        for (String table : new String[]{TreeLearnSchema.GenerationOperations.TABLE,
                TreeLearnSchema.AnswerRevisions.TABLE}) {
            for (String column : textColumns(db, table)) {
                for (String value : distinctTextValues(db, table, column)) {
                    assertFalse(table + "." + column + " holds a prompt body",
                            value.contains(PROMPT_BODY_SENTINEL));
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // Fixtures.
    // ------------------------------------------------------------------

    /** The three rows an attempt needs before it can exist: a topic, its root branch, an AI position. */
    private static final class Positions {

        final Topic topic;
        final Branch branch;
        final Message message;

        Positions(Topic topic, Branch branch, Message message) {
            this.topic = topic;
            this.branch = branch;
            this.message = message;
        }
    }

    private static Positions seedAiPosition(SQLiteDatabase db) {
        Topic topic = Topic.create(T0);
        TopicDao.insert(db, topic);
        Branch branch = Branch.root(topic.id, T0);
        BranchDao.insert(db, branch);
        Message position = Message.ai(branch.id, 0, T0);
        MessageDao.insert(db, position);
        return new Positions(topic, branch, position);
    }

    private static GenerationCommand commandFor(Positions positions, long now) {
        return GenerationCommand.create(positions.branch.id, positions.message.id,
                PromptPurpose.LEARNING_ANSWER, POLICY_VERSION, GenerationModel.FLASH, true, now)
                .value();
    }

    /**
     * An attempt driven to {@code STREAMING_ANSWER} with both buffers non-empty, as the coordinator
     * would leave it, and already persisted.
     */
    private static GenerationOperation seedStreamingOperation(SQLiteDatabase db, Positions positions) {
        GenerationCommand command = commandFor(positions, T0);
        GenerationOperation operation = GenerationOperation.start(command, T0);
        operation.advanceTo(GenerationState.VALIDATING);
        operation.advanceTo(GenerationState.SUBMITTING);
        operation.appendReasoning("reasoning-from-the-model");
        operation.appendAnswer("answer-");
        operation.appendAnswer("from-the-model");
        GenerationOperationDao.insert(db, operation);
        return operation;
    }

    private static AnswerRevision createRevision(String messageId, int revisionNo, String reasoning,
                                                 String answer, GenerationModel model,
                                                 boolean thinkingEnabled, long now) {
        DomainResult<AnswerRevision> created = AnswerRevision.create(messageId, revisionNo, reasoning,
                answer, model, thinkingEnabled, POLICY_VERSION, now);
        assertTrue("fixture revision must be creatable: " + created, created.isOk());
        return created.value();
    }

    // ------------------------------------------------------------------
    // Assertion helpers.
    // ------------------------------------------------------------------

    private static void assertEqualRevision(AnswerRevision expected, AnswerRevision actual) {
        assertNotNull("the revision must be readable", actual);
        assertEquals("revision id", expected.id, actual.id);
        assertEquals("revision messageId", expected.messageId, actual.messageId);
        assertEquals("revision revisionNo", expected.revisionNo, actual.revisionNo);
        assertEquals("revision reasoningContent", expected.reasoningContent,
                actual.reasoningContent);
        assertEquals("revision answerContent", expected.answerContent, actual.answerContent);
        assertEquals("revision model", expected.model, actual.model);
        assertEquals("revision thinkingEnabled", expected.thinkingEnabled, actual.thinkingEnabled);
        assertEquals("revision promptPolicyVersion", expected.promptPolicyVersion,
                actual.promptPolicyVersion);
        assertEquals("revision createdAt", expected.createdAt, actual.createdAt);
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

    private static String textColumn(SQLiteDatabase db, String table, String column,
                                     String keyColumn, String id) {
        try (Cursor cursor = db.query(table, new String[]{column}, keyColumn + "=?",
                new String[]{id}, null, null, null)) {
            return cursor.moveToFirst() ? cursor.getString(0) : null;
        }
    }

    private static Set<String> distinctTextValues(SQLiteDatabase db, String table, String column) {
        Set<String> values = new TreeSet<>();
        try (Cursor cursor = db.query(true, table, new String[]{column}, null, null, null, null,
                null, null)) {
            while (cursor.moveToNext()) {
                if (!cursor.isNull(0)) {
                    values.add(cursor.getString(0));
                }
            }
        }
        return values;
    }

    /** @return every column of {@code table} declared with SQLite's TEXT affinity */
    private static Set<String> textColumns(SQLiteDatabase db, String table) {
        Set<String> columns = new TreeSet<>();
        try (Cursor cursor = db.rawQuery("PRAGMA table_info(" + table + ")", null)) {
            while (cursor.moveToNext()) {
                if ("TEXT".equalsIgnoreCase(
                        cursor.getString(cursor.getColumnIndexOrThrow("type")))) {
                    columns.add(cursor.getString(cursor.getColumnIndexOrThrow("name")));
                }
            }
        }
        return columns;
    }
}
