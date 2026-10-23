package com.treelearn.data.db.dao;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.treelearn.core.domain.Guard;
import com.treelearn.core.domain.MessageRole;
import com.treelearn.core.domain.Persona;
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
     * Writes only {@code topics.persona}.
     *
     * <p>Deliberately not {@link #update}: that writes the whole row and refreshes
     * {@code updated_at}, and a style switch must not count as learning activity or move the tree to
     * the top of the home list (拍板 2026-10-23).
     *
     * @throws IllegalStateException when the id does not name exactly one stored topic — a switch
     *         against a row that is gone is a caller bug, not a silent no-op
     */
    public static void updatePersona(SQLiteDatabase database, String topicId, Persona persona) {
        if (database == null) {
            throw new IllegalArgumentException("database must not be null");
        }
        if (topicId == null || topicId.isEmpty()) {
            throw new IllegalArgumentException("topicId must not be blank");
        }
        if (persona == null) {
            throw new IllegalArgumentException("persona must not be null");
        }
        ContentValues values = new ContentValues();
        values.put(TreeLearnSchema.Topics.PERSONA, persona.wireId);
        int updated = database.update(TreeLearnSchema.Topics.TABLE, values,
                TreeLearnSchema.Topics.ID + "=?", new String[]{topicId});
        if (updated != 1) {
            throw new IllegalStateException("switching the style of topic " + topicId
                    + " affected " + updated + " row(s); the write must touch exactly its own row");
        }
    }

    /**
     * A topic's current style, read through one of its branches.
     *
     * <p>The result keeps the fallback visible instead of letting {@link Topic} absorb it: a stored
     * wire id this build does not know is read as the default style <em>and</em> reported, so the
     * screen can tell the learner once rather than silently pretending the value was always valid.
     * The row itself is not rewritten here — reading must not mutate, and a future build may know the
     * value.
     *
     * @return the style and whether it fell back, or null when no branch row carries this id
     */
    public static PersonaRead personaOfBranch(SQLiteDatabase database, String branchId) {
        if (branchId == null || branchId.isEmpty()) {
            throw new IllegalArgumentException("branchId must not be blank");
        }
        try (Cursor cursor = database.rawQuery(PERSONA_OF_BRANCH_SELECT,
                new String[]{branchId})) {
            if (!cursor.moveToFirst()) {
                return null;
            }
            return personaRead(Rows.string(cursor, ALIAS_PERSONA));
        }
    }

    /** A style read that remembers whether the stored wire id had to fall back. */
    public static final class PersonaRead {

        public final Persona persona;
        /** True when the stored value is not one this build knows; {@link #persona} is then default. */
        public final boolean fellBack;

        public PersonaRead(Persona persona, boolean fellBack) {
            if (persona == null) {
                throw new IllegalArgumentException("persona must not be null");
            }
            this.persona = persona;
            this.fellBack = fellBack;
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
     * One topic as the forest draws it: how many branches the tree has, how many of them are
     * archived, when it was last used, and the style its sheet names.
     *
     * <p>The total includes archived branches on purpose (SPEC-2026-10-21-my-forest): the forest
     * is a retrospective view of the whole tree, not the navigation list. The archived count is
     * the same branch table narrowed by {@code archived_at}, so the two numbers cannot come from
     * two different trees.
     */
    public static final class ForestCount {

        public final String topicId;
        /** Every branch of the topic, archived ones included. */
        public final int branchCount;
        /** The archived branches among {@link #branchCount}. */
        public final int archivedCount;
        /** The topic's own stamp; the forest sheet formats it like every other surface. */
        public final long lastVisitedAt;
        /** The stored style wire id; {@code null} reads as the default through {@link Persona}. */
        public final String personaWireId;

        public ForestCount(String topicId, int branchCount, int archivedCount, long lastVisitedAt,
                String personaWireId) {
            this.topicId = Guard.id(topicId, "topicId");
            this.branchCount = Guard.atLeast(branchCount, 0, "branchCount");
            this.archivedCount = Guard.atLeast(archivedCount, 0, "archivedCount");
            if (archivedCount > branchCount) {
                throw new IllegalArgumentException("archivedCount " + archivedCount
                        + " cannot exceed branchCount " + branchCount);
            }
            this.lastVisitedAt = Guard.epochMillis(lastVisitedAt, "lastVisitedAt");
            this.personaWireId = personaWireId;
        }
    }

    /**
     * Every topic's forest counts in one statement.
     *
     * <p>Read-only and schema-free: the forest view is not allowed to write anything, and this
     * projection only counts rows the branch table already holds. It is deliberately separate from
     * {@link #listOverviews}: the home and library cards need a root heading and a summary, while
     * the forest needs the two branch tallies and the style. Both read the same topics table, so
     * neither can invent a topic the other does not have.
     */
    public static List<ForestCount> listForestCounts(SQLiteDatabase database) {
        if (database == null) {
            throw new IllegalArgumentException("database must not be null");
        }
        List<ForestCount> counts = new ArrayList<ForestCount>();
        try (Cursor cursor = database.rawQuery(TOPIC_FOREST_COUNTS_SELECT, null)) {
            while (cursor.moveToNext()) {
                counts.add(new ForestCount(
                        Rows.string(cursor, ALIAS_TOPIC_ID),
                        Rows.intOf(cursor, ALIAS_BRANCH_COUNT),
                        Rows.intOf(cursor, ALIAS_ARCHIVED_COUNT),
                        Rows.longOf(cursor, ALIAS_LAST_USED_AT),
                        Rows.string(cursor, ALIAS_PERSONA)));
            }
        }
        return Collections.unmodifiableList(counts);
    }

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
    private static final String ALIAS_ARCHIVED_COUNT = "archived_count";
    private static final String ALIAS_UNREAD_COUNT = "unread_count";
    private static final String ALIAS_LAST_USED_AT = "last_used_at";
    private static final String ALIAS_PERSONA = "persona";

    /**
     * The style of the topic that owns one branch, in one statement. The branch is the entry point
     * both the send path and the conversation read already hold, so neither has to load a topic row
     * just to learn which style the next answer should use.
     */
    static final String PERSONA_OF_BRANCH_SELECT =
            "SELECT t." + TreeLearnSchema.Topics.PERSONA + " AS " + ALIAS_PERSONA
                    + " FROM " + TreeLearnSchema.Branches.TABLE + " b JOIN "
                    + TreeLearnSchema.Topics.TABLE + " t ON t." + TreeLearnSchema.Topics.ID
                    + "=b." + TreeLearnSchema.Branches.TOPIC_ID
                    + " WHERE b." + TreeLearnSchema.Branches.ID + "=?";

    /**
     * The one reading rule for a stored wire id: known values pass through, everything else becomes
     * the default <em>and</em> is reported. Extracted from the cursor loop so the fallback itself is
     * testable on a plain JVM; the database half cannot run in a unit test.
     */
    static PersonaRead personaRead(String storedWireId) {
        return new PersonaRead(Persona.fromWireId(storedWireId),
                !Persona.isKnown(storedWireId));
    }

    /**
     * The forest's one read: every topic with its whole branch count, its archived subset, its
     * last-use stamp and its stored style.
     *
     * <p>No per-topic parameter: the forest is read in one pass, exactly like the home list, so a
     * topic cannot be counted twice or skipped between two statements. The total has no
     * {@code archived_at} filter; only the second count narrows to the archived rows.
     */
    static final String TOPIC_FOREST_COUNTS_SELECT =
            "SELECT t." + TreeLearnSchema.Topics.ID + " AS " + ALIAS_TOPIC_ID
                    + ", (SELECT COUNT(*) FROM " + TreeLearnSchema.Branches.TABLE + " b WHERE b."
                    + TreeLearnSchema.Branches.TOPIC_ID + "=t." + TreeLearnSchema.Topics.ID
                    + ") AS " + ALIAS_BRANCH_COUNT
                    + ", (SELECT COUNT(*) FROM " + TreeLearnSchema.Branches.TABLE + " b WHERE b."
                    + TreeLearnSchema.Branches.TOPIC_ID + "=t." + TreeLearnSchema.Topics.ID
                    + " AND b." + TreeLearnSchema.Branches.ARCHIVED_AT + " IS NOT NULL) AS "
                    + ALIAS_ARCHIVED_COUNT
                    + ", t." + TreeLearnSchema.Topics.UPDATED_AT + " AS " + ALIAS_LAST_USED_AT
                    + ", t." + TreeLearnSchema.Topics.PERSONA + " AS " + ALIAS_PERSONA
                    + " FROM " + TreeLearnSchema.Topics.TABLE + " t"
                    + " ORDER BY t." + TreeLearnSchema.Topics.UPDATED_AT + " DESC, t."
                    + TreeLearnSchema.Topics.ID + " ASC";

    private static final String OVERVIEW_SELECT =            "SELECT t." + TreeLearnSchema.Topics.ID + " AS " + ALIAS_TOPIC_ID
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
        // Topic's constructor is public and validates, so it is the rehydration entry point. An
        // unknown stored style falls back here without a flag; the flagged read is personaOfBranch,
        // which is the one the screen uses to tell the learner.
        return new Topic(
                Rows.string(cursor, TreeLearnSchema.Topics.ID),
                Rows.string(cursor, TreeLearnSchema.Topics.LAST_VISITED_BRANCH_ID),
                Persona.fromWireId(Rows.string(cursor, TreeLearnSchema.Topics.PERSONA)),
                Rows.longOf(cursor, TreeLearnSchema.Topics.CREATED_AT),
                Rows.longOf(cursor, TreeLearnSchema.Topics.UPDATED_AT));
    }

    private static ContentValues values(Topic topic) {
        ContentValues values = new ContentValues();
        values.put(TreeLearnSchema.Topics.ID, topic.id);
        values.put(TreeLearnSchema.Topics.LAST_VISITED_BRANCH_ID, topic.lastVisitedBranchId);
        values.put(TreeLearnSchema.Topics.CREATED_AT, topic.createdAt);
        values.put(TreeLearnSchema.Topics.UPDATED_AT, topic.updatedAt);
        values.put(TreeLearnSchema.Topics.PERSONA, topic.persona.wireId);
        return values;
    }
}
