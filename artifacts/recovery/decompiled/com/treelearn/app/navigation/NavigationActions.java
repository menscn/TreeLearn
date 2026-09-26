/*
 * Decompiled with CFR 0.152.
 */
package com.treelearn.app.navigation;

public interface NavigationActions {
    public void onBranchSelected(String var1);

    public void onArchive(String var1);

    public void onRestore(String var1);

    public void onDeleteRequested(String var1, DeleteScope var2);

    public void onDeleteConfirmed();

    public void onDeleteCancelled();

    public void onGroupToggled(String var1);

    public void onSummaryToggled();

    public static enum DeleteScope {
        SUBTREE,
        TOPIC;

    }
}

