package com.treelearn.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.treelearn.app.files.AttachmentStore;
import com.treelearn.core.domain.Branch;
import com.treelearn.core.domain.Message;
import com.treelearn.core.domain.Topic;
import com.treelearn.data.db.TreeLearnDatabaseHelper;
import com.treelearn.data.db.dao.BranchDao;
import com.treelearn.data.db.dao.MessageDao;
import com.treelearn.data.db.dao.TopicDao;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * T-098: the orphan lifecycle. Orphan judgement is {@link AttachmentStore#orphans}'s single
 * decision — with foreign keys on and {@code message_id} cascading its reference rows away, "not
 * in the reference table" is provably the same as "zero references AND no message row", so the
 * task's double-confirmation rule lives in exactly one SQL statement. Cleanup is idempotent and
 * never touches a still-referenced attachment or any message text.
 */
@RunWith(AndroidJUnit4.class)
public final class AttachmentOrphanCleanupInstrumentedTest {

    private static final long T0 = 1_756_000_000_000L;

    /** One distinct body per registration: content-hash dedup would merge shared bytes. */
    private static byte[] bytes(String tag) {
        return ("SYNTHETIC-ORPHAN-" + tag).getBytes();
    }

    private Context context;
    private TreeLearnDatabaseHelper helper;
    private AttachmentStore store;
    private String rootBranchId;
    private String messageId;
    private String siblingBranchId;
    private String siblingMessageId;

    @Before
    public void setUp() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        context.deleteDatabase(TreeLearnDatabaseHelper.DATABASE_NAME);
        helper = new TreeLearnDatabaseHelper(context);
        store = new AttachmentStore(helper, new File(context.getFilesDir(), "attachments"));
        rootBranchId = seedBranchWithMessage("7c7c7c7c-0000-4000-8000-0000000000e1",
                "SYNTHETIC-ORPHAN-ROOT");
        messageId = lastSeededMessageId;
        siblingBranchId = seedBranchWithMessage("7c7c7c7c-0000-4000-8000-0000000000e2",
                "SYNTHETIC-ORPHAN-SIBLING");
        siblingMessageId = lastSeededMessageId;
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


    @Test
    public void anUnreferencedAttachmentIsMarkedAndAReferencedOneIsSpared() {
        String referenced = store.register("SYNTHETIC-IN-USE.txt", "text/plain", bytes("A"), T0);
        String orphan = store.register("SYNTHETIC-ORPHAN.txt", "text/plain", bytes("B"),
                T0 + 1_000L);
        store.link(messageId, referenced, T0 + 2_000L);

        Set<String> orphans = new HashSet<>(store.orphans());

        assertTrue("the unreferenced record is the orphan", orphans.contains(orphan));
        assertFalse("the referenced record is never an orphan", orphans.contains(referenced));
    }

    @Test
    public void deletingTheSubtreeTurnsItsAttachmentIntoAnOrphanWhileTheSiblingSurvives() {
        String doomed = store.register("SYNTHETIC-DOOMED.txt", "text/plain", bytes("A"), T0);
        String survivor = store.register("SYNTHETIC-SURVIVOR.txt", "text/plain", bytes("B"),
                T0 + 1_000L);
        store.link(messageId, doomed, T0 + 2_000L);
        store.link(siblingMessageId, survivor, T0 + 3_000L);

        // The real subtree delete (T-053's entry point): the branch row goes, messages and their
        // reference rows follow the foreign keys.
        BranchDao.delete(helper.getWritableDatabase(), rootBranchId);

        Set<String> orphans = new HashSet<>(store.orphans());
        assertTrue("the deleted subtree's attachment is now the orphan", orphans.contains(doomed));
        assertFalse("the sibling branch's attachment stays in use", orphans.contains(survivor));
        assertTrue("the survivor's record is intact", listHolds(survivor));
    }

    @Test
    public void cleanupRemovesRecordAndFileAndIsIdempotent() {
        String referenced = store.register("SYNTHETIC-KEPT.txt", "text/plain", bytes("A"), T0);
        String orphan = store.register("SYNTHETIC-CLEANED.txt", "text/plain", bytes("B"),
                T0 + 1_000L);
        store.link(messageId, referenced, T0 + 2_000L);
        Set<String> targets = new HashSet<>(Arrays.asList(orphan));
        assertEquals("two registrations, two physical files", 2, physicalFileCount());

        store.cleanupOrphans(targets);
        assertFalse("the orphan's record is gone", listHolds(orphan));
        assertEquals("only the referenced file remains", 1, physicalFileCount());
        assertTrue("the referenced record is untouched", listHolds(referenced));

        // Idempotency: a repeated cleanup over the same (now absent) id changes nothing and does
        // not throw — a concurrent delete or a double tap must stay safe.
        store.cleanupOrphans(targets);
        assertEquals("the second pass still leaves the referenced record", 1, store.list().size());
        assertEquals("and still its physical file", 1, physicalFileCount());
    }

    private boolean listHolds(String attachmentId) {
        for (AttachmentStore.Entry entry : store.list()) {
            if (entry.attachmentId.equals(attachmentId)) {
                return true;
            }
        }
        return false;
    }

    private int physicalFileCount() {
        File[] files = store.directory().listFiles();
        return files == null ? 0 : files.length;
    }

    private String lastSeededMessageId;

    /** One topic per branch: the root uniqueness constraint is per topic. */
    private String seedBranchWithMessage(String topicId, String title) {
        SQLiteDatabase db = helper.getWritableDatabase();
        TopicDao.insert(db, new Topic(topicId, null, T0, T0));
        Branch root = Branch.root(topicId, T0);
        BranchDao.insert(db, root);
        root.setTitle(title, T0 + 1L);
        BranchDao.update(db, root);
        Message message = Message.user(root.id, 0, "SYNTHETIC-ORPHAN-QUESTION", T0 + 2_000L);
        MessageDao.insert(db, message);
        lastSeededMessageId = message.id;
        return root.id;
    }
}
