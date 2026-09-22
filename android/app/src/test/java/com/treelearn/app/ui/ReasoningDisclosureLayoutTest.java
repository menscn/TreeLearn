package com.treelearn.app.ui;

import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.Test;

/**
 * Structural assertions for the reasoning disclosure's DeepSeek-style slice (T-066), read as plain
 * files so they gate the layout on the JVM like {@code IconAssetsTest} does:
 *
 * <ul>
 *   <li><strong>The body sits in a limited scrolling container.</strong> A ScrollView wraps the
 *       reasoning body - {@code android:maxHeight} on a TextView carries no scrolling semantics - and
 *       the cap is a dimens token, not an inline number.
 *   <li><strong>The subordinate style survives.</strong> A 2dp rule on the left (the token and the
 *       color are separate concerns: the width is new, the color is the existing {@code tl_line}),
 *       and the body keeps the secondary pair - {@code text_sm} on {@code tl_ink2} - that the
 *       readability check measures.
 * </ul>
 */
public final class ReasoningDisclosureLayoutTest {

    /** Module-relative root so the test works regardless of where Gradle runs it. */
    private static final Path RES = Paths.get("src", "main", "res").toAbsolutePath();

    private static String read(Path path) throws Exception {
        assertTrue("missing resource file: " + path, Files.exists(path));
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    /** The element that owns the id: from the tag opening it to the end of the open tag. */
    private static String elementAround(String xml, String id) {
        int idIndex = xml.indexOf("android:id=\"@+id/" + id + "\"");
        assertTrue("id " + id + " must exist", idIndex >= 0);
        int elementEnd = xml.indexOf('>', idIndex);
        assertTrue("malformed layout", elementEnd >= 0);
        return xml.substring(Math.max(0, xml.lastIndexOf('<', idIndex)), elementEnd);
    }

    @Test
    public void theBodySitsInALimitedScrollingContainer() throws Exception {
        String xml = read(RES.resolve(Paths.get("layout", "view_reasoning.xml")));
        String scroll = elementAround(xml, "reasoning_body_scroll");
        assertTrue("the body container must be a ScrollView",
                scroll.startsWith("<ScrollView"));
        assertTrue("the body must sit inside the scroll container",
                xml.indexOf("android:id=\"@+id/reasoning_body\"")
                        > xml.indexOf("android:id=\"@+id/reasoning_body_scroll\""));

        String dimens = read(RES.resolve(Paths.get("values", "dimens.xml")));
        assertTrue("the height cap must be a registered token, not an inline dp",
                dimens.contains("name=\"reasoning_max_height\""));
    }

    @Test
    public void theSubordinateStyleSurvives() throws Exception {
        String xml = read(RES.resolve(Paths.get("layout", "view_reasoning.xml")));

        String rule = elementAround(xml, "reasoning_rule");
        assertTrue("the rule must be a 2dp token wide", rule.contains("android:layout_width=\"@dimen/reasoning_rule\""));
        assertTrue("the rule must use the existing line color, no new token",
                rule.contains("android:background=\"@color/tl_line\""));

        String body = elementAround(xml, "reasoning_body");
        assertTrue("the body must keep the secondary ink", body.contains("android:textColor=\"@color/tl_ink2\""));
        assertTrue("the body must keep the small size", body.contains("android:textSize=\"@dimen/text_sm\""));

        String dimens = read(RES.resolve(Paths.get("values", "dimens.xml")));
        assertTrue("the rule width must be a registered token", dimens.contains("name=\"reasoning_rule\""));
    }
}
