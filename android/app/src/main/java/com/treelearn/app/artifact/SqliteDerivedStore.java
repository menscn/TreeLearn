package com.treelearn.app.artifact;

import android.database.sqlite.SQLiteDatabase;

import com.treelearn.core.domain.Guard;
import com.treelearn.core.domain.Branch;
import com.treelearn.core.domain.Message;
import com.treelearn.data.db.DatabaseTransactionRunner;
import com.treelearn.data.db.dao.AiArtifactDao;
import com.treelearn.data.db.dao.BranchDao;
import com.treelearn.data.db.dao.MessageDao;
import com.treelearn.data.db.dao.TopicDao;
import com.treelearn.core.domain.ContextSnapshotCodec;
import com.treelearn.core.domain.PromptPurpose;
import java.util.function.Supplier;

/**
 * The SQLite side of {@link DerivedStore} (technical design 10): it answers the live-baseline
 * question from the tables that own the scope, and it delegates the write to {@code AiArtifactDao}.
 *
 * <p><strong>The live baseline, per kind.</strong> A revision-bound artifact is invalidated by a new
 * stable answer, so its baseline is the branch's newest message with an active revision. A
 * revision-free artifact's input is immutable, so only its scope's existence can invalidate it. A
 * topic-scoped artifact (home recommendations) is invalidated by the outline digest, which the
 * caller supplies through {@link OutlineDigest} because a topic's outline is assembled by the screen
 * layer that asks for the recommendations — the artifacts table cannot reconstruct it.
 *
 * <p><strong>A missing scope answers null, and null is never written.</strong> That is what keeps a
 * callback that lands after the learner deleted a branch or a topic from re-creating the artifact
 * {@code deleteByScope} just removed.
 */
public final class SqliteDerivedStore implements DerivedStore {

    /** The digest of a topic's current outline, or null when the topic is gone. */
    public interface OutlineDigest {

        String of(String topicId);
    }

    private final SQLiteDatabase database;

    private final DatabaseTransactionRunner transactions;

    private final OutlineDigest outlineDigest;

    public SqliteDerivedStore(SQLiteDatabase database, DatabaseTransactionRunner transactions,
            OutlineDigest outlineDigest) {
        this.database = Guard.object(database, "database");
        this.transactions = Guard.object(transactions, "transactions");
        this.outlineDigest = Guard.object(outlineDigest, "outlineDigest");
    }

    @Override
    public <T> T inTransaction(Supplier<T> work) {
        Guard.object(work, "work");
        return transactions.run(database, open -> work.get());
    }

    @Override
    public String liveBaseline(ArtifactKind kind, String scopeId) {
        Guard.object(kind, "kind");
        Guard.id(scopeId, "scopeId");
        if (kind.scope == ArtifactKind.Scope.TOPIC) {
            return outlineDigest.of(scopeId);
        }
        if (BranchDao.findById(database, scopeId) == null) {
            return null;
        }
        if (!kind.revisionBound()) {
            if (kind.purpose == PromptPurpose.ROOT_TITLE) {
                for (Message message : MessageDao.findByBranch(database, scopeId)) {
                    if (message.isUser()) {
                        return ContextSnapshotCodec.digestOf(ArtifactInputs.rootTitle(message.userContent));
                    }
                }
            }
            return scopeId;
        }
        String latest = null;
        for (Message message : MessageDao.findByBranch(database, scopeId)) {
            if (message.activeRevisionId() != null) {
                latest = message.activeRevisionId();
            }
        }
        return latest;
    }

    @Override
    public void write(AiArtifactDao.Row row) {
        Guard.object(row, "row");
        if (AiArtifactDao.findByKindAndScope(database, row.kind, row.scopeId) == null) {
            AiArtifactDao.insert(database, row);
        } else {
            if (ArtifactKind.fromWireId(row.kind).revisionBound()
                    && AiArtifactDao.applyIfBaselineMatches(database, row) == AiArtifactDao.Writeback.APPLIED) {
                return;
            }
            AiArtifactDao.deleteByScope(database, row.kind, row.scopeId);
            AiArtifactDao.insert(database, row);
        }
        nameBranchRow(row);
    }

    /**
     * Writes a derived title back onto the branch row itself, so every reader of the branch sees
     * one name instead of two - the artifact is the source, the branch column is its echo.
     */
    private void nameBranchRow(AiArtifactDao.Row row) {
        String title = TitleArtifactApplier.titleOf(ArtifactKind.fromWireId(row.kind), row.payload);
        if (title == null) {
            return;
        }
        Branch branch = BranchDao.findById(database, row.scopeId);
        if (branch == null) {
            return;
        }
        branch.setTitle(title, row.createdAt);
        BranchDao.update(database, branch);
    }
}
