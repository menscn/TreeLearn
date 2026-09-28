package com.treelearn.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.treelearn.core.domain.Annotation;
import com.treelearn.data.db.ConstraintExtendedCode;
import com.treelearn.data.db.TreeLearnDatabaseHelper;
import com.treelearn.data.db.TreeLearnSchema;
import com.treelearn.data.db.dao.AnnotationDao;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;

/**
 * T-2026-09-28-026 (T-A) acceptance: the annotation data layer, on real Android framework SQLite.
 *
 * <p>Two install paths, one schema. The fresh-install path runs the production {@code onCreate}
 * through the real helper; the upgrade path hand-builds a genuine V4 database — the current
 * production DDL minus exactly the objects V5 introduces — stamps it {@code user_version = 4},
 * and lets the production helper run the real {@code onUpgrade} over it. Neither path is replayed
 * by hand, because a hand replay proves the SQL text is well-formed, not that the helper runs it.
 *
 * <p>The CHECK/UNIQUE/FK rejections below are written with <em>raw SQL</em>, deliberately bypassing
 * the DAO: the DAO refusing a malformed row is the DAO's own validation, and asserting it would
 * prove nothing about the DDL the schema promised. Each rejection is judged by its SQLite extended
 * result code through {@link ConstraintExtendedCode} — the same reading point production uses —
 * because a message substring cannot tell a CHECK from a NOT NULL whose text contains IS NOT NULL
 * (the scar is documented in {@link SqliteSchemaInstrumentedTest}).
 *
 * <p>The digest-clearing read is asserted end to end: rows whose {@code content_digest} no longer
 * matches are gone after {@link AnnotationDao#listFor}, and only matching rows come back. That is
 * the whole regeneration contract of 方案 §3.3 — deletion, no relocation — and it is one write
 * path, so it heals on the next read no matter which code path replaced the answer. The merge flow
 * itself is judged in {@code :core} ({@code Annotation.mergeHighlights}: absorbed rows deleted,
 * union row inserted); this suite proves the verbs that flow is built from.
 *
 * <p>Holds no real user content: every literal is synthetic (rules/sql.md).
 */
@RunWith(AndroidJUnit4.class)
public final class AnnotationDaoInstrumentedTest {

    /** UTC epoch millis, written explicitly by the application (rules/sql.md). */
    private static final long T0 = 1_756_000_000_000L;
    private static final long T1 = T0 + 60_000L;

    private static final String DIGEST_A = "digest-alpha";
    private static final String DIGEST_B = "digest-beta";

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

    // ------------------------------------------------------------------
    // 1. Fresh install: the production onCreate builds the table and both indexes.
    // ------------------------------------------------------------------

    @Test
    public void freshInstallCreatesTheAnnotationTableWithBothIndexes() {
        SQLiteDatabase db = helper.getWritableDatabase();

        assertEquals(TreeLearnSchema.DATABASE_VERSION, rawInt(db, "PRAGMA user_version"));
        assertTrue("the annotation table must exist on a fresh install",
                actualTables(db).contains(TreeLearnSchema.Annotations.TABLE));

        List<String> unique = new ArrayList<>();
        List<String> plain = new ArrayList<>();
        indexesOf(db, TreeLearnSchema.Annotations.TABLE, unique, plain);
        assertTrue("the anchor index must be unique",
                unique.contains(TreeLearnSchema.INDEX_ANNOTATIONS_ANCHOR));
        assertTrue("the message index must be plain",
                plain.contains(TreeLearnSchema.INDEX_ANNOTATIONS_MESSAGE));
    }

    // ------------------------------------------------------------------
    // 2. The V4 -> V5 upgrade: real onUpgrade over a genuine V4 file.
    // ------------------------------------------------------------------

