package com.treelearn.app.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.treelearn.treelayout.BranchGeometry;
import com.treelearn.treelayout.Density;
import com.treelearn.treelayout.LayoutNode;
import com.treelearn.treelayout.LayoutResult;
import com.treelearn.treelayout.TreeLayout;
import com.treelearn.treelayout.TreeSnapshot;
import com.treelearn.treelayout.ViewTransform;
import com.treelearn.treelayout.ViewportConstraints;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * The canvas state as a value: one phase, one geometry, one transform, and counts that cannot drift
 * from the geometry they describe.
 *
 * <p>The counts are asserted against a scan of {@code layout.branches()} rather than against numbers
 * written here, because the point of deriving them is that there is no second tally to disagree with.
 */
public final class TreeCanvasViewStateTest {

    private static final float EPS = 0.0001f;

    /**
     * <pre>
     * r  rounds 1, unread 1   LIMB     (root)
     * |- a  rounds 2, unread 2, summary   LEAF
     * |- b  collapsed                     BUD, hiding h and its 3 unseen answers
     *    |- h  unread 3                   never visible
     * </pre>
     */
    private static LayoutResult collapsible(Density density) {
        List<LayoutNode> nodes = new ArrayList<LayoutNode>();
        nodes.add(new LayoutNode("r", null, "根问题", "", 1, 1, false, false));
        nodes.add(new LayoutNode("a", "r", "叶子", "摘要一行", 2, 2, false, false));
        nodes.add(new LayoutNode("b", "r", "枝", "", 1, 0, true, false));
        nodes.add(new LayoutNode("h", "b", "藏在芽里", "", 0, 3, false, false));
        return TreeLayout.compute(TreeSnapshot.of("r", nodes), "a", density,
                ViewportConstraints.of(TreeCanvasDoubles.PHONE_W, TreeCanvasDoubles.PHONE_H));
    }

    private static ViewportConstraints phone() {
        return ViewportConstraints.of(TreeCanvasDoubles.PHONE_W, TreeCanvasDoubles.PHONE_H);
    }

    @Test
    public void startsLoadingWithAnEmptyCanvasAndNoNotice() {
        TreeCanvasViewState state = TreeCanvasViewState.initial(TreeCanvasDoubles.TOPIC,
                TreeCanvasDoubles.LEAF_A, phone());
        assertEquals(TreeCanvasViewState.Phase.LOADING, state.phase);
        assertEquals(TreeCanvasDoubles.TOPIC, state.topicId);
        assertEquals(TreeCanvasDoubles.LEAF_A, state.currentBranchId);
        assertFalse(state.hasGeometry());
        assertEquals(0, state.branches);
        assertEquals(0, state.unreadTotal);
        assertNull(state.notice);
        assertNull(state.popup);
        assertNotNull(state.transform);
        assertNotNull(state.layout);
    }

    @Test
    public void countsAreReadOffTheGeometryInsteadOfASecondTally() {
        LayoutResult layout = collapsible(Density.MID);
        TreeCanvasViewState state = TreeCanvasViewState.initial(TreeCanvasDoubles.TOPIC, "a", phone())
                .withFrame(layout, layout.fit(phone()));

        assertEquals(layout.branches().size(), state.branches);
        assertEquals(kind(layout, BranchGeometry.Kind.LIMB), state.limbs);
        assertEquals(kind(layout, BranchGeometry.Kind.LEAF), state.leaves);
        assertEquals(kind(layout, BranchGeometry.Kind.BUD), state.buds);
        assertEquals(unreadSum(layout), state.unreadTotal);
        assertEquals(1, state.unreadBuds);
        assertTrue(state.hasGeometry());
    }

    @Test
    public void aBudWithoutUnreadIsNotAnUnreadBud() {
        List<LayoutNode> nodes = new ArrayList<LayoutNode>();
        nodes.add(new LayoutNode("r", null, "根", "", 1, 0, false, false));
        nodes.add(new LayoutNode("b", "r", "枝", "", 1, 0, true, false));
        nodes.add(new LayoutNode("h", "b", "藏", "", 0, 0, false, false));
        LayoutResult layout = TreeLayout.compute(TreeSnapshot.of("r", nodes), "r", Density.MID, phone());

        TreeCanvasViewState state = TreeCanvasViewState.initial(TreeCanvasDoubles.TOPIC, "r", phone())
                .withFrame(layout, layout.fit(phone()));
        assertEquals(1, state.buds);
        assertEquals(0, state.unreadBuds);
        assertEquals(0, state.unreadTotal);
    }

