package com.treelearn.app.screen;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * The settings center's entry binding guard (T-092 携带的 T-087 缺陷修复): every second-level entry
 * the settings screen offers must actually bind its click, not just remember the callback. The
 * provider entry's {@code setOnProvidersPressed} stored the {@code Runnable} without ever attaching
 * a listener to {@code settings_providers_entry}, which made the entry dead — a settable-but-unbound
 * callback is exactly the drift a binding test exists to catch. Like the repo's other source-scan
 * guards, this reads the production source and fails loudly when the binding disappears again.
 */
public class SettingsProvidersBindingTest {

    private static final Path SRC = Paths.get("src", "main", "java", "com", "treelearn", "app",
            "screen", "SettingsScreen.java");

    @Test
    public void theProvidersEntryCallbackActuallyBindsItsClick() throws IOException {
        String source = new String(Files.readAllBytes(SRC), "UTF-8");
        String method = methodBody(source, "setOnProvidersPressed");

        assertTrue("setOnProvidersPressed must bind settings_providers_entry's click, "
                + "not only store the callback", method.contains("settings_providers_entry")
                && method.contains("setOnClickListener"));
    }

    private static String methodBody(String source, String methodName) {
        int at = source.indexOf(methodName);
        assertTrue("the method must exist: " + methodName, at >= 0);
        int open = source.indexOf('{', at);
        int close = source.indexOf('}', open);
        return source.substring(open, close);
    }
}
