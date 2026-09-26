package com.treelearn.data.db.dao;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.treelearn.data.db.TreeLearnSchema;

import java.util.ArrayList;
import java.util.List;

/**
 * The read-only stats queries (T-2026-09-23-100): the three raw counts the single statistics
 * entry point aggregates — every answer revision's timestamp, the question total, the message
 * total. No write, no schema statement, no column literal: the statements are assembled from
 * {@link TreeLearnSchema} constants, exactly like every other DAO here.
 *
 * <p>The statements are exposed as constants so the JVM test suite can pin their shape; actually
 * running them against Android SQLite stays with the device rounds (rules/sql.md). The per-day
 * and streak decisions they feed live in {@code com.treelearn.data.stats.StatsAggregator}, not
 * in SQL, so the calendar rules stay testable and one replaceable predicate wide.
 */
public final class StatsDao {

    /**
     * One row per answer revision, its creation instant in UTC epoch millis. A day's answer count
     * and the activity predicate are derived from these, never stored.
     */
    public static final String ANSWER_TIMESTAMPS_STATEMENT =
            "SELECT " + TreeLearnSchema.AnswerRevisions.CREATED_AT + " FROM "
                    + TreeLearnSchema.AnswerRevisions.TABLE;

    /** A question is a USER row: an AI position is a logical position, not a question. */
    public static final String QUESTION_COUNT_STATEMENT =
            "SELECT COUNT(*) FROM " + TreeLearnSchema.Messages.TABLE + " WHERE "
                    + TreeLearnSchema.Messages.ROLE + "='USER'";

    /** Every message row, USER and AI alike. */
    public static final String MESSAGE_COUNT_STATEMENT =
            "SELECT COUNT(*) FROM " + TreeLearnSchema.Messages.TABLE;

    private StatsDao() {
    }

    /**
     * @return the created_at instant of every answer revision, unordered; empty when there are
     *         none. {@code created_at} is NOT NULL, so no row can read as a fabricated instant.
     */
    public static List<Long> answerTimestamps(SQLiteDatabase database) {
        List<Long> timestamps = new ArrayList<>();
        try (Cursor cursor = database.rawQuery(ANSWER_TIMESTAMPS_STATEMENT, null)) {
            while (cursor.moveToNext()) {
                timestamps.add(cursor.getLong(0));
            }
        }
        return timestamps;
    }

    public static long questionCount(SQLiteDatabase database) {
        return countOf(database, QUESTION_COUNT_STATEMENT);
    }

    public static long messageCount(SQLiteDatabase database) {
        return countOf(database, MESSAGE_COUNT_STATEMENT);
    }

    private static long countOf(SQLiteDatabase database, String statement) {
        try (Cursor cursor = database.rawQuery(statement, null)) {
            return cursor.moveToFirst() ? cursor.getLong(0) : 0L;
        }
    }
}
