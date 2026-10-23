package com.treelearn.app.screen;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

/**
 * Structural pins for who makes room for the floating tab bar (T-2026-10-22-062).
 *
 * <p>Before this slice the host padded itself by the bar's height, so the scroll viewport ended above
 * the capsule and content could never pass under the glass - a translucent face with nothing
 * translucent-able is what "不像是浮在上面" actually measured. The clearance moved to the scrolling
 * containers on purpose; these tests exist so it cannot silently move back, and so the number stays
 * a single fact.
 */
public final class TabBarUnderScrollLayoutTest {

    private static final Path SRC = Paths.get("src", "main", "java", "com", "treelearn", "app")
            .toAbsolutePath();
    private static final Path RES = Paths.get("src", "main", "res").toAbsolutePath();

    private static final String[] SCROLLING_SCREENS = {
        "layout/screen_home.xml", "layout/screen_library.xml",
        "layout-sw600dp/screen_home.xml", "layout-sw600dp/screen_library.xml",
    };

    private static String read(Path path) throws Exception {
        assertTrue("missing file: " + path, Files.exists(path));
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    private static String layout(String variantAndName) throws Exception {
        return read(RES.resolve(Paths.get(variantAndName.replace("/", File.separator))));
    }

    /**
     * The scrolling screens let their content run under the capsule and pay for it with their own
     * bottom padding, so a row can be read through the glass while it passes and still land whole
     * above it at the end of the scroll.
     */
    @Test
    public void theScrollingScreensCarryTheClearanceAndDontClipIt() throws Exception {
        for (String variant : SCROLLING_SCREENS) {
            String body = layout(variant);
            assertTrue(variant + " must let content paint inside its own padding",
                    body.contains("android:clipToPadding=\"false\""));
            assertTrue(variant + " must take the bar's room from the token, not from a literal dp",
                    body.contains("android:paddingBottom=\"@dimen/tab_bar_clearance\""));
        }
    }

    /** The host fills the window again; a screen's scroll container is the only thing that yields. */
    @Test
    public void theHostStopsPaddingItselfForTheBar() throws Exception {
        String main = read(SRC.resolve(Paths.get("MainActivity.java")));
        int swap = main.indexOf("private void swap(View screen)");
        assertTrue("swap still exists as the one place the bar is decided", swap > 0);
        String swapBody = main.substring(swap, main.indexOf("\n    }", swap));
        assertFalse("the host must not reserve the bar's band any more, or content never reaches it",
                swapBody.contains("host.setPadding("));
        assertTrue("one predicate still decides the bar and its room together",
                swapBody.contains("boolean firstLevel") && swapBody.contains("tabBar.setVisibility("));
    }

    /**
     * The room belongs to the two first-level destinations only. A second-level screen that carried
     * it would show a hole at the bottom of a page that has no capsule.
     */
    @Test
    public void onlyTheTabBarScreensPayTheClearance() throws Exception {
        List<String> payers = new ArrayList<>();
        for (File file : new File(RES.toFile(), "layout").listFiles()) {
            if (file.getName().endsWith(".xml")) {
                collect(file, "layout/" + file.getName(), payers);
            }
        }
        for (File file : new File(RES.toFile(), "layout-sw600dp").listFiles()) {
            if (file.getName().endsWith(".xml")) {
                collect(file, "layout-sw600dp/" + file.getName(), payers);
            }
        }
        assertTrue("exactly the two tab-bar screens and their tablet variants may carry it, got "
                + payers, payers.size() == 4);
    }

    private static void collect(File file, String label, List<String> out) throws Exception {
        if (read(file.toPath()).contains("android:paddingBottom=\"@dimen/tab_bar_clearance\"")) {
            out.add(label);
        }
    }

    /** Nobody writes the bar's height as a number, so the dimen stays the only authority for it. */
    @Test
    public void theClearanceIsNeverWrittenAsALiteral() throws Exception {
        String dimens = read(RES.resolve(Paths.get("values", "dimens.xml")));
        int start = dimens.indexOf("<dimen name=\"tab_bar_clearance\">");
        assertTrue("the clearance keeps one authoritative definition", start > 0);
        String value = dimens.substring(start + "<dimen name=\"tab_bar_clearance\">".length());
        value = value.substring(0, value.indexOf("<")).trim();
        for (String variant : SCROLLING_SCREENS) {
            assertFalse(variant + " must reference the token instead of repeating " + value,
                    layout(variant).contains("=\"" + value + "\""));
        }
    }
}
