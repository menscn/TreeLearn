package com.treelearn.app.wiring;

import com.treelearn.app.artifact.ArtifactKind;
import com.treelearn.core.domain.Branch;
import com.treelearn.data.db.TreeLearnDatabaseHelper;
import com.treelearn.data.db.dao.BranchDao;
import com.treelearn.data.db.dao.TopicDao;

import java.util.ArrayList;
import java.util.List;

/**
 * The library list's rows, read from the real local database.
 *
 * <p>It owns no rule. The topic list is one statement in {@link TopicDao} - the same statement the home
 * list reads - and this class only hands it a connection, so the library and the home page cannot
 * come to disagree about what a topic row is.
 *
 * <p><strong>Read-only by construction.</strong> This slice adds no table, no column and no
 * migration: it reads {@code topics} joined with the root branch and its summary artifact, and the
 * branches of each topic, exactly as stored. There is no write method here to misuse, which is why
 * the class takes no transaction runner - an unwrapped single-row write would be the only thing it
 * could be for.
 */
public final class SqliteLibraryStore {

    private final TreeLearnDatabaseHelper helper;

    public SqliteLibraryStore(TreeLearnDatabaseHelper helper) {
        if (helper == null) {
            throw new IllegalArgumentException("helper must not be null");
        }
        this.helper = helper;
    }

    /** The topic rows the library draws, newest first, with the root heading among them. */
    public List<TopicDao.Overview> topics() {
        return TopicDao.listOverviews(helper.getWritableDatabase(),
                ArtifactKind.BRANCH_SUMMARY.wireId);
    }

    /**
     * The archived branches of every topic, as the store holds them.
     *
     * <p>This is the completed segment's own read, and it is deliberately a thin one: identity,
     * topic and title, nothing derived. Where a branch came from is answered in {@link LibraryWiring}
     * from the same snapshot that counts its subtree, so the row's ancestry and its deletion scope
     * cannot come from two different trees.
     */
    public List<ArchivedBranch> archived() {
        List<ArchivedBranch> found = new ArrayList<ArchivedBranch>();
        for (TopicDao.Overview topic : topics()) {
            for (Branch branch : BranchDao.findByTopic(helper.getWritableDatabase(), topic.topicId)) {
                if (branch.isArchived()) {
                    found.add(new ArchivedBranch(topic.topicId, branch.id, branch.title()));
                }
            }
        }
        return found;
    }

    /** One archived branch as the store holds it: which topic it belongs to, and its stored title. */
    public static final class ArchivedBranch {

        public final String topicId;
        public final String branchId;
        /** The branch's stored title, or null while it has not been derived. */
        public final String title;

        ArchivedBranch(String topicId, String branchId, String title) {
            this.topicId = topicId;
            this.branchId = branchId;
            this.title = title;
        }
    }
}
