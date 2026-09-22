package com.treelearn.app.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.treelearn.core.domain.Branch;
import com.treelearn.core.domain.UnreadRollup;
import com.treelearn.treelayout.BranchGeometry;
import com.treelearn.treelayout.Pt;
import com.treelearn.treelayout.ViewportConstraints;

import org.junit.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The tree canvas's decisions, on the JVM and without a device.
 *
 * <p>Four of them are the reason this suite exists rather than an instrumentation one:
 *
 * <ul>
 *   <li><strong>Which branch a finger landed on</strong> is the inverse of the matrix the canvas drew
 *       with, so a screen point in and a branch id out is a pure function. A device only adds the
 *       touch, not the arithmetic.
 *   <li><strong>Where a node stays</strong> after a fold or a new branch is a property of two layouts
 *       and one transform, so "the node did not move on screen" is decidable here.
 *   <li><strong>What a refused write does</strong> is the same rule the home composer follows: the
 *       canvas keeps what it had and says why.
 *   <li><strong>The unread badges</strong> are compared against {@code UnreadRollup}, the domain's own
 *       authority, so the canvas cannot grow a second opinion about what an unseen answer is.
 * </ul>
 */
public final class TreeCanvasPresenterTest {

    private static final float EPS = 0.01f;

    private final TreeCanvasDoubles.FakeSink sink = new TreeCanvasDoubles.FakeSink();
    private final TreeCanvasDoubles.FakeData data = TreeCanvasDoubles.standard();
    private final TreeCanvasDoubles.FakeCommands commands = new TreeCanvasDoubles.FakeCommands(data);

    private TreeCanvasPresenter phone(String branchId) {
        return TreeCanvasDoubles.presenterOf(sink, data, commands, TreeCanvasDoubles.PHONE_W,
                TreeCanvasDoubles.PHONE_H, branchId);
    }

    @Test
    public void startReadsTheTopicFitsTheWholeTreeAndRendersReady() {
        TreeCanvasPresenter presenter = phone(TreeCanvasDoubles.LEAF_A);
        presenter.start();

        TreeCanvasViewState state = sink.last;
        assertNotNull(state);
        assertEquals(TreeCanvasViewState.Phase.READY, state.phase);
        assertEquals(TreeCanvasDoubles.TOPIC, state.topicId);
        assertEquals(TreeCanvasDoubles.LEAF_A, state.currentBranchId);
        assertTrue(state.hasGeometry());
        assertNull(state.notice);

        ViewportConstraints viewport = ViewportConstraints.of(TreeCanvasDoubles.PHONE_W,
                TreeCanvasDoubles.PHONE_H);
        assertEquals(state.layout.fit(viewport).scale, state.transform.scale, EPS);
        assertEquals(state.layout.fit(viewport).translateY, state.transform.translateY, EPS);
        assertTrue("the canvas shows the branch the caller asked for",
                state.layout.branch(TreeCanvasDoubles.LEAF_A) != null);
        assertEquals("loading then ready", 2, sink.renders);
    }

    @Test
    public void aBranchThatIsNoLongerInTheTreeFallsBackToTheRoot() {
        TreeCanvasPresenter presenter = phone(TreeCanvasDoubles.ARCHIVED);
        presenter.start();

        assertEquals(TreeCanvasDoubles.rootIdOf(data), sink.last.currentBranchId);
        assertNotNull(sink.last.layout.branch(TreeCanvasDoubles.rootIdOf(data)));
    }

    @Test
    public void aTopicWithNoBranchesRendersEmptyRatherThanFailing() {
        data.branches.clear();
        TreeCanvasPresenter presenter = phone(TreeCanvasDoubles.ROOT);
        presenter.start();

        assertEquals(TreeCanvasViewState.Phase.EMPTY, sink.last.phase);
        assertFalse(sink.last.hasGeometry());
        assertNull(sink.last.notice);
    }

    @Test
    public void aBranchThatIsGoneShowsAnEmptyCanvasRatherThanAFailure() {
        data.topic = null;
        TreeCanvasPresenter presenter = phone(TreeCanvasDoubles.ROOT);
        presenter.start();

        assertEquals(TreeCanvasViewState.Phase.EMPTY, sink.last.phase);
        assertNull(sink.last.topicId);
        assertNull(sink.last.notice);
    }

    @Test
    public void anUnreadableStoreKeepsTheLastCanvasAndSaysWhy() {
        TreeCanvasPresenter presenter = phone(TreeCanvasDoubles.LEAF_A);
        presenter.start();
        int branches = sink.last.branches;
        float scale = sink.last.transform.scale;

        data.failure = new IllegalStateException("store down");
        presenter.onResumed();

        assertEquals(TreeCanvasViewState.Phase.ERROR, sink.last.phase);
        assertEquals(HomeViewState.Notice.STORAGE, sink.last.notice);
        assertEquals("the last drawable canvas survives the failed read", branches, sink.last.branches);
        assertEquals(scale, sink.last.transform.scale, EPS);
    }

