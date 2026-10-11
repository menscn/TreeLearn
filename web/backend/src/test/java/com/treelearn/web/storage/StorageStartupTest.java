package com.treelearn.web.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * T-2026-10-10-009: the real Spring startup path creates and migrates the desktop database, and a
 * second migration against the already-open database is a no-op. A database whose version is newer
 * than the program refuses to start the service (fail closed).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class StorageStartupTest {

    @TempDir
    static Path dataDir;

    @DynamicPropertySource
    static void pointAtTemporaryDataDir(DynamicPropertyRegistry registry) {
        registry.add("treelearn.data-dir", () -> dataDir.toString());
    }

    @Autowired
    private DesktopDatabase database;

    @Autowired
    private DatabaseMigrator migrator;

    @Test
    void startupCreatesTheDatabaseAndRerunningTheMigrationIsANoOp() throws Exception {
        assertTrue(Files.exists(database.databasePath()),
                "the startup migration must have created the database file");

        try (Connection connection = database.openConnection()) {
            assertEquals(DesktopSchema.SCHEMA_VERSION,
                    DesktopSchemaFreshInstallTest.userVersion(connection));
        }

        migrator.migrate(database.databasePath());

        try (Connection connection = database.openConnection()) {
            assertEquals(DesktopSchema.SCHEMA_VERSION,
                    DesktopSchemaFreshInstallTest.userVersion(connection),
                    "re-migrating an up-to-date database must not change the version");
        }
    }

    @Test
    void aDatabaseNewerThanTheProgramStopsTheService() throws Exception {
        Path newerDataDir = Files.createTempDirectory("treelearn-failclosed");
        Path dbPath = newerDataDir.resolve("treelearn.db");
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
                Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA user_version = 99");
        }

        new ApplicationContextRunner()
                .withUserConfiguration(StorageConfiguration.class)
                .withPropertyValues("treelearn.data-dir="
                        + newerDataDir.toAbsolutePath().toString().replace('\\', '/'))
                .run(context -> assertNotNull(context.getStartupFailure(),
                        "a database newer than the program must fail closed instead of starting"));
    }
}
