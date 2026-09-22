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
 * Golden geometry and invariant tests for the pure-JVM tree layout.
 *
 * <p>The expected numbers are transcribed from the prototype's measured constants
 * ({@code CV_SEG_BASE 120}, {@code CV_SEG_STEP 36}, {@code CV_SEG_MAX 300}, {@code CV_DEPTH_DECAY
 * 0.82}, {@code CV_FAN_MAX 46}, {@code CV_FAN_STEP 16}, {@code CV_SPROUT 0.22}) rather than read
 * from {@link TreeLayout}'s public constants, so the test is an independent encoding of the spec.
 */
public class TreeLayoutGeometryTest {

    private static final float EPS = 0.01f;

    private static LayoutResult layout(TreeSnapshot snapshot, String current) {
        return TreeLayout.compute(snapshot, current, Density.MID, Fixtures.PHONE);
    }

    private static List<String> ids(LayoutResult result) {
        List<String> ids = new ArrayList<>();
        for (BranchGeometry branch : result.branches()) {
            ids.add(branch.branchId);
        }
        return ids;
    }

    @Test
    public void rootIsATrunkGrowingStraightUpWithRoundDerivedLength() {
        BranchGeometry root = layout(Fixtures.fixtureA(), "a1").branch("r");
        assertNotNull(root);
        assertEquals(BranchGeometry.Kind.LIMB, root.kind);
        assertEquals(0, root.depth);
        assertEquals(0f, root.baseX, EPS);
        assertEquals(0f, root.baseY, EPS);
        assertEquals(0f, root.direction, EPS);
        assertEquals(120f, root.length, EPS);
        assertEquals(0f, root.tipX, EPS);
        assertEquals(-120f, root.tipY, EPS);
        // Three leaves hang under the trunk: a1, plus b1 and b2 below the collapsed bud. Collapsing
        // hides those two but must not thin the wood, exactly as the prototype's leavesUnder does.
        float expectedWidth = (2f + 2.6f * (float) Math.sqrt(3f)) / 1f;
        assertEquals(expectedWidth, root.baseWidth, EPS);
        assertEquals(expectedWidth * 0.66f, root.tipWidth, EPS);
    }

    @Test
    public void segmentLengthUsesRoundsDepthDecayAndTheCap() {
        TreeSnapshot snapshot = Fixtures.snapshot("r",
                Fixtures.n("r", null),
                Fixtures.n("a", "r").rounds(1),
                Fixtures.n("a1", "a"));
        LayoutResult chain = layout(snapshot, "a");
        assertEquals(127.92f, chain.branch("a").length, EPS);
        assertEquals(0f, chain.branch("a").tipX, EPS);
        assertEquals(-247.92f, chain.branch("a").tipY, EPS);
        assertEquals(80.688f, chain.branch("a1").length, EPS);
        assertEquals(-328.608f, chain.branch("a1").tipY, EPS);
        assertEquals(2, chain.branch("a1").depth);
        assertEquals(0, chain.branch("a1").rounds);
        TreeSnapshot capped = Fixtures.snapshot("r",
                Fixtures.n("r", null),
                Fixtures.n("b", "r").rounds(20));
        LayoutResult capResult = layout(capped, "b");
        assertEquals(300f, capResult.branch("b").length, EPS);
        assertEquals(20, capResult.branch("b").rounds);
        assertEquals(1, capResult.branch("b").depth);
        assertEquals(-420f, capResult.branch("b").tipY, EPS);
    }

    @Test
    public void siblingsFanOutByLeafWeightAndSproutAlongTheirParent() {
        TreeSnapshot snapshot = Fixtures.snapshot("r",
                Fixtures.n("r", null),
                Fixtures.n("x", "r"),
                Fixtures.n("y", "r"),
                Fixtures.n("z", "r"));
        LayoutResult result = layout(snapshot, "r");
        assertEquals(-0.3723369f, result.branch("x").direction, EPS);
        assertEquals(0f, result.branch("y").direction, EPS);
        assertEquals(0.3723369f, result.branch("z").direction, EPS);
        assertEquals(-93.6f, result.branch("x").baseY, EPS);
        assertEquals(-106.8f, result.branch("y").baseY, EPS);
        assertEquals(-120f, result.branch("z").baseY, EPS);
        assertEquals(-205.2f, result.branch("y").tipY, EPS);
    }

