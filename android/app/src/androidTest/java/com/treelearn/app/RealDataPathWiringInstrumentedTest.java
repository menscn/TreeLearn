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
import com.treelearn.data.db.dao.TopicDao;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

/**
 * T-032 acceptance evidence for the two read-only facts the real data path is built on, executed on
 * real Android framework SQLite:
 *
 * <ol>
 *   <li>{@link TopicDao#listOverviews} - the home list statement: the heading fallback, the summary
 *       envelope, the branch count, the resume branch and the newest-first order.</li>
 *   <li>{@code messages.quoted_text} - the learner's quote round trip, including the
 *       domain-{@code ""} to column-{@code NULL} translation the §6.2.19 trigger forces, and the
 *       active revision a reload must show.</li>
 * </ol>
 *
 * <p><strong>Not device evidence.</strong> The user decided on 2026-09-21 to abandon emulator and
 * real-device rounds and to verify by sideloading the release APK, so this file is committed
 * compiled-but-not-executed. Its acceptance row is recorded as {@code deferred}, never as PASS.
 *
 * <p>Holds no real user content and no API key: every literal is synthetic (rules/sql.md).
 */
@RunWith(AndroidJUnit4.class)
public final class RealDataPathWiringInstrumentedTest {

    private static final long T0 = 1_756_000_000_000L;

    /** Synthetic model id; never a real credential. */
    private static final String MODEL = "deepseek-flash";

    /**
     * Both versions are read from the registry rather than written down here, so a fixture cannot
     * pin a policy version the build no longer ships.
     */
    private static final String ANSWER_POLICY_VERSION =
            DeepSeekPromptRegistry.spec(PromptPurpose.LEARNING_ANSWER).policyVersion;

    private static final String SUMMARY_POLICY_VERSION =
            DeepSeekPromptRegistry.spec(PromptPurpose.BRANCH_SUMMARY).policyVersion;

    private static final String SUMMARY_KIND = ArtifactKind.BRANCH_SUMMARY.wireId;

    /** The codec's own floor for a summary-shaped payload; the same figure {@code HomeWiring} uses. */
    private static final int SUMMARY_ITEM_CEILING = 8;

    private static final String TOPIC_A = "aaaaaaaa-0000-4000-8000-0000000000a1";
    private static final String TOPIC_B = "bbbbbbbb-0000-4000-8000-0000000000b2";
    private static final String TOPIC_C = "cccccccc-0000-4000-8000-0000000000c3";

    private TreeLearnDatabaseHelper helper;
    private DatabaseTransactionRunner runner;
    private FirstTopicCreation firstTopic;