    @Test
    public void tappingABranchSelectsItAndPlacesItsPopupInsideTheCanvas() {
        TreeCanvasPresenter presenter = phone(TreeCanvasDoubles.LEAF_A);
        presenter.start();

        Pt anchor = sink.last.layout.anchorOf(TreeCanvasDoubles.LEAF_B);
        assertNotNull(anchor);
        Pt screen = sink.last.transform.toScreen(anchor.x, anchor.y);

        presenter.onTap(screen.x, screen.y);

        assertEquals(TreeCanvasDoubles.LEAF_B, sink.last.currentBranchId);
        assertNotNull("a hit opens the node popup", sink.last.popup);
        assertEquals(TreeCanvasDoubles.LEAF_B, sink.last.popup.branchId);
        assertTrue("the popup stays inside the window",
                sink.last.popup.fitsInside(TreeCanvasDoubles.PHONE_W, TreeCanvasDoubles.PHONE_H));
    }

    @Test
    public void tappingEmptySpaceClosesThePopupAndKeepsTheSelection() {
        TreeCanvasPresenter presenter = phone(TreeCanvasDoubles.LEAF_A);
        presenter.start();
        presenter.onTap(screenPointOf(TreeCanvasDoubles.LEAF_A).x, screenPointOf(TreeCanvasDoubles.LEAF_A).y);
        assertNotNull(sink.last.popup);

        Pt offscreen = sink.last.transform.toScreen(sink.last.layout.bounds().maxX + 4000f,
                sink.last.layout.bounds().maxY + 4000f);
        presenter.onTap(offscreen.x, offscreen.y);

        assertNull("nothing under the finger means nothing to show", sink.last.popup);
        assertEquals(TreeCanvasDoubles.LEAF_A, sink.last.currentBranchId);
    }

    /** The point on screen where a branch's own anchor sits in the last rendered frame. */
    private Pt screenPointOf(String branchId) {
        Pt anchor = sink.last.layout.anchorOf(branchId);
        assertNotNull("branch " + branchId + " is not in the last layout", anchor);
        return sink.last.transform.toScreen(anchor.x, anchor.y);
    }

    @Test
    public void openingABudIssuesTheCommandAndRebuildsTheGeometry() {
        TreeCanvasPresenter presenter = phone(TreeCanvasDoubles.LEAF_A);
        presenter.start();
        assertNull("the hidden child is not in the layout", sink.last.layout.branch(TreeCanvasDoubles.HIDDEN));

        presenter.onCollapseToggled(TreeCanvasDoubles.LEAF_B);

        assertEquals("the command names the branch and the wanted state",
                List.of(TreeCanvasDoubles.LEAF_B + ":false"), commands.calls);
        assertNotNull("unfolding brings the hidden child back",
                sink.last.layout.branch(TreeCanvasDoubles.HIDDEN));
        assertEquals(BranchGeometry.Kind.LIMB, sink.last.layout.branch(TreeCanvasDoubles.LEAF_B).kind);
        assertEquals("the bud's badge moved onto the visible child",
                2L, sink.last.layout.branch(TreeCanvasDoubles.HIDDEN).ownUnread);
    }

    @Test
    public void aRefusedCollapseReportsTheFailureAndKeepsTheCanvas() {
        TreeCanvasPresenter presenter = phone(TreeCanvasDoubles.LEAF_A);
        presenter.start();
        int branches = sink.last.branches;

        commands.refuse = true;
        presenter.onCollapseToggled(TreeCanvasDoubles.LEAF_B);

        assertEquals(HomeViewState.Notice.STORAGE, sink.last.notice);
        assertEquals(branches, sink.last.branches);
        assertNull(sink.last.layout.branch(TreeCanvasDoubles.HIDDEN));
        assertFalse("a refusal is not an error phase, the canvas is still drawable",
                sink.last.phase == TreeCanvasViewState.Phase.ERROR);
    }

    @Test
    public void aNewBranchKeepsTheCurrentBranchWhereTheLearnerLeftIt() {
        data.branches.clear();
        Branch root = TreeCanvasDoubles.root("根问题", 0);
        data.branches.add(root);
        data.branches.add(TreeCanvasDoubles.child(TreeCanvasDoubles.LEAF_A, root.id, "叶子 A", 0, false,
                false));

        TreeCanvasPresenter presenter = phone(TreeCanvasDoubles.LEAF_A);
        presenter.start();
        Pt before = sink.last.layout.anchorOf(TreeCanvasDoubles.LEAF_A);
        Pt beforeScreen = sink.last.transform.toScreen(before.x, before.y);
        com.treelearn.treelayout.LayoutResult beforeLayout = sink.last.layout;

        data.branches.add(TreeCanvasDoubles.child(TreeCanvasDoubles.BUD, root.id, "新分支", 0, false,
                false));
        presenter.onResumed();

        Pt delta = beforeLayout.anchorDeltaTo(sink.last.layout, TreeCanvasDoubles.LEAF_A);
        assertNotNull(delta);
        assertTrue("fixture must actually move the node, or this proves nothing",
                Math.abs(delta.x) + Math.abs(delta.y) > 1f);
        Pt after = sink.last.layout.anchorOf(TreeCanvasDoubles.LEAF_A);
        Pt afterScreen = sink.last.transform.toScreen(after.x, after.y);
        assertEquals("the node under the finger must not slide", beforeScreen.x, afterScreen.x, 0.5f);
        assertEquals("the node under the finger must not slide", beforeScreen.y, afterScreen.y, 0.5f);
    }

