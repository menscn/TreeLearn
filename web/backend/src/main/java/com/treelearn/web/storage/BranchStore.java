package com.treelearn.web.storage;

import com.treelearn.core.domain.Branch;
import com.treelearn.core.domain.BranchAnchor;
import com.treelearn.core.domain.BranchSource;
import com.treelearn.core.domain.DomainResult;
import com.treelearn.core.domain.ReadingAnchor;
import com.treelearn.web.storage.DesktopSchema.Branches;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.List;
import java.util.Optional;

/**
 * Row access for {@code branches} (task T-2026-10-10-010).
 *
 * <p>Semantic reference — the Android {@code com.treelearn.data.db.dao} implementation it mirrors:
 * <ul>
 *   <li>{@code BranchDao.insert} / {@code findById} / {@code findByTopic} / {@code update} /
 *       {@code delete} → {@link #createRoot(String, long)} / {@link #createChild(String, BranchSource,
 *       long)} / {@link #get(String)} / {@link #listForTopic(String)} / {@link #renameTitle} and
 *       friends.</li>
 *   <li>{@code ReadingPositionDao.read} / {@code write} → {@link #readReadingPosition(String)} /
 *       {@link #writeReadingPosition(String, ReadingAnchor)}, including "the message reference decides
 *       whether a position exists" and the half-written-row refusal.</li>
 *   <li>{@code Branch.markSeenThrough} / {@code onStableAnswer} / {@code setTitle} / {@code
 *       setCollapsed} / {@code archive} / {@code unarchive} → the mutations below: the shared domain
 *       type decides <em>whether</em> a change is legal and this store only persists the decision, so
 *       no rule is written twice.</li>
 * </ul>
 *
 * <p><strong>The two-step write for a fork.</strong> {@code branches.context_snapshot_id} references
 * {@code context_snapshots}, whose {@code branch_id} references {@code branches}, so no single INSERT
 * can carry the pointer. {@link #createChild} writes the branch with its source columns and anchor but
 * leaves the pointer null, and {@link #attachContextSnapshot} completes it once the snapshot row
 * exists — the same two-step contract Android's {@code BranchCreation} owns. Reading a non-root row
 * whose pointer is null fails loudly instead of returning a provenance-less branch.
 */
public final class BranchStore {

