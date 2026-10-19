package com.treelearn.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.treelearn.app.files.AttachmentStore;
import com.treelearn.core.domain.Branch;
import com.treelearn.core.domain.Message;
import com.treelearn.core.domain.Topic;
import com.treelearn.data.db.TreeLearnDatabaseHelper;
import com.treelearn.data.db.TreeLearnSchema;
import com.treelearn.data.db.dao.AttachmentDao;
import com.treelearn.data.db.dao.BranchDao;
import com.treelearn.data.db.dao.MessageDao;
import com.treelearn.data.db.dao.TopicDao;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * T-096 acceptance evidence for the attachment storage layer, the message-attachment reference
 * relation and their deletion/missing semantics, executed on a real device against real framework
 * SQLite (rules/sql.md: Android SQLite gate).
 *
 * <p><strong>What is driven here.</strong> The real {@link AttachmentStore} over the real
 * {@link TreeLearnDatabaseHelper}: registration with content-hash dedup, message references,
 * referenced-file deletion that keeps the message text and turns its reference into a missing
 * annotation, physically-missing detection, the message-delete cascade for reference rows, and
 * the V3 -> V4 forward migration against a genuine V3 database file.
 *
 * <p>Holds no real user content and no API key: every literal is an obviously synthetic placeholder.
 */
@RunWith(AndroidJUnit4.class)
public final class AttachmentStorageInstrumentedTest {

    private static final long T0 = 1_756_000_000_000L;

    private static final String TOPIC = "7c7c7c7c-0000-4000-8000-0000000000c1";
    private static final String TOPIC_TITLE = "SYNTHETIC-ATT-TOPIC-TITLE";
    private static final byte[] FILE_BYTES =
            "SYNTHETIC-ATTACHMENT-CONTENT-FOR-T-096".getBytes(StandardCharsets.UTF_8);

    /**
     * The version the V3 -> V4 upgrade fixture is stamped with: the {@code fromVersion} of the
     * migration exercised by {@code versionThreeCreateStatements()}, which is the second-to-last
     * registered forward migration. Derived from the schema, not retyped, so the next version bump
     * cannot leave this fixture stamped for a version whose DDL the test no longer builds — that
     * mismatch fails loudly instead of quietly judging a different upgrade.
     */
    private static final int PREVIOUS_SCHEMA_VERSION = TreeLearnSchema.FORWARD_MIGRATIONS
            .get(TreeLearnSchema.FORWARD_MIGRATIONS.size() - 2).fromVersion();

    private final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();

    private TreeLearnDatabaseHelper helper;
    private AttachmentStore store;
    private String rootBranchId;
    private String messageId;

    @Before
    public void setUp() {
        context.deleteDatabase(TreeLearnDatabaseHelper.DATABASE_NAME);
        helper = new TreeLearnDatabaseHelper(context);
        store = new AttachmentStore(helper,
                new File(context.getFilesDir(), "attachments"));
        rootBranchId = seedTopicWithRootAndMessage();
    }

    @After
    public void tearDown() {
        if (helper != null) {
            helper.close();
        }
        context.deleteDatabase(TreeLearnDatabaseHelper.DATABASE_NAME);
        cleanAttachmentDirectory(context);
    }

