package com.treelearn.app.search;

import android.database.sqlite.SQLiteDatabase;

import com.treelearn.core.domain.Branch;
import com.treelearn.core.domain.Guard;
import com.treelearn.core.domain.SearchHit;
import com.treelearn.core.domain.SearchQuery;
import com.treelearn.data.db.dao.BranchDao;
import com.treelearn.data.db.dao.MessageDao;
import com.treelearn.data.db.dao.SearchDao;

import java.util.List;

/**
 * The SQLite side of {@link SearchRepository}: it delegates the statement to {@code SearchDao} and
 * answers the reachability question from the two tables that own the answer.
 *
 * <p>It holds no SQL of its own, which is the point — the {@code :data} DAO is the one place a column
 * name appears, and this class would be the first place a second copy of the search rule appeared if
 * it were allowed to write one.
 *
 * <p>A branch row that cannot be rehydrated propagates its failure: {@code BranchDao.findById} refuses
 * a non-root branch with no birth snapshot rather than handing back something half-built, and a
 * search result must not be the one path that papers over that.
 */
public final class SqliteSearchRepository implements SearchRepository {

    private final SQLiteDatabase database;

    public SqliteSearchRepository(SQLiteDatabase database) {
        this.database = Guard.object(database, "database");
    }

    @Override
    public List<SearchHit> search(SearchQuery query) {
        return SearchDao.search(database, Guard.object(query, "query"));
    }

    @Override
    public boolean isStillReachable(SearchHit hit) {
        Guard.object(hit, "hit");
        Branch branch = BranchDao.findById(database, hit.branchId);
        if (branch == null || branch.isArchived()) {
            return false;
        }
        if (hit.messageId == null) {
            return true;
        }
        return MessageDao.findById(database, hit.messageId) != null;
    }
}