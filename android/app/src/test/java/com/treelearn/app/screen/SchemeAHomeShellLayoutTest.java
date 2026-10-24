package com.treelearn.app.screen;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.Test;

/**
 * Structural pins for the 2026-10-22 Scheme A alignment of the shell and the home page
 * (T-2026-10-22-055).
 *
 * <p>The APK built after 049 carried the mist glass on the old structure, which is why the learner
 * could not recognise Scheme A on the device. These assertions therefore pin structure rather than
 * colour: the floating capsule tab bar, the host's bottom clearance, the capsule topbar, the brand
 * lockup, the tall glass composer, the 232dp recommendation card and the compact history card.
 *
 * <p>Everything is read as plain files on the JVM, the same way the other screen pins are. Where a
 * value comes from the proposal's CSS it is named in {@code dimens.xml} with its source, so a later
 * edit has one place to argue with instead of a number scattered through layouts.
 */
public final class SchemeAHomeShellLayoutTest {

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

    private static String tablet(String name) throws Exception {
        return read(RES.resolve(Paths.get("layout-sw600dp", name)));
    }

    private static String drawable(String name) throws Exception {
        return read(RES.resolve(Paths.get("drawable", name)));
    }

    private static String dimens() throws Exception {
        return read(RES.resolve(Paths.get("values", "dimens.xml")));
    }

    /** The element that owns the id: from the tag opening it to the end of the open tag. */
    private static String elementAround(String xml, String id) {
        int idIndex = xml.indexOf("android:id=\"@+id/" + id + "\"");
        assertTrue("id " + id + " must exist", idIndex >= 0);
        int elementEnd = xml.indexOf('>', idIndex);
        assertTrue("malformed layout", elementEnd >= 0);
        return xml.substring(Math.max(0, xml.lastIndexOf('<', idIndex)), elementEnd);
    }

    /** The dp a dimen resolves to, so a judgement can compare two values instead of two strings. */
    private static int dp(String name) throws Exception {
        String xml = dimens();
        int at = xml.indexOf("name=\"" + name + "\"");
        assertTrue("dimen " + name + " must exist", at >= 0);
        int end = xml.indexOf("<", xml.indexOf('>', at));
        String body = xml.substring(xml.indexOf('>', at) + 1, end).trim();
        return Integer.parseInt(body.substring(0, body.length() - 2).trim());
    }

    // ------------------------------------------------------------------
    // The shell: the bar floats over content that scrolls under it
    // ------------------------------------------------------------------

    @Test
    public void theTabBarFloatsAboveTheContentThatScrollsUnderIt() throws Exception {
        String shell = layout("activity_main.xml");
        int includeStart = shell.indexOf("<include");
        assertTrue("the bar arrives through an include", includeStart >= 0);
        String include = shell.substring(includeStart);
        include = include.substring(0, include.indexOf("/>") + 2);
        assertTrue("that include is the tab bar",
                include.contains("layout=\"@layout/screen_tab_bar\""));
        assertTrue("the bar is pinned to the bottom of an overlay container",
                include.contains("android:layout_gravity=\"bottom\""));
        assertTrue("Scheme A's capsule stops 24dp from each side",
                include.contains("android:layout_marginStart=\"@dimen/tab_bar_margin\"")
                        && include.contains("android:layout_marginEnd=\"@dimen/tab_bar_margin\""));
        assertTrue("and floats 20dp above the window bottom",
                include.contains("android:layout_marginBottom=\"@dimen/tab_bar_bottom\""));
        assertTrue("the bar is no longer a child of the content column",
                !shell.contains("android:orientation=\"vertical\"\n    android:paddingBottom"));

        assertTrue("the bar is a later sibling of the content host, so it can float over it",
                shell.indexOf("@+id/shell_host") > 0 && shell.indexOf("@+id/shell_host") < includeStart);

        String main = read(SRC.resolve(Paths.get("MainActivity.java")));
        int swap = main.indexOf("private void swap(View screen)");
        assertTrue("swap still exists as the one place the bar is decided", swap > 0);
        String swapBody = main.substring(swap, main.indexOf("\n    }", swap));
        assertTrue("one predicate decides the bar for every screen",
                swapBody.contains("boolean firstLevel")
                        && swapBody.contains("tabBar.setVisibility("));
        assertTrue("a second-level screen hides the bar rather than sitting under a padded one",
                swapBody.contains("firstLevel ? View.VISIBLE : View.GONE"));
    }

