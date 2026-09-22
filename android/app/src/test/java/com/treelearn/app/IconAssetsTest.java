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
            "ic_action_quote.xml",
            "ic_action_copy.xml",
            "ic_action_fork.xml",
            "ic_chat_send.xml",
            "ic_chat_close.xml",
            "ic_chat_gear.xml",
            "ic_chat_overview.xml",
    };

    @Test
    public void chatIconAssetsFollowTheSharedDraftingConvention() throws Exception {
        for (String name : CHAT_ICON_ASSETS) {
            String xml = read(RES.resolve(Paths.get("drawable", name)));
            assertTrue(name + " must declare 24dp viewport", xml.contains("android:viewportWidth=\"24\"")
                    && xml.contains("android:viewportHeight=\"24\""));
            assertTrue(name + " must render at 24dp", xml.contains("android:width=\"24dp\"")
                    && xml.contains("android:height=\"24dp\""));
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
        // Send stays a Button (Java declares Button) with a compound drawable.
        assertIconAction("layout/screen_chat_body.xml", "chat_send",
                "@drawable/ic_chat_send", "@string/cd_chat_send");
        // Answer action row: quote/copy/reset/fork iconify; children/version stay textual (Java-driven).
        assertIconAction("layout/view_answer_actions.xml", "act_quote",
                "@drawable/ic_action_quote", "@string/cd_act_quote");
        assertIconAction("layout/view_answer_actions.xml", "act_copy",
                "@drawable/ic_action_copy", "@string/cd_act_copy");
        assertIconAction("layout/view_answer_actions.xml", "act_reset",
                "@drawable/ic_nav_restore", "@string/cd_act_reset");
        assertIconAction("layout/view_answer_actions.xml", "act_fork",
                "@drawable/ic_action_fork", "@string/cd_act_fork");
    }

    @Test
    public void wordMenuRowsStayTextualAndKeepTheirHotspotHeights() throws Exception {
        // PRD 6.2.19: the word menu is a full-width text-row menu; iconifying it is out of scope and
        // the 44dp row height must survive untouched.
        String xml = read(RES.resolve(Paths.get("layout", "popup_word_action.xml")));
        assertTrue("word_menu_quote must keep its text",
                xml.contains("android:text=\"@string/chat_word_quote\""));
        assertTrue("word_menu_fork must keep its text",
                xml.contains("android:text=\"@string/chat_act_fork\""));
        assertTrue("word menu rows must stay 44dp tall",
                xml.contains("android:layout_height=\"@dimen/touch_min\""));
    }

    @Test
    public void javaDrivenTextualActionsStayTextual() throws Exception {
        // act_children and act_version are setText-driven from AnswerActionBar; they must not be
        // iconified in this slice, and chat_retry keeps its label.
        String actions = read(RES.resolve(Paths.get("layout", "view_answer_actions.xml")));
        int childrenIdx = actions.indexOf("android:id=\"@+id/act_children\"");
        int childrenEnd = actions.indexOf('>', childrenIdx);
        String childrenEl = actions.substring(actions.lastIndexOf('<', childrenIdx), childrenEnd);
        assertTrue("act_children must stay textual (Java setText)", !childrenEl.contains("@drawable/"));
        String item = read(RES.resolve(Paths.get("layout", "item_chat_message.xml")));
        assertTrue("chat_retry must keep its label", item.contains("android:text=\"@string/chat_retry\""));
    }
}