    @Test
    public void theDensityTierAlwaysMatchesTheScaleItWasDerivedFrom() {
        LayoutResult layout = collapsible(Density.NEAR);
        for (float scale : new float[] {0.2f, 0.32f, 0.71f, 0.72f, 1.04f, 1.05f, 1.25f, 3f}) {
            TreeCanvasViewState state = TreeCanvasViewState
                    .initial(TreeCanvasDoubles.TOPIC, "a", phone())
                    .withFrame(layout, new ViewTransform(scale, 0f, 0f));
            assertEquals("scale " + scale, Density.forScale(scale), state.density);
        }
    }

    @Test
    public void aTransformChangeKeepsTheGeometryAndThePhase() {
        LayoutResult layout = collapsible(Density.MID);
        TreeCanvasViewState state = TreeCanvasViewState
                .initial(TreeCanvasDoubles.TOPIC, "a", phone())
                .withFrame(layout, layout.fit(phone()))
                .withPhase(TreeCanvasViewState.Phase.READY);

        TreeCanvasViewState moved = state.withTransform(new ViewTransform(0.5f, 12f, -7f));
        assertEquals(TreeCanvasViewState.Phase.READY, moved.phase);
        assertEquals(layout.branches().size(), moved.branches);
        assertEquals(0.5f, moved.transform.scale, EPS);
        assertEquals(Density.forScale(0.5f), moved.density);
        assertEquals(state.currentBranchId, moved.currentBranchId);
    }

    @Test
    public void theFrameCarriesTheTextItsPlatesRead() {
        LayoutResult layout = collapsible(Density.NEAR);
        TreeSnapshot snapshot = TreeSnapshot.of("r", List.of(
                new LayoutNode("r", null, "根问题", "", 1, 1, false, false),
                new LayoutNode("a", "r", "叶子", "摘要一行", 2, 2, false, false)));
        TreeCanvasViewState state = TreeCanvasViewState.initial(TreeCanvasDoubles.TOPIC, "a", phone())
                .withSnapshot(snapshot).withFrame(layout, layout.fit(phone()));

        assertEquals("根问题", state.titleOf("r"));
        assertEquals("叶子", state.titleOf("a"));
        assertEquals("摘要一行", state.summaryOf("a"));
        assertEquals("a branch with no summary reads as empty, never as null",
                "", state.summaryOf("r"));
        assertEquals("a branch this frame does not hold reads as empty",
                "", state.titleOf("99999999-9999-4999-8999-999999999999"));
        assertEquals("", state.titleOf(null));
        assertSame(snapshot, state.withPopup(null).snapshot);
    }

    @Test
    public void everyWitherLeavesEveryOtherFieldAlone() {
        LayoutResult layout = collapsible(Density.MID);
        TreeCanvasViewState base = TreeCanvasViewState
                .initial(TreeCanvasDoubles.TOPIC, "a", phone())
                .withFrame(layout, layout.fit(phone()))
                .withPhase(TreeCanvasViewState.Phase.READY);

        assertSameFrame(base, base.withNotice(HomeViewState.Notice.STORAGE));
        assertSameFrame(base, base.withCurrentBranch("b"));
        assertSameFrame(base, base.withPopup(new PopupPlacement("a", 1f, 2f, 3f, 4f, false, false)));
        assertSameFrame(base, base.withViewport(ViewportConstraints.of(1024f, 640f)));
        assertEquals(HomeViewState.Notice.STORAGE, base.withNotice(HomeViewState.Notice.STORAGE).notice);
        assertEquals("b", base.withCurrentBranch("b").currentBranchId);
        assertEquals(1024f, base.withViewport(ViewportConstraints.of(1024f, 640f)).viewport.width, EPS);
    }

    private static void assertSameFrame(TreeCanvasViewState expected, TreeCanvasViewState actual) {
        assertEquals(expected.phase, actual.phase);
        assertEquals(expected.topicId, actual.topicId);
        assertEquals(expected.branches, actual.branches);
        assertEquals(expected.unreadTotal, actual.unreadTotal);
        assertSame(expected.transform, actual.transform);
    }

    private static int kind(LayoutResult layout, BranchGeometry.Kind kind) {
        int count = 0;
        for (BranchGeometry branch : layout.branches()) {
            if (branch.kind == kind) {
                count++;
            }
        }
        return count;
    }

    private static int unreadSum(LayoutResult layout) {
        int total = 0;
        for (BranchGeometry branch : layout.branches()) {
            total += branch.unreadTotal();
        }
        return total;
    }
}