    @Test
    public void theTabBarIsACapsuleWithTwoPillItems() throws Exception {
        String bar = layout("screen_tab_bar.xml");
        assertTrue("the shell keeps the tab_bar id", bar.contains("android:id=\"@+id/tab_bar\""));
        assertTrue("the visible capsule is its own node",
                bar.contains("android:id=\"@+id/tab_bar_pill\""));
        assertTrue("the capsule draws the control face: its labels sit over text that scrolls under it",
                bar.contains("android:background=\"@drawable/bg_glass_control_bar\""));
        assertTrue("Scheme A's 6dp inner padding",
                bar.contains("android:padding=\"@dimen/tab_bar_padding\""));

        String homeItem = elementAround(bar, "tab_home");
        assertTrue("the item is the 50dp pill", homeItem.contains(
                "android:layout_height=\"@dimen/tab_bar_item\""));
        assertTrue("and it paints its selected state through the selector",
                homeItem.contains("android:background=\"@drawable/bg_tab_item\""));
        assertTrue("Scheme A puts the glyph beside the label",
                homeItem.contains("android:orientation=\"horizontal\""));
        assertTrue("the glyph is 18dp", elementAround(bar, "tab_home_icon")
                .contains("android:layout_width=\"@dimen/tab_bar_icon\""));
        assertTrue("the label is the caption size", elementAround(bar, "tab_home_label")
                .contains("android:textSize=\"@dimen/text_cap\""));

        String tablet = read(RES.resolve(Paths.get("layout-sw600dp", "screen_tab_bar.xml")));
        assertTrue("the tablet copy carries the same ids",
                tablet.contains("android:id=\"@+id/tab_bar\"")
                        && tablet.contains("android:id=\"@+id/tab_bar_pill\""));
        assertTrue("the tablet capsule is capped below the reading measure so it cannot clip",
                tablet.contains("@dimen/tab_bar_tablet_width"));
        assertTrue("the tablet capsule takes the same control face, not chrome",
                tablet.contains("android:background=\"@drawable/bg_glass_control_bar\""));
        assertFalse("the old divider is not part of a floating capsule",
                bar.contains("android:background=\"@color/tl_line\"")
                        || tablet.contains("android:background=\"@color/tl_line\""));

        String glassBar = drawable("bg_glass_control_bar.xml");
        assertTrue("the capsule keeps the flat control fill",
                glassBar.contains("@color/glass_control"));
        assertTrue("and the 1px highlight edge",
                glassBar.contains("@color/glass_edge") && glassBar.contains("@dimen/hairline"));
        assertTrue("with Scheme A's fully rounded corners",
                glassBar.contains("@dimen/radius_full"));

        String selector = drawable("bg_tab_item.xml");
        assertTrue("the selected state is what changes the item",
                selector.contains("android:state_selected=\"true\"")
                        && selector.contains("@drawable/bg_tab_item_active"));
        String active = drawable("bg_tab_item_active.xml");
        assertTrue("the active pill is the glass sheen",
                active.contains("@color/glass_sheen"));
        assertTrue("and keeps the capsule radius",
                active.contains("@dimen/radius_full"));
    }

    // ------------------------------------------------------------------
    // Home: topbar, brand, composer
    // ------------------------------------------------------------------

    @Test
    public void theHomeTopbarIsAGlassCapsule() throws Exception {
        String body = layout("screen_home.xml");
        String appbar = elementAround(body, "home_appbar");
        assertTrue("a pinned bar is chrome over moving text, so it takes the control face the "
                        + "bottom capsule takes (072), not the see-through chrome face",
                appbar.contains("android:background=\"@drawable/bg_glass_control_bar\""));
        assertTrue("Scheme A's 52dp capsule", appbar.contains(
                "android:layout_height=\"@dimen/topbar_height\""));
        assertTrue("it sits 6dp below the status bar",
                appbar.contains("android:layout_marginTop=\"@dimen/topbar_offset\""));
        assertTrue("and 16dp in from each side",
                appbar.contains("android:layout_marginStart=\"@dimen/space_s4\"")
                        && appbar.contains("android:layout_marginEnd=\"@dimen/space_s4\""));
        assertTrue("with an 8dp inner inset",
                appbar.contains("android:paddingStart=\"@dimen/space_s2\"")
                        && appbar.contains("android:paddingEnd=\"@dimen/space_s2\""));
        assertFalse("the old bare-row top padding is gone",
                appbar.contains("android:paddingTop=\"@dimen/space_s4\""));

        String drawer = elementAround(body, "home_drawer_button");
        assertTrue("the icon button is 36dp",
                drawer.contains("android:layout_width=\"@dimen/topbar_icon\"")
                        && drawer.contains("android:layout_height=\"@dimen/topbar_icon\""));
        assertTrue("the 20dp glyph comes from the 8dp inset",
                drawer.contains("android:padding=\"@dimen/space_s2\""));
        assertTrue("the menu glyph is unchanged", drawer.contains("@drawable/ic_nav_menu"));
        assertFalse("the old negative offset is gone", drawer.contains("-8dp"));

        String settings = elementAround(body, "home_settings_button");
        assertTrue("the gear keeps the same 36dp capsule target",
                settings.contains("android:layout_width=\"@dimen/topbar_icon\""));
    }

