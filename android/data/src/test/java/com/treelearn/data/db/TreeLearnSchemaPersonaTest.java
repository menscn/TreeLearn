package com.treelearn.data.db;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.treelearn.core.domain.Persona;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import org.junit.Test;

/**
 * V5 -> V6: the three persona columns (SPEC-2026-09-21-ai-persona-preference 数据与迁移).
 *
 * <p>The class documents why every install path must execute the same declaration text: an
 * {@code ALTER TABLE ADD COLUMN} cannot carry a CHECK, so a fresh install and an upgrade must not be
 * allowed to describe the column differently. The column stores an identity, never prompt text, and
 * carries {@code NOT NULL DEFAULT 'default'} so existing rows acquire the default style without the
 * migration inventing per-row semantics.
 */
public final class TreeLearnSchemaPersonaTest {

    @Test
    public void theSchemaVersionIsSixAndTheStepIsRegistered() {
        assertEquals(6, TreeLearnSchema.DATABASE_VERSION);

        int steps = 0;
        for (TreeLearnSchema.Migration migration : TreeLearnSchema.FORWARD_MIGRATIONS) {
            if (migration.fromVersion() == 5) {
                steps++;
                assertEquals(6, migration.toVersion());
            }
        }
        assertEquals("exactly one V5 -> V6 step", 1, steps);
        assertFalse(TreeLearnSchema.MIGRATION_V5_TO_V6_STATEMENTS.isEmpty());
    }

    @Test
    public void bothInstallPathsUseTheSameColumnDeclaration() {
        List<String> upgrade = TreeLearnSchema.MIGRATION_V5_TO_V6_STATEMENTS;
        assertEquals("three tables learn the column", 3, upgrade.size());

        assertAlterAndFreshInstallAgree(upgrade.get(0), TreeLearnSchema.Topics.TABLE,
                TreeLearnSchema.Topics.PERSONA_DECLARATION);
        assertAlterAndFreshInstallAgree(upgrade.get(1),
                TreeLearnSchema.GenerationOperations.TABLE,
                TreeLearnSchema.GenerationOperations.PERSONA_DECLARATION);
        assertAlterAndFreshInstallAgree(upgrade.get(2), TreeLearnSchema.AnswerRevisions.TABLE,
                TreeLearnSchema.AnswerRevisions.PERSONA_DECLARATION);
    }

    @Test
    public void theDefaultValueIsTheDefaultWireId() {
        for (String declaration : new String[] {
                TreeLearnSchema.Topics.PERSONA_DECLARATION,
                TreeLearnSchema.GenerationOperations.PERSONA_DECLARATION,
                TreeLearnSchema.AnswerRevisions.PERSONA_DECLARATION,
        }) {
            assertTrue("the column must be NOT NULL so no row has an absent style: " + declaration,
                    declaration.contains("TEXT NOT NULL"));
            assertTrue("the default must be the stable wire id: " + declaration,
                    declaration.contains("DEFAULT '" + Persona.DEFAULT.wireId + "'"));
            assertFalse("an ALTER-added column must not carry a CHECK, otherwise a fresh install"
                    + " and an upgrade accept different values: " + declaration,
                    declaration.contains("CHECK"));
        }
    }

    @Test
    public void theColumnIsWrittenOncePerTableInTheSchemaSource() throws IOException {
        String source = readSchemaSource();
        // Each table class declares its own constant; the declaration text itself is not retyped
        // on either install path, and the default wire id is never restated here at all.
        assertEquals("one declaration constant per persona table", 3,
                countOf(source, "String PERSONA_DECLARATION ="));
        assertEquals("the default wire id is written once, in the Persona enum, and referenced"
                        + " through it", 0,
                countOf(source, "\"default\""));
    }

    private static void assertAlterAndFreshInstallAgree(String statement, String table,
                                                         String declaration) {
        assertEquals("ALTER TABLE " + table + " ADD COLUMN " + declaration, statement);
        assertTrue("a fresh install must declare exactly this text: " + declaration,
                statementStartingWith(TreeLearnSchema.CREATE_TABLE_STATEMENTS,
                        "CREATE TABLE " + table).contains(declaration));
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
