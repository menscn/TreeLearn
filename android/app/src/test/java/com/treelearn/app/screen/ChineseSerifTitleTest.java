package com.treelearn.app.screen;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.Test;

/**
 * The bundled Chinese serif (T-2026-10-22-054).
 *
 * <p>Scheme A sets only the brand, the home headline and the library page title in serif; topic
 * cards, search hits and the branch-navigation topbar keep the body sans. Before this slice every
 * serif was the ROM's own {@code serif}, whose glyphs and weight differ between vendors, so the
 * brand could not look the same on two phones. The font now ships in the APK under the OFL, and
 * these pins keep the licence next to it and keep the serif from leaking back onto card titles or
 * the shared topbar.
 *
 * <p>T-2026-10-25-100 moved the brand lockup from the column's first row into the floating topbar
 * and promoted the former 12sp subtitle to the page's one headline, so both changed file and size
 * without changing the rule: brand and headline serif, everything else sans.
 */
public final class ChineseSerifTitleTest {

    private static final Path RES = Paths.get("src", "main", "res").toAbsolutePath();
    private static final Path SRC = Paths.get("src", "main", "java", "com", "treelearn", "app")
            .toAbsolutePath();
    private static final Path ASSETS = Paths.get("src", "main", "assets").toAbsolutePath();
    private static final Path FONT = RES.resolve(Paths.get("font", "noto_serif_sc.otf"));

    private static String read(Path path) throws Exception {
        assertTrue("missing file: " + path, Files.exists(path));
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    private static String layout(String name) throws Exception {
        return read(RES.resolve(Paths.get("layout", name)));
    }

    /** The open tag of the element that owns the id, so a sibling's attribute cannot satisfy a pin. */
    private static String elementAround(String xml, String id) {
        int idIndex = xml.indexOf("android:id=\"@+id/" + id + "\"");
        assertTrue("id " + id + " must exist", idIndex >= 0);
        int elementEnd = xml.indexOf('>', idIndex);
        assertTrue("malformed layout", elementEnd >= 0);
        return xml.substring(Math.max(0, xml.lastIndexOf('<', idIndex)), elementEnd);
    }

    @Test
    public void theBundledFaceIsAnOpenTypeCffFont() throws Exception {
        assertTrue("the bundled font must exist at " + FONT, Files.exists(FONT));
        assertTrue("a full SC face is shipped, not a placeholder", Files.size(FONT) > 1_000_000);
        byte[] head = new byte[4];
        try (java.io.InputStream in = Files.newInputStream(FONT)) {
            assertEquals("the file must start with an sfnt header", 4, in.read(head));
        }
        assertEquals("the face is CFF-outline OpenType", "OTTO",
                new String(head, StandardCharsets.US_ASCII));
    }

    @Test
    public void theFontShipsWithItsOflLicence() throws Exception {
        String licence = read(ASSETS.resolve(Paths.get(
                "licenses", "NotoSerifCJK-OFL.txt")));
        assertTrue("the SIL Open Font License 1.1 text ships with the font",
                licence.contains("SIL OPEN FONT LICENSE Version 1.1"));
        assertTrue("the licence names the actual copyright holder",
                licence.contains("Adobe"));
        assertTrue("and the years recorded in the font's name table",
                licence.contains("2017-2022"));
    }

    @Test
    public void onlyTheBrandTheHeadlineAndLibraryTitleUseTheBundledSerif() throws Exception {
        // 2026-10-25 (T-2026-10-25-100): the brand lockup now sits in the floating topbar, so the
        // pin reads screen_home.xml rather than the column.
        assertTrue("the home brand uses the bundled serif",
                elementAround(layout("screen_home.xml"), "home_brand")
                        .contains("@font/noto_serif_sc"));
        // The former 12sp sans subtitle became the page's one headline at 22sp serif: the same
        // literal under the same id, now the largest serif on the page.
        assertTrue("the home headline uses it",
                elementAround(layout("screen_home_body.xml"), "home_hero_title")
                        .contains("@font/noto_serif_sc"));
        assertTrue("the library page title uses it",
                elementAround(layout("screen_library.xml"), "library_title")
                        .contains("@font/noto_serif_sc"));
        // 2026-10-22 Scheme A gives the branch navigation the shared 13sp sans capsule title
        // (T-2026-10-22-057); the library keeps the one large serif page title.
        assertFalse("the branch-navigation topbar is the shared sans",
                elementAround(layout("screen_navigation_body.xml"), "navigation_title")
                        .contains("@font/noto_serif_sc"));
    }

    /**
     * The bundled serif's CFF bounding box is 2.858em, and Android turns it into the line's ascent
     * and descent. The wordmark therefore cannot be {@code wrap_content}: at 41sp the box measured
     * 381px around 124px of ink and set the subtitle 52dp below the brand instead of the proposal's
     * 12dp (2026-10-22 walkthrough). At the topbar's 20sp the box is still nearly three times the
     * declared line, which is why the rule survived the move. The layout frames the view at
     * {@code brand_topbar_line_height} and {@link com.treelearn.app.screen.WordmarkTextView} centres
     * the font's box inside that frame.
     */
    @Test
    public void theWordmarkFramesTheFontsOwnLineBoxAtTheProposalHeight() throws Exception {
        String brand = elementAround(layout("screen_home.xml"), "home_brand");
        assertTrue("the frame is the declared line-height, not the font's box",
                brand.contains("android:layout_height=\"@dimen/brand_topbar_line_height\""));
        assertTrue("and the view that draws it is the one that centres the box",
                brand.startsWith("<com.treelearn.app.screen.WordmarkTextView"));

        String view = read(SRC.resolve(Paths.get("screen", "WordmarkTextView.java")));
        assertTrue("the glyphs keep the font's own centred baseline",
                view.contains("float baseline = (getHeight() - layout.getHeight()) / 2f"
                        + " + layout.getLineBaseline(0);"));
        // A translated canvas is clipped by TextView to the view's height, so only the top of the
        // 381px layout survived. The single line is drawn at that baseline instead.
        assertTrue("and the line is drawn directly rather than through the clipped layout",
                view.contains("canvas.drawText(layout.getText().toString(),"
                        + " layout.getLineLeft(0), baseline, getPaint());"));
        assertTrue("the font metric that forced the frame is written down next to it",
                view.contains("2.858em"));
    }

    /**
     * Scheme A's topic, library and search rows are sans. The old layouts asked for the ROM's
     * {@code serif} on all three, which made the cards read as a second, inconsistent brand voice.
     */
    @Test
    public void cardAndSearchTitlesStaySans() throws Exception {
        for (String name : new String[]{"item_home_topic.xml", "item_library_topic.xml",
                "item_search_hit.xml"}) {
            assertFalse(name + " must not ask for a serif face",
                    layout(name).contains("android:fontFamily=\"serif\""));
            assertFalse(name + " must not use the bundled serif either",
                    layout(name).contains("@font/noto_serif_sc"));
        }
        String theme = read(RES.resolve(Paths.get("values", "themes.xml")));
        assertTrue("the body face stays the platform sans",
                theme.contains("android:fontFamily\">sans-serif"));
    }
}