    /**
     * T-2026-10-23-075: the topbar stays put and the column moves under it.
     *
     * <p>The bar used to be {@code home_content}'s first row, so scrolling took the drawer entry and
     * the settings entry away with the brand - the learner's report was the bar disappearing while
     * the page was still moving. The Web page has pinned it since Scheme A (`.workspace-topbar` is
     * {@code position: absolute} over a scroll container that pays {@code padding-top}), so floating
     * is the parity, not a second design.
     *
     * <p>The judgement is the same pair the bottom capsule already holds: the bar must be a later
     * sibling of the scroll container (so it draws over it), and the container must pay the bar's
     * room in padding with {@code clipToPadding} off (so rows travel under the glass and still rest
     * whole below it). A pinned bar with no clearance would sit on the first row and hide it.
     */
    @Test
    public void theHomeTopbarFloatsWhileTheColumnScrollsUnderIt() throws Exception {
        String shell = layout("screen_home.xml");
        int scroll = shell.indexOf("@+id/home_root");
        int appbar = shell.indexOf("@+id/home_appbar");
        assertTrue("the bar is a later sibling of the scroll container, so it floats over it",
                scroll > 0 && appbar > scroll);
        assertTrue("the column no longer carries the bar as its first row",
                !layout("screen_home_body.xml").contains("home_appbar"));
        String root = elementAround(shell, "home_root");
        assertTrue("the page pays the bar's room in its own scroll container",
                root.contains("android:paddingTop=\"@dimen/top_bar_clearance\""));
        assertTrue("and does not clip it, so content travels under the glass",
                root.contains("android:clipToPadding=\"false\""));
        assertTrue("the wrapper lets the capsule's shadow out",
                elementAround(shell, "home_screen").contains("android:clipChildren=\"false\""));

        assertTrue("the clearance is the bar's outer height and not a new number: "
                        + "6dp offset + 52dp face",
                dp("top_bar_clearance") == dp("topbar_offset") + dp("topbar_height"));

        String tablet = tablet("screen_home.xml");
        assertTrue("the tablet page pins it the same way",
                tablet.contains("@+id/home_appbar")
                        && elementAround(tablet, "home_root")
                                .contains("android:paddingTop=\"@dimen/top_bar_clearance\""));
        assertTrue("and caps the bar to the reading measure so a wide window does not show a "
                        + "full-width bar over a narrow column",
                elementAround(tablet, "home_appbar")
                        .contains("android:layout_width=\"@dimen/content_measure\""));
    }