    @Test
    public void aVersionFourDatabaseIsUpgradedKeepingEveryPreexistingRow() {
        // Fill the database path with a genuine V4 file first, so opening the helper afterwards
        // goes through the real onUpgrade instead of onCreate.
        SQLiteDatabase v4 = SQLiteDatabase.openOrCreateDatabase(
                context.getDatabasePath(TreeLearnDatabaseHelper.DATABASE_NAME), null);
        long topicsBefore;
        long branchesBefore;
        long messagesBefore;
        long revisionsBefore;
        try {
            v4.execSQL("PRAGMA foreign_keys = ON");
            for (String statement : versionFourCreateStatements()) {
                v4.execSQL(statement);
            }
            seedTopic(v4, "t-v4");
            seedBranch(v4, "b-v4-root", "t-v4", null);
            seedMessage(v4, uuid("m-v4"), "b-v4-root", 0, "USER");
            seedRevision(v4, "r-v4", uuid("m-v4"));
            v4.execSQL("PRAGMA user_version = 4");
            assertEquals(4, rawInt(v4, "PRAGMA user_version"));
            assertFalse("a V4 database must not yet have the annotation table",
                    actualTables(v4).contains(TreeLearnSchema.Annotations.TABLE));

            topicsBefore = rowCount(v4, "topics");
            branchesBefore = rowCount(v4, "branches");
            messagesBefore = rowCount(v4, "messages");
            revisionsBefore = rowCount(v4, "answer_revisions");
        } finally {
            v4.close();
        }

        SQLiteDatabase upgraded = helper.getWritableDatabase();

        assertEquals("the upgrade must stamp the current version",
                TreeLearnSchema.DATABASE_VERSION, rawInt(upgraded, "PRAGMA user_version"));
        assertTrue("the upgrade must add the annotation table",
                actualTables(upgraded).contains(TreeLearnSchema.Annotations.TABLE));
        List<String> unique = new ArrayList<>();
        List<String> plain = new ArrayList<>();
        indexesOf(upgraded, TreeLearnSchema.Annotations.TABLE, unique, plain);
        assertTrue("the upgrade must add the unique anchor index",
                unique.contains(TreeLearnSchema.INDEX_ANNOTATIONS_ANCHOR));
        assertTrue("the upgrade must add the plain message index",
                plain.contains(TreeLearnSchema.INDEX_ANNOTATIONS_MESSAGE));

        // The upgrade only adds objects: every pre-existing row must survive untouched.
        assertEquals(topicsBefore, rowCount(upgraded, "topics"));
        assertEquals(branchesBefore, rowCount(upgraded, "branches"));
        assertEquals(messagesBefore, rowCount(upgraded, "messages"));
        assertEquals(revisionsBefore, rowCount(upgraded, "answer_revisions"));
        assertEquals("the pre-existing message survived", 1,
                rowCountWhere(upgraded, "messages", "id='" + uuid("m-v4") + "'"));

        // And the new table must be immediately usable through the production DAO.
        AnnotationDao.insert(upgraded, uuid("m-v4"), DIGEST_A,
                Annotation.highlight(uuid("a-v4"), Annotation.Locator.prose(0), 0, 5), T0);
        assertEquals(1, AnnotationDao.listFor(upgraded, uuid("m-v4"), DIGEST_A).size());
    }

    // ------------------------------------------------------------------
    // 3. DDL-level constraints, written as raw SQL so the DAO's own validation stays out.
    // ------------------------------------------------------------------

    @Test
    public void theDdlRefusesInvertedOrEmptyRanges() {
        SQLiteDatabase db = seededDatabase();
        assertCheckRefused(db, "empty range", rangeInsert(uuid("a-r0"), uuid("m-1"), 0, 5, 5));
        assertCheckRefused(db, "inverted range", rangeInsert(uuid("a-r1"), uuid("m-1"), 0, 7, 5));
    }

    @Test
    public void theDdlRefusesUnknownKindsAndContainers() {
        SQLiteDatabase db = seededDatabase();
        assertCheckRefused(db, "unknown kind", rawInsert(uuid("a-k0"), uuid("m-1"), "'PROSE'", "0, -1, -1, -1",
                "0", "5", "'MARK'", DIGEST_A, "''"));
        assertCheckRefused(db, "unknown container", rawInsert(uuid("a-c0"), uuid("m-1"), "'TABLE'",
                "-1, 0, 0, 0", "0", "5", "'HIGHLIGHT'", DIGEST_A, "''"));
    }

