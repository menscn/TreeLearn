package com.treelearn.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

import org.junit.Test;

public final class VersionSourceTest {

    @Test
    public void trackedVersionSourceStartsAtThreePartVersion() throws IOException {
        Path versionFile = Paths.get("").toAbsolutePath();
        for (int level = 0; level < 5 && versionFile != null; level++) {
            Path candidate = versionFile.resolve("version.properties");
            if (Files.isRegularFile(candidate)) {
                versionFile = candidate;
                break;
            }
            versionFile = versionFile.getParent();
        }
        assertTrue("android/version.properties must exist", Files.isRegularFile(versionFile));
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(versionFile)) {
            properties.load(input);
        }
        assertEquals("1.0.0", properties.getProperty("TREELEARN_VERSION"));
        assertTrue(properties.getProperty("TREELEARN_VERSION")
                .matches("[0-9]+\\.[0-9]+\\.[0-9]+"));
    }
}