    private static final String SELECT_ONE = "SELECT * FROM " + Branches.TABLE + " WHERE " + Branches.ID
            + " = ?";
    private static final String SELECT_CHILDREN = "SELECT * FROM " + Branches.TABLE + " WHERE "
            + Branches.PARENT_ID + " = ? ORDER BY " + Branches.CREATED_AT + " ASC, " + Branches.ID
            + " ASC";
    private static final String SELECT_FOR_TOPIC = "SELECT * FROM " + Branches.TABLE + " WHERE "
            + Branches.TOPIC_ID + " = ? ORDER BY " + Branches.CREATED_AT + " ASC";
    private static final String SELECT_DESCENDANTS = "WITH RECURSIVE sub(id) AS (SELECT " + Branches.ID
            + " FROM " + Branches.TABLE + " WHERE " + Branches.ID + " = ? UNION ALL SELECT b."
            + Branches.ID + " FROM " + Branches.TABLE + " b JOIN sub ON b." + Branches.PARENT_ID
            + " = sub.id) SELECT b.* FROM " + Branches.TABLE + " b JOIN sub ON b." + Branches.ID
            + " = sub.id ORDER BY b." + Branches.CREATED_AT + " ASC, b." + Branches.ID + " ASC";
    private static final String SELECT_PATH = "WITH RECURSIVE chain(id, depth) AS (SELECT "
            + Branches.ID + ", 0 FROM " + Branches.TABLE + " WHERE " + Branches.ID + " = ? UNION ALL"
            + " SELECT b." + Branches.PARENT_ID + ", chain.depth + 1 FROM " + Branches.TABLE
            + " b JOIN chain ON b." + Branches.ID + " = chain.id WHERE b." + Branches.PARENT_ID
            + " IS NOT NULL) SELECT b.* FROM " + Branches.TABLE + " b JOIN chain ON b." + Branches.ID
            + " = chain.id ORDER BY chain.depth DESC";
    private static final String INSERT_ROOT = "INSERT INTO " + Branches.TABLE + "(" + Branches.ID + ", "
            + Branches.TOPIC_ID + ", " + Branches.CREATED_AT + ", " + Branches.UPDATED_AT
            + ") VALUES (?, ?, ?, ?)";
    private static final String INSERT_CHILD = "INSERT INTO " + Branches.TABLE + "(" + Branches.ID + ", "
            + Branches.TOPIC_ID + ", " + Branches.PARENT_ID + ", " + Branches.SOURCE_MESSAGE_ID + ", "
            + Branches.SOURCE_REVISION_ID + ", " + Branches.ANCHOR_START_INCLUSIVE + ", "
            + Branches.ANCHOR_END_EXCLUSIVE + ", " + Branches.ANCHOR_QUOTE + ", " + Branches.ANCHOR_HASH
            + ", " + Branches.CREATED_AT + ", " + Branches.UPDATED_AT
            + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
    private static final String ATTACH_SNAPSHOT = "UPDATE " + Branches.TABLE + " SET "
            + Branches.CONTEXT_SNAPSHOT_ID + " = ? WHERE " + Branches.ID + " = ?";
    private static final String RENAME_TITLE = "UPDATE " + Branches.TABLE + " SET " + Branches.TITLE
            + " = ?, " + Branches.UPDATED_AT + " = ? WHERE " + Branches.ID + " = ?";
    private static final String SET_COLLAPSED = "UPDATE " + Branches.TABLE + " SET " + Branches.COLLAPSED
            + " = ?, " + Branches.UPDATED_AT + " = ? WHERE " + Branches.ID + " = ?";
    private static final String SET_ARCHIVED = "UPDATE " + Branches.TABLE + " SET " + Branches.ARCHIVED_AT
            + " = ?, " + Branches.UPDATED_AT + " = ? WHERE " + Branches.ID + " = ?";
    private static final String SET_LAST_SEEN = "UPDATE " + Branches.TABLE + " SET "
            + Branches.LAST_SEEN_STABLE_SEQUENCE + " = ? WHERE " + Branches.ID + " = ?";
    private static final String SET_LATEST_STABLE = "UPDATE " + Branches.TABLE + " SET "
            + Branches.LATEST_STABLE_SEQUENCE + " = ?, " + Branches.UPDATED_AT + " = ? WHERE "
            + Branches.ID + " = ?";
    private static final String SELECT_POSITION = "SELECT " + Branches.READING_ANCHOR_MESSAGE_ID + ", "
            + Branches.READING_ANCHOR_REVISION_ID + ", " + Branches.READING_LIST_ITEM_OFFSET + ", "
            + Branches.READING_VIEWPORT_OFFSET + " FROM " + Branches.TABLE + " WHERE " + Branches.ID
            + " = ?";
    private static final String WRITE_POSITION = "UPDATE " + Branches.TABLE + " SET "
            + Branches.READING_ANCHOR_MESSAGE_ID + " = ?, " + Branches.READING_ANCHOR_REVISION_ID
            + " = ?, " + Branches.READING_LIST_ITEM_OFFSET + " = ?, "
            + Branches.READING_VIEWPORT_OFFSET + " = ? WHERE " + Branches.ID + " = ?";
    private static final String DELETE_BRANCH = "DELETE FROM " + Branches.TABLE + " WHERE "
            + Branches.ID + " = ?";

    private final JdbcSupport jdbc;

    public BranchStore(JdbcSupport jdbc) {
        this.jdbc = jdbc;
    }

    /** Creates a topic's single root branch; a second one is refused by {@code uq_branches_root}. */
    public Branch createRoot(String topicId, long now) {
        Branch root = Branch.root(topicId, now);
        jdbc.update(INSERT_ROOT, rootBinder(root));
        return root;
    }

    /** Inserts an already-built root branch within the caller's transaction (the accept path). */
    public void insertRoot(Connection connection, Branch root) {
        jdbc.update(connection, INSERT_ROOT, rootBinder(root));
    }

    private static JdbcSupport.Binder rootBinder(Branch root) {
        return s -> {
            s.setString(1, root.id); s.setString(2, root.topicId);
            s.setLong(3, root.createdAt); s.setLong(4, root.updatedAt());
        };
    }

    /**
     * Writes a non-root branch with its source columns and anchor; the birth-snapshot pointer is left
     * null for {@link #attachContextSnapshot} (see the class note on the two-step write).
     */
    public Branch createChild(String topicId, BranchSource source, long now) {
        Branch child = Branch.child(topicId, source, now).value();
        jdbc.update(INSERT_CHILD, childBinder(child));
        return child;
    }

