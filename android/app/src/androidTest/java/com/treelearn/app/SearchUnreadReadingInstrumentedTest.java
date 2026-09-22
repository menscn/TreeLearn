package com.treelearn.app;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.ContentValues;
import android.database.sqlite.SQLiteDatabase;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.treelearn.core.domain.AnswerRevision;
import com.treelearn.core.domain.Branch;
import com.treelearn.core.domain.GenerationModel;
import com.treelearn.core.domain.Message;
import com.treelearn.core.domain.MessageRole;
import com.treelearn.core.domain.PromptPurpose;
import com.treelearn.core.domain.ReadingAnchor;
import com.treelearn.core.domain.SearchHit;
import com.treelearn.core.domain.SearchQuery;
import com.treelearn.core.domain.Topic;
import com.treelearn.data.db.TreeLearnDatabaseHelper;
import com.treelearn.data.db.TreeLearnSchema;
import com.treelearn.data.db.dao.AnswerRevisionDao;
import com.treelearn.data.db.dao.BranchDao;
import com.treelearn.data.db.dao.MessageDao;
import com.treelearn.data.db.dao.ReadingPositionDao;
import com.treelearn.data.db.dao.SearchDao;
import com.treelearn.data.db.dao.TopicDao;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;

/**
 * T-015 acceptance evidence on real Android framework SQLite (API 36) for the search statement and
 * the reading-position columns.
 *
 * <p>What only a device can decide is decided here: whether SQLite's {@code LIKE} with an explicit
 * {@code ESCAPE} really treats a stored {@code %} or {@code _} as a character rather than a wildcard
 * (the whole reason the query is normalised in {@code :core} instead of inlined), whether a query
 * containing a quote is data rather than syntax, and whether {@code ON DELETE SET NULL} on the
 * reading-position message really clears the position instead of leaving a dangling pointer.
 *
 * <p>Every literal here is synthetic (rules/sql.md): no real question, answer or reasoning text, and
 * no API key.
 */
@RunWith(AndroidJUnit4.class)
public final class SearchUnreadReadingInstrumentedTest {

    private static final long T0 = 1_756_000_000_000L;

    /** Synthetic policy version; its body never appears here. */
    private static final String POLICY_VERSION = "tl-learning-answer/1.0.0";

    private static final String SNAPSHOT_ID = "33333333-3333-4333-8333-333333333333";

    private TreeLearnDatabaseHelper helper;

    @Before
    public void setUp() {
        helper = new TreeLearnDatabaseHelper(
                InstrumentationRegistry.getInstrumentation().getTargetContext());
        context().deleteDatabase(TreeLearnDatabaseHelper.DATABASE_NAME);
    }

    @After
    public void tearDown() {
        if (helper != null) {
            helper.close();
        }
        context().deleteDatabase(TreeLearnDatabaseHelper.DATABASE_NAME);
    }

    private static android.content.Context context() {
        return InstrumentationRegistry.getInstrumentation().getTargetContext();
    }

    // ------------------------------------------------------------------
    // Search.
    // ------------------------------------------------------------------

    @Test
    public void chineseSubstringsMatchTitleAnswerAndReasoning() {
        SQLiteDatabase db = helper.getWritableDatabase();
        Branch branch = branchWithTitle(db, "细胞的能量供应");
        writeStableAnswer(db, branch, 0, "线粒体是细胞的能量工厂", "先看结构与膜间隙");

        assertArrayEquals(new String[]{"TITLE", "ANSWER"},
                fields(SearchDao.search(db, SearchQuery.of("细胞"))));
        assertArrayEquals(new String[]{"REASONING"},
                fields(SearchDao.search(db, SearchQuery.of("膜间隙"))));
        assertArrayEquals(new String[]{"ANSWER"},
                fields(SearchDao.search(db, SearchQuery.of("能量工厂"))));
        assertTrue(SearchDao.search(db, SearchQuery.of("不存在的词")).isEmpty());
    }

