/*
 * Decompiled with CFR 0.152.
 */
package com.treelearn.app.ui;

import com.treelearn.app.ui.TreeCanvasViewState;

public interface TreeCanvasSink {
    public void render(TreeCanvasViewState var1);

    public void openBranch(String var1);

    public void openNavigation(String var1);

    public void leaveTree();
}

