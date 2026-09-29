package com.treelearn.app.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.treelearn.core.domain.Annotation;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * The highlight flow's brain, decoupled from any {@code android.text} type so the JVM can judge it
 * (方案 V1.1 §2.2–§2.3, user-confirmed 2026-09-28).
 *
 * <p>Three decisions decide every tap on 高亮, and they must agree with the stored marks before a
 * span is ever drawn or a row ever written:
 *
 * <ul>
 *     <li><strong>Cancel</strong> — the selection sits exactly on an existing highlight; the menu's
 *         third cell has already flipped to 带斜杠记号笔, so a tap removes that row.</li>
 *     <li><strong>Merge</strong> — the selection intersects or touches existing highlights of the
 *         same anchor; the absorbed rows die and the union row is written in one transaction.</li>
 *     <li><strong>Create</strong> — nothing connects; the selection becomes its own row.</li>
 * </ul>
 *
 * <p>The cancelling rule answers 「点在已有高亮上」 only, so a note sharing the very same span does
 * not flip the menu - merging is a highlight-only rule (the {@code Annotation.mergeHighlights}
 * contract T-A locked), and a note's body belongs to the range the learner attached it to. These
 * tests pin the judgement layer of {@link Highlighter}; the span flush it feeds is a thin
 * {@code android.text} shell covered on-device (V-R2) and by real-device evidence.
 */
public final class HighlighterTest {

    private static final Annotation.Locator PROSE_0 = Annotation.Locator.prose(0);

    /** Same deterministic shape the instrumented suite uses: one valid UUID per seed. */
    private static String uuid(String seed) {
        return "00000000-0000-4000-8000-"
                + String.format("%12s", Integer.toHexString(seed.hashCode())).replace(' ', '0');
    }

    private static Annotation highlight(String seed, Annotation.Locator locator, int start,
            int end) {
        return Annotation.highlight(uuid(seed), locator, start, end);
    }

    private static Annotation note(String seed, Annotation.Locator locator, int start, int end) {
        return Annotation.note(uuid(seed), locator, start, end, "a learner's note");
    }

    @Test
    public void anExactMatchOnAnExistingHighlightPlansACancel() {
        List<Annotation> existing = Collections.singletonList(
                highlight("h1", PROSE_0, 10, 20));

        Highlighter.Plan plan = Highlighter.plan(existing, PROSE_0, 10, 20);

        assertEquals(Highlighter.Kind.CANCEL, plan.kind);
        assertEquals("the exact highlight the learner tapped on is the one that dies",
                uuid("h1"), plan.cancelId);
    }

    @Test
    public void anExactMatchOnANoteIsNotACancel() {
        // Strict-overlap ignores kind (the Annotation contract), but the cancel rule answers
        // 「点在已有高亮上」 specifically: a note under the very same span leaves the menu as plain
        // 记号笔, and the tap writes a highlight row of its own.
        List<Annotation> existing = Collections.singletonList(note("n1", PROSE_0, 10, 20));

        Highlighter.Plan plan = Highlighter.plan(existing, PROSE_0, 10, 20);

        assertEquals(Highlighter.Kind.WRITE, plan.kind);
        assertTrue("a note is never absorbed by a highlight merge", plan.merge.absorbedIds.isEmpty());
        assertEquals(10, plan.merge.span.start);
        assertEquals(20, plan.merge.span.end);
    }

    @Test
    public void aPartialOverlapMergesIntoTheUnionAndAbsorbsTheOldRow() {
        List<Annotation> existing = Collections.singletonList(
                highlight("h1", PROSE_0, 10, 20));

        Highlighter.Plan plan = Highlighter.plan(existing, PROSE_0, 5, 15);

        assertEquals(Highlighter.Kind.WRITE, plan.kind);
        assertEquals(5, plan.merge.span.start);
        assertEquals(20, plan.merge.span.end);
        assertEquals(Collections.singletonList(uuid("h1")), plan.merge.absorbedIds);
    }