    @Test
    public void theBrandBlockMatchesSchemeA() throws Exception {
        String body = layout("screen_home_body.xml");
        String brand = elementAround(body, "home_brand");
        assertTrue("the wordmark keeps the bundled serif",
                brand.contains("android:fontFamily=\"@font/noto_serif_sc\""));
        assertTrue("Scheme A's 41sp wordmark",
                brand.contains("android:textSize=\"@dimen/text_brand\""));
        assertTrue("at 600 weight", brand.contains("android:textStyle=\"bold\""));
        assertTrue("with the tracked spacing",
                brand.contains("android:letterSpacing=\"0.06\""));
        assertTrue("in the deep green of the lockup",
                brand.contains("android:textColor=\"@color/tl_green_deep\""));
        assertTrue("the sprout and wordmark are 12dp apart",
                brand.contains("android:layout_marginStart=\"@dimen/space_s3\""));
        assertTrue("the wordmark is framed by the view that clamps the bundled font's line box",
                brand.startsWith("<com.treelearn.app.screen.WordmarkTextView"));
        assertTrue("at the proposal's line-height: 1 rather than the font's 2.858em box",
                brand.contains("android:layout_height=\"@dimen/brand_line_height\""));

        String mark = elementAround(body, "home_brand_mark");
        assertTrue("the sprout is the 40dp lockup mark",
                mark.contains("android:layout_width=\"@dimen/brand_mark_size\""));

        String hero = elementAround(body, "home_hero");
        assertTrue("the brand block keeps Scheme A's 22dp top",
                hero.contains("android:paddingTop=\"@dimen/home_brand_top\""));
        assertTrue("and 20dp bottom",
                hero.contains("android:paddingBottom=\"@dimen/home_brand_bottom\""));

        String subtitle = elementAround(body, "home_hero_title");
        assertFalse("the subtitle is the sans line in Scheme A",
                subtitle.contains("@font/noto_serif_sc"));
        assertTrue("at 12sp", subtitle.contains("android:textSize=\"@dimen/text_home_sub\""));
        assertTrue("in secondary ink", subtitle.contains("android:textColor=\"@color/tl_ink2\""));
        assertTrue("with the same tracking as the proposal",
                subtitle.contains("android:letterSpacing=\"0.06\""));
        assertTrue("and 12dp under the lockup",
                subtitle.contains("android:layout_marginTop=\"@dimen/space_s3\""));

        String home = read(SRC.resolve(Paths.get("screen", "HomeScreen.java")));
        assertFalse("Scheme A always shows the subtitle, so no render hides it",
                home.contains("heroTitle.setVisibility"));
        assertFalse("and no render toggles the brand block",
                home.contains("hero.setVisibility"));

        String dims = dimens();
        assertTrue("the mark lands as 40dp",
                dims.contains("<dimen name=\"brand_mark_size\">40dp</dimen>"));
        assertTrue("the wordmark lands as 41sp",
                dims.contains("<dimen name=\"text_brand\">41sp</dimen>"));
        assertTrue("and its line-height frame is its own token",
                dims.contains("<dimen name=\"brand_line_height\">41sp</dimen>"));
        assertTrue("the subtitle lands as 12sp",
                dims.contains("<dimen name=\"text_home_sub\">12sp</dimen>"));
        assertTrue("the block offsets are named",
                dims.contains("<dimen name=\"home_brand_top\">22dp</dimen>")
                        && dims.contains("<dimen name=\"home_brand_bottom\">20dp</dimen>"));
    }

    @Test
    public void theComposerIsTheTallGlassCard() throws Exception {
        String body = layout("screen_home_body.xml");
        String composer = elementAround(body, "home_composer");
        assertTrue("the composer is the 28dp glass card",
                composer.contains("android:background=\"@drawable/bg_glass_composer\""));
        assertTrue("Scheme A's 138dp minimum",
                composer.contains("android:minHeight=\"@dimen/composer_min_height\""));
        assertTrue("with 14dp sides and top",
                composer.contains("android:paddingStart=\"@dimen/composer_pad\"")
                        && composer.contains("android:paddingTop=\"@dimen/composer_pad\"")
                        && composer.contains("android:paddingEnd=\"@dimen/composer_pad\""));
        assertTrue("and 12dp bottom",
                composer.contains("android:paddingBottom=\"@dimen/composer_pad_bottom\""));
        assertTrue("the field uses the 14.5sp writing size",
                elementAround(body, "home_draft")
                        .contains("android:textSize=\"@dimen/text_composer\""));
        assertTrue("the foot sits 10dp under the field",
                body.contains("android:layout_marginTop=\"@dimen/composer_row_top\""));

        String composerDrawable = drawable("bg_glass_composer.xml");
        assertTrue("the card fill is the glass card token",
                composerDrawable.contains("@color/glass_card"));
        assertTrue("with the highlight edge",
                composerDrawable.contains("@color/glass_edge"));
        assertTrue("and the panel radius",
                composerDrawable.contains("@dimen/radius_2xl"));

        String chip = layout("view_model_chip.xml");
        assertTrue("the model pill keeps its 30dp Scheme A height",
                chip.contains("@dimen/model_chip_height"));

        String dims = dimens();
        assertTrue(dims.contains("<dimen name=\"composer_min_height\">138dp</dimen>"));
        assertTrue(dims.contains("<dimen name=\"text_composer\">14.5sp</dimen>"));
        assertTrue(dims.contains("<dimen name=\"composer_row_top\">10dp</dimen>"));
    }

    // ------------------------------------------------------------------
    // Home: recommendation and history cards
    // ------------------------------------------------------------------

