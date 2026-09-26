package com.treelearn.data.db;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

/**
 * T-037 acceptance, schema half: the quote invariant is written once and both install paths execute
 * it.
 *
 * <p><strong>Why this is not a device test.</strong> {@code ALTER TABLE ADD COLUMN} cannot carry a
 * {@code CHECK}, so the invariant cannot live in the {@code messages} definition: a CHECK written
 * there would exist on a fresh install and not on an upgraded database, and the same write would be
 * accepted by one install and refused by the other. The rule is therefore a trigger, the trigger
 * DDL is assembled from one condition text, and both paths — {@code onCreate} through
 * {@link TreeLearnSchema#CREATE_STATEMENTS} and {@code onUpgrade} through
 * {@link TreeLearnSchema#MIGRATION_V1_TO_V2_STATEMENTS} — execute <em>that same text</em>. This
 * suite pins exactly that: one rule text, one declaration per column, and no third spelling
 * anywhere in either path.
 *
 * <p>What it cannot prove is that the engine refuses the offending writes: that needs the real
 * Android SQLite engine (rules/sql.md) and is deferred with the device rounds (user instruction
 * 2026-09-21). A supplementary run of the same DDL on a desktop SQLite engine is recorded in the
 * task's evidence, explicitly as a supplement and not as device evidence.
 */
public final class TreeLearnSchemaQuoteInvariantTest {

    private static final String CONDITION = TreeLearnSchema.MESSAGES_QUOTE_INVARIANT_CONDITION;

    private static final String COLUMN = TreeLearnSchema.Messages.QUOTED_TEXT;

    @Test
    public void theColumnIsDeclaredExactlyOnceInTheMessagesTable() {
        String messagesTable = statementStartingWith(
                TreeLearnSchema.CREATE_STATEMENTS, "CREATE TABLE " + TreeLearnSchema.Messages.TABLE);

        assertEquals("the declaration is the only place the column may appear in the table: a"
                        + " CHECK mentioning it would exist on fresh installs only, because"
                        + " ALTER TABLE ADD COLUMN cannot carry one",
                1, countOf(messagesTable, COLUMN));
        assertTrue("the declaration must be the one shared with the migration",
                messagesTable.contains(TreeLearnSchema.Messages.QUOTED_TEXT_DECLARATION));
    }

    @Test
    public void theRuleIsOneConditionTextWrappedOncePerWriteEvent() {
        List<String> invariant = TreeLearnSchema.MESSAGES_QUOTE_INVARIANT_STATEMENTS;

        assertEquals("a row invariant has to hold on INSERT and on UPDATE; a third wrapper would"
                + " be a second rule", 2, invariant.size());
        for (String statement : invariant) {
            assertTrue("every wrapper is trigger DDL: " + statement,
                    statement.startsWith("CREATE TRIGGER "));
            assertTrue("every wrapper refuses loudly: " + statement,
                    statement.contains("RAISE(ABORT"));
            assertEquals("each wrapper must carry the one condition text exactly once", 1,
                    countOf(statement, CONDITION));
        }
        assertNotEquals("one wrapper per write event, not the same statement twice",
                invariant.get(0), invariant.get(1));
    }

    @Test
    public void bothPathsExecuteTheSameInvariantText() {
        List<String> fresh = invariantStatementsIn(TreeLearnSchema.CREATE_STATEMENTS);
        List<String> upgrade = invariantStatementsIn(
                TreeLearnSchema.MIGRATION_V1_TO_V2_STATEMENTS);

        assertEquals("the fresh install must execute the invariant statements", 2, fresh.size());
        assertEquals("the upgrade must execute the same invariant statements", 2, upgrade.size());
        assertEquals("an upgraded database has to end up with the identical rule text",
                fresh, upgrade);
        assertEquals(TreeLearnSchema.MESSAGES_QUOTE_INVARIANT_STATEMENTS, fresh);
    }

    @Test
    public void theUpgradeAddsExactlyTheFreshDeclarationOfTheSameColumn() {
        String addition = "ALTER TABLE " + TreeLearnSchema.Messages.TABLE + " ADD COLUMN "
                + TreeLearnSchema.Messages.QUOTED_TEXT_DECLARATION;

        assertEquals("the upgraded column must be declared with the very text a fresh install"
                        + " uses, or the two databases would carry two column shapes",
                1, countOf(String.join("\n", TreeLearnSchema.MIGRATION_V1_TO_V2_STATEMENTS),
                        addition));
    }

    @Test
    public void nothingElseInEitherPathNamesTheColumn() {
        for (String statement : TreeLearnSchema.CREATE_STATEMENTS) {
            if (!statement.contains(COLUMN)) {
                continue;
            }
            boolean isTheTable = statement.startsWith(
                    "CREATE TABLE " + TreeLearnSchema.Messages.TABLE);
            assertTrue("only the messages table definition and the invariant may name the column,"
                            + " but this does: " + statement,
                    isTheTable || TreeLearnSchema.MESSAGES_QUOTE_INVARIANT_STATEMENTS.contains(
                            statement));
        }
        for (String statement : TreeLearnSchema.MIGRATION_V1_TO_V2_STATEMENTS) {
            if (!statement.contains(COLUMN)) {
                continue;
            }
            boolean isTheAddition = statement.startsWith(
                    "ALTER TABLE " + TreeLearnSchema.Messages.TABLE);
            assertTrue("only the column addition and the invariant may name the column on the"
                            + " upgrade path, but this does: " + statement,
                    isTheAddition || TreeLearnSchema.MESSAGES_QUOTE_INVARIANT_STATEMENTS.contains(
                            statement));
        }
    }

    @Test
    public void everyVersionBelowTheCurrentOneHasExactlyOneForwardStep() {
        // V4 (T-096) is the current version; the literal is the one version this suite pins.
        assertEquals(4, TreeLearnSchema.DATABASE_VERSION);

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

        List<String> upgrade = TreeLearnSchema.MIGRATION_V1_TO_V2_STATEMENTS;
        assertFalse("the upgrade must actually do something", upgrade.isEmpty());
    }

    @Test
    public void theRuleTextIsWrittenOnceInTheSchemaSource() throws IOException {
        String source = readSchemaSource();

        assertEquals("the condition is the rule: one text, referenced by every wrapper and by"
                + " both paths", 1, countOf(source, "'USER' OR length(NEW."));
        assertEquals("a second copy of the column name would be a second spelling of the fact", 1,
                countOf(source, "\"" + COLUMN + "\""));
        assertEquals("one refusal text, shared by both write events", 1,
                countOf(source, "a quote may only be carried by a non-empty USER"));
    }

    private static List<String> invariantStatementsIn(List<String> statements) {
        List<String> found = new ArrayList<>();
        for (String statement : statements) {
            if (statement.contains(CONDITION)) {
                found.add(statement);
            }
        }
        return found;
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
     * <em>written text</em> — that the rule is spelled out once — and that property is invisible
     * from the compiled constants alone (two identical literals would compile to the same text).
     * Fails closed when the file cannot be located: a silent skip would turn this into a test that
     * verifies nothing.
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
