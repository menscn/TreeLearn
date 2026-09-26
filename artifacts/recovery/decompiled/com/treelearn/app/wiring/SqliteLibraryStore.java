/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  android.database.sqlite.SQLiteDatabase
 *  com.treelearn.core.domain.Branch
 *  com.treelearn.data.db.TreeLearnDatabaseHelper
 *  com.treelearn.data.db.dao.BranchDao
 *  com.treelearn.data.db.dao.TopicDao
 *  com.treelearn.data.db.dao.TopicDao$Overview
 */
package com.treelearn.app.wiring;

import android.database.sqlite.SQLiteDatabase;
import com.treelearn.app.artifact.ArtifactKind;
import com.treelearn.core.domain.Branch;
import com.treelearn.data.db.TreeLearnDatabaseHelper;
import com.treelearn.data.db.dao.BranchDao;
import com.treelearn.data.db.dao.TopicDao;
import java.util.ArrayList;
import java.util.List;

public final class SqliteLibraryStore {
    private final TreeLearnDatabaseHelper helper;

    public SqliteLibraryStore(TreeLearnDatabaseHelper helper) {
        if (helper == null) {
            throw new IllegalArgumentException("helper must not be null");
        }
        this.helper = helper;
    }

    public List<TopicDao.Overview> topics() {
        return TopicDao.listOverviews((SQLiteDatabase)this.helper.getWritableDatabase(), (String)ArtifactKind.BRANCH_SUMMARY.wireId);
    }

    public List<ArchivedBranch> archived() {
        ArrayList<ArchivedBranch> found = new ArrayList<ArchivedBranch>();
        for (TopicDao.Overview topic : this.topics()) {
            for (Branch branch : BranchDao.findByTopic((SQLiteDatabase)this.helper.getWritableDatabase(), (String)topic.topicId)) {
                if (!branch.isArchived()) continue;
                found.add(new ArchivedBranch(topic.topicId, branch.id, branch.title()));
            }
        }
        return found;
    }

    public static final class ArchivedBranch {
        public final String topicId;
        public final String branchId;
        public final String title;

        ArchivedBranch(String topicId, String branchId, String title) {
            this.topicId = topicId;
            this.branchId = branchId;
            this.title = title;
        }
    }
}