    @Test
    public void theRecommendationCardsAre232dpGlassCards() throws Exception {
        String item = layout("item_recommendation.xml");
        String chip = elementAround(item, "rec_chip");
        assertTrue("Scheme A's fixed 232dp card",
                chip.contains("android:layout_width=\"@dimen/rec_card_width\""));
        assertTrue("on its own glass card",
                chip.contains("android:background=\"@drawable/bg_rec_card\""));
        assertTrue("stacked, because the category tag travels with the question",
                chip.contains("android:orientation=\"vertical\""));
        assertTrue("the category tag survives (PRD 6.2.17 point 3)",
                item.contains("android:id=\"@+id/rec_tag\""));
        assertFalse("the chip is the only target, so no decorative chevron rides on it",
                item.contains("rec_chevron") || item.contains("@drawable/ic_chevron_right"));
        assertTrue("the question is the caption size",
                elementAround(item, "rec_text").contains("android:textSize=\"@dimen/text_cap\""));
        String icon = elementAround(item, "rec_icon");
        assertTrue("the card leads with Scheme A's 14dp sparkle",
                icon.contains("android:layout_width=\"@dimen/rec_icon_size\""));
        assertTrue("tinted with the identity green",
                icon.contains("android:tint=\"@color/tl_green\""));

        String card = drawable("bg_rec_card.xml");
        assertTrue("the recommendation card is glass", card.contains("@color/glass_card"));
        assertTrue("with the 1px edge", card.contains("@color/glass_edge"));
        assertTrue("and Scheme A's 17dp corner", card.contains("@dimen/radius_rec"));

        String dims = dimens();
        assertTrue(dims.contains("<dimen name=\"rec_card_width\">232dp</dimen>"));
        assertTrue(dims.contains("<dimen name=\"radius_rec\">17dp</dimen>"));
        assertTrue(dims.contains("<dimen name=\"rec_icon_size\">14dp</dimen>"));
    }

    @Test
    public void theHistoryCardUsesTheCompactSchemeAType() throws Exception {
        String item = layout("item_home_topic.xml");
        String card = elementAround(item, "home_topic_card");
        assertTrue("Scheme A's 11dp vertical padding",
                card.contains("android:paddingTop=\"@dimen/topic_pad_v\"")
                        && card.contains("android:paddingBottom=\"@dimen/topic_pad_v\""));
        assertTrue("and 12dp horizontal padding",
                card.contains("android:paddingStart=\"@dimen/topic_pad_h\"")
                        && card.contains("android:paddingEnd=\"@dimen/topic_pad_h\""));

        String chevron = elementAround(item, "home_topic_chevron");
        assertTrue("the card carries the 14dp chevron",
                chevron.contains("android:layout_width=\"@dimen/topic_chevron\""));
        assertTrue("pointing right", chevron.contains("@drawable/ic_chevron_right"));

        assertTrue("the title is 13sp",
                elementAround(item, "home_topic_title")
                        .contains("android:textSize=\"@dimen/text_sm\""));
        assertTrue("and semibold",
                elementAround(item, "home_topic_title").contains("android:textStyle=\"bold\""));
        assertTrue("the summary is the caption size",
                elementAround(item, "home_topic_summary")
                        .contains("android:textSize=\"@dimen/text_cap\""));
        assertTrue("the meta line is the caption size",
                elementAround(item, "home_topic_meta")
                        .contains("android:textSize=\"@dimen/text_cap\""));
        assertTrue("the meta row sits 6dp under the summary",
                elementAround(item, "home_topic_meta_row")
                        .contains("android:layout_marginTop=\"@dimen/topic_meta_top\""));

        String home = read(SRC.resolve(Paths.get("screen", "HomeScreen.java")));
        int margin = home.indexOf("params.bottomMargin");
        assertTrue("cards are 8dp apart, as in Scheme A", margin > 0
                && home.substring(margin, Math.min(home.length(), margin + 160))
                        .contains("R.dimen.space_s2"));

        String dims = dimens();
        assertTrue(dims.contains("<dimen name=\"topic_pad_v\">11dp</dimen>"));
        assertTrue(dims.contains("<dimen name=\"topic_pad_h\">12dp</dimen>"));
        assertTrue(dims.contains("<dimen name=\"topic_chevron\">14dp</dimen>"));
        assertTrue(dims.contains("<dimen name=\"topic_meta_top\">6dp</dimen>"));
    }

    @Test
    public void theHomeKeepsNoDeadAttachmentEntry() throws Exception {
        String body = layout("screen_home_body.xml");
        assertFalse("the home has no attachment path, so it carries no attachment button",
                body.contains("home_attach") || body.contains("@drawable/ic_attach"));
        assertTrue("the model chip is still the leading control of the foot",
                body.contains("@+id/home_model_chip"));
        assertTrue("and the send control is still the real one",
                body.contains("@+id/home_send"));
    }
}
