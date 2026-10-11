package com.treelearn.web.storage;

import static com.treelearn.web.storage.DesktopSchemaFreshInstallTest.names;
import static com.treelearn.web.storage.DesktopSchemaFreshInstallTest.userVersion;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * T-2026-10-10-009: the migrator's versioned semantics on the real engine — reopen idempotency, a
 * refused downgrade, a fail-closed missing step, and whole-chain rollback that leaves no half
 * migrated object behind (all pending steps run inside one transaction).
 */
class DatabaseMigratorTest {

    @TempDir
    Path dataDir;

    private Path dbPath() {
        return dataDir.resolve("treelearn.db");
    }

    @Test
    void rerunningMigrationOnAnOpenDatabaseIsANoOp() throws Exception {
        Path dbPath = dbPath();
        DatabaseMigrator migrator = new DatabaseMigrator();
        migrator.migrate(dbPath);
        assertEquals(DesktopSchema.SCHEMA_VERSION, versionOf(dbPath));

        migrator.migrate(dbPath);
        assertEquals(DesktopSchema.SCHEMA_VERSION, versionOf(dbPath),
                "a second run must neither change the version nor fail");
    }

    @Test
    void downgradeIsRefusedWithAReadableMessage() throws Exception {
        Path dbPath = dbPath();
        new DatabaseMigrator().migrate(dbPath);
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
                Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA user_version = 99");
        }

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> new DatabaseMigrator().migrate(dbPath));
        assertTrue(failure.getMessage().contains("downgrade")
                        || failure.getMessage().contains("newer"),
                "the refusal must be readable: " + failure.getMessage());
        assertEquals(99, versionOf(dbPath), "a refused downgrade must not touch the version");
    }

    @Test
    void aMissingStepFailsClosedBeforeAnyStatementRuns() throws Exception {
        Path dbPath = dbPath();
        // Target version 2, but only the real 0 -> 1 step is registered.
        DatabaseMigrator migrator = new DatabaseMigrator(
                List.of(fullStep(0, 1)), 2);

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> migrator.migrate(dbPath));
        assertTrue(failure.getMessage().contains("no forward migration"),
                "the guard must name the missing step: " + failure.getMessage());

        assertEquals(0, versionOf(dbPath), "the version must stay at the source version");
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath)) {
            assertEquals(0, names(connection, "table").size(),
                    "a missing step must be detected before any statement runs");
        }
    }

    @Test
    void aFailingStepRollsBackTheWholeChainLeavingNoObjects() throws Exception {
        Path dbPath = dbPath();
        // 0 -> 1 is the real full install; 1 -> 2 carries invalid SQL. Both run in one transaction.
        DatabaseMigrator migrator = new DatabaseMigrator(
                List.of(fullStep(0, 1), new FixedMigration(1, 2, List.of("THIS IS NOT VALID SQL"))), 2);

        assertThrows(IllegalStateException.class, () -> migrator.migrate(dbPath));

        assertEquals(0, versionOf(dbPath),
                "a failed chain must not advance user_version");
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath)) {
            assertEquals(0, names(connection, "table").size(),
                    "no half-migrated table may survive the rollback");
        }
    }

    private static Migration fullStep(int from, int to) {
        return new FixedMigration(from, to, DesktopSchema.CREATE_STATEMENTS);
    }

    private static int versionOf(Path dbPath) throws SQLException {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath)) {
            return userVersion(connection);
        }
    }

    private record FixedMigration(int fromVersion, int toVersion, List<String> statements)
            implements Migration {
    }
}
