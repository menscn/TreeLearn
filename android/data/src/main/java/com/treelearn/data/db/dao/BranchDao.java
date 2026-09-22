package com.treelearn.data.db.dao;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.treelearn.core.domain.Branch;
import com.treelearn.core.domain.BranchAnchor;
import com.treelearn.core.domain.BranchSource;
import com.treelearn.core.domain.DomainResult;
import com.treelearn.data.db.TreeLearnSchema;

import java.util.ArrayList;
import java.util.List;

/**
 * Row access for {@code branches}.
 *
 * <p>This class holds the <em>only</em> permitted use of {@link BranchSource#restore} in
 * {@code :data}: rehydration belongs to the read path, and putting it here is what makes any other
 * use of it — a write path rebuilding provenance instead of taking it from a completed attempt —
 * visible in one grep.
 *
 * <p>Provenance is written in two steps when a branch is created, because
 * {@code branches.context_snapshot_id} and {@code context_snapshots.branch_id} reference each
 * other. The snapshot row cannot exist before the branch, and the branch cannot point at a snapshot
 * that does not exist yet, so the caller must do both writes inside one transaction (T-004
 * Watch-outs; owned by T-2026-09-21-023).
 */
public final class BranchDao {

    private BranchDao() {
    }

    /**
     * Writes a branch row without its birth snapshot.
     *
     * <p><strong>This method deliberately does not write {@code context_snapshot_id}.</strong> A
     * non-root branch therefore leaves here in a state that cannot be read back, and the caller owes
     * the second half of the write: insert the branch, insert the {@code context_snapshots} row, then
     * call {@link #setContextSnapshot} — all three inside one transaction. The two tables reference
     * each other, so no ordering of two separate steps is valid and the store cannot express this
     * with {@code NOT NULL} (T-004 Watch-outs; owned by T-2026-09-21-023).
     *
     * <p>A branch abandoned between the two steps is not silently tolerated: {@link #findById} and
     * friends refuse to rehydrate it and say so.
     */
    public static void insert(SQLiteDatabase database, Branch branch) {
        if (branch == null) {
            throw new IllegalArgumentException("branch must not be null");
        }
        database.insertOrThrow(TreeLearnSchema.Branches.TABLE, null, values(branch));
    }

    /**
     * Writes the fields a session may have changed: title, collapse, archive and the unread
     * sequence pair. Identity, topic and provenance are never rewritten here — a row's provenance
     * is frozen at creation, which is what lets an old child branch keep its own history.
     */
    public static void update(SQLiteDatabase database, Branch branch) {
        if (branch == null) {
            throw new IllegalArgumentException("branch must not be null");
        }
        ContentValues values = new ContentValues();
        values.put(TreeLearnSchema.Branches.TITLE, branch.title());
        values.put(TreeLearnSchema.Branches.UPDATED_AT, branch.updatedAt());
        values.put(TreeLearnSchema.Branches.COLLAPSED, branch.isCollapsed() ? 1 : 0);
        values.put(TreeLearnSchema.Branches.ARCHIVED_AT, branch.archivedAt());
        values.put(TreeLearnSchema.Branches.LATEST_STABLE_SEQUENCE, branch.latestStableSequence());
        values.put(TreeLearnSchema.Branches.LAST_SEEN_STABLE_SEQUENCE,
                branch.lastSeenStableSequence());
        int updated = database.update(TreeLearnSchema.Branches.TABLE, values,
                TreeLearnSchema.Branches.ID + "=?", new String[]{branch.id});
        if (updated != 1) {
            throw new IllegalStateException("updating branch " + branch.id + " affected " + updated
                    + " row(s); a branch update must touch exactly its own row");
        }
    }

    /**
     * Points a branch at the birth snapshot it was created with.
     *
     * <p>This is the second half of a two-step write, and it has to be: {@code insert} cannot write
     * the pointer because the snapshot row does not exist yet, and the snapshot row cannot be
     * created first because it references the branch. Callers must therefore do insert → snapshot
     * row → this call inside one transaction; a branch left between the two steps has no snapshot,
     * which is a state the create use case must never expose.
     */
    public static void setContextSnapshot(SQLiteDatabase database, String branchId,
                                          String contextSnapshotId) {
        if (branchId == null || branchId.isEmpty()) {
            throw new IllegalArgumentException("branchId must not be blank");
        }
        if (contextSnapshotId == null || contextSnapshotId.isEmpty()) {
            throw new IllegalArgumentException("contextSnapshotId must not be blank");
        }
        ContentValues values = new ContentValues();
        values.put(TreeLearnSchema.Branches.CONTEXT_SNAPSHOT_ID, contextSnapshotId);
        int updated = database.update(TreeLearnSchema.Branches.TABLE, values,
                TreeLearnSchema.Branches.ID + "=?", new String[]{branchId});
        if (updated != 1) {
            throw new IllegalStateException("attaching the snapshot to branch " + branchId
                    + " affected " + updated + " row(s)");
        }
    }

