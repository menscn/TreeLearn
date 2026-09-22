package com.treelearn.data.db.dao;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.treelearn.core.domain.Guard;
import com.treelearn.data.db.TreeLearnSchema;

/**
 * Row access for {@code context_snapshots}: the immutable birth snapshot of a non-root branch.
 *
 * <p>Authority: 技术方案 §5.3 / §6 and 建表清单 §2/§3. One branch has at most one snapshot
 * ({@code uq_context_snapshots_branch}); the row is immutable and disappears only together with its
 * branch rather than on its own — both enforced by the T-004 triggers, not by this class.
 *
 * <p><strong>{@code payload} is an opaque string here.</strong> Its JSON schema belongs to the T-007
 * codec. This layer writes the exact string it was handed and reads it back unchanged, so a payload
 * can never be silently repaired, truncated or re-serialised on its way through the store. Nothing
 * in this file parses, searches or builds JSON.
 *
 * <p><strong>Why the write is two steps, and who owns the transaction.</strong> {@code
 * branches.context_snapshot_id} references this table while {@code context_snapshots.branch_id}
 * references {@code branches}, so neither row can be written first with a complete foreign key. The
 * caller writes the branch, then the snapshot row, then {@code BranchDao.setContextSnapshot}, all
 * inside one {@code DatabaseTransactionRunner} block (T-009/T-025). No method here opens a
 * transaction.
 */
public final class ContextSnapshotDao {

    private ContextSnapshotDao() {
    }

    /**
     * One snapshot row, exactly as stored.
     *
     * <p>Deliberately not a domain entity: the user ruled on 2026-09-21 that {@code ContextSnapshot}
     * belongs to T-007, so this layer moves rows and opaque payloads only. The constructor still
     * checks shape, following the {@code :core} error-channel rule — a blank id, a non-positive
     * schema version or an empty payload is a caller bug, therefore an
     * {@link IllegalArgumentException} rather than a business code.
     */
    public static final class Row {

        public final String id;
        public final String branchId;
        public final int schemaVersion;
        public final String digest;
        public final String payload;
        public final long createdAt;

        public Row(String id, String branchId, int schemaVersion, String digest, String payload,
                   long createdAt) {
            this.id = Guard.id(id, "context snapshot id");
            this.branchId = Guard.id(branchId, "context snapshot branchId");
            this.schemaVersion = Guard.atLeast(schemaVersion, 1, "context snapshot schemaVersion");
            this.digest = Guard.nonBlank(digest, "context snapshot digest");
            this.payload = Guard.nonBlank(payload, "context snapshot payload");
            this.createdAt = Guard.epochMillis(createdAt, "context snapshot createdAt");
        }
    }

    /**
     * Writes the snapshot of {@code row.branchId}.
     *
     * <p>Refusals are the store's: a second snapshot for the same branch fails the unique index, and
     * a {@code NULL} in any mandatory column fails NOT NULL. Both surface as
     * {@link android.database.sqlite.SQLiteConstraintException} rather than as a domain code, because
     * {@code :core} has no business code meaning "this branch already has its birth snapshot" —
     * inventing one here would be a code with no domain rule behind it. Naming the cause is the
     * caller's job, and the instrumentation suite asserts it by SQLite extended result code.
     */
    public static void insert(SQLiteDatabase database, Row row) {
        Guard.object(row, "context snapshot row");
        database.insertOrThrow(TreeLearnSchema.ContextSnapshots.TABLE, null, values(row));
    }

    /** @return the snapshot of this branch, or null when the branch has none */
    public static Row findByBranchId(SQLiteDatabase database, String branchId) {
        Guard.id(branchId, "branchId");
        try (Cursor cursor = database.query(TreeLearnSchema.ContextSnapshots.TABLE, null,
                TreeLearnSchema.ContextSnapshots.BRANCH_ID + "=?", new String[]{branchId},
                null, null, null)) {
            if (!cursor.moveToFirst()) {
                return null;
            }
            Row row = read(cursor);
            // uq_context_snapshots_branch makes a second row impossible; if one is there anyway the
            // index was dropped or bypassed, and returning "the first" would hide that silently.
            if (cursor.moveToNext()) {
                throw new IllegalStateException("branch " + branchId + " has more than one birth"
                        + " snapshot, but uq_context_snapshots_branch requires exactly one; the store"
                        + " was written around the index");
            }
            return row;
        }
    }

    private static Row read(Cursor cursor) {
        return new Row(
                Rows.string(cursor, TreeLearnSchema.ContextSnapshots.ID),
                Rows.string(cursor, TreeLearnSchema.ContextSnapshots.BRANCH_ID),
                Rows.intOf(cursor, TreeLearnSchema.ContextSnapshots.SCHEMA_VERSION),
                Rows.string(cursor, TreeLearnSchema.ContextSnapshots.DIGEST),
                Rows.string(cursor, TreeLearnSchema.ContextSnapshots.PAYLOAD),
                Rows.longOf(cursor, TreeLearnSchema.ContextSnapshots.CREATED_AT));
    }

    private static ContentValues values(Row row) {
        ContentValues values = new ContentValues();
        values.put(TreeLearnSchema.ContextSnapshots.ID, row.id);
        values.put(TreeLearnSchema.ContextSnapshots.BRANCH_ID, row.branchId);
        values.put(TreeLearnSchema.ContextSnapshots.SCHEMA_VERSION, row.schemaVersion);
        values.put(TreeLearnSchema.ContextSnapshots.DIGEST, row.digest);
        values.put(TreeLearnSchema.ContextSnapshots.PAYLOAD, row.payload);
        values.put(TreeLearnSchema.ContextSnapshots.CREATED_AT, row.createdAt);
        return values;
    }
}