    @Test
    public void learnerQuestionsAreSearchableAndKeepTheirMessageAnchor() {
        SQLiteDatabase db = helper.getWritableDatabase();
        Branch branch = branchWithTitle(db, "问题臂主题");
        Message question = Message.user(branch.id, 0, "我问过的约束条件", "", T0);
        MessageDao.insert(db, question);

        List<SearchHit> hits = SearchDao.search(db, SearchQuery.of("约束条件"));

        assertEquals(1, hits.size());
        assertEquals(SearchHit.Field.QUESTION, hits.get(0).field);
        assertEquals(branch.id, hits.get(0).branchId);
        assertEquals(question.id, hits.get(0).messageId);
    }

    @Test
    public void learnerQuestionsInArchivedBranchesAreNotSearchable() {
        SQLiteDatabase db = helper.getWritableDatabase();
        Branch branch = branchWithTitle(db, "问题归档主题");
        MessageDao.insert(db, Message.user(branch.id, 0, "归档后不应命中", "", T0));
        branch.archive(T0);
        BranchDao.update(db, branch);

        assertTrue(SearchDao.search(db, SearchQuery.of("归档后不应命中")).isEmpty());
    }

    @Test
    public void aStoredPercentIsACharacterNotAWildcard() {
        SQLiteDatabase db = helper.getWritableDatabase();
        Branch literal = branchWithTitle(db, "命中率 100% 的样例");
        Branch decoy = branchWithTitle(db, "命中率 1000 的样例");
        assertNotNull("the decoy must really be in the store for the negative case to mean anything",
                BranchDao.findById(db, decoy.id));

        List<SearchHit> hits = SearchDao.search(db, SearchQuery.of("100%"));

        assertEquals("the stored % must be matched literally, or this would also match 1000",
                1, hits.size());
        assertEquals(literal.id, hits.get(0).branchId);
    }

    @Test
    public void aStoredUnderscoreIsACharacterNotAWildcard() {
        SQLiteDatabase db = helper.getWritableDatabase();
        Branch literal = branchWithTitle(db, "命名 a_b 的样例");
        Branch decoy = branchWithTitle(db, "命名 axb 的样例");
        assertNotNull(decoy);

        List<SearchHit> hits = SearchDao.search(db, SearchQuery.of("a_b"));

        assertEquals(1, hits.size());
        assertEquals(literal.id, hits.get(0).branchId);
    }

    @Test
    public void aQuoteInTheQueryIsDataNotSyntax() {
        SQLiteDatabase db = helper.getWritableDatabase();
        branchWithTitle(db, "引号样例");

        // If the pattern were spliced into the statement, this would either throw or match every
        // row. Bound as a parameter it simply finds nothing.
        assertTrue(SearchDao.search(db, SearchQuery.of("' OR '1'='1")).isEmpty());
    }

    @Test
    public void anExpiredRevisionIsNotReturnedWithoutTheForkThatPinsIt() {
        SQLiteDatabase db = helper.getWritableDatabase();
        Branch branch = branchWithTitle(db, "合成标题");
        Message position = Message.ai(branch.id, 0, T0);
        MessageDao.insert(db, position);

        AnswerRevision first = revision(position.id, 1, "旧回答含 TOKEN_A", "");
        AnswerRevisionDao.insert(db, first);
        AnswerRevision second = revision(position.id, 2, "新回答不含标记", "");
        AnswerRevisionDao.insert(db, second);
        MessageDao.updateActiveReferences(db, withActiveRevision(position, second.id));

        // The first revision is still in the table and still matches, but nothing displays it and no
        // branch forked from it: returning it would answer the same question with a sentence the
        // learner can no longer reach.
        List<SearchHit> hits = SearchDao.search(db, SearchQuery.of("TOKEN_A"));
        assertEquals(0, hits.size());

        forkFrom(db, branch, position.id, first.id, "旧回答含 TOKEN_A");
        List<SearchHit> viaFork = SearchDao.search(db, SearchQuery.of("TOKEN_A"));
        assertEquals(1, viaFork.size());
        assertEquals("ANCHOR", viaFork.get(0).field.name());
    }