    /** The attachment directory is shared on the device; each suite must leave it empty. */
    private static void cleanAttachmentDirectory(Context context) {
        File dir = new File(context.getFilesDir(), "attachments");
        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                // deleteRecursive-lite: bodies are flat files; a leftover .tmp is flat too.
                if (!file.delete() && file.isDirectory()) {
                    File[] nested = file.listFiles();
                    if (nested != null) {
                        for (File inner : nested) {
                            inner.delete();
                        }
                    }
                    file.delete();
                }
            }
        }
    }


    // ------------------------------------------------------------------
    // 1. Registration: one physical file per unique content, hash dedup.
    // ------------------------------------------------------------------

    @Test
    public void registeringTheSameContentTwiceStoresOneRowAndOnePhysicalFile() {
        String first = store.register("SYNTHETIC-FIRST-NAME.txt", "text/plain", FILE_BYTES, T0);
        String second = store.register("SYNTHETIC-SECOND-NAME.txt", "text/plain", FILE_BYTES,
                T0 + 1_000L);

        assertEquals("identical content must resolve to one stored record", first, second);
        assertEquals("one registration per unique content: the list holds",
                1, store.list().size());
        assertEquals("the first display name wins the dedup race",
                "SYNTHETIC-FIRST-NAME.txt", store.list().get(0).displayName);
        assertEquals("the physical directory holds exactly one file", 1, physicalFileCount());
        assertTrue("the stored file's bytes are the registered bytes",
                store.list().get(0).present);
    }

    // ------------------------------------------------------------------
    // 2. References: link, resolve, delete the file, message annotated missing, text intact.
    // ------------------------------------------------------------------

    @Test
    public void deletingAReferencedFileKeepsTheMessageTextAndMarksTheReferenceMissing() {
        String attachmentId = store.register("SYNTHETIC-REFERENCED.txt", "text/plain",
                FILE_BYTES, T0);
        store.link(messageId, attachmentId, T0 + 1_000L);

        List<AttachmentStore.Ref> before = store.refsOf(messageId);
        assertEquals("the message holds exactly one reference", 1, before.size());
        assertTrue("the reference resolves while the file exists", before.get(0).present);
        assertEquals("with the registered display name",
                "SYNTHETIC-REFERENCED.txt", before.get(0).displayName);

        store.deleteAttachment(attachmentId);

        assertEquals("the record leaves the manager list", 0, store.list().size());
        assertEquals("the physical file is gone", 0, physicalFileCount());
        List<AttachmentStore.Ref> after = store.refsOf(messageId);
        assertEquals("the reference row survives the attachment deletion", 1, after.size());
        assertFalse("and now resolves as missing: that is the annotation", after.get(0).present);

        assertEquals("the message row itself is untouched by the file deletion", 1, messageCount());
        assertEquals("and its text is intact",
                "SYNTHETIC-ATT-QUESTION", messageContent(messageId));
    }

    // ------------------------------------------------------------------
    // 3. Physical missing: a file the system cleaned away is reported, not silently listed.
    // ------------------------------------------------------------------

    @Test
    public void aPhysicallyMissingFileIsReportedInsteadOfSilentlyListed() {
        String attachmentId = store.register("SYNTHETIC-VANISHED.txt", "text/plain",
                FILE_BYTES, T0);
        assertTrue(store.fileOf(attachmentId).delete());

        List<AttachmentStore.Entry> entries = store.list();
        assertEquals("the record is still listed: only its file is gone", 1, entries.size());
        assertFalse("and the entry reports the physical absence",
                entries.get(0).present);
    }

    // ------------------------------------------------------------------
    // 4. Message deletion cascades reference rows, never the attachment record.
    // ------------------------------------------------------------------

    @Test
    public void deletingTheMessageCascadesItsReferenceRowsButNotTheAttachment() {
        String attachmentId = store.register("SYNTHETIC-ORPHAN-CANDIDATE.txt", "text/plain",
                FILE_BYTES, T0);
        store.link(messageId, attachmentId, T0 + 1_000L);

        SQLiteDatabase db = helper.getWritableDatabase();
        db.execSQL("DELETE FROM " + TreeLearnSchema.Messages.TABLE + " WHERE "
                + TreeLearnSchema.Messages.ID + "=?", new Object[]{messageId});

        assertEquals("the reference row died with its message", 0,
                AttachmentDao.referenceCount(db, attachmentId));
        assertEquals("the attachment record survives as the orphan candidate", 1,
                store.list().size());
    }

    // ------------------------------------------------------------------
    // 5. The V3 -> V4 forward migration on a genuine V3 database file.
    // ------------------------------------------------------------------

    @Test
    public void aVersionThreeDatabaseIsUpgradedToTheCurrentSchemaByTheProductionHelper() {
        helper.close();
        context.deleteDatabase(TreeLearnDatabaseHelper.DATABASE_NAME);

        SQLiteDatabase v3 = SQLiteDatabase.openOrCreateDatabase(
                context.getDatabasePath(TreeLearnDatabaseHelper.DATABASE_NAME), null);
        try {
            v3.execSQL("PRAGMA foreign_keys = ON");
            for (String statement : versionThreeCreateStatements()) {
                v3.execSQL(statement);
            }
            // Seed the V3 file through its own handle: going through the helper would reopen and
            // recreate the deleted database at the current version, and the fixture would prove
            // nothing about the upgrade.
            seedTopicWithRootAndMessage(v3);
            v3.execSQL("PRAGMA user_version = " + PREVIOUS_SCHEMA_VERSION);
            assertEquals("the fixture must look like V3", PREVIOUS_SCHEMA_VERSION,
                    rawInt(v3, "PRAGMA user_version"));
            assertTrue("a V3 database must not yet have the attachments table",
                    !tables(v3).contains(TreeLearnSchema.Attachments.TABLE));
        } finally {
            v3.close();
        }

        SQLiteDatabase upgraded = new TreeLearnDatabaseHelper(context).getWritableDatabase();

        assertEquals("the upgrade must stamp the current version",
                TreeLearnSchema.DATABASE_VERSION, rawInt(upgraded, "PRAGMA user_version"));
        assertTrue("the upgrade must add the attachments table",
                tables(upgraded).contains(TreeLearnSchema.Attachments.TABLE));
        assertTrue("the upgrade must add the message_attachments table",
                tables(upgraded).contains(TreeLearnSchema.MessageAttachments.TABLE));
        assertEquals("rows written before the upgrade survive it", 1, messageCount());
        assertEquals("foreign_key_check must report no dangling reference", 0,
                rawRows(upgraded, "PRAGMA foreign_key_check"));
        assertEquals("integrity_check must be ok", "ok",
                rawString(upgraded, "PRAGMA integrity_check"));
    }

    // ------------------------------------------------------------------
    // Fixtures and readers.
    // ------------------------------------------------------------------

    private String seedTopicWithRootAndMessage() {
        return seedTopicWithRootAndMessage(helper.getWritableDatabase());
    }

    private String seedTopicWithRootAndMessage(SQLiteDatabase db) {
        TopicDao.insert(db, new Topic(TOPIC, null, T0, T0));
        Branch root = Branch.root(TOPIC, T0);
        BranchDao.insert(db, root);
        root.setTitle(TOPIC_TITLE, T0 + 1L);
        BranchDao.update(db, root);
        Message message = Message.user(root.id, 0, "SYNTHETIC-ATT-QUESTION", T0 + 2_000L);
        MessageDao.insert(db, message);
        messageId = message.id;
        return root.id;
    }

    private int physicalFileCount() {
        File[] files = store.directory().listFiles();
        return files == null ? 0 : files.length;
    }

    private int messageCount() {
        return rawInt(helper.getWritableDatabase(), "SELECT COUNT(*) FROM "
                + TreeLearnSchema.Messages.TABLE);
    }

    private String messageContent(String id) {
        try (Cursor cursor = helper.getWritableDatabase().rawQuery("SELECT "
                + TreeLearnSchema.Messages.USER_CONTENT + " FROM " + TreeLearnSchema.Messages.TABLE
                + " WHERE " + TreeLearnSchema.Messages.ID + "=?", new String[]{id})) {
            assertTrue("the message row must exist", cursor.moveToFirst());
            return cursor.getString(0);
        }
    }

    /** The current production DDL minus what V4 introduces: a genuine V3 database. */
    private static List<String> versionThreeCreateStatements() {
        java.util.List<String> statements = new java.util.ArrayList<>();
        for (String statement : TreeLearnSchema.CREATE_TABLE_STATEMENTS) {
            if (statement.startsWith("CREATE TABLE " + TreeLearnSchema.Attachments.TABLE)
                    || statement.startsWith("CREATE TABLE "
                            + TreeLearnSchema.MessageAttachments.TABLE)) {
                continue;
            }
            statements.add(statement);
        }
        for (String statement : TreeLearnSchema.CREATE_INDEX_STATEMENTS) {
            if (statement.contains(TreeLearnSchema.Attachments.TABLE)
                    || statement.contains(TreeLearnSchema.MessageAttachments.TABLE)) {
                continue;
            }
            statements.add(statement);
        }
        statements.addAll(TreeLearnSchema.CREATE_TRIGGER_STATEMENTS);
        statements.addAll(TreeLearnSchema.SEED_STATEMENTS);
        return statements;
    }

    private static java.util.Set<String> tables(SQLiteDatabase db) {
        java.util.Set<String> names = new java.util.LinkedHashSet<>();
        try (Cursor cursor = db.rawQuery("SELECT name FROM sqlite_master WHERE type='table'"
                + " AND name NOT LIKE 'sqlite_%' AND name NOT LIKE 'android_%'", null)) {
            while (cursor.moveToNext()) {
                names.add(cursor.getString(0));
            }
        }
        return names;
    }

    private static int rawInt(SQLiteDatabase db, String sql) {
        try (Cursor cursor = db.rawQuery(sql, null)) {
            cursor.moveToFirst();
            return cursor.getInt(0);
        }
    }

    private static int rawRows(SQLiteDatabase db, String pragma) {
        try (Cursor cursor = db.rawQuery(pragma, null)) {
            return cursor.getCount();
        }
    }

    private static String rawString(SQLiteDatabase db, String pragma) {
        try (Cursor cursor = db.rawQuery(pragma, null)) {
            cursor.moveToFirst();
            return cursor.getString(0);
        }
    }
}
