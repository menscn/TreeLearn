/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  android.database.sqlite.SQLiteDatabase
 *  com.treelearn.core.domain.Branch
 *  com.treelearn.core.domain.Guard
 *  com.treelearn.core.domain.ReadingAnchor
 *  com.treelearn.data.db.TreeLearnDatabaseHelper
 *  com.treelearn.data.db.dao.BranchDao
 *  com.treelearn.data.db.dao.ReadingPositionDao
 */
package com.treelearn.app.reading;

import android.database.sqlite.SQLiteDatabase;
import com.treelearn.app.reading.ReadingStore;
import com.treelearn.core.domain.Branch;
import com.treelearn.core.domain.Guard;
import com.treelearn.core.domain.ReadingAnchor;
import com.treelearn.data.db.TreeLearnDatabaseHelper;
import com.treelearn.data.db.dao.BranchDao;
import com.treelearn.data.db.dao.ReadingPositionDao;

public final class SqliteReadingStore
implements ReadingStore {
    private final TreeLearnDatabaseHelper helper;

    public SqliteReadingStore(TreeLearnDatabaseHelper helper) {
        this.helper = (TreeLearnDatabaseHelper)Guard.object((Object)helper, (String)"helper");
    }

    @Override
    public ReadingAnchor anchorOf(String branchId) {
        return ReadingPositionDao.read((SQLiteDatabase)this.database(), (String)branchId);
    }

    @Override
    public void saveAnchor(String branchId, ReadingAnchor anchor) {
        ReadingPositionDao.write((SQLiteDatabase)this.database(), (String)branchId, (ReadingAnchor)((ReadingAnchor)Guard.object((Object)anchor, (String)"anchor")));
    }

    @Override
    public void saveSeen(Branch branch) {
        BranchDao.update((SQLiteDatabase)this.database(), (Branch)((Branch)Guard.object((Object)branch, (String)"branch")));
    }

    private SQLiteDatabase database() {
        return this.helper.getWritableDatabase();
    }
}

