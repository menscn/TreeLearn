package com.treelearn.web.storage;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;

/**
 * T-2026-10-10-009: DDL has exactly one home. Every schema statement lives in {@link DesktopSchema};
 * no other production file may carry a {@code CREATE TABLE/INDEX/TRIGGER} or {@code ALTER TABLE},
 * so a second, drifting copy of the schema cannot appear in a business path (rules/sql.md).
 */
class DdlLocationGuardTest {

    private static final List<String> FORBIDDEN_TOKENS = List.of(
            "CREATE TABLE",
            "CREATE INDEX",
            "CREATE UNIQUE INDEX",
            "CREATE TRIGGER",
            "ALTER TABLE");

    @Test
    void ddlAppearsOnlyInDesktopSchema() throws IOException {
        Path sourceRoot = Path.of("src/main/java");
        assertTrue(Files.isDirectory(sourceRoot),
                "expected src/main/java relative to the backend working directory, found: "
                        + sourceRoot.toAbsolutePath());

        List<Path> javaFiles;
        try (var paths = Files.walk(sourceRoot)) {
            javaFiles = paths.filter(path -> path.toString().endsWith(".java")).toList();
        }
        assertFalse(javaFiles.isEmpty(), "the guard must scan at least one production file");

        for (Path file : javaFiles) {
            if (file.getFileName().toString().equals("DesktopSchema.java")) {
                continue;
            }
            String content = Files.readString(file).toUpperCase(Locale.ROOT);
            for (String token : FORBIDDEN_TOKENS) {
                assertFalse(content.contains(token),
                        file + " must not contain DDL token '" + token
                                + "'; schema DDL belongs to DesktopSchema.java only");
            }
        }
    }
}
