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
}
