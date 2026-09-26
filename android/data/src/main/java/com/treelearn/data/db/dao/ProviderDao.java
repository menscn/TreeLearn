package com.treelearn.data.db.dao;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.treelearn.data.db.TreeLearnSchema;

import java.util.ArrayList;
import java.util.List;

/**
 * Row access for {@code providers} (SPEC-2026-09-23 数据结构, T-2026-09-23-083).
 *
 * <p>The table holds configuration only — no column may carry an API key or any other secret
 * (keys live in the Keystore, T-084). Name uniqueness is enforced by the store's UNIQUE index on
 * exact text; the [DUP] trim-then-compare rule belongs to the form layer that calls this class.
 * Deleting a row takes its model rows with it, via the schema's ON DELETE CASCADE.
 */
public final class ProviderDao {

    private ProviderDao() {
    }

    /** One {@code providers} row. */
    public static final class Row {
        public final String id, name, baseUrl;
        public final boolean enabled, builtin;
        public final long createdAt, updatedAt;

        public Row(String id, String name, String baseUrl, boolean enabled, boolean builtin,
                   long createdAt, long updatedAt) {
            this.id = id;
            this.name = name;
            this.baseUrl = baseUrl;
            this.enabled = enabled;
            this.builtin = builtin;
            this.createdAt = createdAt;
            this.updatedAt = updatedAt;
        }
    }

    public static void insert(SQLiteDatabase database, Row row) {
        requireRow(row);
        database.insertOrThrow(TreeLearnSchema.Providers.TABLE, null, values(row));
    }

    /** @return the provider, or null when no row carries this id */
    public static Row findById(SQLiteDatabase database, String id) {
        return firstWhere(database, TreeLearnSchema.Providers.ID + "=?", id);
    }

    /** @return the provider whose name is exactly this text, or null */
    public static Row findByName(SQLiteDatabase database, String name) {
        return firstWhere(database, TreeLearnSchema.Providers.NAME + "=?", name);
    }

    /** Every provider in creation order, id as the tie-breaker for equal timestamps. */
    public static List<Row> listByCreatedAt(SQLiteDatabase database) {
        List<Row> rows = new ArrayList<>();
        try (Cursor cursor = database.query(TreeLearnSchema.Providers.TABLE, null, null, null,
                null, null, TreeLearnSchema.Providers.CREATED_AT + " ASC, "
                        + TreeLearnSchema.Providers.ID + " ASC")) {
            while (cursor.moveToNext()) {
                rows.add(read(cursor));
            }
        }
        return rows;
    }

    /** The enable/disable switch writes only the enabled bit and the row's updated_at. */
    public static void setEnabled(SQLiteDatabase database, String id, boolean enabled) {
        ContentValues values = new ContentValues();
        values.put(TreeLearnSchema.Providers.ENABLED, enabled ? 1 : 0);
        values.put(TreeLearnSchema.Providers.UPDATED_AT, System.currentTimeMillis());
        int updated = database.update(TreeLearnSchema.Providers.TABLE, values,
                TreeLearnSchema.Providers.ID + "=?", new String[]{id});
        if (updated != 1) {
            throw new IllegalStateException("setting enabled on provider " + id + " affected "
                    + updated + " row(s); an enable/disable must touch exactly its own row");
        }
    }

    /**
     * The edit form's single write: the name and the base URL land together with the row's
     * updated_at; the enabled bit and the key (which is not a column) are untouched.
     */
    public static void updateConfig(SQLiteDatabase database, String id, String name,
            String baseUrl) {
        ContentValues values = new ContentValues();
        values.put(TreeLearnSchema.Providers.NAME, name);
        values.put(TreeLearnSchema.Providers.BASE_URL, baseUrl);
        values.put(TreeLearnSchema.Providers.UPDATED_AT, System.currentTimeMillis());
        int updated = database.update(TreeLearnSchema.Providers.TABLE, values,
                TreeLearnSchema.Providers.ID + "=?", new String[]{id});
        if (updated != 1) {
            throw new IllegalStateException("updating provider " + id + " affected " + updated
                    + " row(s); a config edit must touch exactly its own row");
        }
    }

    public static void delete(SQLiteDatabase database, String id) {
        int deleted = database.delete(TreeLearnSchema.Providers.TABLE,
                TreeLearnSchema.Providers.ID + "=?", new String[]{id});
        if (deleted != 1) {
            throw new IllegalStateException("deleting provider " + id + " affected " + deleted
                    + " row(s); a delete must touch exactly its own row");
        }
    }

    private static Row firstWhere(SQLiteDatabase database, String selection, String argument) {
        try (Cursor cursor = database.query(TreeLearnSchema.Providers.TABLE, null, selection,
                new String[]{argument}, null, null, null)) {
            return cursor.moveToFirst() ? read(cursor) : null;
        }
    }

    private static Row read(Cursor cursor) {
        return new Row(Rows.string(cursor, TreeLearnSchema.Providers.ID),
                Rows.string(cursor, TreeLearnSchema.Providers.NAME),
                Rows.string(cursor, TreeLearnSchema.Providers.BASE_URL),
                Rows.bool(cursor, TreeLearnSchema.Providers.ENABLED),
                Rows.bool(cursor, TreeLearnSchema.Providers.IS_BUILTIN),
                Rows.longOf(cursor, TreeLearnSchema.Providers.CREATED_AT),
                Rows.longOf(cursor, TreeLearnSchema.Providers.UPDATED_AT));
    }

    private static ContentValues values(Row row) {
        ContentValues values = new ContentValues();
        values.put(TreeLearnSchema.Providers.ID, row.id);
        values.put(TreeLearnSchema.Providers.NAME, row.name);
        values.put(TreeLearnSchema.Providers.BASE_URL, row.baseUrl);
        values.put(TreeLearnSchema.Providers.ENABLED, row.enabled ? 1 : 0);
        values.put(TreeLearnSchema.Providers.IS_BUILTIN, row.builtin ? 1 : 0);
        values.put(TreeLearnSchema.Providers.CREATED_AT, row.createdAt);
        values.put(TreeLearnSchema.Providers.UPDATED_AT, row.updatedAt);
        return values;
    }

    private static void requireRow(Row row) {
        if (row == null || isBlank(row.id) || isBlank(row.name) || isBlank(row.baseUrl)) {
            throw new IllegalArgumentException("a provider row needs non-blank id, name, baseUrl");
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
