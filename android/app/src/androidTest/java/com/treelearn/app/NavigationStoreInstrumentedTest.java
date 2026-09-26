package com.treelearn.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.treelearn.ai.gateway.AiGatewayError;
import com.treelearn.app.artifact.ArtifactKind;
import com.treelearn.app.generation.GenerationCoordinator;
import com.treelearn.app.generation.GenerationGateway;
import com.treelearn.app.generation.GenerationTelemetry;
import com.treelearn.app.navigation.NavigationData;
import com.treelearn.app.navigation.SqliteNavigationStore;
import com.treelearn.core.domain.Branch;
import com.treelearn.core.domain.DomainResult;
import com.treelearn.core.domain.GenerationCommand;
import com.treelearn.core.domain.GenerationModel;
import com.treelearn.core.domain.GenerationOperation;
import com.treelearn.core.domain.Message;
import com.treelearn.core.domain.PromptPurpose;
import com.treelearn.core.domain.Topic;
import com.treelearn.data.db.DatabaseTransactionRunner;
import com.treelearn.data.db.TreeLearnDatabaseHelper;
import com.treelearn.data.db.TreeLearnSchema;
import com.treelearn.data.db.dao.AiArtifactDao;
import com.treelearn.data.db.dao.BranchDao;
import com.treelearn.data.db.dao.GenerationOperationDao;
import com.treelearn.data.db.dao.GenerationStabilizer;
import com.treelearn.data.db.dao.MessageDao;
import com.treelearn.data.db.dao.TopicDao;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Collections;

/**
 * T-014 acceptance evidence for the navigation store, written for real Android framework SQLite
 * (API 36): archiving that keeps history, a deletion that cascades, an attempt that cannot write
 * back after its branch is gone, and a summary read that only ever returns the branch-summary kind.
 *
 * <p><strong>Not executed this round.</strong> The project owner forbade emulator use on 2026-09-21,
 * so every case here is compiled by {@code :app:assembleDebugAndroidTest} and no case is run; the
 * device-side results stay deferred until a device is allowed back. The JVM suite
 * {@code NavigationPresenterTest} covers the decisions that do not need the platform.
 *
 * <p>Holds no real user content and no API key: every literal here is an obviously synthetic placeholder, and the payloads
 * are summary-shaped JSON the codec would have produced, not anything a provider returned.
 */
@RunWith(AndroidJUnit4.class)
public final class NavigationStoreInstrumentedTest {

    private static final long T0 = 1_756_000_000_000L;

    private static final String TOPIC = "11111111-1111-4111-8111-000000000001";
    private static final String TITLE_ARTIFACT = "22222222-2222-4222-8222-000000000002";
    private static final String SUMMARY_ARTIFACT = "33333333-3333-4333-8333-000000000003";

    /** Synthetic policy versions; the purposes own these prefixes (prompt design 3). */
    private static final String LEARNING_VERSION = "tl-learning-answer/1.0.0";
    private static final String TITLE_VERSION = "tl-root-title/1.0.0";
    private static final String SUMMARY_VERSION = "tl-branch-summary/1.0.0";

    private static final String TITLE_PAYLOAD = "{\"title\":\"SYNTHETIC-TITLE\"}";

    /**
     * The branch title the fixture persists. A root branch is born without one and receives it from
     * the root-title artifact, so navigation reads a row whose title is null until that write
     * happens; {@code NavigationData.Node} refuses a null title, which is the production rule, so the
     * fixture has to make the same write the product makes rather than the rule being relaxed.
     */
    private static final String TITLE_TEXT = "SYNTHETIC-TITLE";
    private static final String SUMMARY_PAYLOAD =
            "{\"summary\":\"SYNTHETIC-SUMMARY\",\"key_points\":[\"SYNTHETIC-KEY-POINT-1\",\"SYNTHETIC-KEY-POINT-2\"]}";

    private TreeLearnDatabaseHelper helper;
    private DatabaseTransactionRunner runner;
    private SqliteNavigationStore store;
    private String rootBranchId;
    private String aiMessageId;

    @Before
    public void setUp() {
        context().deleteDatabase(TreeLearnDatabaseHelper.DATABASE_NAME);
        helper = new TreeLearnDatabaseHelper(context());
        runner = new DatabaseTransactionRunner();
        store = new SqliteNavigationStore(helper, runner, coordinator());
        seed();
    }

    @After
    public void tearDown() {
        if (helper != null) {
            helper.close();
        }
        context().deleteDatabase(TreeLearnDatabaseHelper.DATABASE_NAME);
    }

    @Test
    public void archivingFlipsTheFlagKeepsTheHistoryAndIsReversible() {
        store.setArchived(rootBranchId, true, T0 + 10_000L);

        NavigationData.Snapshot archived = store.load(TOPIC);
        assertTrue("the branch reports itself archived", archived.node(rootBranchId).archived);
        assertEquals("archiving never removes a message", 2,
                MessageDao.findByBranch(db(), rootBranchId).size());

        store.setArchived(rootBranchId, false, T0 + 20_000L);

        assertFalse("an archived branch is recoverable, not gone",
                store.load(TOPIC).node(rootBranchId).archived);
        assertEquals("and its history is still there", 2,
                MessageDao.findByBranch(db(), rootBranchId).size());
    }

