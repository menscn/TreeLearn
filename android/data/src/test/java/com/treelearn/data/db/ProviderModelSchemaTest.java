package com.treelearn.data.db;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.treelearn.core.domain.GenerationModel;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * T-2026-09-23-083 acceptance, schema half: the two provider/model tables, their forward migration
 * and the builtin DeepSeek seed are pinned here as written text (SPEC-2026-09-23 数据结构).
 *
 * <p>What a JVM suite can prove is the <em>shape</em>: the exact column sets the spec fixes (no
 * field may be added or dropped), that no column can hold a key, that a model row dies with its
 * provider (ON DELETE CASCADE), and that model ids dedup case-exactly (the F8 口径: a UNIQUE index
 * on (provider_id, model_id) with SQLite's default BINARY collation and no NOCASE anywhere).
 *
 * <p>Whether the engine actually enforces these, and whether a real V2 database upgrades with its
 * rows intact, is proven on real Android SQLite by
 * {@code com.treelearn.app.ProviderModelDaoInstrumentedTest} (rules/sql.md).
 */
public final class ProviderModelSchemaTest {

    @Test
    public void theSchemaVersionIsFourWithAForwardStepPerPublishedVersion() {
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
    }

    @Test
    public void theUpgradeCreatesBothTablesWithTheFreshInstallText() {
        List<String> fresh = createTableStatementsFor(TreeLearnSchema.Providers.TABLE,
                TreeLearnSchema.Models.TABLE);
        List<String> upgrade = createTableStatementsIn(
                TreeLearnSchema.MIGRATION_V2_TO_V3_STATEMENTS);

        assertEquals("an upgraded database must end up with the identical table DDL", fresh,
                upgrade);
    }

    @Test
    public void bothInstallPathsSeedTheSameBuiltinDeepSeekRowsIdempotently() {
        assertEquals("the upgrade seeds exactly the statements a fresh install ends with",
                TreeLearnSchema.SEED_STATEMENTS,
                seedStatementsIn(TreeLearnSchema.MIGRATION_V2_TO_V3_STATEMENTS));
        assertEquals("a fresh install must also seed, or a new user has no DeepSeek provider",
                TreeLearnSchema.SEED_STATEMENTS,
                seedStatementsIn(TreeLearnSchema.CREATE_STATEMENTS));

        for (String seed : TreeLearnSchema.SEED_STATEMENTS) {
            assertTrue("the seed must be rerunnable without effect: " + seed,
                    seed.startsWith("INSERT OR IGNORE INTO "));
        }
        String providerSeed = statementFor(TreeLearnSchema.SEED_STATEMENTS,
                TreeLearnSchema.Providers.TABLE);
        assertTrue(providerSeed.contains(TreeLearnSchema.BUILTIN_DEEPSEEK_PROVIDER_ID));
        assertTrue(providerSeed.contains("'" + TreeLearnSchema.BUILTIN_DEEPSEEK_NAME + "'"));
        assertTrue(providerSeed.contains("'" + TreeLearnSchema.BUILTIN_DEEPSEEK_BASE_URL + "'"));
        String modelSeed = statementFor(TreeLearnSchema.SEED_STATEMENTS,
                TreeLearnSchema.Models.TABLE);
        assertTrue(modelSeed.contains(TreeLearnSchema.BUILTIN_DEEPSEEK_DEFAULT_MODEL_ID));
        assertTrue("the default model is the current chat tier", modelSeed.contains(
                "'" + GenerationModel.FLASH.wireId + "'"));
    }

    @Test
    public void bothTablesCarryExactlyTheSpecifiedColumns() {
        assertEquals("provider columns, exactly as SPEC-2026-09-23 fixes them",
                new LinkedHashSet<>(Arrays.asList("id", "name", "base_url", "enabled",
                        "is_builtin", "created_at", "updated_at")),
                columnNamesOf(createTableFor(TreeLearnSchema.Providers.TABLE)));
        assertEquals("model columns, exactly as SPEC-2026-09-23 fixes them",
                new LinkedHashSet<>(Arrays.asList("id", "provider_id", "model_id", "display_name",
                        "created_at")),
                columnNamesOf(createTableFor(TreeLearnSchema.Models.TABLE)));
    }

    @Test
    public void noColumnCanCarryASecret() {
        for (String table : new String[]{TreeLearnSchema.Providers.TABLE,
                TreeLearnSchema.Models.TABLE}) {
            for (String column : columnNamesOf(createTableFor(table))) {
                String lower = column.toLowerCase(Locale.ROOT);
                assertFalse(table + "." + column + " must not hold a key", lower.contains("key"));
                assertFalse(table + "." + column + " must not hold a secret",
                        lower.contains("secret"));
                assertFalse(table + "." + column + " must not hold a token",
                        lower.contains("token"));
                assertFalse(table + "." + column + " must not hold a credential",
                        lower.contains("credential"));
                assertFalse(table + "." + column + " must not hold an api value",
                        lower.contains("api"));
            }
        }
    }

    @Test
    public void modelsCascadeWithTheirProviderAndDedupByExactCase() {
        String models = createTableFor(TreeLearnSchema.Models.TABLE);
        assertTrue("a model row dies only together with its provider",
                models.contains(TreeLearnSchema.Models.PROVIDER_ID + " TEXT NOT NULL REFERENCES "
                        + TreeLearnSchema.Providers.TABLE + "(" + TreeLearnSchema.Providers.ID
                        + ") ON DELETE CASCADE"));

        String index = statementFor(TreeLearnSchema.CREATE_INDEX_STATEMENTS,
                TreeLearnSchema.INDEX_MODELS_PROVIDER_MODEL);
        assertTrue(index.contains("CREATE UNIQUE INDEX"));
        assertTrue("case-exact dedup lives on the (provider_id, model_id) pair",
                index.contains("(" + TreeLearnSchema.Models.PROVIDER_ID + ", "
                        + TreeLearnSchema.Models.MODEL_ID + ")"));
        assertFalse("BINARY is the exact-case collation; NOCASE would fold two model ids into one",
                index.toUpperCase(Locale.ROOT).contains("NOCASE"));
    }

    private static List<String> createTableStatementsFor(String... tables) {
        List<String> found = new ArrayList<>();
        for (String table : tables) {
            found.add(createTableFor(table));
        }
        return found;
    }

    private static String createTableFor(String table) {
        return statementStartingWith(TreeLearnSchema.CREATE_TABLE_STATEMENTS, "CREATE TABLE "
                + table + " (");
    }

    private static List<String> createTableStatementsIn(List<String> statements) {
        List<String> found = new ArrayList<>();
        for (String statement : statements) {
            if (statement.startsWith("CREATE TABLE ")) {
                found.add(statement);
            }
        }
        return found;
    }

    private static List<String> seedStatementsIn(List<String> statements) {
        List<String> found = new ArrayList<>();
        for (String statement : statements) {
            if (statement.startsWith("INSERT OR IGNORE INTO ")) {
                found.add(statement);
            }
        }
        return found;
    }

    private static String statementFor(List<String> statements, String marker) {
        for (String statement : statements) {
            if (statement.contains(marker)) {
                return statement;
            }
        }
        throw new AssertionError("no statement mentions " + marker);
    }

    private static String statementStartingWith(List<String> statements, String prefix) {
        for (String statement : statements) {
            if (statement.startsWith(prefix)) {
                return statement;
            }
        }
        throw new AssertionError("no statement starts with: " + prefix);
    }

    /**
     * The first token of every top-level comma-separated entry between the table's outer
     * parentheses. Commas inside CHECK expressions sit at a deeper nesting level, so a depth
     * counter is what keeps a column's CHECK from being mistaken for a column boundary.
     */
    private static Set<String> columnNamesOf(String createTable) {
        Set<String> names = new LinkedHashSet<>();
        String body = createTable.substring(createTable.indexOf('(') + 1,
                createTable.lastIndexOf(')'));
        int depth = 0;
        StringBuilder entry = new StringBuilder();
        for (int i = 0; i < body.length(); i++) {
            char c = body.charAt(i);
            if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
            } else if (c == ',' && depth == 0) {
                addColumnName(names, entry);
                entry.setLength(0);
                continue;
            }
            entry.append(c);
        }
        addColumnName(names, entry);
        return names;
    }

    private static void addColumnName(Set<String> names, StringBuilder entry) {
        String text = entry.toString().trim();
        if (!text.isEmpty()) {
            names.add(text.split("\\s+", 2)[0]);
        }
    }
}
