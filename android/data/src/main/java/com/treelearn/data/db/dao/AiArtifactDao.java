package com.treelearn.data.db.dao;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.treelearn.core.domain.Guard;
import com.treelearn.data.db.TreeLearnSchema;

/**
 * Row access for {@code ai_artifacts}: AI-derived products (title, summary, recommendations, context
 * compression) that may be recomputed and therefore may go stale.
 *
 * <p>Authority: 技术方案 §6/§10 and 建表清单 §2/§3. Unlike a stable revision, an artifact is
 * <em>not</em> immutable history — it is a cache of a derivation, and the whole point of the table is
 * to decide when recomputing it is allowed to replace it.
 *
 * <p><strong>The staleness rule is enforced by the write itself.</strong> A write-back is a single
 * conditional {@code UPDATE} whose {@code WHERE} clause carries the baseline
 * ({@code based_on_revision_id} and {@code input_digest}). One affected row means the baseline still
 * matched and the payload was replaced; zero means the result is stale and was dropped, leaving the
 * stored payload untouched. There is deliberately no "SELECT, compare, then UPDATE": that shape moves
 * the baseline check outside the statement that acts on it, which is exactly the bug the CAS exists
 * to prevent.
 *
 * <p><strong>{@code scope_id} is polymorphic and has no foreign key.</strong> Which table it points
 * at depends on {@code kind}, so deleting a scope does <em>not</em> delete its artifacts and
 * {@link #deleteByScope} is the caller's cleanup entry point rather than a cascade.
 *
 * <p>{@code payload} is opaque here; its schema belongs to the T-011 codec.
 */
public final class AiArtifactDao {

    /** The outcome of a conditional write-back. Stale is a normal result, not a failure. */
    public enum Writeback {

        /** The baseline matched and the stored payload was replaced. */
        APPLIED,

        /** The baseline no longer matched; the stored payload is untouched. */
        DISCARDED_STALE
    }

    /** One artifact row, exactly as stored. */
    public static final class Row {

        public final String id;
        public final String kind;
        public final String scopeId;
        public final String promptPurpose;
        public final String promptPolicyVersion;
        public final String payload;
        /** Nullable: {@code ON DELETE SET NULL} clears it when the baseline revision is deleted. */
        public final String basedOnRevisionId;
        public final String inputDigest;
        public final long createdAt;
        public final long updatedAt;

        public Row(String id, String kind, String scopeId, String promptPurpose,
                   String promptPolicyVersion, String payload, String basedOnRevisionId,
                   String inputDigest, long createdAt, long updatedAt) {
            this.id = Guard.id(id, "artifact id");
            this.kind = Guard.nonBlank(kind, "artifact kind");
            // Deliberately not Guard.id: scope_id is polymorphic, and a single identifier format is
            // an assumption this row shape does not make. Which table it names follows from kind.
            this.scopeId = Guard.nonBlank(scopeId, "artifact scopeId");
            this.promptPurpose = Guard.nonBlank(promptPurpose, "artifact promptPurpose");
            this.promptPolicyVersion = Guard.nonBlank(promptPolicyVersion, "artifact policyVersion");
            this.payload = Guard.nonBlank(payload, "artifact payload");
            this.basedOnRevisionId = Guard.idOrNull(basedOnRevisionId, "artifact basedOnRevisionId");
            this.inputDigest = Guard.nonBlank(inputDigest, "artifact inputDigest");
            this.createdAt = Guard.epochMillis(createdAt, "artifact createdAt");
            this.updatedAt = Guard.epochMillis(updatedAt, "artifact updatedAt");
        }
    }

