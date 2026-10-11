package com.treelearn.web.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.treelearn.core.domain.GenerationModel;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * T-2026-10-10-009: a fresh install against the real sqlite-jdbc engine must land the exact final
 * desktop shape — the twelve tables, the fifteen named indexes, the eight named triggers and the
 * seeded builtin provider/model — and the connection factory must turn foreign key enforcement on
 * and report a clean integrity/foreign-key check.
 */
class DesktopSchemaFreshInstallTest {

    @TempDir
    Path dataDir;

    @Test
    void freshInstallCreatesTheFinalShapeOnRealSqlite() throws Exception {
        Path dbPath = dataDir.resolve("treelearn.db");
        new DatabaseMigrator().migrate(dbPath);

        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath)) {
            assertEquals(DesktopSchema.SCHEMA_VERSION, userVersion(connection),
                    "a fresh install must stamp the current schema version");

            assertEquals(new HashSet<>(Arrays.asList(DesktopSchema.TABLE_NAMES)),
                    names(connection, "table"), "every confirmed table must exist");
            assertEquals(12, DesktopSchema.TABLE_NAMES.length);

            Set<String> namedIndexes = names(connection, "index");
            namedIndexes.removeIf(name -> name.startsWith("sqlite_autoindex"));
            assertEquals(new HashSet<>(Arrays.asList(DesktopSchema.INDEX_NAMES)), namedIndexes,
                    "every named index must exist and nothing extra may be created");
            assertEquals(15, DesktopSchema.INDEX_NAMES.length);

            assertEquals(new HashSet<>(Arrays.asList(DesktopSchema.TRIGGER_NAMES)),
                    names(connection, "trigger"), "every invariant trigger must exist");

            assertEquals(1, count(connection, "providers"), "one seeded provider");
            assertEquals(DesktopSchema.BUILTIN_DEEPSEEK_PROVIDER_ID,
                    singleString(connection, "SELECT id FROM providers"));
            assertEquals(1, count(connection, "models"), "one seeded default model");
            assertEquals(DesktopSchema.BUILTIN_DEEPSEEK_DEFAULT_MODEL_ID,
                    singleString(connection, "SELECT id FROM models"));
            assertEquals(GenerationModel.FLASH.wireId,
                    singleString(connection, "SELECT model_id FROM models"));
        }
    }

    @Test
    void connectionFactoryEnforcesForeignKeysAndStaysHealthy() throws Exception {
        Path dbPath = dataDir.resolve("treelearn.db");
        new DatabaseMigrator().migrate(dbPath);

        DesktopDatabase database = new DesktopDatabase(dataDir);
        try (Connection connection = database.openConnection()) {
            assertEquals(1, readInt(connection, "PRAGMA foreign_keys"),
                    "every opened connection must enforce foreign keys");
            assertTrue(database.verifyIntegrity(connection), "integrity_check must report ok");
            assertTrue(database.foreignKeyCheck(connection).isEmpty(),
                    "foreign_key_check must return no violations");
        }
    }

    static int userVersion(Connection connection) throws SQLException {
        return readInt(connection, "PRAGMA user_version");
    }

    static int readInt(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery(sql)) {
            return rows.next() ? rows.getInt(1) : 0;
        }
    }

    static int count(Connection connection, String table) throws SQLException {
        return readInt(connection, "SELECT count(*) FROM " + table);
    }

    static String singleString(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery(sql)) {
            assertTrue(rows.next(), "expected one row for: " + sql);
            return rows.getString(1);
        }
    }

    static Set<String> names(Connection connection, String type) throws SQLException {
        List<String> names = new ArrayList<>();
        try (Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery(
                        "SELECT name FROM sqlite_master WHERE type = '" + type + "'")) {
            while (rows.next()) {
                names.add(rows.getString(1));
            }
        }
        return new HashSet<>(names);
    }
}