    @Test
    public void theDdlRefusesBrokenContainerSentinels() {
        SQLiteDatabase db = seededDatabase();
        // PROSE row carrying cell sentinels, and the CELL mirror image — the mutual exclusion the
        // container CHECK encodes (方案 §3.1 拍板: PROSE/CELL share one table on one sentinel).
        assertCheckRefused(db, "PROSE with a table index", rawInsert(uuid("a-s0"), uuid("m-1"), "'PROSE'",
                "0, 0, -1, -1", "0", "5", "'HIGHLIGHT'", DIGEST_A, "''"));
        assertCheckRefused(db, "CELL with a prose index", rawInsert(uuid("a-s1"), uuid("m-1"), "'CELL'",
                "0, -1, -1, -1", "0", "5", "'HIGHLIGHT'", DIGEST_A, "''"));
        assertCheckRefused(db, "CELL with a negative row", rawInsert(uuid("a-s2"), uuid("m-1"), "'CELL'",
                "-1, 0, -1, 0", "0", "5", "'HIGHLIGHT'", DIGEST_A, "''"));
    }

    @Test
    public void theDdlRefusesNoteWithEmptyBodyAndHighlightWithBody() {
        SQLiteDatabase db = seededDatabase();
        // F1 (db-migration-reviewer 2026-09-28): both directions, one CHECK each.
        assertCheckRefused(db, "NOTE with an empty body", rawInsert(uuid("a-b0"), uuid("m-1"), "'PROSE'",
                "0, -1, -1, -1", "0", "5", "'NOTE'", DIGEST_A, "''"));
        assertCheckRefused(db, "HIGHLIGHT carrying a body", rawInsert(uuid("a-b1"), uuid("m-1"), "'PROSE'",
                "0, -1, -1, -1", "0", "5", "'HIGHLIGHT'", DIGEST_A, "'text'"));
    }

    @Test
    public void theDdlRefusesDuplicateAnchorsButAllowsSameRangeAcrossKinds() {
        SQLiteDatabase db = seededDatabase();
        AnnotationDao.insert(db, uuid("m-1"), DIGEST_A,
                Annotation.highlight(uuid("a-u0"), Annotation.Locator.prose(1), 10, 20), T0);
        try {
            AnnotationDao.insert(db, uuid("m-1"), DIGEST_A,
                    Annotation.highlight(uuid("a-u1"), Annotation.Locator.prose(1), 10, 20), T0);
            fail("a duplicate anchor was accepted; the unique index is the dedup backstop");
        } catch (android.database.sqlite.SQLiteConstraintException expected) {
            assertEquals(2067, ConstraintExtendedCode.of(expected.getMessage()));
        }

        // 拍板: kind is part of the anchor, so a highlight and a note coexist on one range.
        AnnotationDao.insert(db, uuid("m-1"), DIGEST_A,
                Annotation.note(uuid("a-u2"), Annotation.Locator.prose(1), 10, 20, "first note"), T0);
        assertEquals(2, rowCountWhere(db, TreeLearnSchema.Annotations.TABLE,
                "message_id='" + uuid("m-1") + "'"));
    }

    @Test
    public void theDdlRefusesAnnotationsForMissingMessagesAndCascadesTheirDeletion() {
        SQLiteDatabase db = seededDatabase();
        try {
            AnnotationDao.insert(db, uuid("m-ghost"), DIGEST_A,
                    Annotation.highlight(uuid("a-f0"), Annotation.Locator.prose(0), 0, 5), T0);
            fail("an annotation for a missing message was accepted");
        } catch (android.database.sqlite.SQLiteConstraintException expected) {
            assertEquals(787, ConstraintExtendedCode.of(expected.getMessage()));
        }

        AnnotationDao.insert(db, uuid("m-1"), DIGEST_A,
                Annotation.highlight(uuid("a-f1"), Annotation.Locator.prose(0), 0, 5), T0);
        AnnotationDao.insert(db, uuid("m-1"), DIGEST_A,
                Annotation.highlight(uuid("a-f2"), Annotation.Locator.prose(1), 10, 20), T0);
        db.execSQL("DELETE FROM messages WHERE id='" + uuid("m-1") + "'");
        assertEquals("the annotation rows must cascade away with their message",
                0, rowCount(db, TreeLearnSchema.Annotations.TABLE));
    }

    // ------------------------------------------------------------------
    // 4. The digest-clearing read (方案 §3.3): one write path, self-healing.
    // ------------------------------------------------------------------

