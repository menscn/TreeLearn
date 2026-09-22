package com.treelearn.app.wiring;

import com.treelearn.app.search.SearchRepository;
import com.treelearn.app.search.SqliteSearchRepository;
import com.treelearn.core.domain.Guard;
import com.treelearn.core.domain.SearchHit;
import com.treelearn.core.domain.SearchQuery;
import com.treelearn.data.db.TreeLearnDatabaseHelper;

import java.util.List;

/**
 * The search repository as the container holds it: one statement runner, with the connection opened the
 * first time a question is asked rather than when the app is assembled.
 *
 * <p>The delegation exists for that timing and nothing else. {@link SqliteSearchRepository} takes a
 * connection, and taking one in the container's constructor would open the database during
 * {@code Activity.onCreate} - on the UI thread, before any screen has asked for anything. Opening it
 * lazily puts the open on whichever caller asks first, and every caller of this port is a background
 * one.
 *
 * <p>Two builders racing would at worst build two identical repositories over the same helper; the
 * helper hands back the one connection either way, and neither copy holds state.
 */
public final class SqliteSearchStore implements SearchRepository {

    private final TreeLearnDatabaseHelper helper;

    private SearchRepository open;

    public SqliteSearchStore(TreeLearnDatabaseHelper helper) {
        this.helper = Guard.object(helper, "helper");
    }

    @Override
    public List<SearchHit> search(SearchQuery query) {
        return repository().search(query);
    }

    @Override
    public boolean isStillReachable(SearchHit hit) {
        return repository().isStillReachable(hit);
    }

    private SearchRepository repository() {
        if (open == null) {
            open = new SqliteSearchRepository(helper.getReadableDatabase());
        }
        return open;
    }
}
