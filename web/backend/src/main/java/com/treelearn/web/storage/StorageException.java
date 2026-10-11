package com.treelearn.web.storage;

/**
 * Unchecked failure of the desktop SQLite store (task T-2026-10-10-010).
 *
 * <p>{@link JdbcSupport} raises it whenever {@link java.sql.SQLException} surfaces, so a constraint
 * refusal — a second root branch, a reused ordinal, a duplicate revision number — reaches the caller
 * as a readable cause with the failing SQL text instead of a checked exception the business layer
 * cannot act on. A row count that is not exactly one is reported the same way, and the message names
 * the action, so "which write touched the wrong number of rows" is answerable from the log line.
 */
public class StorageException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public StorageException(String message) {
        super(message);
    }

    public StorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
