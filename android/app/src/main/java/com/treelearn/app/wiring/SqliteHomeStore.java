package com.treelearn.app.wiring;

import com.treelearn.app.artifact.ArtifactKind;
import com.treelearn.core.domain.Branch;
import com.treelearn.core.domain.GenerationOperation;
import com.treelearn.core.domain.Message;
import com.treelearn.core.domain.Topic;
import com.treelearn.data.db.DatabaseTransactionRunner;
import com.treelearn.data.db.TreeLearnDatabaseHelper;
import com.treelearn.data.db.dao.FirstTopicCreation;
import com.treelearn.data.db.dao.TopicDao;
import com.treelearn.data.db.dao.BranchDao;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The home path's rows, read from the real local database.
 *
 * <p>It owns no rule. The list is one statement in {@link TopicDao}, and the first topic's five row
 * groups are one transaction in {@link FirstTopicCreation}; this class only hands them a connection
 * and copies the values across. Nothing here decides what a card says or what a refusal means - those
 * live in {@link HomeWiring}, where a plain-JVM test can reach them.
 *
 * <p>One connection for reads and writes on purpose: {@code SQLiteOpenHelper} returns the same
 * connection either way, and the DAOs take a database rather than a mode, so asking for a second
 * handle would only make "which connection am I on" a question somebody has to answer.
 */
public final class SqliteHomeStore implements HomeStore {

    private final TreeLearnDatabaseHelper helper;
    private final FirstTopicCreation firstTopic;

    public SqliteHomeStore(TreeLearnDatabaseHelper helper, DatabaseTransactionRunner runner) {
        if (helper == null) {
            throw new IllegalArgumentException("helper must not be null");
        }
        if (runner == null) {
            throw new IllegalArgumentException("runner must not be null");
        }
        this.helper = helper;
        this.firstTopic = new FirstTopicCreation(runner);
    }

    @Override
    public List<Row> recentTopics() {
        List<TopicDao.Overview> overviews = TopicDao.listOverviews(helper.getWritableDatabase(),
                ArtifactKind.BRANCH_SUMMARY.wireId);
        List<Row> rows = new ArrayList<Row>(overviews.size());
        for (TopicDao.Overview overview : overviews) {
            rows.add(new Row(overview.topicId, overview.resumeBranchId, overview.rootTitle,
                    overview.rootQuestion, overview.summaryPayload, overview.branchCount,
                    overview.unreadCount, overview.lastUsedAt));
        }
        return Collections.unmodifiableList(rows);
    }

    @Override
    public FirstTopicCreation.Outcome createFirstTopic(Topic topic, Branch rootBranch,
            Message userMessage, Message aiMessage, GenerationOperation operation) {
        return firstTopic.create(helper.getWritableDatabase(), topic, rootBranch, userMessage,
                aiMessage, operation);
    }

    @Override
    public void renameTopic(String topicId, String title, long now) {
        android.database.sqlite.SQLiteDatabase database = helper.getWritableDatabase();
        com.treelearn.core.domain.Branch root = BranchDao.findRootOfTopic(database, topicId);
        if (root == null) {
            throw new IllegalStateException("topic has no root branch: " + topicId);
        }
        root.setTitle(title, now);
        BranchDao.update(database, root);
    }

    @Override
    public void deleteTopic(String topicId) {
        TopicDao.delete(helper.getWritableDatabase(), topicId);
    }
}
