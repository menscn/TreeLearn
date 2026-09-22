package com.treelearn.app.wiring;

import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;

import com.treelearn.app.artifact.ArtifactKind;
import com.treelearn.core.domain.Branch;
import com.treelearn.core.domain.DomainErrorCode;
import com.treelearn.core.domain.DomainResult;
import com.treelearn.core.domain.Guard;
import com.treelearn.core.domain.Message;
import com.treelearn.data.db.DatabaseTransactionRunner;
import com.treelearn.data.db.TreeLearnDatabaseHelper;
import com.treelearn.data.db.dao.AiArtifactDao;
import com.treelearn.data.db.dao.BranchDao;
import com.treelearn.data.db.dao.MessageDao;

import java.util.List;

/**
 * {@link TreeCanvasStore}'s SQLite side (technical design 12).
 *
 * <p><strong>It decides nothing either.</strong> Four statements and one transaction, each of them an
 * existing DAO call: the branch read is {@code BranchDao}'s, the summary is read by
 * {@link ArtifactKind#BRANCH_SUMMARY} so a lookup through the polymorphic {@code scope_id} cannot match
 * a title or a compression (T-024), and the fold goes through the domain's own
 * {@code Branch.setCollapsed}, so the 0/1 column rule stays where it is declared.
 *
 * <p><strong>A refusal is a value, not an exception.</strong> The canvas asks for one flag to be
 * stored; a database that will not take it is an ordinary outcome the screen renders, so a
 * {@link SQLException} comes back as {@link DomainErrorCode#OPERATION_NOT_COMPLETABLE} with a detail
 * this class wrote - never the driver's, which names the file and the statement. A branch row that is
 * gone stays an exception: that is a caller working from a stale tree, and no screen can repair it.
 */
public final class SqliteTreeCanvasStore implements TreeCanvasStore {

    /** The detail carried by a refused write; the canvas renders the category, not this text. */
    private static final String REFUSED_DETAIL = "the fold was not stored";

    private final TreeLearnDatabaseHelper helper;

    private final DatabaseTransactionRunner transactions;

    public SqliteTreeCanvasStore(TreeLearnDatabaseHelper helper,
            DatabaseTransactionRunner transactions) {
        this.helper = Guard.object(helper, "helper");
        this.transactions = Guard.object(transactions, "transactions");
    }

    @Override
    public String topicOf(String branchId) {
        Branch branch = BranchDao.findById(database(), branchId);
        return branch == null ? null : branch.topicId;
    }

    @Override
    public List<Branch> branchesOf(String topicId) {
        return BranchDao.findByTopic(database(), topicId);
    }

    @Override
    public String summaryPayload(String branchId) {
        AiArtifactDao.Row row = AiArtifactDao.findByKindAndScope(database(),
                ArtifactKind.BRANCH_SUMMARY.wireId, branchId);
        return row == null ? null : row.payload;
    }

    /**
     * The branch's committed rounds: one per stored learner turn, counted row by row over the positions
     * the domain marks as the learner's own ({@code Message.isUser()}).
     *
     * <p>What is counted is the learner's <em>question</em> row, and never the answer row stored beside
     * it. {@code ChatStore.appendTurn} writes a question together with the answer <em>position</em> it is
     * answered at, in one transaction or neither of them, so one question row is one committed turn and
     * the answer's own content is not consulted. {@code ChatWiring.send} stores that turn before the
     * generation attempt is admitted, so a turn is counted from the moment its question is committed -
     * while its answer is still being generated, and equally when the attempt was refused, whose answer
     * position then reads back FAILED and stays retryable.
     */
    @Override
    public int committedRounds(String branchId) {
        int rounds = 0;
        for (Message row : MessageDao.findByBranch(database(), branchId)) {
            if (row.isUser()) {
                rounds++;
            }
        }
        return rounds;
    }

    @Override
    public DomainResult<Void> setCollapsed(String branchId, boolean collapsed, long now) {
        Guard.id(branchId, "branchId");
        final SQLiteDatabase database = database();
        try {
            transactions.run(database, open -> {
                Branch branch = BranchDao.findById(open, branchId);
                if (branch == null) {
                    throw new IllegalStateException("branch " + branchId
                            + " is gone, so there is no fold to store for it");
                }
                branch.setCollapsed(collapsed, now);
                BranchDao.update(open, branch);
                return null;
            });
        } catch (SQLException refused) {
            return DomainResult.fail(DomainErrorCode.OPERATION_NOT_COMPLETABLE, REFUSED_DETAIL);
        }
        return DomainResult.ok();
    }

    private SQLiteDatabase database() {
        return helper.getWritableDatabase();
    }
}
