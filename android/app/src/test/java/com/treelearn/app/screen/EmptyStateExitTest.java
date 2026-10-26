package com.treelearn.app.screen;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.Test;

/**
 * T-2026-10-25-096 (review AC3): an empty state that only says what is missing is a dead end.
 *
 * <p>The board named two surfaces - the conversation page's whitespace and the overview canvas's
 * 「这棵树还没有分支。」. This build has no chat EMPTY phase (the reading page always has at least
 * the root question, so its whitespace is a short conversation rather than an empty state), and the
 * canvas is the one place with a real EMPTY phase. The exit therefore lands there, and this pin
 * keeps it from being removed as "decoration" by a later slice.
 *
 * <p>Like the neighbouring layout tests this reads the XML and the Java source on the JVM, so the
 * gate needs no device.
 */
public final class EmptyStateExitTest {

    private static final Path RES = Paths.get("src", "main", "res").toAbsolutePath();
    private static final Path SRC = Paths.get("src", "main", "java", "com", "treelearn", "app");

    private static String read(Path path) throws Exception {
        assertTrue("missing file: " + path, Files.exists(path));
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    private static String elementAround(String xml, String id) {
        int idIndex = xml.indexOf("android:id=\"@+id/" + id + "\"");
        assertTrue("id " + id + " must exist", idIndex >= 0);
        int elementEnd = xml.indexOf('>', idIndex);
        assertTrue("malformed layout", elementEnd >= 0);
        return xml.substring(Math.max(0, xml.lastIndexOf('<', idIndex)), elementEnd);
    }

    @Test
    public void theOverviewEmptyStateOffersTheOneStepThatExists() throws Exception {
        String tree = read(RES.resolve(Paths.get("layout", "screen_tree_body.xml")));
        int text = tree.indexOf("android:id=\"@+id/tree_empty\"");
        int action = tree.indexOf("android:id=\"@+id/tree_empty_action\"");
        assertTrue("the empty state keeps its sentence", text >= 0);
        assertTrue("and gains an exit directly under it", action > text);

        String button = elementAround(tree, "tree_empty_action");
        assertTrue("the exit is a real 44dp control",
                button.contains("android:layout_height=\"@dimen/touch_min\""));
        assertTrue("it is a button, not a link",
                button.startsWith("<Button") || button.contains("android:background="));
        assertTrue("it carries its own label", button.contains("@string/tree_empty_action"));

        String screen = read(SRC.resolve(Paths.get("screen", "TreeCanvasScreen.java")));
        assertTrue("it is wired to the one gesture the canvas already has",
                screen.contains("R.id.tree_empty_action")
                        && screen.contains("TreeCanvasActions::onBackPressed"));
        assertTrue("and it only shows in the EMPTY phase",
                screen.contains("emptyAction.setVisibility(")
                        && screen.contains("Phase.EMPTY"));
        // 2026-10-25: the conversation page has no EMPTY phase; an exit invented for whitespace
        // that is really a short conversation would be a control with nothing to do.
        String chat = read(SRC.resolve(Paths.get("screen", "ChatScreen.java")));
        assertFalse("no invented chat empty-exit key", chat.contains("chat_empty_exit"));
    }
}
