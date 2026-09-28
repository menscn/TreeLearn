package com.treelearn.app.ui;

/**
 * Every gesture the canvas turns into a decision.
 *
 * <p>Pan and zoom arrive as deltas around a focus point rather than as a matrix, because the matrix is
 * the presenter's to keep: it is the one value that has to survive a rotation, and a view that owned it
 * would have to reload it from the screen it was just detached from.
 */
public interface TreeCanvasActions {

    /** One-finger drag, in screen pixels. */
    void onPan(float dx, float dy);

    /** Pinch, as a multiplier around the point the fingers are centred on. */
    void onZoom(float factor, float focusX, float focusY);

    /** The drawable area changed: a rotation, a split-screen resize, a window first laid out. */
    void onViewportChanged(float width, float height);

    /** The explicit "fit the whole tree" control. */
    void onFit();

    /** Centres the current branch without changing the learner's zoom. */
    default void onCenterCurrent() {
    }

    /** A tap in screen pixels; the presenter inverts its own matrix before hit-testing. */
    void onTap(float screenX, float screenY);

    void onDismissPopup();

    /** Folds an unfolded branch and unfolds a folded one. */
    void onCollapseToggled(String branchId);

    /** Enters the conversation on the selected branch. */
    void onOpenCurrent();

    /** The navigation entry: opens the topic's branch list, standing on the selected branch. */
    void onNavigationPressed();

    void onBackPressed();
}
