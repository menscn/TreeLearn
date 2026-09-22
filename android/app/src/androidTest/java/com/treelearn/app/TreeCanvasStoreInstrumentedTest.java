package com.treelearn.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.treelearn.ai.prompt.DeepSeekPromptRegistry;
import com.treelearn.app.artifact.ArtifactKind;
import com.treelearn.app.artifact.ArtifactPayload;
import com.treelearn.app.artifact.ArtifactPayloadCodec;
import com.treelearn.app.artifact.DerivedResult;
import com.treelearn.app.wiring.SqliteChatStore;
import com.treelearn.app.wiring.SqliteTreeCanvasStore;
import com.treelearn.core.domain.AnswerRevision;
import com.treelearn.core.domain.Branch;
import com.treelearn.core.domain.BranchAnchor;
import com.treelearn.core.domain.BranchSource;
import com.treelearn.core.domain.DomainResult;
import com.treelearn.core.domain.GenerationCommand;
import com.treelearn.core.domain.GenerationModel;
import com.treelearn.core.domain.GenerationOperation;
import com.treelearn.core.domain.GenerationState;
import com.treelearn.core.domain.Guard;
import com.treelearn.core.domain.Message;
import com.treelearn.core.domain.PromptPurpose;
import com.treelearn.core.domain.Topic;
import com.treelearn.data.db.DatabaseTransactionRunner;
import com.treelearn.data.db.TreeLearnDatabaseHelper;
import com.treelearn.data.db.TreeLearnSchema;
import com.treelearn.data.db.dao.AiArtifactDao;
import com.treelearn.data.db.dao.AnswerRevisionDao;
import com.treelearn.data.db.dao.BranchDao;
import com.treelearn.data.db.dao.FirstTopicCreation;
import com.treelearn.data.db.dao.MessageDao;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

/**
 * T-038 acceptance evidence for {@link SqliteTreeCanvasStore}, on real Android framework SQLite:
 * its four reads and its one write, plus the two boundaries its own contract names.
 *
 * <p><strong>The canvas's two rules are deliberately not asserted here.</strong> That archived rows
 * are dropped and that a summary reads as its first line are {@code TreeCanvasWiring}'s decisions,
 * made above this class. This file asserts the raw material is handed over intact - archived rows
 * included, the stored envelope as written - so the JVM suite stays the single owner of those two
 * verdicts and this half proves only what a plain JVM cannot reach: the statements, the column round
 * trip and the refusal classification.
 *
 * <p><strong>Compiled, never executed - so this is not evidence that the SQL ran.</strong> The user
 * decided on 2026-09-21 to abandon emulator and real-device rounds and to verify by sideloading the
 * release APK, so the acceptance row for device-side SQL is recorded as
 * {@code compiled, never executed - deferred 用户自行装机验证（user decision 2026-09-21）} and never as PASS.
 *
 * <p>Holds no real user content and no API key: every literal is synthetic (rules/sql.md).
 *
 * <p><strong>Fixture caveat, stated rather than hidden:</strong> the two-branch fixtures reuse the
 * fork-below-a-stable-revision shape of {@code RealDataPathWiringInstrumentedTest}. That shape is
 * itself compile-only, so if it proves wrong on a device it is wrong in both files at once.
 */
@RunWith(AndroidJUnit4.class)
public final class TreeCanvasStoreInstrumentedTest {

    private static final long T0 = 1_756_000_000_000L;

    /** Synthetic model id; never a real credential. */
    private static final String MODEL = "deepseek-flash";

    /**
     * Both policy versions come from the registry rather than being written down here, so a fixture
     * cannot pin a version the build no longer ships.
     */
    private static final String ANSWER_POLICY_VERSION =
            DeepSeekPromptRegistry.spec(PromptPurpose.LEARNING_ANSWER).policyVersion;

    private static final String SUMMARY_POLICY_VERSION =
            DeepSeekPromptRegistry.spec(PromptPurpose.BRANCH_SUMMARY).policyVersion;

    private static final String SUMMARY_KIND = ArtifactKind.BRANCH_SUMMARY.wireId;

    /** The codec's own floor for a summary-shaped payload; the same figure {@code HomeWiring} uses. */
    private static final int SUMMARY_ITEM_CEILING = 8;

