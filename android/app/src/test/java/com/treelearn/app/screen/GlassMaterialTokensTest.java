package com.treelearn.app.screen;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.Test;

/**
 * The Android copy of the glass tokens (T-2026-10-21-049).
 *
 * <p>{@code colors.xml} only accepts opaque six-digit hex because the four-way design-token guard
 * maps every opaque token to exactly one {@code tl_*} resource. The glass surfaces are rgba, so
 * their Android copy lives in {@code values/glass.xml} as eight-digit ARGB. That copy must not be
 * hand-maintained: this test reads the authoritative {@code tokens.json} and checks the alpha
 * conversion automatically, so a value change in the prototype cannot silently leave Android on an
 * older material.
 */
public final class GlassMaterialTokensTest {

    /** Tests run with the app module as the working directory. */
    private static final Path REPO = Paths.get("..", "..").toAbsolutePath().normalize();
    private static final Path TOKENS = REPO.resolve(Paths.get(
            "Docs", "04-ux-design", "design-system", "tokens.json"));
    private static final Path RES = Paths.get("src", "main", "res").toAbsolutePath();

    private static final Pattern RGBA = Pattern.compile(
            "\"([a-z0-9-]+)\"\\s*:\\s*\\{\\s*\"value\"\\s*:\\s*\"rgba\\((\\d+),(\\d+),(\\d+),"
                    + "([0-9.]+)\\)\"");
    private static final Pattern HEX_TOKEN = Pattern.compile(
            "\"([a-z0-9-]+)\"\\s*:\\s*\\{\\s*\"value\"\\s*:\\s*\"(#[0-9A-Fa-f]{6})\"");
    private static final Pattern ARGB_RESOURCE = Pattern.compile(
            "<color\\s+name=\"([^\"]+)\"\\s*>\\s*(#[0-9A-Fa-f]{8})\\s*</color>");
    private static final Pattern OPAQUE_RESOURCE = Pattern.compile(
            "<color\\s+name=\"([^\"]+)\"\\s*>\\s*(#[0-9A-Fa-f]{6})\\s*</color>");
    private static final Pattern FALLOFF = Pattern.compile(
            "\"glass-tint-falloff\"\\s*:\\s*\\{\\s*\"value\"\\s*:\\s*\"([0-9.]+)\"");
    /** A surface drawn with the diagonal tint names its dark end {@code <surface>_lo} in the drawable. */
    private static final Pattern LO_REFERENCE = Pattern.compile(
            "@color/(glass_[a-z_]+)_lo\\b");

