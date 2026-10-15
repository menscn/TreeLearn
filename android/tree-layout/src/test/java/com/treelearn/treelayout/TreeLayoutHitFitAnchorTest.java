package com.treelearn.treelayout;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

/**
 * Hit testing, viewport fitting and the stable-anchor delta that keeps a node's screen position
 * after the tree is folded, unfolded or re-weighted.
 */
public class TreeLayoutHitFitAnchorTest {

    private static final float EPS = 0.01f;

    private static LayoutResult layout(TreeSnapshot snapshot, String current, Density density) {
        return TreeLayout.compute(snapshot, current, density, Fixtures.PHONE);
    }

    @Test
    public void everyVisibleNodeIsHittableAtItsOwnAnchor() {
        for (Density density : new Density[] {Density.FAR, Density.MID, Density.NEAR}) {
            LayoutResult result = layout(Fixtures.fixtureA(), "a1", density);
            assertEquals(4, result.branches().size());
            for (BranchGeometry branch : result.branches()) {
                Pt anchor = result.anchorOf(branch.branchId);
                assertNotNull(branch.branchId, anchor);
                HitRegion hit = result.hitTest(anchor.x, anchor.y);
                assertNotNull(branch.branchId + " at " + density, hit);
                assertEquals(branch.branchId, hit.branchId);
                assertTrue(branch.branchId, hit.width >= 44f);
                assertTrue(branch.branchId, hit.height >= 44f);
                assertTrue(branch.branchId, hit.contains(anchor.x, anchor.y));
            }
        }
    }

    @Test
    public void screenPointsRoundTripThroughTheFitTransform() {
        LayoutResult result = layout(Fixtures.fixtureA(), "a1", Density.MID);
        for (ViewportConstraints viewport : new ViewportConstraints[] {Fixtures.PHONE, Fixtures.TABLET}) {
            ViewTransform transform = result.fit(viewport);
            for (BranchGeometry branch : result.branches()) {
                Pt anchor = result.anchorOf(branch.branchId);
                Pt screen = transform.toScreen(anchor.x, anchor.y);
                Pt back = transform.toTree(screen.x, screen.y);
                assertEquals(branch.branchId, anchor.x, back.x, 0.5f);
                assertEquals(branch.branchId, anchor.y, back.y, 0.5f);
                HitRegion hit = result.hitTest(back.x, back.y);
                assertNotNull(branch.branchId, hit);
                assertEquals(branch.branchId, hit.branchId);
            }
        }
    }

    @Test
    public void hitTestAtALabelCentreReturnsTheLabeledBranch() {
        LayoutResult result = layout(Fixtures.wideLeaves(12, 1), "w6", Density.MID);
        assertTrue(result.labels().size() > 0);
        for (LabelGeometry label : result.labels()) {
            HitRegion hit = result.hitTest(label.x + label.width / 2f, label.y + label.height / 2f);
            assertNotNull(label.branchId, hit);
            assertEquals(label.branchId, hit.branchId);
            assertEquals(HitRegion.Source.LABEL, hit.source);
        }
    }

    @Test
    public void hitTestOutsideEveryRegionReturnsNull() {
        LayoutResult result = layout(Fixtures.fixtureA(), "a1", Density.MID);
        Bounds bounds = result.bounds();
        assertNull(result.hitTest(bounds.maxX + 10_000f, bounds.maxY + 10_000f));
        assertNull(result.hitTest(bounds.minX - 10_000f, bounds.minY - 10_000f));
        assertNull(TreeLayout.compute(TreeSnapshot.empty(), null, Density.MID, Fixtures.PHONE)
                .hitTest(0f, 0f));
    }