    @Test
    public void listForRemovesDigestMismatchedRowsAndReturnsOnlyMatchingOnes() {
        SQLiteDatabase db = seededDatabase();
        AnnotationDao.insert(db, uuid("m-1"), DIGEST_A,
                Annotation.highlight(uuid("a-d0"), Annotation.Locator.prose(0), 0, 5), T0);
        AnnotationDao.insert(db, uuid("m-1"), DIGEST_A,
                Annotation.highlight(uuid("a-d1"), Annotation.Locator.prose(1), 10, 20), T0);
        AnnotationDao.insert(db, uuid("m-1"), DIGEST_B,
                Annotation.highlight(uuid("a-d2"), Annotation.Locator.prose(2), 40, 45), T0);

        List<Annotation> kept = AnnotationDao.listFor(db, uuid("m-1"), DIGEST_A);

        assertEquals("only the rows whose digest matches come back", 2, kept.size());
        for (Annotation annotation : kept) {
            assertEquals(DIGEST_A, digestOf(db, annotation.id));
        }

        // And the mismatched row is genuinely gone, not merely filtered out of one answer.
        assertEquals(2, rowCountWhere(db, TreeLearnSchema.Annotations.TABLE,
                "message_id='" + uuid("m-1") + "'"));
        assertEquals(0, rowCountWhere(db, TreeLearnSchema.Annotations.TABLE,
                "id='" + uuid("a-d2") + "'"));
    }

    @Test
    public void listForWithANeverSeenDigestClearsEveryRowOfTheMessage() {
        SQLiteDatabase db = seededDatabase();
        AnnotationDao.insert(db, uuid("m-1"), DIGEST_A,
                Annotation.highlight(uuid("a-e0"), Annotation.Locator.prose(0), 0, 5), T0);

        assertTrue(AnnotationDao.listFor(db, uuid("m-1"), "digest-never-stored").isEmpty());
        assertEquals("a digest the answer never carried leaves nothing behind",
                0, rowCountWhere(db, TreeLearnSchema.Annotations.TABLE,
                        "message_id='" + uuid("m-1") + "'"));
    }

    @Test
    public void listForLeavesOtherMessagesUntouched() {
        SQLiteDatabase db = seededDatabase();
        seedMessage(db, uuid("m-2"), "b-root", 1, "USER");
        AnnotationDao.insert(db, uuid("m-1"), DIGEST_A,
                Annotation.highlight(uuid("a-g0"), Annotation.Locator.prose(0), 0, 5), T0);
        AnnotationDao.insert(db, uuid("m-2"), DIGEST_A,
                Annotation.highlight(uuid("a-g1"), Annotation.Locator.prose(0), 0, 5), T0);

        assertTrue(AnnotationDao.listFor(db, uuid("m-1"), DIGEST_B).isEmpty());
        assertEquals("a clear is per message, never per table",
                1, rowCountWhere(db, TreeLearnSchema.Annotations.TABLE,
                        "message_id='" + uuid("m-2") + "'"));
    }

    // ------------------------------------------------------------------
    // 5. The write verbs the merge and edit flows are built from.
    // ------------------------------------------------------------------

    @Test
    public void deleteRemovesExactlyTheNamedRowAndIsIdempotent() {
        SQLiteDatabase db = seededDatabase();
        AnnotationDao.insert(db, uuid("m-1"), DIGEST_A,
                Annotation.highlight(uuid("a-i0"), Annotation.Locator.prose(0), 0, 5), T0);
        AnnotationDao.insert(db, uuid("m-1"), DIGEST_A,
                Annotation.highlight(uuid("a-i1"), Annotation.Locator.prose(1), 10, 20), T0);

        AnnotationDao.delete(db, uuid("a-i0"));
        AnnotationDao.delete(db, uuid("a-i0"));
        AnnotationDao.delete(db, uuid("a-never-existed"));
        assertEquals("the sibling row must survive a delete of another",
                1, rowCountWhere(db, TreeLearnSchema.Annotations.TABLE,
                        "id='" + uuid("a-i1") + "'"));
        assertEquals(1, rowCount(db, TreeLearnSchema.Annotations.TABLE));
    }

    @Test
    public void updateBodyRewritesOnlyNotes() {
        SQLiteDatabase db = seededDatabase();
        AnnotationDao.insert(db, uuid("m-1"), DIGEST_A,
                Annotation.note(uuid("a-n0"), Annotation.Locator.prose(0), 0, 5, "first draft"), T0);
        AnnotationDao.insert(db, uuid("m-1"), DIGEST_A,
                Annotation.highlight(uuid("a-n1"), Annotation.Locator.prose(1), 10, 20), T0);

        AnnotationDao.updateBody(db, uuid("a-n0"), "revised note", T1);
        Annotation reread = byId(db, uuid("a-n0"), uuid("m-1"));
        assertEquals(Annotation.Kind.NOTE, reread.kind);
        assertEquals("revised note", reread.body);
        assertEquals(T1, updatedAtOf(db, uuid("a-n0")));
        assertEquals(T0, createdAtOf(db, uuid("a-n0")));
        assertEquals("a highlight must still have an empty body", "",
                byId(db, uuid("a-n1"), uuid("m-1")).body);

        try {
            AnnotationDao.updateBody(db, uuid("a-n1"), "text on a highlight", T1);
            fail("a highlight accepted a body; the DDL forbids exactly that");
        } catch (IllegalStateException expected) {
            // The DAO refuses before the CHECK has to.
        }
    }

