package com.treelearn.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteConstraintException;
import android.database.sqlite.SQLiteDatabase;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.treelearn.data.db.DatabaseTransactionRunner;
import com.treelearn.data.db.TreeLearnDatabaseHelper;
import com.treelearn.data.db.TreeLearnSchema;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * T-004 acceptance evidence, executed on real Android framework SQLite (API 36).
 *
 * <p>The expected schema below is derived from the confirmed authorities — the admission checklist
 * §2/§3, 技术方案 §5.1/§5.2/§6.1/§11, and the field surface of the {@code :core} entities those
 * sections describe — and deliberately does NOT read the production constants. Deriving it from the
 * constants would be comparing the implementation with itself: the first version of this file did
 * exactly that, and consequently could not notice that {@code answer_revisions} was missing the
 * model/thinking columns §5.1 requires and that {@code branches} stored an unread ordinal instead of
 * the §11 sequence pair.
 *
 * <p>Every rejection is asserted <em>by cause</em>, not merely by "a constraint exception was
 * thrown". Claiming a CHECK fired while the insert actually died on a foreign key is a false
 * witness: the assertion passes, the invariant is never exercised, and the suite looks greener than
 * the system is. For the same reason no assertion may pass only because a table is empty.
 *
 * <p>The cause is judged by the SQLite <em>extended result code</em> of the failure, measured on
 * this device and recorded in {@code artifacts/evidence/T-004-constraint-code-probe.txt}. Message
 * substrings are not sufficient, and this suite has the scar to prove it: it once asserted "mentions
 * CHECK and not NOT NULL", which the role CHECK satisfies in the wrong direction because that
 * constraint's own text contains {@code user_content IS NOT NULL}. A code belongs to the failure
 * class rather than to the wording of one constraint, and one failure carries exactly one code, so
 * asserting the right code also rules out every competing cause without a negated-substring clause.
 *
 * <p>Holds no real user content and no API key: every literal is synthetic (rules/sql.md).
 */
@RunWith(AndroidJUnit4.class)
public final class SqliteSchemaInstrumentedTest {

    /** UTC epoch millis, written explicitly by the application (rules/sql.md). */
    private static final long T0 = 1_756_000_000_000L;

    /** Synthetic model id; never a real credential. */
    private static final String MODEL = "deepseek-flash";

    /** Synthetic policy version; the body of that policy never appears here. */
    private static final String POLICY_VERSION = "tl-learning-answer/1.0.0";

    /** Confirmed permanent tables (checklist §2). */
    private static final Set<String> EXPECTED_TABLES = new LinkedHashSet<>(Arrays.asList(
            "topics", "branches", "messages", "answer_revisions",
            "generation_operations", "context_snapshots", "ai_artifacts"));

    /**
     * Confirmed columns per table, as {@code name:TYPE} or {@code name:TYPE:1} where {@code :1}
     * means NOT NULL.
     *
     * <p>Driving the column set from 技术方案 §5.1/§5.2/§11 is what keeps the store honest: §5.1
     * says a stable revision saves 模型 and 思考模式, §11 says unread is derived from
     * {@code latestStableSequence}/{@code lastSeenStableSequence} and that an independent unread
     * authority must not be stored, and §11 also spells out the four parts of the semantic reading
     * position. Reading positions are prefixed {@code reading_} so they cannot be confused with the
     * fork anchor ({@code anchor_*}) of §5.2.
     */
    private static final Map<String, String> EXPECTED_COLUMN_SPEC = expectedColumnSpec();

    /** Unique indexes the schema must provide (checklist §2 "关键索引与约束"). */
    private static final Set<String> EXPECTED_UNIQUE_INDEXES = new LinkedHashSet<>(Arrays.asList(
            "uq_branches_root", "uq_messages_branch_ordinal",
            "uq_answer_revisions_message_no", "uq_context_snapshots_branch",
            "uq_generation_operations_active"));

    private static final Set<String> EXPECTED_PARTIAL_INDEXES = new LinkedHashSet<>(Arrays.asList(
            "uq_branches_root", "uq_generation_operations_active"));

    private static final Set<String> EXPECTED_PLAIN_INDEXES = new LinkedHashSet<>(Arrays.asList(
            "idx_branches_topic_parent", "idx_branches_source_revision",
            "idx_branches_archived", "idx_ai_artifacts_kind_scope"));

    private static final Set<String> EXPECTED_TRIGGERS = new LinkedHashSet<>(Arrays.asList(
            "trg_answer_revisions_immutable", "trg_answer_revisions_no_direct_delete",
            "trg_context_snapshots_immutable", "trg_context_snapshots_no_direct_delete",
            "trg_generation_operations_terminal_immutable",
            "trg_messages_user_content_immutable"));

    /**
     * SQLite extended result codes, measured on this device rather than assumed
     * (see {@code artifacts/evidence/T-004-constraint-code-probe.txt}).
     *
     * <p>Every rejection below is asserted against one of these codes. The previous version of this
     * suite used message substrings instead, and that was already defeated once: it claimed "the
     * failure mentions CHECK and not NOT NULL", which a CHECK whose own expression text contains
     * {@code IS NOT NULL} satisfies in both directions. A code is a property of the failure class
     * and is independent of how the constraint happens to be worded, so it cannot be fooled the
     * same way. A single failure carries exactly one code, so asserting the right one also rules
     * out every other cause without a brittle negated-substring clause.
     */
    private static final String CODE_NOT_NULL = "(code 1299";
    private static final String CODE_CHECK = "(code 275";
    private static final String CODE_UNIQUE = "(code 2067";
    private static final String CODE_FOREIGN_KEY = "(code 787";
    private static final String CODE_TRIGGER = "(code 1811";

    /** Root branch id for a topic id, mirroring the fixture below. */
    private static String rootBranchOf(String topicId) {
        return "b-root-" + topicId;
    }

    private Context context;
    private TreeLearnDatabaseHelper helper;

