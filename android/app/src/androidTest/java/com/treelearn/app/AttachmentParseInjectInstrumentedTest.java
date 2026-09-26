package com.treelearn.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.treelearn.app.files.AttachmentStore;
import com.treelearn.app.files.AttachmentTextExtractor;
import com.treelearn.app.wiring.SqliteChatStore;
import com.treelearn.core.domain.Branch;
import com.treelearn.core.domain.ContextPlan;
import com.treelearn.core.domain.Message;
import com.treelearn.core.domain.Topic;
import com.treelearn.ai.prompt.DeepSeekContextEncoder;
import com.treelearn.data.db.DatabaseTransactionRunner;
import com.treelearn.data.db.TreeLearnDatabaseHelper;
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
 * T-097 acceptance evidence on real Android framework SQLite: register, parse, cached text,
 * the persisted message reference, and the rebuilt history carrying the attachment block.
 *
 * <p>Holds no real user content: every literal is synthetic (rules/sql.md).
 */
@RunWith(AndroidJUnit4.class)
public final class AttachmentParseInjectInstrumentedTest {

    private static final long T0 = 1_756_000_000_000L;

    private Context context;
    private TreeLearnDatabaseHelper helper;
    private AttachmentStore store;
    private SqliteChatStore chatStore;
    private String branchId;
    private String messageId;

    @Before
    public void setUp() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        context.deleteDatabase(TreeLearnDatabaseHelper.DATABASE_NAME);
        helper = new TreeLearnDatabaseHelper(context);
        store = new AttachmentStore(helper, new File(context.getFilesDir(), "attachments"));
        chatStore = new SqliteChatStore(helper, new DatabaseTransactionRunner());
        branchId = seedTopicWithRootAndMessage();
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
    // 1. Parse outcome lands in the record; the cached text is the one readers use.
    // ------------------------------------------------------------------

    @Test
    public void aTextFileIsParsedAndItsTextIsCachedOnTheRecord() {
        String id = store.register("SYNTHETIC-NOTES.txt", "text/plain",
                "SYNTHETIC-PARAGRAPH-ONE".getBytes(StandardCharsets.UTF_8), T0);

        store.finalizeParse(id, T0 + 1_000L);

        AttachmentStore.Parsed parsed = store.parseOutcome(id);
        assertEquals("a txt file parses", AttachmentStore.PARSE_STATE_PARSED, parsed.state);
        assertEquals("the cached text is the file's decoded text",
                "SYNTHETIC-PARAGRAPH-ONE", parsed.text);
    }

    @Test
    public void aPdfTextLayerIsExtractedIntoTheCache() {
        String id = store.register("SYNTHETIC-LECTURE.pdf", "application/pdf",
                minimalPdfWithText("SYNTHETIC-PDF-SENTENCE"), T0);

        store.finalizeParse(id, T0 + 1_000L);

        AttachmentStore.Parsed parsed = store.parseOutcome(id);
        assertEquals("the PDF text layer parses", AttachmentStore.PARSE_STATE_PARSED, parsed.state);
        assertNotNull(parsed.text);
        assertTrue("the extracted layer carries the PDF's own sentence: " + parsed.text,
                parsed.text.contains("SYNTHETIC-PDF-SENTENCE"));
    }

    @Test
    public void anUnparsableFileIsMarkedFailedAndNeverBlocksAnything() {
        String id = store.register("SYNTHETIC-BROKEN.pdf", "application/pdf",
                "this is not a pdf at all".getBytes(StandardCharsets.UTF_8), T0);

        store.finalizeParse(id, T0 + 1_000L);

        AttachmentStore.Parsed parsed = store.parseOutcome(id);
        assertEquals("garbage bytes fail the parse", AttachmentStore.PARSE_STATE_FAILED,
                parsed.state);
        assertNull("a failed parse caches no text", parsed.text);
    }

    // ------------------------------------------------------------------
    // 2. The sent message persists the reference; the rebuilt history re-reads the cache.
    // ------------------------------------------------------------------

    @Test
    public void aLinkedMessageRebuildsItsTurnWithTheAttachmentBlock() {
        String id = store.register("SYNTHETIC-CONTEXT.txt", "text/plain",
                "SYNTHETIC-ATTACHMENT-CONTEXT".getBytes(StandardCharsets.UTF_8), T0);
        store.finalizeParse(id, T0 + 1_000L);
        store.link(messageId, id, T0 + 2_000L);

        List<ContextPlan.Entry> entries = chatStore.history(branchId, 2);

        assertEquals("the turn's user message is in the plan", 1, entries.size());
        assertEquals(ContextPlan.EntryKind.USER_MESSAGE, entries.get(0).kind);
        String rebuilt = entries.get(0).userText;
        assertTrue("the rebuilt turn still asks the question: " + rebuilt,
                rebuilt.contains("SYNTHETIC-ATTACH-QUESTION"));
        assertTrue("the rebuilt turn carries the attachment block: " + rebuilt,
                rebuilt.contains(DeepSeekContextEncoder.ATTACH_OPEN));
        assertTrue("the attachment text rides inside the block: " + rebuilt,
                rebuilt.contains("SYNTHETIC-ATTACHMENT-CONTEXT"));
    }

    @Test
    public void aMessageWithoutAttachmentsRebuildsExactlyAsBefore() {
        List<ContextPlan.Entry> entries = chatStore.history(branchId, 2);

        assertEquals(1, entries.size());
        assertEquals("SYNTHETIC-ATTACH-QUESTION", entries.get(0).userText);
    }

    // ------------------------------------------------------------------
    // Fixtures.
    // ------------------------------------------------------------------

    private String seedTopicWithRootAndMessage() {
        String topicId = java.util.UUID.randomUUID().toString();
        TopicDao.insert(helper.getWritableDatabase(), new Topic(topicId, null, T0, T0));
        Branch root = Branch.root(topicId, T0);
        BranchDao.insert(helper.getWritableDatabase(), root);
        root.setTitle("SYNTHETIC-ATTACH-PARSE-TOPIC", T0 + 1L);
        BranchDao.update(helper.getWritableDatabase(), root);
        Message message = Message.user(root.id, 0, "SYNTHETIC-ATTACH-QUESTION", T0 + 2_000L);
        MessageDao.insert(helper.getWritableDatabase(), message);
        messageId = message.id;
        return root.id;
    }

    /** A hand-built single-page PDF whose content stream writes one literal string with Tj. */
    private static byte[] minimalPdfWithText(String sentence) {
        String stream = "BT /F1 12 Tf 72 720 Td (" + sentence + ") Tj ET";
        String pdf = "%PDF-1.4\n"
                + "1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n"
                + "2 0 obj\n<< /Type /Pages /Kids [3 0 R] /Count 1 >>\nendobj\n"
                + "3 0 obj\n<< /Type /Page /Parent 2 0 R /Contents 4 0 R >>\nendobj\n"
                + "4 0 obj\n<< /Length " + stream.length() + " >>\nstream\n"
                + stream + "\nendstream\nendobj\n"
                + "trailer\n<< /Root 1 0 R >>\n%%EOF\n";
        return pdf.getBytes(StandardCharsets.US_ASCII);
    }
}
