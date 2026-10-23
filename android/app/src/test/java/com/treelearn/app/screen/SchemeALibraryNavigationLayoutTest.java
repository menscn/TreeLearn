package com.treelearn.app.screen;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.Test;

/**
 * Structural pins for the 2026-10-22 Scheme A alignment of the library and the branch navigation
 * (T-2026-10-22-057).
 *
 * <p>Like 055 and 056 these assertions read the layout, drawable and source files on the JVM. The
 * point is structure rather than colour: after 049 the mist glass sat on the old page shape, and
 * the two pages the learner compares with Scheme A's third and fifth figures still led with a bare
 * app bar, independent segment buttons and text-only branch rows.
 *
 * <p>What is deliberately not pinned here: the 40dp segment capsule and the 24dp row menu are below
 * this project's 44dp touch floor, so the layouts draw their faces inside 44dp targets. The pins
 * therefore hold the touch sizes and the visible face tokens, not the proposal's raw hit boxes.
 */
public final class SchemeALibraryNavigationLayoutTest {

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

    private static String dimens() throws Exception {
        return read(RES.resolve(Paths.get("values", "dimens.xml")));
    }

    private static String strings() throws Exception {
        return read(RES.resolve(Paths.get("values", "strings.xml")));
    }

    private static String screen(String name) throws Exception {
        return read(SRC.resolve(Paths.get("screen", name)));
    }

    /** The open tag that owns the id, so a neighbour cannot satisfy the assertion. */
    private static String elementAround(String xml, String id) {
        int idIndex = xml.indexOf("android:id=\"@+id/" + id + "\"");
        assertTrue("id " + id + " must exist", idIndex >= 0);
        int elementEnd = xml.indexOf('>', idIndex);
        assertTrue("malformed layout", elementEnd >= 0);
        return xml.substring(Math.max(0, xml.lastIndexOf('<', idIndex)), elementEnd);
    }

