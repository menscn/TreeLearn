package com.treelearn.app.wiring;

import com.treelearn.app.library.LibraryData;
import com.treelearn.app.library.LibraryViewState;
import com.treelearn.app.navigation.NavigationData;
import com.treelearn.core.domain.Guard;
import com.treelearn.data.db.dao.TopicDao;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The library's real data path: one port, one read-only store, and the navigation port's two writes.
 *
 * <p><strong>The row is named once.</strong> A tree's display name is {@link BranchHeading}'s answer,
 * not this class's: the same rule names the home card and the conversation heading, and a third copy
 * here is exactly how the library would come to call a topic something the conversation does not.
 * Everything else is copied across unchanged.
 *
 * <p><strong>Why the mapping happens in wiring rather than in the screen.</strong> The screen may own
 * no rule, and the SQL half may own no rule either - it can only say what the columns hold. The names
 * a reader sees are therefore resolved here, which is the same split {@code HomeWiring} makes between
 * its {@code HomeStore} and the cards the presenter renders.
 *
 * <p><strong>The completed segment borrows the navigation port rather than growing a second one.</strong>
 * An archived branch's ancestry is read from the same {@link NavigationData.Snapshot} the deletion
 * scope will be counted from, and its restore and delete are the navigation store's own
 * {@code setArchived} and {@code delete}. Nothing here walks a tree, and nothing here decides which
 * branches a subtree is.
 */
public final class LibraryWiring implements LibraryData {

    /** Joins the ancestors of an archived branch, root first. */
    private static final String PATH_SEPARATOR = " \u203a ";

    private final SqliteLibraryStore store;
    private final NavigationData navigation;

    public LibraryWiring(SqliteLibraryStore store, NavigationData navigation) {
        this.store = Guard.object(store, "store");
        this.navigation = Guard.object(navigation, "navigation");
    }

    @Override
    public List<LibraryViewState.Row> topics() {
        List<TopicDao.Overview> read = store.topics();
        List<LibraryViewState.Row> rows = new ArrayList<LibraryViewState.Row>(read.size());
        for (TopicDao.Overview row : read) {
            rows.add(new LibraryViewState.Row(row.topicId, row.resumeBranchId,
                    BranchHeading.of(row.rootTitle, row.rootQuestion), row.branchCount,
                    row.unreadCount, row.lastUsedAt));
        }
        return rows;
    }

    @Override
    public List<LibraryViewState.ArchiveRow> archived() {
        List<SqliteLibraryStore.ArchivedBranch> read = store.archived();
        List<LibraryViewState.ArchiveRow> rows = new ArrayList<LibraryViewState.ArchiveRow>(read.size());
        // One snapshot per topic, shared by its rows: the ancestry a row shows and the subtree a
        // confirmation counts are then the same tree, read once.
        Map<String, NavigationData.Snapshot> trees = new HashMap<String, NavigationData.Snapshot>();
        for (SqliteLibraryStore.ArchivedBranch branch : read) {
            NavigationData.Snapshot tree = trees.get(branch.topicId);
            if (tree == null) {
                tree = navigation.load(branch.topicId);
                trees.put(branch.topicId, tree);
            }
            rows.add(new LibraryViewState.ArchiveRow(branch.branchId, branch.topicId,
                    BranchHeading.of(branch.title, null), sourcePath(tree, branch.branchId),
                    summaryOf(branch.branchId)));
        }
        return rows;
    }

    @Override
    public NavigationData.Snapshot snapshot(String topicId) {
        return navigation.load(topicId);
    }

    @Override
    public void restore(String branchId, long now) {
        navigation.setArchived(branchId, false, now);
    }

    @Override
    public int cancelInFlight(List<String> branchIds) {
        return navigation.cancelInFlight(branchIds);
    }

    @Override
    public void delete(NavigationData.Plan plan) {
        navigation.delete(plan);
    }

    /**
     * @return the chain of titles from the topic's root down to the branch's own parent, or the empty
     *         string when the tree does not hold the branch - a row with no ancestry says so by saying
     *         nothing, which is the same rule an absent summary follows
     */
    private static String sourcePath(NavigationData.Snapshot tree, String branchId) {
        NavigationData.Node branch = tree.node(branchId);
        if (branch == null || branch.isRoot()) {
            return "";
        }
        List<String> names = new ArrayList<String>();
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

    /** @return the branch's own summary text, or null while it has none */
    private String summaryOf(String branchId) {
        NavigationData.Summary summary = navigation.summary(branchId);
        return summary == null ? null : summary.text;
    }
}
