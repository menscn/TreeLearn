package com.treelearn.app.navigation;

/**
 * What the navigation screen may ask the presenter to do.
 *
 * <p>It exists so the view depends on a contract instead of on {@link NavigationPresenter}: the
 * screen forwards a gesture, the presenter decides, and the decision is asserted in a JVM test.
 */
public interface NavigationActions {

    /** How much of the tree one permanent deletion removes. */
    enum DeleteScope {
        /** The tapped branch and every branch under it. */
        SUBTREE,
        /** The whole topic. */
        TOPIC
    }

    void onBranchSelected(String branchId);

    void onArchive(String branchId);

    void onRestore(String branchId);

    /** Opens the confirmation. Nothing is written until {@link #onDeleteConfirmed()}. */
    void onDeleteRequested(String branchId, DeleteScope scope);

    void onDeleteConfirmed();

    void onDeleteCancelled();

    /** Folding or unfolding a group on the list. A view fact: it is never written to the store. */
    void onGroupToggled(String branchId);

    /** Folding or unfolding the branch-summary strip above the list. */
    void onSummaryToggled();
}
