package com.treelearn.core.domain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

/**
 * T-2026-09-28-026 acceptance, pure-logic half: the learner's text marks (方案 V1.1 §2.2–§2.4, §3.1,
 * user-confirmed 2026-09-28).
 *
 * <p>This type is the single judge of the three rules the whole feature stands on — the DDL only
 * backstops the shape of a stored row, and the screen only consumes decisions made here:
 *
 * <ul>
 *   <li><strong>Locator shape</strong> — a mark anchors in exactly one container: a prose segment
 *       carries {@code proseIndex} and -1 elsewhere, a cell carries the cell triple with
 *       {@code proseIndex = -1}. The same grouping the annotations CHECK enforces, judged before a
 *       row can even be built (the two judgements agree on every input, which is the deliberate
 *       bounded overlap the schema already documents for anchors).</li>
 *   <li><strong>Strict overlap</strong> (拍板 2026-09-28) — a duplicate is the <em>exact</em> same
 *       anchor: same locator, same start, same end. A partial overlap and a same-start-different-end
 *       selection are both new marks, not duplicates.</li>
 *   <li><strong>Merging</strong> (拍板 2026-09-28) — a new highlight that intersects <em>or touches</em>
 *       an existing one merges into their union interval; the old rows are absorbed (deleted and
 *       replaced by the union). The touching case follows the confirmed interactive demo
 *       (mergeRanges, 交互 Demo V1.1), not the narrower word "相交" of the prose: two adjacent
 *       highlights read as one, and only a merge keeps a boundary click from belonging to "neither"
 *       span in the user's mind.</li>
 * </ul>
 */
public class AnnotationTest {

    private static final String ID = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa";
    private static final String ID_2 = "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb";
    private static final String ID_3 = "cccccccc-cccc-4ccc-8ccc-cccccccccccc";

    private static final Annotation.Locator PROSE_0 = Annotation.Locator.prose(0);
    private static final Annotation.Locator PROSE_1 = Annotation.Locator.prose(1);
    private static final Annotation.Locator CELL_1_2_3 = Annotation.Locator.cell(1, 2, 3);

    // ------------------------------------------------------------------
    // Locator shape.
    // ------------------------------------------------------------------

    @Test
    public void aProseLocatorCarriesTheProseIndexAndNegativeSentinelsElsewhere() {
        Annotation.Locator locator = Annotation.Locator.prose(2);

        assertEquals(Annotation.Container.PROSE, locator.container);
        assertEquals(2, locator.proseIndex);
        assertEquals("the cell triple of a prose mark is the -1 sentinel, not a leftover value",
                -1, locator.tableIndex);
        assertEquals(-1, locator.rowIndex);
        assertEquals(-1, locator.colIndex);
    }

    @Test
    public void aCellLocatorCarriesTheCellTripleAndAProseSentinel() {
        Annotation.Locator locator = Annotation.Locator.cell(1, 2, 3);

        assertEquals(Annotation.Container.CELL, locator.container);
        assertEquals(-1, locator.proseIndex);
        assertEquals(1, locator.tableIndex);
        assertEquals(2, locator.rowIndex);
        assertEquals(3, locator.colIndex);
    }