    @Test
    public void updateBodyRefusesBlankBodiesAndUnknownIds() {
        SQLiteDatabase db = seededDatabase();
        AnnotationDao.insert(db, uuid("m-1"), DIGEST_A,
                Annotation.note(uuid("a-n2"), Annotation.Locator.prose(0), 0, 5, "note"), T0);

        assertIllegalArgument(() -> AnnotationDao.updateBody(db, uuid("a-n2"), "   ", T1),
                "a blank body");
        assertIllegalArgument(() -> AnnotationDao.updateBody(db, uuid("a-n2"), null, T1),
                "a null body");
        try {
            AnnotationDao.updateBody(db, uuid("a-ghost"), "note", T1);
            fail("updating a missing row was accepted");
        } catch (IllegalStateException expected) {
            // The update must touch exactly one row.
        }
    }

    @Test
    public void insertRefusesMalformedArgumentsBeforeTouchingTheDatabase() {
        SQLiteDatabase db = seededDatabase();
        assertIllegalArgument(() -> AnnotationDao.insert(null, uuid("m-1"), DIGEST_A,
                        Annotation.highlight(uuid("a-v0"), Annotation.Locator.prose(0), 0, 5), T0),
                "a null database");
        assertIllegalArgument(() -> AnnotationDao.insert(db, null, DIGEST_A,
                        Annotation.highlight(uuid("a-v1"), Annotation.Locator.prose(0), 0, 5), T0),
                "a null message id");
        assertIllegalArgument(() -> AnnotationDao.insert(db, "  ", DIGEST_A,
                        Annotation.highlight(uuid("a-v2"), Annotation.Locator.prose(0), 0, 5), T0),
                "a blank message id");
        assertIllegalArgument(() -> AnnotationDao.insert(db, uuid("m-1"), null,
                        Annotation.highlight(uuid("a-v3"), Annotation.Locator.prose(0), 0, 5), T0),
                "a null digest");
        assertIllegalArgument(() -> AnnotationDao.insert(db, uuid("m-1"), " ",
                        Annotation.highlight(uuid("a-v4"), Annotation.Locator.prose(0), 0, 5), T0),
                "a blank digest");
        assertIllegalArgument(() -> AnnotationDao.insert(db, uuid("m-1"), DIGEST_A, null, T0),
                "a null annotation");
    }

    // ------------------------------------------------------------------
    // 6. Close / reopen: the rows are the record.
    // ------------------------------------------------------------------

    @Test
    public void rowsSurviveCloseAndReopenFieldByField() {
        SQLiteDatabase db = helper.getWritableDatabase();
        seedTopic(db, "t-open");
        seedBranch(db, "b-open-root", "t-open", null);
        seedMessage(db, uuid("m-open"), "b-open-root", 0, "USER");
        Annotation prose = Annotation.highlight(uuid("a-o0"), Annotation.Locator.prose(2), 10, 25);
        Annotation cell = Annotation.note(uuid("a-o1"), Annotation.Locator.cell(1, 3, 2), 0, 7,
                "cell note");
        AnnotationDao.insert(db, uuid("m-open"), DIGEST_A, prose, T0);
        AnnotationDao.insert(db, uuid("m-open"), DIGEST_A, cell, T0);
        helper.close();

        helper = new TreeLearnDatabaseHelper(context);
        List<Annotation> reread = AnnotationDao.listFor(
                helper.getWritableDatabase(), uuid("m-open"), DIGEST_A);

        assertEquals(2, reread.size());
        Annotation proseBack = byIdIn(reread, prose.id);
        assertEquals(prose.id, proseBack.id);
        assertEquals(prose.locator, proseBack.locator);
        assertEquals(prose.span, proseBack.span);
        assertEquals(Annotation.Kind.HIGHLIGHT, proseBack.kind);
        assertEquals("", proseBack.body);
        Annotation cellBack = byIdIn(reread, cell.id);
        assertEquals(cell.locator, cellBack.locator);
        assertEquals(cell.span, cellBack.span);
        assertEquals(Annotation.Kind.NOTE, cellBack.kind);
        assertEquals("cell note", cellBack.body);
        assertEquals("the write timestamp survives as stored (application-written UTC millis)",
                T0, createdAtOf(helper.getWritableDatabase(), cell.id));
    }