    /** Deletes one branch; its subtree follows through {@code ON DELETE CASCADE}. */
    public static void delete(SQLiteDatabase database, String branchId) {
        if (branchId == null || branchId.isEmpty()) {
            throw new IllegalArgumentException("branchId must not be blank");
        }
        database.delete(TreeLearnSchema.Branches.TABLE, TreeLearnSchema.Branches.ID + "=?",
                new String[]{branchId});
    }

    /** @return the branch, or null when no row carries this id */
    public static Branch findById(SQLiteDatabase database, String branchId) {
        if (branchId == null || branchId.isEmpty()) {
            throw new IllegalArgumentException("branchId must not be blank");
        }
        try (Cursor cursor = database.query(TreeLearnSchema.Branches.TABLE, null,
                TreeLearnSchema.Branches.ID + "=?", new String[]{branchId}, null, null, null)) {
            return cursor.moveToFirst() ? read(cursor) : null;
        }
    }

    /** @return the single root branch of a topic, or null if the topic has none */
    public static Branch findRootOfTopic(SQLiteDatabase database, String topicId) {
        if (topicId == null || topicId.isEmpty()) {
            throw new IllegalArgumentException("topicId must not be blank");
        }
        try (Cursor cursor = database.query(TreeLearnSchema.Branches.TABLE, null,
                TreeLearnSchema.Branches.TOPIC_ID + "=? AND "
                        + TreeLearnSchema.Branches.PARENT_ID + " IS NULL",
                new String[]{topicId}, null, null, null)) {
            return cursor.moveToFirst() ? read(cursor) : null;
        }
    }

    /** @return every branch of a topic, root included, in creation order */
    public static List<Branch> findByTopic(SQLiteDatabase database, String topicId) {
        if (topicId == null || topicId.isEmpty()) {
            throw new IllegalArgumentException("topicId must not be blank");
        }
        List<Branch> branches = new ArrayList<>();
        try (Cursor cursor = database.query(TreeLearnSchema.Branches.TABLE, null,
                TreeLearnSchema.Branches.TOPIC_ID + "=?", new String[]{topicId}, null, null,
                TreeLearnSchema.Branches.CREATED_AT + " ASC")) {
            while (cursor.moveToNext()) {
                branches.add(read(cursor));
            }
        }
        return branches;
    }

    /**
     * Rehydrates one row.
     *
     * <p><strong>Caller obligation, which this class cannot observe:</strong> a non-root branch must
     * already have completed its two-step write — {@link #insert} followed by
     * {@link #setContextSnapshot} — before it is read. A branch left between the two steps has a
     * null {@code context_snapshot_id}, and this class refuses to rehydrate it. {@link #readSource}
     * first re-asserts the anchor rules on the four anchor columns and only then reads the snapshot
     * id; a null one throws an {@link IllegalStateException} naming the write contract and the branch
     * id. It used to fall through to {@link BranchSource#restore},
     * whose {@link IllegalArgumentException} said only that the id was not a UUID — true, but it left
     * the reader to guess why it was null. Silently tolerating the half-state would be worse still: it
     * would hand callers a branch whose provenance cannot be resolved, which is exactly the shape the
     * create use case must never expose. A read that hits such a row therefore fails loudly and early,
     * and that is the intended behaviour rather than a gap to be papered over here.
     */
    private static Branch read(Cursor cursor) {
        String parentBranchId = Rows.string(cursor, TreeLearnSchema.Branches.PARENT_ID);
        BranchSource source = parentBranchId == null ? null : readSource(cursor);
        return Branch.restore(
                Rows.string(cursor, TreeLearnSchema.Branches.ID),
                Rows.string(cursor, TreeLearnSchema.Branches.TOPIC_ID),
                parentBranchId,
                source,
                Rows.string(cursor, TreeLearnSchema.Branches.TITLE),
                Rows.longOf(cursor, TreeLearnSchema.Branches.CREATED_AT),
                Rows.longOf(cursor, TreeLearnSchema.Branches.UPDATED_AT),
                Rows.longOf(cursor, TreeLearnSchema.Branches.LATEST_STABLE_SEQUENCE),
                Rows.longOf(cursor, TreeLearnSchema.Branches.LAST_SEEN_STABLE_SEQUENCE),
                Rows.bool(cursor, TreeLearnSchema.Branches.COLLAPSED),
                Rows.nullableLong(cursor, TreeLearnSchema.Branches.ARCHIVED_AT));
    }

