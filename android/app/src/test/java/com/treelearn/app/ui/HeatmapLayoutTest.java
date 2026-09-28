package com.treelearn.app.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * The heatmap as arithmetic (T-2026-09-23-101): the intensity→level ramp, the geometry that
 * measures the strip and grid forms, and the empty-data contract — all provable on the JVM,
 * matching the TreeCanvas precedent of keeping {@code onDraw} a thin painter over tested math.
 *
 * <p>A structural check pins the no-hardcoded-hex rule: every color the View paints must come
 * from an {@code R.color} token, never a literal.
 */
public final class HeatmapLayoutTest {

    private static final Path MAIN = Paths.get("src", "main").toAbsolutePath();

    // ---- intensity → level ramp: 5 levels total (empty + 4 greens) ----

    @Test
    public void zeroAndNegativeIntensityAreTheEmptyLevel() {
        assertEquals(0, HeatmapLayout.levelFor(0f));
        assertEquals(0, HeatmapLayout.levelFor(-0.5f));
    }

    @Test
    public void nanIsTheEmptyLevelNotAMisleadingBucket() {
        assertEquals(0, HeatmapLayout.levelFor(Float.NaN));
    }

    @Test
    public void intensityBucketsIntoFourGreens() {
        assertEquals(1, HeatmapLayout.levelFor(0.1f));
        assertEquals(1, HeatmapLayout.levelFor(0.25f));
        assertEquals(2, HeatmapLayout.levelFor(0.5f));
        assertEquals(3, HeatmapLayout.levelFor(0.75f));
        assertEquals(4, HeatmapLayout.levelFor(1.0f));
    }

    @Test
    public void overdrivenIntensityClampsAtTheTop() {
        assertEquals(4, HeatmapLayout.levelFor(1.5f));
        assertEquals(4, HeatmapLayout.levelFor(Float.MAX_VALUE));
    }

    // ---- data → levels: empty data renders all-empty cells ----

    @Test
    public void nullDataRendersEveryCellEmpty() {
        int[] levels = HeatmapLayout.levelsFor(null, 14);
        assertEquals(14, levels.length);
        for (int level : levels) {
            assertEquals(0, level);
        }
    }

    @Test
    public void blankArrayRendersEveryCellEmpty() {
        int[] levels = HeatmapLayout.levelsFor(new float[0], 14);
        assertEquals(14, levels.length);
        for (int level : levels) {
            assertEquals(0, level);
        }
    }

    @Test
    public void shortDataPadsTrailingCellsWithEmpty() {
        int[] levels = HeatmapLayout.levelsFor(new float[] {0.5f}, 3);
        assertEquals(2, levels[0]);
        assertEquals(0, levels[1]);
        assertEquals(0, levels[2]);
    }

    @Test
    public void dataMapsThroughTheSameRamp() {
        int[] levels = HeatmapLayout.levelsFor(
                new float[] {0.0f, 0.25f, 0.5f, 0.75f, 1.0f}, 5);
        for (int i = 0; i < 5; i++) {
            assertEquals(HeatmapLayout.levelFor(i * 0.25f), levels[i]);
        }
    }

    // ---- blank cells paint the empty placeholder: the grid stays a full rectangle ----

    @Test
    public void blankCellsPaintTheEmptyPlaceholderInsteadOfBeingSkipped() {
        assertEquals(0, HeatmapLayout.colorIndexFor(3, true));
        assertEquals(0, HeatmapLayout.colorIndexFor(4, true));
    }

    @Test
    public void emptyDaysAndBlanksShareThePlaceholderColor() {
        assertEquals(0, HeatmapLayout.colorIndexFor(0, false));
        assertEquals(0, HeatmapLayout.colorIndexFor(0, true));
    }

    @Test
    public void activeDaysKeepTheirGreenBuckets() {
        assertEquals(1, HeatmapLayout.colorIndexFor(1, false));
        assertEquals(2, HeatmapLayout.colorIndexFor(2, false));
        assertEquals(3, HeatmapLayout.colorIndexFor(3, false));
        assertEquals(4, HeatmapLayout.colorIndexFor(4, false));
    }

    // ---- measurement: strip (single row) and grid share the same arithmetic ----

    @Test
    public void stripFormIsAlwaysASingleRow() {
        assertEquals(1, HeatmapLayout.resolveRows(HeatmapLayout.MODE_STRIP, 7));
        assertEquals(1, HeatmapLayout.resolveRows(HeatmapLayout.MODE_STRIP, 0));
    }

