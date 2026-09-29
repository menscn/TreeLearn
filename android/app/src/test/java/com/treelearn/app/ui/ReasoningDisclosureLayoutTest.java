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

    /**
     * The reasoning area must not jitter while the model is thinking (2026-09-28 user report:
     * "思考过程对应的区域一直闪烁，直到正文出现后才结束跳动").
     *
     * <p>Two things caused it, and both are asserted here as gone:
     *
     * <ol>
     *   <li><strong>Every write was unconditional.</strong> {@code render} runs once per streamed
     *       state - once per token - and rewrote the body text, the state label and the content
     *       description every time. Each write costs a measure and a layout pass.
     *   <li><strong>The clamp read the wrong height.</strong> It compared {@code
     *       bodyScroll.getHeight()} - the previous frame's height - against the cap, while {@code
     *       setText} had already changed the content. The two disagree for one frame, so the
     *       decision oscillated between {@code WRAP_CONTENT} and the cap on every token.
     * </ol>
     *
     * <p>Read as a file, the way this suite reads the other view contracts: the JVM cannot measure
     * a ScrollView, but it can insist the caches and the correct measurement exist.
     */
    @Test
    public void theDisclosureDoesNotRewriteWhatDidNotChange() throws Exception {
        String source = new String(
                Files.readAllBytes(Paths.get("src", "main", "java", "com", "treelearn", "app",
                        "screen", "ReasoningDisclosure.java").toAbsolutePath()),
                StandardCharsets.UTF_8);

        assertTrue("the body must be written only when its text changed",
                source.contains("renderedBody") && source.contains("contentEquals(renderedBody)"));
        assertTrue("the state label must be written only when it changed",
                source.contains("renderedState") && source.contains("contentEquals(renderedState)"));
        assertTrue("the applied height must be remembered so it is not re-decided per token",
                source.contains("appliedHeight"));

        // The clamp's evidence: it must measure the child's content, never this container's own
        // previous height. getHeight() on bodyScroll is the exact expression that oscillated.
        assertTrue("the clamp must measure the content, not the container's previous height",
                source.contains("getMeasuredHeight()"));
        // Scoped to the method body: the javadoc above clampBodyHeight names the removed expression
        // on purpose, to record why it was wrong.
        String clampBody = source.substring(source.indexOf("private void clampBodyHeight()"));
        assertTrue("reading bodyScroll.getHeight() is the flicker; it must be gone from the method",
                !clampBody.contains("bodyScroll.getHeight()"));
    }

    /**
     * The half-folded body has to show the words the model is writing now, not the ones it wrote
     * first (2026-09-28 user report: "思考过程没有自动跟随滚动").
     *
     * <p>Read as a file, like the rest of this suite: the JVM cannot scroll a ScrollView, but it can
     * insist on the two halves of the contract - the body keeps its own end on screen, and a learner
     * who scrolls away keeps the position they chose (the conversation makes the same hand-over in
     * its follow-latest, T-075).
     */
    @Test
    public void theBodyFollowsItsOwnTail() throws Exception {
        String source = new String(
                Files.readAllBytes(Paths.get("src", "main", "java", "com", "treelearn", "app",
                        "screen", "ReasoningDisclosure.java").toAbsolutePath()),
                StandardCharsets.UTF_8);

        assertTrue("the follow state must exist", source.contains("followingTail"));
        assertTrue("the learner's own scroll must decide who owns the position",
                source.contains("setOnScrollChangeListener"));
        assertTrue("being at the end has to be measured against the content",
                source.contains("private boolean atTail()"));
        assertTrue("the body must be scrolled to its end",
                source.contains("bodyScroll.scrollTo(0, tail)"));
        assertTrue("a learner who scrolled up must keep the position they chose",
                source.contains("if (!followingTail)"));

        // The offset is only knowable where the content was measured: inside the clamp's own posted
        // runnable, not in a second post that would read a range from the previous frame.
        String clampBody = source.substring(source.indexOf("private void clampBodyHeight()"));
        assertTrue("the follow must ride on the measured content, inside the clamp",
                clampBody.contains("followTail(content, maxHeight)"));

        // The tail is taken back where a stream starts fresh: the field, the hidden branch and the
        // new-answer branch of render.
        int resets = source.split("followingTail = true", -1).length - 1;
        assertTrue("a new answer and a hidden disclosure must both take the tail back, found "
                + resets, resets >= 3);
    }
}