    @Test
    public void rotationKeepsTheZoomAndPinsTheCurrentBranch() {
        TreeCanvasPresenter presenter = phone(TreeCanvasDoubles.LEAF_A);
        presenter.start();
        float scale = sink.last.transform.scale;
        Pt anchor = sink.last.layout.anchorOf(TreeCanvasDoubles.LEAF_A);
        Pt before = sink.last.transform.toScreen(anchor.x, anchor.y);

        presenter.onViewportChanged(TreeCanvasDoubles.TABLET_W, TreeCanvasDoubles.TABLET_H);

        assertEquals("the zoom the learner chose survives the rotation", scale,
                sink.last.transform.scale, EPS);
        Pt after = sink.last.transform.toScreen(anchor.x, anchor.y);
        assertEquals("the anchor keeps its place in the new window",
                before.x / TreeCanvasDoubles.PHONE_W, after.x / TreeCanvasDoubles.TABLET_W, 0.01f);
        assertEquals(before.y / TreeCanvasDoubles.PHONE_H, after.y / TreeCanvasDoubles.TABLET_H, 0.01f);
    }

    @Test
    public void fitRestoresTheCompleteBoundsBottomAligned() {
        TreeCanvasPresenter presenter = phone(TreeCanvasDoubles.LEAF_A);
        presenter.start();
        presenter.onZoom(3f, 10f, 10f);
        presenter.onPan(-400f, -400f);

        presenter.onFit();

        ViewportConstraints viewport = ViewportConstraints.of(TreeCanvasDoubles.PHONE_W,
                TreeCanvasDoubles.PHONE_H);
        assertEquals(sink.last.layout.fit(viewport).scale, sink.last.transform.scale, EPS);
        assertEquals(sink.last.layout.fit(viewport).translateX, sink.last.transform.translateX, EPS);
        assertEquals(sink.last.layout.fit(viewport).translateY, sink.last.transform.translateY, EPS);
    }

    @Test
    public void openingTheCurrentBranchHandsOverItsId() {
        TreeCanvasPresenter presenter = phone(TreeCanvasDoubles.LEAF_A);
        presenter.start();
        presenter.onOpenCurrent();

        assertEquals(List.of(TreeCanvasDoubles.LEAF_A), sink.opened);
    }

    @Test
    public void openingAnEmptyCanvasOpensNothing() {
        data.branches.clear();
        TreeCanvasPresenter presenter = phone(TreeCanvasDoubles.ROOT);
        presenter.start();
        presenter.onOpenCurrent();

        assertTrue(sink.opened.isEmpty());
    }

    @Test
    public void backLeavesTheTree() {
        TreeCanvasPresenter presenter = phone(TreeCanvasDoubles.LEAF_A);
        presenter.start();
        presenter.onBackPressed();

        assertTrue(sink.leftTree);
    }

    /**
     * The canvas draws badges; the domain decides what an unseen answer is. This compares the two
     * entry points branch by branch, so neither "an archived branch leaks its unread" nor "a collapsed
     * branch loses its descendants'" can pass unnoticed: the first changes the key set, the second the
     * value.
     */
    @Test
    public void theBadgesTheCanvasDrawsAreTheUnreadRollupsOwnNumbers() {
        TreeCanvasPresenter presenter = phone(TreeCanvasDoubles.LEAF_A);
        presenter.start();

        Map<String, Long> authority = UnreadRollup.visibleUnread(data.branches);
        Set<String> drawn = new HashSet<String>();
        for (BranchGeometry branch : sink.last.layout.branches()) {
            drawn.add(branch.branchId);
            Long badge = authority.get(branch.branchId);
            assertNotNull("the canvas drew a branch the rollup does not publish: " + branch.branchId,
                    badge);
            assertEquals("badge for " + branch.branchId, badge.longValue(),
                    (long) branch.unreadTotal());
        }
        assertEquals("the rollup publishes a branch the canvas does not draw",
                new HashSet<String>(authority.keySet()), drawn);
        assertFalse("the archived branch must not be drawn",
                drawn.contains(TreeCanvasDoubles.ARCHIVED));
        assertFalse("a branch hidden behind a bud must not be drawn",
                drawn.contains(TreeCanvasDoubles.HIDDEN));
        assertEquals(3, drawn.size());
        List<String> ids = new ArrayList<String>(drawn);
        assertTrue(ids.contains(TreeCanvasDoubles.rootIdOf(data)));
    }
}