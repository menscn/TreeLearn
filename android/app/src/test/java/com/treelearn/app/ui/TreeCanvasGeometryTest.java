package com.treelearn.app.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.treelearn.treelayout.Bounds;
import com.treelearn.treelayout.HitRegion;
import com.treelearn.treelayout.Pt;
import com.treelearn.treelayout.TreeLayout;
import com.treelearn.treelayout.ViewTransform;
import com.treelearn.treelayout.ViewportConstraints;

import org.junit.Test;

/**
 * The canvas as arithmetic: the matrix, the inverse that turns a finger back into tree space, the
 * readable zoom range, and where the node popup lands.
 *
 * <p>None of this needs a screen. A device contributes a touch stream; whether the point under the
 * finger names the right branch is decided by numbers this suite feeds in itself, which is why the
 * "tap hits the branch the layout reports" criterion is graded here rather than deferred.
 */
public final class TreeCanvasGeometryTest {

    private static final float EPS = 0.01f;

    private final TreeCanvasDoubles.FakeSink sink = new TreeCanvasDoubles.FakeSink();
    private final TreeCanvasDoubles.FakeData data = TreeCanvasDoubles.standard();
    private final TreeCanvasDoubles.FakeCommands commands = new TreeCanvasDoubles.FakeCommands(data);

    private TreeCanvasPresenter started() {
        TreeCanvasPresenter presenter = TreeCanvasDoubles.presenterOf(sink, data, commands,
                TreeCanvasDoubles.PHONE_W, TreeCanvasDoubles.PHONE_H, TreeCanvasDoubles.LEAF_A);
        presenter.start();
        return presenter;
    }

    @Test
    public void screenAndTreeSpaceSurviveTheRoundTrip() {
        started();
        ViewTransform transform = sink.last.transform;
        for (int row = 0; row <= 8; row++) {
            for (int column = 0; column <= 8; column++) {
                float x = TreeCanvasDoubles.PHONE_W * column / 8f;
                float y = TreeCanvasDoubles.PHONE_H * row / 8f;
                Pt tree = transform.toTree(x, y);
                Pt back = transform.toScreen(tree.x, tree.y);
                assertEquals("x at " + x + "," + y, x, back.x, EPS);
                assertEquals("y at " + x + "," + y, y, back.y, EPS);
            }
        }
    }

    @Test
    public void aScreenPointSelectsTheBranchTheLayoutHitsAtTheInversePoint() {
        TreeCanvasPresenter presenter = started();
        ViewTransform transform = sink.last.transform;

        int hits = 0;
        for (int row = 0; row <= 12; row++) {
            for (int column = 0; column <= 12; column++) {
                float x = TreeCanvasDoubles.PHONE_W * column / 12f;
                float y = TreeCanvasDoubles.PHONE_H * row / 12f;
                Pt tree = transform.toTree(x, y);
                HitRegion expected = sink.last.layout.hitTest(tree.x, tree.y);

                presenter.onTap(x, y);

                if (expected == null) {
                    assertNull("nothing is under " + x + "," + y, sink.last.popup);
                } else {
                    hits++;
                    assertNotNull("a hit must open a popup at " + x + "," + y, sink.last.popup);
                    assertEquals("branch under " + x + "," + y, expected.branchId,
                            sink.last.popup.branchId);
                    assertEquals(expected.branchId, sink.last.currentBranchId);
                }
            }
        }
        assertTrue("the sample grid must actually land on the tree", hits > 0);
    }

    @Test
    public void fitUsesTheWholeBoundsCentredAndBottomAligned() {
        started();
        ViewportConstraints viewport = ViewportConstraints.of(TreeCanvasDoubles.PHONE_W,
                TreeCanvasDoubles.PHONE_H);
        ViewTransform fitted = sink.last.transform;
        Bounds bounds = sink.last.layout.bounds();

        assertEquals("the soil band ends one padding above the bottom edge",
                TreeCanvasDoubles.PHONE_H - viewport.padding, fitted.toScreen(0f, bounds.maxY).y, EPS);
        assertEquals("the tree is centred horizontally",
                TreeCanvasDoubles.PHONE_W / 2f,
                (fitted.toScreen(bounds.minX, 0f).x + fitted.toScreen(bounds.maxX, 0f).x) / 2f, EPS);
        assertTrue("the fit is clamped to a readable range",
                fitted.scale >= TreeLayout.MIN_SCALE && fitted.scale <= TreeLayout.MAX_SCALE);
    }