    /**
     * Inserts the first artifact for a {@code (kind, scope_id)} pair.
     *
     * <p>Conditional on purpose: the INSERT carries its own {@code WHERE NOT EXISTS}, so a second row
     * for the same pair is impossible. Without that, {@link #applyIfBaselineMatches} could match more
     * than one row and there would be no single stored payload to compare a baseline against — the
     * "one current artifact per (kind, scope)" rule the CAS depends on would be a comment rather than
     * a fact. This is one statement, so it is atomic and needs no transaction wrapper.
     *
     * @throws IllegalStateException when a row for this pair already exists; the caller must use
     *         {@link #applyIfBaselineMatches} to replace it instead of inserting a second one
     */
    public static void insert(SQLiteDatabase database, Row row) {
        Guard.object(row, "artifact row");
        database.execSQL(INSERT_IF_ABSENT, new Object[]{
                row.id, row.kind, row.scopeId, row.promptPurpose, row.promptPolicyVersion,
                row.payload, row.basedOnRevisionId, row.inputDigest, row.createdAt, row.updatedAt,
                row.kind, row.scopeId});
        if (changedRows(database) != 1) {
            throw new IllegalStateException("an artifact for kind=" + row.kind + " scope="
                    + row.scopeId + " already exists; the first write of a baseline is insert and"
                    + " every later one is applyIfBaselineMatches, so a second row must not appear");
        }
    }

    /** @return the current artifact for this pair, or null when none was ever derived */
    public static Row findByKindAndScope(SQLiteDatabase database, String kind, String scopeId) {
        Guard.nonBlank(kind, "artifact kind");
        Guard.nonBlank(scopeId, "artifact scopeId");
        try (Cursor cursor = database.query(TreeLearnSchema.AiArtifacts.TABLE, null,
                TreeLearnSchema.AiArtifacts.KIND + "=? AND " + TreeLearnSchema.AiArtifacts.SCOPE_ID
                        + "=?", new String[]{kind, scopeId}, null, null, null)) {
            if (!cursor.moveToFirst()) {
                return null;
            }
            Row row = read(cursor);
            if (cursor.moveToNext()) {
                throw new IllegalStateException("kind=" + kind + " scope=" + scopeId + " has more"
                        + " than one artifact row; insert() is conditional so exactly one row is the"
                        + " only shape this store produces");
            }
            return row;
        }
    }

    /**
     * Replaces the payload only if the stored baseline still matches the candidate's.
     *
     * <p>A row whose {@code based_on_revision_id} was cleared by {@code ON DELETE SET NULL} can never
     * match, so it is reported stale and keeps its payload. That is the deliberate trade-off: a
     * derivation whose source revision is gone is not "the same baseline with one field missing", and
     * silently re-adopting it would let an outdated summary outlive the answer it described. Callers
     * that need to re-derive it must delete and insert again, which is an explicit decision.
     *
     * @return {@link Writeback#APPLIED} when the stored payload was replaced, otherwise
     *         {@link Writeback#DISCARDED_STALE}
     */
    public static Writeback applyIfBaselineMatches(SQLiteDatabase database, Row candidate) {
        Guard.object(candidate, "artifact candidate");
        ContentValues values = new ContentValues();
        values.put(TreeLearnSchema.AiArtifacts.PAYLOAD, candidate.payload);
        values.put(TreeLearnSchema.AiArtifacts.PROMPT_PURPOSE, candidate.promptPurpose);
        values.put(TreeLearnSchema.AiArtifacts.PROMPT_POLICY_VERSION, candidate.promptPolicyVersion);
        values.put(TreeLearnSchema.AiArtifacts.BASED_ON_REVISION_ID, candidate.basedOnRevisionId);
        values.put(TreeLearnSchema.AiArtifacts.INPUT_DIGEST, candidate.inputDigest);
        values.put(TreeLearnSchema.AiArtifacts.UPDATED_AT, candidate.updatedAt);

        int updated = database.update(TreeLearnSchema.AiArtifacts.TABLE, values, BASELINE_WHERE,
                new String[]{candidate.kind, candidate.scopeId, candidate.basedOnRevisionId,
                        candidate.inputDigest});
        if (updated == 1) {
            return Writeback.APPLIED;
        }
        if (updated == 0) {
            return Writeback.DISCARDED_STALE;
        }
        throw new IllegalStateException("a baseline write-back for kind=" + candidate.kind + " scope="
                + candidate.scopeId + " matched " + updated + " rows; insert() is conditional, so"
                + " more than one current artifact for a pair means the store holds duplicates");
    }