    @Before
    public void setUp() {
        helper = new TreeLearnDatabaseHelper(
                InstrumentationRegistry.getInstrumentation().getTargetContext());
        runner = new DatabaseTransactionRunner();
        firstTopic = new FirstTopicCreation(runner);
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
    // 1. listOverviews: the home list statement, row by row.
    // ------------------------------------------------------------------

    @Test
    public void homeListCarriesTheHeadingTheFirstQuestionTheSummaryAndTheBranchCount() {
        Writes writes = firstTopic(TOPIC_A, T0);
        assertTrue("the fixture must be a real first topic",
                create(writes).isCreated());

        String revisionId = insertStableRevision(writes.aiMessage.id, T0 + 5).id;
        Branch fork = insertFork(writes, revisionId, T0 + 10);

        String title = "天空为什么是蓝的";
        Branch titled = BranchDao.findById(db(), writes.branch.id);
        assertNotNull("the root branch must read back", titled);
        titled.setTitle(title, T0 + 20);
        BranchDao.update(db(), titled);

        String summary = "瑞利散射：短波更容易被大气分子散射。";
        String envelope = summaryEnvelope(summary, "短波更易散射");
        insertSummary(writes.branch.id, revisionId, envelope, T0 + 30);

        Topic revisited = writes.topic.withLastVisitedBranch(fork.id, T0 + 40);
        TopicDao.update(db(), revisited);

        List<TopicDao.Overview> overviews = TopicDao.listOverviews(db(), SUMMARY_KIND);

        assertEquals("one row per topic, not one per branch", 1, overviews.size());
        TopicDao.Overview overview = overviews.get(0);
        assertEquals("topic id", TOPIC_A, overview.topicId);
        assertEquals("a visited topic resumes on the branch it was left on", fork.id,
                overview.resumeBranchId);
        assertEquals("the derived root title", title, overview.rootTitle);
        assertEquals("the first learner message is the heading fallback",
                writes.userMessage.userContent, overview.rootQuestion);
        assertEquals("the summary travels as its stored envelope", envelope,
                overview.summaryPayload);
        assertEquals("the count is the size of the tree", 2, overview.branchCount);
        assertEquals("the list is stamped by the topic row", T0 + 40, overview.lastUsedAt);

        // The row is only useful if the screen can decode what the DAO handed over.
        DerivedResult reparsed = ArtifactPayloadCodec.parse(ArtifactKind.BRANCH_SUMMARY,
                overview.summaryPayload, SUMMARY_ITEM_CEILING);
        assertTrue("the stored envelope must be one the codec accepts: " + reparsed.refusal(),
                reparsed.isUsable());
        assertEquals("and it must still carry the summary", summary, reparsed.payload().summary);
    }

    @Test
    public void homeListIsEmptyBeforeAnyTopicExists() {
        assertTrue("a fresh install has no topics to draw",
                TopicDao.listOverviews(db(), SUMMARY_KIND).isEmpty());
    }

    @Test
    public void homeListResumesOnTheRootAndLeavesUnderivedColumnsNullUntilSomethingIsDerived() {
        Writes writes = firstTopic(TOPIC_A, T0);
        assertTrue(create(writes).isCreated());

        List<TopicDao.Overview> overviews = TopicDao.listOverviews(db(), SUMMARY_KIND);

        assertEquals("the first topic is already readable", 1, overviews.size());
        TopicDao.Overview overview = overviews.get(0);
        assertEquals("never visited, so a tap resumes on the root", writes.branch.id,
                overview.resumeBranchId);
        assertNull("no title has been derived yet", overview.rootTitle);
        assertNull("no summary artifact exists yet", overview.summaryPayload);
        assertEquals("but the question is already stored", writes.userMessage.userContent,
                overview.rootQuestion);
        assertEquals("and the tree is just its root", 1, overview.branchCount);
        assertEquals("stamped by the topic row", T0, overview.lastUsedAt);
    }

    @Test
    public void homeListOrdersNewestFirstAndBreaksEqualStampsByIdAscending() {
        assertTrue(create(firstTopic(TOPIC_B, T0)).isCreated());
        assertTrue("the same stamp as TOPIC_B on purpose",
                create(firstTopic(TOPIC_A, T0)).isCreated());

        List<TopicDao.Overview> tied = TopicDao.listOverviews(db(), SUMMARY_KIND);
        assertEquals(2, tied.size());
        assertEquals("equal stamps fall back to id ascending", TOPIC_A, tied.get(0).topicId);
        assertEquals(TOPIC_B, tied.get(1).topicId);

        assertTrue(create(firstTopic(TOPIC_C, T0 + 1_000)).isCreated());

        List<TopicDao.Overview> ordered = TopicDao.listOverviews(db(), SUMMARY_KIND);
        assertEquals(3, ordered.size());
        assertEquals("the newest stamp is first", TOPIC_C, ordered.get(0).topicId);
        assertEquals(TOPIC_A, ordered.get(1).topicId);
        assertEquals(TOPIC_B, ordered.get(2).topicId);
    }

    @Test
    public void homeListCountsAnArchivedBranchInTheSizeOfTheTree() {
        Writes writes = firstTopic(TOPIC_A, T0);
        assertTrue(create(writes).isCreated());
        String revisionId = insertStableRevision(writes.aiMessage.id, T0 + 5).id;
        Branch fork = insertFork(writes, revisionId, T0 + 10);

        Branch archived = BranchDao.findById(db(), fork.id);
        assertNotNull(archived);
        archived.archive(T0 + 20);
        BranchDao.update(db(), archived);

        TopicDao.Overview overview = TopicDao.listOverviews(db(), SUMMARY_KIND).get(0);
        assertEquals("an archived branch is still part of the tree", 2, overview.branchCount);
    }

    @Test
    public void homeListCannotBeMultipliedByASummaryBecauseOneSummaryPerScopeIsTheStoredRule() {
        Writes writes = firstTopic(TOPIC_A, T0);
        assertTrue(create(writes).isCreated());
        String revisionId = insertStableRevision(writes.aiMessage.id, T0 + 5).id;
        insertSummary(writes.branch.id, revisionId, summaryEnvelope("一", "要点"), T0 + 10);

        List<TopicDao.Overview> overviews = TopicDao.listOverviews(db(), SUMMARY_KIND);

        assertEquals("the summary join must not multiply its topic", 1, overviews.size());
        assertEquals("and the count is still the tree's size", 1, overviews.get(0).branchCount);

        // The row shape above is only duplicate-free because the store refuses a second artifact
        // for one (kind, scope) pair; assert that refusal rather than assume it.
        IllegalStateException refused = assertThrows(IllegalStateException.class,
                () -> insertSummary(writes.branch.id, revisionId, summaryEnvelope("二", "要点"),
                        T0 + 20));
        assertTrue("the refusal must name the stored pair: " + refused.getMessage(),
                refused.getMessage().contains(SUMMARY_KIND));
    }

    // ------------------------------------------------------------------
    // 2. messages.quoted_text: the quote round trip (§6.2.19).
    // ------------------------------------------------------------------

    @Test
    public void learnerQuoteRoundTripsThroughTheQuotedTextColumn() {
        Writes writes = firstTopic(TOPIC_A, T0);
        assertTrue(create(writes).isCreated());

        String quote = "大气中的分子把蓝光散射得更多";
        Message asking = Message.user(writes.branch.id, 2, "那为什么不是紫色？", quote, T0 + 10);
        MessageDao.insert(db(), asking);

        Message readBack = messageById(asking.id);
        assertNotNull("the row must read back", readBack);
        assertEquals("the quote comes back with the message", quote, readBack.quotedText());
        assertEquals("and the column keeps the selection exactly as handed over", quote,
                quotedTextColumnOf(asking.id));
    }

    @Test
    public void noQuoteIsTheEmptyStringInTheDomainAndNullInTheColumn() {
        Writes writes = firstTopic(TOPIC_A, T0);
        assertTrue(create(writes).isCreated());

        Message plain = Message.user(writes.branch.id, 2, "继续", T0 + 10);
        MessageDao.insert(db(), plain);

        Message readBack = messageById(plain.id);
        assertNotNull("the row must read back", readBack);
        assertEquals("the domain spells no quote as the empty string", "", readBack.quotedText());
        assertNull("and the column stores NULL for it, never an empty string",
                quotedTextColumnOf(plain.id));
    }

    @Test
    public void anAiPositionCarriesNoQuote() {
        Writes writes = firstTopic(TOPIC_A, T0);
        assertTrue(create(writes).isCreated());

        Message readBack = messageById(writes.aiMessage.id);
        assertNotNull("the position must read back", readBack);
        assertFalse("the fixture's second position is the answer position", readBack.isUser());
        assertEquals("an answer position quotes nothing", "", readBack.quotedText());
        assertNull("and its column is NULL", quotedTextColumnOf(writes.aiMessage.id));
    }

    @Test
    public void readBackCarriesTheDisplayedRevisionOfAnAnswerPosition() {
        Writes writes = firstTopic(TOPIC_A, T0);
        assertTrue(create(writes).isCreated());
        String revisionId = insertStableRevision(writes.aiMessage.id, T0 + 5).id;

        Message position = MessageDao.findById(db(), writes.aiMessage.id);
        assertNotNull("the position must read back", position);
        assertTrue("only an answer position displays a revision", position.isAi());
        assertTrue("the fixture must be a legal display switch: " + position.activeRevisionId(),
                position.activateRevision(revisionId).isOk());
        MessageDao.updateActiveReferences(db(), position);

        Message readBack = messageById(writes.aiMessage.id);
        assertNotNull(readBack);
        assertEquals("the displayed version is read from the row", revisionId,
                readBack.activeRevisionId());
        assertNull("no attempt is running, so the row names none", readBack.activeOperationId());

        List<Message> positions = MessageDao.findByBranch(db(), writes.branch.id);
        assertEquals("the transcript is the branch's positions", 2, positions.size());
        assertEquals("in ordinal order", 0, positions.get(0).ordinal);
        assertEquals(1, positions.get(1).ordinal);
    }

    // ------------------------------------------------------------------
    // Fixtures: every one of these is a write the product really makes.
    // ------------------------------------------------------------------

    /**
     * The five row groups of a first topic, through the one creation path, so the fixture is the
     * transaction under test rather than a hand-built approximation of it.
     */
    private FirstTopicCreation.Outcome create(Writes writes) {
        return firstTopic.create(db(), writes.topic, writes.branch, writes.userMessage,
                writes.aiMessage, writes.operation);
    }

    private static Writes firstTopic(String topicId, long now) {
        Topic topic = new Topic(topicId, null, now, now);
        Branch root = Branch.root(topic.id, now);
        Message userMessage = Message.user(root.id, 0, "为什么天空是蓝色的？", now);
        Message aiMessage = Message.ai(root.id, 1, now);
        return new Writes(topic, root, userMessage, aiMessage,
                operationFor(root, aiMessage, now));
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
     * carrying a birth snapshot row, and built through {@code Branch.child} rather than through
     * {@code restore}.
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

    private Message messageById(String messageId) {
        return MessageDao.findById(db(), messageId);
    }

    /** @return the raw {@code quoted_text} column: null when the column is SQL NULL */
    private String quotedTextColumnOf(String messageId) {
        try (Cursor cursor = db().rawQuery(
                "SELECT " + TreeLearnSchema.Messages.QUOTED_TEXT + " FROM "
                        + TreeLearnSchema.Messages.TABLE + " WHERE "
                        + TreeLearnSchema.Messages.ID + "=?",
                new String[]{messageId})) {
            assertTrue("no row carries message " + messageId, cursor.moveToFirst());
            return cursor.isNull(0) ? null : cursor.getString(0);
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