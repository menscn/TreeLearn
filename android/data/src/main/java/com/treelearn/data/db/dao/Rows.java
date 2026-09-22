package com.treelearn.data.db.dao;

import android.database.Cursor;

/**
 * Small typed readers over a {@link Cursor}, shared by the DAOs in this package.
 *
 * <p>They exist so that no DAO has to remember whether a null column is a stored {@code NULL} or a
 * missing column, and so that the "nullable integer" case — used by the archive stamp and the fork
 * offsets — is handled once instead of three times with three different conventions.
 *
 * <p>Holds no DDL and no column literals of its own: callers pass column names, and the DAOs obtain
 * those from {@code TreeLearnSchema}.
 */
final class Rows {

    private Rows() {
    }

    static String string(Cursor cursor, String column) {
        return cursor.isNull(cursor.getColumnIndexOrThrow(column)) ? null
                : cursor.getString(cursor.getColumnIndexOrThrow(column));
    }

    static long longOf(Cursor cursor, String column) {
        return cursor.getLong(cursor.getColumnIndexOrThrow(column));
    }

    static int intOf(Cursor cursor, String column) {
        return cursor.getInt(cursor.getColumnIndexOrThrow(column));
    }

    /**
     * Reads a nullable {@code INTEGER} column as a boxed value.
     *
     * @return null when the column holds SQL {@code NULL}; never a fabricated zero
     */
    static Long nullableLong(Cursor cursor, String column) {
        return cursor.isNull(cursor.getColumnIndexOrThrow(column)) ? null
                : Long.valueOf(cursor.getLong(cursor.getColumnIndexOrThrow(column)));
    }

    /**
     * Reads a nullable {@code INTEGER} column as a boxed value, for columns whose nullity carries
     * meaning rather than absence (the fork offsets of §5.2).
     */
    static Integer nullableInt(Cursor cursor, String column) {
        return cursor.isNull(cursor.getColumnIndexOrThrow(column)) ? null
                : Integer.valueOf(cursor.getInt(cursor.getColumnIndexOrThrow(column)));
    }

    static boolean bool(Cursor cursor, String column) {
        return cursor.getInt(cursor.getColumnIndexOrThrow(column)) != 0;
    }
}