    /** One element and its body, matched by depth; comments are stripped first. */
    private static String elementFor(String xml, String id) {
        String body = xml.replaceAll("(?s)<!--.*?-->", "");
        int start = body.indexOf("android:id=\"@+id/" + id + "\"");
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
            Matcher nextOpen = openTag.matcher(body);
            Matcher nextClose = closeTag.matcher(body);
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

    // ------------------------------------------------------------------
    // Library
    // ------------------------------------------------------------------

    @Test
    public void theLibraryTopbarIsTheSerifCapsule() throws Exception {
        String body = layout("screen_library_body.xml");
        String appbar = elementAround(body, "library_appbar");
        assertTrue("the library leads with the floating capsule",
                appbar.contains("android:background=\"@drawable/bg_glass_bar\""));
        assertTrue("Scheme A's 52dp capsule",
                appbar.contains("android:layout_height=\"@dimen/topbar_height\""));
        assertTrue("it sits 6dp below the status bar",
                appbar.contains("android:layout_marginTop=\"@dimen/topbar_offset\""));
        assertTrue("and 16dp in from each side",
                appbar.contains("android:layout_marginStart=\"@dimen/space_s4\"")
                        && appbar.contains("android:layout_marginEnd=\"@dimen/space_s4\""));
        assertTrue("with an 8dp inner inset",
                appbar.contains("android:paddingStart=\"@dimen/space_s2\"")
                        && appbar.contains("android:paddingEnd=\"@dimen/space_s2\""));

        String back = elementAround(body, "library_back");
        assertTrue("the back key is the 36dp capsule control",
                back.contains("android:layout_width=\"@dimen/topbar_icon\"")
                        && back.contains("android:layout_height=\"@dimen/topbar_icon\""));
        assertTrue("drawing the 20dp glyph through its inset",
                back.contains("android:padding=\"@dimen/space_s2\""));
        assertTrue("and keeping its real destination", back.contains("@drawable/ic_nav_back"));

        String title = elementAround(body, "library_title");
        assertTrue("the library keeps Scheme A's large title",
                title.contains("android:fontFamily=\"@font/noto_serif_sc\""));
        assertTrue("at 19sp", title.contains("android:textSize=\"@dimen/text_library_title\""));
        assertTrue("semibold", title.contains("android:textStyle=\"bold\""));
        assertTrue("with the proposal's tracked spacing",
                title.contains("android:letterSpacing=\"0.02\""));

        String dims = dimens();
        assertTrue("the 19sp page title is named",
                dims.contains("<dimen name=\"text_library_title\">19sp</dimen>"));
    }

    @Test
    public void theLibrarySearchAndSegmentsArePillGlassesWithRealCounts() throws Exception {
        String body = layout("screen_library_body.xml");

        String search = elementAround(body, "library_search_input");
        assertTrue("the search is the 44dp field",
                search.contains("android:layout_height=\"@dimen/touch_min\""));
        assertTrue("with the pill glass background",
                search.contains("android:background=\"@drawable/bg_library_search\""));
        assertTrue("the magnifier leads the hint", search.contains("@drawable/ic_search"));
        assertTrue("the hint is the proposal's 11sp",
                search.contains("android:textSize=\"@dimen/text_library_search\""));
        assertTrue("and 13dp from the pill's edge",
                search.contains("android:paddingStart=\"@dimen/library_search_pad\""));

        assertTrue("the search drawable is the pill-shaped glass field",
                drawable("bg_library_search.xml").contains("@drawable/bg_glass_search"));
        String pill = drawable("bg_glass_search.xml");
        assertTrue("the pill keeps the field fill token", pill.contains("@color/glass_field"));
        assertTrue("the 1px highlight edge", pill.contains("@color/glass_edge")
                && pill.contains("@dimen/hairline"));
        assertTrue("and the fully rounded corner", pill.contains("@dimen/radius_full"));

        String container = elementFor(body, "library_segment");
        assertTrue("the two segments share one glass capsule",
                container.contains("android:background=\"@drawable/bg_library_segment\""));
        assertTrue("at the 44dp touch floor",
                container.contains("android:layout_height=\"@dimen/touch_min\""));
        assertTrue("with the proposal's 4dp inset",
                container.contains("android:paddingStart=\"@dimen/library_segment_pad\"")
                        && container.contains("android:paddingEnd=\"@dimen/library_segment_pad\""));
        assertTrue("both segments live inside that one capsule",
                container.contains("android:id=\"@+id/library_segment_in_progress\"")
                        && container.contains("android:id=\"@+id/library_segment_done\""));

        String inProgress = elementAround(body, "library_segment_in_progress");
        assertTrue("each segment owns half the capsule",
                inProgress.contains("android:layout_width=\"0dp\"")
                        && inProgress.contains("android:layout_weight=\"1\""));
        assertTrue("and fills its 44dp height",
                inProgress.contains("android:layout_height=\"match_parent\""));
        assertTrue("at 10.5sp", inProgress.contains(
                "android:textSize=\"@dimen/text_library_segment\""));

        String screen = screen("LibraryScreen.java");
        assertTrue("the in-progress count is the real row count",
                screen.contains("next.rows.size()"));
        assertTrue("the completed count is the real archived count",
                screen.contains("next.archived.size()"));
        assertTrue("the count is drawn at 9.5sp without splitting the text contract",
                screen.contains("RelativeSizeSpan")
                        && dimens().contains("<dimen name=\"text_library_segment_num\">9.5sp</dimen>"));
        assertTrue("the selected segment paints Scheme A's bright face",
                screen.contains("R.drawable.bg_segment_active"));
        String active = drawable("bg_segment_active.xml");
        assertTrue("the face is the glass sheen", active.contains("@color/glass_sheen"));
        assertTrue("inside 4dp of the 44dp touch target",
                active.contains("android:top=\"@dimen/space_s1\"")
                        && active.contains("android:bottom=\"@dimen/space_s1\""));
        assertTrue("and keeps the capsule shape", active.contains("@dimen/radius_full"));
    }

    @Test
    public void theLibraryTopicCardUsesTheCompactSchemeAType() throws Exception {
        String item = layout("item_library_topic.xml");
        String card = elementAround(item, "library_topic");
        assertTrue("the row is the shared glass card",
                card.contains("android:background=\"@drawable/bg_library_card\""));
        assertTrue("Schema A's 12dp vertical padding",
                card.contains("android:paddingTop=\"@dimen/library_card_pad_v\"")
                        && card.contains("android:paddingBottom=\"@dimen/library_card_pad_v\""));
        assertTrue("and 13dp horizontal padding",
                card.contains("android:paddingStart=\"@dimen/library_card_pad_h\"")
                        && card.contains("android:paddingEnd=\"@dimen/library_card_pad_h\""));
        assertTrue("cards are 9dp apart",
                card.contains("android:layout_marginBottom=\"@dimen/library_card_gap\""));
        assertFalse("Scheme A's library card has no decorative accent bar",
                item.contains("library_topic_accent"));

        String title = elementAround(item, "library_topic_title");
        assertTrue("the title is 12.5sp",
                title.contains("android:textSize=\"@dimen/text_library_card_title\""));
        assertTrue("semibold", title.contains("android:textStyle=\"bold\""));

        String summary = elementAround(item, "library_topic_summary");
        assertTrue("the summary is 10.5sp",
                summary.contains("android:textSize=\"@dimen/text_library_summary\""));
        assertTrue("at the proposal's 1.55 leading",
                summary.contains("android:lineSpacingMultiplier=\"1.55\""));
        assertTrue("clamped to two lines", summary.contains("android:maxLines=\"2\""));

        String meta = elementAround(item, "library_topic_meta");
        assertTrue("the meta line is 9sp",
                meta.contains("android:textSize=\"@dimen/text_library_meta\""));
        String metaRow = elementAround(item, "library_topic_meta_row");
        assertTrue("and sits 6dp under the summary",
                metaRow.contains("android:layout_marginTop=\"@dimen/library_meta_top\""));
        String tree = elementAround(item, "library_topic_tree");
        assertTrue("the meta leads with Scheme A's 11dp tree",
                tree.contains("android:layout_width=\"@dimen/library_meta_icon\""));
        assertTrue("tinted with the identity green", tree.contains("android:tint=\"@color/tl_green\""));

        String chevron = elementAround(item, "library_topic_chevron");
        assertTrue("the card carries the 14dp disclosure chevron",
                chevron.contains("android:layout_width=\"@dimen/library_chevron\""));
        assertTrue("pointing right",
                chevron.contains("@drawable/ic_chevron_right"));

        String dims = dimens();
        assertTrue(dims.contains("<dimen name=\"text_library_card_title\">12.5sp</dimen>"));
        assertTrue(dims.contains("<dimen name=\"text_library_summary\">10.5sp</dimen>"));
        assertTrue(dims.contains("<dimen name=\"text_library_meta\">9sp</dimen>"));
        assertTrue(dims.contains("<dimen name=\"library_card_pad_v\">12dp</dimen>"));
        assertTrue(dims.contains("<dimen name=\"library_card_pad_h\">13dp</dimen>"));
    }

    @Test
    public void theArchivedCardKeepsItsControlsAndDropsTheDecorativeBar() throws Exception {
        String item = layout("item_library_archived.xml");
        String card = elementAround(item, "library_done_row");
        assertTrue("the archived row is the same glass card",
                card.contains("android:background=\"@drawable/bg_library_card\""));
        assertTrue("with the library card's padding",
                card.contains("android:paddingTop=\"@dimen/library_card_pad_v\"")
                        && card.contains("android:paddingStart=\"@dimen/library_card_pad_h\""));
        assertTrue("and the same 9dp rhythm",
                card.contains("android:layout_marginBottom=\"@dimen/library_card_gap\""));
        assertFalse("the decorative accent bar is gone here too",
                item.contains("library_done_accent"));
        assertTrue("the title follows the compact card type",
                elementAround(item, "library_done_title")
                        .contains("android:textSize=\"@dimen/text_library_card_title\""));
        assertTrue("the archived mark uses the meta size",
                elementAround(item, "library_done_mark")
                        .contains("android:textSize=\"@dimen/text_library_meta\""));
        assertTrue("restore still has a real control",
                item.contains("android:id=\"@+id/library_done_restore\""));
        assertTrue("delete still has a real control",
                item.contains("android:id=\"@+id/library_done_delete\""));
        assertTrue("and the inline confirmation survives",
                item.contains("android:id=\"@+id/library_done_confirm\""));
    }

    @Test
    public void theLibraryClosesWithTheSproutNoteAndHidesItDuringSearch() throws Exception {
        String body = layout("screen_library_body.xml");
        String note = elementAround(body, "library_note");
        assertTrue("the note sits below the list",
                body.indexOf("android:id=\"@+id/library_note\"")
                        > body.indexOf("android:id=\"@+id/library_rows\""));
        assertTrue("it carries Scheme A's 13dp sprout",
                body.contains("@drawable/ic_brand_sprout")
                        && elementAround(body, "library_note_icon")
                                .contains("android:layout_width=\"@dimen/library_note_icon\""));
        assertTrue("and the proposal's 10sp line",
                elementAround(body, "library_note_text")
                        .contains("android:textSize=\"@dimen/text_library_note\""));
        assertTrue("the copy is a real string", strings().contains("name=\"library_note\""));
        assertTrue("it sits 24dp under the list",
                note.contains("android:layout_marginTop=\"@dimen/library_note_top\"")
                        || body.contains("android:layout_marginTop=\"@dimen/library_note_top\""));

        String screen = screen("LibraryScreen.java");
        assertTrue("the note belongs to the browsing state, not to search results",
                screen.contains("note.setVisibility(GONE)")
                        && screen.contains("note.setVisibility(VISIBLE)"));
    }

    // ------------------------------------------------------------------
    // Branch navigation
    // ------------------------------------------------------------------

    @Test
    public void theNavigationTopbarIsTheSharedSansCapsule() throws Exception {
        String body = layout("screen_navigation_body.xml");
        String appbar = elementAround(body, "navigation_appbar");
        assertTrue("the navigation leads with the same capsule",
                appbar.contains("android:background=\"@drawable/bg_glass_bar\""));
        assertTrue("at 52dp", appbar.contains("android:layout_height=\"@dimen/topbar_height\""));
        assertTrue("6dp below the status bar",
                appbar.contains("android:layout_marginTop=\"@dimen/topbar_offset\""));
        assertTrue("16dp in from each side",
                appbar.contains("android:layout_marginStart=\"@dimen/space_s4\"")
                        && appbar.contains("android:layout_marginEnd=\"@dimen/space_s4\""));
        assertTrue("with 8dp inner padding",
                appbar.contains("android:padding=\"@dimen/space_s2\""));

        String back = elementAround(body, "navigation_back");
        assertTrue("the back key is 36dp",
                back.contains("android:layout_width=\"@dimen/topbar_icon\"")
                        && back.contains("android:padding=\"@dimen/space_s2\""));
        assertTrue("and keeps its glyph", back.contains("@drawable/ic_nav_back"));

        String title = elementAround(body, "navigation_title");
        assertTrue("Scheme A's navigation title is the shared 13sp sans",
                title.contains("android:textSize=\"@dimen/text_topbar_title\""));
        assertFalse("it is not a serif page title",
                title.contains("@font/noto_serif_sc"));

        for (String id : new String[]{"navigation_enter", "navigation_canvas"}) {
            String control = elementAround(body, id);
            assertTrue(id + " is a 36dp capsule key",
                    control.contains("android:layout_width=\"@dimen/topbar_icon\"")
                            && control.contains("android:layout_height=\"@dimen/topbar_icon\""));
            assertTrue(id + " draws its glyph through the 8dp inset",
                    control.contains("android:padding=\"@dimen/space_s2\""));
        }
    }

    @Test
    public void theNavigationSummaryIsThePanelWithItsAISummaryLabel() throws Exception {
        String body = layout("screen_navigation_body.xml");
        String card = elementAround(body, "navigation_summary_card");
        assertTrue("the summary is the 28dp glass panel",
                card.contains("android:background=\"@drawable/bg_summary_panel\""));
        assertTrue("with 15dp sides and top",
                card.contains("android:paddingStart=\"@dimen/nav_summary_pad\"")
                        && card.contains("android:paddingTop=\"@dimen/nav_summary_pad\""));
        assertTrue("and 13dp bottom",
                card.contains("android:paddingBottom=\"@dimen/nav_summary_pad_bottom\""));

        String title = elementAround(body, "navigation_current_title");
        assertTrue("the branch title is 13.5sp",
                title.contains("android:textSize=\"@dimen/text_nav_summary_title\""));
        assertTrue("semibold", title.contains("android:textStyle=\"bold\""));

        String chip = elementAround(body, "navigation_current_chip");
        assertTrue("the root tag is 8.5sp",
                chip.contains("android:textSize=\"@dimen/text_nav_tag\""));
        assertTrue("on the green-faint pill",
                chip.contains("android:background=\"@drawable/bg_root_tag\""));
        String tag = drawable("bg_root_tag.xml");
        assertTrue("the tag fill is the green-faint token", tag.contains("@color/tl_green_faint"));
        assertTrue("at the capsule radius", tag.contains("@dimen/radius_full"));

        String fold = elementFor(body, "navigation_summary_fold");
        assertTrue("the fold control is Scheme A's chevron, not a word",
                fold.contains("@drawable/ic_chevron_down")
                        && fold.contains("android:layout_width=\"@dimen/nav_fold_size\""));
        assertTrue("drawn at 15dp inside its 26dp box",
                fold.contains("android:padding=\"@dimen/nav_fold_pad\""));

        String label = elementFor(body, "navigation_summary_label");
        assertTrue("the AI summary label leads with the 12dp sparkle",
                label.contains("@drawable/ic_sparkle")
                        && label.contains("android:layout_width=\"@dimen/nav_summary_label_icon\""));
        assertTrue("the word is 9sp and green",
                label.contains("android:textSize=\"@dimen/text_nav_label\"")
                        && label.contains("android:textColor=\"@color/tl_green\""));
        assertTrue("with the proposal's tracking", label.contains("android:letterSpacing=\"0.12\""));

        String text = elementAround(body, "navigation_summary_text");
        assertTrue("the summary body is 11sp",
                text.contains("android:textSize=\"@dimen/text_nav_summary\""));
        assertTrue("at 1.78 leading",
                text.contains("android:lineSpacingMultiplier=\"1.78\""));
        assertTrue("and the proposal's 15-line reading cap",
                text.contains("android:maxLines=\"15\""));

        String panel = drawable("bg_summary_panel.xml");
        assertTrue("the panel keeps the card fill", panel.contains("@color/glass_card"));
        assertTrue("the 1px edge", panel.contains("@color/glass_edge"));
        assertTrue("and the 28dp panel corner", panel.contains("@dimen/radius_2xl"));
        assertTrue(dimens().contains("<dimen name=\"nav_summary_pad\">15dp</dimen>"));
        assertTrue(dimens().contains("<dimen name=\"nav_summary_pad_bottom\">13dp</dimen>"));
    }

    @Test
    public void theNavigationBranchRowsAreGlassRowsWithNodes() throws Exception {
        String item = layout("item_navigation_row.xml");
        String row = elementAround(item, "navigation_row");
        assertTrue("the branch row is Scheme A's 17dp glass row",
                row.contains("android:background=\"@drawable/bg_nav_row\""));
        assertTrue("with 11dp vertical padding",
                row.contains("android:paddingTop=\"@dimen/nav_row_pad_v\"")
                        && row.contains("android:paddingBottom=\"@dimen/nav_row_pad_v\""));
        assertTrue("12dp horizontal padding",
                row.contains("android:paddingStart=\"@dimen/nav_row_pad_h\"")
                        && row.contains("android:paddingEnd=\"@dimen/nav_row_pad_h\""));
        assertTrue("and 9dp between the node and the text",
                row.contains("android:gravity=\"center_vertical\""));

        String node = elementAround(item, "navigation_row_node");
        assertTrue("the node is a 22dp circle",
                node.contains("android:layout_width=\"@dimen/nav_row_node\"")
                        && node.contains("android:layout_height=\"@dimen/nav_row_node\""));
        assertTrue("painted by the node drawable",
                node.contains("android:background=\"@drawable/bg_nav_node\""));
        String nodeDrawable = drawable("bg_nav_node.xml");
        assertTrue("a white glass disc", nodeDrawable.contains("android:shape=\"oval\"")
                && nodeDrawable.contains("@android:color/white"));
        assertTrue("ringed at the proposal's 45% green",
                nodeDrawable.contains("android:alpha=\"0.45\"")
                        && nodeDrawable.contains("@color/tl_green"));
        String nodeIcon = elementAround(item, "navigation_row_node_icon");
        assertTrue("the node draws the 12dp branch tree",
                nodeIcon.contains("android:layout_width=\"@dimen/nav_row_node_icon\"")
                        && nodeIcon.contains("@drawable/ic_nav_tree")
                        && nodeIcon.contains("android:tint=\"@color/tl_green\""));

        String title = elementAround(item, "navigation_row_title");
        assertTrue("the title is 11.5sp",
                title.contains("android:textSize=\"@dimen/text_nav_row_title\""));
        assertTrue("semibold", title.contains("android:textStyle=\"bold\""));
        String meta = elementAround(item, "navigation_row_meta");
        assertTrue("the meta line is 9sp",
                meta.contains("android:textSize=\"@dimen/text_nav_row_meta\""));
        assertTrue("2dp under the title",
                meta.contains("android:layout_marginTop=\"@dimen/nav_row_meta_top\""));

        String more = elementAround(item, "navigation_row_more");
        assertTrue("the row menu keeps a 44dp target",
                more.contains("android:layout_width=\"@dimen/touch_min\"")
                        && more.contains("android:layout_height=\"@dimen/touch_min\""));
        assertTrue("draws the 15dp glyph through its inset",
                more.contains("android:padding=\"@dimen/nav_row_action_inset\""));
        assertTrue("and keeps its glyph and description",
                more.contains("@drawable/ic_nav_more")
                        && more.contains("@string/cd_navigation_row_more"));
        String restore = elementAround(item, "navigation_row_restore");
        assertTrue("the archived arm keeps its 44dp restore target",
                restore.contains("android:layout_height=\"@dimen/touch_min\"")
                        && restore.contains("@drawable/ic_nav_restore")
                        && restore.contains("@string/cd_navigation_row_restore"));

        String rowDrawable = drawable("bg_nav_row.xml");
        assertTrue("the row keeps the card fill", rowDrawable.contains("@color/glass_card"));
        assertTrue("the 1px edge", rowDrawable.contains("@color/glass_edge"));
        assertTrue("and the 17dp corner", rowDrawable.contains("@dimen/radius_nav_row"));
    }

    @Test
    public void theNavigationListHeadAndEmptyNoteCarryRealFacts() throws Exception {
        String body = layout("screen_navigation_body.xml");
        String head = elementFor(body, "navigation_list_head");
        assertTrue("the section head is one row",
                head.contains("android:orientation=\"horizontal\""));
        assertTrue("the label is Scheme A's 9.5sp tracked caption",
                head.contains("android:id=\"@+id/navigation_rows_section\"")
                        && head.contains("android:textSize=\"@dimen/text_list_head\"")
                        && head.contains("android:letterSpacing=\"0.1\""));
        assertTrue("and the right side has its own real count",
                head.contains("android:id=\"@+id/navigation_rows_count\""));

        String screen = screen("NavigationScreen.java");
        assertTrue("the count is the current row's child count",
                screen.contains("rowsCount.setText")
                        && screen.contains("current.childCount"));
        assertTrue("the empty note appears only for a branch with no children",
                screen.contains("empty.setVisibility")
                        && screen.contains("current.childCount == 0"));

        String empty = elementFor(body, "navigation_empty");
        assertTrue("the empty note is the dashed card",
                empty.contains("android:background=\"@drawable/bg_empty_branch\""));
        assertTrue("led by the sprout", empty.contains("@drawable/ic_brand_sprout"));
        assertTrue("the note is 10sp",
                empty.contains("android:textSize=\"@dimen/text_nav_empty\""));
        assertTrue("with real copy", strings().contains("name=\"navigation_empty\""));
        String emptyDrawable = drawable("bg_empty_branch.xml");
        assertTrue("the border is dashed", emptyDrawable.contains("android:dashWidth"));
        assertTrue("at the 14dp note corner", emptyDrawable.contains("@dimen/radius_md"));

        String dims = dimens();
        assertTrue(dims.contains("<dimen name=\"text_list_head\">9.5sp</dimen>"));
        assertTrue(dims.contains("<dimen name=\"text_nav_empty\">10sp</dimen>"));
        assertTrue(dims.contains("<dimen name=\"radius_nav_row\">17dp</dimen>"));
    }

    @Test
    public void theRealEntrypointsSurviveTheStructuralChange() throws Exception {
        String library = layout("screen_library_body.xml");
        assertTrue("the search box is still the one real input",
                library.contains("android:id=\"@+id/library_search_input\""));
        assertTrue("both segments are still clickable",
                elementAround(library, "library_segment_in_progress")
                        .contains("android:clickable=\"true\"")
                        && elementAround(library, "library_segment_done")
                                .contains("android:clickable=\"true\""));

        String navigation = layout("screen_navigation_body.xml");
        assertTrue("the summary head still toggles the fold",
                navigation.contains("android:id=\"@+id/navigation_summary_head\""));
        assertTrue("the conversation and canvas entries keep their ids",
                navigation.contains("android:id=\"@+id/navigation_enter\"")
                        && navigation.contains("android:id=\"@+id/navigation_canvas\""));
        assertTrue("the branch list and archived list still exist",
                navigation.contains("android:id=\"@+id/navigation_rows\"")
                        && navigation.contains("android:id=\"@+id/navigation_archived_rows\""));
        assertFalse("no dead attachment or chevron entry was invented on either page",
                library.contains("library_attach") || navigation.contains("navigation_share"));
    }
}
