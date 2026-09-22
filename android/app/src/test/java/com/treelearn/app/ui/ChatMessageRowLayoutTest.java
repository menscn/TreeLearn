package com.treelearn.app.ui;

import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.Test;

/**
 * T-074: structural assertions for the message row and its action bar (073 visual spec §3/§7),
 * read as plain files so they gate the layout on the JVM like {@code ReasoningDisclosureLayoutTest}
 * does:
 *
 * <ul>
 *   <li><strong>The action bar leads with the primary action and the switcher.</strong> Reset, then
 *       the version switcher, then the secondary icons, each 44dp tall and spaced by the s2 token.
 *   <li><strong>The switcher reads "n/N".</strong> The chip is small secondary ink and the TalkBack
 *       text names the displayed version's place (073 §7).
 *   <li><strong>The answer body carries the green left edge</strong> that marks AI identity instead
 *       of a card background, and the AI tag sits on the soft green surface.
 *   <li><strong>Tapping a code block offers copy</strong> (matrix 1.2): the renderer wires a click
 *       span and the screen forwards it to the existing selection-copy action. The renderer's span
 *       output itself needs a laid-out view, so this is the JVM-side slice of that behaviour.
 * </ul>
 */
public final class ChatMessageRowLayoutTest {

    /** Module-relative root so the test works regardless of where Gradle runs it. */
    private static final Path RES = Paths.get("src", "main", "res").toAbsolutePath();
    private static final Path SRC = Paths.get("src", "main", "java", "com", "treelearn", "app");

    private static String read(Path path) throws Exception {
        assertTrue("missing file: " + path, Files.exists(path));
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
    public void theActionBarLeadsWithThePrimaryActionAndTheSwitcher() throws Exception {
        String xml = read(RES.resolve(Paths.get("layout", "view_answer_actions.xml")));
        int reset = xml.indexOf("android:id=\"@+id/act_reset\"");
        int version = xml.indexOf("android:id=\"@+id/act_version\"");
        int quote = xml.indexOf("android:id=\"@+id/act_quote\"");
        int copy = xml.indexOf("android:id=\"@+id/act_copy\"");
        int fork = xml.indexOf("android:id=\"@+id/act_fork\"");
        int children = xml.indexOf("android:id=\"@+id/act_children\"");
        assertTrue(reset >= 0);
        assertTrue("reset, then the switcher, then the secondary icons, then the child entry",
                reset < version && version < quote && quote < copy && copy < fork && fork < children);
    }

    @Test
    public void everyActionKeepsItsFortyFourDpHotZone() throws Exception {
        String xml = read(RES.resolve(Paths.get("layout", "view_answer_actions.xml")));
        for (String id : new String[] {"act_quote", "act_copy", "act_reset", "act_fork",
                "act_children", "act_version"}) {
            assertTrue(id + " must stay 44dp tall", elementAround(xml, id)
                    .contains("android:layout_height=\"@dimen/touch_min\""));
        }
    }

    @Test
    public void theSecondaryIconsAreSpacedByTheSpacingToken() throws Exception {
        String xml = read(RES.resolve(Paths.get("layout", "view_answer_actions.xml")));
        for (String id : new String[] {"act_quote", "act_copy", "act_fork", "act_children"}) {
            assertTrue(id + " must use the s2 spacing token", elementAround(xml, id)
                    .contains("android:layout_marginStart=\"@dimen/space_s2\""));
        }
    }

    @Test
    public void theSwitcherReadsAsNOverN() throws Exception {
        String xml = read(RES.resolve(Paths.get("layout", "view_answer_actions.xml")));
        String chip = elementAround(xml, "act_version");
        assertTrue("the chip is the small size", chip.contains("android:textSize=\"@dimen/text_sm\""));
        assertTrue("the chip is the secondary ink", chip.contains("android:textColor=\"@color/tl_ink2\""));

        String strings = read(RES.resolve(Paths.get("values", "strings.xml")));
        assertTrue("the chip text is n over N",
                strings.contains("<string name=\"chat_version_chip\">%1$d/%2$d</string>"));
        assertTrue("TalkBack hears which answer is displayed out of how many",
                strings.contains("第 %1$d 个回答，共 %2$d 个版本"));
    }

    @Test
    public void theAnswerBodyCarriesTheGreenLeftEdge() throws Exception {
        String xml = read(RES.resolve(Paths.get("layout", "item_chat_message.xml")));
        int edge = xml.indexOf("android:background=\"@color/tl_green\"");
        int answer = xml.indexOf("android:id=\"@+id/chat_answer\"");
        assertTrue("the answer body must have a green edge view before it",
                edge >= 0 && edge < answer);
        assertTrue("the edge width must be a registered token, not an inline dp",
                xml.contains("android:layout_width=\"@dimen/answer_rule\""));

        String dimens = read(RES.resolve(Paths.get("values", "dimens.xml")));
        assertTrue(dimens.contains("name=\"answer_rule\""));
    }

    @Test
    public void theAiTagSitsOnTheSoftGreenSurface() throws Exception {
        String xml = read(RES.resolve(Paths.get("layout", "item_chat_message.xml")));
        String tag = elementAround(xml, "chat_ai_tag");
        assertTrue("the tag must sit on a small-radius surface", tag.contains("@drawable/bg_tag"));
        assertTrue("the surface must be tinted with the faint green", tag
                .contains("android:backgroundTint=\"@color/tl_green_faint\""));
    }

    @Test
    public void tappingACodeBlockOffersCopy() throws Exception {
        String renderer = read(SRC.resolve(Paths.get("screen", "MarkdownRenderer.java")));
        assertTrue("the renderer must wire a click span over code blocks",
                renderer.contains("ClickableSpan") && renderer.contains("OnCodeBlockClick"));
        String screen = read(SRC.resolve(Paths.get("screen", "ChatScreen.java")));
        assertTrue("the screen must forward the code tap to the selection-copy action",
                screen.contains("MarkdownRenderer.render(message.text,")
                        && screen.contains("onSelectionCopyPressed(code)"));
    }
}
