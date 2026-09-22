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
 *
 * <p><strong>What this class does not do.</strong> A work body is handed the very same
 * {@link SQLiteDatabase} the runner opened the transaction on, so it can legally call
 * {@code setTransactionSuccessful()} and {@code endTransaction()} itself and commit early. Nothing
 * in the platform API can prevent that short of hiding the connection, which would defeat the
 * purpose of passing it in. What the runner does is <em>detect</em> it afterwards and fail: the
 * caller gets an {@link IllegalStateException}, but the rows written before the stolen commit are
 * already durable. Callers must therefore treat that exception as "the transaction boundary was
 * violated and the database may hold partial work", not as "nothing happened". The acceptance suite
 * pins this semantics down explicitly instead of leaving it to be inferred from the name of the
 * class.
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
     *                                  {@code work} ended the transaction it was given. In the
     *                                  second case the work body's writes have already been
     *                                  committed; the exception reports the violation, it does not
     *                                  undo it.
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
