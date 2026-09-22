package com.treelearn.app.library;

/**
 * What the library screen is asked to do besides drawing.
 *
 * <p>A port for the same reason the data is: the presenter decides <em>when</em> the screen must move,
 * and a JVM test can assert that decision without an Activity. The one move out of this page is a
 * topic tap, and it leaves through the same door the home card uses, so a resumed topic cannot open
 * two different ways.
 */
public interface LibrarySink {

    void render(LibraryViewState state);

    /** A tapped row: resume the topic at the branch the learner last left it on. */
    void openBranch(String branchId);
}
