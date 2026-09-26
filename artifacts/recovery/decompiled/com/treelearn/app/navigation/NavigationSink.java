/*
 * Decompiled with CFR 0.152.
 */
package com.treelearn.app.navigation;

import com.treelearn.app.navigation.NavigationViewState;

public interface NavigationSink {
    public void render(NavigationViewState var1);

    public void leaveTree();
}

