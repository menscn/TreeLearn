/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  android.database.sqlite.SQLiteDatabase
 *  com.treelearn.core.domain.ContextSnapshotCodec
 *  com.treelearn.core.domain.Guard
 *  com.treelearn.core.domain.Message
 *  com.treelearn.core.domain.PromptPurpose
 *  com.treelearn.data.db.DatabaseTransactionRunner
 *  com.treelearn.data.db.dao.AiArtifactDao
 *  com.treelearn.data.db.dao.AiArtifactDao$Row
 *  com.treelearn.data.db.dao.AiArtifactDao$Writeback
 *  com.treelearn.data.db.dao.BranchDao
 *  com.treelearn.data.db.dao.MessageDao
 */
package com.treelearn.app.artifact;

import android.database.sqlite.SQLiteDatabase;
import com.treelearn.app.artifact.ArtifactInputs;
import com.treelearn.app.artifact.ArtifactKind;
import com.treelearn.app.artifact.DerivedStore;
import com.treelearn.core.domain.ContextSnapshotCodec;
import com.treelearn.core.domain.Guard;
import com.treelearn.core.domain.Message;
import com.treelearn.core.domain.PromptPurpose;
import com.treelearn.data.db.DatabaseTransactionRunner;
import com.treelearn.data.db.dao.AiArtifactDao;
import com.treelearn.data.db.dao.BranchDao;
import com.treelearn.data.db.dao.MessageDao;
import java.util.function.Supplier;

public final class SqliteDerivedStore
implements DerivedStore {
    private final SQLiteDatabase database;
    private final DatabaseTransactionRunner transactions;
    private final OutlineDigest outlineDigest;

    public SqliteDerivedStore(SQLiteDatabase database, DatabaseTransactionRunner transactions, OutlineDigest outlineDigest) {
        this.database = (SQLiteDatabase)Guard.object((Object)database, (String)"database");
        this.transactions = (DatabaseTransactionRunner)Guard.object((Object)transactions, (String)"transactions");
        this.outlineDigest = (OutlineDigest)Guard.object((Object)outlineDigest, (String)"outlineDigest");
    }

    @Override
    public <T> T inTransaction(Supplier<T> work) {
        Guard.object(work, (String)"work");
        return (T)this.transactions.run(this.database, open -> work.get());
    }

    @Override
    public String liveBaseline(ArtifactKind kind, String scopeId) {
        Guard.object((Object)((Object)kind), (String)"kind");
        Guard.id((String)scopeId, (String)"scopeId");
        if (kind.scope == ArtifactKind.Scope.TOPIC) {
            return this.outlineDigest.of(scopeId);
        }
        if (BranchDao.findById((SQLiteDatabase)this.database, (String)scopeId) == null) {
            return null;
        }
        if (!kind.revisionBound()) {
            if (kind.purpose == PromptPurpose.ROOT_TITLE) {
                for (Message message : MessageDao.findByBranch((SQLiteDatabase)this.database, (String)scopeId)) {
                    if (!message.isUser()) continue;
                    return ContextSnapshotCodec.digestOf((String)ArtifactInputs.rootTitle(message.userContent));
                }
            }
            return scopeId;
        }
        String latest = null;
        for (Message message : MessageDao.findByBranch((SQLiteDatabase)this.database, (String)scopeId)) {
            if (message.activeRevisionId() == null) continue;
            latest = message.activeRevisionId();
        }
        return latest;
    }

    @Override
    public void write(AiArtifactDao.Row row) {
        Guard.object((Object)row, (String)"row");
        if (AiArtifactDao.findByKindAndScope((SQLiteDatabase)this.database, (String)row.kind, (String)row.scopeId) == null) {
            AiArtifactDao.insert((SQLiteDatabase)this.database, (AiArtifactDao.Row)row);
            return;
        }
        if (ArtifactKind.fromWireId(row.kind).revisionBound() && AiArtifactDao.applyIfBaselineMatches((SQLiteDatabase)this.database, (AiArtifactDao.Row)row) == AiArtifactDao.Writeback.APPLIED) {
            return;
        }
        AiArtifactDao.deleteByScope((SQLiteDatabase)this.database, (String)row.kind, (String)row.scopeId);
        AiArtifactDao.insert((SQLiteDatabase)this.database, (AiArtifactDao.Row)row);
    }

    public static interface OutlineDigest {
        public String of(String var1);
    }
}

