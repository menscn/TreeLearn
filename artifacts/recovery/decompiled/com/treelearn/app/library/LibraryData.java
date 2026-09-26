/*
 * Decompiled with CFR 0.152.
 */
package com.treelearn.app.library;

import com.treelearn.app.library.LibraryViewState;
import com.treelearn.app.navigation.NavigationData;
import java.util.List;

public interface LibraryData {
    public List<LibraryViewState.Row> topics();

    public List<LibraryViewState.ArchiveRow> archived();

    public NavigationData.Snapshot snapshot(String var1);

    public void restore(String var1, long var2);

    public int cancelInFlight(List<String> var1);

    public void delete(NavigationData.Plan var1);
}