    @Test
    public void listForReturnsRowsInAnchorOrderAndRefusesBlankArguments() {
        SQLiteDatabase db = seededDatabase();
        // The ids deliberately run opposite to the prose order, so this case pins the anchor
        // ordering and cannot silently pass on an id-ordered read.
        AnnotationDao.insert(db, uuid("m-1"), DIGEST_A,
                Annotation.highlight(uuid("a-z0"), Annotation.Locator.prose(2), 0, 5), T0);
        AnnotationDao.insert(db, uuid("m-1"), DIGEST_A,
                Annotation.highlight(uuid("a-z1"), Annotation.Locator.prose(1), 0, 5), T0);
        AnnotationDao.insert(db, uuid("m-1"), DIGEST_A,
                Annotation.highlight(uuid("a-z2"), Annotation.Locator.prose(0), 0, 5), T0);

        List<Annotation> rows = AnnotationDao.listFor(db, uuid("m-1"), DIGEST_A);
        assertEquals(uuid("a-z2"), rows.get(0).id);
        assertEquals(uuid("a-z1"), rows.get(1).id);
        assertEquals(uuid("a-z0"), rows.get(2).id);

        assertIllegalArgument(() -> AnnotationDao.listFor(db, "  ", DIGEST_A),
                "a blank message id");
        assertIllegalArgument(() -> AnnotationDao.listFor(db, uuid("m-1"), null),
                "a null digest");
    }

    // ------------------------------------------------------------------
    // Fixtures.
    // ------------------------------------------------------------------

    /**
     * Deterministic UUID-shaped id for a fixture seed: stable across runs (this suite asserts by
     * id), unique per seed, and accepted by {@code Guard.id}, which the domain's factories run
     * every id through. Hex of the seed, left-padded, in the final group.
     */
    private static String uuid(String seed) {
        StringBuilder hex = new StringBuilder();
        for (char c : seed.toCharArray()) {
            hex.append(String.format("%02x", (int) c & 0xff));
        }
        while (hex.length() < 12) {
            hex.insert(0, '0');
        }
        return "00000000-0000-4000-8000-" + hex.substring(hex.length() - 12);
    }

    /**
     * The DDL a V4 database was created with: the current production statements minus exactly the
     * objects V5 introduces. Derived from the live constants rather than pasted, for the same
     * reason {@code SqliteSchemaInstrumentedTest.versionOneCreateStatements} is — a pasted copy
     * would drift and prove the upgrade on a schema no device ever had.
     */
    private static List<String> versionFourCreateStatements() {
        List<String> statements = new ArrayList<>();
        for (String statement : TreeLearnSchema.CREATE_TABLE_STATEMENTS) {
            if (statement.startsWith("CREATE TABLE " + TreeLearnSchema.Annotations.TABLE)) {
                continue;
            }
            statements.add(statement);
        }
        for (String statement : TreeLearnSchema.CREATE_INDEX_STATEMENTS) {
            if (statement.contains(TreeLearnSchema.Annotations.TABLE)) {
                continue;
            }
            statements.add(statement);
        }
        statements.addAll(TreeLearnSchema.CREATE_TRIGGER_STATEMENTS);
        statements.addAll(TreeLearnSchema.SEED_STATEMENTS);
        return statements;
    }

    private SQLiteDatabase seededDatabase() {
        SQLiteDatabase db = helper.getWritableDatabase();
        seedTopic(db, "t-1");
        seedBranch(db, "b-root", "t-1", null);
        seedMessage(db, uuid("m-1"), "b-root", 0, "USER");
        return db;
    }

    private static void seedTopic(SQLiteDatabase db, String id) {
        db.execSQL("INSERT INTO topics(id, last_visited_branch_id, created_at, updated_at)"
                + " VALUES(?,?,?,?)", new Object[]{id, null, T0, T0});
    }

