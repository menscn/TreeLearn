package com.treelearn.app.search;

/**
 * The one question the search page asks a store: what is this branch called?
 *
 * <p>A hit carries ids and a snippet and no title - the search statement joins no branch row - so
 * naming a row is a second question, and this is its single owner. On the device that owner is the
 * conversation store, which already reads branch rows. Nothing here writes, and nothing here decides
 * whether a hit is worth showing: the search repository answered that before the row was built.
 *
 * <p>T-054 adds the siting question a search row asks when it is shown inside the library: where in
 * the tree does this branch sit, and is it archived? The same "one question per branch" rule holds -
 * the page asks once per distinct branch and caches, never once per row.
 */
public interface BranchTitles {

    /** @return the branch's title, or null when no row carries the id */
    String titleOf(String branchId);

    /**
     * The branch's ancestor path and archived state, or null when this port cannot answer.
     *
     * <p>The path names the ancestors only, not the branch itself - the row already shows the
     * branch's own title next to it. A null answer degrades to an empty path, the same way a
     * missing title does: a hit is never dropped for being hard to name.
     */
    default Badge badgeOf(String branchId) {
        return null;
    }

    /** Where one hit's branch sits in the tree, and whether it is currently archived. */
    final class Badge {

        public final String path;
        public final boolean archived;

        public Badge(String path, boolean archived) {
            this.path = path == null ? "" : path;
            this.archived = archived;
        }
    }
}