    @Test
    public void archivedBranchesAreNotSearchable() {
        SQLiteDatabase db = helper.getWritableDatabase();
        Branch branch = branchWithTitle(db, "合成归档标题");
        branch.archive(T0);
        BranchDao.update(db, branch);

        assertTrue(SearchDao.search(db, SearchQuery.of("合成归档标题")).isEmpty());
    }

    @Test
    public void resultsComeBackInDisplayOrder() {
        SQLiteDatabase db = helper.getWritableDatabase();
        Branch first = branchWithTitle(db, "合成排序标题");
        writeStableAnswer(db, first, 0, "合成排序正文", "");
        Branch second = branchWithTitle(db, "合成排序标题二");
        writeStableAnswer(db, second, 0, "合成排序正文", "");

        List<SearchHit> hits = SearchDao.search(db, SearchQuery.of("合成排序"));

        assertEquals(4, hits.size());
        for (int i = 1; i < hits.size(); i++) {
            assertTrue("the DAO must return the hits in its own total order",
                    SearchHit.DISPLAY_ORDER.compare(hits.get(i - 1), hits.get(i)) <= 0);
        }
        assertEquals("TITLE", hits.get(0).field.name());
    }

    // ------------------------------------------------------------------
    // Reading position.
    // ------------------------------------------------------------------

    @Test
    public void aReadingPositionRoundTripsThroughItsFourColumns() {
        SQLiteDatabase db = helper.getWritableDatabase();
        Branch branch = branchWithTitle(db, "合成阅读标题");
        List<Message> messages = new ArrayList<Message>();
        for (int ordinal = 0; ordinal < 3; ordinal++) {
            Message message = Message.ai(branch.id, ordinal, T0);
            MessageDao.insert(db, message);
            messages.add(message);
        }
        ReadingAnchor written = ReadingAnchor.of(messages.get(1).id, null, 23, 8);

        ReadingPositionDao.write(db, branch.id, written);
        ReadingAnchor readBack = ReadingPositionDao.read(db, branch.id);

        assertNotNull(readBack);
        assertEquals(written.messageId, readBack.messageId);
        assertNull(readBack.revisionId);
        assertEquals(23, readBack.listItemOffset);
        assertEquals(8, readBack.viewportOffset);
    }

    @Test
    public void deletingTheMessageClearsThePositionInsteadOfLeavingADanglingOne() {
        SQLiteDatabase db = helper.getWritableDatabase();
        Branch branch = branchWithTitle(db, "合成清理标题");
        Message message = Message.ai(branch.id, 0, T0);
        MessageDao.insert(db, message);
        ReadingPositionDao.write(db, branch.id, ReadingAnchor.of(message.id, null, 5, 5));

        db.delete(TreeLearnSchema.Messages.TABLE, TreeLearnSchema.Messages.ID + "=?",
                new String[]{message.id});

        assertNull("the store clears the pointer, so the session must open at the live end",
                ReadingPositionDao.read(db, branch.id));
    }

    @Test
    public void clearingThePositionEmptiesAllFourColumns() {
        SQLiteDatabase db = helper.getWritableDatabase();
        Branch branch = branchWithTitle(db, "合成清空标题");
        Message message = Message.ai(branch.id, 0, T0);
        MessageDao.insert(db, message);
        ReadingPositionDao.write(db, branch.id, ReadingAnchor.atMessage(message.id, null));

        ReadingPositionDao.clear(db, branch.id);

        assertNull(ReadingPositionDao.read(db, branch.id));
        assertFalse("a cleared position must not keep an offset behind",
                columnHasValue(db, branch.id, TreeLearnSchema.Branches.READING_LIST_ITEM_OFFSET));
    }

    // ------------------------------------------------------------------
    // Fixtures.
    // ------------------------------------------------------------------

    private Branch branchWithTitle(SQLiteDatabase db, String title) {
        Topic topic = Topic.create(T0);
        TopicDao.insert(db, topic);
        Branch branch = Branch.root(topic.id, T0);
        branch.setTitle(title, T0);
        BranchDao.insert(db, branch);
        return branch;
    }