    @Test
    public void negativeLocatorComponentsAreRefused() {
        assertThrows(IllegalArgumentException.class, () -> Annotation.Locator.prose(-1));
        assertThrows(IllegalArgumentException.class, () -> Annotation.Locator.cell(-1, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> Annotation.Locator.cell(0, -1, 0));
        assertThrows(IllegalArgumentException.class, () -> Annotation.Locator.cell(0, 0, -1));
    }

    @Test
    public void locatorsCompareByAllFiveComponents() {
        assertEquals(Annotation.Locator.prose(2), Annotation.Locator.prose(2));
        assertNotEquals(Annotation.Locator.prose(2), Annotation.Locator.prose(3));
        assertNotEquals("a prose mark and a cell mark never share an anchor, even with numbers"
                + " that look alike", Annotation.Locator.prose(0), Annotation.Locator.cell(0, 0, 0));
        assertEquals(Annotation.Locator.cell(1, 2, 3), Annotation.Locator.cell(1, 2, 3));
        assertNotEquals(Annotation.Locator.cell(1, 2, 3), Annotation.Locator.cell(1, 2, 4));
    }

    // ------------------------------------------------------------------
    // Spans.
    // ------------------------------------------------------------------

    @Test
    public void anEmptyOrInvertedRangeIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> Annotation.Span.of(5, 5));
        assertThrows(IllegalArgumentException.class, () -> Annotation.Span.of(6, 5));
    }

    @Test
    public void aNegativeStartIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> Annotation.Span.of(-1, 3));
    }

    @Test
    public void spansCompareByBounds() {
        assertEquals(Annotation.Span.of(3, 9), Annotation.Span.of(3, 9));
        assertNotEquals(Annotation.Span.of(3, 9), Annotation.Span.of(3, 10));
    }

    // ------------------------------------------------------------------
    // Factories: the kind/body pairing the DDL also refuses.
    // ------------------------------------------------------------------

    @Test
    public void aHighlightHasAnEmptyBody() {
        Annotation highlight = Annotation.highlight(ID, PROSE_0, 4, 12);

        assertEquals(Annotation.Kind.HIGHLIGHT, highlight.kind);
        assertEquals("a highlight carries no body, so the stored row reads '' and the DDL's"
                + " HIGHLIGHT-half of the body rule agrees", "", highlight.body);
        assertEquals(Annotation.Span.of(4, 12), highlight.span);
    }

    @Test
    public void aNoteCarriesItsBody() {
        Annotation note = Annotation.note(ID, PROSE_0, 4, 12, "查一下这个词的出处");

        assertEquals(Annotation.Kind.NOTE, note.kind);
        assertEquals("查一下这个词的出处", note.body);
    }

    @Test
    public void aNoteWithABlankBodyIsRefused() {
        assertThrows(IllegalArgumentException.class,
                () -> Annotation.note(ID, PROSE_0, 4, 12, ""));
        assertThrows(IllegalArgumentException.class,
                () -> Annotation.note(ID, PROSE_0, 4, 12, "   "));
        assertThrows(IllegalArgumentException.class,
                () -> Annotation.note(ID, PROSE_0, 4, 12, null));
    }

    @Test
    public void anAnnotationNeedsAWellFormedIdAndLocator() {
        assertThrows(IllegalArgumentException.class,
                () -> Annotation.highlight(null, PROSE_0, 4, 12));
        assertThrows(IllegalArgumentException.class,
                () -> Annotation.highlight("", PROSE_0, 4, 12));
        assertThrows(IllegalArgumentException.class,
                () -> Annotation.note(ID, null, 4, 12, "body"));
    }

    @Test
    public void annotationsWithTheSameFieldsAreEqual() {
        assertEquals(Annotation.note(ID, PROSE_0, 4, 12, "同文"),
                Annotation.note(ID, PROSE_0, 4, 12, "同文"));
        assertNotEquals(Annotation.note(ID, PROSE_0, 4, 12, "同文"),
                Annotation.note(ID_2, PROSE_0, 4, 12, "同文"));
        assertNotEquals(Annotation.highlight(ID, PROSE_0, 4, 12),
                Annotation.note(ID, PROSE_0, 4, 12, "同"));
    }

    // ------------------------------------------------------------------
    // Strict overlap (拍板：严格重叠才算).
    // ------------------------------------------------------------------

    @Test
    public void theExactSameAnchorIsAnExactOverlapRegardlessOfKind() {
        Annotation stored = Annotation.note(ID, PROSE_0, 10, 20, "旧批注");
        Annotation fresh = Annotation.note(ID_2, PROSE_0, 10, 20, "新批注");

        assertTrue(stored.isExactOverlap(fresh));
        assertTrue(fresh.isExactOverlap(stored));
    }

    @Test
    public void theSameStartWithADifferentEndIsNotAnExactOverlap() {
        Annotation stored = Annotation.note(ID, PROSE_0, 10, 20, "旧");
        Annotation fresh = Annotation.note(ID_2, PROSE_0, 10, 25, "新");

        assertFalse(stored.isExactOverlap(fresh));
    }

    @Test
    public void aPartialOverlapIsNotAnExactOverlap() {
        Annotation stored = Annotation.note(ID, PROSE_0, 10, 20, "旧");
        Annotation fresh = Annotation.note(ID_2, PROSE_0, 15, 30, "新");

        assertFalse(stored.isExactOverlap(fresh));
    }

    @Test
    public void theSameRangeInAnotherContainerIsNotAnExactOverlap() {
        Annotation stored = Annotation.note(ID, PROSE_0, 10, 20, "旧");
        Annotation fresh = Annotation.note(ID_2, PROSE_1, 10, 20, "新");

        assertFalse(stored.isExactOverlap(fresh));
    }

    // ------------------------------------------------------------------
    // Merging (拍板：相交合并为并区间；相接同合并，与已验收 Demo 的 mergeRanges 一致).
    // ------------------------------------------------------------------

    @Test
    public void aSelectionThatTouchesNothingStandsAlone() {
        Annotation.Merge merge = Annotation.mergeHighlights(
                Collections.singletonList(Annotation.highlight(ID, PROSE_0, 0, 5)), PROSE_0, 20, 30);

        assertEquals(Annotation.Span.of(20, 30), merge.span);
        assertTrue("nothing was absorbed, so nothing is deleted", merge.absorbedIds.isEmpty());
    }

    @Test
    public void anIntersectingSelectionAbsorbsTheOldRow() {
        Annotation.Merge merge = Annotation.mergeHighlights(
                Collections.singletonList(Annotation.highlight(ID, PROSE_0, 10, 20)), PROSE_0,
                15, 25);

        assertEquals("the union of the old and the new interval", Annotation.Span.of(10, 25),
                merge.span);
        assertEquals(Collections.singletonList(ID), merge.absorbedIds);
    }

    @Test
    public void aSelectionBridgingTwoHighlightsAbsorbsBoth() {
        List<Annotation> existing = Arrays.asList(
                Annotation.highlight(ID, PROSE_0, 0, 10),
                Annotation.highlight(ID_2, PROSE_0, 20, 30));
        Annotation.Merge merge = Annotation.mergeHighlights(existing, PROSE_0, 5, 25);

        assertEquals("both highlights connect through the new selection, so the union is one"
                + " interval", Annotation.Span.of(0, 30), merge.span);
        assertEquals(Arrays.asList(ID, ID_2), merge.absorbedIds);
    }

    @Test
    public void aSelectionInsideAnExistingHighlightLeavesTheSpanUnchanged() {
        Annotation.Merge merge = Annotation.mergeHighlights(
                Collections.singletonList(Annotation.highlight(ID, PROSE_0, 0, 30)), PROSE_0,
                10, 12);

        assertEquals("视觉无变化：并区间就是原区间（方案 §2.2 边界推论）", Annotation.Span.of(0, 30),
                merge.span);
        assertEquals("the old row is still absorbed and rewritten, so the stored set stays the"
                + " union", Collections.singletonList(ID), merge.absorbedIds);
    }

    @Test
    public void touchingHighlightsMergeToo() {
        Annotation.Merge merge = Annotation.mergeHighlights(
                Collections.singletonList(Annotation.highlight(ID, PROSE_0, 0, 5)), PROSE_0, 5, 10);

        assertEquals("the confirmed demo merges a touch: two adjacent highlights read as one",
                Annotation.Span.of(0, 10), merge.span);
        assertEquals(Collections.singletonList(ID), merge.absorbedIds);
    }

    @Test
    public void anExactReHighlightOfTheSameRangeAbsorbsAndRewritesIt() {
        Annotation.Merge merge = Annotation.mergeHighlights(
                Collections.singletonList(Annotation.highlight(ID, PROSE_0, 10, 20)), PROSE_0,
                10, 20);

        assertEquals(Annotation.Span.of(10, 20), merge.span);
        assertEquals(Collections.singletonList(ID), merge.absorbedIds);
    }

    @Test
    public void highlightsOfAnotherContainerAreNeverAbsorbed() {
        List<Annotation> existing = Arrays.asList(
                Annotation.highlight(ID, PROSE_1, 10, 20),
                Annotation.highlight(ID_2, CELL_1_2_3, 10, 20));
        Annotation.Merge merge = Annotation.mergeHighlights(existing, PROSE_0, 10, 20);

        assertEquals(Annotation.Span.of(10, 20), merge.span);
        assertTrue("another prose segment and a cell are different anchors; merging must not"
                + " reach across containers", merge.absorbedIds.isEmpty());
    }

    @Test
    public void anEmptyHistoryYieldsTheSelectionAlone() {
        Annotation.Merge merge = Annotation.mergeHighlights(
                Collections.<Annotation>emptyList(), PROSE_0, 3, 8);

        assertEquals(Annotation.Span.of(3, 8), merge.span);
        assertTrue(merge.absorbedIds.isEmpty());
    }

    @Test
    public void anInvertedOrEmptySelectionIsRefused() {
        assertThrows(IllegalArgumentException.class,
                () -> Annotation.mergeHighlights(Collections.<Annotation>emptyList(), PROSE_0,
                        5, 5));
        assertThrows(IllegalArgumentException.class,
                () -> Annotation.mergeHighlights(Collections.<Annotation>emptyList(), PROSE_0,
                        6, 5));
    }
}
