package com.treelearn.app.ui;

/**
 * Where a presenter hands a rendered state back to the view.
 *
 * <p>It exists so that a presenter can be exercised on the JVM: tests pass a recording sink and read
 * the states a screen would have been asked to draw, without an Activity, a Looper or a device.
 */
public interface UiThread {

    /** Runs the action on the thread that owns the views. */
    void post(Runnable action);
}