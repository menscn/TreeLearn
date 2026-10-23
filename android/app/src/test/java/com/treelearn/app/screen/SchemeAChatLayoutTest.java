package com.treelearn.app.screen;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.Test;

/**
 * Structural pins for the 2026-10-22 Scheme A alignment of the conversation page
 * (T-2026-10-22-056).
 *
 * <p>The APK built after 049 carried the mist glass on the old chat structure, which is why the
 * conversation did not read as Scheme A on the device. These assertions therefore pin structure:
 * the capsule topbar, the 15dp reader panel, the Scheme A question/answer/action typography, the
 * glass dock composer, and the child-branch stack that replaced the popup.
 *
 * <p>2026-10-23 (T-066): the dock is now two lines inside the same glass card. The model chip and the
 * new persona chip moved to a tools line so the draft keeps the input line; the single-row assertion
 * was replaced rather than kept, because one row with both chips leaves the draft about 17dp on a
 * 360dp screen.
 *
 * <p>The popup files must be gone rather than merely unused: a second carrier left in the tree is
 * the next reader's invitation to wire it back in.
 */
public final class SchemeAChatLayoutTest {

    private static final Path RES = Paths.get("src", "main", "res").toAbsolutePath();
    private static final Path SRC = Paths.get("src", "main", "java", "com", "treelearn", "app")
            .toAbsolutePath();

