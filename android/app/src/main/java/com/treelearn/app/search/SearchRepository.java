package com.treelearn.app.search;

import com.treelearn.core.domain.SearchHit;
import com.treelearn.core.domain.SearchQuery;

import java.util.List;

/**
 * What the search screen asks the store, behind one port (technical design 6.3).
 *
 * <p>The port exists for the same reason {@code HomeData} does: the presenter must be exercisable on
 * the JVM, and — more importantly here — the design says a future n-gram index replaces the query
 * <em>inside the repository</em> without the screen or the use cases noticing. That promise is only
 * keepable if the screen never sees SQL, so it never does.
 */
public interface SearchRepository {

    /**
     * The hits for one normalised query, in {@link SearchHit#DISPLAY_ORDER}, never null.
     *
     * <p>May throw: a store that cannot be read is a state the screen shows, not one it hides.
     */
    List<SearchHit> search(SearchQuery query);

    /**
     * Whether a hit from an earlier result list can still be opened.
     *
     * <p>A result list is a snapshot, and a branch can be archived or deleted between the query and
     * the tap. Asking here — rather than navigating and failing — keeps the answer where the data is
     * and lets the screen drop the row instead of showing a broken one.
     */
    boolean isStillReachable(SearchHit hit);
}