package com.treelearn.web.storage;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Versioned, fail-closed migration runner for the desktop SQLite store (task T-2026-10-10-009).
 *
 * <p>Semantics, all proven on the real sqlite-jdbc engine by {@link DatabaseMigratorTest}:
 * <ul>
 *   <li>reads {@code PRAGMA user_version};</li>
 *   <li>a database newer than this build is refused with a readable message (no downgrade);</li>
 *   <li>an up-to-date database is a logged no-op;</li>
 *   <li>the whole pending chain is validated <em>before</em> anything runs, so a missing step is
 *       detected and refused instead of leaving a half-migrated database that reports itself as
 *       current;</li>
 *   <li>all pending steps run inside one transaction; any failure rolls the transaction back and
 *       leaves {@code user_version} at its original value.</li>
 * </ul>
 *
 * <p>The version is stamped with a safely concatenated integer ({@code PRAGMA} does not accept bound
 * parameters).
 */
public final class DatabaseMigrator {

    private static final Logger LOG = LoggerFactory.getLogger(DatabaseMigrator.class);

    private final List<Migration> migrations;
    private final int targetVersion;

    /** The production runner: the registered steps and the current schema version. */
    public DatabaseMigrator() {
        this(Migrations.forward(), Migrations.TARGET_VERSION);
    }

    /** A runner over an explicit step list and target, used to exercise the mechanism in tests. */
    public DatabaseMigrator(List<Migration> migrations, int targetVersion) {
        this.migrations = List.copyOf(migrations);
        this.targetVersion = targetVersion;
    }

    /** Opens the database file and migrates it. */
    public void migrate(Path databasePath) {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + databasePath)) {
            migrate(connection);
        } catch (SQLException failure) {
            throw new IllegalStateException(
                    "could not open or migrate the desktop database at " + databasePath, failure);
        }
    }

    /** Migrates the given open connection to {@link #targetVersion}. */
    public void migrate(Connection connection) {
        int current;
        try {
            current = readUserVersion(connection);
        } catch (SQLException failure) {
            throw new IllegalStateException("could not read PRAGMA user_version", failure);
        }

        if (current > targetVersion) {
            throw new IllegalStateException("downgrade is not supported: the database at version "
                    + current + " is newer than this build's schema version " + targetVersion);
        }
        if (current == targetVersion) {
            LOG.info("desktop schema is already up to date at user_version={}", targetVersion);
            return;
        }

        List<Migration> pending = new ArrayList<>();
        for (int version = current; version < targetVersion; version++) {
            Migration step = findFrom(version);
            if (step == null) {
                throw new IllegalStateException("no forward migration is registered from schema"
                        + " version " + version + " to " + (version + 1) + "; refusing to stamp"
                        + " user_version " + targetVersion + " on a half-migrated database");
            }
            pending.add(step);
        }

        long startedAt = System.nanoTime();
        boolean originalAutoCommit;
        try {
            originalAutoCommit = connection.getAutoCommit();
        } catch (SQLException failure) {
            throw new IllegalStateException("could not read the connection auto-commit mode", failure);
        }

        try {
            connection.setAutoCommit(false);
            int statementCount = 0;
            try {
                for (Migration step : pending) {
                    for (String statement : step.statements()) {
                        execute(connection, statement);
                        statementCount++;
                    }
                }
                setUserVersion(connection, targetVersion);
                connection.commit();
                LOG.info("desktop schema migrated user_version {} -> {} ({} statements, {} ms)",
                        current, targetVersion, statementCount, elapsedMillis(startedAt));
            } catch (Exception failure) {
                rollback(connection, failure);
                LOG.warn("desktop schema migration failed user_version {} -> {}; rolled back",
                        current, targetVersion);
                throw new IllegalStateException("desktop schema migration failed from version "
                        + current + " to " + targetVersion + "; the transaction was rolled back and"
                        + " user_version stays " + current, failure);
            }
        } catch (SQLException failure) {
            throw new IllegalStateException("could not manage the migration transaction", failure);
        } finally {
            restoreAutoCommit(connection, originalAutoCommit);
        }
    }

    private Migration findFrom(int fromVersion) {
        for (Migration migration : migrations) {
            if (migration.fromVersion() == fromVersion) {
                return migration;
            }
        }
        return null;
    }

    private static void execute(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private static int readUserVersion(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery("PRAGMA user_version")) {
            return rows.next() ? rows.getInt(1) : 0;
        }
    }

    private static void setUserVersion(Connection connection, int version) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA user_version = " + version);
        }
    }

    private static void rollback(Connection connection, Exception failure) {
        try {
            connection.rollback();
        } catch (SQLException rollbackFailure) {
            failure.addSuppressed(rollbackFailure);
        }
    }

    private static void restoreAutoCommit(Connection connection, boolean originalAutoCommit) {
        try {
            connection.setAutoCommit(originalAutoCommit);
        } catch (SQLException failure) {
            LOG.warn("could not restore the connection auto-commit mode: {}", failure.getMessage());
        }
    }

    private static long elapsedMillis(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000L;
    }
}