    private void writeStableAnswer(SQLiteDatabase db, Branch branch, int ordinal, String answer,
            String reasoning) {
        Message position = Message.ai(branch.id, ordinal, T0);
        MessageDao.insert(db, position);
        AnswerRevision revision = revision(position.id, 1, answer, reasoning);
        AnswerRevisionDao.insert(db, revision);
        MessageDao.updateActiveReferences(db, withActiveRevision(position, revision.id));
    }

    private static AnswerRevision revision(String messageId, int revisionNo, String answer,
            String reasoning) {
        return AnswerRevision.create(messageId, revisionNo, reasoning, answer, GenerationModel.FLASH,
                false, POLICY_VERSION, T0).value();
    }

    private static Message withActiveRevision(Message position, String revisionId) {
        return Message.restore(position.id, position.branchId, MessageRole.AI, position.ordinal,
                null, position.createdAt, revisionId, null);
    }

    /**
     * Inserts the row a real fork produces: a child branch whose anchor quotes a source revision.
     *
     * <p>Written with the schema constants rather than through {@code BranchCreation} because this
     * case is about what the <em>search</em> statement can reach, not about the creation path (which
     * has its own instrumented suite); the shape is the one that path produces, including the birth
     * snapshot the row must point at.
     */
    private static void forkFrom(SQLiteDatabase db, Branch parent, String sourceMessageId,
            String sourceRevisionId, String quote) {
        ContentValues snapshot = new ContentValues();
        snapshot.put(TreeLearnSchema.ContextSnapshots.ID, SNAPSHOT_ID);
        snapshot.put(TreeLearnSchema.ContextSnapshots.BRANCH_ID, parent.id);
        snapshot.put(TreeLearnSchema.ContextSnapshots.SCHEMA_VERSION, 1);
        snapshot.put(TreeLearnSchema.ContextSnapshots.DIGEST, "synthetic-digest");
        snapshot.put(TreeLearnSchema.ContextSnapshots.PAYLOAD, "{}");
        snapshot.put(TreeLearnSchema.ContextSnapshots.CREATED_AT, T0);
        db.insertOrThrow(TreeLearnSchema.ContextSnapshots.TABLE, null, snapshot);

        ContentValues child = new ContentValues();
        child.put(TreeLearnSchema.Branches.ID, "44444444-4444-4444-8444-444444444444");
        child.put(TreeLearnSchema.Branches.TOPIC_ID, parent.topicId);
        child.put(TreeLearnSchema.Branches.PARENT_ID, parent.id);
        child.put(TreeLearnSchema.Branches.TITLE, "合成分叉标题");
        child.put(TreeLearnSchema.Branches.CREATED_AT, T0);
        child.put(TreeLearnSchema.Branches.UPDATED_AT, T0);
        child.put(TreeLearnSchema.Branches.SOURCE_MESSAGE_ID, sourceMessageId);
        child.put(TreeLearnSchema.Branches.SOURCE_REVISION_ID, sourceRevisionId);
        child.put(TreeLearnSchema.Branches.ANCHOR_START_INCLUSIVE, 0);
        child.put(TreeLearnSchema.Branches.ANCHOR_END_EXCLUSIVE, quote.length());
        child.put(TreeLearnSchema.Branches.ANCHOR_QUOTE, quote);
        child.put(TreeLearnSchema.Branches.ANCHOR_HASH, "synthetic-hash");
        child.put(TreeLearnSchema.Branches.CONTEXT_SNAPSHOT_ID, SNAPSHOT_ID);
        db.insertOrThrow(TreeLearnSchema.Branches.TABLE, null, child);
    }

    private static boolean columnHasValue(SQLiteDatabase db, String branchId, String column) {
        try (android.database.Cursor cursor = db.query(TreeLearnSchema.Branches.TABLE,
                new String[]{column}, TreeLearnSchema.Branches.ID + "=?", new String[]{branchId},
                null, null, null)) {
            return cursor.moveToFirst() && !cursor.isNull(0);
        }
    }

    private static String[] fields(List<SearchHit> hits) {
        String[] fields = new String[hits.size()];
        for (int i = 0; i < hits.size(); i++) {
            fields[i] = hits.get(i).field.name();
        }
        return fields;
    }
}
