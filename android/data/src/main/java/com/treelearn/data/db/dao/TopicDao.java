package com.treelearn.data.db.dao;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.treelearn.core.domain.MessageRole;
import com.treelearn.core.domain.Topic;
import com.treelearn.data.db.TreeLearnSchema;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Row access for {@code topics}.
 *
 * <p>Narrow on purpose: this is the only place that knows the table's column names, and it offers
 * no query the product does not need yet.
 *
 * <p>The transaction boundary is <em>not</em> opened here. Callers that write more than one row wrap
 * the calls in {@code DatabaseTransactionRunner}, which is what keeps "one topic plus its root
 * branch" atomic. A single-row write needs no wrapper because SQLite gives each statement its own
 * implicit transaction.
 *
 * <p>Reads return {@code null} for "no such row" rather than an empty object or a thrown exception:
 * absence is a normal answer to a lookup, not a failure.
 */
public final class TopicDao {

    private TopicDao() {
    }

    public static void insert(SQLiteDatabase database, Topic topic) {
        if (topic == null) {
            throw new IllegalArgumentException("topic must not be null");
        }
        database.insertOrThrow(TreeLearnSchema.Topics.TABLE, null, values(topic));
    }

    /** Writes the fields a session may have changed: the last visited branch and the stamp. */
    public static void update(SQLiteDatabase database, Topic topic) {
        if (topic == null) {
            throw new IllegalArgumentException("topic must not be null");
        }
        ContentValues values = values(topic);
        String where = TreeLearnSchema.Topics.ID + "=?";
        int updated = database.update(TreeLearnSchema.Topics.TABLE, values, where,
                new String[]{topic.id});
        if (updated != 1) {
            throw new IllegalStateException("updating topic " + topic.id + " affected " + updated
                    + " row(s); a topic update must touch exactly its own row");
        }
    }

    /**
     * Deletes the topic. Its branches and messages go with it through {@code ON DELETE CASCADE};
     * this DAO deliberately contains no recursive walk, so the database remains the only
     * implementation of "delete the whole tree".
     */
    public static void delete(SQLiteDatabase database, String topicId) {
        if (topicId == null || topicId.isEmpty()) {
            throw new IllegalArgumentException("topicId must not be blank");
        }
        database.delete(TreeLearnSchema.Topics.TABLE, TreeLearnSchema.Topics.ID + "=?",
                new String[]{topicId});
    }

    /** @return the topic, or null when no row carries this id */
    public static Topic findById(SQLiteDatabase database, String topicId) {
        if (topicId == null || topicId.isEmpty()) {
            throw new IllegalArgumentException("topicId must not be blank");
        }
        try (Cursor cursor = database.query(TreeLearnSchema.Topics.TABLE, null,
                TreeLearnSchema.Topics.ID + "=?", new String[]{topicId}, null, null, null)) {
            if (!cursor.moveToFirst()) {
                return null;
            }
            return read(cursor);
        }
    }

    /**
     * One topic as the home list draws it: the root branch's heading and the artifact-derived
     * summary, plus the size of the tree.
     *
     * <p>Every derived value is nullable on purpose. A topic is created and readable before any AI
     * artifact about it exists, so "no title yet" and "no summary yet" are ordinary answers to this
     * query and not missing columns: the heading falls back to the root question (PRD 6.2.2 point 2)
     * and an absent summary is drawn as nothing at all (PRD 6.2.8 point 7).
     *
     * <p>The summary travels as its stored envelope, not as extracted text, because decoding a
     * payload belongs to the artifact codec and not to this DAO.
     */
    public static final class Overview {

        public final String topicId;
        /** The branch a tap resumes: the last one opened, or the root when none was. */
        public final String resumeBranchId;
        /** The root branch's stored title, or null while it has not been derived (PRD 6.2.2). */
        public final String rootTitle;
        /**
         * The root branch's first learner message, or null when the root branch stores none. It is
         * carried alongside the title because it is the heading's fallback.
         */
        public final String rootQuestion;
        /** The root summary's stored payload, or null while it has not been derived. */
        public final String summaryPayload;
        /** Every branch of the topic, archived ones included: the count the home card shows. */
        public final int branchCount;
        /** Sum of branch unread gaps, derived from the two sequence columns. */
        public final long unreadCount;
        /** The topic's own stamp; the home list is ordered by it. */
        public final long lastUsedAt;

