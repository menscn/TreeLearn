package com.treelearn.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteConstraintException;
import android.database.sqlite.SQLiteDatabase;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.treelearn.core.domain.Branch;
import com.treelearn.core.domain.Topic;
import com.treelearn.data.db.ConstraintExtendedCode;
import com.treelearn.data.db.DatabaseTransactionRunner;
import com.treelearn.data.db.TreeLearnDatabaseHelper;
import com.treelearn.data.db.TreeLearnSchema;
import com.treelearn.data.db.dao.AiArtifactDao;
import com.treelearn.data.db.dao.BranchDao;
import com.treelearn.data.db.dao.ContextSnapshotDao;
import com.treelearn.data.db.dao.TopicDao;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;

/**
 * T-024 acceptance evidence for {@code context_snapshots} / {@code ai_artifacts}, executed on real
 * Android framework SQLite (API 36).
 *
 * <p>Rejections are asserted <em>by cause</em> using the SQLite extended result code, for the same
 * reason T-004 moved to them: a message substring can be satisfied by the wording of a constraint
 * rather than by its class. Codes measured in
 * {@code artifacts/evidence/T-004-constraint-code-probe.txt} (API 36) and
 * {@code artifacts/evidence/T-059-constraint-codes.txt} (Android 13), and read through the shared
 * {@link ConstraintExtendedCode} point rather than through a hand-written prefix.
 *
 * <p>Every literal is synthetic; the file holds no real user content and no API key (rules/sql.md).
 */
@RunWith(AndroidJUnit4.class)
public final class ContextSnapshotAiArtifactDaoInstrumentedTest {

    private static final long T0 = 1_756_000_000_000L;

    private static final String SNAPSHOT_A = "aaaaaaa1-1111-4111-8111-111111111111";
    private static final String SNAPSHOT_B = "aaaaaaa2-1111-4111-8111-111111111111";
    private static final String AI_MESSAGE_ID = "bbbbbbb1-1111-4111-8111-111111111111";
    private static final String REVISION_1 = "ccccccc1-1111-4111-8111-111111111111";
    private static final String ARTIFACT_1 = "ddddddd1-1111-4111-8111-111111111111";
    private static final String ARTIFACT_2 = "ddddddd2-1111-4111-8111-111111111111";

    private static final String KIND = "branch-title";
    private static final String OTHER_KIND = "branch-summary";
    private static final String PURPOSE = "tl-title";
    private static final String POLICY_VERSION = "tl-title/1.0.0";

    /** Synthetic body marker: proves nothing outside a payload column can carry such text. */
    private static final String BODY_SENTINEL = "SENTINEL-DO-NOT-STORE-OUTSIDE-PAYLOAD";

    /**
     * Declared here independently; the reading of the code out of the refusal text is shared with
     * production through {@link ConstraintExtendedCode}. The reader matches the stable
     * {@code code <digits>} fragment rather than a prefix, because Android spells the same refusal as
     * {@code (code 2067 ...)} on API 36 and as {@code (Sqlite code 2067 ...)} on Android 13.
     */
    private static final int CODE_UNIQUE = 2067;
    private static final int CODE_NOT_NULL = 1299;
    private static final int CODE_TRIGGER = 1811;

    private TreeLearnDatabaseHelper helper;

    @Before
    public void setUp() {
        helper = new TreeLearnDatabaseHelper(context());
        context().deleteDatabase(TreeLearnDatabaseHelper.DATABASE_NAME);
    }

    @After
    public void tearDown() {
        if (helper != null) {
            helper.close();
        }
        context().deleteDatabase(TreeLearnDatabaseHelper.DATABASE_NAME);
    }

    private static Context context() {
        return InstrumentationRegistry.getInstrumentation().getTargetContext();
    }

    // ------------------------------------------------------------------
    // context_snapshots
    // ------------------------------------------------------------------

