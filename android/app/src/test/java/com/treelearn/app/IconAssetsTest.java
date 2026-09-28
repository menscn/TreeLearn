package com.treelearn.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Pattern;

import org.junit.Test;

/**
 * Structural assertions for the icon-asset slice (T-056): every new vector asset
 * follows the shared drafting convention (24dp grid, 1.8 stroke, round caps, no
 * business token inside the asset), and every replaced text action keeps its
 * original id, its tint and its contentDescription in the layouts.
 *
 * These are plain file reads on purpose: the assertions must run on the JVM
 * (no instrumentation device) so they can gate every future icon addition the
 * same way.
 */
public class IconAssetsTest {

    /** Module-relative root so the test works regardless of where Gradle runs it. */
    private static final Path RES = Paths.get("src", "main", "res").toAbsolutePath();

    private static final String[] ICON_ASSETS = {
            "ic_nav_back.xml",
            "ic_nav_chat.xml",
            "ic_nav_tree.xml",
            "ic_nav_more.xml",
            "ic_nav_restore.xml",
    };

    private static String read(Path path) throws Exception {
        assertTrue("missing resource file: " + path, Files.exists(path));
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    @Test
    public void everyIconAssetFollowsTheSharedDraftingConvention() throws Exception {
        for (String name : ICON_ASSETS) {
            String xml = read(RES.resolve(Paths.get("drawable", name)));
            assertTrue(name + " must declare 24dp viewport", xml.contains("android:viewportWidth=\"24\"")
                    && xml.contains("android:viewportHeight=\"24\""));
            assertTrue(name + " must render at 24dp", xml.contains("android:width=\"24dp\"")
                    && xml.contains("android:height=\"24dp\""));
            // Stroke conventions only bind stroke-drawn icons; solid icons (dots) have no stroke.
            if (xml.contains("android:strokeColor")) {
                assertTrue(name + " must use the 1.8 stroke", xml.contains("android:strokeWidth=\"1.8\""));
                assertTrue(name + " must use round line caps", xml.contains("android:strokeLineCap=\"round\""));
            }
        }
    }

    @Test
    public void iconAssetsCarryNoBusinessTokenInsideTheAsset() throws Exception {
        Pattern businessToken = Pattern.compile("@color/tl_");
        for (String name : ICON_ASSETS) {
            String xml = read(RES.resolve(Paths.get("drawable", name)));
            assertTrue(name + " must not reference business colors; tinting belongs to the usage site",
                    !businessToken.matcher(xml).find());
        }
    }

    @Test
    public void replacedActionKeepsItsOriginalIdIconTintAndDescription() throws Exception {
        assertIconAction("layout/screen_navigation_body.xml", "navigation_back",
                "@drawable/ic_nav_back", "@string/cd_navigation_back");
        assertIconAction("layout/screen_navigation_body.xml", "navigation_enter",
                "@drawable/ic_nav_chat", "@string/cd_navigation_enter");
        assertIconAction("layout/screen_navigation_body.xml", "navigation_canvas",
                "@drawable/ic_nav_tree", "@string/cd_navigation_canvas");
        assertIconAction("layout/item_navigation_row.xml", "navigation_row_more",
                "@drawable/ic_nav_more", "@string/cd_navigation_row_more");
        assertIconAction("layout/item_navigation_row.xml", "navigation_row_restore",
                "@drawable/ic_nav_restore", "@string/cd_navigation_row_restore");
        assertIconAction("layout/screen_settings_body.xml", "settings_back",
                "@drawable/ic_nav_back", "@string/cd_settings_back");
        assertIconAction("layout/screen_library_body.xml", "library_back",
                "@drawable/ic_nav_back", "@string/cd_library_back");
    }

    /** The control must keep its id, reference the icon, tint through a token and stay described. */
    private void assertIconAction(String layoutPath, String id, String drawableRef, String cdRef)
            throws Exception {
        String xml = read(RES.resolve(layoutPath));
        int idIndex = xml.indexOf("android:id=\"@+id/" + id + "\"");
        assertTrue(layoutPath + " must keep the original id " + id, idIndex >= 0);
        // Scan the element that owns the id: from the id up to the next closing tag.
        int elementEnd = xml.indexOf('>', idIndex);
        assertTrue("malformed layout " + layoutPath, elementEnd >= 0);
        String element = xml.substring(Math.max(0, xml.lastIndexOf('<', idIndex)), elementEnd);
        assertTrue(id + " must reference " + drawableRef, element.contains(drawableRef));
        // ImageView usages tint the source; Button compound-drawable usages tint via drawableTint.
        assertTrue(id + " must tint through a token",
                element.contains("android:tint=\"@color/tl_")
                        || element.contains("android:drawableTint=\"@color/tl_"));
        assertTrue(id + " must carry a contentDescription", element.contains("android:contentDescription=\"" + cdRef + "\""));
    }

    @Test
    public void originalTextLabelsSurviveInTheStringTable() throws Exception {
        String strings = read(RES.resolve(Paths.get("values", "strings.xml")));
        // The slice replaces visual carriers only; the string keys must stay for other screens.
        for (String key : new String[] {
                "navigation_back", "navigation_enter", "navigation_canvas",
                "navigation_row_more", "navigation_restore", "settings_back"}) {
            assertTrue("string key " + key + " must survive", strings.contains("name=\"" + key + "\""));
        }
    }

    @Test
    public void iconAssetsKeepTheStatedElementCountOnScreen() throws Exception {
        assertEquals(ICON_ASSETS.length, 5);
    }

    // ---- T-057: chat, branch and action icons (same drafting convention as T-056) ----

    private static final String[] CHAT_ICON_ASSETS = {
            "ic_action_copy.xml",
            "ic_action_fork.xml",
            "ic_action_select_all.xml",
            "ic_chat_send.xml",
            "ic_chat_attach.xml",
            "ic_chat_close.xml",
            "ic_chat_gear.xml",
            "ic_chat_overview.xml",
    };

    @Test
    public void theRetiredActionGlyphsAreGoneFromTheTree() {
        // 2026-09-28 user decision: quote / translate / speak left the answer action row, and no
        // other screen picked their glyphs up - the selection menu draws 问 AI, 新建主题, 复制 and
        // 全选 from ic_sparkle / ic_action_fork / ic_action_copy / ic_action_select_all. An unused
        // vector is not free: it ships in the APK and it invites a later reader to think the action
        // still exists. Asserting the absence keeps the deletion from being quietly undone.
        for (String gone : new String[] {
                "ic_action_quote.xml", "ic_action_translate.xml", "ic_action_speak.xml"}) {
            assertTrue(gone + " must stay deleted (orphaned by the 2026-09-28 action-row trim)",
                    !Files.exists(RES.resolve(Paths.get("drawable", gone))));
        }
    }

    @Test
    public void chatIconAssetsFollowTheSharedDraftingConvention() throws Exception {
        for (String name : CHAT_ICON_ASSETS) {
            String xml = read(RES.resolve(Paths.get("drawable", name)));
            assertTrue(name + " must declare 24dp viewport", xml.contains("android:viewportWidth=\"24\"")
                    && xml.contains("android:viewportHeight=\"24\""));
            // ic_chat_attach draws at 15dp to match the model chip's own glyph in the composer row
            // (2026-09-28 user decision); the rest render at the 24dp drafting size.
            String expected = "ic_chat_attach.xml".equals(name) ? "15dp" : "24dp";
            assertTrue(name + " must render at " + expected, xml.contains("android:width=\"" + expected + "\"")
                    && xml.contains("android:height=\"" + expected + "\""));
            if (xml.contains("android:strokeColor")) {
                assertTrue(name + " must use the 1.8 stroke", xml.contains("android:strokeWidth=\"1.8\""));
                assertTrue(name + " must use round line caps", xml.contains("android:strokeLineCap=\"round\""));
            }
            assertTrue(name + " must not reference business colors", !xml.contains("@color/tl_"));
        }
    }

    @Test
    public void chatReplacedActionKeepsItsOriginalIdIconTintAndDescription() throws Exception {
        // App bar: back reuses the nav back chevron; nav reuses the branch tree; overview and
        // settings get their own glyphs; the quote-row clear gets a close glyph.
        assertIconAction("layout/screen_chat_body.xml", "chat_back",
                "@drawable/ic_nav_back", "@string/cd_chat_back");
        assertIconAction("layout/screen_chat_body.xml", "chat_nav",
                "@drawable/ic_nav_tree", "@string/cd_chat_nav");
        assertIconAction("layout/screen_chat_body.xml", "chat_overview",
                "@drawable/ic_chat_overview", "@string/cd_chat_overview");
        assertIconAction("layout/screen_chat_body.xml", "chat_settings",
                "@drawable/ic_chat_gear", "@string/cd_chat_settings");
        assertIconAction("layout/screen_chat_body.xml", "chat_quote_clear",
                "@drawable/ic_chat_close", "@string/cd_chat_quote_clear");
        assertIconAction("layout/screen_chat_body.xml", "chat_attach",
                "@drawable/ic_chat_attach", "@string/cd_chat_attach");
        // Send stays a Button (Java declares Button) with a compound drawable.
        assertIconAction("layout/screen_chat_body.xml", "chat_send",
                "@drawable/ic_chat_send", "@string/cd_chat_send");
        // Answer action row: copy/reset/fork iconify; children stays textual (Java-driven). 2026-09-28
        // user decision: quote/translate/speak left the row, so act_quote is no longer asserted here.
        assertIconAction("layout/view_answer_actions.xml", "act_copy",
                "@drawable/ic_action_copy", "@string/cd_act_copy");
        assertIconAction("layout/view_answer_actions.xml", "act_reset",
                "@drawable/ic_nav_restore", "@string/cd_act_reset");
        assertIconAction("layout/view_answer_actions.xml", "act_fork",
                "@drawable/ic_action_fork", "@string/cd_act_fork");
        // 2026-09-28 user decision: the recommendation strip's fork zone is a glyph too, drawn with the
        // very same asset the action row uses - one glyph, one meaning. It used to be a text Button.
        assertIconAction("layout/item_chat_suggestion.xml", "suggestion_fork",
                "@drawable/ic_action_fork", "@string/chat_suggestion_fork_title");
    }

    @Test
    public void wordMenuIsFourIconCellsInOneRowAndEachKeepsItsHotspotAndDescription() throws Exception {
        // 2026-09-28 user decision, which replaces the T-056 shape: the menu is 问 AI, 新建主题, 复制
        // and 全选 as four icon cells in one row - no text label, no captions, no selection label. The
        // name of an item is therefore not in the layout as text; it must survive as the cell's
        // contentDescription, or the menu becomes unusable to a screen reader. What this test holds is
        // that contract, not the old textual shape: it asserts the row, the four cells, their glyphs,
        // their descriptions and the 44dp target each one is still drawn at.
        String xml = read(RES.resolve(Paths.get("layout", "popup_word_action.xml")));
        assertTrue("the menu must be a single horizontal row",
                xml.contains("android:orientation=\"horizontal\""));
        String[] cells = {"word_menu_ask_ai", "word_menu_fork", "word_menu_copy",
                "word_menu_select_all"};
        String[] glyphs = {"@drawable/ic_sparkle", "@drawable/ic_action_fork",
                "@drawable/ic_action_copy", "@drawable/ic_action_select_all"};
        String[] descriptions = {"@string/chat_word_ask_ai_desc", "@string/chat_word_fork_desc",
                "@string/chat_word_copy_desc", "@string/chat_word_select_all_desc"};
        for (int i = 0; i < cells.length; i++) {
            String cell = elementFor(xml, "@+id/" + cells[i]);
            assertTrue(cells[i] + " must draw its glyph", cell.contains(glyphs[i]));
            assertTrue(cells[i] + " must carry its content description",
                    cell.contains("android:contentDescription=\"" + descriptions[i] + "\""));
            assertTrue(cells[i] + " must stay a 44dp target",
                    cell.contains("android:layout_height=\"@dimen/touch_min\""));
            assertTrue(cells[i] + " must not fall back to a text label",
                    !cell.contains("android:text="));
        }
    }

    /**
     * One element and everything inside it, so an assertion can be scoped to the cell it names rather
     * than to the whole file - a file-wide `contains` would let a description or a glyph drift onto a
     * different cell.
     *
     * <p>The slice runs to the element's matching close tag, counted by depth, rather than to the
     * first `>`: it has to include the body, because the glyph lives in a child ImageView and a
     * tag-only slice would never see it. Comments are stripped first so a commented-out element can
     * never satisfy an assertion.
     */
    private static String elementFor(String xml, String id) {
        String body = xml.replaceAll("(?s)<!--.*?-->", "");
        int start = body.indexOf("android:id=\"" + id + "\"");
        assertTrue("missing element: " + id, start >= 0);
        int open = body.lastIndexOf('<', start);
        int openEnd = body.indexOf('>', start);
        if (body.charAt(openEnd - 1) == '/') {
            return body.substring(open, openEnd + 1);
        }
        int nameEnd = open + 1;
        while (nameEnd < openEnd && !Character.isWhitespace(body.charAt(nameEnd))) {
            nameEnd++;
        }
        String tag = body.substring(open + 1, nameEnd);
        Pattern openTag = Pattern.compile("<" + Pattern.quote(tag) + "(?=[\\s/>])");
        Pattern closeTag = Pattern.compile("</" + Pattern.quote(tag) + "\\s*>");
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
    public void javaDrivenTextualActionsStayTextual() throws Exception {
        // 2026-09-28 user decision reversed this test's old claim about act_children: the child entry
        // used to be setText-driven and therefore had to stay textual; it is now a glyph with a count
        // badge ("分支列表 对应的也是图标，不要是文字！"), so it belongs to the icon slice, not here.
        // What this test still holds is that a genuinely textual action stays textual: chat_retry
        // keeps its label. If a future slice iconifies it, that is a decision, and this must be
        // rewritten rather than deleted.
        String item = read(RES.resolve(Paths.get("layout", "item_chat_message.xml")));
        assertTrue("chat_retry must keep its label", item.contains("android:text=\"@string/chat_retry\""));
    }

    @Test
    public void theChildEntryDrawsAGlyphAndItsBadgeUsesTheBrandGreen() throws Exception {
        // The child entry joined the icon slice on 2026-09-28. Its glyph is ic_nav_tree (the
        // prototype's own ITree, which the app bar's nav entry also draws), and its count badge is a
        // solid pill in the same token the prototype's .ai-act-n uses. This is asserted here - beside
        // the other chat glyphs - because a badge whose fill drifts off the token stops reading as a
        // count and starts reading as a second colour on the row.
        String actions = read(RES.resolve(Paths.get("layout", "view_answer_actions.xml")));
        assertTrue("the child entry must draw the branch tree glyph",
                actions.contains("android:src=\"@drawable/ic_nav_tree\"")
                        && actions.contains("android:id=\"@+id/act_children_count\""));
        String badge = read(RES.resolve(Paths.get("drawable", "bg_count_badge.xml")));
        assertTrue("the badge must fill with the brand green token (prototype .ai-act-n background)",
                badge.contains("@color/tl_green"));
        assertTrue("the badge must be a pill, so a two-digit count still reads",
                badge.contains("<corners"));
    }
}
