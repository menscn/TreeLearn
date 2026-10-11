package com.treelearn.web.storage;

import com.treelearn.core.domain.Branch;
import com.treelearn.core.domain.MessageRole;
import com.treelearn.core.domain.Topic;
import com.treelearn.web.storage.DesktopSchema.Branches;
import com.treelearn.web.storage.DesktopSchema.Messages;
import com.treelearn.web.storage.DesktopSchema.Topics;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/**
 * Row access for {@code topics} and their derived home-list rows (task T-2026-10-10-010).
 *
 * <p>Semantic reference — the Android {@code com.treelearn.data.db.dao} implementation it mirrors:
 * <ul>
 *   <li>{@code TopicDao.insert} / {@code findById} → {@link #create(long)} / {@link #get(String)}.</li>
 *   <li>{@code TopicDao.listOverviews} → {@link #listOrderedByRecency()}: the same LEFT JOIN on the
 *       topic's root branch, the same nullable root title and the same first-USER-message fallback, so
 *       "the tree's label is the root branch's title" has one spelling per engine.</li>
 *   <li>{@code TopicDao.update} → {@link #touchLastVisited(String, String, long)}.</li>
 *   <li>{@code TopicDao.delete} → {@link #deleteCascade(String)}: the database, through
 *       {@code ON DELETE CASCADE}, is the only implementation of "delete the whole tree".</li>
 * </ul>
 *
 * <p>{@link #create(long)} owns the transaction boundary Android's {@code FirstTopicCreation} owns for
 * the first pair: the topic row and its root branch are written together, so "there is a topic but no
 * root branch" cannot survive.
 */
public final class TopicStore {

    private static final String INSERT_TOPIC = "INSERT INTO " + Topics.TABLE + "(" + Topics.ID + ", "
            + Topics.LAST_VISITED_BRANCH_ID + ", " + Topics.CREATED_AT + ", " + Topics.UPDATED_AT
            + ") VALUES (?, ?, ?, ?)";
    private static final String INSERT_ROOT = "INSERT INTO " + Branches.TABLE + "(" + Branches.ID + ", "
            + Branches.TOPIC_ID + ", " + Branches.CREATED_AT + ", " + Branches.UPDATED_AT
            + ") VALUES (?, ?, ?, ?)";
    private static final String SELECT_ONE = "SELECT * FROM " + Topics.TABLE + " WHERE " + Topics.ID
            + " = ?";
    private static final String TOUCH = "UPDATE " + Topics.TABLE + " SET "
            + Topics.LAST_VISITED_BRANCH_ID + " = ?, " + Topics.UPDATED_AT + " = ? WHERE " + Topics.ID
            + " = ?";
    private static final String DELETE_TOPIC = "DELETE FROM " + Topics.TABLE + " WHERE " + Topics.ID
            + " = ?";
    private static final String COUNT_BRANCHES = "SELECT COUNT(*) FROM " + Branches.TABLE + " WHERE "
            + Branches.TOPIC_ID + " = ?";
    private static final String COUNT_MESSAGES = "SELECT COUNT(*) FROM " + Messages.TABLE + " m JOIN "
            + Branches.TABLE + " b ON m." + Messages.BRANCH_ID + " = b." + Branches.ID + " WHERE b."
            + Branches.TOPIC_ID + " = ?";
    /** Android {@code TopicDao.OVERVIEW_SELECT} without the artifact join: title plus its fallback. */
    private static final String LIST_ORDERED = "SELECT t." + Topics.ID + " AS topic_id, r."
            + Branches.TITLE + " AS root_title, (SELECT q." + Messages.USER_CONTENT + " FROM "
            + Messages.TABLE + " q WHERE q." + Messages.BRANCH_ID + " = r." + Branches.ID + " AND q."
            + Messages.ROLE + " = '" + MessageRole.USER.name() + "' ORDER BY q." + Messages.ORDINAL
            + " ASC LIMIT 1) AS root_question, t." + Topics.UPDATED_AT + " AS last_used_at FROM "
            + Topics.TABLE + " t LEFT JOIN " + Branches.TABLE + " r ON r." + Branches.TOPIC_ID + " = t."
            + Topics.ID + " AND r." + Branches.PARENT_ID + " IS NULL ORDER BY t." + Topics.UPDATED_AT
            + " DESC, t." + Topics.ID + " ASC";
    private static final String A_TOPIC_ID = "topic_id";
    private static final String A_ROOT_TITLE = "root_title";
    private static final String A_ROOT_QUESTION = "root_question";
    private static final String A_LAST_USED_AT = "last_used_at";

    private final JdbcSupport jdbc;

