package com.treelearn.app.screen;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.Test;

/**
 * T-075: the motion tokens (073 visual spec §4/§9) and the low-motion degradation (§2 S-LOWMOTION).
 *
 * <p>The four durations are 073 §9's new token candidates and live in exactly one file until the
 * token gate adopts them; the low-motion rule is the system animator scale reading zero, and it
 * switches every motion straight to its end state without touching the state text.
 */
public final class ChatMotionTest {

    private static final Path SRC = Paths.get("src", "main", "java", "com", "treelearn", "app",
            "screen").toAbsolutePath();

    private static String read(String name) throws Exception {
        Path path = SRC.resolve(name);
        assertTrue("missing file: " + path, Files.exists(path));
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    @Test
    public void theMotionTokensMatchTheSpecCandidates() {
        assertEquals(150L, ChatMotion.QUICK_MS);
        assertEquals(220L, ChatMotion.ENTER_MS);
        assertEquals(180L, ChatMotion.COLLAPSE_MS);
        assertEquals(800L, ChatMotion.CARET_STEP_MS);
    }

    @Test
    public void aZeroAnimatorScaleIsLowMotion() {
        assertTrue("the system's off switch must staticize every motion",
                ChatMotion.isStatic(0f));
        assertFalse(ChatMotion.isStatic(1f));
        assertFalse("partial scales are not low motion",
                ChatMotion.isStatic(0.5f));
    }

    @Test
    public void theScaleIsReadFromTheSystemSetting() throws Exception {
        String motion = read("ChatMotion.java");
        assertTrue("the screen must read Settings.Global.ANIMATOR_DURATION_SCALE",
                motion.contains("ANIMATOR_DURATION_SCALE"));
    }

    @Test
    public void theDurationsLiveInOnePlace() throws Exception {
        String motion = read("ChatMotion.java");
        assertTrue(motion.contains("QUICK_MS"));
        assertTrue(motion.contains("ENTER_MS"));
        assertTrue(motion.contains("COLLAPSE_MS"));
        assertTrue(motion.contains("CARET_STEP_MS"));

        String screen = read("ChatScreen.java");
        assertTrue("the screen must not hardcode durations, it must read ChatMotion",
                screen.contains("ChatMotion."));
    }
}
