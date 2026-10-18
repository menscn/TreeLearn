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

    /**
     * A read the learner was told about, with the cause kept for the log.
     *
     * <p>The screen shows a notice and cannot show a stack trace; without this port the exception
     * that produced 「本机数据读不出来」 is discarded at the catch site, so the only survivor of a
     * broken read is a message that names nothing. Same shape and same reason as
     * {@code ChatSink.reportUnreadable}. A default no-op keeps a JVM double from having to care.
     *
     * @param scope what was being read (a topic or branch id), never a message body
     * @param cause exception class and message, formatted by the presenter
     */
    default void reportUnreadable(String scope, String cause) {
    }
}
