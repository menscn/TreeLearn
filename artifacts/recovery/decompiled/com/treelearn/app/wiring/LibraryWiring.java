/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.treelearn.core.domain.Guard
 *  com.treelearn.data.db.dao.TopicDao$Overview
 */
package com.treelearn.app.wiring;

import com.treelearn.app.library.LibraryData;
import com.treelearn.app.library.LibraryViewState;
import com.treelearn.app.navigation.NavigationData;
import com.treelearn.app.wiring.BranchHeading;
import com.treelearn.app.wiring.SqliteLibraryStore;
import com.treelearn.core.domain.Guard;
import com.treelearn.data.db.dao.TopicDao;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

public final class LibraryWiring
implements LibraryData {
    private static final String PATH_SEPARATOR = " \u203a ";
    private final SqliteLibraryStore store;
    private final NavigationData navigation;

    public LibraryWiring(SqliteLibraryStore store, NavigationData navigation) {
        this.store = (SqliteLibraryStore)Guard.object((Object)store, (String)"store");
        this.navigation = (NavigationData)Guard.object((Object)navigation, (String)"navigation");
    }

    @Override
    public List<LibraryViewState.Row> topics() {
        List<TopicDao.Overview> read = this.store.topics();
        ArrayList<LibraryViewState.Row> rows = new ArrayList<LibraryViewState.Row>(read.size());
        for (TopicDao.Overview row : read) {
            rows.add(new LibraryViewState.Row(row.topicId, row.resumeBranchId, BranchHeading.of(row.rootTitle, row.rootQuestion), row.branchCount, row.unreadCount, row.lastUsedAt));
        }
        return rows;
    }

    @Override
    public List<LibraryViewState.ArchiveRow> archived() {
        List<SqliteLibraryStore.ArchivedBranch> read = this.store.archived();
        ArrayList<LibraryViewState.ArchiveRow> rows = new ArrayList<LibraryViewState.ArchiveRow>(read.size());
        HashMap<String, NavigationData.Snapshot> trees = new HashMap<String, NavigationData.Snapshot>();
        for (SqliteLibraryStore.ArchivedBranch branch : read) {
            NavigationData.Snapshot tree = (NavigationData.Snapshot)trees.get(branch.topicId);
            if (tree == null) {
                tree = this.navigation.load(branch.topicId);
                trees.put(branch.topicId, tree);
            }
            rows.add(new LibraryViewState.ArchiveRow(branch.branchId, branch.topicId, BranchHeading.of(branch.title, null), LibraryWiring.sourcePath(tree, branch.branchId), this.summaryOf(branch.branchId)));
        }
        return rows;
    }

    @Override
    public NavigationData.Snapshot snapshot(String topicId) {
        return this.navigation.load(topicId);
    }

    @Override
    public void restore(String branchId, long now) {
        this.navigation.setArchived(branchId, false, now);
    }

    @Override
    public int cancelInFlight(List<String> branchIds) {
        return this.navigation.cancelInFlight(branchIds);
    }

    @Override
    public void delete(NavigationData.Plan plan) {
        this.navigation.delete(plan);
    }

    private static String sourcePath(NavigationData.Snapshot tree, String branchId) {
        NavigationData.Node branch = tree.node(branchId);
        if (branch == null || branch.isRoot()) {
            return "";
        }
        ArrayList<String> names = new ArrayList<String>();
        NavigationData.Node node = tree.node(branch.parentBranchId);
        while (node != null) {
            names.add(node.title);
            node = node.parentBranchId == null ? null : tree.node(node.parentBranchId);
        }
        Collections.reverse(names);
        StringBuilder path = new StringBuilder();
        for (String name : names) {
            if (path.length() > 0) {
                path.append(PATH_SEPARATOR);
            }
            path.append(name);
        }
        return path.toString();
    }

    private String summaryOf(String branchId) {
        NavigationData.Summary summary = this.navigation.summary(branchId);
        return summary == null ? null : summary.text;
    }
}