        Overview(String topicId, String resumeBranchId, String rootTitle, String rootQuestion,
                String summaryPayload, int branchCount, long lastUsedAt) {
            this(topicId, resumeBranchId, rootTitle, rootQuestion, summaryPayload, branchCount, 0L,
                    lastUsedAt);
        }

        Overview(String topicId, String resumeBranchId, String rootTitle, String rootQuestion,
                String summaryPayload, int branchCount, long unreadCount, long lastUsedAt) {
            this.topicId = topicId;
            this.resumeBranchId = resumeBranchId;
            this.rootTitle = rootTitle;
            this.rootQuestion = rootQuestion;
            this.summaryPayload = summaryPayload;
            this.branchCount = branchCount;
            this.unreadCount = unreadCount;
            this.lastUsedAt = lastUsedAt;
        }
    }

    /**
     * Every topic, newest first, with what one home card draws (PRD 6.2.16 point 4).
     *
     * <p>One statement rather than a read per topic: the home list is drawn in a single pass and a
     * per-topic follow-up query would be N+1 on the screen that is opened most often. The statement
     * decides nothing - a null title or summary means "not derived yet", and the caller answers that
     * with the root question and with no summary row. The derived column is named by the caller, so
     * this DAO keeps knowing nothing about which artifact kinds the product derives.
     *
     * <p>Read-only: no statement here, and no statement in this task, changes the schema.
     */
    public static List<Overview> listOverviews(SQLiteDatabase database, String summaryKind) {
        if (database == null) {
            throw new IllegalArgumentException("database must not be null");
        }
        if (summaryKind == null || summaryKind.isEmpty()) {
            throw new IllegalArgumentException("summaryKind must not be blank");
        }
        List<Overview> overviews = new ArrayList<Overview>();
        try (Cursor cursor = database.rawQuery(OVERVIEW_SELECT, new String[]{summaryKind})) {
            while (cursor.moveToNext()) {
                overviews.add(new Overview(
                        Rows.string(cursor, ALIAS_TOPIC_ID),
                        Rows.string(cursor, ALIAS_RESUME_BRANCH_ID),
                        Rows.string(cursor, ALIAS_ROOT_TITLE),
                        Rows.string(cursor, ALIAS_ROOT_QUESTION),
                        Rows.string(cursor, ALIAS_SUMMARY_PAYLOAD),
                        Rows.intOf(cursor, ALIAS_BRANCH_COUNT),
                        Rows.longOf(cursor, ALIAS_UNREAD_COUNT),
                        Rows.longOf(cursor, ALIAS_LAST_USED_AT)));
            }
        }
        return Collections.unmodifiableList(overviews);
    }

    /**
     * The stored role of a learner's message. The enum is the vocabulary's single owner, so the
     * literal in the statement below cannot drift away from what {@code MessageDao} writes.
     */
    private static final String ROLE_USER = MessageRole.USER.name();

    /**
     * Result-column names, shared between the statement and the reader so a renamed alias cannot
     * silently produce a cursor read of a column that no longer exists.
     */
    private static final String ALIAS_TOPIC_ID = "topic_id";
    private static final String ALIAS_RESUME_BRANCH_ID = "resume_branch_id";
    private static final String ALIAS_ROOT_TITLE = "root_title";
    private static final String ALIAS_ROOT_QUESTION = "root_question";
    private static final String ALIAS_SUMMARY_PAYLOAD = "summary_payload";
    private static final String ALIAS_BRANCH_COUNT = "branch_count";
    private static final String ALIAS_UNREAD_COUNT = "unread_count";
    private static final String ALIAS_LAST_USED_AT = "last_used_at";

