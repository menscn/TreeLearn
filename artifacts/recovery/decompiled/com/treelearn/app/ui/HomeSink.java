/*
 * Decompiled with CFR 0.152.
 */
package com.treelearn.app.ui;

import com.treelearn.app.ui.HomeViewState;

public interface HomeSink {
    public void render(HomeViewState var1);

    public void openSettings();

    public void openBranch(String var1);

    public void openArchived(String var1);

    public void openSearch();
}

