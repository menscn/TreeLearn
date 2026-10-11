package com.treelearn.web.storage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * Wires desktop storage into the service startup (task T-2026-10-10-009).
 *
 * <p>The data directory comes from {@code treelearn.data-dir} (default
 * {@code %LOCALAPPDATA%\TreeLearn-Web}); the environment variable {@code TREELEARN_DATA_DIR} is
 * honoured through Spring's relaxed binding. The directory and its {@code attachments/} and
 * {@code logs/} children are created, then the database is created/migrated as part of building the
 * {@link DesktopDatabase} bean — so a migration failure fails the context and the service does not
 * start (fail closed). Logs carry only the version, statement count, elapsed time and failure
 * category; never row content or keys.
 */
@Configuration
public class StorageConfiguration {

    private static final Logger LOG = LoggerFactory.getLogger(StorageConfiguration.class);

    private static final String DEFAULT_DIR_NAME = "TreeLearn-Web";
    private static final String ATTACHMENTS_DIR = "attachments";
    private static final String LOGS_DIR = "logs";

    @Bean
    DatabaseMigrator databaseMigrator() {
        return new DatabaseMigrator();
    }

    @Bean
    DesktopDatabase desktopDatabase(Environment environment, DatabaseMigrator migrator)
            throws IOException {
        Path dataDir = resolveDataDir(environment.getProperty("treelearn.data-dir"));
        Files.createDirectories(dataDir);
        Files.createDirectories(dataDir.resolve(ATTACHMENTS_DIR));
        Files.createDirectories(dataDir.resolve(LOGS_DIR));

        DesktopDatabase database = new DesktopDatabase(dataDir);
        migrateRecoverAndSelfCheck(database, migrator);
        return database;
    }

    /**
     * The startup order this method owns: migrate, then settle any attempt a dead process left behind,
     * then self-check — and only then is the store ready. Each step fails closed: a migration, sweep or
     * integrity failure aborts the {@link DesktopDatabase} bean so the service does not start serving
     * unsettled rows.
     */
    private void migrateRecoverAndSelfCheck(DesktopDatabase database, DatabaseMigrator migrator) {
        try {
            migrator.migrate(database.databasePath());
        } catch (RuntimeException failure) {
            throw new IllegalStateException("desktop storage is not ready: migration failed for data"
                    + " directory " + database.dataDir(), failure);
        }
        try {
            int settled = new InterruptionSweeper(new OperationStore(new JdbcSupport(database)),
                    Clock.systemUTC()).sweep();
            if (settled > 0) {
                LOG.info("desktop storage settled {} interrupted generation operation(s) on startup",
                        settled);
            }
        } catch (RuntimeException failure) {
            throw new IllegalStateException("desktop storage is not ready: the interruption sweep failed"
                    + " for data directory " + database.dataDir(), failure);
        }
        try (Connection connection = database.openConnection()) {
            boolean integrity = database.verifyIntegrity(connection);
            int violations = database.foreignKeyCheck(connection).size();
            LOG.info("desktop storage ready: db={} user_version={} integrity={}"
                            + " foreignKeyViolations={}",
                    database.databasePath(), DesktopSchema.SCHEMA_VERSION, integrity, violations);
        } catch (SQLException failure) {
            throw new IllegalStateException("desktop storage self-check failed for data directory "
                    + database.dataDir(), failure);
        }
    }

    /**
     * Resolves the data directory: an explicit value wins, otherwise {@code LOCALAPPDATA}, otherwise
     * {@code user.home\AppData\Local}. With none available the service refuses to start rather than
     * guessing a location.
     */
    static Path resolveDataDir(String configured) {
        if (configured != null && !configured.isBlank()) {
            return Path.of(configured.trim());
        }
        String localAppData = System.getenv("LOCALAPPDATA");
        if (localAppData != null && !localAppData.isBlank()) {
            return Path.of(localAppData, DEFAULT_DIR_NAME);
        }
        String home = System.getProperty("user.home");
        if (home != null && !home.isBlank()) {
            return Path.of(home, "AppData", "Local", DEFAULT_DIR_NAME);
        }
        throw new IllegalStateException("cannot resolve the TreeLearn data directory: set"
                + " treelearn.data-dir or TREELEARN_DATA_DIR (LOCALAPPDATA and user.home are both"
                + " unavailable)");
    }
}