    private static void seedBranch(SQLiteDatabase db, String id, String topicId, String parentId) {
        db.execSQL("INSERT INTO branches(id, topic_id, parent_id, title, created_at, updated_at,"
                + " collapsed) VALUES(?,?,?,?,?,?,?)",
                new Object[]{id, topicId, parentId, "", T0, T0, 0});
    }

    private static void seedMessage(SQLiteDatabase db, String id, String branchId, int ordinal,
                                    String role) {
        Object content = "USER".equals(role) ? "synthetic question" : null;
        db.execSQL("INSERT INTO messages(id, branch_id, ordinal, role, user_content, created_at)"
                        + " VALUES(?,?,?,?,?,?)",
                new Object[]{id, branchId, ordinal, role, content, T0});
    }

    private static void seedRevision(SQLiteDatabase db, String id, String messageId) {
        db.execSQL("INSERT INTO answer_revisions(id, message_id, revision_no, reasoning_content,"
                        + " answer_content, model, thinking_enabled, prompt_policy_version,"
                        + " created_at) VALUES(?,?,?,?,?,?,?,?,?)",
                new Object[]{id, messageId, 1, "reasoning", "answer", "deepseek-flash", 1,
                        "tl-learning-answer/1.0.0", T0});
    }

    /** Raw-SQL insert with every column spelled out, for the DDL-level rejection cases. */
    private static final class ThrowingInsert {
        private final String id;
        private final String messageId;
        private final String containerSql;
        private final String sentinels;
        private final String start;
        private final String end;
        private final String kindSql;
        private final String digest;
        private final String bodySql;

        ThrowingInsert(String id, String messageId, String containerSql, String sentinels,
                       String start, String end, String kindSql, String digest, String bodySql) {
            this.id = id;
            this.messageId = messageId;
            this.containerSql = containerSql;
            this.sentinels = sentinels;
            this.start = start;
            this.end = end;
            this.kindSql = kindSql;
            this.digest = digest;
            this.bodySql = bodySql;
        }

        void run(SQLiteDatabase db) {
            db.execSQL("INSERT INTO " + TreeLearnSchema.Annotations.TABLE + "("
                    + TreeLearnSchema.Annotations.ID + ", "
                    + TreeLearnSchema.Annotations.MESSAGE_ID + ", "
                    + TreeLearnSchema.Annotations.CONTAINER + ", "
                    + TreeLearnSchema.Annotations.PROSE_INDEX + ", "
                    + TreeLearnSchema.Annotations.TABLE_INDEX + ", "
                    + TreeLearnSchema.Annotations.ROW_INDEX + ", "
                    + TreeLearnSchema.Annotations.COL_INDEX + ", "
                    + TreeLearnSchema.Annotations.START_OFFSET + ", "
                    + TreeLearnSchema.Annotations.END_OFFSET + ", "
                    + TreeLearnSchema.Annotations.KIND + ", "
                    + TreeLearnSchema.Annotations.CONTENT_DIGEST + ", "
                    + TreeLearnSchema.Annotations.BODY + ", "
                    + TreeLearnSchema.Annotations.CREATED_AT + ", "
                    + TreeLearnSchema.Annotations.UPDATED_AT + ") VALUES('"
                    + id + "', '" + messageId + "', " + containerSql + ", " + sentinels + ", "
                    + start + ", " + end + ", " + kindSql + ", '" + digest + "', " + bodySql
                    + ", " + T0 + ", " + T0 + ")");
        }
    }

    private static ThrowingInsert rawInsert(String id, String messageId, String containerSql,
                                            String sentinels, String start, String end,
                                            String kindSql, String digest, String bodySql) {
        return new ThrowingInsert(id, messageId, containerSql, sentinels, start, end, kindSql,
                digest, bodySql);
    }

    private static ThrowingInsert rangeInsert(String id, String messageId, int proseIndex,
                                              int start, int end) {
        return new ThrowingInsert(id, messageId, "'PROSE'",
                proseIndex + ", -1, -1, -1", String.valueOf(start), String.valueOf(end),
                "'HIGHLIGHT'", DIGEST_A, "''");
    }

    private void assertCheckRefused(SQLiteDatabase db, String scenario, ThrowingInsert insert) {
        try {
            insert.run(db);
        } catch (android.database.sqlite.SQLiteConstraintException expected) {
            assertEquals(scenario + ": expected the CHECK failure class",
                    275, ConstraintExtendedCode.of(expected.getMessage()));
            return;
        }
        fail(scenario + ": the DDL accepted a row its CHECK forbids");
    }

