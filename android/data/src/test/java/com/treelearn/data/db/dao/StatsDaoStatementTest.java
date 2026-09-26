package com.treelearn.data.db.dao;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.treelearn.data.db.TreeLearnSchema;

import org.junit.Test;

/**
 * T-2026-09-23-100 acceptance, JVM half: the statement shapes of the read-only stats queries.
 *
 * <p>This suite deliberately does not execute SQL — real execution on Android SQLite stays with
 * the device rounds (rules/sql.md). What the JVM can pin is the shape: the three queries the
 * single stats entry point reads, which table each touches, and that the question count is the
 * {@code USER} rows rather than every row.
 */
public class StatsDaoStatementTest {

    @Test
    public void answerTimestampsComeFromTheStableRevisionsTable() {
        assertTrue(StatsDao.ANSWER_TIMESTAMPS_STATEMENT.contains("answer_revisions"));
        assertTrue(StatsDao.ANSWER_TIMESTAMPS_STATEMENT.contains(
                TreeLearnSchema.AnswerRevisions.CREATED_AT));
    }

    @Test
    public void theQuestionCountIsTheUserRowsOnly() {
        assertTrue("a question is a USER row; counting every row would inflate the total",
                StatsDao.QUESTION_COUNT_STATEMENT.contains(
                        TreeLearnSchema.Messages.ROLE + "='USER'"));
        assertTrue(StatsDao.QUESTION_COUNT_STATEMENT.contains("COUNT(*)"));
    }

    @Test
    public void theMessageCountCoversEveryRowOfTheMessagesTable() {
        assertTrue(StatsDao.MESSAGE_COUNT_STATEMENT.contains("COUNT(*)"));
        assertTrue(StatsDao.MESSAGE_COUNT_STATEMENT.contains(TreeLearnSchema.Messages.TABLE));
    }

    @Test
    public void theAnswerStatementSelectsOnlyTheTimestampColumn() {
        assertEquals("the aggregation needs the timestamps and nothing else",
                "SELECT " + TreeLearnSchema.AnswerRevisions.CREATED_AT + " FROM "
                        + TreeLearnSchema.AnswerRevisions.TABLE,
                StatsDao.ANSWER_TIMESTAMPS_STATEMENT);
    }
}
