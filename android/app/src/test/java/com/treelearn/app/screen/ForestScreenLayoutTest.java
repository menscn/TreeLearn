package com.treelearn.app.screen;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.Test;

/**
 * T-2026-10-23-068: the forest page is one read-only ground reached from the drawer, drawn from
 * tokens, with a 44dp way back and a 44dp way into a tree.
 */
public final class ForestScreenLayoutTest {

    private static final Path RES = Paths.get("src", "main", "res").toAbsolutePath();
    private static final Path SRC = Paths.get("src", "main", "java", "com", "treelearn", "app")
            .toAbsolutePath();

    private static String read(Path path) throws Exception {
        assertTrue("missing file: " + path, Files.exists(path));
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    @Test
    public void theForestPageCarriesItsOwnBackTitleAndGround() throws Exception {
        String screen = read(RES.resolve(Paths.get("layout", "screen_forest.xml")));

        assertTrue("the page names itself",
                screen.contains("android:text=\"@string/forest_title\""));
        assertTrue("the subtitle keeps the one-line promise",
                screen.contains("android:text=\"@string/forest_subtitle\""));
        assertTrue("the back key keeps the 44dp target",
                screen.contains("android:id=\"@+id/forest_back\"")
                        && screen.contains("android:layout_width=\"@dimen/touch_min\"")
                        && screen.contains("android:layout_height=\"@dimen/touch_min\""));
        assertTrue("the scene is the custom ground",
                screen.contains("com.treelearn.app.forest.ForestScene"));
        assertTrue("the hint invites the first tap",
                screen.contains("android:text=\"@string/forest_hint\""));
        assertTrue("the compass is a decoration, not a control",
                screen.contains("android:id=\"@+id/forest_compass\""));
        assertTrue("the sheet carries title and facts",
                screen.contains("android:id=\"@+id/forest_sheet_title\"")
                        && screen.contains("android:id=\"@+id/forest_sheet_meta\""));
        assertTrue("the sheet's way into the tree is 44dp",
                screen.contains("android:id=\"@+id/forest_sheet_open\"")
                        && screen.contains("android:layout_width=\"@dimen/touch_min\"")
                        && screen.contains("android:layout_height=\"@dimen/touch_min\""));
        assertTrue("the footnote sits on the ground",
                screen.contains("android:id=\"@+id/forest_footnote\""));
        assertTrue("the empty ground is drawn, not implied",
                screen.contains("android:id=\"@+id/forest_state\""));
    }

    @Test
    public void theDrawerEntrySitsAfterTheHeatmapAndBeforeRecentLearning() throws Exception {
        String entry = read(RES.resolve(Paths.get("layout", "drawer_forest_entry.xml")));
        String drawer = read(SRC.resolve(Paths.get("screen", "HomeDrawerScreen.java")));

        assertTrue("the row is a 44dp target",
                entry.contains("android:minHeight=\"@dimen/touch_min\""));
        assertTrue("the row names the forest",
                entry.contains("android:text=\"@string/drawer_forest_title\""));
        assertTrue("the row explains itself",
                entry.contains("android:text=\"@string/drawer_forest_sub\""));
        assertTrue("the round badge carries the forest icon",
                entry.contains("android:src=\"@drawable/ic_forest\""));
        assertTrue("the row ends in a chevron",
                entry.contains("android:src=\"@drawable/ic_chevron_right\""));

        int heatmap = drawer.indexOf("R.layout.drawer_heatmap");
        int forest = drawer.indexOf("R.layout.drawer_forest_entry");
        int recent = drawer.indexOf("R.string.drawer_quick_title");
        assertTrue("the entry exists in the drawer", forest >= 0);
        assertTrue("the forest follows the heatmap", heatmap >= 0 && forest > heatmap);
        assertTrue("and stands before recent learning", recent >= 0 && forest < recent);
    }

    @Test
    public void theSceneUsesTokensAndA44dpHitTarget() throws Exception {
        String scene = read(SRC.resolve(Paths.get("forest", "ForestScene.java")));
        String sprite = read(SRC.resolve(Paths.get("forest", "ForestTreeSprite.java")));

        for (String token : new String[]{"R.color.tl_green", "R.color.tl_green_soft",
                "R.color.tl_green_deep", "R.color.tl_surface", "R.color.tl_line",
                "R.color.tl_ink"}) {
            assertTrue("the scene must draw from " + token, scene.contains(token)
                    || sprite.contains(token));
        }
        assertTrue("the hit box never falls under the project's touch minimum",
                scene.contains("R.dimen.touch_min"));
        assertFalse("a random source would make a tree change between frames",
                sprite.contains("new java.util.Random")
                        || sprite.contains("Math.random")
                        || sprite.contains("new Random"));
        assertTrue("the sprite's randomness is seeded", sprite.contains("seed"));
    }

    @Test
    public void theForestReusesTheCanonicalTopicStatsAndStyleReads() throws Exception {
        String wiring = read(SRC.resolve(Paths.get("wiring", "ForestWiring.java")));
        String dao = new String(Files.readAllBytes(Paths.get("..", "data", "src", "main", "java",
                "com", "treelearn", "data", "db", "dao", "TopicDao.java").toAbsolutePath()),
                StandardCharsets.UTF_8);

        assertTrue("the topic collection is the library's own read",
                wiring.contains(".topics()"));
        assertTrue("the segment count comes from the one forest projection",
                wiring.contains("forestCounts()"));
        assertTrue("the style comes from the existing branch→topic read",
                wiring.contains("personaOfBranch"));
        assertTrue("the streak uses the existing aggregation entry",
                wiring.contains("StatsAggregator.aggregate")
                        && wiring.contains("statsRaw()"));
        assertTrue("the data layer owns the one forest statement",
                dao.contains("listForestCounts"));
        assertFalse("the app layer carries no SQL of its own",
                wiring.contains("SELECT "));
    }

    @Test
    public void everyForestSentenceComesFromResources() throws Exception {
        String strings = read(RES.resolve(Paths.get("values", "strings.xml")));

        String[] keys = {"drawer_forest_title", "drawer_forest_sub", "cd_drawer_forest",
            "forest_title", "forest_subtitle", "cd_forest_back", "forest_hint",
            "cd_forest_compass", "forest_loading", "forest_empty_title", "forest_empty_message",
            "forest_empty_action", "forest_error_title", "forest_error_message",
            "forest_error_action", "forest_meta_branches", "forest_meta_archived",
            "forest_foot_tree", "forest_foot_segments", "forest_foot_archived",
            "forest_foot_streak", "forest_foot_separator", "forest_meta_separator",
            "cd_forest_open_topic"};
        for (String key : keys) {
            assertTrue("missing forest copy " + key,
                    strings.contains("<string name=\"" + key + "\""));
        }
    }

    @Test
    public void theForestOnlyNavigatesAndNeverManagesTrees() throws Exception {
        String presenter = read(SRC.resolve(Paths.get("forest", "ForestPresenter.java")));
        String screen = read(SRC.resolve(Paths.get("screen", "ForestScreen.java")));

        assertTrue("back is a screen contract", screen.contains("setOnBackPressed"));
        assertTrue("the pill's arrow reports a tap", screen.contains("onSelectedOpened"));
        assertFalse("the forest never archives", presenter.contains("setArchived")
                || screen.contains("setArchived"));
        assertFalse("the forest never deletes", presenter.contains("delete")
                || screen.contains("delete"));
        assertFalse("the forest never renames", presenter.contains("rename")
                || screen.contains("rename"));
    }
}