    @Test
    public void fitKeepsTheTreeInsideTheViewportAndAlignsTheBottom() {
        TreeSnapshot[] snapshots = {
                Fixtures.fixtureA(), Fixtures.twoLeaves(false), Fixtures.chain(50), Fixtures.balanced(4, 4)};
        for (TreeSnapshot snapshot : snapshots) {
            LayoutResult result = layout(snapshot, Fixtures.lastId(snapshot), Density.MID);
            for (ViewportConstraints viewport : new ViewportConstraints[] {Fixtures.PHONE, Fixtures.TABLET}) {
                ViewTransform transform = result.fit(viewport);
                Bounds bounds = result.bounds();
                String where = snapshot.rootId() + " " + viewport.width + "x" + viewport.height;
                assertTrue(where, Float.isFinite(transform.scale));
                assertTrue(where, Float.isFinite(transform.translateX));
                assertTrue(where, Float.isFinite(transform.translateY));
                assertTrue(where, transform.scale >= TreeLayout.MIN_SCALE - EPS);
                assertTrue(where, transform.scale <= TreeLayout.MAX_SCALE + EPS);
                float usableWidth = viewport.width - 2f * viewport.padding;
                float usableHeight = viewport.height - 2f * viewport.padding;
                float expected = Math.min(usableWidth / bounds.width(), usableHeight / bounds.height());
                expected = Math.min(TreeLayout.MAX_SCALE, Math.max(TreeLayout.MIN_SCALE, expected));
                assertEquals(where, expected, transform.scale, EPS);
                if (expected < TreeLayout.MAX_SCALE && expected > TreeLayout.MIN_SCALE) {
                    assertTrue(where, bounds.width() * transform.scale <= usableWidth + EPS);
                    assertTrue(where, bounds.height() * transform.scale <= usableHeight + EPS);
                }
                assertEquals(where, viewport.height - viewport.padding,
                        bounds.maxY * transform.scale + transform.translateY, EPS);
                assertEquals(where, viewport.width / 2f,
                        (bounds.minX + bounds.maxX) / 2f * transform.scale + transform.translateX, EPS);
            }
        }
    }

    @Test
    public void degenerateViewportsStayFinite() {
        LayoutResult result = layout(Fixtures.chain(50), "n49", Density.MID);
        for (ViewportConstraints viewport : new ViewportConstraints[] {
                ViewportConstraints.of(0f, 0f),
                ViewportConstraints.of(1f, 1f),
                ViewportConstraints.of(0f, 700f),
                ViewportConstraints.of(412f, 0f),
                ViewportConstraints.of(10_000_000f, 10_000_000f)}) {
            ViewTransform transform = result.fit(viewport);
            assertTrue(String.valueOf(viewport.width), Float.isFinite(transform.scale));
            assertTrue(String.valueOf(viewport.width), Float.isFinite(transform.translateX));
            assertTrue(String.valueOf(viewport.width), Float.isFinite(transform.translateY));
            assertTrue(String.valueOf(viewport.width), transform.scale >= TreeLayout.MIN_SCALE);
            assertTrue(String.valueOf(viewport.width), transform.scale <= TreeLayout.MAX_SCALE);
        }
        ViewTransform huge = result.fit(ViewportConstraints.of(10_000_000f, 10_000_000f));
        assertEquals(TreeLayout.MAX_SCALE, huge.scale, EPS);
    }

    @Test
    public void viewportRefusesNegativeOrZeroTouchTarget() {
        try {
            ViewportConstraints.of(-10f, -10f);
            fail("a negative viewport must be refused instead of quietly normalised");
        } catch (IllegalArgumentException expected) {
            assertNotNull(expected.getMessage());
        }
        try {
            new ViewportConstraints(412f, 700f, 0f, 26f, 44f);
            fail("a zero touch target would allow unhittable nodes");
        } catch (IllegalArgumentException expected) {
            assertNotNull(expected.getMessage());
        }
    }

    @Test
    public void viewTransformRefusesANonPositiveScale() {
        for (float scale : new float[] {0f, -1f, Float.NaN}) {
            try {
                new ViewTransform(scale, 0f, 0f);
                fail("scale " + scale + " must be refused: toTree would divide by it");
            } catch (IllegalArgumentException expected) {
                assertNotNull(expected.getMessage());
            }
        }
        ViewTransform identity = ViewTransform.identity();
        Pt point = identity.toScreen(3f, 4f);
        assertEquals(3f, point.x, EPS);
        assertEquals(4f, point.y, EPS);
    }

