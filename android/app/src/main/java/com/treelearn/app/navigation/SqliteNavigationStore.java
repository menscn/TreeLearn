package com.treelearn.app.navigation;

import android.database.sqlite.SQLiteDatabase;

import com.treelearn.app.artifact.ArtifactKind;
import com.treelearn.app.artifact.ArtifactPayloadCodec;
import com.treelearn.app.artifact.DerivedResult;
import com.treelearn.app.generation.GenerationCoordinator;
import com.treelearn.core.domain.Branch;
import com.treelearn.core.domain.Guard;
import com.treelearn.data.db.DatabaseTransactionRunner;
import com.treelearn.data.db.TreeLearnDatabaseHelper;
import com.treelearn.data.db.dao.AiArtifactDao;
import com.treelearn.data.db.dao.BranchDao;
import com.treelearn.data.db.dao.GenerationOperationDao;
import com.treelearn.data.db.dao.GenerationOperationRow;
import com.treelearn.data.db.dao.TopicDao;

import java.util.ArrayList;
import java.util.List;

/**
 * The SQLite side of {@link NavigationData} (technical design §12).
 *
 * <p><strong>Three acts, three shapes.</strong> Archiving reads the branch, flips {@code archived_at}
 * through the domain and writes one row back - it never touches a message, a revision or an attempt,
 * which is what makes "archiving does not hide the branch's own history" true by construction.
 * Cancelling asks the coordinator, which owns the live attempts, so the store does not need a second
 * registry of what is running. Deleting removes the plan's branches inside one transaction and lets
 * the schema's {@code ON DELETE CASCADE} take the messages, revisions, operations and artifacts with
 * them, so the tree is never left half removed.
 *
 * <p><strong>The summary is read by kind, not by scope alone.</strong> {@code ai_artifacts.scope_id}
 * is polymorphic (T-024), so a lookup by id could match a title or a compression. Naming
 * {@link ArtifactKind#BRANCH_SUMMARY} in the query is what makes "the strip shows this branch's own
 * summary and nothing else" a property of the read rather than of the caller.
 *
 * <p>A decoded row that is not shaped like a summary is treated as absent rather than raised: the
 * codec validated it before it was stored, so a row that no longer parses is a store-side defect the
 * navigation view must not crash on, and the presenter's rule for an absent summary is to show
 * nothing.
 */
public final class SqliteNavigationStore implements NavigationData {

    /** {@code BRANCH_SUMMARY} carries {@code key_points}, not {@code items}: no array ceiling applies. */
    private static final int NO_ITEM_CEILING = Integer.MAX_VALUE;

    private final TreeLearnDatabaseHelper helper;

    private final DatabaseTransactionRunner transactions;

    private final GenerationCoordinator coordinator;

    public SqliteNavigationStore(TreeLearnDatabaseHelper helper,
            DatabaseTransactionRunner transactions, GenerationCoordinator coordinator) {
        this.helper = Guard.object(helper, "helper");
        this.transactions = Guard.object(transactions, "transactions");
        this.coordinator = Guard.object(coordinator, "coordinator");
    }

    @Override
    public String topicOf(String branchId) {
        Branch branch = BranchDao.findById(database(), branchId);
        return branch == null ? null : branch.topicId;
    }

    @Override
    public Snapshot load(String topicId) {
        List<Node> nodes = new ArrayList<Node>();
        for (Branch branch : BranchDao.findByTopic(database(), topicId)) {
            nodes.add(new Node(branch.id, branch.parentBranchId, branch.title(),
                    branch.isArchived()));
        }
        return new Snapshot(topicId, nodes);
    }

    @Override
    public Summary summary(String branchId) {
        AiArtifactDao.Row row = AiArtifactDao.findByKindAndScope(database(),
                ArtifactKind.BRANCH_SUMMARY.wireId, branchId);
        if (row == null) {
            return null;
        }
        DerivedResult parsed = ArtifactPayloadCodec.parse(ArtifactKind.BRANCH_SUMMARY, row.payload,
                NO_ITEM_CEILING);
        if (!parsed.isUsable()) {
            return null;
        }
        return new Summary(branchId, parsed.payload().summary, parsed.payload().keyPoints);
    }

    @Override
    public void setArchived(String branchId, boolean archived, long now) {
        Guard.id(branchId, "branchId");
        transactions.run(database(), open -> {
            Branch branch = BranchDao.findById(open, branchId);
            if (branch == null) {
                throw new IllegalStateException("branch " + branchId
                        + " is gone, so there is nothing left to archive or restore");
            }
            if (archived) {
                branch.archive(now);
            } else {
                branch.unarchive(now);
            }
            BranchDao.update(open, branch);
            return null;
        });
    }

    @Override
    public int cancelInFlight(List<String> branchIds) {
        Guard.object(branchIds, "branchIds");
        int cancelled = 0;
        for (String branchId : branchIds) {
            GenerationOperationRow active = GenerationOperationDao.findActiveByBranch(database(),
                    branchId);
            if (active != null && coordinator.cancel(active.id)) {
                cancelled++;
            }
        }
        return cancelled;
    }

    @Override
    public void delete(Plan plan) {
        Guard.object(plan, "plan");
        transactions.run(database(), open -> {
            if (plan.wholeTopic) {
                TopicDao.delete(open, plan.topicId);
                return null;
            }
            for (String branchId : plan.branchIds) {
                BranchDao.delete(open, branchId);
            }
            return null;
        });
    }

    private SQLiteDatabase database() {
        return helper.getWritableDatabase();
    }
}
