package com.treelearn.data.db.dao;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.treelearn.data.db.TreeLearnSchema;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.function.Supplier;

/**
 * Row access for {@code models} (SPEC-2026-09-23 数据结构, T-2026-09-23-083).
 *
 * <p>{@code model_id} is the provider-side wire id. Its uniqueness within a provider is the
 * schema's {@code uq_models_provider_model} index under BINARY collation — case-exact, the F8
 * 口径 — so this class adds no second dedup of its own: a duplicate insert is refused by the
 * engine, not quietly absorbed here. A model row never outlives its provider (ON DELETE CASCADE).
 */
public final class ModelDao {

    private ModelDao() {
    }

    /** One {@code models} row. */
    public static final class Row {
        public final String id, providerId, modelId, displayName;
        public final long createdAt;

        public Row(String id, String providerId, String modelId, String displayName,
                   long createdAt) {
            this.id = id;
            this.providerId = providerId;
            this.modelId = modelId;
            this.displayName = displayName;
            this.createdAt = createdAt;
        }
    }

    public static void insert(SQLiteDatabase database, Row row) {
        if (row == null || isBlank(row.id) || isBlank(row.providerId) || isBlank(row.modelId)) {
            throw new IllegalArgumentException("a model row needs non-blank id, providerId, modelId");
        }
        database.insertOrThrow(TreeLearnSchema.Models.TABLE, null, values(row));
    }

    /** @return the provider's models in creation order; empty, never null, for an unknown id */
    public static List<Row> findByProvider(SQLiteDatabase database, String providerId) {
        List<Row> rows = new ArrayList<>();
        try (Cursor cursor = database.query(TreeLearnSchema.Models.TABLE, null,
                TreeLearnSchema.Models.PROVIDER_ID + "=?", new String[]{providerId}, null, null,
                TreeLearnSchema.Models.CREATED_AT + " ASC, " + TreeLearnSchema.Models.ID + " ASC")) {
            while (cursor.moveToNext()) {
                rows.add(read(cursor));
            }
        }
        return rows;
    }

    public static void delete(SQLiteDatabase database, String id) {
        int deleted = database.delete(TreeLearnSchema.Models.TABLE,
                TreeLearnSchema.Models.ID + "=?", new String[]{id});
        if (deleted != 1) {
            throw new IllegalStateException("deleting model " + id + " affected " + deleted
                    + " row(s); a delete must touch exactly its own row");
        }
    }

    /**
     * The T-086 merge decision (the F8 口径): the remote ids that are not already stored,
     * compared case-exactly, in the remote's order, with duplicates inside the remote list itself
     * silently skipped. The plan never reduces the store — a fetch that comes back shorter keeps
     * every local entry — so applying it can only ever add rows.
     */
    public static List<String> newModelIds(Collection<String> storedModelIds,
            List<String> remoteModelIds) {
        HashSet<String> unseen = new HashSet<>(storedModelIds);
        List<String> plan = new ArrayList<>();
        for (String remoteId : remoteModelIds) {
            if (unseen.add(remoteId)) {
                plan.add(remoteId);
            }
        }
        return plan;
    }

    /**
     * Applies one fetched model list: only the ids {@link #newModelIds} plans are inserted, each
     * row naming the wire id as its display name. Returns the number of rows added. The caller owns
     * the transaction, so a failed insert rolls every row of this fetch back together and the local
     * list stays exactly as it was.
     */
    public static int mergeRemote(SQLiteDatabase database, String providerId,
            List<String> remoteModelIds, Supplier<String> idSupplier) {
        List<String> stored = new ArrayList<>();
        for (Row row : findByProvider(database, providerId)) {
            stored.add(row.modelId);
        }
        long now = System.currentTimeMillis();
        int added = 0;
        for (String modelId : newModelIds(stored, remoteModelIds)) {
            insert(database, new Row(idSupplier.get(), providerId, modelId, modelId, now));
            added++;
        }
        return added;
    }

    private static Row read(Cursor cursor) {
        return new Row(Rows.string(cursor, TreeLearnSchema.Models.ID),
                Rows.string(cursor, TreeLearnSchema.Models.PROVIDER_ID),
                Rows.string(cursor, TreeLearnSchema.Models.MODEL_ID),
                Rows.string(cursor, TreeLearnSchema.Models.DISPLAY_NAME),
                Rows.longOf(cursor, TreeLearnSchema.Models.CREATED_AT));
    }

    private static ContentValues values(Row row) {
        ContentValues values = new ContentValues();
        values.put(TreeLearnSchema.Models.ID, row.id);
        values.put(TreeLearnSchema.Models.PROVIDER_ID, row.providerId);
        values.put(TreeLearnSchema.Models.MODEL_ID, row.modelId);
        values.put(TreeLearnSchema.Models.DISPLAY_NAME, row.displayName);
        values.put(TreeLearnSchema.Models.CREATED_AT, row.createdAt);
        return values;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