    @Test
    public void collapseNeverMovesTheSurvivingNodes() {
        LayoutResult expanded = layout(Fixtures.fixtureA(false), "a1", Density.MID);
        LayoutResult collapsed = layout(Fixtures.fixtureA(true), "a1", Density.MID);
        assertNotNull(expanded.branch("b1"));
        assertNull(collapsed.branch("b1"));
        assertEquals(0, expanded.branch("b").hiddenDescendants);
        assertEquals(2, collapsed.branch("b").hiddenDescendants);
        for (String id : new String[] {"r", "a", "a1", "b"}) {
            Pt delta = expanded.anchorDeltaTo(collapsed, id);
            assertNotNull(id, delta);
            assertEquals(id, 0f, delta.x, 0f);
            assertEquals(id, 0f, delta.y, 0f);
        }
        assertNull(expanded.anchorDeltaTo(collapsed, "b1"));
        assertNull(collapsed.anchorDeltaTo(expanded, "b2"));
    }

    @Test
    public void archivingASiblingReportsTheExactAnchorShift() {
        LayoutResult before = layout(Fixtures.twoLeaves(false), "p", Density.MID);
        LayoutResult after = layout(Fixtures.twoLeaves(true), "p", Density.MID);
        // Two equal leaves fan by 16 degrees, so the first one leans by half the fan while the
        // second stays on the axis; once q is archived p is the only child and both go to zero.
        assertEquals(-13.69463f, before.branch("p").tipX, EPS);
        assertEquals(-191.04238f, before.branch("p").tipY, EPS);
        assertEquals(0f, after.branch("p").tipX, EPS);
        assertEquals(-218.4f, after.branch("p").tipY, EPS);
        Pt delta = before.anchorDeltaTo(after, "p");
        assertNotNull(delta);
        assertEquals(-16.06058f, delta.x, EPS);
        assertEquals(27.52306f, delta.y, EPS);
        assertEquals(before.anchorOf("p").x - after.anchorOf("p").x, delta.x, EPS);
        assertEquals(before.anchorOf("p").y - after.anchorOf("p").y, delta.y, EPS);
        ViewTransform transform = before.fit(Fixtures.PHONE);
        Pt anchorBefore = before.anchorOf("p");
        Pt anchorAfter = after.anchorOf("p");
        float panX = transform.translateX + delta.x * transform.scale;
        float panY = transform.translateY + delta.y * transform.scale;
        assertEquals(anchorBefore.x * transform.scale + transform.translateX,
                anchorAfter.x * transform.scale + panX, EPS);
        assertEquals(anchorBefore.y * transform.scale + transform.translateY,
                anchorAfter.y * transform.scale + panY, EPS);
    }

    @Test
    public void boundsCoverEveryTipBaseAndLabel() {
        for (Density density : new Density[] {Density.MID, Density.NEAR}) {
            LayoutResult result = layout(Fixtures.fixtureA(), "a1", density);
            Bounds bounds = result.bounds();
            float maxBaseY = -Float.MAX_VALUE;
            for (BranchGeometry branch : result.branches()) {
                assertTrue(branch.branchId, bounds.contains(branch.tipX, branch.tipY));
                assertTrue(branch.branchId, bounds.contains(branch.baseX, branch.baseY));
                maxBaseY = Math.max(maxBaseY, branch.baseY);
            }
            assertTrue(bounds.width() > 0f);
            assertTrue(bounds.height() > 0f);
            assertEquals(maxBaseY + 44f, bounds.maxY, EPS);
            for (LabelGeometry label : result.labels()) {
                assertTrue(label.branchId, bounds.contains(label.x, label.y));
                assertTrue(label.branchId, bounds.contains(label.x + label.width, label.y + label.height));
                assertTrue(label.branchId, bounds.contains(label.leaderX, label.leaderY));
            }
        }
    }
}
