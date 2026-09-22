package com.treelearn.app.navigation;

/**
 * What the navigation screen is asked to do besides drawing.
 *
 * <p>Navigation is a port for the same reason the data is: the presenter decides <em>when</em> the
 * screen must move, and a JVM test can assert that decision without an Activity.
 */
public interface NavigationSink {

    void render(NavigationViewState state);

    /**
     * The tree has no visible node left to show - it was deleted, or every ancestor of the deleted
     * node is archived. The screen leaves the navigation view; technical design §12 makes "back to
     * the nearest visible ancestor, otherwise home" a navigation rule rather than a UI accident.
     */
    void leaveTree();
}
