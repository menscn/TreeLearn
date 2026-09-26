/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  android.database.sqlite.SQLiteDatabase
 *  com.treelearn.core.domain.Branch
 *  com.treelearn.core.domain.Guard
 *  com.treelearn.data.db.DatabaseTransactionRunner
 *  com.treelearn.data.db.TreeLearnDatabaseHelper
 *  com.treelearn.data.db.dao.AiArtifactDao
 *  com.treelearn.data.db.dao.AiArtifactDao$Row
 *  com.treelearn.data.db.dao.BranchDao
 *  com.treelearn.data.db.dao.GenerationOperationDao
 *  com.treelearn.data.db.dao.GenerationOperationRow
 *  com.treelearn.data.db.dao.TopicDao
 */
package com.treelearn.app.navigation;

import android.database.sqlite.SQLiteDatabase;
import com.treelearn.app.artifact.ArtifactKind;
import com.treelearn.app.artifact.ArtifactPayloadCodec;
import com.treelearn.app.artifact.DerivedResult;
import com.treelearn.app.generation.GenerationCoordinator;
import com.treelearn.app.navigation.NavigationData;
import com.treelearn.core.domain.Branch;
import com.treelearn.core.domain.Guard;
import com.treelearn.data.db.DatabaseTransactionRunner;
import com.treelearn.data.db.TreeLearnDatabaseHelper;
import com.treelearn.data.db.dao.AiArtifactDao;
import com.treelearn.data.db.dao.BranchDao;
import com.treelearn.data.db.dao.GenerationOperationDao;
import com.treelearn.data.db.dao.GenerationOperationRow;
import com.treelearn.data.db.dao.TopicDao;
import java.util.ArrayList;
import java.util.List;

public final class SqliteNavigationStore
implements NavigationData {
    private static final int NO_ITEM_CEILING = Integer.MAX_VALUE;
    private final TreeLearnDatabaseHelper helper;
    private final DatabaseTransactionRunner transactions;
    private final GenerationCoordinator coordinator;

    public SqliteNavigationStore(TreeLearnDatabaseHelper helper, DatabaseTransactionRunner transactions, GenerationCoordinator coordinator) {
        this.helper = (TreeLearnDatabaseHelper)Guard.object((Object)helper, (String)"helper");
        this.transactions = (DatabaseTransactionRunner)Guard.object((Object)transactions, (String)"transactions");
        this.coordinator = (GenerationCoordinator)Guard.object((Object)coordinator, (String)"coordinator");
    }

    @Override
    public String topicOf(String branchId) {
        Branch branch = BranchDao.findById((SQLiteDatabase)this.database(), (String)branchId);
        return branch == null ? null : branch.topicId;
    }

    @Override
    public NavigationData.Snapshot load(String topicId) {
        ArrayList<NavigationData.Node> nodes = new ArrayList<NavigationData.Node>();
        for (Branch branch : BranchDao.findByTopic((SQLiteDatabase)this.database(), (String)topicId)) {
            nodes.add(new NavigationData.Node(branch.id, branch.parentBranchId, branch.title(), branch.isArchived()));
        }
        return new NavigationData.Snapshot(topicId, nodes);
    }

    @Override
    public NavigationData.Summary summary(String branchId) {
        AiArtifactDao.Row row = AiArtifactDao.findByKindAndScope((SQLiteDatabase)this.database(), (String)ArtifactKind.BRANCH_SUMMARY.wireId, (String)branchId);
        if (row == null) {
            return null;
        }
        DerivedResult parsed = ArtifactPayloadCodec.parse(ArtifactKind.BRANCH_SUMMARY, row.payload, Integer.MAX_VALUE);
        if (!parsed.isUsable()) {
            return null;
        }
        return new NavigationData.Summary(branchId, parsed.payload().summary, parsed.payload().keyPoints);
    }

    @Override
    public void setArchived(String branchId, boolean archived, long now) {
        Guard.id((String)branchId, (String)"branchId");
        this.transactions.run(this.database(), open -> {
            Branch branch = BranchDao.findById((SQLiteDatabase)open, (String)branchId);
            if (branch == null) {
                throw new IllegalStateException("branch " + branchId + " is gone, so there is nothing left to archive or restore");
            }
            if (archived) {
                branch.archive(now);
            } else {
                branch.unarchive(now);
            }
            BranchDao.update((SQLiteDatabase)open, (Branch)branch);
            return null;
        });
    }

    @Override
    public int cancelInFlight(List<String> branchIds) {
        Guard.object(branchIds, (String)"branchIds");
        int cancelled = 0;
        for (String branchId : branchIds) {
            GenerationOperationRow active = GenerationOperationDao.findActiveByBranch((SQLiteDatabase)this.database(), (String)branchId);
            if (active == null || !this.coordinator.cancel(active.id)) continue;
            ++cancelled;
        }
        return cancelled;
    }

    @Override
    public void delete(NavigationData.Plan plan) {
        Guard.object((Object)plan, (String)"plan");
        this.transactions.run(this.database(), open -> {
            if (plan.wholeTopic) {
                TopicDao.delete((SQLiteDatabase)open, (String)plan.topicId);
                return null;
            }
            for (String branchId : plan.branchIds) {
                BranchDao.delete((SQLiteDatabase)open, (String)branchId);
            }
            return null;
        });
    }

    private SQLiteDatabase database() {
        return this.helper.getWritableDatabase();
    }
}