    @Test
    public void gridFormKeepsDeclaredRowsAndNeverCollapsesToZero() {
        assertEquals(7, HeatmapLayout.resolveRows(HeatmapLayout.MODE_GRID, 7));
        assertEquals(1, HeatmapLayout.resolveRows(HeatmapLayout.MODE_GRID, 0));
    }

    @Test
    public void columnsNeverCollapseToZero() {
        assertEquals(14, HeatmapLayout.resolveColumns(HeatmapLayout.MODE_STRIP, 14));
        assertEquals(1, HeatmapLayout.resolveColumns(HeatmapLayout.MODE_GRID, 0));
    }

    @Test
    public void fourteenDayStripMeasuresWithGapsBetweenCells() {
        // 14 cells of 12px + 13 gaps of 2px = 194.
        assertEquals(194f, HeatmapLayout.contentWidth(14, 12f, 2f), 0.001f);
        assertEquals(12f, HeatmapLayout.contentHeight(1, 12f, 2f), 0.001f);
    }

    @Test
    public void sevenRowGridMeasuresWithGapsBetweenCells() {
        // 7 rows of 12px + 6 gaps of 2px = 96; 4 columns = 4*12 + 3*2 = 54.
        assertEquals(96f, HeatmapLayout.contentHeight(7, 12f, 2f), 0.001f);
        assertEquals(54f, HeatmapLayout.contentWidth(4, 12f, 2f), 0.001f);
    }

    @Test
    public void cellsFlowDownThenToTheNextColumn() {
        // GitHub-style: a column is one time step; rows run downward inside it.
        assertEquals(0f, HeatmapLayout.cellX(0, 7, 12f, 2f), 0.001f);
        assertEquals(0f, HeatmapLayout.cellY(0, 7, 12f, 2f), 0.001f);
        assertEquals(42f, HeatmapLayout.cellY(3, 7, 12f, 2f), 0.001f);
    }

    @Test
    public void nextColumnStartsOneCellPlusGapFurtherRight() {
        assertEquals(0f, HeatmapLayout.cellX(6, 7, 12f, 2f), 0.001f);
        assertEquals(14f, HeatmapLayout.cellX(7, 7, 12f, 2f), 0.001f);
        assertEquals(0f, HeatmapLayout.cellY(7, 7, 12f, 2f), 0.001f);
    }

    // ---- structural: colors must come from tokens, never hex literals ----

    @Test
    public void theViewPaintsOnlyTokenColorsAndDeclaresItsAttributes() throws Exception {
        String view = read(MAIN.resolve(Paths.get("java", "com", "treelearn", "app", "ui",
                "HeatmapView.java")));
        for (String token : new String[] {"tl_line", "tl_green_faint", "tl_green_soft",
                "tl_green", "tl_green_deep"}) {
            assertTrue("the view must resolve " + token + " from R.color",
                    view.contains("R.color." + token));
        }
        for (String line : view.split("\n")) {
            String code = line.replaceAll("//.*", "").trim();
            assertTrue("no hex literal in view code: " + code,
                    !code.matches(".*\"#[0-9A-Fa-f]{3,8}\".*"));
        }

        String attrs = read(MAIN.resolve(Paths.get("res", "values", "attrs.xml")));
        for (String attr : new String[] {"tl_mode", "tl_rows", "tl_columns", "tl_cellSize",
                "tl_emptyColor"}) {
            assertTrue("attrs.xml must declare " + attr, attrs.contains("name=\"" + attr + "\""));
        }
        assertTrue("strip form must exist", attrs.contains("name=\"strip\""));
        assertTrue("grid form must exist", attrs.contains("name=\"grid\""));
    }

    /**
     * Cross-entry check (failure-library rule): both painting views must route blank judgment
     * through the one shared predicate, and neither may regain the old skip-blank branch that
     * left the calendar ragged (missing leading Monday, missing Saturday/Sunday).
     */
    @Test
    public void bothPaintingViewsRouteBlanksThroughTheSharedJudgment() throws Exception {
        String[] views = new String[] {
                read(MAIN.resolve(Paths.get("java", "com", "treelearn", "app", "ui",
                        "HeatmapView.java"))),
                read(MAIN.resolve(Paths.get("java", "com", "treelearn", "app", "ui",
                        "MonthCalendarView.java"))),
        };
        for (String view : views) {
            assertTrue("the view must paint blanks via HeatmapLayout.colorIndexFor",
                    view.contains("HeatmapLayout.colorIndexFor"));
            assertTrue("the view must not skip blank cells any more",
                    !view.contains("continue;"));
        }
    }

    private static String read(Path path) throws Exception {
        assertTrue("missing source file: " + path, Files.exists(path));
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }
}
