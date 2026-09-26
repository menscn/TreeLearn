/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.treelearn.core.domain.Guard
 *  com.treelearn.core.domain.SearchHit
 *  com.treelearn.core.domain.SearchQuery
 *  com.treelearn.data.db.TreeLearnDatabaseHelper
 */
package com.treelearn.app.wiring;

import com.treelearn.app.search.SearchRepository;
import com.treelearn.app.search.SqliteSearchRepository;
import com.treelearn.core.domain.Guard;
import com.treelearn.core.domain.SearchHit;
import com.treelearn.core.domain.SearchQuery;
import com.treelearn.data.db.TreeLearnDatabaseHelper;
import java.util.List;

public final class SqliteSearchStore
implements SearchRepository {
    private final TreeLearnDatabaseHelper helper;
    private SearchRepository open;

    public SqliteSearchStore(TreeLearnDatabaseHelper helper) {
        this.helper = (TreeLearnDatabaseHelper)Guard.object((Object)helper, (String)"helper");
    }

    @Override
    public List<SearchHit> search(SearchQuery query) {
        return this.repository().search(query);
    }

    @Override
    public boolean isStillReachable(SearchHit hit) {
        return this.repository().isStillReachable(hit);
    }

    private SearchRepository repository() {
        if (this.open == null) {
            this.open = new SqliteSearchRepository(this.helper.getReadableDatabase());
        }
        return this.open;
    }
}