    /**
     * Transaction-scoped half of {@link #createChild(String, BranchSource, long)}: the fork entry point
     * writes the child, its birth snapshot and the pointer in one transaction (mirroring Android
     * {@code BranchCreation}), so a half-written branch the read path would refuse cannot survive.
     */
    public void insertChild(Connection connection, Branch child) {
        jdbc.update(connection, INSERT_CHILD, childBinder(child));
    }

    private static JdbcSupport.Binder childBinder(Branch child) {
        BranchSource source = child.source;
        return s -> {
            s.setString(1, child.id); s.setString(2, child.topicId); s.setString(3, child.parentBranchId);
            s.setString(4, source.sourceMessageId); s.setString(5, source.sourceRevisionId);
            s.setObject(6, source.anchor.startInclusive); s.setObject(7, source.anchor.endExclusive);
            s.setString(8, source.anchor.quote); s.setString(9, source.anchor.hash);
            s.setLong(10, child.createdAt); s.setLong(11, child.updatedAt());
        };
    }

    /** Points a child branch at the birth snapshot it was created with; the second half of the write. */
    public void attachContextSnapshot(String branchId, String snapshotId) {
        JdbcSupport.requireSingleRow(jdbc.update(ATTACH_SNAPSHOT, snapshotBinder(branchId, snapshotId)),
                "attaching the birth snapshot to branch " + branchId);
    }

    /** Transaction-scoped half of {@link #attachContextSnapshot(String, String)}, for the fork write. */
    public void attachContextSnapshot(Connection connection, String branchId, String snapshotId) {
        JdbcSupport.requireSingleRow(
                jdbc.update(connection, ATTACH_SNAPSHOT, snapshotBinder(branchId, snapshotId)),
                "attaching the birth snapshot to branch " + branchId);
    }

    private static JdbcSupport.Binder snapshotBinder(String branchId, String snapshotId) {
        return s -> {
            s.setString(1, snapshotId); s.setString(2, branchId);
        };
    }

    public Optional<Branch> get(String branchId) {
        return jdbc.queryOne(SELECT_ONE, s -> s.setString(1, branchId), BranchStore::readBranch);
    }

    /** Transaction-scoped read of one branch, for a caller that already holds the connection. */
    public Optional<Branch> get(Connection connection, String branchId) {
        return jdbc.queryOne(connection, SELECT_ONE, s -> s.setString(1, branchId),
                BranchStore::readBranch);
    }

    /**
     * Every branch of the topic, root included, in creation order ({@code created_at ASC}).
     *
     * <p>This is a flat list, exactly as Android's {@code BranchDao.findByTopic} returns it: the same
     * row order, no tiebreak column beyond what Android's statement uses. Assembling the list into a
     * tree is the caller's job — this store does not impose a pre-order walk the Android DAO does not.
     */
    public List<Branch> listForTopic(String topicId) {
        return jdbc.queryList(SELECT_FOR_TOPIC, s -> s.setString(1, topicId), BranchStore::readBranch);
    }

    public List<Branch> children(String parentBranchId) {
        return jdbc.queryList(SELECT_CHILDREN, s -> s.setString(1, parentBranchId),
                BranchStore::readBranch);
    }

    /** The chain root → branch, in that order; the branch itself is last. */
    public List<Branch> pathToRoot(String branchId) {
        return jdbc.queryList(SELECT_PATH, s -> s.setString(1, branchId), BranchStore::readBranch);
    }

    /** The branch and everything below it, for a delete plan or a subtree operation. */
    public List<Branch> descendants(String branchId) {
        return jdbc.queryList(SELECT_DESCENDANTS, s -> s.setString(1, branchId),
                BranchStore::readBranch);
    }

    /** Refines the derived title; {@code null} clears it. */
    public Branch renameTitle(String branchId, String title, long now) {
        Branch branch = requireBranch(branchId);
        branch.setTitle(title, now);
        JdbcSupport.requireSingleRow(jdbc.update(RENAME_TITLE, s -> {
            s.setString(1, branch.title()); s.setLong(2, branch.updatedAt()); s.setString(3, branchId);
        }), "renaming branch " + branchId);
        return branch;
    }

