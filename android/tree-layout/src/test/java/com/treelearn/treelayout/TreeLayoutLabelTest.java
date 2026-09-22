package com.treelearn.treelayout;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

/**
 * Label placement tests: the three density tiers, the canopy bar budget and the promise that a
 * placed label never overlaps another label and never covers a leaf.
 */
public class TreeLayoutLabelTest {

    private static final float EPS = 0.01f;

    private static LayoutResult layout(TreeSnapshot snapshot, String current, Density density) {
        return TreeLayout.compute(snapshot, current, density, Fixtures.PHONE);
    }

    private static LabelGeometry labelOf(LayoutResult result, String branchId) {
        for (LabelGeometry label : result.labels()) {
            if (label.branchId.equals(branchId)) {
                return label;
            }
        }
        return null;
    }

    private static List<BranchGeometry> leaves(LayoutResult result) {
        List<BranchGeometry> leaves = new ArrayList<>();
        for (BranchGeometry branch : result.branches()) {
            if (branch.kind == BranchGeometry.Kind.LEAF) {
                leaves.add(branch);
            }
        }
        return leaves;
    }

    private static float[] labelBox(LabelGeometry label) {
        return new float[] {label.x, label.y, label.x + label.width, label.y + label.height};
    }

    @Test
    public void densityIsDerivedFromTheZoomScale() {
        assertEquals(Density.FAR, Density.forScale(0.35f));
        assertEquals(Density.FAR, Density.forScale(0.71f));
        assertEquals(Density.MID, Density.forScale(0.72f));
        assertEquals(Density.MID, Density.forScale(1.04f));
        assertEquals(Density.NEAR, Density.forScale(1.05f));
        assertEquals(Density.NEAR, Density.forScale(1.6f));
        assertFalse(Density.FAR.labels);
        assertTrue(Density.MID.labels);
        assertFalse(Density.MID.summaries);
        assertTrue(Density.NEAR.summaries);
    }

    @Test
    public void farDensityPlacesNoLabelsAndKeepsEveryNodeHittable() {
        LayoutResult result = layout(Fixtures.fixtureA(), "a1", Density.FAR);
        assertTrue(result.labels().isEmpty());
        for (BranchGeometry branch : result.branches()) {
            Pt anchor = result.anchorOf(branch.branchId);
            assertNotNull(branch.branchId, anchor);
            HitRegion hit = result.hitTest(anchor.x, anchor.y);
            assertNotNull(branch.branchId, hit);
            assertEquals(branch.branchId, hit.branchId);
            assertTrue(branch.branchId, hit.width >= 44f);
            assertTrue(branch.branchId, hit.height >= 44f);
        }
    }

    @Test
    public void midDensityPlacesOneBarLabelPerLeafAtTheMeasuredPosition() {
        LayoutResult result = layout(Fixtures.fixtureA(), "a1", Density.MID);
        assertEquals(1, result.labels().size());
        LabelGeometry label = labelOf(result, "a1");
        assertNotNull(label);
        BranchGeometry leaf = result.branch("a1");
        assertEquals(136f, label.width, EPS);
        assertEquals(24f, label.height, EPS);
        assertEquals(0, label.row);
        assertFalse(label.showSummary);
        assertEquals(Fixtures.leafTipX(leaf), label.leaderX, EPS);
        assertEquals(Fixtures.leafTipY(leaf), label.leaderY, EPS);
        assertEquals(label.leaderX - 68f, label.x, EPS);
        assertEquals(label.leaderY - 14f - 24f, label.y, EPS);
    }

    @Test
    public void nearDensityAddsTheSummaryLineOnlyWhenTheBranchHasOne() {
        LabelGeometry withSummary = labelOf(layout(Fixtures.fixtureA(), "a1", Density.NEAR), "a1");
        assertNotNull(withSummary);
        assertTrue(withSummary.showSummary);
        assertEquals(43f, withSummary.height, EPS);
        assertEquals(withSummary.leaderY - 14f - 43f, withSummary.y, EPS);
        LabelGeometry withoutSummary =
                labelOf(layout(Fixtures.fixtureAWithoutSummary(), "a1", Density.NEAR), "a1");
        assertNotNull(withoutSummary);
        assertFalse(withoutSummary.showSummary);
        assertEquals(24f, withoutSummary.height, EPS);
    }

