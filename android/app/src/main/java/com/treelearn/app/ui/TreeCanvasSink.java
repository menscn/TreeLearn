package com.treelearn.app.ui;

/**
 * What the canvas is asked to do besides drawing.
 *
 * <p>Opening a branch is a port rather than a call into an Activity for the same reason the data is:
 * the presenter decides <em>when</em> the learner has chosen a branch, and a JVM test can assert that
 * decision without a device. Only the branch's identifier crosses this boundary - never a node, a
 * coordinate or a message body.
 */
public interface TreeCanvasSink {

    void render(TreeCanvasViewState state);

    /** Enters the conversation on one branch. */
    void openBranch(String branchId);

    /**
     * Opens the topic's branch list, standing on one branch.
     *
     * <p>Navigation only: the canvas keeps the branch it was centred on, and the list is opened on that
     * branch rather than on the topic's root.
     */
    void openNavigation(String branchId);

    /** Leaves the canvas, back to the topic list. */
    void leaveTree();
}