    private static final String TOPIC_A = "dddddddd-0000-4000-8000-0000000000d4";
    private static final String TOPIC_B = "eeeeeeee-0000-4000-8000-0000000000e5";

    private TreeLearnDatabaseHelper helper;
    private DatabaseTransactionRunner runner;
    private FirstTopicCreation firstTopic;
    private SqliteChatStore chat;
    private SqliteTreeCanvasStore canvas;

    @Before
    public void setUp() {
        helper = new TreeLearnDatabaseHelper(
                InstrumentationRegistry.getInstrumentation().getTargetContext());
        runner = new DatabaseTransactionRunner();
        firstTopic = new FirstTopicCreation(runner);
        chat = new SqliteChatStore(helper, runner);
        canvas = new SqliteTreeCanvasStore(helper, runner);
        context().deleteDatabase(TreeLearnDatabaseHelper.DATABASE_NAME);
    }

    @After
    public void tearDown() {
        if (helper != null) {
            helper.close();
        }
        context().deleteDatabase(TreeLearnDatabaseHelper.DATABASE_NAME);
    }

    // ------------------------------------------------------------------
    // Read 1: topicOf - which topic a branch belongs to.
    // ------------------------------------------------------------------

    @Test
    public void topicOfAnswersTheBranchesTopicAndNothingForAnIdNoRowCarries() {
        Writes writes = createTopic(TOPIC_A, T0);
        createTopic(TOPIC_B, T0 + 1_000);

        assertEquals("the root's own topic", TOPIC_A, canvas.topicOf(writes.branch.id));
        assertNull("a branch that does not exist belongs to no topic",
                canvas.topicOf(Guard.newId()));
    }

    // ------------------------------------------------------------------
    // Read 2: branchesOf - the topic's whole tree, archived rows included.
    // ------------------------------------------------------------------

    @Test
    public void branchesOfReturnsEveryBranchOfTheTopicInCreationOrderAndNothingFromAnotherTopic() {
        Writes writes = createTopic(TOPIC_A, T0);
        String revisionId = insertStableRevision(writes.aiMessage.id, T0 + 5).id;
        Branch fork = insertFork(writes, revisionId, T0 + 10);
        createTopic(TOPIC_B, T0 + 20);

        List<Branch> rows = canvas.branchesOf(TOPIC_A);

        assertEquals("every branch of the topic, not only the root", 2, rows.size());
        assertEquals("in creation order, root first", writes.branch.id, rows.get(0).id);
        assertEquals(fork.id, rows.get(1).id);
        assertEquals("the other topic's tree is not this topic's tree", 1,
                canvas.branchesOf(TOPIC_B).size());
    }

    @Test
    public void branchesOfKeepsArchivedRowsBecauseDroppingThemIsTheCanvassDecision() {
        Writes writes = createTopic(TOPIC_A, T0);
        String revisionId = insertStableRevision(writes.aiMessage.id, T0 + 5).id;
        Branch fork = insertFork(writes, revisionId, T0 + 10);
        archive(fork.id, T0 + 20);

        List<Branch> rows = canvas.branchesOf(TOPIC_A);

        assertEquals("the archived row is still read back, so the filter has something to drop", 2,
                rows.size());
        Branch archived = rows.get(1);
        assertEquals(fork.id, archived.id);
        assertTrue("and it still reads as archived rather than being silently un-archived",
                archived.isArchived());
        assertFalse("the root was never archived", rows.get(0).isArchived());
    }

    // ------------------------------------------------------------------
    // Read 3: summaryPayload - the stored envelope, by kind and scope.
    // ------------------------------------------------------------------

