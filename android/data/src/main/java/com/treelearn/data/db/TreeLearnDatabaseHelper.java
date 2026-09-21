package com.treelearn.data.db;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

/**
 * The only place in the application that creates or upgrades the local schema.
 *
 * <p>Authority: 《Android SQLite 建表入场确认清单 V1.0》§3 and `.trae/rules/sql.md`
 * ("Android SQLite / SQLiteOpenHelper"). No repository, view or background task may run DDL.
 *
 * <p>Foreign keys are switched on in {@link #onConfigure} — the earliest hook that runs before
 * {@code onCreate}/{@code onUpgrade} — and re-verified in {@link #onOpen} so a connection whose
 * pragma silently failed to apply fails loudly instead of accepting orphan rows.
 */
public final class TreeLearnDatabaseHelper extends SQLiteOpenHelper {

    /** Fixed for the lifetime of V1; renaming it would orphan every installed database. */
    public static final String DATABASE_NAME = "treelearn.db";

    public TreeLearnDatabaseHelper(Context context) {
        super(context.getApplicationContext(), DATABASE_NAME, null,
                TreeLearnSchema.DATABASE_VERSION);
    }

    @Override
    public void onConfigure(SQLiteDatabase database) {
        super.onConfigure(database);
        // SQLite defaults to foreign_keys=OFF on every connection; the schema relies on
        // ON DELETE CASCADE and on FK violations being rejected.
        database.setForeignKeyConstraintsEnabled(true);
    }

    @Override
    public void onCreate(SQLiteDatabase database) {
        for (String statement : TreeLearnSchema.CREATE_STATEMENTS) {
            database.execSQL(statement);
        }
    }

    @Override
    public void onUpgrade(SQLiteDatabase database, int oldVersion, int newVersion) {
        // Forward-only. V1 has no predecessor, so this is a no-op until a migration is registered;
        // published steps are never rewritten (rules/sql.md).
        TreeLearnSchema.applyForwardMigrations(database, oldVersion, newVersion);
    }

    @Override
    public void onDowngrade(SQLiteDatabase database, int oldVersion, int newVersion) {
        // Refuse rather than silently run an older app against a newer schema: a half-understood
        // schema is worse than a clear failure.
        throw new IllegalStateException("TreeLearn does not downgrade the local schema ("
                + oldVersion + " -> " + newVersion + "); the user's data is newer than this app.");
    }

    @Override
    public void onOpen(SQLiteDatabase database) {
        super.onOpen(database);
        // Read the pragma back from this very connection. The platform exposes no getter for the
        // setting, and trusting the setter would mean trusting that it actually applied.
        if (!isForeignKeyEnforcementOn(database)) {
            throw new IllegalStateException(
                    "PRAGMA foreign_keys is not enabled on this connection; refusing to serve a "
                            + "database whose referential integrity would be silently unenforced");
        }
    }

    private static boolean isForeignKeyEnforcementOn(SQLiteDatabase database) {
        try (Cursor cursor = database.rawQuery("PRAGMA foreign_keys", null)) {
            return cursor.moveToFirst() && cursor.getInt(0) == 1;
        }
    }
}