    @Before
    public void setUp() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        context.deleteDatabase(TreeLearnDatabaseHelper.DATABASE_NAME);
        helper = new TreeLearnDatabaseHelper(context);
    }

    @After
    public void tearDown() {
        if (helper != null) {
            helper.close();
        }
        context.deleteDatabase(TreeLearnDatabaseHelper.DATABASE_NAME);
    }

    private static Map<String, String> expectedColumnSpec() {
        Map<String, String> spec = new LinkedHashMap<>();
        spec.put("topics", String.join(", ",
                "id:TEXT:1",
                "last_visited_branch_id:TEXT:0",
                "created_at:INTEGER:1",
                "updated_at:INTEGER:1"));
        spec.put("branches", String.join(", ",
                "id:TEXT:1",
                "topic_id:TEXT:1",
                "parent_id:TEXT:0",
                "title:TEXT:0",
                "created_at:INTEGER:1",
                "updated_at:INTEGER:1",
                "archived_at:INTEGER:0",
                "collapsed:INTEGER:1",
                "latest_stable_sequence:INTEGER:1",
                "last_seen_stable_sequence:INTEGER:1",
                "reading_anchor_message_id:TEXT:0",
                "reading_anchor_revision_id:TEXT:0",
                "reading_list_item_offset:INTEGER:0",
                "reading_viewport_offset:INTEGER:0",
                "source_message_id:TEXT:0",
                "source_revision_id:TEXT:0",
                "anchor_start_inclusive:INTEGER:0",
                "anchor_end_exclusive:INTEGER:0",
                "anchor_quote:TEXT:0",
                "anchor_hash:TEXT:0",
                "context_snapshot_id:TEXT:0"));
        spec.put("messages", String.join(", ",
                "id:TEXT:1",
                "branch_id:TEXT:1",
                "ordinal:INTEGER:1",
                "role:TEXT:1",
                "user_content:TEXT:0",
                "created_at:INTEGER:1",
                "active_revision_id:TEXT:0",
                "active_operation_id:TEXT:0"));
        spec.put("answer_revisions", String.join(", ",
                "id:TEXT:1",
                "message_id:TEXT:1",
                "revision_no:INTEGER:1",
                "reasoning_content:TEXT:1",
                "answer_content:TEXT:1",
                "model:TEXT:1",
                "thinking_enabled:INTEGER:1",
                "prompt_policy_version:TEXT:1",
                "created_at:INTEGER:1"));
        spec.put("generation_operations", String.join(", ",
                "id:TEXT:1",
                "branch_id:TEXT:1",
                "message_id:TEXT:1",
                "command_id:TEXT:1",
                "prompt_purpose:TEXT:1",
                "model:TEXT:1",
                "thinking_enabled:INTEGER:1",
                "prompt_policy_version:TEXT:1",
                "state:TEXT:1",
                "partial_reasoning:TEXT:1",
                "partial_answer:TEXT:1",
                "error_code:TEXT:0",
                "created_at:INTEGER:1",
                "updated_at:INTEGER:1"));
        spec.put("context_snapshots", String.join(", ",
                "id:TEXT:1",
                "branch_id:TEXT:1",
                "schema_version:INTEGER:1",
                "digest:TEXT:1",
                "payload:TEXT:1",
                "created_at:INTEGER:1"));
        spec.put("ai_artifacts", String.join(", ",
                "id:TEXT:1",
                "kind:TEXT:1",
                "scope_id:TEXT:1",
                "prompt_purpose:TEXT:1",
                "prompt_policy_version:TEXT:1",
                "payload:TEXT:1",
                "based_on_revision_id:TEXT:0",
                "input_digest:TEXT:1",
                "created_at:INTEGER:1",
                "updated_at:INTEGER:1"));
        return spec;
    }

    private static Set<String> expectedColumnNames(String table) {
        Set<String> names = new LinkedHashSet<>();
        for (String entry : EXPECTED_COLUMN_SPEC.get(table).split(",")) {
            names.add(entry.trim().split(":")[0]);
        }
        return names;
    }

    // ------------------------------------------------------------------
    // 1. New install runs the production onCreate.
    // ------------------------------------------------------------------

    @Test
    public void newInstallCreatesExactlySevenTablesThroughProductionOnCreate() {
        SQLiteDatabase db = helper.getWritableDatabase();

        assertEquals("schema version after a fresh install", 1, rawInt(db, "PRAGMA user_version"));
        assertEquals("permanent table set", EXPECTED_TABLES, actualTables(db));
        assertEquals("DDL is bound to the current schema version",
                1, TreeLearnSchema.DATABASE_VERSION);
        assertEquals("no permanent table may exist outside the confirmed seven",
                EXPECTED_TABLES.size(), TableCensus.count(db));
    }

    // ------------------------------------------------------------------
    // 2. Columns match the confirmed admission record, including type and NOT NULL.
    // ------------------------------------------------------------------

    @Test
    public void everyTableHasExactlyTheConfirmedColumnsWithConfirmedTypes() {
        SQLiteDatabase db = helper.getWritableDatabase();
        assertEquals("column census covers every confirmed table",
                EXPECTED_TABLES, EXPECTED_COLUMN_SPEC.keySet());

        for (String table : EXPECTED_TABLES) {
            assertEquals("column set of " + table,
                    expectedColumnNames(table), actualColumns(db, table));
            for (String entry : EXPECTED_COLUMN_SPEC.get(table).split(",")) {
                String[] parts = entry.trim().split(":");
                String column = parts[0];
                String expectedType = parts[1];
                int expectedNotNull = Integer.parseInt(parts[2]);
                assertEquals(table + "." + column + " declared type",
                        expectedType, columnType(db, table, column));
                assertEquals(table + "." + column + " NOT NULL",
                        expectedNotNull, columnNotNull(db, table, column));
            }
        }
    }

    @Test
    public void timePointColumnsAreIntegersAndNoSqliteClockIsUsed() {
        SQLiteDatabase db = helper.getWritableDatabase();
        for (String table : EXPECTED_TABLES) {
            for (String column : actualColumns(db, table)) {
                if (column.endsWith("_at")) {
                    assertEquals(column + " must store UTC epoch millis as INTEGER",
                            "INTEGER", columnType(db, table, column));
                }
            }
        }
        for (String table : EXPECTED_TABLES) {
            String ddl = tableDdl(db, table).toUpperCase();
            assertFalse(table + " must not use the SQLite clock", ddl.contains("CURRENT_TIMESTAMP"));
            assertFalse(table + " must not use datetime()", ddl.contains("DATETIME("));
            assertFalse(table + " must not use date('now')", ddl.contains("DATE('NOW')"));
            assertFalse(table + " must not use julianday()", ddl.contains("JULIANDAY("));
            assertFalse(table + " must not use unixepoch()", ddl.contains("UNIXEPOCH("));
            assertFalse(table + " must not use strftime()", ddl.contains("STRFTIME("));
            assertFalse(table + " must not use localtime", ddl.contains("LOCALTIME"));
        }
    }

    @Test
    public void noColumnIsDedicatedToTheSystemPromptBody() {
        SQLiteDatabase db = helper.getWritableDatabase();
        Set<String> promptRelated = new TreeSet<>();
        for (String table : EXPECTED_TABLES) {
            for (String column : actualColumns(db, table)) {
                String lower = column.toLowerCase();
                if (lower.contains("system")) {
                    fail("column " + table + "." + column
                            + " can hold the system prompt body, which must never be persisted");
                }
                if (lower.contains("prompt")) {
                    promptRelated.add(lower);
                }
            }
        }
        assertEquals("only the stable purpose and policy version may be persisted",
                new TreeSet<>(Arrays.asList("prompt_purpose", "prompt_policy_version")),
                promptRelated);
    }

    @Test
    public void unreadHasASingleRepresentationInTheSequencePair() {
        SQLiteDatabase db = helper.getWritableDatabase();
        Set<String> branchColumns = actualColumns(db, "branches");
        for (String column : branchColumns) {
            assertFalse("§11 forbids an independent unread authority; found " + column,
                    column.contains("unread") || column.equals("unread_count"));
        }
        assertTrue("the §11 sequence pair must exist",
                branchColumns.containsAll(Arrays.asList("latest_stable_sequence",
                        "last_seen_stable_sequence")));

        seedTopicWithRoot(db, "t-unread");
        assertEquals("a new branch starts with a seen, empty sequence pair", 0,
                rawInt(db, "SELECT latest_stable_sequence FROM branches WHERE id='"
                        + rootBranchOf("t-unread") + "'"));

        String orderingFailure = constraintFailure("last seen beyond latest", () -> db.execSQL(
                "UPDATE branches SET last_seen_stable_sequence=5 WHERE id=?",
                new Object[]{rootBranchOf("t-unread")})).getMessage();
        assertCause("seen must never run ahead of latest", orderingFailure, CODE_CHECK,
                "last_seen_stable_sequence");

        String negativeFailure = constraintFailure("negative sequence", () -> db.execSQL(
                "UPDATE branches SET latest_stable_sequence=-1 WHERE id=?",
                new Object[]{rootBranchOf("t-unread")})).getMessage();
        assertCause("sequences are counts, not offsets", negativeFailure, CODE_CHECK,
                "latest_stable_sequence");

        db.execSQL("UPDATE branches SET latest_stable_sequence=3, last_seen_stable_sequence=3"
                + " WHERE id=?", new Object[]{rootBranchOf("t-unread")});
        assertEquals("a legal advance is accepted", 3,
                rawInt(db, "SELECT latest_stable_sequence FROM branches WHERE id='"
                        + rootBranchOf("t-unread") + "'"));
    }

    // ------------------------------------------------------------------
    // 3. PRAGMA state on every open.
    // ------------------------------------------------------------------

    @Test
    public void foreignKeysAreEnabledAndIntegrityIsCleanOnEveryOpen() {
        assertPragmasAreClean(helper.getWritableDatabase());

        helper.close();
        helper = new TreeLearnDatabaseHelper(context);
        assertPragmasAreClean(helper.getWritableDatabase());
    }

    private static void assertPragmasAreClean(SQLiteDatabase db) {
        assertEquals("PRAGMA foreign_keys must be ON for every connection",
                1, rawInt(db, "PRAGMA foreign_keys"));
        assertEquals("PRAGMA foreign_key_check must return no rows",
                0, rawRows(db, "PRAGMA foreign_key_check"));
        assertEquals("PRAGMA integrity_check must be ok",
                "ok", rawString(db, "PRAGMA integrity_check"));
    }

    // ------------------------------------------------------------------
    // 4. Indexes, triggers and constraints exist and actually bite.
    // ------------------------------------------------------------------

    @Test
    public void criticalIndexesExistAndPartialOnesArePartial() {
        SQLiteDatabase db = helper.getWritableDatabase();
        for (String index : EXPECTED_UNIQUE_INDEXES) {
            String sql = schemaObjectDdl(db, "index", index);
            assertNotNull("missing unique index " + index, sql);
            assertTrue(index + " must be UNIQUE", sql.toUpperCase().contains("UNIQUE"));
            if (EXPECTED_PARTIAL_INDEXES.contains(index)) {
                assertTrue(index + " must be a partial index (WHERE clause)",
                        sql.toUpperCase().contains("WHERE"));
            }
        }
        for (String index : EXPECTED_PLAIN_INDEXES) {
            assertNotNull("missing index " + index, schemaObjectDdl(db, "index", index));
        }
        Set<String> declared = new TreeSet<>(EXPECTED_UNIQUE_INDEXES);
        declared.addAll(EXPECTED_PLAIN_INDEXES);
        assertEquals("the declared index set", declared, actualSchemaObjects(db, "index"));

        assertEquals("the declared trigger set", new TreeSet<>(EXPECTED_TRIGGERS),
                actualSchemaObjects(db, "trigger"));
    }

    @Test
    public void messageOrdinalRevisionNumberAndSnapshotAreUniquePerOwner() {
        SQLiteDatabase db = helper.getWritableDatabase();
        seedTopicWithRoot(db, "t-dupe");
        seedMessage(db, "m-dupe", rootBranchOf("t-dupe"), 0, "AI");
        seedRevision(db, "r-dupe", "m-dupe", 1);

        String ordinalFailure = constraintFailure("duplicate ordinal", () -> db.execSQL(
                "INSERT INTO messages(id, branch_id, ordinal, role, user_content, created_at)"
                        + " VALUES(?,?,?,?,?,?)",
                new Object[]{"m-dupe-2", rootBranchOf("t-dupe"), 0, "USER", "x", T0})).getMessage();
        assertCause("(branch_id, ordinal) must be rejected for uniqueness", ordinalFailure,
                CODE_UNIQUE, "messages.branch_id, messages.ordinal");

        String revisionFailure = constraintFailure("duplicate revision number", () -> db.execSQL(
                "INSERT INTO answer_revisions(id, message_id, revision_no, reasoning_content,"
                        + " answer_content, model, thinking_enabled, prompt_policy_version,"
                        + " created_at) VALUES(?,?,?,?,?,?,?,?,?)",
                new Object[]{"r-dupe-2", "m-dupe", 1, "", "a", MODEL, 1, POLICY_VERSION,
                        T0})).getMessage();
        assertCause("(message_id, revision_no) must be rejected for uniqueness", revisionFailure,
                CODE_UNIQUE, "answer_revisions.message_id, answer_revisions.revision_no");

        seedSnapshot(db, "s-dupe", rootBranchOf("t-dupe"));
        String snapshotFailure = constraintFailure("duplicate snapshot", () -> db.execSQL(
                "INSERT INTO context_snapshots(id, branch_id, schema_version, digest, payload,"
                        + " created_at) VALUES(?,?,?,?,?,?)",
                new Object[]{"s-dupe-2", rootBranchOf("t-dupe"), 1, "d", "{}", T0})).getMessage();
        assertCause("one birth snapshot per branch, rejected for uniqueness", snapshotFailure,
                CODE_UNIQUE, "context_snapshots.branch_id");
    }

    @Test
    public void onlyOneRootBranchPerTopic() {
        SQLiteDatabase db = helper.getWritableDatabase();
        seedTopicWithRoot(db, "t-root");

        String rootFailure = constraintFailure("second root branch", () -> db.execSQL(
                "INSERT INTO branches(id, topic_id, parent_id, title, created_at, updated_at,"
                        + " collapsed) VALUES(?,?,?,?,?,?,?)",
                new Object[]{"b-root-2", "t-root", null, "", T0, T0, 0})).getMessage();
        assertCause("a topic must have exactly one root branch", rootFailure, CODE_UNIQUE,
                "branches.topic_id");

        // Sibling child branches under the same root must stay insertable. Without this the
        // assertion above would also be satisfied by a plain UNIQUE(topic_id) index, which would
        // have broken every fork in the product.
        seedMessage(db, "m-src", rootBranchOf("t-root"), 1, "AI");
        seedRevision(db, "r-src", "m-src", 1);
        insertChildBranch(db, "b-child", "t-root", rootBranchOf("t-root"), "m-src", "r-src");
        insertChildBranch(db, "b-child-2", "t-root", rootBranchOf("t-root"), "m-src", "r-src");
        assertEquals("two child branches coexist under one root", 3, rowCount(db, "branches"));
    }

    @Test
    public void generationOperationActiveLimitIsAPartialIndex() {
        SQLiteDatabase db = helper.getWritableDatabase();
        seedTopicWithRoot(db, "t-op");
        seedMessage(db, "m-op", rootBranchOf("t-op"), 0, "AI");

        insertOperation(db, "o-active", rootBranchOf("t-op"), "m-op", "STREAMING_ANSWER");
        String activeFailure = constraintFailure("second active operation", () ->
                insertOperation(db, "o-active-2", rootBranchOf("t-op"), "m-op", "VALIDATING"))
                .getMessage();
        assertCause("only one active operation per branch", activeFailure, CODE_UNIQUE,
                "generation_operations.branch_id");

        // Terminal operations next to an active one must be accepted. A plain UNIQUE(branch_id)
        // index would reject them, so this is what proves the index is partial.
        insertOperation(db, "o-failed", rootBranchOf("t-op"), "m-op", "FAILED");
        insertOperation(db, "o-interrupted", rootBranchOf("t-op"), "m-op", "INTERRUPTED");
        assertEquals("three operations coexist", 3, rowCount(db, "generation_operations"));
    }

    @Test
    public void requiredIdentityColumnsRejectNull() {
        SQLiteDatabase db = helper.getWritableDatabase();
        seedTopicWithRoot(db, "t-null");
        seedMessage(db, "m-null", rootBranchOf("t-null"), 0, "AI");

        String revisionFailure = constraintFailure("null policy version on a revision",
                () -> db.execSQL("INSERT INTO answer_revisions(id, message_id, revision_no,"
                                + " reasoning_content, answer_content, model, thinking_enabled,"
                                + " prompt_policy_version, created_at)"
                                + " VALUES(?,?,?,?,?,?,?,?,?)",
                        new Object[]{"r-null", "m-null", 1, "", "a", MODEL, 1, null,
                                T0})).getMessage();
        assertCause("the revision must fail on its own policy version column", revisionFailure,
                CODE_NOT_NULL, "answer_revisions.prompt_policy_version");

        String modelFailure = constraintFailure("null model on a revision",
                () -> db.execSQL("INSERT INTO answer_revisions(id, message_id, revision_no,"
                                + " reasoning_content, answer_content, model, thinking_enabled,"
                                + " prompt_policy_version, created_at)"
                                + " VALUES(?,?,?,?,?,?,?,?,?)",
                        new Object[]{"r-null-model", "m-null", 2, "", "a", null, 1,
                                POLICY_VERSION, T0})).getMessage();
        assertCause("§5.1 says a revision records the model that produced it", modelFailure,
                CODE_NOT_NULL, "answer_revisions.model");

        String operationFailure = constraintFailure("null policy version on an operation",
                () -> db.execSQL("INSERT INTO generation_operations(id, branch_id, message_id,"
                                + " command_id, prompt_purpose, model, thinking_enabled,"
                                + " prompt_policy_version, state, partial_reasoning, partial_answer,"
                                + " created_at, updated_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)",
                        new Object[]{"o-null", rootBranchOf("t-null"), "m-null", "c",
                                "tl-learning-answer", MODEL, 1, null, "STAGED", "", "", T0,
                                T0})).getMessage();
        assertCause("the operation must fail on its own policy version column", operationFailure,
                CODE_NOT_NULL, "generation_operations.prompt_policy_version");

        String artifactFailure = constraintFailure("null purpose on an artifact",
                () -> db.execSQL("INSERT INTO ai_artifacts(id, kind, scope_id, prompt_purpose,"
                                + " prompt_policy_version, payload, input_digest, created_at,"
                                + " updated_at) VALUES(?,?,?,?,?,?,?,?,?)",
                        new Object[]{"a-null", "ROOT_TITLE", "t-null", null, POLICY_VERSION, "{}",
                                "d", T0, T0})).getMessage();
        assertCause("the artifact must fail on its own purpose column", artifactFailure,
                CODE_NOT_NULL, "ai_artifacts.prompt_purpose");
    }

    @Test
    public void messageContentIsConstrainedByRole() {
        SQLiteDatabase db = helper.getWritableDatabase();
        seedTopicWithRoot(db, "t-role");

        String userFailure = constraintFailure("a user message without content", () -> db.execSQL(
                "INSERT INTO messages(id, branch_id, ordinal, role, user_content, created_at)"
                        + " VALUES(?,?,?,?,?,?)",
                new Object[]{"m-empty-user", rootBranchOf("t-role"), 0, "USER", null,
                        T0})).getMessage();
        // The code is the load-bearing part here: this CHECK's own text contains "IS NOT NULL", so
        // the old "mentions CHECK and not NOT NULL" test could not tell the two classes apart.
        assertCause("a USER row is content and must carry it", userFailure, CODE_CHECK,
                "role = 'AI'");

        String aiFailure = constraintFailure("an AI message carrying content", () -> db.execSQL(
                "INSERT INTO messages(id, branch_id, ordinal, role, user_content, created_at)"
                        + " VALUES(?,?,?,?,?,?)",
                new Object[]{"m-ai-content", rootBranchOf("t-role"), 1, "AI", "text",
                        T0})).getMessage();
        assertCause("an AI row is a logical position and must not carry content", aiFailure,
                CODE_CHECK, "role = 'USER'");
    }

    // ------------------------------------------------------------------
    // 5. Foreign keys and cascades.
    // ------------------------------------------------------------------

    @Test
    public void foreignKeysAreEnforcedNotMerelyDeclared() {
        SQLiteDatabase db = helper.getWritableDatabase();

        String fkFailure = constraintFailure("orphan message", () -> db.execSQL(
                "INSERT INTO messages(id, branch_id, ordinal, role, user_content, created_at)"
                        + " VALUES(?,?,?,?,?,?)",
                new Object[]{"m-orphan", "b-missing", 0, "USER", "x", T0})).getMessage();
        assertCause("an orphan message must be rejected by the foreign key", fkFailure,
                CODE_FOREIGN_KEY, null);

        // The branch must exist first, otherwise the insert below dies on the foreign key and the
        // test never reaches the role CHECK it claims to verify.
        seedTopicWithRoot(db, "t-fk");
        String roleFailure = constraintFailure("unknown role", () -> db.execSQL(
                "INSERT INTO messages(id, branch_id, ordinal, role, user_content, created_at)"
                        + " VALUES(?,?,?,?,?,?)",
                new Object[]{"m-badrole", rootBranchOf("t-fk"), 0, "ROBOT", "x", T0})).getMessage();
        // The branch exists by now, so the only constraint that can fire is the role CHECK. The
        // code assertion is what makes that claim checkable instead of merely intended.
        assertCause("the role CHECK must be what rejects an unknown role", roleFailure, CODE_CHECK,
                "role");

        // A non-root branch without its source version must be refused (技术方案 §5.2).
        String sourceFailure = constraintFailure("fork without a source revision", () -> db.execSQL(
                "INSERT INTO branches(id, topic_id, parent_id, title, created_at, updated_at,"
                        + " collapsed, anchor_hash) VALUES(?,?,?,?,?,?,?,?)",
                new Object[]{"b-no-source", "t-fk", rootBranchOf("t-fk"), "", T0, T0, 0,
                        "hash"})).getMessage();
        // Both branch provenance CHECKs are written on parent_id, so naming that column alone
        // cannot tell them apart; the mutually exclusive continuation does.
        assertCause("a non-root branch must carry its source version", sourceFailure, CODE_CHECK,
                "source_message_id IS NOT NULL");

        // A root branch must not carry fork provenance. This uses a topic with no root branch yet,
        // so the only constraint that can fire is the CHECK itself — pointing it at t-fk would let
        // the one-root unique index fire first and the assertion would blame the wrong constraint.
        insertTopic(db, "t-dirty");
        String rootProvenanceFailure = constraintFailure("root branch with provenance",
                () -> db.execSQL("INSERT INTO branches(id, topic_id, parent_id, title, created_at,"
                                + " updated_at, collapsed, anchor_hash) VALUES(?,?,?,?,?,?,?,?)",
                        new Object[]{"b-root-dirty", "t-dirty", null, "", T0, T0, 0,
                                "hash"})).getMessage();
        assertCause("a root branch must not carry provenance", rootProvenanceFailure, CODE_CHECK,
                "source_message_id IS NULL");
    }

    @Test
    public void deletingATopicCascadesTheWholeTreeWithoutDanglingRows() {
        SQLiteDatabase db = helper.getWritableDatabase();
        seedTopicWithRoot(db, "t-cascade");
        seedMessage(db, "m-user", rootBranchOf("t-cascade"), 0, "USER");
        seedMessage(db, "m-ai", rootBranchOf("t-cascade"), 1, "AI");
        seedRevision(db, "r-ai", "m-ai", 1);
        insertOperation(db, "o-ai", rootBranchOf("t-cascade"), "m-ai", "STABLE");
        insertChildBranch(db, "b-child", "t-cascade", rootBranchOf("t-cascade"), "m-ai", "r-ai");
        // A birth snapshot belongs to the branch it was created for, so the child points at it.
        seedSnapshot(db, "s-birth", "b-child");
        db.execSQL("UPDATE branches SET context_snapshot_id=? WHERE id=?",
                new Object[]{"s-birth", "b-child"});
        seedMessage(db, "m-child", "b-child", 0, "USER");
        seedArtifact(db, "a-scope", "t-cascade", "r-ai");

        db.execSQL("DELETE FROM topics WHERE id=?", new Object[]{"t-cascade"});

        for (String table : EXPECTED_TABLES) {
            if ("ai_artifacts".equals(table)) {
                continue;
            }
            assertEquals(table + " must be empty after the topic is deleted",
                    0, rowCount(db, table));
        }

        // ai_artifacts is deliberately NOT cascaded: scope_id is polymorphic, so no foreign key can
        // reach it. Asserting "the table is empty" here would be a false witness that passes on an
        // empty table; the honest claim is the boundary itself, which the use case owns.
        assertEquals("an artifact is not cascaded by the topic delete; its owner must clean it up",
                1, rowCount(db, "ai_artifacts"));
        assertNull("the artifact's revision reference is cleared by the foreign key",
                stringWhere(db, "ai_artifacts", "based_on_revision_id", "id='a-scope'"));

        assertEquals("no dangling references after the cascade",
                0, rawRows(db, "PRAGMA foreign_key_check"));
        assertEquals("integrity after the cascade", "ok", rawString(db, "PRAGMA integrity_check"));
    }

    @Test
    public void deletingABranchSubtreeCascadesItsDescendantsOnly() {
        SQLiteDatabase db = helper.getWritableDatabase();
        seedTopicWithRoot(db, "t-subtree");
        seedMessage(db, "m-source", rootBranchOf("t-subtree"), 0, "AI");
        seedRevision(db, "r-source", "m-source", 1);
        insertChildBranch(db, "b-child-sub", "t-subtree", rootBranchOf("t-subtree"), "m-source",
                "r-source");
        insertChildBranch(db, "b-grand-sub", "t-subtree", "b-child-sub", "m-source", "r-source");
        seedMessage(db, "m-grand", "b-grand-sub", 0, "USER");

        db.execSQL("DELETE FROM branches WHERE id=?", new Object[]{"b-child-sub"});

        assertEquals("only the root branch survives", 1, rowCount(db, "branches"));
        assertEquals("the root branch is untouched", 1,
                rowCountWhere(db, "branches", "id='" + rootBranchOf("t-subtree") + "'"));
        assertEquals("the grandchild's messages are gone", 0,
                rowCountWhere(db, "messages", "id='m-grand'"));
        assertEquals("the topic itself survives a subtree delete", 1, rowCount(db, "topics"));
        assertEquals("no dangling references after the subtree cascade",
                0, rawRows(db, "PRAGMA foreign_key_check"));
    }

    // ------------------------------------------------------------------
    // 6. Transaction runner.
    // ------------------------------------------------------------------

    @Test
    public void transactionRunnerCommitsOnSuccessAndRollsBackOnFailure() {
        SQLiteDatabase db = helper.getWritableDatabase();
        DatabaseTransactionRunner runner = new DatabaseTransactionRunner();

        runner.run(db, workDatabase -> {
            insertTopic(workDatabase, "t-committed");
            return null;
        });
        assertEquals("committed work is visible", 1, rowCount(db, "topics"));

        IllegalStateException propagated = expectedFailure(IllegalStateException.class,
                "injected failure", () -> runner.run(db, workDatabase -> {
                    insertTopic(workDatabase, "t-rolled-back");
                    throw new IllegalStateException("injected failure");
                }));
        assertTrue(propagated.getMessage().contains("injected failure"));
        assertEquals("rolled back work is invisible", 1, rowCount(db, "topics"));
        assertEquals("the wrapped row is gone", 0,
                rowCountWhere(db, "topics", "id='t-rolled-back'"));
    }

    @Test
    public void transactionRunnerRejectsNestedOpenAndStolenTransactions() {
        SQLiteDatabase db = helper.getWritableDatabase();
        DatabaseTransactionRunner runner = new DatabaseTransactionRunner();

        runner.run(db, workDatabase -> {
            expectedFailure(IllegalStateException.class, "nested transaction",
                    () -> runner.run(workDatabase, inner -> null));
            return null;
        });
        assertFalse("the runner must leave no transaction open", db.inTransaction());

        // Android's beginTransaction() silently joins an enclosing transaction, so a work body that
        // commits on its own would otherwise bypass the runner's single boundary.
        IllegalStateException stolen = expectedFailure(IllegalStateException.class,
                "work body ending its own transaction", () -> runner.run(db, workDatabase -> {
                    workDatabase.setTransactionSuccessful();
                    workDatabase.endTransaction();
                    return null;
                }));
        assertTrue("the stolen transaction must be reported: " + stolen.getMessage(),
                stolen.getMessage().contains("ended the transaction it was given"));
        assertFalse("no transaction is left open", db.inTransaction());
    }

    @Test
    public void transactionRunnerRejectsMissingArguments() {
        SQLiteDatabase db = helper.getWritableDatabase();
        DatabaseTransactionRunner runner = new DatabaseTransactionRunner();
        expectedFailure(IllegalArgumentException.class, "null database",
                () -> runner.run(null, inner -> null));
        expectedFailure(IllegalArgumentException.class, "null work", () -> runner.run(db, null));
    }

    // ------------------------------------------------------------------
    // 7. Immutability is enforced by the store, not only by convention.
    // ------------------------------------------------------------------

    @Test
    public void immutableRowsRejectUpdates() {
        SQLiteDatabase db = helper.getWritableDatabase();
        seedTopicWithRoot(db, "t-immutable");
        seedMessage(db, "m-user-imm", rootBranchOf("t-immutable"), 0, "USER");
        seedMessage(db, "m-ai-imm", rootBranchOf("t-immutable"), 1, "AI");
        seedRevision(db, "r-imm", "m-ai-imm", 1);
        seedSnapshot(db, "s-imm", rootBranchOf("t-immutable"));
        insertOperation(db, "o-imm", rootBranchOf("t-immutable"), "m-ai-imm", "STABLE");

        String revisionFailure = constraintFailure("rewriting a stable revision", () ->
                db.execSQL("UPDATE answer_revisions SET answer_content='rewritten' WHERE id=?",
                        new Object[]{"r-imm"})).getMessage();
        assertCause("a stable answer revision is immutable", revisionFailure, CODE_TRIGGER,
                "answer_revisions rows are immutable");

        String snapshotFailure = constraintFailure("rewriting a birth snapshot", () ->
                db.execSQL("UPDATE context_snapshots SET digest='rewritten' WHERE id=?",
                        new Object[]{"s-imm"})).getMessage();
        assertCause("a birth snapshot is immutable", snapshotFailure, CODE_TRIGGER,
                "context_snapshots rows are immutable");

        String terminalFailure = constraintFailure("rewriting a terminal operation", () ->
                db.execSQL("UPDATE generation_operations SET partial_answer='late' WHERE id=?",
                        new Object[]{"o-imm"})).getMessage();
        assertCause("a terminal operation must not be rewritten", terminalFailure, CODE_TRIGGER,
                "a terminal generation operation is final");

        String contentFailure = constraintFailure("editing a user message", () ->
                db.execSQL("UPDATE messages SET user_content='edited' WHERE id=?",
                        new Object[]{"m-user-imm"})).getMessage();
        assertCause("user message content is immutable once created", contentFailure, CODE_TRIGGER,
                "user message content is immutable");

        // Negative control: the store must not be so strict that normal progress is impossible.
        insertOperation(db, "o-live", rootBranchOf("t-immutable"), "m-ai-imm",
                "STREAMING_REASONING");
        db.execSQL("UPDATE generation_operations SET state='INTERRUPTED' WHERE id=?",
                new Object[]{"o-live"});
        db.execSQL("UPDATE messages SET active_revision_id=? WHERE id=?",
                new Object[]{"r-imm", "m-ai-imm"});
        assertEquals("a non-terminal operation still advances", "INTERRUPTED",
                stringWhere(db, "generation_operations", "state", "id='o-live'"));
        assertEquals("an AI message still repoints at its active revision", "r-imm",
                stringWhere(db, "messages", "active_revision_id", "id='m-ai-imm'"));
    }

    @Test
    public void immutableRowsRefuseDirectDeleteButAllowTheOwnerCascade() {
        SQLiteDatabase db = helper.getWritableDatabase();
        seedTopicWithRoot(db, "t-del");
        seedMessage(db, "m-ai-del", rootBranchOf("t-del"), 0, "AI");
        seedRevision(db, "r-del", "m-ai-del", 1);
        seedSnapshot(db, "s-del", rootBranchOf("t-del"));

        // The fork FK is ON DELETE CASCADE, so without a guard a stray delete of one revision
        // would silently take the user's fork subtree with it.
        String revisionDelete = constraintFailure("direct revision delete",
                () -> db.execSQL("DELETE FROM answer_revisions WHERE id=?", new Object[]{"r-del"}))
                .getMessage();
        assertCause("a stable revision must not be deletable on its own", revisionDelete,
                CODE_TRIGGER, "never on its own");
        assertEquals("the revision survived", 1,
                rowCountWhere(db, "answer_revisions", "id='r-del'"));

        String snapshotDelete = constraintFailure("direct snapshot delete",
                () -> db.execSQL("DELETE FROM context_snapshots WHERE id=?",
                        new Object[]{"s-del"})).getMessage();
        assertCause("a birth snapshot must not be deletable on its own", snapshotDelete,
                CODE_TRIGGER, "never on its own");
        assertEquals("the snapshot survived", 1,
                rowCountWhere(db, "context_snapshots", "id='s-del'"));

        // The legitimate path — the owner row goes first, its children follow — must still work,
        // otherwise the guard would simply have broken topic deletion.
        db.execSQL("DELETE FROM topics WHERE id=?", new Object[]{"t-del"});
        assertEquals("the revision left with its message", 0, rowCount(db, "answer_revisions"));
        assertEquals("the snapshot left with its branch", 0, rowCount(db, "context_snapshots"));
        assertEquals("no dangling references", 0, rawRows(db, "PRAGMA foreign_key_check"));
    }

    // ------------------------------------------------------------------
    // 8. Migration mechanism: delegation is proven, absence fails closed.
    // ------------------------------------------------------------------

    @Test
    public void onUpgradeDelegatesAndForwardMigrationsFailClosed() {
        SQLiteDatabase db = helper.getWritableDatabase();

        assertEquals("V1 has exactly DATABASE_VERSION-1 published predecessors",
                TreeLearnSchema.DATABASE_VERSION - 1, TreeLearnSchema.FORWARD_MIGRATIONS.size());

        // 1. The production onUpgrade really delegates: a same-version upgrade is a no-op.
        helper.onUpgrade(db, TreeLearnSchema.DATABASE_VERSION, TreeLearnSchema.DATABASE_VERSION);
        assertEquals("a same-version upgrade must leave the schema and its version alone", 1,
                rawInt(db, "PRAGMA user_version"));

        // 2. Any single missing step must fail closed instead of stamping a newer user_version on a
        // half-migrated database (rules/sql.md). Without this check the task's own "upgrade is N/A
        // for V1" note would be backed only by a test that executes zero assertions.
        IllegalStateException missingStep = expectedFailure(IllegalStateException.class,
                "a step with no registered migration", () ->
                        TreeLearnSchema.applyForwardMigrations(db, 1, 2));
        assertTrue("a missing migration must fail closed: " + missingStep.getMessage(),
                missingStep.getMessage().contains("no forward migration is registered from schema"
                        + " version 1"));

        IllegalStateException downgrade = expectedFailure(IllegalStateException.class,
                "downgrade", () -> TreeLearnSchema.applyForwardMigrations(db, 3, 2));
        assertTrue("a downgrade must be refused: " + downgrade.getMessage(),
                downgrade.getMessage().contains("downgrade is not supported"));

        // 3. The registry, when non-empty, must form a gap-free +1 chain. The loop counter is
        // asserted against the list size so this can never silently iterate zero times.
        int visited = 0;
        int previousTo = 0;
        for (TreeLearnSchema.Migration migration : TreeLearnSchema.FORWARD_MIGRATIONS) {
            assertEquals("a migration may only step forward by exactly one version",
                    previousTo, migration.fromVersion());
            assertEquals("no gap between migration versions",
                    migration.fromVersion() + 1, migration.toVersion());
            previousTo = migration.toVersion();
            visited++;
        }
        assertEquals("every registered migration was inspected, none silently skipped",
                TreeLearnSchema.FORWARD_MIGRATIONS.size(), visited);
        if (visited > 0) {
            assertEquals("the chain must reach the current schema version",
                    TreeLearnSchema.DATABASE_VERSION, previousTo + 1);
        }
    }

    // ------------------------------------------------------------------
    // 9. Close / reopen and the pre-install empty database file.
    // ------------------------------------------------------------------

    @Test
    public void closeAndReopenKeepsSchemaVersionAndRows() {
        SQLiteDatabase db = helper.getWritableDatabase();
        seedTopicWithRoot(db, "t-reopen");
        helper.close();

        helper = new TreeLearnDatabaseHelper(context);
        SQLiteDatabase reopened = helper.getWritableDatabase();
        assertEquals("reopen must not rebuild or migrate", 1, rawInt(reopened, "PRAGMA user_version"));
        assertEquals("rows survive the reopen", 1, rowCount(reopened, "topics"));
        assertEquals("tree survives the reopen", 1, rowCount(reopened, "branches"));
        assertPragmasAreClean(reopened);
    }

    @Test
    public void existingEmptyFileIsBuiltByOnCreate() {
        File databaseFile = context.getDatabasePath(TreeLearnDatabaseHelper.DATABASE_NAME);
        File parent = databaseFile.getParentFile();
        assertNotNull(parent);
        assertTrue("could not prepare the databases directory", parent.exists() || parent.mkdirs());
        assertTrue("stale database file", !databaseFile.exists() || databaseFile.delete());

        SQLiteDatabase raw = SQLiteDatabase.openOrCreateDatabase(databaseFile, null);
        assertEquals("an untouched database file reports version 0",
                0, rawInt(raw, "PRAGMA user_version"));
        assertEquals("an untouched database file has no permanent tables",
                0, actualTables(raw).size());
        raw.close();

        SQLiteDatabase opened = helper.getWritableDatabase();
        assertEquals("the production helper must build the schema on the existing file",
                EXPECTED_TABLES, actualTables(opened));
        assertEquals("and stamp the schema version", 1, rawInt(opened, "PRAGMA user_version"));
        assertPragmasAreClean(opened);
    }

    // ------------------------------------------------------------------
    // Fixture helpers.
    // ------------------------------------------------------------------

    private void seedTopicWithRoot(SQLiteDatabase db, String topicId) {
        insertTopic(db, topicId);
        db.execSQL("INSERT INTO branches(id, topic_id, parent_id, title, created_at, updated_at,"
                        + " collapsed) VALUES(?,?,?,?,?,?,?)",
                new Object[]{rootBranchOf(topicId), topicId, null, "", T0, T0, 0});
        db.execSQL("UPDATE topics SET last_visited_branch_id=? WHERE id=?",
                new Object[]{rootBranchOf(topicId), topicId});
    }

    private void insertTopic(SQLiteDatabase db, String id) {
        db.execSQL("INSERT INTO topics(id, last_visited_branch_id, created_at, updated_at)"
                + " VALUES(?,?,?,?)", new Object[]{id, null, T0, T0});
    }

    private void insertChildBranch(SQLiteDatabase db, String id, String topicId, String parentId,
                                   String sourceMessageId, String sourceRevisionId) {
        db.execSQL("INSERT INTO branches(id, topic_id, parent_id, title, created_at, updated_at,"
                        + " collapsed, source_message_id, source_revision_id, anchor_quote,"
                        + " anchor_hash) VALUES(?,?,?,?,?,?,?,?,?,?,?)",
                new Object[]{id, topicId, parentId, "", T0, T0, 0, sourceMessageId,
                        sourceRevisionId, "quote", "hash"});
    }

    private void seedMessage(SQLiteDatabase db, String id, String branchId, int ordinal,
                             String role) {
        // §5.1: only a USER row carries content, so the AI fixture must leave it NULL.
        Object content = "USER".equals(role) ? "synthetic " + role : null;
        db.execSQL("INSERT INTO messages(id, branch_id, ordinal, role, user_content, created_at)"
                        + " VALUES(?,?,?,?,?,?)",
                new Object[]{id, branchId, ordinal, role, content, T0});
    }

    private void seedRevision(SQLiteDatabase db, String id, String messageId, int revisionNo) {
        db.execSQL("INSERT INTO answer_revisions(id, message_id, revision_no, reasoning_content,"
                        + " answer_content, model, thinking_enabled, prompt_policy_version,"
                        + " created_at) VALUES(?,?,?,?,?,?,?,?,?)",
                new Object[]{id, messageId, revisionNo, "reasoning", "answer", MODEL, 1,
                        POLICY_VERSION, T0});
    }

    private void insertOperation(SQLiteDatabase db, String id, String branchId, String messageId,
                                 String state) {
        db.execSQL("INSERT INTO generation_operations(id, branch_id, message_id, command_id,"
                        + " prompt_purpose, model, thinking_enabled, prompt_policy_version, state,"
                        + " partial_reasoning, partial_answer, created_at, updated_at)"
                        + " VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)",
                new Object[]{id, branchId, messageId, "cmd-" + id, "tl-learning-answer", MODEL, 1,
                        POLICY_VERSION, state, "", "", T0, T0});
    }

    private void seedSnapshot(SQLiteDatabase db, String id, String branchId) {
        db.execSQL("INSERT INTO context_snapshots(id, branch_id, schema_version, digest, payload,"
                        + " created_at) VALUES(?,?,?,?,?,?)",
                new Object[]{id, branchId, 1, "digest-" + id, "[]", T0});
    }

    private void seedArtifact(SQLiteDatabase db, String id, String scopeId,
                              String basedOnRevisionId) {
        db.execSQL("INSERT INTO ai_artifacts(id, kind, scope_id, prompt_purpose,"
                        + " prompt_policy_version, payload, based_on_revision_id, input_digest,"
                        + " created_at, updated_at) VALUES(?,?,?,?,?,?,?,?,?,?)",
                new Object[]{id, "ROOT_TITLE", scopeId, "tl-root-title", POLICY_VERSION, "{}",
                        basedOnRevisionId, "digest-" + id, T0, T0});
    }

    /**
     * Runs an action that must fail and hands back the failure so the caller can assert which
     * invariant fired. A bare {@code assertThrows} would also be satisfied by an unrelated failure,
     * which silently turns a real invariant into an untested claim.
     */
    private static <T extends Throwable> T expectedFailure(Class<T> type, String scenario,
                                                           Runnable action) {
        try {
            action.run();
        } catch (Throwable actual) {
            if (type.isInstance(actual)) {
                return type.cast(actual);
            }
            fail(scenario + ": expected " + type.getSimpleName() + " but got " + actual);
        }
        fail(scenario + ": the database accepted an operation it must reject");
        throw new AssertionError("unreachable");
    }

    private static SQLiteConstraintException constraintFailure(String scenario, Runnable action) {
        return expectedFailure(SQLiteConstraintException.class, scenario, action);
    }

    /**
     * Asserts that a rejection was produced by one specific constraint class, and — when
     * {@code requiredText} is given — by the specific constraint this scenario is about.
     *
     * <p>The code proves the class; the text proves which constraint fired. Both are needed: a
     * CHECK violation on an unrelated column would satisfy the code alone, and the text alone can
     * be matched by the wording of a different constraint.
     */
    private static void assertCause(String scenario, String message, String expectedCode,
                                    String requiredText) {
        assertTrue(scenario + ": expected " + expectedCode + " but the store reported " + message,
                message.contains(expectedCode));
        if (requiredText != null) {
            assertTrue(scenario + ": the failure must name " + requiredText + " but was " + message,
                    message.contains(requiredText));
        }
    }

    // ------------------------------------------------------------------
    // Raw inspection helpers (platform API only).
    // ------------------------------------------------------------------

    /** Counts user tables without naming them, so it cannot mirror a hardcoded list. */
    private static final class TableCensus {

        private TableCensus() {
        }

        static int count(SQLiteDatabase db) {
            int count = 0;
            try (Cursor cursor = db.rawQuery(
                    "SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%'"
                            + " AND name NOT LIKE 'android_%'", null)) {
                count = cursor.getCount();
            }
            return count;
        }
    }

    private static Set<String> actualTables(SQLiteDatabase db) {
        Set<String> tables = new TreeSet<>();
        try (Cursor cursor = db.rawQuery(
                "SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%'"
                        + " AND name NOT LIKE 'android_%'", null)) {
            while (cursor.moveToNext()) {
                tables.add(cursor.getString(0));
            }
        }
        return tables;
    }

    private static Set<String> actualSchemaObjects(SQLiteDatabase db, String type) {
        Set<String> names = new TreeSet<>();
        try (Cursor cursor = db.rawQuery(
                "SELECT name FROM sqlite_master WHERE type=? AND name NOT LIKE 'sqlite_%'",
                new String[]{type})) {
            while (cursor.moveToNext()) {
                names.add(cursor.getString(0));
            }
        }
        return names;
    }

    private static Set<String> actualColumns(SQLiteDatabase db, String table) {
        Set<String> columns = new LinkedHashSet<>();
        try (Cursor cursor = db.rawQuery("PRAGMA table_info(" + table + ")", null)) {
            while (cursor.moveToNext()) {
                columns.add(cursor.getString(cursor.getColumnIndexOrThrow("name")));
            }
        }
        return columns;
    }

    private static String columnType(SQLiteDatabase db, String table, String column) {
        return columnProperty(db, table, column, "type");
    }

    private static int columnNotNull(SQLiteDatabase db, String table, String column) {
        return Integer.parseInt(columnProperty(db, table, column, "notnull"));
    }

    private static String columnProperty(SQLiteDatabase db, String table, String column,
                                         String property) {
        try (Cursor cursor = db.rawQuery("PRAGMA table_info(" + table + ")", null)) {
            while (cursor.moveToNext()) {
                if (column.equals(cursor.getString(cursor.getColumnIndexOrThrow("name")))) {
                    return cursor.getString(cursor.getColumnIndexOrThrow(property));
                }
            }
        }
        return null;
    }

    private static String schemaObjectDdl(SQLiteDatabase db, String type, String name) {
        return rawString(db, "SELECT sql FROM sqlite_master WHERE type='" + type + "' AND name='"
                + name + "'");
    }

    private static String tableDdl(SQLiteDatabase db, String table) {
        String sql = schemaObjectDdl(db, "table", table);
        return sql == null ? "" : sql;
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

    private static String rawString(SQLiteDatabase db, String sql) {
        try (Cursor cursor = db.rawQuery(sql, null)) {
            return cursor.moveToFirst() ? cursor.getString(0) : null;
        }
    }

    private static int rowCount(SQLiteDatabase db, String table) {
        return rawInt(db, "SELECT COUNT(*) FROM " + table);
    }

    private static int rowCountWhere(SQLiteDatabase db, String table, String where) {
        return rawInt(db, "SELECT COUNT(*) FROM " + table + " WHERE " + where);
    }

    private static String stringWhere(SQLiteDatabase db, String table, String column,
                                      String where) {
        return rawString(db, "SELECT " + column + " FROM " + table + " WHERE " + where);
    }
}