    @Test
    public void childOrderFollowsTheSnapshotOrder() {
        LayoutResult forward = layout(Fixtures.snapshot("r",
                Fixtures.n("r", null), Fixtures.n("a", "r"), Fixtures.n("b", "r"), Fixtures.n("c", "r")), "r");
        LayoutResult reversed = layout(Fixtures.snapshot("r",
                Fixtures.n("r", null), Fixtures.n("c", "r"), Fixtures.n("b", "r"), Fixtures.n("a", "r")), "r");
        assertEquals(-0.3723369f, forward.branch("a").direction, EPS);
        assertEquals(0.3723369f, forward.branch("c").direction, EPS);
        assertEquals(0.3723369f, reversed.branch("a").direction, EPS);
        assertEquals(-0.3723369f, reversed.branch("c").direction, EPS);
        assertEquals(List.of("r", "c", "b", "a"), ids(reversed));
        assertEquals(List.of("r", "a", "b", "c"), ids(forward));
    }

    @Test
    public void snapshotChildrenKeepTheirInputOrder() {
        TreeSnapshot snapshot = Fixtures.snapshot("r",
                Fixtures.n("r", null), Fixtures.n("c", "r"), Fixtures.n("b", "r"), Fixtures.n("a", "r"));
        List<LayoutNode> children = snapshot.childrenOf("r");
        assertEquals(3, children.size());
        assertEquals("c", children.get(0).id);
        assertEquals("b", children.get(1).id);
        assertEquals("a", children.get(2).id);
        assertEquals("r", snapshot.node("a").parentId);
        assertTrue(snapshot.childrenOf("a").isEmpty());
    }

    @Test
    public void archivedSubtreesLeaveTheDefaultLayout() {
        LayoutResult result = layout(Fixtures.fixtureA(), "a1");
        assertEquals(4, result.branches().size());
        assertNull(result.branch("c"));
        assertNull(result.branch("c1"));
        assertEquals(BranchGeometry.Kind.LEAF, result.branch("a1").kind);
        assertEquals(BranchGeometry.Kind.LIMB, result.branch("a").kind);
        assertEquals(BranchGeometry.Kind.BUD, result.branch("b").kind);
    }

    @Test
    public void branchWithOnlyArchivedChildrenBecomesALeaf() {
        TreeSnapshot snapshot = Fixtures.snapshot("r",
                Fixtures.n("r", null),
                Fixtures.n("a", "r"),
                Fixtures.n("a1", "a").archived());
        LayoutResult result = layout(snapshot, "r");
        assertEquals(2, result.branches().size());
        assertEquals(BranchGeometry.Kind.LEAF, result.branch("a").kind);
        assertNull(result.branch("a1"));
    }

    @Test
    public void collapsedBranchCountsHiddenDescendantsAndTheirUnread() {
        LayoutResult result = layout(Fixtures.fixtureA(), "a1");
        BranchGeometry bud = result.branch("b");
        assertEquals(2, bud.hiddenDescendants);
        assertEquals(3, bud.hiddenUnread);
        assertEquals(1, bud.ownUnread);
        assertEquals(4, bud.unreadTotal());
        assertNull(result.branch("b1"));
        assertNull(result.branch("b2"));
        assertEquals(0, result.branch("r").hiddenDescendants);
        assertEquals(0, result.branch("r").hiddenUnread);
        assertEquals(1, result.branch("r").unreadTotal());
        assertEquals(2, result.branch("a1").unreadTotal());
    }

