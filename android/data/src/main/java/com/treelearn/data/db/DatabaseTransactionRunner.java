package com.treelearn.data.db;

import android.database.sqlite.SQLiteDatabase;

/**
 * The single, explicit transaction boundary for local writes.
 *
 * <p>Cross-table facts — most importantly the first-topic write described in 技术方案 §8 — must be
 * committed or rolled back as a unit. Making the boundary a separate, injectable object keeps
 * "who owns the transaction" visible at the call site instead of hiding nesting inside repositories.
 *
 * <p>Parallel to the Guard error-channel rule: a missing or {@code null} argument is an
 * {@link IllegalArgumentException}; a valid argument against a receiver in the wrong state (the
 * connection is already inside a transaction) is an {@link IllegalStateException}. Nesting is
 * refused rather than silently joined, because joining would let an inner failure be committed by
 * an outer success.
 */
public final class DatabaseTransactionRunner {

    /** The work to execute inside one transaction. */
    public interface Work<T> {

        T execute(SQLiteDatabase database);
    }

    /**
     * Runs {@code work} inside a single transaction.
     *
     * @return whatever {@code work} returned, after the transaction has been committed
     * @throws IllegalArgumentException if an argument is {@code null}
     * @throws IllegalStateException    if the connection is already inside a transaction, or if
     *                                  {@code work} ended the transaction it was given
     */
    public <T> T run(SQLiteDatabase database, Work<T> work) {
        if (database == null) {
            throw new IllegalArgumentException("database must not be null");
        }
        if (work == null) {
            throw new IllegalArgumentException("work must not be null");
        }
        if (database.inTransaction()) {
            throw new IllegalStateException(
                    "refusing to open a nested transaction; the caller already owns one and a "
                            + "joined transaction would commit an inner failure");
        }

        database.beginTransaction();
        try {
            T result = work.execute(database);
            // Android's beginTransaction() silently joins an enclosing transaction, so a work body
            // that calls beginTransaction() + setTransactionSuccessful() + endTransaction() can
            // commit this transaction early. Checking afterwards is the only way to notice: the
            // finally block can roll back a transaction that is still open, but it cannot undo a
            // commit that already happened.
            if (!database.inTransaction()) {
                throw new IllegalStateException(
                        "the work body ended the transaction it was given; the runner's single "
                                + "transaction boundary must not be bypassed by nested commit calls");
            }
            database.setTransactionSuccessful();
            return result;
        } finally {
            // Only touch the transaction if it is still ours. When a work body already ended it,
            // endTransaction() would throw "no current transaction" and mask the real failure.
            if (database.inTransaction()) {
                database.endTransaction();
            }
        }
    }
}