    @Test
    public void deletingTheWholeTopicRemovesTheTopicItsBranchesAndTheirMessages() {
        store.delete(new NavigationData.Plan(TOPIC, rootBranchId,
                Collections.singletonList(rootBranchId), true));

        assertNull("the topic row is gone", TopicDao.findById(db(), TOPIC));
        assertTrue("its branch went with it", BranchDao.findByTopic(db(), TOPIC).isEmpty());
        assertEquals("and so did the messages the branch owned, by cascade", 0,
                count(TreeLearnSchema.Messages.TABLE));
    }

    @Test
    public void aSubtreePlanRemovesOnlyTheBranchesItNamesAndLeavesTheTopic() {
        store.delete(new NavigationData.Plan(TOPIC, rootBranchId,
                Collections.singletonList(rootBranchId), false));

        assertNotNull("a branch deletion is not a topic deletion", TopicDao.findById(db(), TOPIC));
        assertTrue(BranchDao.findByTopic(db(), TOPIC).isEmpty());
        assertEquals(0, count(TreeLearnSchema.Messages.TABLE));
    }

    @Test
    public void aLateStabilizationAfterTheDeletionIsRefusedAndWritesNoRevision() {
        GenerationOperation operation = GenerationOperation.start(command(), T0 + 3_000L);
        GenerationOperationDao.insert(db(), operation);

        store.delete(new NavigationData.Plan(TOPIC, rootBranchId,
                Collections.singletonList(rootBranchId), false));

        DomainResult<com.treelearn.core.domain.AnswerRevision> refused =
                new GenerationStabilizer(runner).stabilize(db(), operation.id, 1, T0 + 9_000L);

        assertTrue("an attempt whose branch was deleted must not freeze a revision: " + refused,
                refused.isFailure());
        assertEquals("and it writes no revision row", 0,
                count(TreeLearnSchema.AnswerRevisions.TABLE));
    }

    @Test
    public void cancellingWithNothingRunningReportsNothingWasCancelled() {
        assertEquals("no attempt is open, so nothing is aborted",
                0, store.cancelInFlight(Collections.singletonList(rootBranchId)));
    }

    @Test
    public void theSummaryIsTheBranchSummaryKindAndNeverAnotherUsesPayload() {
        SQLiteDatabase database = db();
        AiArtifactDao.insert(database, new AiArtifactDao.Row(TITLE_ARTIFACT,
                ArtifactKind.ROOT_TITLE.wireId, rootBranchId,
                ArtifactKind.ROOT_TITLE.purpose.policyPrefix, TITLE_VERSION, TITLE_PAYLOAD, null,
                "synthetic-title-digest", T0, T0));

        assertNull("a title row for the same scope is not a summary",
                store.summary(rootBranchId));

        AiArtifactDao.insert(database, new AiArtifactDao.Row(SUMMARY_ARTIFACT,
                ArtifactKind.BRANCH_SUMMARY.wireId, rootBranchId,
                ArtifactKind.BRANCH_SUMMARY.purpose.policyPrefix, SUMMARY_VERSION, SUMMARY_PAYLOAD,
                null, "synthetic-summary-digest", T0, T0));

        NavigationData.Summary summary = store.summary(rootBranchId);
        assertNotNull(summary);
        assertEquals(rootBranchId, summary.branchId);
        assertEquals("SYNTHETIC-SUMMARY", summary.text);
        assertEquals(Arrays.asList("SYNTHETIC-KEY-POINT-1", "SYNTHETIC-KEY-POINT-2"), summary.keyPoints);
    }

    private void seed() {
        SQLiteDatabase database = db();
        TopicDao.insert(database, new Topic(TOPIC, null, T0, T0));
        Branch root = Branch.root(TOPIC, T0);
        BranchDao.insert(database, root);
        // The title arrives after creation, through the same mutable-field write the product uses
        // (the root-title artifact deriver): the root-title artifact is generated for an existing
        // branch, so a freshly inserted root really does carry no title until this write.
        root.setTitle(TITLE_TEXT, T0 + 1L);
        BranchDao.update(database, root);
        rootBranchId = root.id;
        MessageDao.insert(database, Message.user(root.id, 1, "SYNTHETIC-USER-TURN", T0 + 1_000L));
        Message ai = Message.ai(root.id, 2, T0 + 2_000L);
        MessageDao.insert(database, ai);
        aiMessageId = ai.id;
    }

    private GenerationCommand command() {
        DomainResult<GenerationCommand> created = GenerationCommand.create(rootBranchId, aiMessageId,
                PromptPurpose.LEARNING_ANSWER, LEARNING_VERSION, GenerationModel.FLASH, false,
                T0 + 3_000L);
        assertTrue("the fixture pins a version the learning purpose owns: " + created,
                created.isOk());
        return created.value();
    }

    private GenerationCoordinator coordinator() {
        return new GenerationCoordinator(helper, runner,
                (model, request, sink) -> {
                    throw new UnsupportedOperationException("this suite never opens a stream");
                },
                Clock.fixed(Instant.ofEpochMilli(T0), ZoneOffset.UTC),
                new GenerationTelemetry() {
                    @Override
                    public void record(GenerationTelemetry.Event event, String operationId,
                            String branchId, int chars, AiGatewayError.Kind errorKind) {
                        // Redacted by construction: this suite asserts store behaviour, not telemetry.
                    }
                });
    }

    private int count(String table) {
        try (Cursor cursor = db().rawQuery("SELECT COUNT(*) FROM " + table, null)) {
            cursor.moveToFirst();
            return cursor.getInt(0);
        }
    }

    private SQLiteDatabase db() {
        return helper.getWritableDatabase();
    }

    private static Context context() {
        return InstrumentationRegistry.getInstrumentation().getTargetContext();
    }
}
