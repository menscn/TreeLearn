/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  android.database.sqlite.SQLiteDatabase
 *  com.treelearn.core.domain.Branch
 *  com.treelearn.core.domain.Guard
 *  com.treelearn.core.domain.SearchHit
 *  com.treelearn.core.domain.SearchQuery
 *  com.treelearn.data.db.dao.BranchDao
 *  com.treelearn.data.db.dao.MessageDao
 *  com.treelearn.data.db.dao.SearchDao
 */
package com.treelearn.app.search;

import android.database.sqlite.SQLiteDatabase;
import com.treelearn.app.search.SearchRepository;
import com.treelearn.core.domain.Branch;
import com.treelearn.core.domain.Guard;
import com.treelearn.core.domain.SearchHit;
import com.treelearn.core.domain.SearchQuery;
import com.treelearn.data.db.dao.BranchDao;
import com.treelearn.data.db.dao.MessageDao;
import com.treelearn.data.db.dao.SearchDao;
import java.util.List;

public final class SqliteSearchRepository
implements SearchRepository {
    private final SQLiteDatabase database;

    public SqliteSearchRepository(SQLiteDatabase database) {
        this.database = (SQLiteDatabase)Guard.object((Object)database, (String)"database");
    }

    @Override
    public List<SearchHit> search(SearchQuery query) {
        return SearchDao.search((SQLiteDatabase)this.database, (SearchQuery)((SearchQuery)Guard.object((Object)query, (String)"query")));
    }

    @Override
    public boolean isStillReachable(SearchHit hit) {
        Guard.object((Object)hit, (String)"hit");
        Branch branch = BranchDao.findById((SQLiteDatabase)this.database, (String)hit.branchId);
        if (branch == null || branch.isArchived()) {
            return false;
        }
        if (hit.messageId == null) {
            return true;
        }
        return MessageDao.findById((SQLiteDatabase)this.database, (String)hit.messageId) != null;
    }
}

