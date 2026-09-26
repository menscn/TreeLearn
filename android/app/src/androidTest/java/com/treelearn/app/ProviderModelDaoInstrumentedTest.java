package com.treelearn.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteConstraintException;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.treelearn.core.domain.GenerationModel;
import com.treelearn.data.db.TreeLearnDatabaseHelper;
import com.treelearn.data.db.TreeLearnSchema;
import com.treelearn.data.db.dao.ModelDao;
import com.treelearn.data.db.dao.ProviderDao;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;

/**
 * T-2026-09-23-083 acceptance, executed on real Android framework SQLite (rules/sql.md): the
 * V2 -> V3 upgrade, the builtin DeepSeek seed, and the {@code ProviderDao}/{@code ModelDao}
 * create/read/update/delete surface including the case-exact model-id dedup.
 *
 * <p>The upgrade fixture is a genuine previous-version database — the production DDL minus the two
 * tables and the one index V3 introduced — stamped {@code user_version = PREVIOUS_SCHEMA_VERSION}
 * and holding a row written before the upgrade, so the production {@code onUpgrade} (not a
 * hand-called replay) is what runs.
 *
 * <p>Holds no real user content and no API key: every literal is synthetic (rules/sql.md). The
 * tables carry configuration only, so nothing here needs a secret.
 */
@RunWith(AndroidJUnit4.class)
public final class ProviderModelDaoInstrumentedTest {

    /** UTC epoch millis (rules/sql.md): a fixed date in the past, before the builtin seed. */
    private static final long T0 = 1_756_000_000_000L;

    private static final long T1 = T0 + 1_000L;

    /**
     * The schema version the upgrade fixture simulates: the version immediately before the current
     * one, derived from the last registered forward migration (so a future version bump follows
     * automatically instead of silently restating a literal). Whitelisted by name in
     * {@code check_androidtest_schema_expectations.py} alongside {@code V1_SCHEMA_VERSION}.
     */
    private static final int PREVIOUS_SCHEMA_VERSION = TreeLearnSchema.FORWARD_MIGRATIONS
            .get(TreeLearnSchema.FORWARD_MIGRATIONS.size() - 1).fromVersion();

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
    // Fresh install: the builtin DeepSeek provider and its default model exist.
    // ------------------------------------------------------------------

    @Test
    public void aFreshInstallSeedsExactlyOneBuiltinProviderWithItsDefaultModel() {
        SQLiteDatabase db = helper.getWritableDatabase();

        assertEquals(TreeLearnSchema.DATABASE_VERSION, rawInt(db, "PRAGMA user_version"));
        List<ProviderDao.Row> providers = ProviderDao.listByCreatedAt(db);
        assertEquals("a fresh install starts with exactly the builtin provider", 1,
                providers.size());
        ProviderDao.Row builtin = providers.get(0);
        assertEquals(TreeLearnSchema.BUILTIN_DEEPSEEK_PROVIDER_ID, builtin.id);
        assertEquals(TreeLearnSchema.BUILTIN_DEEPSEEK_NAME, builtin.name);
        assertEquals(TreeLearnSchema.BUILTIN_DEEPSEEK_BASE_URL, builtin.baseUrl);
        assertTrue("the builtin provider ships enabled", builtin.enabled);
        assertTrue("the builtin provider is marked builtin", builtin.builtin);

        List<ModelDao.Row> models = ModelDao.findByProvider(db, builtin.id);
        assertEquals("the builtin provider ships its default model", 1, models.size());
        assertEquals(GenerationModel.FLASH.wireId, models.get(0).modelId);
        assertPragmasAreClean(db);
    }

    // ------------------------------------------------------------------
    // Upgrade: a real V2 database gains both tables, the seed, keeps its rows.
    // ------------------------------------------------------------------