    @Test
    public void collapsedBranchWithoutVisibleChildrenIsStillALeaf() {
        TreeSnapshot snapshot = Fixtures.snapshot("r",
                Fixtures.n("r", null),
                Fixtures.n("x", "r").collapsed(),
                Fixtures.n("y", "x").archived());
        LayoutResult result = layout(snapshot, "x");
        assertEquals(2, result.branches().size());
        assertEquals(BranchGeometry.Kind.LEAF, result.branch("x").kind);
        assertEquals(0, result.branch("x").hiddenDescendants);
        assertEquals(0, result.branch("x").hiddenUnread);
    }

    @Test
    public void currentPathIsFlaggedAndFallsBackToTheRootWhenTargetIsNotVisible() {
        LayoutResult result = layout(Fixtures.fixtureA(), "a1");
        assertTrue(result.branch("r").onCurrentPath);
        assertTrue(result.branch("a").onCurrentPath);
        assertTrue(result.branch("a1").onCurrentPath);
        assertFalse(result.branch("b").onCurrentPath);
        assertNull(result.anchorOf("c"));
        for (String hidden : new String[] {"c", "c1", "missing", null}) {
            LayoutResult fallback = TreeLayout.compute(Fixtures.fixtureA(), hidden, Density.MID, Fixtures.PHONE);
            assertTrue(fallback.branch("r").onCurrentPath);
            assertFalse(fallback.branch("a").onCurrentPath);
            assertFalse(fallback.branch("b").onCurrentPath);
        }
    }

    @Test
    public void sameInputProducesBitIdenticalOutput() {
        LayoutResult first = layout(Fixtures.fixtureA(), "a1");
        LayoutResult second = layout(Fixtures.fixtureA(), "a1");
        assertEquals(first.branches().size(), second.branches().size());
        for (int i = 0; i < first.branches().size(); i++) {
            BranchGeometry a = first.branches().get(i);
            BranchGeometry b = second.branches().get(i);
            assertEquals(a.branchId, b.branchId);
            assertEquals(a.kind, b.kind);
            assertEquals(a.depth, b.depth);
            assertEquals(a.rounds, b.rounds);
            assertEquals(a.ownUnread, b.ownUnread);
            assertEquals(a.hiddenDescendants, b.hiddenDescendants);
            assertEquals(a.hiddenUnread, b.hiddenUnread);
            assertEquals(a.onCurrentPath, b.onCurrentPath);
            assertEquals(a.baseX, b.baseX, 0f);
            assertEquals(a.baseY, b.baseY, 0f);
            assertEquals(a.tipX, b.tipX, 0f);
            assertEquals(a.tipY, b.tipY, 0f);
            assertEquals(a.baseWidth, b.baseWidth, 0f);
            assertEquals(a.tipWidth, b.tipWidth, 0f);
            assertEquals(a.length, b.length, 0f);
            assertEquals(a.direction, b.direction, 0f);
        }
        assertEquals(first.labels().size(), second.labels().size());
        for (int i = 0; i < first.labels().size(); i++) {
            LabelGeometry a = first.labels().get(i);
            LabelGeometry b = second.labels().get(i);
            assertEquals(a.branchId, b.branchId);
            assertEquals(a.row, b.row);
            assertEquals(a.showSummary, b.showSummary);
            assertEquals(a.x, b.x, 0f);
            assertEquals(a.y, b.y, 0f);
            assertEquals(a.width, b.width, 0f);
            assertEquals(a.height, b.height, 0f);
            assertEquals(a.leaderX, b.leaderX, 0f);
            assertEquals(a.leaderY, b.leaderY, 0f);
        }
        assertEquals(first.hits().size(), second.hits().size());
        for (int i = 0; i < first.hits().size(); i++) {
            assertEquals(first.hits().get(i).branchId, second.hits().get(i).branchId);
            assertEquals(first.hits().get(i).x, second.hits().get(i).x, 0f);
            assertEquals(first.hits().get(i).y, second.hits().get(i).y, 0f);
        }
        assertEquals(first.bounds().minX, second.bounds().minX, 0f);
        assertEquals(first.bounds().minY, second.bounds().minY, 0f);
        assertEquals(first.bounds().maxX, second.bounds().maxX, 0f);
        assertEquals(first.bounds().maxY, second.bounds().maxY, 0f);
    }