    @Test
    public void snapshotRoundTripsThroughCloseAndReopen() {
        SQLiteDatabase db = helper.getWritableDatabase();
        Branch branch = seedBranch(db);
        String payload = "{\"schema\":1,\"sources\":[{\"messageId\":\"" + AI_MESSAGE_ID + "\"}]}";
        ContextSnapshotDao.insert(db, new ContextSnapshotDao.Row(SNAPSHOT_A, branch.id, 1,
                "digest-a", payload, T0));

        helper.close();
        SQLiteDatabase reopened = helper.getWritableDatabase();

        ContextSnapshotDao.Row readBack = ContextSnapshotDao.findByBranchId(reopened, branch.id);
        assertNotNull("a written snapshot must be readable after a reopen", readBack);
        assertEquals("snapshot id", SNAPSHOT_A, readBack.id);
        assertEquals("snapshot branch", branch.id, readBack.branchId);
        assertEquals("snapshot schema version", 1, readBack.schemaVersion);
        assertEquals("snapshot digest", "digest-a", readBack.digest);
        assertEquals("snapshot payload must come back byte for byte", payload, readBack.payload);
        assertEquals("snapshot createdAt", T0, readBack.createdAt);
        assertForeignKeysIntact(reopened);
    }

    @Test
    public void secondSnapshotForTheSameBranchIsRefusedByTheUniqueIndex() {
        SQLiteDatabase db = helper.getWritableDatabase();
        Branch branch = seedBranch(db);
        ContextSnapshotDao.insert(db, new ContextSnapshotDao.Row(SNAPSHOT_A, branch.id, 1,
                "digest-a", "{}", T0));

        SQLiteConstraintException failure = assertThrows(SQLiteConstraintException.class,
                () -> ContextSnapshotDao.insert(db, new ContextSnapshotDao.Row(SNAPSHOT_B, branch.id,
                        1, "digest-b", "{}", T0 + 1)));
        assertCause("a branch has exactly one birth snapshot", failure.getMessage(), CODE_UNIQUE,
                "context_snapshots.branch_id");
        assertEquals("the refused row must not be present",
                SNAPSHOT_A, ContextSnapshotDao.findByBranchId(db, branch.id).id);
    }

    @Test
    public void snapshotShapeIsRejectedBeforeTheStoreSeesIt() {
        SQLiteDatabase db = helper.getWritableDatabase();
        Branch branch = seedBranch(db);

        assertThrows("a blank digest is a caller bug, not a business refusal",
                IllegalArgumentException.class,
                () -> new ContextSnapshotDao.Row(SNAPSHOT_A, branch.id, 1, "  ", "{}", T0));
        assertThrows("a blank payload is a caller bug",
                IllegalArgumentException.class,
                () -> new ContextSnapshotDao.Row(SNAPSHOT_A, branch.id, 1, "digest-a", "", T0));
        assertThrows("schema version 0 is not a schema version",
                IllegalArgumentException.class,
                () -> new ContextSnapshotDao.Row(SNAPSHOT_A, branch.id, 0, "digest-a", "{}", T0));
        assertThrows("a non-UUID snapshot id is rejected",
                IllegalArgumentException.class,
                () -> new ContextSnapshotDao.Row("not-a-uuid", branch.id, 1, "digest-a", "{}", T0));

        // The store keeps the last word on NULL, which the constructor cannot produce at all.
        SQLiteConstraintException nullDigest = assertThrows(SQLiteConstraintException.class,
                () -> db.execSQL("INSERT INTO context_snapshots(id, branch_id, schema_version,"
                                + " digest, payload, created_at) VALUES(?,?,?,?,?,?)",
                        new Object[]{SNAPSHOT_A, branch.id, 1, null, "{}", T0}));
        assertCause("digest is mandatory in the store too", nullDigest.getMessage(), CODE_NOT_NULL,
                "context_snapshots.digest");
    }