    @Test
    public void aVersionTwoDatabaseIsUpgradedToTheCurrentSchemaWithItsDataIntact() {
        SQLiteDatabase v2 = SQLiteDatabase.openOrCreateDatabase(
                context.getDatabasePath(TreeLearnDatabaseHelper.DATABASE_NAME), null);
        try {
            v2.execSQL("PRAGMA foreign_keys = ON");
            for (String statement : versionTwoCreateStatements()) {
                v2.execSQL(statement);
            }
            v2.execSQL("INSERT INTO topics (id, created_at, updated_at) VALUES ('t-083', ?, ?)",
                    new Object[]{T0, T0});
            // The simulated database is the version immediately before the current one; the
            // historical version derives from the registered forward migrations instead of
            // restating a literal (check_androidtest_schema_expectations.py guards this).
            v2.execSQL("PRAGMA user_version = " + PREVIOUS_SCHEMA_VERSION);
            assertEquals(PREVIOUS_SCHEMA_VERSION, rawInt(v2, "PRAGMA user_version"));
        } finally {
            v2.close();
        }

        SQLiteDatabase upgraded = helper.getWritableDatabase();

        assertEquals("the upgrade must stamp the current version",
                TreeLearnSchema.DATABASE_VERSION, rawInt(upgraded, "PRAGMA user_version"));
        assertEquals("the pre-existing topic survived", 1,
                rawInt(upgraded, "SELECT COUNT(*) FROM topics WHERE id='t-083'"));
        assertEquals("the upgrade seeded the builtin provider", 1, ProviderDao.listByCreatedAt(
                upgraded).size());
        assertTrue("the builtin provider is enabled after the upgrade",
                ProviderDao.findById(upgraded, TreeLearnSchema.BUILTIN_DEEPSEEK_PROVIDER_ID).enabled);
        assertPragmasAreClean(upgraded);

        // Re-running the seed statements must not duplicate the builtin rows (幂等).
        for (String statement : TreeLearnSchema.SEED_STATEMENTS) {
            upgraded.execSQL(statement);
        }
        assertEquals("the seed is idempotent", 1, ProviderDao.listByCreatedAt(upgraded).size());
    }

    // ------------------------------------------------------------------
    // DAO surface: insert / find / list / enable / delete.
    // ------------------------------------------------------------------

    @Test
    public void aProviderRoundTripsThroughInsertFindListAndEnable() {
        SQLiteDatabase db = helper.getWritableDatabase();
        ProviderDao.Row row = new ProviderDao.Row("p-aurora", "Aurora", "https://aurora.example",
                true, false, T0, T0);
        ProviderDao.insert(db, row);

        ProviderDao.Row read = ProviderDao.findById(db, "p-aurora");
        assertNotNull(read);
        assertEquals("Aurora", read.name);
        assertEquals("https://aurora.example", read.baseUrl);
        assertTrue(read.enabled);
        assertFalse(read.builtin);

        ProviderDao.setEnabled(db, "p-aurora", false);
        ProviderDao.Row disabled = ProviderDao.findById(db, "p-aurora");
        assertNotNull(disabled);
        assertFalse("the enabled bit persists", disabled.enabled);
        assertTrue("toggling writes a fresh updated_at", disabled.updatedAt > disabled.createdAt);

        // Ordered by created_at, id as tie-breaker: the two 2025 rows first, then the builtin seed.
        ProviderDao.insert(db, new ProviderDao.Row("p-borealis", "Borealis",
                "https://borealis.example", false, false, T0, T0));
        List<ProviderDao.Row> all = ProviderDao.listByCreatedAt(db);
        assertEquals(3, all.size());
        assertEquals("p-aurora", all.get(0).id);
        assertEquals("p-borealis", all.get(1).id);
        assertEquals(TreeLearnSchema.BUILTIN_DEEPSEEK_PROVIDER_ID, all.get(2).id);

        assertEquals("Aurora", ProviderDao.findByName(db, "Aurora").name);
        assertNull("an unknown name reads as absent, not as a failure",
                ProviderDao.findByName(db, "no-such-name"));
    }

    @Test
    public void duplicateProviderNamesAreRefusedByTheStore() {
        SQLiteDatabase db = helper.getWritableDatabase();
        ProviderDao.insert(db, new ProviderDao.Row("p-first", "Twice", "https://one.example",
                true, false, T0, T0));
        assertThrows("the UNIQUE index on name must bite",
                SQLiteConstraintException.class,
                () -> ProviderDao.insert(db, new ProviderDao.Row("p-second", "Twice",
                        "https://two.example", true, false, T1, T1)));
    }

    @Test
    public void modelIdsDedupCaseExactlyWithinAProvider() {
        SQLiteDatabase db = helper.getWritableDatabase();
        ProviderDao.insert(db, new ProviderDao.Row("p-case", "CaseProv", "https://case.example",
                true, false, T0, T0));
        ModelDao.insert(db, new ModelDao.Row("m-1", "p-case", "Gpt-X", "Gpt X", T0));
        ModelDao.insert(db, new ModelDao.Row("m-2", "p-case", "gpt-x", "gpt x", T0));

        assertThrows("an exactly equal model id is a duplicate",
                SQLiteConstraintException.class,
                () -> ModelDao.insert(db, new ModelDao.Row("m-3", "p-case", "Gpt-X", "again", T1)));
        assertEquals("a case variant is a distinct model id (F8 大小写精确口径)", 2,
                ModelDao.findByProvider(db, "p-case").size());
    }

