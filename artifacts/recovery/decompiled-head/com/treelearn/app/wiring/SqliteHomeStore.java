/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  android.database.sqlite.SQLiteDatabase
 *  com.treelearn.core.domain.Branch
 *  com.treelearn.core.domain.GenerationOperation
 *  com.treelearn.core.domain.Message
 *  com.treelearn.core.domain.Topic
 *  com.treelearn.data.db.DatabaseTransactionRunner
 *  com.treelearn.data.db.TreeLearnDatabaseHelper
 *  com.treelearn.data.db.dao.BranchDao
 *  com.treelearn.data.db.dao.FirstTopicCreation
 *  com.treelearn.data.db.dao.FirstTopicCreation$Outcome
 *  com.treelearn.data.db.dao.TopicDao
 *  com.treelearn.data.db.dao.TopicDao$Overview
 */
package com.treelearn.app.wiring;

import android.database.sqlite.SQLiteDatabase;
import com.treelearn.app.artifact.ArtifactKind;
import com.treelearn.app.wiring.HomeStore;
import com.treelearn.core.domain.Branch;
import com.treelearn.core.domain.GenerationOperation;
import com.treelearn.core.domain.Message;
import com.treelearn.core.domain.Topic;
import com.treelearn.data.db.DatabaseTransactionRunner;
import com.treelearn.data.db.TreeLearnDatabaseHelper;
import com.treelearn.data.db.dao.BranchDao;
import com.treelearn.data.db.dao.FirstTopicCreation;
import com.treelearn.data.db.dao.TopicDao;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class SqliteHomeStore
implements HomeStore {
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
    public List<HomeStore.Row> recentTopics() {
        List overviews = TopicDao.listOverviews((SQLiteDatabase)this.helper.getWritableDatabase(), (String)ArtifactKind.BRANCH_SUMMARY.wireId);
        ArrayList<HomeStore.Row> rows = new ArrayList<HomeStore.Row>(overviews.size());
        for (TopicDao.Overview overview : overviews) {
            rows.add(new HomeStore.Row(overview.topicId, overview.resumeBranchId, overview.rootTitle, overview.rootQuestion, overview.summaryPayload, overview.branchCount, overview.unreadCount, overview.lastUsedAt));
        }
        return Collections.unmodifiableList(rows);
    }

    @Override
    public FirstTopicCreation.Outcome createFirstTopic(Topic topic, Branch rootBranch, Message userMessage, Message aiMessage, GenerationOperation operation) {
        return this.firstTopic.create(this.helper.getWritableDatabase(), topic, rootBranch, userMessage, aiMessage, operation);
    }

    @Override
    public void renameTopic(String topicId, String title, long now) {
        SQLiteDatabase database = this.helper.getWritableDatabase();
        Branch root = BranchDao.findRootOfTopic((SQLiteDatabase)database, (String)topicId);
        if (root == null) {
            throw new IllegalStateException("topic has no root branch: " + topicId);
        }
        root.setTitle(title, now);
        BranchDao.update((SQLiteDatabase)database, (Branch)root);
    }

    @Override
    public void deleteTopic(String topicId) {
        TopicDao.delete((SQLiteDatabase)this.helper.getWritableDatabase(), (String)topicId);
    }
}

