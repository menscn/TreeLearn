/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  android.database.SQLException
 *  android.database.sqlite.SQLiteDatabase
 *  com.treelearn.core.domain.Branch
 *  com.treelearn.core.domain.DomainErrorCode
 *  com.treelearn.core.domain.DomainResult
 *  com.treelearn.core.domain.Guard
 *  com.treelearn.core.domain.Message
 *  com.treelearn.data.db.DatabaseTransactionRunner
 *  com.treelearn.data.db.TreeLearnDatabaseHelper
 *  com.treelearn.data.db.dao.AiArtifactDao
 *  com.treelearn.data.db.dao.AiArtifactDao$Row
 *  com.treelearn.data.db.dao.BranchDao
 *  com.treelearn.data.db.dao.MessageDao
 */
package com.treelearn.app.wiring;

import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import com.treelearn.app.artifact.ArtifactKind;
import com.treelearn.app.wiring.TreeCanvasStore;
import com.treelearn.core.domain.Branch;
import com.treelearn.core.domain.DomainErrorCode;
import com.treelearn.core.domain.DomainResult;
import com.treelearn.core.domain.Guard;
import com.treelearn.core.domain.Message;
import com.treelearn.data.db.DatabaseTransactionRunner;
import com.treelearn.data.db.TreeLearnDatabaseHelper;
import com.treelearn.data.db.dao.AiArtifactDao;
import com.treelearn.data.db.dao.BranchDao;
import com.treelearn.data.db.dao.MessageDao;
import java.util.List;

public final class SqliteTreeCanvasStore
implements TreeCanvasStore {
    private static final String REFUSED_DETAIL = "the fold was not stored";
    private final TreeLearnDatabaseHelper helper;
    private final DatabaseTransactionRunner transactions;

    public SqliteTreeCanvasStore(TreeLearnDatabaseHelper helper, DatabaseTransactionRunner transactions) {
        this.helper = (TreeLearnDatabaseHelper)Guard.object((Object)helper, (String)"helper");
        this.transactions = (DatabaseTransactionRunner)Guard.object((Object)transactions, (String)"transactions");
    }

    @Override
    public String topicOf(String branchId) {
        Branch branch = BranchDao.findById((SQLiteDatabase)this.database(), (String)branchId);
        return branch == null ? null : branch.topicId;
    }

    @Override
    public List<Branch> branchesOf(String topicId) {
        return BranchDao.findByTopic((SQLiteDatabase)this.database(), (String)topicId);
    }

    @Override
    public String summaryPayload(String branchId) {
        AiArtifactDao.Row row = AiArtifactDao.findByKindAndScope((SQLiteDatabase)this.database(), (String)ArtifactKind.BRANCH_SUMMARY.wireId, (String)branchId);
        return row == null ? null : row.payload;
    }

    @Override
    public int committedRounds(String branchId) {
        int rounds = 0;
        for (Message row : MessageDao.findByBranch((SQLiteDatabase)this.database(), (String)branchId)) {
            if (!row.isUser()) continue;
            ++rounds;
        }
        return rounds;
    }

    @Override
    public DomainResult<Void> setCollapsed(String branchId, boolean collapsed, long now) {
        Guard.id((String)branchId, (String)"branchId");
        SQLiteDatabase database = this.database();
        try {
            this.transactions.run(database, open -> {
                Branch branch = BranchDao.findById((SQLiteDatabase)open, (String)branchId);
                if (branch == null) {
                    throw new IllegalStateException("branch " + branchId + " is gone, so there is no fold to store for it");
                }
                branch.setCollapsed(collapsed, now);
                BranchDao.update((SQLiteDatabase)open, (Branch)branch);
                return null;
            });
        }
        catch (SQLException refused) {
            return DomainResult.fail((DomainErrorCode)DomainErrorCode.OPERATION_NOT_COMPLETABLE, (String)REFUSED_DETAIL);
        }
        return DomainResult.ok();
    }

    private SQLiteDatabase database() {
        return this.helper.getWritableDatabase();
    }
}