    private static String read(Path path) throws Exception {
        assertTrue("missing file: " + path, Files.exists(path));
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    private static String argb(int red, int green, int blue, String alpha) {
        int a = Math.round(Float.parseFloat(alpha) * 255f);
        return String.format("#%02X%02X%02X%02X", a, red, green, blue);
    }

    private static float falloff() throws Exception {
        Matcher matcher = FALLOFF.matcher(read(TOKENS));
        assertTrue("tokens.json must carry glass-tint-falloff", matcher.find());
        return Float.parseFloat(matcher.group(1));
    }

    /**
     * Every rgba colour token the translucent surfaces and the Morandi scene consume, keyed by
     * resource name. Android has no other way to read an rgba token, so the ARGB copy is the
     * consumption path rather than a convenience duplicate.
     */
    private static Map<String, String> expectedGlassColours() throws Exception {
        Map<String, String> expected = new LinkedHashMap<>();
        Matcher matcher = RGBA.matcher(read(TOKENS));
        while (matcher.find()) {
            String token = matcher.group(1);
            if (!token.startsWith("glass-") && !token.startsWith("glow-")) {
                continue;
            }
            expected.put(token.replace('-', '_'),
                    argb(Integer.parseInt(matcher.group(2)),
                            Integer.parseInt(matcher.group(3)),
                            Integer.parseInt(matcher.group(4)),
                            matcher.group(5)));
        }
        return expected;
    }

    /**
     * The tint's dark ends are never authored: each one is the surface's own alpha times the single
     * falloff token. The set of tinted surfaces is read from the drawables that reference a
     * {@code _lo} colour, so this test cannot become a second list of which faces are tinted.
     */
    private static Map<String, String> expectedTintFallopStops() throws Exception {
        Map<String, String> expected = expectedGlassColours();
        Map<String, String> derived = new LinkedHashMap<>();
        File drawableDir = RES.resolve("drawable").toFile();
        File[] drawables = drawableDir.listFiles();
        assertTrue("no drawables to read", drawables != null && drawables.length > 0);
        Arrays.sort(drawables);
        for (File drawable : drawables) {
            Matcher matcher = LO_REFERENCE.matcher(
                    new String(Files.readAllBytes(drawable.toPath()), StandardCharsets.UTF_8));
            while (matcher.find()) {
                String surface = matcher.group(1);
                String argb = expected.get(surface);
                assertNotNull(surface + " must be a token-mirrored surface before it can be tinted",
                        argb);
                int alpha = Math.round((Integer.parseInt(argb.substring(1, 3), 16) & 0xFF) * falloff());
                derived.put(surface + "_lo",
                        String.format("#%02X%s", alpha, argb.substring(3)));
            }
        }
        return derived;
    }

    private static Map<String, String> glassResources() throws Exception {
        Map<String, String> resources = new LinkedHashMap<>();
        Matcher matcher = ARGB_RESOURCE.matcher(read(RES.resolve(Paths.get("values", "glass.xml"))));
        while (matcher.find()) {
            resources.put(matcher.group(1), matcher.group(2).toUpperCase());
        }
        return resources;
    }

    @Test
    public void everyGlassTokenHasTheSameArgbCopyOnAndroid() throws Exception {
        Map<String, String> expected = expectedGlassColours();
        assertEquals("the glass material and the Morandi scene define fifteen translucent colour tokens",
                15, expected.size());
        Map<String, String> wanted = new LinkedHashMap<>(expected);
        wanted.putAll(expectedTintFallopStops());
        assertEquals("glass.xml must carry the token copies plus the derived tint ends, "
                + "nothing hand-written", wanted, glassResources());
    }

    /** The falloff must be a ratio, never a second literal: two faces at different alphas cannot share one dark end. */
    @Test
    public void theTintEndFollowsEachSurfaceAlphaInsteadOfOneSharedValue() throws Exception {
        Map<String, String> stops = expectedTintFallopStops();
        assertTrue("the chrome capsule must be tinted", stops.containsKey("glass_chrome_lo"));
        assertTrue("the reader surface must be tinted", stops.containsKey("glass_reader_lo"));
        assertTrue("the control face is flat on purpose: its labels sit on other text, and a tint "
                + "that darkens the lower half of a 62dp pill is what made the two layers collide",
                !stops.containsKey("glass_control_lo"));
        int chrome = Integer.parseInt(stops.get("glass_chrome_lo").substring(1, 3), 16);
        int reader = Integer.parseInt(stops.get("glass_reader_lo").substring(1, 3), 16);
        assertTrue("the more opaque reader must keep a darker-lit end than the chrome, got "
                + chrome + " vs " + reader, reader > chrome);
    }

    /**
     * 2026-10-23 decision: a surface that lets the scene through at this strength stops reading as a
     * surface at all. The assertion is a floor rather than a copy of the tokens — pinning today's
     * value would put a second source of the same fact in the test suite and would stay silent about
     * a change in the other direction.
     *
     * <p>{@code glass-control} joins the reader's floor the same day for the opposite reason: a face
     * whose labels overlap other text is judged by legibility, not by how much scene it lets through.
     *
     * <p>2026-10-23-077 lifts it past the reader. The pinned home bar measured the recommendation row
     * crossing underneath it at |dL| max 10.3 L over 13.6..22.3% of the row — above the 8 L line
     * {@code check_glass_depth.py} already uses for "content is passing under this glass". For a
     * transparent face that reading is the goal; for the control face it is the defect, so the same
     * number is a ceiling there and a floor here.
     */
    @Test
    public void theGlassSurfacesStayAboveTheAgreedOpacityFloors() throws Exception {
        Map<String, Float> floors = new LinkedHashMap<>();
        floors.put("glass-chrome", 0.70f);
        floors.put("glass-card", 0.84f);
        floors.put("glass-reader", 0.92f);
        floors.put("glass-field", 0.70f);
        floors.put("glass-control", 0.97f);

        Map<String, Float> alphas = new LinkedHashMap<>();
        Matcher matcher = RGBA.matcher(read(TOKENS));
        while (matcher.find()) {
            alphas.put(matcher.group(1), Float.parseFloat(matcher.group(5)));
        }
        for (Map.Entry<String, Float> entry : floors.entrySet()) {
            Float value = alphas.get(entry.getKey());
            assertNotNull("tokens.json must carry " + entry.getKey(), value);
            assertTrue(entry.getKey() + " must stay at or above alpha " + entry.getValue()
                    + ", got " + value, value >= entry.getValue());
        }
    }

    /**
     * The Morandi tints are opaque surface colours, not glass: they go to {@code colors.xml} like
     * the mist scene, so the four-way guard keeps mapping them one-to-one. Keeping them out of
     * {@code glass.xml} is already enforced by the exact map above; this test pins the other half.
     */
    @Test
    public void theMorandiTintsAreOpaqueColoursOnAndroid() throws Exception {
        Map<String, String> expected = new LinkedHashMap<>();
        Matcher matcher = HEX_TOKEN.matcher(read(TOKENS));
        while (matcher.find()) {
            if (matcher.group(1).startsWith("tint-")) {
                expected.put("tl_" + matcher.group(1).replace('-', '_'), matcher.group(2));
            }
        }
        assertEquals("the Morandi palette has four opaque tints", 4, expected.size());

        Map<String, String> colours = new LinkedHashMap<>();
        Matcher opaque = OPAQUE_RESOURCE.matcher(read(RES.resolve(Paths.get("values", "colors.xml"))));
        while (opaque.find()) {
            colours.put(opaque.group(1), opaque.group(2).toUpperCase());
        }
        for (Map.Entry<String, String> entry : expected.entrySet()) {
            assertEquals("colors.xml must mirror " + entry.getKey(),
                    entry.getValue().toUpperCase(), colours.get(entry.getKey()));
        }
    }

    @Test
    public void theMistSceneColoursAreTheOpaqueSubset() throws Exception {
        Map<String, String> expected = new LinkedHashMap<>();
        Matcher matcher = HEX_TOKEN.matcher(read(TOKENS));
        while (matcher.find()) {
            if (matcher.group(1).startsWith("mist-")) {
                expected.put("tl_" + matcher.group(1).replace('-', '_'), matcher.group(2));
            }
        }
        assertEquals("the mist scene has four opaque tokens", 4, expected.size());

        Map<String, String> colours = new LinkedHashMap<>();
        Matcher opaque = OPAQUE_RESOURCE.matcher(read(RES.resolve(Paths.get("values", "colors.xml"))));
        while (opaque.find()) {
            colours.put(opaque.group(1), opaque.group(2).toUpperCase());
        }
        for (Map.Entry<String, String> entry : expected.entrySet()) {
            assertEquals("colors.xml must mirror " + entry.getKey(),
                    entry.getValue().toUpperCase(), colours.get(entry.getKey()));
        }
    }
}
