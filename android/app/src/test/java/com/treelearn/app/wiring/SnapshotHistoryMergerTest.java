package com.treelearn.app.wiring;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.treelearn.core.domain.AnswerRevision;
import com.treelearn.core.domain.ContextPlan;
import com.treelearn.core.domain.ContextSnapshot;
import com.treelearn.core.domain.DomainErrorCode;
import com.treelearn.core.domain.DomainResult;
import com.treelearn.core.domain.Message;
import com.treelearn.data.db.TreeLearnSchema;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * T-064 JVM half: a forked branch's request history opens with its birth snapshot's sources, in the
 * order the fork pinned them, followed by the branch's own rows (PRD §6.2.4 point 2).
 *
 * <p>The merger is the pure type that turns snapshot references into {@link ContextPlan.Entry}
 * values; the store only reads the row and hands it over. A birth context that cannot be resolved
 * row by row refuses the whole plan instead of being silently shortened.
 */
public class SnapshotHistoryMergerTest {

    private static final String CHILD_BRANCH_ID = "a1a1a1a1-a1a1-4a1a-8a1a-a1a1a1a1a1a1";
    private static final String MISSING_ROW_ID = "91919191-9191-4191-8191-919191919191";
    private static final String SUMMARY_ID = "52525252-5252-4252-8252-525252525252";
    private static final String OWN_QUESTION_ID = "44444444-4444-4444-8444-444444444444";

    // ------------------------------------------------------------- the merged history

    @Test
    public void historyOfForkedBranchPrependsSnapshotSources() {
        Message pinnedQuestion = Message.user(WiringDoubles.BRANCH_ID, 0,
                WiringDoubles.FIRST_QUESTION, WiringDoubles.NOW);
        AnswerRevision pinnedAnswer = revision(WiringDoubles.ANSWER_ID, "the reasoning",
                "the answer");
        ContextSnapshot snapshot = ContextSnapshot.create(CHILD_BRANCH_ID, Arrays.asList(
                ContextSnapshot.Source.userMessage(pinnedQuestion.id),
                ContextSnapshot.Source.stableRevision(pinnedAnswer.id)), WiringDoubles.NOW);
        StoredRows stored = new StoredRows();
        stored.messages.put(pinnedQuestion.id, pinnedQuestion);
        stored.revisions.put(pinnedAnswer.id, pinnedAnswer);

        DomainResult<List<ContextPlan.Entry>> merged = SnapshotHistoryMerger.merge(snapshot,
                ownRows(), stored);

        assertTrue(merged.isOk());
        List<ContextPlan.Entry> entries = merged.value();
        assertEquals(3, entries.size());
        // The snapshot's sources come first, in the order the fork pinned them.
        assertEquals(ContextPlan.EntryKind.USER_MESSAGE, entries.get(0).kind);
        assertEquals(pinnedQuestion.id, entries.get(0).refId);
        assertEquals(WiringDoubles.FIRST_QUESTION, entries.get(0).userText);
        assertEquals(ContextPlan.EntryKind.STABLE_REVISION, entries.get(1).kind);
        assertEquals(pinnedAnswer.id, entries.get(1).refId);
        assertEquals("the reasoning", entries.get(1).reasoningText);
        assertEquals("the answer", entries.get(1).answerText);
        // The branch's own rows follow, untouched.
        assertEquals(ContextPlan.EntryKind.USER_MESSAGE, entries.get(2).kind);
        assertEquals(OWN_QUESTION_ID, entries.get(2).refId);
        assertEquals(WiringDoubles.LATER_QUESTION, entries.get(2).userText);
    }

    @Test
    public void rootBranchWithoutSnapshotKeepsRowsOnly() {
        List<ContextPlan.Entry> ownRows = ownRows();

        DomainResult<List<ContextPlan.Entry>> merged = SnapshotHistoryMerger.merge(null, ownRows,
                new StoredRows());

        assertTrue(merged.isOk());
        // A branch with no birth snapshot reads exactly as it did before this change: the rows and
        // nothing else - same count, same order, same kinds.
        assertEquals(1, merged.value().size());
        assertEquals(ContextPlan.EntryKind.USER_MESSAGE, merged.value().get(0).kind);
        assertEquals(OWN_QUESTION_ID, merged.value().get(0).refId);
        assertEquals(WiringDoubles.LATER_QUESTION, merged.value().get(0).userText);
    }