    public Branch setCollapsed(String branchId, boolean collapsed, long now) {
        Branch branch = requireBranch(branchId);
        branch.setCollapsed(collapsed, now);
        JdbcSupport.requireSingleRow(jdbc.update(SET_COLLAPSED, s -> {
            s.setInt(1, branch.isCollapsed() ? 1 : 0);
            s.setLong(2, branch.updatedAt()); s.setString(3, branchId);
        }), "setting the collapse state of branch " + branchId);
        return branch;
    }

    public Branch archive(String branchId, long now) {
        Branch branch = requireBranch(branchId);
        branch.archive(now);
        JdbcSupport.requireSingleRow(jdbc.update(SET_ARCHIVED, s -> {
            s.setLong(1, branch.archivedAt()); s.setLong(2, branch.updatedAt()); s.setString(3, branchId);
        }), "archiving branch " + branchId);
        return branch;
    }

    public Branch restore(String branchId, long now) {
        Branch branch = requireBranch(branchId);
        branch.unarchive(now);
        JdbcSupport.requireSingleRow(jdbc.update(SET_ARCHIVED, s -> {
            s.setNull(1, Types.INTEGER); s.setLong(2, branch.updatedAt()); s.setString(3, branchId);
        }), "restoring branch " + branchId);
        return branch;
    }

    /**
     * Advances the read position. The shared domain type answers whether the move is legal — a
     * backwards move or one past the latest answer is refused, and then nothing is written.
     */
    public DomainResult<Void> markSeenThrough(String branchId, long sequence) {
        Branch branch = requireBranch(branchId);
        DomainResult<Void> outcome = branch.markSeenThrough(sequence);
        if (outcome.isFailure()) {
            return outcome;
        }
        JdbcSupport.requireSingleRow(jdbc.update(SET_LAST_SEEN, s -> {
            s.setLong(1, branch.lastSeenStableSequence()); s.setString(2, branchId);
        }), "marking branch " + branchId + " seen through " + sequence);
        return outcome;
    }

    /** Records that this branch gained a new stable answer; unread is the resulting gap. */
    public Branch incrementLatestStable(String branchId, long now) {
        Branch branch = requireBranch(branchId);
        branch.onStableAnswer(now);
        JdbcSupport.requireSingleRow(jdbc.update(SET_LATEST_STABLE, stableSequenceBinder(branch)),
                "incrementing the stable sequence of branch " + branchId);
        return branch;
    }

    /**
     * Transaction-scoped half of {@link #incrementLatestStable(String, long)}: the stabilizer advances
     * the branch's stable sequence inside the same transaction that inserts the revision, so the unread
     * gap and the answer it counts commit together.
     */
    public Branch incrementLatestStable(Connection connection, String branchId, long now) {
        Branch branch = get(connection, branchId).orElseThrow(
                () -> new StorageException("no branch row carries id " + branchId));
        branch.onStableAnswer(now);
        JdbcSupport.requireSingleRow(jdbc.update(connection, SET_LATEST_STABLE,
                stableSequenceBinder(branch)),
                "incrementing the stable sequence of branch " + branchId);
        return branch;
    }

    private static JdbcSupport.Binder stableSequenceBinder(Branch branch) {
        return s -> {
            s.setLong(1, branch.latestStableSequence());
            s.setLong(2, branch.updatedAt()); s.setString(3, branch.id);
        };
    }

    /** @return the stored reading position; empty once its anchor message is gone (SET NULL). */
    public Optional<ReadingAnchor> readReadingPosition(String branchId) {
        List<Optional<ReadingAnchor>> found = jdbc.queryList(SELECT_POSITION,
                s -> s.setString(1, branchId), BranchStore::readAnchorRow);
        return found.isEmpty() ? Optional.empty() : found.get(0);
    }

    public void writeReadingPosition(String branchId, ReadingAnchor anchor) {
        JdbcSupport.requireSingleRow(jdbc.update(WRITE_POSITION, s -> {
            s.setString(1, anchor.messageId); s.setString(2, anchor.revisionId);
            s.setInt(3, anchor.listItemOffset); s.setInt(4, anchor.viewportOffset);
            s.setString(5, branchId);
        }), "writing the reading position of branch " + branchId);
    }

    /**
     * Deletes the subtree rooted at {@code branchId} in one transaction: the target row is the only
     * explicit DELETE, and {@code branches.parent_id ON DELETE CASCADE} takes the descendants while
     * the messages, revisions, snapshots and operations hanging off them cascade in the same stroke.
     * The deletion plan decides <em>what</em> dies before anything is written; this method only
     * commits that decision.
     *
     * @return 1 when the target row existed and was removed; the caller treats a missing target as an
     *         already-happened deletion (contract §6.3 idempotency) rather than a failure
     */
    public int deleteSubtree(String branchId) {
        return jdbc.runInTransaction(connection ->
                jdbc.update(connection, DELETE_BRANCH, s -> s.setString(1, branchId)));
    }

