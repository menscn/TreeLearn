package com.treelearn.app.reading;

import android.database.sqlite.SQLiteDatabase;

import com.treelearn.core.domain.Branch;
import com.treelearn.core.domain.Guard;
import com.treelearn.core.domain.ReadingAnchor;
import com.treelearn.data.db.TreeLearnDatabaseHelper;
import com.treelearn.data.db.dao.BranchDao;
import com.treelearn.data.db.dao.ReadingPositionDao;

/**
 * The SQLite side of {@link ReadingStore}: the reading position comes from
 * {@code ReadingPositionDao}, the seen sequence from the branch row {@code BranchDao} already owns.
 *
 * <p>It writes no SQL of its own. The seen update goes through the same {@code BranchDao.update} the
 * rest of the app uses, so a branch cannot end up with two writers that disagree about which columns
 * a session may change.
 *
 * <p>It holds the helper rather than an open database so that the connection is opened by the first
 * call, on whatever thread made it: every caller is a background executor, and opening a database in a
 * constructor that a screen happens to run would be the UI-thread read this slice exists to avoid.
 */
public final class SqliteReadingStore implements ReadingStore {

    private final TreeLearnDatabaseHelper helper;

    public SqliteReadingStore(TreeLearnDatabaseHelper helper) {
        this.helper = Guard.object(helper, "helper");
    }

    @Override
    public ReadingAnchor anchorOf(String branchId) {
        return ReadingPositionDao.read(database(), branchId);
    }

    @Override
    public void saveAnchor(String branchId, ReadingAnchor anchor) {
        ReadingPositionDao.write(database(), branchId, Guard.object(anchor, "anchor"));
    }

    @Override
    public void saveSeen(Branch branch) {
        BranchDao.update(database(), Guard.object(branch, "branch"));
    }

    private SQLiteDatabase database() {
        return helper.getWritableDatabase();
    }
}