    private static String read(Path path) throws Exception {
        assertTrue("missing file: " + path, Files.exists(path));
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    private static String layout(String name) throws Exception {
        return read(RES.resolve(Paths.get("layout", name)));
    }

    private static String drawable(String name) throws Exception {
        return read(RES.resolve(Paths.get("drawable", name)));
    }

    private static String values(String name) throws Exception {
        return read(RES.resolve(Paths.get("values", name)));
    }

    private static String elementAround(String xml, String id) {
        int idIndex = xml.indexOf("android:id=\"@+id/" + id + "\"");
        assertTrue("id " + id + " must exist", idIndex >= 0);
        int elementEnd = xml.indexOf('>', idIndex);
        assertTrue("malformed layout", elementEnd >= 0);
        return xml.substring(Math.max(0, xml.lastIndexOf('<', idIndex)), elementEnd);
    }

    private static String dimens() throws Exception {
        return values("dimens.xml");
    }

    private static void assertDimen(String name, String value) throws Exception {
        assertTrue(name + " must be " + value,
                dimens().contains("<dimen name=\"" + name + "\">" + value + "</dimen>"));
    }

    @Test
    public void theTopbarIsTheSchemeACapsuleWithoutATitleChevron() throws Exception {
        String chat = layout("screen_chat_body.xml");
        String bar = elementAround(chat, "chat_appbar");
        assertTrue("the bar draws the shared glass chrome",
                bar.contains("android:background=\"@drawable/bg_glass_bar\""));
        assertTrue("Scheme A's 52dp capsule", bar.contains(
                "android:layout_height=\"@dimen/topbar_height\""));
        assertTrue("6dp below the status bar", bar.contains(
                "android:layout_marginTop=\"@dimen/topbar_offset\""));
        assertTrue("16dp from each side",
                bar.contains("android:layout_marginStart=\"@dimen/space_s4\"")
                        && bar.contains("android:layout_marginEnd=\"@dimen/space_s4\""));
        assertTrue("8dp inner padding", bar.contains("android:padding=\"@dimen/space_s2\""));
        for (String id : new String[]{"chat_back", "chat_nav", "chat_overview", "chat_settings"}) {
            String icon = elementAround(chat, id);
            assertTrue(id + " is the 36dp capsule key",
                    icon.contains("android:layout_width=\"@dimen/topbar_icon\"")
                            && icon.contains("android:layout_height=\"@dimen/topbar_icon\""));
            assertTrue(id + " draws a 20dp glyph inside", icon.contains("android:padding=\"8dp\""));
        }
        assertTrue("the title is Scheme A's 13sp semibold line",
                chat.contains("android:textSize=\"@dimen/text_topbar_title\""));
        // 2026-10-22 parent decision: the proposal's title chevron has no real branch switcher
        // behind it, so the page must not grow a dead one. The follow-latest key legitimately draws
        // the same glyph, so the pin is the title's own element and not the whole file.
        String title = elementAround(chat, "chat_title");
        assertFalse("no title chevron",
                title.contains("android:drawableEnd=") || title.contains("android:drawableRight="));
        assertFalse("no title chevron node", chat.contains("chat_title_chevron"));
    }

    @Test
    public void theReaderIsTheGlassPanelWithSchemeAPadding() throws Exception {
        String chat = layout("screen_chat_body.xml");
        String reader = elementAround(chat, "chat_reader");
        assertTrue("the reader is the long-form glass surface",
                reader.contains("android:background=\"@drawable/bg_glass_reader\""));
        assertTrue("12dp column gutter",
                reader.contains("android:layout_marginStart=\"@dimen/chat_body_pad\"")
                        && reader.contains("android:layout_marginEnd=\"@dimen/chat_body_pad\"")
                        && reader.contains("android:layout_marginTop=\"@dimen/chat_body_pad\""));
        assertTrue("10dp above the dock",
                reader.contains("android:layout_marginBottom=\"@dimen/chat_gap\""));
        assertTrue("15dp inside the reader",
                chat.contains("android:paddingStart=\"@dimen/reader_pad\"")
                        && chat.contains("android:paddingEnd=\"@dimen/reader_pad\"")
                        && chat.contains("android:paddingTop=\"@dimen/reader_pad\""));
        assertTrue("the reader is a 28dp panel",
                drawable("bg_glass_reader.xml").contains("@dimen/radius_2xl"));
        assertDimen("reader_pad", "15dp");
        assertDimen("chat_body_pad", "12dp");
        assertDimen("chat_body_bottom", "22dp");
        assertDimen("chat_gap", "10dp");
        assertDimen("radius_2xl", "28dp");
    }

    @Test
    public void theQuestionAnswerAndBrandTagTakeTheSchemeAType() throws Exception {
        String row = layout("item_chat_message.xml");
        String who = elementAround(row, "chat_user_label");
        assertTrue("the learner is named above the question",
                who.contains("android:text=\"@string/chat_user_tag\""));
        assertTrue("9sp letterspaced label", who.contains(
                "android:textSize=\"@dimen/text_chat_who\"")
                && who.contains("android:letterSpacing=\".14\""));
        assertTrue("the question is 12.5sp",
                elementAround(row, "chat_question")
                        .contains("android:textSize=\"@dimen/text_chat_user\""));
        String tag = elementAround(row, "chat_ai_tag");
        assertTrue("the brand tag is the sprout", tag.contains("@drawable/ic_brand_sprout"));
        assertTrue("9.5sp green label", tag.contains("android:textSize=\"@dimen/text_ai_tag\"")
                && tag.contains("android:textColor=\"@color/tl_green\""));
        String answer = elementAround(row, "chat_answer");
        assertTrue("13.2sp answer", answer.contains("android:textSize=\"@dimen/text_answer\""));
        assertTrue("1.82 leading", answer.contains("android:lineSpacingMultiplier=\"1.82\""));
        assertTrue("every prose run carries the answer type",
                layout("view_chat_answer_text.xml")
                        .contains("android:textSize=\"@dimen/text_answer\""));
        assertDimen("text_chat_user", "12.5sp");
        assertDimen("text_chat_who", "9sp");
        assertDimen("text_ai_tag", "9.5sp");
        assertDimen("text_answer", "13.2sp");
        assertDimen("text_topbar_title", "13sp");
    }

    @Test
    public void theActionRowDrawsTheSchemeAGlassDotsInsideTheTouchTarget() throws Exception {
        String actions = layout("view_answer_actions.xml");
        for (String id : new String[]{"act_copy", "act_reset", "act_fork", "act_children"}) {
            String element = elementAround(actions, id);
            assertTrue(id + " paints the glass dot", element.contains(
                    "android:background=\"@drawable/bg_action_glass\""));
            assertTrue(id + " keeps the 44dp target",
                    element.contains("android:layout_width=\"@dimen/touch_min\"")
                            && element.contains("android:layout_height=\"@dimen/touch_min\""));
        }
        String glass = drawable("bg_action_glass.xml");
        assertTrue("a full-round dot", glass.contains("@dimen/radius_full"));
        assertTrue("token glass inside", glass.contains("@color/glass_chrome")
                && glass.contains("@color/glass_edge"));
        assertTrue("the dot is inset inside the 44dp target",
                glass.contains("@dimen/action_glass_inset"));
        assertDimen("action_glass_inset", "7.5dp");
    }

    @Test
    public void theComposerIsTheTwoLineDock() throws Exception {
        String chat = layout("screen_chat_body.xml");
        String composer = elementAround(chat, "chat_composer");
        assertTrue("the dock owns the column gutter",
                composer.contains("android:paddingStart=\"@dimen/chat_body_pad\"")
                        && composer.contains("android:paddingEnd=\"@dimen/chat_body_pad\""));
        assertTrue("22dp below the dock before the window edge",
                composer.contains("android:paddingBottom=\"@dimen/chat_body_bottom\""));
        String card = elementAround(chat, "chat_composer_card");
        assertTrue("the card is the dock glass", card.contains(
                "android:background=\"@drawable/bg_glass_dock\""));
        assertTrue("the card stacks the input line and the tools line",
                card.contains("android:orientation=\"vertical\""));
        assertTrue("Scheme A's 14/11/12/11 padding",
                card.contains("android:paddingStart=\"@dimen/dock_pad_start\"")
                        && card.contains("android:paddingTop=\"@dimen/dock_pad\"")
                        && card.contains("android:paddingEnd=\"@dimen/dock_pad_end\"")
                        && card.contains("android:paddingBottom=\"@dimen/dock_pad\""));
        assertTrue("the input line keeps its controls on one line",
                elementAround(chat, "chat_composer_row")
                        .contains("android:orientation=\"horizontal\""));
        assertTrue("the tools line is the second horizontal line",
                elementAround(chat, "chat_composer_tools")
                        .contains("android:orientation=\"horizontal\""));
        assertTrue("controls centre vertically inside each line",
                card.contains("android:gravity=\"center_vertical\""));
        assertTrue("the dock is 24dp round",
                drawable("bg_glass_dock.xml").contains("@dimen/dock_radius"));
        assertTrue("the attachment is a glyph key",
                elementAround(chat, "chat_attach").contains("android:src=\"@drawable/ic_chat_attach\""));
        assertTrue("the send key keeps its fab", elementAround(chat, "chat_send")
                .contains("@drawable/bg_send_fab"));
        assertTrue("the draft is 12.5sp",
                elementAround(chat, "chat_draft")
                        .contains("android:textSize=\"@dimen/text_chat_user\""));
        assertDimen("dock_radius", "24dp");
        assertDimen("dock_pad_start", "14dp");
        assertDimen("dock_pad", "11dp");
        assertDimen("dock_pad_end", "12dp");
    }

    @Test
    public void theChildBranchesAreAnInlineStackUnderTheAnswer() throws Exception {
        String row = layout("item_chat_message.xml");
        String section = elementAround(row, "chat_branch_section");
        assertTrue("the stack starts hidden", section.contains("android:visibility=\"gone\""));
        assertTrue("the label is Scheme A's sentence",
                row.contains("android:text=\"@string/chat_branch_continue\""));
        assertTrue("the label carries the fork glyph", row.contains("@drawable/ic_action_fork"));
        assertTrue("the rail is a 1dp line plus a 5dp dot",
                row.contains("@drawable/bg_branch_line")
                        && row.contains("@drawable/bg_branch_dot"));
        assertTrue("the rows land in their own list", row.contains(
                "android:id=\"@+id/chat_branch_list\""));
        String item = layout("item_child_branch.xml");
        assertTrue("each row is a 14dp glass card",
                item.contains("android:background=\"@drawable/bg_branch_item\""));
        assertTrue("a full row is the target", item.contains(
                "android:minHeight=\"@dimen/touch_min\""));
        assertFalse("no decorative chevron on an inline row", item.contains("ic_chevron_right"));
        String glass = drawable("bg_branch_item.xml");
        assertTrue("the row reads as glass", glass.contains("@color/glass_card")
                && glass.contains("@color/glass_edge")
                && glass.contains("@dimen/radius_branch_item"));
        String strings = values("strings.xml");
        assertTrue("the learner is named", strings.contains(
                "<string name=\"chat_user_tag\">你</string>"));
        assertTrue("the stack names itself", strings.contains(
                "<string name=\"chat_branch_continue\">从这条回答继续</string>"));
        assertDimen("radius_branch_item", "14dp");
        assertDimen("branch_item_pad_v", "9dp");
        assertDimen("branch_item_pad_h", "10dp");
        assertDimen("branch_item_gap", "6dp");
        assertDimen("branch_rail", "5dp");
        assertDimen("branch_line", "1dp");
    }

    @Test
    public void thePopupCarrierIsGoneAndTheInlineOneUsesRealChildren() throws Exception {
        String screen = read(SRC.resolve(Paths.get("screen", "ChatScreen.java")));
        assertFalse("the popup class must not be referenced", screen.contains("ChildBranchPopup"));
        assertFalse("no popup field survives", screen.contains("childrenPopup"));
        assertTrue("the screen binds the inline list",
                screen.contains("R.id.chat_branch_list"));
        assertTrue("a row draws the children it was handed",
                screen.contains("R.layout.item_child_branch")
                        && screen.contains("R.id.child_branch_title")
                        && screen.contains("listener.accept(child.branchId)"));
        String main = read(SRC.resolve(Paths.get("MainActivity.java")));
        assertTrue("the host still forwards the presenter read",
                main.contains("chatScreen.showChildren(messageId, children")
                        && main.contains("presenter::onChildBranchChosen"));
        assertFalse("the popup implementation is deleted", Files.exists(
                SRC.resolve(Paths.get("screen", "ChildBranchPopup.java"))));
        assertFalse("the popup layout is deleted", Files.exists(
                RES.resolve(Paths.get("layout", "popup_child_branches.xml"))));
        assertFalse("the popup placement test is deleted", Files.exists(
                Paths.get("src", "test", "java", "com", "treelearn", "app", "screen",
                        "ChildBranchPopupPlacementTest.java").toAbsolutePath()));
    }
}