    @Test
    public void summaryPayloadReturnsTheStoredEnvelopeForItsOwnBranchAndNullBeforeAnythingIsDerived() {
        Writes writes = createTopic(TOPIC_A, T0);
        String revisionId = insertStableRevision(writes.aiMessage.id, T0 + 5).id;
        Branch fork = insertFork(writes, revisionId, T0 + 10);

        assertNull("nothing has been derived for the root yet",
                canvas.summaryPayload(writes.branch.id));
        assertNull("nor for the fork", canvas.summaryPayload(fork.id));

        String rootEnvelope = summaryEnvelope("瑞利散射：短波更容易被大气分子散射。", "短波更易散射");
        String forkEnvelope = summaryEnvelope("分叉自己的摘要。", "分叉要点");
        insertSummary(writes.branch.id, revisionId, rootEnvelope, T0 + 20);
        insertSummary(fork.id, revisionId, forkEnvelope, T0 + 30);

        assertEquals("the envelope travels exactly as it was stored", rootEnvelope,
                canvas.summaryPayload(writes.branch.id));
        assertEquals("and one scope's envelope is not another's", forkEnvelope,
                canvas.summaryPayload(fork.id));

        // The envelope is only useful if the canvas can decode what the store handed over.
        DerivedResult reparsed = ArtifactPayloadCodec.parse(ArtifactKind.BRANCH_SUMMARY,
                canvas.summaryPayload(writes.branch.id), SUMMARY_ITEM_CEILING);
        assertTrue("the stored envelope must be one the codec accepts: " + reparsed.refusal(),
                reparsed.isUsable());
    }

    // ------------------------------------------------------------------
    // Read 4: committedRounds - the branch's committed questions.
    // ------------------------------------------------------------------

    @Test
    public void committedRoundsCountsTheBranchesQuestionsAndNotItsAnswerPositions() {
        Writes writes = createTopic(TOPIC_A, T0);
        String revisionId = insertStableRevision(writes.aiMessage.id, T0 + 5).id;
        Branch fork = insertFork(writes, revisionId, T0 + 10);

        assertEquals("the first topic already holds one committed question", 1,
                canvas.committedRounds(writes.branch.id));

        appendTurn(writes.branch.id, 2, "那为什么不是紫色？", T0 + 20);
        assertEquals("a second committed question is a second round", 2,
                canvas.committedRounds(writes.branch.id));

        List<Message> positions = MessageDao.findByBranch(db(), writes.branch.id);
        int questions = 0;
        int answers = 0;
        for (Message row : positions) {
            if (row.isUser()) {
                questions++;
            } else {
                answers++;
            }
        }
        assertEquals("each question has its answer position stored beside it", questions, answers);
        assertEquals("so the count is the questions, and every stored row would be double it", 2,
                questions);
        assertEquals("the round count follows the questions, not the rows", questions,
                canvas.committedRounds(writes.branch.id));

        assertEquals("a sibling branch with no turn of its own counts none", 0,
                canvas.committedRounds(fork.id));
    }

    // ------------------------------------------------------------------
    // Write: setCollapsed - the canvas's one write, read back from its column.
    // ------------------------------------------------------------------

    @Test
    public void aFoldIsStoredOnTheBranchAndReadsBackFromTheColumn() {
        Writes writes = createTopic(TOPIC_A, T0);
        String revisionId = insertStableRevision(writes.aiMessage.id, T0 + 5).id;
        Branch fork = insertFork(writes, revisionId, T0 + 10);

        assertEquals("a fresh branch starts unfolded", Integer.valueOf(0),
                collapsedColumnOf(writes.branch.id));

        DomainResult<Void> folded = canvas.setCollapsed(writes.branch.id, true, T0 + 20);

        assertTrue("storing the fold must be accepted: " + folded.error(), folded.isOk());
        assertEquals("the column carries the new flag, not merely the object", Integer.valueOf(1),
                collapsedColumnOf(writes.branch.id));
        assertTrue("and the row reads back folded through the domain",
                BranchDao.findById(db(), writes.branch.id).isCollapsed());
        assertEquals("the fold went through the domain, so the row's stamp advanced", T0 + 20,
                BranchDao.findById(db(), writes.branch.id).updatedAt());
        assertEquals("a sibling branch is not folded with it", Integer.valueOf(0),
                collapsedColumnOf(fork.id));

        DomainResult<Void> unfolded = canvas.setCollapsed(writes.branch.id, false, T0 + 30);

        assertTrue("unfolding is the same write: " + unfolded.error(), unfolded.isOk());
        assertEquals("the column goes back to 0", Integer.valueOf(0),
                collapsedColumnOf(writes.branch.id));
        assertFalse("and the row reads back unfolded",
                BranchDao.findById(db(), writes.branch.id).isCollapsed());
    }