    // ------------------------------------------------------------------
    // Read helpers (raw where the value under test is not the DAO's own read).
    // ------------------------------------------------------------------

    private static Annotation byId(SQLiteDatabase db, String id, String messageId) {
        return byIdIn(AnnotationDao.listFor(db, messageId, digestOf(db, id)), id);
    }

    private static Annotation byIdIn(List<Annotation> rows, String id) {
        for (Annotation annotation : rows) {
            if (annotation.id.equals(id)) {
                return annotation;
            }
        }
        fail("no annotation row carries " + id);
        return null;
    }

    private static String digestOf(SQLiteDatabase db, String id) {
        return rawString(db, "SELECT " + TreeLearnSchema.Annotations.CONTENT_DIGEST + " FROM "
                + TreeLearnSchema.Annotations.TABLE + " WHERE "
                + TreeLearnSchema.Annotations.ID + "='" + id + "'");
    }

    private static long createdAtOf(SQLiteDatabase db, String id) {
        return rawLong(db, "SELECT " + TreeLearnSchema.Annotations.CREATED_AT + " FROM "
                + TreeLearnSchema.Annotations.TABLE + " WHERE "
                + TreeLearnSchema.Annotations.ID + "='" + id + "'");
    }

    private static long updatedAtOf(SQLiteDatabase db, String id) {
        return rawLong(db, "SELECT " + TreeLearnSchema.Annotations.UPDATED_AT + " FROM "
                + TreeLearnSchema.Annotations.TABLE + " WHERE "
                + TreeLearnSchema.Annotations.ID + "='" + id + "'");
    }

    private static void indexesOf(SQLiteDatabase db, String table, List<String> unique,
                                  List<String> plain) {
        try (Cursor cursor = db.rawQuery("PRAGMA index_list(" + table + ")", null)) {
            int nameIndex = cursor.getColumnIndex("name");
            int uniqueIndex = cursor.getColumnIndex("unique");
            assertTrue("PRAGMA index_list must report name and unique",
                    nameIndex >= 0 && uniqueIndex >= 0);
            while (cursor.moveToNext()) {
                if (cursor.getInt(uniqueIndex) == 1) {
                    unique.add(cursor.getString(nameIndex));
                } else {
                    plain.add(cursor.getString(nameIndex));
                }
            }
        }
    }

    private static List<String> actualTables(SQLiteDatabase db) {
        List<String> tables = new ArrayList<>();
        try (Cursor cursor = db.rawQuery(
                "SELECT name FROM sqlite_master WHERE type='table'"
                        + " AND name NOT LIKE 'sqlite_%' AND name NOT LIKE 'android_%'", null)) {
            while (cursor.moveToNext()) {
                tables.add(cursor.getString(0));
            }
        }
        return tables;
    }

    private static long rowCount(SQLiteDatabase db, String table) {
        return rawLong(db, "SELECT COUNT(*) FROM " + table);
    }

    private static long rowCountWhere(SQLiteDatabase db, String table, String where) {
        return rawLong(db, "SELECT COUNT(*) FROM " + table + " WHERE " + where);
    }

    private static long rawLong(SQLiteDatabase db, String sql) {
        try (Cursor cursor = db.rawQuery(sql, null)) {
            cursor.moveToFirst();
            return cursor.getLong(0);
        }
    }

    private static int rawInt(SQLiteDatabase db, String sql) {
        try (Cursor cursor = db.rawQuery(sql, null)) {
            cursor.moveToFirst();
            return cursor.getInt(0);
        }
    }

    private static String rawString(SQLiteDatabase db, String sql) {
        try (Cursor cursor = db.rawQuery(sql, null)) {
            if (!cursor.moveToFirst()) {
                return null;
            }
            return cursor.isNull(0) ? null : cursor.getString(0);
        }
    }

    private interface Throwing {
        void run();
    }

    private static void assertIllegalArgument(Throwing action, String scenario) {
        try {
            action.run();
        } catch (IllegalArgumentException expected) {
            return;
        } catch (Throwable unexpected) {
            fail(scenario + ": expected IllegalArgumentException but got " + unexpected);
        }
        fail(scenario + ": the DAO accepted an argument it must refuse");
    }
}