    @Test
    public void missingSnapshotRowRefusesPlan() {
        ContextSnapshot pinningAMissingQuestion = ContextSnapshot.create(CHILD_BRANCH_ID,
                Arrays.asList(ContextSnapshot.Source.userMessage(MISSING_ROW_ID)),
                WiringDoubles.NOW);
        ContextSnapshot pinningAMissingRevision = ContextSnapshot.create(CHILD_BRANCH_ID,
                Arrays.asList(ContextSnapshot.Source.stableRevision(MISSING_ROW_ID)),
                WiringDoubles.NOW);

        DomainResult<List<ContextPlan.Entry>> missingQuestion = SnapshotHistoryMerger.merge(
                pinningAMissingQuestion, ownRows(), new StoredRows());
        DomainResult<List<ContextPlan.Entry>> missingRevision = SnapshotHistoryMerger.merge(
                pinningAMissingRevision, ownRows(), new StoredRows());

        // The upstream background is the reason the branch exists: a plan that silently dropped it
        // would send the model a question with no context at all, which is the defect this task
        // fixes. So the whole plan is refused, never shortened.
        assertTrue(missingQuestion.isFailure());
        assertEquals(DomainErrorCode.CONTEXT_SOURCE_MISSING, missingQuestion.error().code);
        assertTrue(missingRevision.isFailure());
        assertEquals(DomainErrorCode.CONTEXT_SOURCE_MISSING, missingRevision.error().code);
    }

    @Test
    public void aSummarySourceIsRefusedUntilTheDomainGrowsAReader() {
        // Defensive branch: the fork writes only user/revision sources today, and no read path can
        // produce a summary's content yet (T-007 deferral). Refusing beats inventing content.
        ContextSnapshot snapshot = ContextSnapshot.create(CHILD_BRANCH_ID, Arrays.asList(
                ContextSnapshot.Source.summary(SUMMARY_ID)), WiringDoubles.NOW);

        DomainResult<List<ContextPlan.Entry>> merged = SnapshotHistoryMerger.merge(snapshot,
                ownRows(), new StoredRows());

        assertTrue(merged.isFailure());
        assertEquals(DomainErrorCode.CONTEXT_SUMMARY_MISSING, merged.error().code);
    }

    /**
     * 口径钉死（任务约束 ②）：快照前置 entry 是普通的 {@link ContextPlan.Entry}
     * （USER_MESSAGE / STABLE_REVISION），不引入新类别、不走 SUMMARY，因此
     * {@code ChatWiring.historyForRequest} 的「最近十轮」压缩按原样把它们计入本分支的
     * 轮次统计，两处口径不会漂移。
     */
    @Test
    public void snapshotEntriesAreOrdinaryPlanEntriesSoTheTenRoundCompressionCountsThem() {
        AnswerRevision pinnedAnswer = revision(WiringDoubles.ANSWER_ID, "", "the answer");
        ContextSnapshot snapshot = ContextSnapshot.create(CHILD_BRANCH_ID, Arrays.asList(
                ContextSnapshot.Source.stableRevision(pinnedAnswer.id)), WiringDoubles.NOW);
        StoredRows stored = new StoredRows();
        stored.revisions.put(pinnedAnswer.id, pinnedAnswer);

        DomainResult<List<ContextPlan.Entry>> merged = SnapshotHistoryMerger.merge(snapshot,
                new ArrayList<ContextPlan.Entry>(), stored);

        assertTrue(merged.isOk());
        for (ContextPlan.Entry entry : merged.value()) {
            assertTrue(entry.kind == ContextPlan.EntryKind.USER_MESSAGE
                    || entry.kind == ContextPlan.EntryKind.STABLE_REVISION);
        }
    }