    @Test
    public void deletingAProviderCascadesItsModels() {
        SQLiteDatabase db = helper.getWritableDatabase();
        ProviderDao.insert(db, new ProviderDao.Row("p-doomed", "Doomed", "https://doomed.example",
                true, false, T0, T0));
        ModelDao.insert(db, new ModelDao.Row("m-a", "p-doomed", "model-a", "A", T0));
        ModelDao.insert(db, new ModelDao.Row("m-b", "p-doomed", "model-b", "B", T0));

        ProviderDao.delete(db, "p-doomed");

        assertTrue("the models disappeared with their provider",
                ModelDao.findByProvider(db, "p-doomed").isEmpty());
        assertPragmasAreClean(db);
    }

    @Test
    public void emptySelectionsReadBackAsEmptySetsWithoutRaising() {
        SQLiteDatabase db = helper.getWritableDatabase();

        assertTrue("an unknown provider has an empty model set, not an exception",
                ModelDao.findByProvider(db, "p-never-was").isEmpty());
        assertNull(ProviderDao.findById(db, "p-never-was"));
        assertThrows("deleting an absent row is a caller bug, not a silent success",
                IllegalStateException.class, () -> ProviderDao.delete(db, "p-never-was"));
    }

    // ------------------------------------------------------------------
    // Fixtures and helpers.
    // ------------------------------------------------------------------

    /** The production DDL minus the two tables and the one index V3 introduced. */
    private static List<String> versionTwoCreateStatements() {
        List<String> statements = new ArrayList<>();
        for (String statement : TreeLearnSchema.CREATE_TABLE_STATEMENTS) {
            // The fixture shape must match PREVIOUS_SCHEMA_VERSION, which derives to the version
            // before the current one: V3 today. So the V4 attachment pair stays out, while the V3
            // provider/model pair stays in — a genuine previous-version database has it, and the
            // production upgrade must not replay CREATE TABLE on existing tables (T-096).
            if (statement.startsWith("CREATE TABLE " + TreeLearnSchema.Attachments.TABLE)
                    || statement.startsWith("CREATE TABLE "
                            + TreeLearnSchema.MessageAttachments.TABLE)) {
                continue;
            }
            statements.add(statement);
        }
        for (String statement : TreeLearnSchema.CREATE_INDEX_STATEMENTS) {
            if (statement.contains(TreeLearnSchema.INDEX_ATTACHMENTS_CONTENT_HASH)
                    || statement.contains(TreeLearnSchema.INDEX_MESSAGE_ATTACHMENTS_MESSAGE)
                    || statement.contains(TreeLearnSchema.INDEX_MESSAGE_ATTACHMENTS_ATTACHMENT)) {
                continue;
            }
            statements.add(statement);
        }
        statements.addAll(TreeLearnSchema.CREATE_TRIGGER_STATEMENTS);
        // A genuine V3 device database carries the builtin seed (it arrived with the V2 -> V3
        // step); the fixture reproduces the result, not the journey.
        statements.addAll(TreeLearnSchema.SEED_STATEMENTS);
        return statements;
    }

    private static void assertPragmasAreClean(SQLiteDatabase db) {
        assertEquals(1, rawInt(db, "PRAGMA foreign_keys"));
        try (android.database.Cursor cursor = db.rawQuery("PRAGMA foreign_key_check", null)) {
            assertEquals("PRAGMA foreign_key_check must return no rows", 0, cursor.getCount());
        }
        assertEquals("PRAGMA integrity_check must be ok", "ok",
                rawString(db, "PRAGMA integrity_check"));
    }

    private static int rawInt(SQLiteDatabase db, String sql) {
        try (android.database.Cursor cursor = db.rawQuery(sql, null)) {
            cursor.moveToFirst();
            return cursor.getInt(0);
        }
    }

    private static String rawString(SQLiteDatabase db, String sql) {
        try (android.database.Cursor cursor = db.rawQuery(sql, null)) {
            cursor.moveToFirst();
            return cursor.getString(0);
        }
    }
}
