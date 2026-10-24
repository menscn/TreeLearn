package com.treelearn.app.screen;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

import org.junit.Test;

/**
 * Structural pins for the Android glass surfaces (T-2026-10-21-049). Read as plain files so the
 * slice is gated on the JVM: the glass look is shared, and a later screen that copies a shape
 * instead of reusing the shared drawable is exactly the drift these assertions exist to catch.
 */
public final class GlassSurfaceLayoutTest {

    private static final Path RES = Paths.get("src", "main", "res").toAbsolutePath();
    private static final Path SRC = Paths.get("src", "main", "java", "com", "treelearn", "app")
            .toAbsolutePath();

    private static String read(Path path) throws Exception {
        assertTrue("missing file: " + path, Files.exists(path));
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    private static String drawable(String name) throws Exception {
        return read(RES.resolve(Paths.get("drawable", name)));
    }

    private static String layout(String name) throws Exception {
        return read(RES.resolve(Paths.get("layout", name)));
    }

    @Test
    public void everyGlassSurfaceCarriesTokenColoursAndTheOnePixelEdge() throws Exception {
        assertGlassDrawable("bg_glass_card.xml", "@color/glass_card",
                "@dimen/radius_xl");
        assertGlassDrawable("bg_glass_reader.xml", "@color/glass_reader",
                "@dimen/radius_2xl");
        assertGlassDrawable("bg_glass_field.xml", "@color/glass_field",
                "@dimen/radius_lg");
        assertGlassDrawable("bg_glass_bar.xml", "@color/glass_chrome", null);
        assertGlassDrawable("bg_glass_panel.xml", "@color/glass_chrome", null);
        assertGlassDrawable("bg_glass_control_bar.xml", "@color/glass_control",
                "@dimen/radius_full");
    }

    private void assertGlassDrawable(String name, String fill, String radius) throws Exception {
        String body = drawable(name);
        assertTrue(name + " must take its fill from the token copy", body.contains(fill));
        assertTrue(name + " must draw the 1px highlight edge",
                body.contains("@color/glass_edge") && body.contains("@dimen/hairline"));
        if (radius != null) {
            assertTrue(name + " must take its corner from the glass radius token",
                    body.contains(radius));
        }
    }

    @Test
    public void sharedSurfacesDelegateToTheGlassDrawablesInsteadOfPaintingTheirOwn() throws Exception {
        assertTrue("bg_card is the shared card surface",
                drawable("bg_card.xml").contains("@drawable/bg_glass_card"));
        assertTrue("library cards use the shared glass card",
                drawable("bg_library_card.xml").contains("@drawable/bg_glass_card"));
        assertTrue("home topic cards use the shared glass card",
                drawable("bg_topic_card.xml").contains("@drawable/bg_glass_card"));
        assertTrue("the composer's inner field is a glass field",
                drawable("bg_composer_inner.xml").contains("@drawable/bg_glass_field"));
        assertTrue("text fields are glass fields",
                drawable("bg_field.xml").contains("@drawable/bg_glass_field"));
        // 2026-10-22 Scheme A draws the library searchbar as a capsule (T-2026-10-22-057), so it
        // keeps the same field fill and edge on the fully rounded shape instead of the 18dp field.
        assertTrue("library search is the pill-shaped glass field",
                drawable("bg_library_search.xml").contains("@drawable/bg_glass_search")
                        && drawable("bg_glass_search.xml").contains("@color/glass_field"));
        assertTrue("the ask-AI chip keeps its semantic green outline over glass",
                drawable("bg_ask_ai_chip.xml").contains("@color/glass_field")
                        && drawable("bg_ask_ai_chip.xml").contains("@color/tl_green"));
    }

    @Test
    public void theTabBarIsGlassAndDropsTheDecorativeDivider() throws Exception {
        String bar = layout("screen_tab_bar.xml");
        // 2026-10-23 片 069：标签压在从它下方滚过去的正文之上，这一面改用控制面 token 而不是 chrome。
        assertTrue("the tab bar must be opaque enough to keep two layers of text apart",
                bar.contains("android:background=\"@drawable/bg_glass_control_bar\""));
        assertFalse("the 1px glass edge replaces the old line divider",
                bar.contains("android:background=\"@color/tl_line\""));
    }

    @Test
    public void theChatReaderAndComposerUseTheGlassLayer() throws Exception {
        String chat = layout("screen_chat_body.xml");
        assertTrue("the message flow sits on the reader surface",
                chat.contains("@drawable/bg_glass_reader"));
        assertTrue("the composer strip itself must not paint an opaque surface",
                chat.contains("android:id=\"@+id/chat_composer\"")
                        && chat.contains("android:background=\"@android:color/transparent\""));
    }

    @Test
    public void semanticCardsKeepTheirMeaningInsteadOfBeingTintedGlass() throws Exception {
        String message = layout("item_chat_message.xml");
        assertTrue("the failure bar keeps the soft red on its own opaque surface",
                message.contains("@drawable/bg_semantic_alert")
                        && drawable("bg_semantic_alert.xml").contains("@color/tl_red_soft"));
        assertTrue("the translation card keeps the faint green on its own opaque surface",
                message.contains("@drawable/bg_semantic_note")
                        && drawable("bg_semantic_note.xml").contains("@color/tl_green_faint"));
        assertFalse("a backgroundTint over bg_card would erase the glass edge",
                message.matches("(?s).*@drawable/bg_card\"[^>]*android:backgroundTint.*"));
    }

    @Test
    public void theUnselectedLibrarySegmentStopsPaintingAnOpaqueTint() throws Exception {
        String library = layout("screen_library_body.xml");
        assertFalse("the unselected segment must show the glass, not tl_surface",
                library.contains("android:backgroundTint=\"@color/tl_surface\""));
    }

    @Test
    public void theDrawerPanelAndStatusBarFollowTheMistScene() throws Exception {
        String drawer = read(SRC.resolve(Paths.get("screen", "HomeDrawerScreen.java")));
        assertTrue("the drawer panel is the shared glass panel",
                drawer.contains("setBackgroundResource(R.drawable.bg_glass_panel)"));
        assertFalse("the drawer must not paint an opaque surface colour",
                drawer.contains("setBackgroundColor(context.getColor(R.color.tl_surface))"));
        String theme = read(RES.resolve(Paths.get("values", "themes.xml")));
        assertTrue("the status bar takes the mist base so it joins the scene",
                theme.contains("android:statusBarColor\">@color/tl_mist_base"));
    }

    @Test
    public void everyTintedGlassFaceFalloffsOnTheProposalDiagonal() throws Exception {
        File[] faces = RES.resolve("drawable").toFile().listFiles(
                (dir, name) -> name.startsWith("bg_glass_") && name.endsWith(".xml"));
        assertTrue("no glass drawables to judge", faces != null && faces.length > 0);
        Arrays.sort(faces);
        for (File face : faces) {
            String body = new String(Files.readAllBytes(face.toPath()), StandardCharsets.UTF_8);
            String name = face.getName();
            // The control face is the one registered exception, and it is registered by its fill:
            // a tint that darkens the lower half of a 62dp pill is exactly what let the labels and
            // the text scrolling under them blur into one band.
            if (name.equals("bg_glass_control_bar.xml")) {
                assertFalse(name + " is flat on purpose, so it must not derive a tint stop",
                        body.contains("_lo\""));
                continue;
            }
            // CSS 145deg 在 shape 渐变里只能落到 45° 的倍数，取 315（=CSS 135，左上亮、右下暗）。
            // 这 10° 是机械等价的登记项，不是随手取整：见 T-2026-10-22-061 的 Constraints。
            assertTrue(name + " must fall off along the tint diagonal, not sit flat",
                    body.contains("android:angle=\"315\""));
            assertTrue(name + " must take its dark end from the derived tint stop",
                    body.matches("(?s).*@color/glass_[a-z]+_lo\".*"));
        }
    }

    @Test
    public void theFloatingCapsuleProjectsThroughItsOwnOutline() throws Exception {
        String bar = layout("screen_tab_bar.xml");
        assertTrue("the pill must be the projecting glass surface, not a plain LinearLayout",
                bar.contains("com.treelearn.app.screen.GlassSurfaceLayout"));
        String surface = read(SRC.resolve(Paths.get("screen", "GlassSurfaceLayout.java")));
        assertTrue("the shadow shape must come from an outline the surface owns",
                surface.contains("ViewOutlineProvider") && surface.contains("outline.setAlpha("));
        assertTrue("both shadow passes must be taken from the tokens",
                surface.contains("setOutlineSpotShadowColor")
                        && surface.contains("setOutlineAmbientShadowColor")
                        && surface.contains("R.color.glass_shadow_key")
                        && surface.contains("R.color.glass_shadow_ambient"));
        assertTrue("an attenuated background must not attenuate the shadow with it",
                !surface.contains("getBackground().getAlpha()"));
        for (String variant : new String[]{"layout/screen_tab_bar.xml",
                "layout-sw600dp/screen_tab_bar.xml"}) {
            assertTrue(variant + " must let the projected shadow leave its own bounds",
                    read(RES.resolve(Paths.get(variant.replace("/", File.separator))))
                            .contains("android:clipChildren=\"false\""));
        }
        // 阴影由父容器绘制：胶囊的直接父级放开还不够，grandparent 会把它裁回自己的框。
        assertTrue("the shell must not clip the capsule's shadow back to the capsule's bounds",
                layout("activity_main.xml").contains("android:clipChildren=\"false\""));
    }

    @Test
    public void theGlassFacesCarryTheTopSheenUnderTheirFill() throws Exception {
        // 提案把高光画在 .glass 上，glass.css 把它画在 .workspace-topbar/.app-sidebar/.branch-panel 上。
        // 少了这一层，底栏与侧栏就不是「更透的玻璃」，而是全 app 唯一没有光源的面。
        for (String name : new String[]{"bg_glass_bar.xml", "bg_glass_panel.xml",
                "bg_glass_control_bar.xml"}) {
            String body = drawable(name);
            assertTrue(name + " must put the sheen under its fill like every other glass face",
                    body.contains("@color/glass_sheen") && body.contains("android:angle=\"270\""));
        }
    }

    @Test
    public void theScenePainterUsesTheMistAndGlowTokens() throws Exception {
        String backdrop = read(SRC.resolve(Paths.get("screen", "PaperRuleBackdrop.java")));
        assertTrue("the scene is a real linear gradient, not a flat fill",
                backdrop.contains("LinearGradient"));
        assertTrue("the scene must keep its five glows", backdrop.contains("RadialGradient"));
        for (String token : new String[]{"tl_mist_base", "tl_mist_deep", "tl_mist_warm",
                "tl_mist_cool", "glow_green", "glow_sand", "glow_white", "glow_rose",
                "glow_lilac"}) {
            assertTrue("the scene must read " + token, backdrop.contains("R.color." + token));
        }
        assertTrue("the page background paints no rules since 2026-10-23, so it must not read the "
                + "line token any more", !backdrop.contains("tl_line"));
        assertTrue("and it must not schedule any lines", !backdrop.contains("lineOffsets"));
    }

    @Test
    public void theGlassRadiiAreTokensNotInlineNumbers() throws Exception {
        String dimens = read(RES.resolve(Paths.get("values", "dimens.xml")));
        assertTrue("r-xl lands as 22dp", dimens.contains("<dimen name=\"radius_xl\">22dp</dimen>"));
        assertTrue("r-2xl lands as 28dp", dimens.contains("<dimen name=\"radius_2xl\">28dp</dimen>"));
    }
}