    // ------------------------------------------------------------------
    // Boundaries: what the store refuses, and what it does not.
    // ------------------------------------------------------------------

    @Test
    public void foldingABranchThatIsGoneIsAnExceptionAndLeavesTheTableAlone() {
        Writes writes = createTopic(TOPIC_A, T0);
        int rowsBefore = branchRowCount();

        IllegalStateException gone = assertThrows(IllegalStateException.class,
                () -> canvas.setCollapsed(Guard.newId(), true, T0 + 10));

        assertTrue("the refusal must name the branch that is gone: " + gone.getMessage(),
                gone.getMessage().contains("is gone"));
        assertEquals("a stale id must not add a row", rowsBefore, branchRowCount());
        assertEquals("and it must not fold the branch that does exist", Integer.valueOf(0),
                collapsedColumnOf(writes.branch.id));
    }

    @Test
    public void foldingAnArchivedBranchIsStoredBecauseArchiveAndFoldAreSeparateFlags() {
        Writes writes = createTopic(TOPIC_A, T0);
        archive(writes.branch.id, T0 + 5);

        DomainResult<Void> folded = canvas.setCollapsed(writes.branch.id, true, T0 + 10);

        // Pinned as the store behaves, not as a screen would want it: the canvas never draws an
        // archived branch, so this path is unreachable from the UI, but the store does not refuse it.
        assertTrue("archive is not a guard on the fold: " + folded.error(), folded.isOk());
        assertEquals("the column carries the fold anyway", Integer.valueOf(1),
                collapsedColumnOf(writes.branch.id));
        assertTrue("and archiving is untouched by the fold",
                BranchDao.findById(db(), writes.branch.id).isArchived());
    }

    // ------------------------------------------------------------------
    // Fixtures: every one of these is a write the product really makes.
    // ------------------------------------------------------------------

    /**
     * The five row groups of a first topic, through the one creation path, so the fixture is the
     * transaction under test rather than a hand-built approximation of it.
     */
    private Writes createTopic(String topicId, long now) {
        Topic topic = new Topic(topicId, null, now, now);
        Branch root = Branch.root(topic.id, now);
        Message userMessage = Message.user(root.id, 0, "为什么天空是蓝色的？", now);
        Message aiMessage = Message.ai(root.id, 1, now);
        Writes writes = new Writes(topic, root, userMessage, aiMessage,
                operationFor(root, aiMessage, now));
        assertTrue("the fixture must be a real first topic",
                firstTopic.create(db(), writes.topic, writes.branch, writes.userMessage,
                        writes.aiMessage, writes.operation).isCreated());
        return writes;
    }

    /**
     * A turn as the conversation writes one: the question and the answer position it is answered at,
     * through the product's own {@code appendTurn} rather than two hand-placed inserts.
     */
    private void appendTurn(String branchId, int questionOrdinal, String text, long now) {
        chat.appendTurn(Message.user(branchId, questionOrdinal, text, now),
                Message.ai(branchId, questionOrdinal + 1, now));
    }

    private void archive(String branchId, long now) {
        Branch branch = BranchDao.findById(db(), branchId);
        assertNotNull("the branch to archive must exist", branch);
        branch.archive(now);
        BranchDao.update(db(), branch);
    }

    private static GenerationOperation operationFor(Branch branch, Message aiMessage, long now) {
        DomainResult<GenerationCommand> command = GenerationCommand.create(branch.id, aiMessage.id,
                PromptPurpose.LEARNING_ANSWER, ANSWER_POLICY_VERSION,
                GenerationModel.fromWireId(MODEL), true, now);
        assertTrue("fixture command must be accepted: "
                + (command.isOk() ? "" : command.error()), command.isOk());
        return GenerationOperation.start(command.value(), now);
    }

    private AnswerRevision insertStableRevision(String messageId, long now) {
        DomainResult<AnswerRevision> created = AnswerRevision.create(messageId, 1,
                "推理：瑞利散射让短波更易被散射。", "因为大气中的分子把蓝光散射得更多。",
                GenerationModel.fromWireId(MODEL), true, ANSWER_POLICY_VERSION, now);
        assertTrue("fixture revision must be accepted: "
                + (created.isOk() ? "" : created.error()), created.isOk());
        AnswerRevisionDao.insert(db(), created.value());
        return created.value();
    }