    // ------------------------------------------------------------- statement-level assertions

    /**
     * The store reads the snapshot row back through {@code ContextSnapshotDao.findByBranchId} and
     * trusts what it returns. What makes that trust warranted is the schema's own shape: one row per
     * branch (unique index), and a row nobody can edit or delete behind the reader's back
     * (immutability triggers). This is the SearchDaoStatementTest pattern: the statement shape is
     * proven here; real execution stays with the instrumented suite (rules/sql.md).
     */
    @Test
    public void theSnapshotReadStandsOnASchemaThatGuaranteesOneIntactRowPerBranch() {
        String table = statementContaining(TreeLearnSchema.CREATE_TABLE_STATEMENTS,
                "CREATE TABLE " + TreeLearnSchema.ContextSnapshots.TABLE + " (");
        assertTrue("the snapshot row hangs off its branch with a cascade FK",
                table.contains(TreeLearnSchema.ContextSnapshots.BRANCH_ID + " TEXT NOT NULL REFERENCES "
                        + TreeLearnSchema.Branches.TABLE));
        assertTrue("a payload without its digest column could not be verified on read",
                table.contains(TreeLearnSchema.ContextSnapshots.DIGEST + " TEXT NOT NULL"));

        String index = statementContaining(TreeLearnSchema.CREATE_INDEX_STATEMENTS,
                TreeLearnSchema.INDEX_SNAPSHOTS_BRANCH);
        assertTrue(index.contains("CREATE UNIQUE INDEX"));
        assertTrue(index.contains("ON " + TreeLearnSchema.ContextSnapshots.TABLE
                + "(" + TreeLearnSchema.ContextSnapshots.BRANCH_ID + ")"));

        String immutable = statementContaining(TreeLearnSchema.CREATE_TRIGGER_STATEMENTS,
                TreeLearnSchema.TRIGGER_SNAPSHOTS_IMMUTABLE);
        assertTrue(immutable.contains("BEFORE UPDATE ON " + TreeLearnSchema.ContextSnapshots.TABLE));
        String noDirectDelete = statementContaining(TreeLearnSchema.CREATE_TRIGGER_STATEMENTS,
                TreeLearnSchema.TRIGGER_SNAPSHOTS_NO_DIRECT_DELETE);
        assertTrue(noDirectDelete.contains("BEFORE DELETE ON " + TreeLearnSchema.ContextSnapshots.TABLE));
    }

    // ------------------------------------------------------------- helpers

    /** The child branch's own first question, as the store's row loop would build it. */
    private static List<ContextPlan.Entry> ownRows() {
        List<ContextPlan.Entry> rows = new ArrayList<ContextPlan.Entry>();
        rows.add(ContextPlan.Entry.userMessage(OWN_QUESTION_ID, WiringDoubles.LATER_QUESTION));
        return rows;
    }

    private static AnswerRevision revision(String messageId, String reasoning, String answer) {
        DomainResult<AnswerRevision> created = AnswerRevision.create(messageId, 1, reasoning,
                answer, WiringDoubles.MODEL, false, AnswerPolicy.version(), WiringDoubles.NOW);
        assertTrue(created.isOk());
        return created.value();
    }

    private static String statementContaining(List<String> statements, String needle) {
        for (String statement : statements) {
            if (statement.contains(needle)) {
                return statement;
            }
        }
        throw new AssertionError("no schema statement contains: " + needle);
    }

    /** The rows a test stored, answered from maps; everything absent reads as missing. */
    private static final class StoredRows implements SnapshotHistoryMerger.Rows {

        final Map<String, Message> messages = new HashMap<String, Message>();
        final Map<String, AnswerRevision> revisions = new HashMap<String, AnswerRevision>();

        @Override
        public Message message(String messageId) {
            return messages.get(messageId);
        }

        @Override
        public AnswerRevision revision(String revisionId) {
            return revisions.get(revisionId);
        }
    }
}