    @Test
    public void fitIsClampedToTheReadableRangeAndNeverDegenerates() {
        started();
        ViewTransform huge = sink.last.layout.fit(ViewportConstraints.of(20000f, 20000f));
        assertEquals(TreeLayout.MAX_SCALE, huge.scale, EPS);

        ViewTransform degenerate = sink.last.layout.fit(ViewportConstraints.of(0f, 0f));
        assertTrue("a zero-area viewport still yields a drawable matrix",
                Float.isFinite(degenerate.scale) && degenerate.scale > 0f
                        && Float.isFinite(degenerate.translateX)
                        && Float.isFinite(degenerate.translateY));
        assertTrue(degenerate.scale >= TreeLayout.MIN_SCALE
                && degenerate.scale <= TreeLayout.MAX_SCALE);
    }

    @Test
    public void pinchKeepsThePointUnderTheFingersStill() {
        TreeCanvasPresenter presenter = started();
        float focusX = 200f;
        float focusY = 300f;
        Pt before = sink.last.transform.toTree(focusX, focusY);
        float wanted = Math.min(TreeLayout.MAX_SCALE,
                Math.max(TreeLayout.MIN_SCALE, sink.last.transform.scale * 1.4f));

        presenter.onZoom(1.4f, focusX, focusY);

        assertEquals("the pinch lands on the requested zoom", wanted, sink.last.transform.scale, EPS);
        assertTrue("the pinch actually zoomed", sink.last.transform.scale > 0f);
        Pt after = sink.last.transform.toTree(focusX, focusY);
        assertEquals(before.x, after.x, EPS);
        assertEquals(before.y, after.y, EPS);
    }

    @Test
    public void pinchCannotLeaveTheReadableRangeEvenWhenClamped() {
        TreeCanvasPresenter presenter = started();
        float focusX = 180f;
        float focusY = 420f;

        for (int i = 0; i < 12; i++) {
            presenter.onZoom(2f, focusX, focusY);
        }
        assertEquals(TreeLayout.MAX_SCALE, sink.last.transform.scale, EPS);
        assertEquals("the focus point is still the same point, even clamped",
                focusX, sink.last.transform.toScreen(sink.last.transform.toTree(focusX, focusY).x,
                        0f).x, EPS);

        for (int i = 0; i < 24; i++) {
            presenter.onZoom(0.5f, focusX, focusY);
        }
        assertEquals(TreeLayout.MIN_SCALE, sink.last.transform.scale, EPS);
        assertTrue("a clamped zoom never produces NaN", Float.isFinite(sink.last.transform.translateX)
                && Float.isFinite(sink.last.transform.translateY));
        assertEquals("the point under the fingers survives clamping too", focusX,
                sink.last.transform.toScreen(sink.last.transform.toTree(focusX, focusY).x, 0f).x, EPS);
    }

    @Test
    public void panMovesEveryPointByTheSameNumberOfPixels() {
        TreeCanvasPresenter presenter = started();
        Pt probe = sink.last.transform.toScreen(0f, 0f);
        float scale = sink.last.transform.scale;

        presenter.onPan(-37f, 21f);

        Pt moved = sink.last.transform.toScreen(0f, 0f);
        assertEquals(-37f, moved.x - probe.x, EPS);
        assertEquals(21f, moved.y - probe.y, EPS);
        assertEquals("panning must not rescale", scale, sink.last.transform.scale, EPS);
    }

    @Test
    public void aPopupMidWindowSitsAboveItsAnchorAndInsideTheWindow() {
        PopupPlacement popup = PopupPlacement.place("b", 500f, 400f, 200f, 100f, 1000f, 800f);
        assertEquals(400f - 100f - PopupPlacement.MARGIN, popup.y, EPS);
        assertEquals(400f, popup.x, EPS);
        assertTrue(popup.fitsInside(1000f, 800f));
        assertFalse(popup.flippedY);
        assertFalse(popup.flippedX);
    }

    @Test
    public void aPopupWithNoRoomAboveFlipsBelowTheAnchorAndStaysInside() {
        PopupPlacement popup = PopupPlacement.place("b", 500f, 50f, 200f, 100f, 1000f, 800f);
        assertTrue("there is no space above, so it opens downwards", popup.flippedY);
        assertEquals(50f + PopupPlacement.MARGIN, popup.y, EPS);
        assertTrue(popup.fitsInside(1000f, 800f));
    }

    @Test
    public void aPopupNearASideIsClampedInsideTheWindowEdge() {
        PopupPlacement right = PopupPlacement.place("b", 990f, 400f, 200f, 100f, 1000f, 800f);
        assertTrue(right.flippedX);
        assertTrue(right.fitsInside(1000f, 800f));
        assertEquals(1000f - 200f - PopupPlacement.MARGIN, right.x, EPS);
    }

    @Test
    public void aPopupWiderThanTheWindowStaysAtTheMarginInsteadOfGoingNegative() {
        PopupPlacement popup = PopupPlacement.place("b", 500f, 400f, 1200f, 100f, 1000f, 800f);
        assertEquals(PopupPlacement.MARGIN, popup.x, EPS);
        assertTrue(popup.flippedX);
    }
}