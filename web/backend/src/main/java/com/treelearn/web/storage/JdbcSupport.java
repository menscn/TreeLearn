package com.treelearn.web.storage;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Mechanical JDBC support for the desktop stores (task T-2026-10-10-010).
 *
 * <p>It owns connection handling and nothing else: a store hands in SQL text, a parameter binder and a
 * row mapper, and this class prepares, binds, executes and translates. No ORM and no new dependency is
 * introduced (rules/sql.md). {@link #runInTransaction} is the method-level transaction template the
 * task asks for: the work body runs inside one transaction and any failure rolls it back, which is
 * what makes "one topic plus its root branch" atomic.
 *
 * <p>{@link SQLException} is always wrapped in {@link StorageException} with the failing SQL text, so
 * a constraint refusal reaches the caller as a readable cause. The connection factory
 * ({@link DesktopDatabase}) enables and verifies foreign keys on every connection, so the cascades and
 * {@code CHECK}s these stores lean on are actually enforced.
 */
public final class JdbcSupport {

    /** Binds parameters onto a statement; parameter indexes are 1-based, as JDBC requires. */
    public interface Binder {
        void bind(PreparedStatement statement) throws SQLException;
    }

    /** Reads exactly one row into a value. */
    public interface RowMapper<T> {
        T map(ResultSet rows) throws SQLException;
    }

    /** A unit of work that owns a single open connection. */
    public interface Work<T> {
        T run(Connection connection) throws SQLException;
    }

    /** A binder for statements without parameters. */
    public static final Binder NO_PARAMS = statement -> { };

    private final DesktopDatabase database;

    public JdbcSupport(DesktopDatabase database) {
        this.database = database;
    }

    public <T> Optional<T> queryOne(String sql, Binder binder, RowMapper<T> mapper) {
        return withConnection(connection -> queryOne(connection, sql, binder, mapper));
    }

    public <T> List<T> queryList(String sql, Binder binder, RowMapper<T> mapper) {
        return withConnection(connection -> queryList(connection, sql, binder, mapper));
    }

    public int update(String sql, Binder binder) {
        return withConnection(connection -> update(connection, sql, binder));
    }

    public <T> Optional<T> queryOne(Connection connection, String sql, Binder binder,
            RowMapper<T> mapper) {
        List<T> rows = queryList(connection, sql, binder, mapper);
        if (rows.size() > 1) {
            throw new StorageException("expected at most one row but got " + rows.size() + ": " + sql);
        }
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    public <T> List<T> queryList(Connection connection, String sql, Binder binder,
            RowMapper<T> mapper) {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            binder.bind(statement);
            try (ResultSet rows = statement.executeQuery()) {
                List<T> results = new ArrayList<>();
                while (rows.next()) {
                    results.add(mapper.map(rows));
                }
                return results;
            }
        } catch (SQLException failure) {
            throw new StorageException("query failed: " + sql, failure);
        }
    }

    public int update(Connection connection, String sql, Binder binder) {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            binder.bind(statement);
            return statement.executeUpdate();
        } catch (SQLException failure) {
            throw new StorageException("update failed: " + sql, failure);
        }
    }

    /** Runs a whole unit of work inside one transaction; any failure rolls it back. */
    public <T> T runInTransaction(Work<T> work) {
        try (Connection connection = database.openConnection()) {
            connection.setAutoCommit(false);
            try {
                T result = work.run(connection);
                connection.commit();
                return result;
            } catch (Exception failure) {
                rollback(connection, failure);
                throw failure instanceof RuntimeException runtime ? runtime
                        : new StorageException("transaction failed and was rolled back", failure);
            }
        } catch (SQLException failure) {
            throw new StorageException("could not use the desktop database connection", failure);
        }
    }

    private <T> T withConnection(Work<T> work) {
        try (Connection connection = database.openConnection()) {
            return work.run(connection);
        } catch (SQLException failure) {
            throw new StorageException("could not use the desktop database connection", failure);
        }
    }

    static Integer nullableInt(ResultSet rows, String column) throws SQLException {
        int value = rows.getInt(column);
        return rows.wasNull() ? null : Integer.valueOf(value);
    }

    static Long nullableLong(ResultSet rows, String column) throws SQLException {
        long value = rows.getLong(column);
        return rows.wasNull() ? null : Long.valueOf(value);
    }

    static boolean flag(ResultSet rows, String column) throws SQLException {
        return rows.getInt(column) != 0;
    }

    static void requireSingleRow(int updated, String action) {
        if (updated != 1) {
            throw new StorageException(action + " affected " + updated
                    + " row(s); it must touch exactly its own row");
        }
    }

    private static void rollback(Connection connection, Exception failure) {
        try {
            connection.rollback();
        } catch (SQLException rollbackFailure) {
            failure.addSuppressed(rollbackFailure);
        }
    }
}
