package com.treelearn.data.db;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import org.junit.Test;

/**
 * T-2026-09-28-026 acceptance, schema half: the annotations table is written once and both install
 * paths execute it (方案 V1.1 §3.1/§3.4, user-confirmed 2026-09-28, plus the three
 * db-migration-reviewer amendments F1–F3 of the same day).
 *
 * <p>The annotations table is a plain new object — no {@code ALTER TABLE} trick is needed — so the
 * whole DDL can live in the one definition list and both install paths execute <em>the same
 * strings</em>: a fresh install through {@link TreeLearnSchema#CREATE_STATEMENTS}, the V4 → V5
 * upgrade through {@link TreeLearnSchema#MIGRATION_V4_TO_V5_STATEMENTS}. This suite pins exactly
 * that, plus the confirmed rules the CHECK set carries (including F1: a highlight's body is empty
 * <em>by DDL</em>, not only by DAO discipline) and that the upgrade only adds objects.
 *
 * <p>What it cannot prove is that the engine refuses the offending writes — that needs the real
 * Android SQLite engine (rules/sql.md) and is deferred with the device rounds (user instruction
 * 2026-09-21, same as the quote-invariant precedent). A supplementary run of the same DDL on a
 * desktop SQLite engine is recorded in the task's evidence, explicitly as a supplement and not as
 * device evidence.
 */
public final class TreeLearnSchemaAnnotationsTest {

    private static final String TABLE = TreeLearnSchema.Annotations.TABLE;

    private static final String ANCHOR_INDEX = TreeLearnSchema.INDEX_ANNOTATIONS_ANCHOR;
    private static final String MESSAGE_INDEX = TreeLearnSchema.INDEX_ANNOTATIONS_MESSAGE;

    @Test
    public void theAnnotationsTableIsDeclaredExactlyOnceOnAFreshInstall() {
        String annotationsTable = statementStartingWith(
                TreeLearnSchema.CREATE_STATEMENTS, "CREATE TABLE " + TABLE);

        assertEquals("the twelfth permanent table joins the confirmed set", 12,
                TreeLearnSchema.CREATE_TABLE_STATEMENTS.size());
        assertEquals("the table name is registered once in TABLE_NAMES", 1,
                countOf(String.join("\n", TreeLearnSchema.TABLE_NAMES), TABLE));
        assertTrue("the message FK must cascade: regenerating or deleting a message takes its"
                        + " marks with it (拍板 2026-09-28)",
                annotationsTable.contains("REFERENCES " + TreeLearnSchema.Messages.TABLE + "("
                        + TreeLearnSchema.Messages.ID + ") ON DELETE CASCADE"));
        assertTrue("content_digest is written by the insert path and read by the clear, so it"
                        + " cannot be null",
                annotationsTable.contains(TreeLearnSchema.Annotations.CONTENT_DIGEST
                        + " TEXT NOT NULL"));
    }

    @Test
    public void theUpgradeExecutesExactlyTheStatementsTheFreshInstallUses() {
        List<String> upgrade = TreeLearnSchema.MIGRATION_V4_TO_V5_STATEMENTS;

        assertEquals("the upgrade adds the table and its two indexes, nothing else", 3,
                upgrade.size());
        assertEquals("the upgraded table must be the identical text a fresh install creates",
                statementStartingWith(TreeLearnSchema.CREATE_TABLE_STATEMENTS,
                        "CREATE TABLE " + TABLE), upgrade.get(0));
        assertTrue("the anchor index text is the fresh install's",
                TreeLearnSchema.CREATE_INDEX_STATEMENTS.contains(upgrade.get(1)));
        assertTrue("the message index text is the fresh install's",
                TreeLearnSchema.CREATE_INDEX_STATEMENTS.contains(upgrade.get(2)));
    }

    @Test
    public void theAnchorIndexIsUniqueAndCarriesTheKind() {
        String anchor = TreeLearnSchema.MIGRATION_V4_TO_V5_STATEMENTS.get(1);
        String message = TreeLearnSchema.MIGRATION_V4_TO_V5_STATEMENTS.get(2);

        assertTrue("uniqueness is the dedup backstop: the same anchor row must not be writable"
                        + " twice behind the DAO's back",
                anchor.startsWith("CREATE UNIQUE INDEX " + ANCHOR_INDEX));
        assertTrue("kind is inside the unique key: the same range may carry a highlight AND a"
                        + " note (方案 §3.1)",
                anchor.contains(", " + TreeLearnSchema.Annotations.KIND + ", "));
        assertTrue(anchor.contains(TreeLearnSchema.Annotations.START_OFFSET));
        assertTrue(anchor.contains(TreeLearnSchema.Annotations.END_OFFSET));
        assertEquals("the message index is a plain lookup index",
                "CREATE INDEX " + MESSAGE_INDEX + " ON " + TABLE + "("
                        + TreeLearnSchema.Annotations.MESSAGE_ID + ")", message);
    }

