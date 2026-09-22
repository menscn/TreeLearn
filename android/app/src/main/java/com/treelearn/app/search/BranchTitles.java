package com.treelearn.app.search;

/**
 * The one question the search page asks a store: what is this branch called?
 *
 * <p>A hit carries ids and a snippet and no title - the search statement joins no branch row - so
 * naming a row is a second question, and this is its single owner. On the device that owner is the
 * conversation store, which already reads branch rows. Nothing here writes, and nothing here decides
 * whether a hit is worth showing: the search repository answered that before the row was built.
 */
public interface BranchTitles {

    /** @return the branch's title, or null when no row carries the id */
    String titleOf(String branchId);
}