    /**
     * A fork as the product writes one: born from a real stable revision of the source position,
     * carrying its birth-snapshot row, and built through {@code Branch.child} rather than
     * {@code restore}. A topic allows exactly one root, so this is the only legal second branch.
     */
    private Branch insertFork(Writes writes, String sourceRevisionId, long now) {
        String snapshotId = Guard.newId();
        DomainResult<BranchSource> provenance = BranchSource.fromOperation(writes.branch.id,
                writes.aiMessage.id, snapshotId, GenerationState.STABLE, sourceRevisionId,
                BranchAnchor.wholeAnswer());
        assertTrue("fixture provenance must be accepted: "
                + (provenance.isOk() ? "" : provenance.error()), provenance.isOk());
        DomainResult<Branch> fork = Branch.child(writes.topic.id, provenance.value(), now);
        assertTrue("fixture fork must be accepted: " + (fork.isOk() ? "" : fork.error()),
                fork.isOk());

        Branch child = fork.value();
        BranchDao.insert(db(), child);
        insertSnapshotRow(db(), snapshotId, child.id);
        BranchDao.setContextSnapshot(db(), child.id, snapshotId);
        return child;
    }

    /**
     * The summary envelope exactly as the derived writer stores one: the codec's own canonical form
     * of an envelope it accepted, never a hand-written string.
     */
    private static String summaryEnvelope(String summary, String keyPoint) {
        String text = "{\"summary\":\"" + summary + "\",\"key_points\":[\"" + keyPoint + "\"]}";
        DerivedResult parsed = ArtifactPayloadCodec.parse(ArtifactKind.BRANCH_SUMMARY, text,
                SUMMARY_ITEM_CEILING);
        assertTrue("fixture envelope must be one the codec accepts: " + parsed.refusal(),
                parsed.isUsable());
        ArtifactPayload payload = parsed.payload();
        assertNotNull(payload);
        return payload.canonicalJson();
    }

    private void insertSummary(String branchId, String baselineRevisionId, String payload, long now) {
        AiArtifactDao.insert(db(), new AiArtifactDao.Row(Guard.newId(), SUMMARY_KIND, branchId,
                PromptPurpose.BRANCH_SUMMARY.policyPrefix, SUMMARY_POLICY_VERSION, payload,
                baselineRevisionId, "digest-" + branchId, now, now));
    }

    /** Inserts a birth-snapshot row; its DAO belongs to T-2026-09-21-024. */
    private static void insertSnapshotRow(SQLiteDatabase db, String id, String branchId) {
        db.execSQL("INSERT INTO context_snapshots(id, branch_id, schema_version, digest,"
                        + " payload, created_at) VALUES(?,?,?,?,?,?)",
                new Object[]{id, branchId, 1, "digest-" + id, "[]", T0});
    }

    // ------------------------------------------------------------------
    // Raw readers: they ask the columns directly, so a translated value cannot
    // be confirmed by the very layer that translated it.
    // ------------------------------------------------------------------

    /** @return the raw {@code collapsed} column: 0 or 1 as stored, null when SQL NULL */
    private Integer collapsedColumnOf(String branchId) {
        try (Cursor cursor = db().rawQuery(
                "SELECT " + TreeLearnSchema.Branches.COLLAPSED + " FROM "
                        + TreeLearnSchema.Branches.TABLE + " WHERE "
                        + TreeLearnSchema.Branches.ID + "=?",
                new String[]{branchId})) {
            assertTrue("no row carries branch " + branchId, cursor.moveToFirst());
            return cursor.isNull(0) ? null : Integer.valueOf(cursor.getInt(0));
        }
    }

    private int branchRowCount() {
        try (Cursor cursor = db().rawQuery(
                "SELECT COUNT(*) FROM " + TreeLearnSchema.Branches.TABLE, null)) {
            assertTrue(cursor.moveToFirst());
            return cursor.getInt(0);
        }
    }

    private SQLiteDatabase db() {
        return helper.getWritableDatabase();
    }

    private static Context context() {
        return InstrumentationRegistry.getInstrumentation().getTargetContext();
    }

    /** The five row groups of a first topic, built by the same factory the product calls. */
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
}