    private Branch requireBranch(String branchId) {
        return get(branchId).orElseThrow(
                () -> new StorageException("no branch row carries id " + branchId));
    }

    /**
     * Rehydrates one row. A non-root branch whose birth-snapshot pointer is null was abandoned between
     * the two halves of its write, and this refuses it rather than handing back a branch whose
     * provenance cannot be resolved.
     */
    private static Branch readBranch(ResultSet rows) throws SQLException {
        String parentBranchId = rows.getString(Branches.PARENT_ID);
        BranchSource source = parentBranchId == null ? null : readSource(rows);
        return Branch.restore(rows.getString(Branches.ID), rows.getString(Branches.TOPIC_ID),
                parentBranchId, source, rows.getString(Branches.TITLE),
                rows.getLong(Branches.CREATED_AT), rows.getLong(Branches.UPDATED_AT),
                rows.getLong(Branches.LATEST_STABLE_SEQUENCE),
                rows.getLong(Branches.LAST_SEEN_STABLE_SEQUENCE),
                JdbcSupport.flag(rows, Branches.COLLAPSED),
                JdbcSupport.nullableLong(rows, Branches.ARCHIVED_AT));
    }

    private static BranchSource readSource(ResultSet rows) throws SQLException {
        Integer start = JdbcSupport.nullableInt(rows, Branches.ANCHOR_START_INCLUSIVE);
        Integer end = JdbcSupport.nullableInt(rows, Branches.ANCHOR_END_EXCLUSIVE);
        String quote = rows.getString(Branches.ANCHOR_QUOTE);
        String hash = rows.getString(Branches.ANCHOR_HASH);
        boolean wholeAnswer = start == null && end == null && quote == null && hash == null;
        boolean ranged = start != null && end != null && quote != null && hash != null;
        if (!wholeAnswer && !ranged) {
            throw new IllegalStateException("this branches row holds a half-filled anchor; the store's"
                    + " CHECK admits only all-present or all-null, so the row was written around it and"
                    + " cannot be read back faithfully");
        }
        BranchAnchor anchor = BranchAnchor.wholeAnswer();
        if (ranged) {
            DomainResult<BranchAnchor> parsed = BranchAnchor.ofRange(start, end, quote, hash);
            if (parsed.isFailure()) {
                throw new IllegalStateException("the stored anchor is one the domain refuses ("
                        + parsed.error() + "); the store cannot repair it, so the row is corrupt");
            }
            anchor = parsed.value();
        }
        String contextSnapshotId = rows.getString(Branches.CONTEXT_SNAPSHOT_ID);
        if (contextSnapshotId == null) {
            throw new IllegalStateException("this non-root branch has no birth snapshot, so it was"
                    + " abandoned between the two halves of its write; createChild followed by"
                    + " attachContextSnapshot must happen in one transaction (branch "
                    + rows.getString(Branches.ID) + ")");
        }
        return BranchSource.restore(rows.getString(Branches.PARENT_ID),
                rows.getString(Branches.SOURCE_MESSAGE_ID),
                rows.getString(Branches.SOURCE_REVISION_ID), anchor, contextSnapshotId);
    }

    private static Optional<ReadingAnchor> readAnchorRow(ResultSet rows) throws SQLException {
        String messageId = rows.getString(Branches.READING_ANCHOR_MESSAGE_ID);
        if (messageId == null) {
            return Optional.empty();
        }
        Integer listItemOffset = JdbcSupport.nullableInt(rows, Branches.READING_LIST_ITEM_OFFSET);
        Integer viewportOffset = JdbcSupport.nullableInt(rows, Branches.READING_VIEWPORT_OFFSET);
        if (listItemOffset == null || viewportOffset == null) {
            throw new IllegalStateException("this branches row points at reading_anchor_message_id "
                    + messageId + " but has no offset; the two offsets are written with the message and"
                    + " never alone, so the row was written around the store");
        }
        return Optional.of(ReadingAnchor.of(messageId,
                rows.getString(Branches.READING_ANCHOR_REVISION_ID), listItemOffset, viewportOffset));
    }
}