    private static final String OVERVIEW_SELECT =
            "SELECT t." + TreeLearnSchema.Topics.ID + " AS " + ALIAS_TOPIC_ID
                    + ", COALESCE(t." + TreeLearnSchema.Topics.LAST_VISITED_BRANCH_ID + ", r."
                    + TreeLearnSchema.Branches.ID + ") AS " + ALIAS_RESUME_BRANCH_ID
                    + ", r." + TreeLearnSchema.Branches.TITLE + " AS " + ALIAS_ROOT_TITLE
                    + ", (SELECT q." + TreeLearnSchema.Messages.USER_CONTENT + " FROM "
                    + TreeLearnSchema.Messages.TABLE + " q WHERE q."
                    + TreeLearnSchema.Messages.BRANCH_ID + "=r." + TreeLearnSchema.Branches.ID
                    + " AND q." + TreeLearnSchema.Messages.ROLE + "='" + ROLE_USER + "'"
                    + " ORDER BY q." + TreeLearnSchema.Messages.ORDINAL + " ASC LIMIT 1) AS "
                    + ALIAS_ROOT_QUESTION
                    + ", s." + TreeLearnSchema.AiArtifacts.PAYLOAD + " AS " + ALIAS_SUMMARY_PAYLOAD
                    + ", (SELECT COUNT(*) FROM " + TreeLearnSchema.Branches.TABLE + " c WHERE c."
                    + TreeLearnSchema.Branches.TOPIC_ID + "=t." + TreeLearnSchema.Topics.ID + ") AS "
                    + ALIAS_BRANCH_COUNT
                    + ", (SELECT COALESCE(SUM(CASE WHEN c." + TreeLearnSchema.Branches.LATEST_STABLE_SEQUENCE
                    + " > c." + TreeLearnSchema.Branches.LAST_SEEN_STABLE_SEQUENCE + " THEN c."
                    + TreeLearnSchema.Branches.LATEST_STABLE_SEQUENCE + " - c."
                    + TreeLearnSchema.Branches.LAST_SEEN_STABLE_SEQUENCE + " ELSE 0 END), 0) FROM "
                    + TreeLearnSchema.Branches.TABLE + " c WHERE c." + TreeLearnSchema.Branches.TOPIC_ID
                    + "=t." + TreeLearnSchema.Topics.ID + ") AS " + ALIAS_UNREAD_COUNT
                    + ", t." + TreeLearnSchema.Topics.UPDATED_AT + " AS " + ALIAS_LAST_USED_AT
                    + " FROM " + TreeLearnSchema.Topics.TABLE + " t"
                    + " LEFT JOIN " + TreeLearnSchema.Branches.TABLE + " r ON r."
                    + TreeLearnSchema.Branches.TOPIC_ID + "=t." + TreeLearnSchema.Topics.ID
                    + " AND r." + TreeLearnSchema.Branches.PARENT_ID + " IS NULL"
                    + " LEFT JOIN " + TreeLearnSchema.AiArtifacts.TABLE + " s ON s."
                    + TreeLearnSchema.AiArtifacts.KIND + "=?"
                    + " AND s." + TreeLearnSchema.AiArtifacts.SCOPE_ID + "=r."
                    + TreeLearnSchema.Branches.ID
                    + " ORDER BY t." + TreeLearnSchema.Topics.UPDATED_AT + " DESC, t."
                    + TreeLearnSchema.Topics.ID + " ASC";
    private static Topic read(Cursor cursor) {
        // Topic's constructor is public and validates, so it is the rehydration entry point.
        return new Topic(
                Rows.string(cursor, TreeLearnSchema.Topics.ID),
                Rows.string(cursor, TreeLearnSchema.Topics.LAST_VISITED_BRANCH_ID),
                Rows.longOf(cursor, TreeLearnSchema.Topics.CREATED_AT),
                Rows.longOf(cursor, TreeLearnSchema.Topics.UPDATED_AT));
    }

    private static ContentValues values(Topic topic) {
        ContentValues values = new ContentValues();
        values.put(TreeLearnSchema.Topics.ID, topic.id);
        values.put(TreeLearnSchema.Topics.LAST_VISITED_BRANCH_ID, topic.lastVisitedBranchId);
        values.put(TreeLearnSchema.Topics.CREATED_AT, topic.createdAt);
        values.put(TreeLearnSchema.Topics.UPDATED_AT, topic.updatedAt);
        return values;
    }
}