    /**
     * Removes every artifact of this kind bound to this scope.
     *
     * <p>The cleanup counterpart of the missing foreign key: the caller that deletes a scope owes
     * this call, and the instrumentation suite proves it does not touch another kind or scope.
     *
     * @return the number of rows removed
     */
    public static int deleteByScope(SQLiteDatabase database, String kind, String scopeId) {
        Guard.nonBlank(kind, "artifact kind");
        Guard.nonBlank(scopeId, "artifact scopeId");
        return database.delete(TreeLearnSchema.AiArtifacts.TABLE,
                TreeLearnSchema.AiArtifacts.KIND + "=? AND " + TreeLearnSchema.AiArtifacts.SCOPE_ID
                        + "=?", new String[]{kind, scopeId});
    }

    private static final String TABLE = TreeLearnSchema.AiArtifacts.TABLE;

    private static final String INSERT_IF_ABSENT =
            "INSERT INTO " + TABLE + "(" + TreeLearnSchema.AiArtifacts.ID + ","
                    + TreeLearnSchema.AiArtifacts.KIND + "," + TreeLearnSchema.AiArtifacts.SCOPE_ID
                    + "," + TreeLearnSchema.AiArtifacts.PROMPT_PURPOSE + ","
                    + TreeLearnSchema.AiArtifacts.PROMPT_POLICY_VERSION + ","
                    + TreeLearnSchema.AiArtifacts.PAYLOAD + ","
                    + TreeLearnSchema.AiArtifacts.BASED_ON_REVISION_ID + ","
                    + TreeLearnSchema.AiArtifacts.INPUT_DIGEST + ","
                    + TreeLearnSchema.AiArtifacts.CREATED_AT + ","
                    + TreeLearnSchema.AiArtifacts.UPDATED_AT + ")"
                    + " SELECT ?,?,?,?,?,?,?,?,?,? WHERE NOT EXISTS (SELECT 1 FROM " + TABLE
                    + " WHERE " + TreeLearnSchema.AiArtifacts.KIND + "=?"
                    + " AND " + TreeLearnSchema.AiArtifacts.SCOPE_ID + "=?)";

    private static final String BASELINE_WHERE =
            TreeLearnSchema.AiArtifacts.KIND + "=?"
                    + " AND " + TreeLearnSchema.AiArtifacts.SCOPE_ID + "=?"
                    + " AND " + TreeLearnSchema.AiArtifacts.BASED_ON_REVISION_ID + " IS NOT NULL"
                    + " AND " + TreeLearnSchema.AiArtifacts.BASED_ON_REVISION_ID + "=?"
                    + " AND " + TreeLearnSchema.AiArtifacts.INPUT_DIGEST + "=?";

    private static int changedRows(SQLiteDatabase database) {
        try (Cursor cursor = database.rawQuery("SELECT changes()", null)) {
            if (!cursor.moveToFirst()) {
                throw new IllegalStateException("SELECT changes() returned no row");
            }
            return cursor.getInt(0);
        }
    }

    private static Row read(Cursor cursor) {
        return new Row(
                Rows.string(cursor, TreeLearnSchema.AiArtifacts.ID),
                Rows.string(cursor, TreeLearnSchema.AiArtifacts.KIND),
                Rows.string(cursor, TreeLearnSchema.AiArtifacts.SCOPE_ID),
                Rows.string(cursor, TreeLearnSchema.AiArtifacts.PROMPT_PURPOSE),
                Rows.string(cursor, TreeLearnSchema.AiArtifacts.PROMPT_POLICY_VERSION),
                Rows.string(cursor, TreeLearnSchema.AiArtifacts.PAYLOAD),
                Rows.string(cursor, TreeLearnSchema.AiArtifacts.BASED_ON_REVISION_ID),
                Rows.string(cursor, TreeLearnSchema.AiArtifacts.INPUT_DIGEST),
                Rows.longOf(cursor, TreeLearnSchema.AiArtifacts.CREATED_AT),
                Rows.longOf(cursor, TreeLearnSchema.AiArtifacts.UPDATED_AT));
    }
}