    @Test
    public void aTouchingSelectionAlsoMerges() {
        // The confirmed interactive demo merges touching intervals, not merely intersecting ones:
        // two adjacent highlights read as one, and only a union keeps a boundary click from
        // belonging to neither span.
        List<Annotation> existing = Collections.singletonList(
                highlight("h1", PROSE_0, 0, 5));

        Highlighter.Plan plan = Highlighter.plan(existing, PROSE_0, 5, 10);

        assertEquals(Highlighter.Kind.WRITE, plan.kind);
        assertEquals(0, plan.merge.span.start);
        assertEquals(10, plan.merge.span.end);
        assertEquals(Collections.singletonList(uuid("h1")), plan.merge.absorbedIds);
    }

    @Test
    public void aSelectionBridgingTwoHighlightsAbsorbsBoth() {
        List<Annotation> existing = Arrays.asList(
                highlight("h1", PROSE_0, 0, 5),
                highlight("h2", PROSE_0, 10, 15));

        Highlighter.Plan plan = Highlighter.plan(existing, PROSE_0, 3, 12);

        assertEquals(Highlighter.Kind.WRITE, plan.kind);
        assertEquals(0, plan.merge.span.start);
        assertEquals(15, plan.merge.span.end);
        assertEquals(2, plan.merge.absorbedIds.size());
        assertTrue(plan.merge.absorbedIds.contains(uuid("h1")));
        assertTrue(plan.merge.absorbedIds.contains(uuid("h2")));
    }

    @Test
    public void aFreshSelectionAwayFromEveryMarkIsACreate() {
        List<Annotation> existing = Collections.singletonList(
                highlight("h1", PROSE_0, 0, 5));

        Highlighter.Plan plan = Highlighter.plan(existing, PROSE_0, 100, 110);

        assertEquals(Highlighter.Kind.WRITE, plan.kind);
        assertTrue(plan.merge.absorbedIds.isEmpty());
        assertEquals(100, plan.merge.span.start);
        assertEquals(110, plan.merge.span.end);
    }

    @Test
    public void anotherAnchorIsOutOfScopeForBothCancelAndMerge() {
        // Same span, different prose segment: a different anchor - no cancel, no merge, no reading
        // across containers.
        List<Annotation> existing = Collections.singletonList(
                highlight("h1", Annotation.Locator.prose(0), 10, 20));

        Highlighter.Plan plan = Highlighter.plan(existing, Annotation.Locator.prose(1), 10, 20);

        assertEquals(Highlighter.Kind.WRITE, plan.kind);
        assertTrue(plan.merge.absorbedIds.isEmpty());
    }

    @Test
    public void anAnswerWithoutMarksWritesTheSelectionAsItsOwnRow() {
        Highlighter.Plan plan = Highlighter.plan(Collections.<Annotation>emptyList(), PROSE_0, 0, 8);

        assertEquals(Highlighter.Kind.WRITE, plan.kind);
        assertTrue(plan.merge.absorbedIds.isEmpty());
        assertEquals(0, plan.merge.span.start);
        assertEquals(8, plan.merge.span.end);
    }

    @Test
    public void optimisticWriteProjectsTheUnionBeforeStorageReturns() {
        List<Annotation> existing = Collections.singletonList(
                highlight("h1", PROSE_0, 10, 20));

        List<Annotation> projected = Highlighter.optimisticMarks(existing, PROSE_0, 5, 15);

        assertEquals(1, projected.size());
        assertEquals(5, projected.get(0).span.start);
        assertEquals(20, projected.get(0).span.end);
        assertEquals(Annotation.Kind.HIGHLIGHT, projected.get(0).kind);
    }

    @Test
    public void optimisticCancelRemovesTheExactHighlightBeforeStorageReturns() {
        List<Annotation> existing = Collections.singletonList(
                highlight("h1", PROSE_0, 10, 20));

        List<Annotation> projected = Highlighter.optimisticMarks(existing, PROSE_0, 10, 20);

        assertTrue(projected.isEmpty());
    }
}