    @Test
    public void snapshotIsImmutableAndSurvivesOnlyWithItsBranch() {
        SQLiteDatabase db = helper.getWritableDatabase();
        Branch branch = seedBranch(db);
        ContextSnapshotDao.insert(db, new ContextSnapshotDao.Row(SNAPSHOT_A, branch.id, 1,
                "digest-a", "{}", T0));

        SQLiteConstraintException rewrite = assertThrows(SQLiteConstraintException.class,
                () -> db.execSQL("UPDATE context_snapshots SET payload=? WHERE id=?",
                        new Object[]{"rewritten", SNAPSHOT_A}));
        assertCause("a birth snapshot is immutable", rewrite.getMessage(), CODE_TRIGGER, null);

        SQLiteConstraintException strayDelete = assertThrows(SQLiteConstraintException.class,
                () -> db.execSQL("DELETE FROM context_snapshots WHERE id=?", new Object[]{SNAPSHOT_A}));
        assertCause("a snapshot is never deleted on its own", strayDelete.getMessage(), CODE_TRIGGER,
                null);

        int removed = db.delete(TreeLearnSchema.Branches.TABLE, TreeLearnSchema.Branches.ID + "=?",
                new String[]{branch.id});
        assertEquals("deleting the branch removes exactly one branch row", 1, removed);
        assertNull("the snapshot leaves together with its branch",
                ContextSnapshotDao.findByBranchId(db, branch.id));
        assertEquals("and the snapshot row is gone", 0, count(db,
                "SELECT COUNT(*) FROM context_snapshots WHERE id='" + SNAPSHOT_A + "'"));
    }

    /**
     * The payload is stored verbatim and read back as the same string, and it survives a real
     * structured parse. Nothing here may substring, trim or rebuild it.
     */
    @Test
    public void snapshotPayloadSurvivesAStructuredParseRoundTrip() throws JSONException {
        SQLiteDatabase db = helper.getWritableDatabase();
        Branch branch = seedBranch(db);
        String payload = "{\"schema\":1,\"sources\":[{\"messageId\":\"" + AI_MESSAGE_ID
                + "\",\"revisionId\":\"" + REVISION_1 + "\"}],\"policy\":\"" + POLICY_VERSION + "\"}";
        ContextSnapshotDao.insert(db, new ContextSnapshotDao.Row(SNAPSHOT_A, branch.id, 1,
                "digest-a", payload, T0));

        String stored = ContextSnapshotDao.findByBranchId(db, branch.id).payload;
        assertEquals("the stored string must equal the written string", payload, stored);

        JSONObject parsed = new JSONObject(stored);
        assertEquals("parsed schema version", 1, parsed.getInt("schema"));
        assertEquals("parsed source message", AI_MESSAGE_ID,
                parsed.getJSONArray("sources").getJSONObject(0).getString("messageId"));
        assertEquals("parsed policy", POLICY_VERSION, parsed.getString("policy"));
        assertEquals("a parse and re-serialise round trip must not change the value",
                parsed.toString(), new JSONObject(payload).toString());
    }

    // ------------------------------------------------------------------
    // ai_artifacts
    // ------------------------------------------------------------------

    @Test
    public void artifactShapeIsRejectedBeforeTheStoreSeesIt() {
        SQLiteDatabase db = helper.getWritableDatabase();
        Branch branch = seedBranch(db);
        seedRevision(db, branch.id);

        assertThrows("a blank payload is a caller bug", IllegalArgumentException.class,
                () -> artifact(ARTIFACT_1, KIND, branch.id, " ", REVISION_1, "in-1", T0));
        assertThrows("a blank prompt purpose is a caller bug", IllegalArgumentException.class,
                () -> new AiArtifactDao.Row(ARTIFACT_1, KIND, branch.id, "  ", POLICY_VERSION,
                        "payload", REVISION_1, "in-1", T0, T0));
        assertThrows("a blank policy version is a caller bug", IllegalArgumentException.class,
                () -> new AiArtifactDao.Row(ARTIFACT_1, KIND, branch.id, PURPOSE, "", "payload",
                        REVISION_1, "in-1", T0, T0));
        assertThrows("a blank input digest is a caller bug", IllegalArgumentException.class,
                () -> new AiArtifactDao.Row(ARTIFACT_1, KIND, branch.id, PURPOSE, POLICY_VERSION,
                        "payload", REVISION_1, "  ", T0, T0));
        assertThrows("a non-UUID artifact id is rejected", IllegalArgumentException.class,
                () -> new AiArtifactDao.Row("nope", KIND, branch.id, PURPOSE, POLICY_VERSION,
                        "payload", REVISION_1, "in-1", T0, T0));

        SQLiteConstraintException nullPayload = assertThrows(SQLiteConstraintException.class,
                () -> db.execSQL("INSERT INTO ai_artifacts(id, kind, scope_id, prompt_purpose,"
                                + " prompt_policy_version, payload, based_on_revision_id,"
                                + " input_digest, created_at, updated_at) VALUES(?,?,?,?,?,?,?,?,?,?)",
                        new Object[]{ARTIFACT_1, KIND, branch.id, PURPOSE, POLICY_VERSION, null,
                                REVISION_1, "in-1", T0, T0}));
        assertCause("payload is mandatory in the store too", nullPayload.getMessage(), CODE_NOT_NULL,
                "ai_artifacts.payload");
    }

