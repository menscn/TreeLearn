package com.treelearn.app.ui;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.Test;

/**
 * T-074: structural assertions for the message row and its action bar, read as plain files so they
 * gate the layout on the JVM like {@code ReasoningDisclosureLayoutTest} does. Updated 2026-09-27 to
 * the user's A+B+C decision (the conversation page follows the design shot):
 *
 * <ul>
 *   <li><strong>The action row leads with the prototype's set.</strong> Copy, reset, fork, the
 *       data-driven child entry, the app's state-driven extras, and the time closes the row
 *       (prototype `.ai-actions`, lines 867-878).
 *   <li><strong>The learner's message is plain text</strong> (`.msg-user`, line 182), the same
 *       treatment the AI answer gets - the green bubble was retired on 2026-09-28 - and the answer
 *       body is flush: the green left edge is voided with the design shot (spec V1.1 §3.3).
 *   <li><strong>The AI tag is the plain brand + spark</strong> (`.at-brand`, line 187), not a chip.
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
    public void theActionBarLeadsWithThePrototypeSetAndTheTimeClosesIt() throws Exception {
        String xml = read(RES.resolve(Paths.get("layout", "view_answer_actions.xml")));
        int copy = xml.indexOf("android:id=\"@+id/act_copy\"");
        int reset = xml.indexOf("android:id=\"@+id/act_reset\"");
        int fork = xml.indexOf("android:id=\"@+id/act_fork\"");
        int children = xml.indexOf("android:id=\"@+id/act_children\"");
        int quote = xml.indexOf("android:id=\"@+id/act_quote\"");
        int translate = xml.indexOf("android:id=\"@+id/act_translate\"");
        int speak = xml.indexOf("android:id=\"@+id/act_speak\"");
        int time = xml.indexOf("android:id=\"@+id/act_time\"");
        assertTrue(copy >= 0);
        assertTrue("copy, reset, fork, the child entry, the extras, and the time last",
                copy < reset && reset < fork && fork < children && children < quote
                        && quote < translate && translate < speak && speak < time);
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
        // s1 (4dp): 2026-09-27 the row carries the prototype's set plus the app's extras, so the
        // 44dp-tall icon buttons tighten their gaps to keep the whole row inside a 360dp column.
        // Copy leads the row, so it carries no start margin - the others space off it.
        for (String id : new String[] {"act_quote", "act_fork", "act_children"}) {
            assertTrue(id + " must use the s1 spacing token", elementAround(xml, id)
                    .contains("android:layout_marginStart=\"@dimen/space_s1\""));
        }
        assertFalse("act_copy leads the row and must have no start margin",
                elementAround(xml, "act_copy").contains("android:layout_marginStart="));
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
    public void theAnswerBodyIsFlushAndTheGreenEdgeIsGone() throws Exception {
        // 2026-09-27 user decision: the left edge is voided with the design shot (spec V1.1 §3.3);
        // AI identity moved to the spark tag, the reasoning disclosure keeps its own tl_line.
        String xml = read(RES.resolve(Paths.get("layout", "item_chat_message.xml")));
        String answer = elementAround(xml, "chat_answer");
        assertTrue("the answer body must sit flush (no indent where the rule used to be)",
                !answer.contains("android:layout_marginStart="));
        // The streaming caret legitimately keeps the identity green; the edge view itself is gone.
        assertTrue("no left-edge rule may remain in the row",
                !xml.contains("@dimen/answer_rule"));

        String dimens = read(RES.resolve(Paths.get("values", "dimens.xml")));
        assertFalse("the retired answer_rule token must be deleted, not orphaned",
                dimens.contains("name=\"answer_rule\""));
    }

    @Test
    public void theLearnerMessageIsPlainTextRatherThanABubble() throws Exception {
        // 2026-09-28 user decision: the green bubble is gone. The question reads as body text with no
        // background of its own - the same treatment the AI answer gets - so the two roles differ only
        // by the tag above them and the question's being right-aligned.
        String xml = read(RES.resolve(Paths.get("layout", "item_chat_message.xml")));
        String block = elementAround(xml, "chat_question_block");
        assertTrue("the question must carry no bubble background of its own",
                !block.contains("android:background=") && !block.contains("@drawable/bg_bubble_user"));
        assertTrue("and it must not pad itself like a bubble, or the text shifts off the page gutter",
                !block.contains("android:padding="));
        assertTrue("the question still hugs the end like .msg-user (align-self:flex-end)",
                block.contains("android:layout_gravity=\"end\""));
        String question = elementAround(xml, "chat_question");
        assertTrue("the question's own text is the body ink, not the on-bubble surface colour",
                question.contains("android:textColor=\"@color/tl_ink\""));
    }

    @Test
    public void theAiTagIsThePlainBrandWithTheSpark() throws Exception {
        String xml = read(RES.resolve(Paths.get("layout", "item_chat_message.xml")));
        String tag = elementAround(xml, "chat_ai_tag");
        assertTrue("the tag is the plain brand + spark (.at-brand), not a chip",
                tag.contains("@drawable/ic_ai_spark") && !tag.contains("@drawable/bg_tag"));
        assertTrue("the brand reads green-deep like the prototype",
                tag.contains("android:textColor=\"@color/tl_green_deep\""));
        assertTrue("the spark is tinted green-deep too",
                tag.contains("android:drawableTint=\"@color/tl_green_deep\""));
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

    @Test
    public void theAnswerUsesNativeDraggableSelection() throws Exception {
        String row = read(RES.resolve(Paths.get("layout", "item_chat_message.xml")));
        String answer = elementAround(row, "chat_answer");
        assertTrue("the answer text must expose platform selection handles",
                answer.contains("android:textIsSelectable=\"true\""));

        String screen = read(SRC.resolve(Paths.get("screen", "ChatScreen.java")));
        assertTrue("the row must opt into selectable text",
                screen.contains("answer.setTextIsSelectable(true)"));
        assertTrue("the screen must not intercept long press",
                !screen.contains("answer.setOnLongClickListener"));
    }

    @Test
    public void theChatScreenAppliesSystemBarInsetsAndReusesRowsDuringStreaming() throws Exception {
        String screen = read(SRC.resolve(Paths.get("screen", "ChatScreen.java")));
        assertTrue("the chat root must avoid the status bar",
                screen.contains("setOnApplyWindowInsetsListener")
                        && screen.contains("getSystemWindowInsetTop()"));
        assertTrue("stream updates must reuse rows by message id",
                screen.contains("Map<String, Row> nextRows")
                        && screen.contains("Row row = rows.get(message.messageId)"));
        assertTrue("stream updates must not rebuild the whole message list",
                !screen.contains("messageFlow.removeAllViews()"));
    }

    @Test
    public void theComposerHidesUnavailableControlsAndKeepsAttachmentImport() throws Exception {
        String composer = read(RES.resolve(Paths.get("layout", "screen_chat_body.xml")));
        assertTrue("network search must be hidden", elementAround(composer, "chat_search_chip")
                .contains("android:visibility=\"gone\""));
        assertTrue("fullscreen entry must be hidden", elementAround(composer, "chat_expand")
                .contains("android:visibility=\"gone\""));
        assertTrue("voice entry must be hidden", elementAround(composer, "chat_voice")
                .contains("android:visibility=\"gone\""));
        assertTrue("attachment entry must use a paperclip icon",
                elementAround(composer, "chat_attach").contains("@drawable/ic_chat_attach"));
        // 2026-09-27 user decision: the two-level card with the circular send (prototype .composer).
        assertTrue("the send control is the circular fab in the layout too",
                elementAround(composer, "chat_send").contains("@drawable/bg_send_fab"));
        assertTrue("the input card is the paper surface on the strip",
                composer.contains("@drawable/bg_composer_inner"));
        String strings = read(RES.resolve(Paths.get("values", "strings.xml")));
        assertTrue("the hint names the branch, like the prototype placeholder",
                strings.contains("<string name=\"chat_draft_hint\">在当前分支继续追问…</string>"));
        assertTrue("the attach pill carries its label",
                strings.contains("<string name=\"chat_attach_label\">附件</string>"));
    }

    @Test
    public void theActionBarKeepsTheVersionSwitcherHiddenAndShowsThePrototypeSet() throws Exception {
        // 2026-09-27 user decision: copy and fork return to the row (prototype .ai-actions); the
        // version switcher stays a hidden compatibility entry, driven by Java.
        String actions = read(RES.resolve(Paths.get("layout", "view_answer_actions.xml")));
        assertTrue("act_version must stay hidden", elementAround(actions, "act_version")
                .contains("android:visibility=\"gone\""));
        for (String id : new String[] {"act_copy", "act_fork"}) {
            assertFalse(id + " must be visible again", elementAround(actions, id)
                    .contains("android:visibility=\"gone\""));
        }
    }

    @Test
    public void theSelectionMenuSaysNewTopicAndEmptiesThePlatformToolbar() throws Exception {
        // 2026-09-27 (user decision): the operation that opens a branch from an answer is 新建主题, and
        // the three entrances - the selection menu, the answer action row and the recommendation strip's
        // fork zone - read the same four strings, so a rename that misses one is caught here.
        String strings = read(RES.resolve(Paths.get("values", "strings.xml")));
        assertTrue("the selection menu's row", strings.contains(
                "<string name=\"chat_act_fork\">新建主题</string>"));
        assertTrue("the action row's face", strings.contains(
                "<string name=\"cd_act_fork\">新建主题</string>"));
        assertTrue("the action row's description", strings.contains(
                "<string name=\"chat_fork_title\">新建主题："));
        assertTrue("the recommendation strip's description", strings.contains(
                "<string name=\"chat_suggestion_fork_title\">直接用这个问题新建主题</string>"));
        assertFalse("no user-facing copy may still say 新建分支", strings.contains("新建分支"));

        // The four rows are the app's own card now: the platform's selection menu is emptied instead of
        // used, which is what keeps 新建主题 out of the platform's 更多 sheet.
        String screen = read(SRC.resolve(Paths.get("screen", "ChatScreen.java")));
        assertTrue("the platform selection toolbar must be emptied", screen.contains("menu.clear()"));
        assertFalse("no action may be handed to the platform selection menu",
                screen.contains("menu.add(Menu.NONE, R.id.chat_selection"));
    }
}
