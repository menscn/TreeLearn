package com.treelearn.web.storage;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Connection factory for the desktop SQLite store (task T-2026-10-10-009).
 *
 * <p>Every connection this factory hands out has foreign key enforcement on: the factory enables
 * {@code PRAGMA foreign_keys = ON}, {@code PRAGMA busy_timeout = 5000} and {@code PRAGMA
 * journal_mode = WAL} outside any transaction, then reads {@code foreign_keys} back and refuses the
 * connection if it did not take effect. The read-back is deliberate — a silently ignored PRAGMA
 * would turn every cascade and {@code CHECK} into a no-op.
 *
 * <p>{@link #verifyIntegrity(Connection)} and {@link #foreignKeyCheck(Connection)} are startup
 * self-check helpers.
 */
public final class DesktopDatabase {

    private static final String DATABASE_FILE = "treelearn.db";

    private final Path dataDir;
    private final Path databasePath;
    private final String jdbcUrl;

    public DesktopDatabase(Path dataDir) {
        this.dataDir = dataDir.toAbsolutePath().normalize();
        this.databasePath = this.dataDir.resolve(DATABASE_FILE);
        this.jdbcUrl = "jdbc:sqlite:" + this.databasePath;
    }

    public Path dataDir() {
        return dataDir;
    }

    public Path databasePath() {
        return databasePath;
    }

    public String jdbcUrl() {
        return jdbcUrl;
    }

    /** Opens a connection with foreign keys enforced, verified, and WAL enabled. */
    public Connection openConnection() throws SQLException {
        Connection connection = DriverManager.getConnection(jdbcUrl);
        boolean ready = false;
        try {
            try (Statement statement = connection.createStatement()) {
                statement.execute("PRAGMA foreign_keys = ON");
                statement.execute("PRAGMA busy_timeout = 5000");
                statement.execute("PRAGMA journal_mode = WAL");
            }
            if (readInt(connection, "PRAGMA foreign_keys") != 1) {
                throw new SQLException("foreign key enforcement could not be enabled on "
                        + databasePath);
            }
            ready = true;
            return connection;
        } finally {
            if (!ready) {
                connection.close();
            }
        }
    }

    /** {@code true} when {@code PRAGMA integrity_check} reports {@code ok}. */
    public boolean verifyIntegrity(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery("PRAGMA integrity_check")) {
            return rows.next() && "ok".equalsIgnoreCase(rows.getString(1));
        }
    }

    /** The {@code PRAGMA foreign_key_check} violations; an empty list means the store is consistent. */
    public List<String> foreignKeyCheck(Connection connection) throws SQLException {
        List<String> violations = new ArrayList<>();
        try (Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery("PRAGMA foreign_key_check")) {
            while (rows.next()) {
                violations.add(rows.getString(1) + " -> " + rows.getString(2));
            }
        }
        return violations;
    }

    private static int readInt(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery(sql)) {
            return rows.next() ? rows.getInt(1) : 0;
        }
    }
}