    @Test
    public void theFiveTableChecksCarryTheConfirmedRules() {
        String annotationsTable = statementStartingWith(
                TreeLearnSchema.CREATE_TABLE_STATEMENTS, "CREATE TABLE " + TABLE);

        assertEquals("the confirmed DDL carries exactly five CHECK constraints", 5,
                countOf(annotationsTable, "CHECK ("));
        assertTrue(annotationsTable.contains(TreeLearnSchema.Annotations.KIND
                + " IN ('HIGHLIGHT','NOTE')"));
        assertTrue("a note without a body is not a note",
                annotationsTable.contains(TreeLearnSchema.Annotations.KIND + " <> 'NOTE' OR "
                        + TreeLearnSchema.Annotations.BODY + " <> ''"));
        assertTrue("F1 (db-migration-reviewer 2026-09-28): a highlight's body is empty by DDL,"
                        + " not only by DAO discipline",
                annotationsTable.contains(TreeLearnSchema.Annotations.KIND
                        + " <> 'HIGHLIGHT' OR " + TreeLearnSchema.Annotations.BODY + " = ''"));
        assertTrue("the locator sentinels: a PROSE row carries prose_index and -1 elsewhere",
                annotationsTable.contains(TreeLearnSchema.Annotations.CONTAINER
                        + " = 'PROSE' AND "));
        assertTrue("and a CELL row carries the cell triple with prose_index = -1",
                annotationsTable.contains(TreeLearnSchema.Annotations.CONTAINER
                        + " = 'CELL' AND "));
    }

    @Test
    public void theVersionChainIsContiguousUpToSix() {
        // V6 (SPEC-2026-09-21-ai-persona-preference) is the current version; the literal is the one
        // version this suite pins, exactly like the quote-invariant suite pins its own.
        assertEquals(6, TreeLearnSchema.DATABASE_VERSION);

        for (int version = 1; version < TreeLearnSchema.DATABASE_VERSION; version++) {
            int steps = 0;
            for (TreeLearnSchema.Migration migration : TreeLearnSchema.FORWARD_MIGRATIONS) {
                if (migration.fromVersion() == version) {
                    steps++;
                    assertEquals("a forward step moves exactly one version", version + 1,
                            migration.toVersion());
                }
            }
            assertEquals("applyForwardMigrations fails closed without the step from version "
                    + version, 1, steps);
        }

        assertNotEquals("the V4 -> V5 upgrade must actually do something", 0,
                TreeLearnSchema.MIGRATION_V4_TO_V5_STATEMENTS.size());
    }

    @Test
    public void theUpgradeOnlyAddsObjects() {
        for (String statement : TreeLearnSchema.MIGRATION_V4_TO_V5_STATEMENTS) {
            assertTrue("the upgrade must not touch the existing eleven tables; it only adds"
                            + " objects: " + statement,
                    statement.startsWith("CREATE "));
        }
    }

    @Test
    public void theAnnotationRulesAreSpelledOnceInTheSchemaSource() throws IOException {
        String source = readSchemaSource();

        assertEquals("a second copy of the table name would be a second spelling of the fact", 1,
                countOf(source, "\"" + TABLE + "\""));
        assertEquals("one spelling of the anchor index name", 1,
                countOf(source, "\"" + ANCHOR_INDEX + "\""));
        assertEquals("one spelling of the message index name", 1,
                countOf(source, "\"" + MESSAGE_INDEX + "\""));
        assertEquals("the kind vocabulary is spelled once in DDL", 1,
                countOf(source, "'HIGHLIGHT','NOTE'"));
        assertEquals("the NOTE half of the body rule is spelled once", 1,
                countOf(source, "<> 'NOTE' OR "));
        assertEquals("the HIGHLIGHT half (F1) is spelled once", 1,
                countOf(source, "<> 'HIGHLIGHT' OR "));
        assertEquals("the locator-sentinel rule is spelled once", 1,
                countOf(source, "= 'PROSE' AND "));
    }

    private static String statementStartingWith(List<String> statements, String prefix) {
        for (String statement : statements) {
            if (statement.startsWith(prefix)) {
                return statement;
            }
        }
        fail("no statement starts with: " + prefix);
        return null;
    }

    /**
     * Reads the schema source itself, because what has to hold here is a property of the
     * <em>written text</em> — that each rule is spelled out once — and that property is invisible
     * from the compiled constants alone. Fails closed when the file cannot be located: a silent
     * skip would turn this into a test that verifies nothing.
     */
    private static String readSchemaSource() throws IOException {
        Path[] candidates = {
                Paths.get("src/main/java/com/treelearn/data/db/TreeLearnSchema.java"),
                Paths.get("data/src/main/java/com/treelearn/data/db/TreeLearnSchema.java"),
                Paths.get("android/data/src/main/java/com/treelearn/data/db/TreeLearnSchema.java"),
        };
        for (Path candidate : candidates) {
            if (Files.isRegularFile(candidate)) {
                return new String(Files.readAllBytes(candidate), StandardCharsets.UTF_8);
            }
        }
        throw new AssertionError("cannot locate TreeLearnSchema.java from "
                + Paths.get("").toAbsolutePath() + "; this assertion must not be skipped");
    }

    private static int countOf(String haystack, String needle) {
        int count = 0;
        int index = haystack.indexOf(needle);
        while (index >= 0) {
            count++;
            index = haystack.indexOf(needle, index + needle.length());
        }
        return count;
    }
}