    @Test
    public void emptySnapshotProducesAnEmptyFiniteResult() {
        LayoutResult result = TreeLayout.compute(TreeSnapshot.empty(), null, Density.NEAR, Fixtures.PHONE);
        assertTrue(result.branches().isEmpty());
        assertTrue(result.labels().isEmpty());
        assertTrue(result.hits().isEmpty());
        assertTrue(result.bounds().isEmpty());
        assertNull(result.hitTest(0f, 0f));
        assertNull(result.anchorOf("r"));
        ViewTransform transform = result.fit(Fixtures.PHONE);
        assertTrue(Float.isFinite(transform.scale));
        assertTrue(Float.isFinite(transform.translateX));
        assertTrue(Float.isFinite(transform.translateY));
        assertEquals(1f, transform.scale, EPS);
    }

    @Test
    public void singleNodeTreeIsOneLeafAndKeepsTheSoilBand() {
        TreeSnapshot snapshot = Fixtures.snapshot("r", Fixtures.n("r", null).rounds(3));
        LayoutResult result = TreeLayout.compute(snapshot, "r", Density.NEAR, Fixtures.PHONE);
        assertEquals(1, result.branches().size());
        BranchGeometry root = result.branch("r");
        assertEquals(BranchGeometry.Kind.LEAF, root.kind);
        assertEquals(0, root.depth);
        assertEquals(228f, root.length, EPS);
        assertEquals(-228f, root.tipY, EPS);
        assertEquals(44f, result.bounds().maxY, EPS);
        assertTrue(result.bounds().contains(root.tipX, root.tipY));
    }

    @Test
    public void deepChainStaysFinite() {
        TreeSnapshot snapshot = Fixtures.chain(1200);
        LayoutResult result = layout(snapshot, "n1199");
        assertEquals(1200, result.branches().size());
        for (BranchGeometry branch : result.branches()) {
            assertTrue(branch.branchId, Float.isFinite(branch.baseX));
            assertTrue(branch.branchId, Float.isFinite(branch.baseY));
            assertTrue(branch.branchId, Float.isFinite(branch.tipX));
            assertTrue(branch.branchId, Float.isFinite(branch.tipY));
            assertTrue(branch.branchId, Float.isFinite(branch.length));
            assertTrue(branch.branchId, Float.isFinite(branch.direction));
            assertTrue(branch.branchId, Float.isFinite(branch.baseWidth));
            assertTrue(branch.branchId, Float.isFinite(branch.tipWidth));
        }
        assertTrue(Float.isFinite(result.bounds().minX));
        assertTrue(Float.isFinite(result.bounds().maxY));
        assertEquals(BranchGeometry.Kind.LEAF, result.branch("n1199").kind);
        assertEquals(BranchGeometry.Kind.LIMB, result.branch("n0").kind);
    }

    @Test
    public void largeTreeStaysFiniteAndReportsItsSize() {
        TreeSnapshot snapshot = Fixtures.balanced(6, 5);
        long startedAt = System.nanoTime();
        LayoutResult result = layout(snapshot, Fixtures.lastId(snapshot));
        long elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000L;
        assertEquals(9331, result.branches().size());
        assertEquals(7776, countLeaves(result));
        System.out.println("[T-016] nodes=" + result.branches().size() + " leaves=" + countLeaves(result)
                + " labels=" + result.labels().size() + " elapsedMs=" + elapsedMillis);
        for (BranchGeometry branch : result.branches()) {
            assertTrue(branch.branchId, Float.isFinite(branch.tipX));
            assertTrue(branch.branchId, Float.isFinite(branch.tipY));
        }
        assertTrue("layout of 9331 nodes took " + elapsedMillis + " ms", elapsedMillis < 5000L);
    }

    private static int countLeaves(LayoutResult result) {
        int leaves = 0;
        for (BranchGeometry branch : result.branches()) {
            if (branch.kind == BranchGeometry.Kind.LEAF) {
                leaves++;
            }
        }
        return leaves;
    }
}