    @Test
    public void artifactInsertIsConditionalOnThePairBeingFree() {
        SQLiteDatabase db = helper.getWritableDatabase();
        Branch branch = seedBranch(db);
        seedRevision(db, branch.id);
        AiArtifactDao.insert(db, artifact(ARTIFACT_1, KIND, branch.id, "first payload", REVISION_1,
                "in-1", T0));

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> AiArtifactDao.insert(db, artifact(ARTIFACT_2, KIND, branch.id, "second payload",
                        REVISION_1, "in-2", T0 + 1)));
        assertTrue("the refusal must explain the one-row-per-pair rule: " + failure.getMessage(),
                failure.getMessage().contains("already exists"));
        assertEquals("the first payload must survive the refused insert", "first payload",
                AiArtifactDao.findByKindAndScope(db, KIND, branch.id).payload);
    }

    @Test
    public void artifactWritebackAppliesOnlyWhileTheBaselineMatches() {
        SQLiteDatabase db = helper.getWritableDatabase();
        Branch branch = seedBranch(db);
        seedRevision(db, branch.id);
        AiArtifactDao.insert(db, artifact(ARTIFACT_1, KIND, branch.id, "v1", REVISION_1, "in-1", T0));

        assertEquals("a matching baseline replaces the payload", AiArtifactDao.Writeback.APPLIED,
                AiArtifactDao.applyIfBaselineMatches(db,
                        artifact(ARTIFACT_2, KIND, branch.id, "v2", REVISION_1, "in-1", T0 + 10)));
        assertEquals("the new payload is stored", "v2",
                AiArtifactDao.findByKindAndScope(db, KIND, branch.id).payload);

        assertEquals("a different input digest is stale", AiArtifactDao.Writeback.DISCARDED_STALE,
                AiArtifactDao.applyIfBaselineMatches(db,
                        artifact(ARTIFACT_1, KIND, branch.id, "v3", REVISION_1, "in-other", T0 + 20)));
        assertEquals("a stale write-back must leave the stored payload untouched", "v2",
                AiArtifactDao.findByKindAndScope(db, KIND, branch.id).payload);

        assertEquals("a different baseline revision is stale",
                AiArtifactDao.Writeback.DISCARDED_STALE,
                AiArtifactDao.applyIfBaselineMatches(db,
                        artifact(ARTIFACT_1, KIND, branch.id, "v4",
                                "ccccccc2-1111-4111-8111-111111111111", "in-1", T0 + 30)));
        assertEquals("still untouched", "v2",
                AiArtifactDao.findByKindAndScope(db, KIND, branch.id).payload);

        assertEquals("a pair that was never derived has nothing to update",
                AiArtifactDao.Writeback.DISCARDED_STALE,
                AiArtifactDao.applyIfBaselineMatches(db,
                        artifact(ARTIFACT_1, OTHER_KIND, branch.id, "v5", REVISION_1, "in-1", T0)));
        assertNull("and the stale attempt must not create a row",
                AiArtifactDao.findByKindAndScope(db, OTHER_KIND, branch.id));
    }

    /**
     * {@code ON DELETE SET NULL} can clear the stored baseline. Such an artifact can never be
     * written back, and the payload it already holds is kept rather than overwritten by an unrelated
     * derivation. This is the deliberate trade-off recorded in the task handoff notes.
     */
    @Test
    public void artifactWithAClearedBaselineNeverAcceptsAWriteback() {
        SQLiteDatabase db = helper.getWritableDatabase();
        Branch branch = seedBranch(db);
        seedRevision(db, branch.id);
        AiArtifactDao.insert(db, artifact(ARTIFACT_1, KIND, branch.id, "v1", REVISION_1, "in-1", T0));

        // The revision cannot be deleted on its own — trg_answer_revisions_no_direct_delete forbids
        // exactly that. Deleting the owning message is the legitimate path: the revision then leaves
        // through the cascade, and the artifact's FK action clears its baseline.
        db.execSQL("DELETE FROM messages WHERE id=?", new Object[]{AI_MESSAGE_ID});
        assertNull("the baseline was cleared by the FK action",
                AiArtifactDao.findByKindAndScope(db, KIND, branch.id).basedOnRevisionId);

        assertEquals("a cleared baseline is stale, never a match",
                AiArtifactDao.Writeback.DISCARDED_STALE,
                AiArtifactDao.applyIfBaselineMatches(db,
                        artifact(ARTIFACT_2, KIND, branch.id, "v2", REVISION_1, "in-1", T0 + 10)));
        assertEquals("the earlier payload survives", "v1",
                AiArtifactDao.findByKindAndScope(db, KIND, branch.id).payload);
    }

    @Test
    public void deleteByScopeRemovesOnlyThatKindAndScope() {
        SQLiteDatabase db = helper.getWritableDatabase();
        Branch branch = seedBranch(db);
        Branch otherBranch = seedBranch(db, "topic-2");
        seedRevision(db, branch.id);
        AiArtifactDao.insert(db, artifact(ARTIFACT_1, KIND, branch.id, "a", REVISION_1, "in-1", T0));
        AiArtifactDao.insert(db, artifact(ARTIFACT_2, OTHER_KIND, branch.id, "b", REVISION_1,
                "in-1", T0));
        AiArtifactDao.insert(db, artifact("ddddddd3-1111-4111-8111-111111111111", KIND,
                otherBranch.id, "c", REVISION_1, "in-1", T0));

        assertEquals("exactly the one (kind, scope) pair is removed", 1,
                AiArtifactDao.deleteByScope(db, KIND, branch.id));
        assertNull(AiArtifactDao.findByKindAndScope(db, KIND, branch.id));
        assertNotNull("another kind for the same scope is untouched",
                AiArtifactDao.findByKindAndScope(db, OTHER_KIND, branch.id));
        assertNotNull("the same kind in another scope is untouched",
                AiArtifactDao.findByKindAndScope(db, KIND, otherBranch.id));
    }

    /**
     * The documented choice for a payload that is not valid JSON: this layer does not parse, so it
     * stores the bytes and the consumer fails on its own parse. Asserted rather than left implicit,
     * because the alternative (rejecting at write time) would put the payload's schema in this layer
     * and create a second place that knows it.
     */
    @Test
    public void illegalJsonPayloadIsStoredVerbatimAndFailsOnlyWhenParsed() {
        SQLiteDatabase db = helper.getWritableDatabase();
        Branch branch = seedBranch(db);
        String notJson = "{ this is not json";
        ContextSnapshotDao.insert(db, new ContextSnapshotDao.Row(SNAPSHOT_A, branch.id, 1,
                "digest-a", notJson, T0));

        assertEquals("the layer stores the bytes it was given", notJson,
                ContextSnapshotDao.findByBranchId(db, branch.id).payload);
        assertThrows("the consumer's parse is where a malformed payload surfaces",
                JSONException.class, () -> new JSONObject(notJson));
    }

    /**
     * No column outside the payload columns may carry body-like text, and no DAO method takes a
     * system-prompt argument. Both halves of "no prompt body reaches the store" are checked here:
     * the signature half is a source-level guarantee recorded in the task's documentation impact,
     * and the storage half is this scan.
     */
    @Test
    public void onlyPayloadColumnsCanCarryBodyText() {
        SQLiteDatabase db = helper.getWritableDatabase();
        Branch branch = seedBranch(db);
        seedRevision(db, branch.id);
        ContextSnapshotDao.insert(db, new ContextSnapshotDao.Row(SNAPSHOT_A, branch.id, 1,
                "digest-a", "{\"note\":\"" + BODY_SENTINEL + "\"}", T0));
        AiArtifactDao.insert(db, artifact(ARTIFACT_1, KIND, branch.id,
                "{\"title\":\"" + BODY_SENTINEL + "\"}", REVISION_1, "in-1", T0));

        assertEquals("only the snapshot payload may hold such text", "[payload]",
                textColumnsHolding(db, TreeLearnSchema.ContextSnapshots.TABLE, BODY_SENTINEL)
                        .toString());
        assertEquals("only the artifact payload may hold such text", "[payload]",
                textColumnsHolding(db, TreeLearnSchema.AiArtifacts.TABLE, BODY_SENTINEL).toString());
    }

    // ------------------------------------------------------------------
    // Fixtures and helpers.
    // ------------------------------------------------------------------

    private static Branch seedBranch(SQLiteDatabase db) {
        return seedBranch(db, "topic-1");
    }

    private static Branch seedBranch(SQLiteDatabase db, String topicSuffix) {
        String topicId = "aaaa0000-0000-4000-8000-0000000000" + (topicSuffix.equals("topic-1")
                ? "01" : "02");
        Topic topic = new Topic(topicId, null, T0, T0);
        TopicDao.insert(db, topic);
        Branch root = Branch.root(topic.id, T0);
        BranchDao.insert(db, root);
        return root;
    }

    /** The stable revision an artifact's baseline points at. Its DAO belongs to T-023. */
    private static void seedRevision(SQLiteDatabase db, String branchId) {
        db.execSQL("INSERT INTO messages(id, branch_id, ordinal, role, user_content, created_at)"
                        + " VALUES(?,?,?,?,?,?)",
                new Object[]{AI_MESSAGE_ID, branchId, 0, "AI", null, T0});
        db.execSQL("INSERT INTO answer_revisions(id, message_id, revision_no, reasoning_content,"
                        + " answer_content, model, thinking_enabled, prompt_policy_version,"
                        + " created_at) VALUES(?,?,?,?,?,?,?,?,?)",
                new Object[]{REVISION_1, AI_MESSAGE_ID, 1, "", "answer", "deepseek-flash", 1,
                        "tl-learning-answer/1.0.0", T0});
    }

    private static AiArtifactDao.Row artifact(String id, String kind, String scopeId, String payload,
                                              String basedOnRevisionId, String inputDigest,
                                              long updatedAt) {
        return new AiArtifactDao.Row(id, kind, scopeId, PURPOSE, POLICY_VERSION, payload,
                basedOnRevisionId, inputDigest, T0, updatedAt);
    }

    private static void assertForeignKeysIntact(SQLiteDatabase db) {
        try (Cursor cursor = db.rawQuery("PRAGMA foreign_key_check", null)) {
            assertFalse("PRAGMA foreign_key_check must report nothing", cursor.moveToFirst());
        }
        try (Cursor cursor = db.rawQuery("PRAGMA integrity_check", null)) {
            assertTrue(cursor.moveToFirst());
            assertEquals("PRAGMA integrity_check", "ok", cursor.getString(0));
        }
    }

    private static int count(SQLiteDatabase db, String sql) {
        try (Cursor cursor = db.rawQuery(sql, null)) {
            assertTrue("a count query must return a row", cursor.moveToFirst());
            return cursor.getInt(0);
        }
    }

    /** The TEXT columns of {@code table} whose content holds {@code needle}. */
    private static List<String> textColumnsHolding(SQLiteDatabase db, String table, String needle) {
        List<String> columns = new ArrayList<>();
        try (Cursor info = db.rawQuery("PRAGMA table_info(" + table + ")", null)) {
            while (info.moveToNext()) {
                if (info.getString(info.getColumnIndexOrThrow("type")).contains("TEXT")) {
                    columns.add(info.getString(info.getColumnIndexOrThrow("name")));
                }
            }
        }
        assertFalse("a scan with no columns would pass vacuously", columns.isEmpty());

        List<String> holding = new ArrayList<>();
        for (String column : columns) {
            try (Cursor row = db.rawQuery("SELECT " + column + " FROM " + table, null)) {
                while (row.moveToNext()) {
                    if (!row.isNull(0) && row.getString(0).contains(needle)) {
                        holding.add(column);
                        break;
                    }
                }
            }
        }
        return holding;
    }

    private static void assertCause(String label, String message, int expectedCode,
                                    String expectedColumns) {
        assertNotNull(label + ": the store must report a cause", message);
        int actualCode = ConstraintExtendedCode.of(message);
        assertTrue(label + ": expected code " + expectedCode + " but got code " + actualCode
                        + " in: " + message,
                actualCode == expectedCode);
        if (expectedColumns != null) {
            assertTrue(label + ": expected column list " + expectedColumns + " in: " + message,
                    message.contains(expectedColumns));
        }
    }
}