    public TopicStore(JdbcSupport jdbc) {
        this.jdbc = jdbc;
    }

    /** Creates a topic and its single root branch in one transaction, or neither. */
    public Created create(long now) {
        return jdbc.runInTransaction(connection -> {
            Topic topic = Topic.create(now);
            Branch root = Branch.root(topic.id, now);
            insert(connection, topic);
            jdbc.update(connection, INSERT_ROOT, s -> {
                s.setString(1, root.id); s.setString(2, root.topicId);
                s.setLong(3, root.createdAt); s.setLong(4, root.updatedAt());
            });
            return new Created(topic, root);
        });
    }

    /**
     * Transaction-scoped insert of an already-built topic: the caller owns the boundary, so the
     * first-question accept writes the topic beside its root branch, both positions and the attempt in
     * one transaction (contract §6.2, mirroring Android {@code FirstTopicCreation}).
     */
    public void insert(Connection connection, Topic topic) {
        jdbc.update(connection, INSERT_TOPIC, s -> {
            s.setString(1, topic.id); s.setString(2, topic.lastVisitedBranchId);
            s.setLong(3, topic.createdAt); s.setLong(4, topic.updatedAt);
        });
    }

    public Optional<Topic> get(String topicId) {
        return jdbc.queryOne(SELECT_ONE, s -> s.setString(1, topicId), TopicStore::readTopic);
    }

    /** Every topic, newest first, with the root title and the question that stands in for it. */
    public List<Summary> listOrderedByRecency() {
        return jdbc.queryList(LIST_ORDERED, JdbcSupport.NO_PARAMS, TopicStore::readSummary);
    }

    /** Records the last opened branch and stamps the topic; the home list is ordered by that stamp. */
    public void touchLastVisited(String topicId, String branchId, long now) {
        JdbcSupport.requireSingleRow(jdbc.update(TOUCH, s -> {
            s.setString(1, branchId); s.setLong(2, now); s.setString(3, topicId);
        }), "touching topic " + topicId);
    }

    /** Deletes the topic; its branches, messages, revisions and snapshots cascade with it. */
    public DeletionCounts deleteCascade(String topicId) {
        return jdbc.runInTransaction(connection -> {
            int branchCount = count(connection, COUNT_BRANCHES, topicId);
            int messageCount = count(connection, COUNT_MESSAGES, topicId);
            int topicCount = jdbc.update(connection, DELETE_TOPIC, s -> s.setString(1, topicId));
            return new DeletionCounts(topicCount, branchCount, messageCount);
        });
    }

    private int count(Connection connection, String sql, String topicId) {
        return jdbc.queryOne(connection, sql, s -> s.setString(1, topicId),
                rows -> Integer.valueOf(rows.getInt(1))).orElse(0);
    }

    private static Topic readTopic(ResultSet rows) throws SQLException {
        return new Topic(rows.getString(Topics.ID), rows.getString(Topics.LAST_VISITED_BRANCH_ID),
                rows.getLong(Topics.CREATED_AT), rows.getLong(Topics.UPDATED_AT));
    }

    private static Summary readSummary(ResultSet rows) throws SQLException {
        return new Summary(rows.getString(A_TOPIC_ID), rows.getString(A_ROOT_TITLE),
                rows.getString(A_ROOT_QUESTION), rows.getLong(A_LAST_USED_AT));
    }

    /** The committed topic together with the root branch written alongside it. */
    public static final class Created {

        public final Topic topic;
        public final Branch rootBranch;

        Created(Topic topic, Branch rootBranch) {
            this.topic = topic;
            this.rootBranch = rootBranch;
        }
    }

    /**
     * One home-list row: the topic and the two fields its label is derived from. The root title is
     * null until something derives it, and the root question is its fallback (PRD 6.2.2 point 2).
     */
    public static final class Summary {

        public final String topicId;
        public final String rootTitle;
        public final String rootQuestion;
        public final long lastUsedAt;

        Summary(String topicId, String rootTitle, String rootQuestion, long lastUsedAt) {
            this.topicId = topicId; this.rootTitle = rootTitle;
            this.rootQuestion = rootQuestion; this.lastUsedAt = lastUsedAt;
        }

        /** The title the home card shows: the derived title, else the root question. */
        public String displayTitle() {
            return rootTitle != null ? rootTitle : rootQuestion;
        }
    }

    /** What one cascading topic delete removed: the topic, its branches and their messages. */
    public static final class DeletionCounts {

        public final int topics;
        public final int branches;
        public final int messages;

        DeletionCounts(int topics, int branches, int messages) {
            this.topics = topics; this.branches = branches; this.messages = messages;
        }
    }
}