    /**
     * The single rehydration point for provenance.
     *
     * <p>The store's CHECK admits exactly two anchor shapes — whole-answer (all four columns null)
     * or ranged (all four present) — and this method re-asserts that rather than trusting it. The
     * three ways a half-filled anchor used to misbehave were all silent: an unboxing
     * {@link NullPointerException}, a {@code DomainResult} failure surfacing as a bare
     * {@code IllegalStateException} with no domain code, and worst of all a ranged anchor read back
     * as a whole-answer fork with its quote and hash quietly dropped. None of those should return
     * to the codebase if the constraint is ever relaxed.
     */
    private static BranchSource readSource(Cursor cursor) {
        Integer start = Rows.nullableInt(cursor, TreeLearnSchema.Branches.ANCHOR_START_INCLUSIVE);
        Integer end = Rows.nullableInt(cursor, TreeLearnSchema.Branches.ANCHOR_END_EXCLUSIVE);
        String quote = Rows.string(cursor, TreeLearnSchema.Branches.ANCHOR_QUOTE);
        String hash = Rows.string(cursor, TreeLearnSchema.Branches.ANCHOR_HASH);

        boolean wholeAnswer = start == null && end == null && quote == null && hash == null;
        boolean ranged = start != null && end != null && quote != null && hash != null;
        if (!wholeAnswer && !ranged) {
            throw new IllegalStateException("this branches row holds a half-filled anchor"
                    + " (start=" + start + ", end=" + end + ", quote=" + (quote != null)
                    + ", hash=" + (hash != null) + "); §5.2 anchors are all-or-nothing, so the row"
                    + " was written around the store's CHECK and cannot be read back faithfully");
        }

        BranchAnchor anchor;
        if (wholeAnswer) {
            anchor = BranchAnchor.wholeAnswer();
        } else {
            DomainResult<BranchAnchor> parsed = BranchAnchor.ofRange(start, end, quote, hash);
            if (parsed.isFailure()) {
                // A real domain code, not a bare "failure has no value" from DomainResult.value().
                throw new IllegalStateException("the stored anchor is one the domain refuses ("
                        + parsed.error() + "); the store cannot repair it, so the row is corrupt");
            }
            anchor = parsed.value();
        }
        String contextSnapshotId = Rows.string(cursor, TreeLearnSchema.Branches.CONTEXT_SNAPSHOT_ID);
        // Checked here rather than left to BranchSource.restore, which rejects a null id with a
        // generic "contextSnapshotId must be a non-empty UUID string". That message is true but
        // useless: it does not tell whoever is debugging why the id is null, which is always the same
        // reason — the branch was abandoned between the two halves of a write that has to be atomic.
        if (contextSnapshotId == null) {
            throw new IllegalStateException("this non-root branch has no birth snapshot, so it was"
                    + " abandoned between the two halves of its write; BranchDao.insert → insert the"
                    + " context_snapshots row → BranchDao.setContextSnapshot must all happen in one"
                    + " transaction (branch " + Rows.string(cursor, TreeLearnSchema.Branches.ID)
                    + ")");
        }
        return BranchSource.restore(
                Rows.string(cursor, TreeLearnSchema.Branches.PARENT_ID),
                Rows.string(cursor, TreeLearnSchema.Branches.SOURCE_MESSAGE_ID),
                Rows.string(cursor, TreeLearnSchema.Branches.SOURCE_REVISION_ID),
                anchor,
                contextSnapshotId);
    }

    private static ContentValues values(Branch branch) {
        ContentValues values = new ContentValues();
        values.put(TreeLearnSchema.Branches.ID, branch.id);
        values.put(TreeLearnSchema.Branches.TOPIC_ID, branch.topicId);
        values.put(TreeLearnSchema.Branches.PARENT_ID, branch.parentBranchId);
        values.put(TreeLearnSchema.Branches.TITLE, branch.title());
        values.put(TreeLearnSchema.Branches.CREATED_AT, branch.createdAt);
        values.put(TreeLearnSchema.Branches.UPDATED_AT, branch.updatedAt());
        values.put(TreeLearnSchema.Branches.COLLAPSED, branch.isCollapsed() ? 1 : 0);
        values.put(TreeLearnSchema.Branches.ARCHIVED_AT, branch.archivedAt());
        values.put(TreeLearnSchema.Branches.LATEST_STABLE_SEQUENCE, branch.latestStableSequence());
        values.put(TreeLearnSchema.Branches.LAST_SEEN_STABLE_SEQUENCE,
                branch.lastSeenStableSequence());
        BranchSource source = branch.source;
        if (source != null) {
            values.put(TreeLearnSchema.Branches.SOURCE_MESSAGE_ID, source.sourceMessageId);
            values.put(TreeLearnSchema.Branches.SOURCE_REVISION_ID, source.sourceRevisionId);
            values.put(TreeLearnSchema.Branches.ANCHOR_START_INCLUSIVE,
                    source.anchor.startInclusive);
            values.put(TreeLearnSchema.Branches.ANCHOR_END_EXCLUSIVE, source.anchor.endExclusive);
            values.put(TreeLearnSchema.Branches.ANCHOR_QUOTE, source.anchor.quote);
            values.put(TreeLearnSchema.Branches.ANCHOR_HASH, source.anchor.hash);
            // context_snapshot_id is deliberately absent: the snapshot row references this branch,
            // so writing the pointer now would fail the foreign key. See setContextSnapshot.
        }
        return values;
    }
}
