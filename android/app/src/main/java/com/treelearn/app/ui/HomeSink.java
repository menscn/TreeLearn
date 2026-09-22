package com.treelearn.app.ui;

/**
 * What the home screen is asked to do besides drawing.
 *
 * <p>Navigation is a port for the same reason the data is: the presenter decides <em>when</em> the
 * screen must move (a stored key is missing, a first topic was accepted, a topic card was tapped)
 * and a test can assert that decision without an Activity.
 */
public interface HomeSink {

    void render(HomeViewState state);

    /** Opens the settings screen - the PRD's answer to a missing key (6.2.13 exception 1). */
    void openSettings();

    /** Enters a branch: the resume branch of a tapped topic, or the root branch just created. */
    void openBranch(String branchId);

    /**
     * Opens the branch list of one topic, entering on the branch it was given.
     *
     * <p>The home page holds no tree of its own, so the branch is the topic's resume branch: the page
     * then lists the topic the learner last used, which is the one whose rows are on screen behind it.
     */
    void openArchived(String branchId);

    /**
     * Opens the search page.
     *
     * <p>Search spans every branch, so - unlike {@link #openArchived(String)} - this entry needs no
     * topic to have been created first: an empty home is exactly where a learner has something to look
     * for and nothing to browse.
     */
    void openSearch();
}