    @Test
    public void barLabelsNeverOverlapAndNeverCoverALeaf() {
        TreeSnapshot balanced = Fixtures.balanced(4, 4);
        TreeSnapshot[] snapshots = {Fixtures.fixtureA(), balanced, Fixtures.wideLeaves(12, 1)};
        for (TreeSnapshot snapshot : snapshots) {
            for (Density density : new Density[] {Density.MID, Density.NEAR}) {
                LayoutResult result = layout(snapshot, Fixtures.lastId(snapshot), density);
                assertFalse(result.labels().isEmpty());
                String where = snapshot.rootId() + "/" + density;
                for (int i = 0; i < result.labels().size(); i++) {
                    LabelGeometry label = result.labels().get(i);
                    assertTrue(where + " " + label.branchId, label.row >= 0);
                    assertTrue(where + " " + label.branchId, label.row < TreeLayout.LABEL_MAX_ROWS);
                    float[] box = labelBox(label);
                    for (int j = i + 1; j < result.labels().size(); j++) {
                        assertFalse(where + " " + label.branchId + " overlaps "
                                + result.labels().get(j).branchId,
                                Fixtures.overlaps(box, labelBox(result.labels().get(j))));
                    }
                    for (BranchGeometry leaf : leaves(result)) {
                        assertFalse(where + " " + label.branchId + " covers leaf " + leaf.branchId,
                                Fixtures.overlaps(box, Fixtures.leafBox(leaf)));
                    }
                }
                float crownTop = Float.MAX_VALUE;
                for (BranchGeometry leaf : leaves(result)) {
                    crownTop = Math.min(crownTop, Fixtures.leafTipY(leaf));
                }
                for (LabelGeometry label : result.labels()) {
                    assertTrue(where, label.y + label.height <= crownTop - 14f + EPS);
                }
            }
        }
    }

    @Test
    public void crowdedBarOmitsLabelsButKeepsEveryNodeHittable() {
        TreeSnapshot snapshot = Fixtures.wideLeaves(12, 1);
        LayoutResult result = layout(snapshot, "w6", Density.MID);
        assertTrue(result.labels().size() < 12);
        assertFalse(result.labels().isEmpty());
        int unlabeled = 0;
        for (BranchGeometry leaf : leaves(result)) {
            if (labelOf(result, leaf.branchId) == null) {
                unlabeled++;
            }
            Pt anchor = result.anchorOf(leaf.branchId);
            HitRegion hit = result.hitTest(anchor.x, anchor.y);
            assertNotNull(leaf.branchId, hit);
            assertEquals(leaf.branchId, hit.branchId);
            assertTrue(leaf.branchId, hit.width >= 44f && hit.height >= 44f);
        }
        assertTrue("at least one leaf must be left without a plate", unlabeled > 0);
    }

    @Test
    public void currentBranchLeafTakesTheFirstRow() {
        TreeSnapshot snapshot = Fixtures.wideLeaves(12, 1);
        LayoutResult rightmost = layout(snapshot, "w11", Density.MID);
        LabelGeometry pinned = labelOf(rightmost, "w11");
        assertNotNull(pinned);
        assertEquals(0, pinned.row);
        LayoutResult leftmost = layout(snapshot, "w0", Density.MID);
        LabelGeometry other = labelOf(leftmost, "w0");
        assertNotNull(other);
        assertEquals(0, other.row);
        assertTrue(rightmost.labels().size() < 12);
        assertTrue(leftmost.labels().size() < 12);
    }

    @Test
    public void canopyBarSitsEntirelyAboveTheCrownAndAboveEveryLeafBox() {
        TreeSnapshot snapshot = Fixtures.balanced(4, 4);
        LayoutResult result = layout(snapshot, Fixtures.lastId(snapshot), Density.MID);
        assertFalse(result.labels().isEmpty());
        float crownTop = Float.MAX_VALUE;
        float minLeafBoxTop = Float.MAX_VALUE;
        for (BranchGeometry leaf : leaves(result)) {
            crownTop = Math.min(crownTop, Fixtures.leafTipY(leaf));
            minLeafBoxTop = Math.min(minLeafBoxTop, Fixtures.leafBox(leaf)[1]);
        }
        float barBottom = -Float.MAX_VALUE;
        for (LabelGeometry label : result.labels()) {
            barBottom = Math.max(barBottom, label.y + label.height);
        }
        assertTrue("bar bottom " + barBottom + " vs crown " + crownTop,
                barBottom <= crownTop - 14f + EPS);
        assertTrue("bar bottom " + barBottom + " vs leaf box top " + minLeafBoxTop,
                barBottom <= minLeafBoxTop - 6f + EPS);
    }
}
