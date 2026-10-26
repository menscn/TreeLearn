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
 * gate the layout on the JVM like {@code ReasoningDisclosureLayoutTest} does. Updated 2026-09-28 to
 * the user's annotated-shot decision (the conversation page follows the design shot):
 *
 * <ul>
 *   <li><strong>The action row carries only the four prototype buttons.</strong> Copy, reset, fork
 *       and the data-driven child entry. The extras this row used to carry - quote, translation,
 *       speech - and the hidden version switcher were removed on 2026-09-28. 2026-10-25 removed the
 *       answer's time from this row too: the reading-area header states the branch's time once, so a
 *       copy after every answer was a second version of the same fact.
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

    /**
     * The element that owns the id <em>and everything inside it</em>, so an assertion can see the
     * children - a glyph ImageView, a badge TextView - that an open-tag-only slice would miss. The
     * slice runs to the element's matching close tag, counted by nesting depth, so a sibling that
     * happens to follow is never swallowed. A self-closing tag returns itself.
     */
    private static String elementWithBody(String xml, String id) {
        String body = xml.replaceAll("(?s)<!--.*?-->", "");
        int start = body.indexOf("android:id=\"@+id/" + id + "\"");
        assertTrue("id " + id + " must exist", start >= 0);
        int open = body.lastIndexOf('<', start);
        int openEnd = body.indexOf('>', start);
        assertTrue("malformed layout", openEnd >= 0);
        if (body.charAt(openEnd - 1) == '/') {
            return body.substring(open, openEnd + 1);
        }
        int nameEnd = open + 1;
        while (nameEnd < openEnd && !Character.isWhitespace(body.charAt(nameEnd))) {
            nameEnd++;
        }
        String tag = body.substring(open + 1, nameEnd);
        java.util.regex.Pattern openTag = java.util.regex.Pattern.compile("<" + java.util.regex.Pattern.quote(tag) + "(?=[\\s/>])");
        java.util.regex.Pattern closeTag = java.util.regex.Pattern.compile("</" + java.util.regex.Pattern.quote(tag) + "\\s*>");
        int depth = 0;
        int cursor = open;
        while (cursor < body.length()) {
            java.util.regex.Matcher nextOpen = openTag.matcher(body);
            java.util.regex.Matcher nextClose = closeTag.matcher(body);
            boolean hasOpen = nextOpen.find(cursor);
            boolean hasClose = nextClose.find(cursor);
            if (hasClose && (!hasOpen || nextClose.start() < nextOpen.start())) {
                depth--;
                if (depth == 0) {
                    return body.substring(open, nextClose.end());
                }
                cursor = nextClose.end();
            } else if (hasOpen) {
                depth++;
                cursor = nextOpen.end();
            } else {
                break;
            }
        }
        throw new AssertionError("unterminated element: " + id);
    }

    @Test
    public void theActionBarLeadsWithThePrototypeSetAndCarriesNoTime() throws Exception {
        String xml = read(RES.resolve(Paths.get("layout", "view_answer_actions.xml")));
        int copy = xml.indexOf("android:id=\"@+id/act_copy\"");
        int reset = xml.indexOf("android:id=\"@+id/act_reset\"");
        int fork = xml.indexOf("android:id=\"@+id/act_fork\"");
        int children = xml.indexOf("android:id=\"@+id/act_children\"");
        assertTrue(copy >= 0);
        assertTrue("copy, reset, fork, then the child entry",
                copy < reset && reset < fork && fork < children);
        // 2026-10-25 (T-2026-10-25-101): the branch's time moved to the reading header, where it is
        // stated once; the row no longer ends with a second copy of it.
        assertFalse("no time view closes the row", xml.contains("android:id=\"@+id/act_time\""));
    }

    @Test
    public void theActionRowDropsTheExtrasTheUserMarkedOut() throws Exception {
        // 2026-09-28 user decision: the annotated shot crossed out the app's own extras and the
        // hidden version switcher ("删除不需要的按钮，比如那个定位、还有语音播放" / "其他的不要").
        // The row is exactly the four prototype buttons - nothing else may come back without a
        // fresh decision, which is why this asserts absence rather than a count.
        String xml = read(RES.resolve(Paths.get("layout", "view_answer_actions.xml")));
        for (String gone : new String[] {"act_quote", "act_translate", "act_speak", "act_version"}) {
            assertFalse(gone + " was removed from the row and must not reappear",
                    xml.contains("android:id=\"@+id/" + gone + "\""));
        }
        // Quote keeps a way in: the selection menu on the answer text. Translation keeps its card
        // header. Both are asserted where they live, so their absence here is not a dead feature.
        String screen = read(Paths.get("src", "main", "java", "com", "treelearn", "app", "screen",
                "ChatScreen.java"));
        assertTrue("the selection menu still offers quote",
                screen.contains("onQuotePressed(id, selection)"));
        assertTrue("the translation card header still offers a retry",
                screen.contains("actions.onTranslatePressed(messageId)"));
    }

    @Test
    public void everyActionKeepsItsFortyFourDpHotZone() throws Exception {
        String xml = read(RES.resolve(Paths.get("layout", "view_answer_actions.xml")));
        for (String id : new String[] {"act_copy", "act_reset", "act_fork", "act_children"}) {
            assertTrue(id + " must stay 44dp tall", elementAround(xml, id)
                    .contains("android:layout_height=\"@dimen/touch_min\""));
        }
    }

    @Test
    public void theChildEntryIsAGlyphWithACountBadgeNotALabel() throws Exception {
        // 2026-09-28 user decision, replacing the T-057 shape: "分支列表 对应的也是图标，不要是文字！"
        // The entry used to be a text Button reading "子分支 N". It is now what the prototype draws at
        // line 1178 - the ITree glyph with the count in a green badge (.ai-act-n). This test holds that
        // shape: a glyph, a badge, the number in the badge, and no text label anywhere in the entry.
        String xml = read(RES.resolve(Paths.get("layout", "view_answer_actions.xml")));
        String entry = elementWithBody(xml, "act_children");
        assertTrue("the child entry must draw the branch tree glyph (.ai-act ITree)",
                entry.contains("@drawable/ic_nav_tree"));
        assertTrue("its count must ride a badge child (.ai-act-n)",
                entry.contains("android:id=\"@+id/act_children_count\""));
        assertTrue("the badge must be the green pill",
                entry.contains("@drawable/bg_count_badge"));
        // The old shape must be gone: a Button that renders its own text, and the count string that
        // fed it. Either one coming back means the entry regressed to a label.
        assertTrue("the entry must not be a Button any more (that is what made it textual)",
                !entry.contains("<Button"));
        assertTrue("the entry must carry no android:text of its own",
                !entry.contains("android:text="));
        String strings = read(RES.resolve(Paths.get("values", "strings.xml")));
        assertFalse("the count label string was retired with the text shape",
                strings.contains("name=\"chat_act_children_count\""));
    }

    @Test
    public void theActionButtonsCarryNoInnerPadding() throws Exception {
        // 2026-09-28 user decision: the row read "现在隔的太开了". The surplus was the 6dp inner
        // padding each button stacked on its 4dp start margin, not the 44dp target. Both are gone.
        // 2026-10-25 (T-2026-10-25-101): the lead is a FrameLayout now, so it carries no padding at
        // all and the 18dp glyph centres itself inside the 44dp cell.
        String xml = read(RES.resolve(Paths.get("layout", "view_answer_actions.xml")));
        for (String id : new String[] {"act_copy", "act_reset", "act_fork"}) {
            String element = elementAround(xml, id);
            assertTrue(id + " must be exactly the 44dp target wide, so the glyph centres in it",
                    element.contains("android:layout_width=\"@dimen/touch_min\""));
            assertFalse(id + " must not pad its 44dp target", element.contains("android:padding"));
            assertFalse(id + " must not add its own start margin on top of the 44dp target",
                    element.contains("android:layout_marginStart="));
        }
        assertTrue("the glyph is centred by the cell, not pushed by padding",
                elementWithBody(xml, "act_copy")
                        .contains("android:layout_gravity=\"center\""));
        // Copy leads the row; nothing in the row carries a start margin any more.
        assertFalse("no action button may re-introduce a start margin",
                elementAround(xml, "act_copy").contains("android:layout_marginStart="));
    }

    @Test
    public void theActionRowCarriesNoVersionSwitcher() throws Exception {
        // 2026-09-28 user decision: the version switcher left the row with the rest of the extras.
        // The n/N face and its strings are asserted away here so a stray re-add is caught.
        String xml = read(RES.resolve(Paths.get("layout", "view_answer_actions.xml")));
        assertFalse("act_version left the row", xml.contains("act_version"));

        String screen = read(Paths.get("src", "main", "java", "com", "treelearn", "app", "screen",
                "AnswerActionBar.java"));
        assertFalse("the bar must not paint a version chip", screen.contains("chat_version_chip"));
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
        // 2026-10-22 Scheme A (T-056): .user-msg is ink-2, one step below the answer's ink, so the
        // question no longer shares the answer's exact colour. What this pins is that it stays a
        // text colour and never returns to the on-bubble surface colour.
        assertTrue("the question's own text is a text ink, not the on-bubble surface colour",
                question.contains("android:textColor=\"@color/tl_ink2\"")
                        && !question.contains("@color/tl_surface"));
    }

    @Test
    public void theAiTagIsThePlainBrandWithTheSpark() throws Exception {
        String xml = read(RES.resolve(Paths.get("layout", "item_chat_message.xml")));
        String tag = elementAround(xml, "chat_ai_tag");
        // 2026-10-22 Scheme A (T-056): .ai-tag draws the 13dp sprout in green with a letterspaced
        // label, replacing the older spark + green-deep pair. The contract is unchanged: a plain
        // brand mark, not a chip.
        assertTrue("the tag is the plain brand + sprout (.ai-tag), not a chip",
                tag.contains("@drawable/ic_brand_sprout") && !tag.contains("@drawable/bg_tag"));
        assertTrue("the brand reads green like the prototype",
                tag.contains("android:textColor=\"@color/tl_green\""));
        assertTrue("the sprout is tinted green too",
                tag.contains("android:drawableTint=\"@color/tl_green\""));
    }

    @Test
    public void tappingACodeBlockOffersCopy() throws Exception {
        String renderer = read(SRC.resolve(Paths.get("screen", "MarkdownRenderer.java")));
        assertTrue("the renderer must wire a click span over code blocks",
                renderer.contains("ClickableSpan") && renderer.contains("OnCodeBlockClick"));
        String screen = read(SRC.resolve(Paths.get("screen", "ChatScreen.java")));
        // 2026-09-28: the answer is rendered as segments now, because a table has to be a view to be
        // aligned. The code tap still forwards to the one selection-copy action.
        assertTrue("the screen must render the answer as segments",
                screen.contains("MarkdownRenderer.renderSegments("));
        assertTrue("the screen must forward the code tap to the selection-copy action",
                screen.contains("onSelectionCopyPressed(code)"));
    }

    @Test
    public void aTableIsRenderedAsATableAndNotAsText() throws Exception {
        String renderer = read(SRC.resolve(Paths.get("screen", "MarkdownRenderer.java")));
        assertTrue("the renderer must not draw box characters any more",
                !renderer.contains("'┌'") && !renderer.contains("'┼'"));

        String screen = read(SRC.resolve(Paths.get("screen", "ChatScreen.java")));
        assertTrue("a table must become a real TableLayout",
                screen.contains("TableLayout") && screen.contains("TableRow"));
        assertTrue("and it must scroll rather than squash a wide table",
                screen.contains("R.layout.item_chat_table"));

        // The table's own layouts must exist and be tables, not text with padding.
        assertTrue("the table host must be a TableLayout",
                read(RES.resolve(Paths.get("layout", "item_chat_table.xml")))
                        .contains("<TableLayout"));
        assertTrue("the row must be a TableRow",
                read(RES.resolve(Paths.get("layout", "item_chat_table_row.xml")))
                        .contains("<TableRow"));
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
        // 2026-10-22 Scheme A (T-056): the two-level card became one horizontal dock, so the send
        // is still the circular fab but the field sits on the dock's glass, not on a second strip.
        assertTrue("the send control is the circular fab in the layout too",
                elementAround(composer, "chat_send").contains("@drawable/bg_send_fab"));
        assertTrue("the input card is the dock glass",
                elementAround(composer, "chat_composer_card")
                        .contains("@drawable/bg_glass_dock"));
        String strings = read(RES.resolve(Paths.get("values", "strings.xml")));
        assertTrue("the hint names the branch, like the prototype placeholder",
                strings.contains("<string name=\"chat_draft_hint\">在当前分支继续追问…</string>"));
        assertTrue("the attach pill carries its label",
                strings.contains("<string name=\"chat_attach_label\">附件</string>"));
    }

    @Test
    public void theActionRowShowsThePrototypeSetAndNothingHidden() throws Exception {
        // 2026-09-28 user decision: the row is exactly copy, reset, fork, the child entry and the
        // time. Nothing rides along hidden any more - the version switcher that used to is gone.
        String actions = read(RES.resolve(Paths.get("layout", "view_answer_actions.xml")));
        for (String id : new String[] {"act_copy", "act_reset", "act_fork"}) {
            String element = elementAround(actions, id);
            assertFalse(id + " must be visible", element.contains("android:visibility=\"gone\""));
            assertTrue(id + " must expose a content description",
                    element.contains("android:contentDescription="));
        }
    }

    @Test
    public void theSelectionMenuSaysNewTopicAndEmptiesThePlatformToolbar() throws Exception {
        // 2026-09-27 (user decision): the operation that opens a branch from an answer is 新建主题, and
        // the three entrances - the selection menu, the answer action row and the recommendation strip's
        // fork zone - all name it the same way in their content descriptions, so a rename that misses
        // one is caught here. 2026-09-28 (user decision): the strip's fork zone is a glyph now, so it
        // carries no visible label; the string it used to print is gone and only the descriptions
        // remain, which is exactly why this test reads descriptions rather than faces.
        String strings = read(RES.resolve(Paths.get("values", "strings.xml")));
        assertTrue("the action row's face", strings.contains(
                "<string name=\"cd_act_fork\">新建主题</string>"));
        assertTrue("the action row's description", strings.contains(
                "<string name=\"chat_fork_title\">新建主题："));
        assertTrue("the recommendation strip's description", strings.contains(
                "<string name=\"chat_suggestion_fork_title\">直接用这个问题新建主题</string>"));
        assertFalse("no user-facing copy may still say 新建分支", strings.contains("新建分支"));
        assertFalse("the strip's fork zone shows no text label any more",
                strings.contains("chat_act_fork"));

        // The app owns the six primary cells (更多 is the width fallback behind them, not a menu); the
        // platform selection menu is emptied instead of used, which keeps 新建主题 out of the
        // platform's overflow sheet.
        String screen = read(SRC.resolve(Paths.get("screen", "ChatScreen.java")));
        assertTrue("the platform selection toolbar must be emptied", screen.contains("menu.clear()"));
        assertFalse("no action may be handed to the platform selection menu",
                screen.contains("menu.add(Menu.NONE, R.id.chat_selection"));
    }

    @Test
    public void selectionMenuProtectsTheLongPressUpBeforeEnablingOutsideDismiss() throws Exception {
        String popup = read(SRC.resolve(Paths.get("screen", "WordMenuPopup.java")));
        assertTrue("the primary popup must start with outside dismissal disabled",
                popup.contains("popup.setOutsideTouchable(false)"));
        assertTrue("outside dismissal must be enabled only after the popup is posted",
                popup.contains("enableOutsideTouchAfterOpening(popup)")
                        && popup.contains("answer.post(() ->"));
        assertTrue("more must stay inside the same selection lifecycle",
                popup.contains("morePopup.setOutsideTouchable(false)")
                        && popup.contains("enableOutsideTouchAfterOpening(morePopup)"));
    }

    @Test
    public void theSuggestionForkZoneIsAGlyphRatherThanATextButton() throws Exception {
        // 2026-09-28 user decision: "新建主题 需要换成图标 ... 不要是文字按钮". The zone used to be a
        // text Button; the prototype draws its own IFork there (line 1200), so the row must carry that
        // glyph and no text at all.
        String suggestion = read(RES.resolve(Paths.get("layout", "item_chat_suggestion.xml")));
        String fork = elementAround(suggestion, "suggestion_fork");
        assertTrue("the fork zone draws the fork glyph", fork.contains("@drawable/ic_action_fork"));
        assertTrue("the fork zone is an icon view, not a Button", fork.contains("<ImageView"));
        assertFalse("a glyph carries no android:text", fork.contains("android:text="));
        assertFalse("a glyph carries no textSize", fork.contains("android:textSize="));
        assertTrue("the zone keeps its accessibility name", fork.contains("android:contentDescription="));
        assertTrue("the whole row still asks the question", suggestion.contains("suggestion_text"));
    }
}
