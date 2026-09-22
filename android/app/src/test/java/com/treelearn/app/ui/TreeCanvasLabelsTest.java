package com.treelearn.app.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.treelearn.treelayout.BranchGeometry;
import com.treelearn.treelayout.Density;
import com.treelearn.treelayout.HitRegion;
import com.treelearn.treelayout.LabelGeometry;

import org.junit.Test;

/**
 * What the canvas is allowed to draw at each zoom, and the rule that matters most when it draws least:
 * a node whose title was dropped - no room for a plate, a title too long to fit, the far tier where
 * nothing is written at all - must still be reachable by a finger.
 *
 * <p>The tier is not a field the screen sets. It is derived from the matrix the canvas already has, so
 * these tests drive it the way a learner does: they pinch, and then ask what the tier and the layout
 * became.
 */
public final class TreeCanvasLabelsTest {

    private static final float EPS = 0.001f;

    private final TreeCanvasDoubles.FakeSink sink = new TreeCanvasDoubles.FakeSink();
    private final TreeCanvasDoubles.FakeData data = TreeCanvasDoubles.standard();
    private final TreeCanvasDoubles.FakeCommands commands = new TreeCanvasDoubles.FakeCommands(data);

    private TreeCanvasPresenter started() {
        TreeCanvasPresenter presenter = TreeCanvasDoubles.presenterOf(sink, data, commands,
                TreeCanvasDoubles.PHONE_W, TreeCanvasDoubles.PHONE_H, TreeCanvasDoubles.LEAF_A);
        presenter.start();
        return presenter;
    }

    /** Pinches until the tier is {@code wanted}, and fails loudly rather than looping forever. */
    private void zoomTo(TreeCanvasPresenter presenter, Density wanted) {
        for (int attempt = 0; attempt < 60 && sink.last.density != wanted; attempt++) {
            float scale = sink.last.transform.scale;
            float factor;
            if (wanted == Density.FAR) {
                factor = 0.8f;
            } else if (scale < Density.MID_START_SCALE) {
                factor = 1.3f;
            } else if (wanted == Density.MID && scale >= Density.NEAR_START_SCALE) {
                factor = 0.9f;
            } else {
                factor = 1.3f;
            }
            presenter.onZoom(factor, 100f, 100f);
        }
        assertEquals("could not reach the " + wanted + " tier", wanted, sink.last.density);
    }

    @Test
    public void theFarTierWritesNothingButStillLetsEveryNodeBeTouched() {
        TreeCanvasPresenter presenter = started();
        zoomTo(presenter, Density.FAR);

        assertTrue("no plate may be placed in the far tier", sink.last.layout.labels().isEmpty());
        assertFalse(sink.last.layout.branches().isEmpty());
        for (BranchGeometry branch : sink.last.layout.branches()) {
            HitRegion hit = hitFor(branch.branchId);
            assertNotNull("every drawn node keeps a target: " + branch.branchId, hit);
            assertTrue("44dp floor for " + branch.branchId,
                    hit.width >= 44f && hit.height >= 44f);
        }
    }

    @Test
    public void theMidTierPlacesPlatesWithoutSummaries() {
        TreeCanvasPresenter presenter = started();
        zoomTo(presenter, Density.MID);

        assertFalse(sink.last.layout.labels().isEmpty());
        for (LabelGeometry label : sink.last.layout.labels()) {
            assertFalse("the mid tier must not grow a summary line: " + label.branchId,
                    label.showSummary);
        }
    }

    @Test
    public void theNearTierShowsTheSummaryOnlyForABranchThatHasOne() {
        TreeCanvasPresenter presenter = started();
        zoomTo(presenter, Density.NEAR);

        int withSummary = 0;
        for (LabelGeometry label : sink.last.layout.labels()) {
            if (label.showSummary) {
                withSummary++;
                assertEquals("only a branch with a summary may carry one", TreeCanvasDoubles.LEAF_A,
                        label.branchId);
            }
        }
        assertEquals(1, withSummary);
        assertTrue("the plate must still be drawn", sink.last.layout.labels().size() >= 1);
    }

    @Test
    public void zoomingOutAndBackInReplacesTheGeometryRatherThanTheLabelFlag() {
        TreeCanvasPresenter presenter = started();
        zoomTo(presenter, Density.NEAR);
        int nearLabels = sink.last.layout.labels().size();
        assertTrue(nearLabels > 0);

        zoomTo(presenter, Density.FAR);
        assertEquals(0, sink.last.layout.labels().size());

        zoomTo(presenter, Density.NEAR);
        assertEquals("the same tier must reproduce the same plates", nearLabels,
                sink.last.layout.labels().size());
        assertEquals(Density.NEAR, sink.last.density);
        assertEquals(Density.forScale(sink.last.transform.scale), sink.last.density);
    }

    @Test
    public void aTitleTooLongForItsPlateIsEllipsisedWithoutLosingTheTarget() {
        StringBuilder long_ = new StringBuilder();
        for (int i = 0; i < 30; i++) {
            long_.append("很长的标题 ");
        }
        for (com.treelearn.core.domain.Branch branch : data.branches) {
            branch.setTitle(long_.toString(), TreeCanvasDoubles.T0 + 5);
        }

        TreeCanvasPresenter presenter = started();
        zoomTo(presenter, Density.NEAR);

        for (BranchGeometry branch : sink.last.layout.branches()) {
            HitRegion hit = hitFor(branch.branchId);
            assertNotNull("long titles must not cost a node its target: " + branch.branchId, hit);
            assertTrue(hit.width >= 44f && hit.height >= 44f);
        }
    }

    @Test
    public void theTierIsAFunctionOfTheZoomAndNothingElse() {
        TreeCanvasPresenter presenter = started();
        zoomTo(presenter, Density.FAR);
        assertEquals(Density.FAR, Density.forScale(sink.last.transform.scale));
        assertFalse(Density.FAR.labels);

        zoomTo(presenter, Density.NEAR);
        assertTrue(sink.last.transform.scale >= Density.NEAR_START_SCALE);
        assertTrue(Density.NEAR.labels && Density.NEAR.summaries);
    }

    private HitRegion hitFor(String branchId) {
        for (HitRegion region : sink.last.layout.hits()) {
            if (region.branchId.equals(branchId)) {
                return region;
            }
        }
        return null;
    }

    @Test
    public void everyPlateStaysInsideItsRowAndClearOfTheCrown() {
        started();
        for (LabelGeometry label : sink.last.layout.labels()) {
            assertTrue(label.row >= 0);
            assertTrue(label.width > 0f && label.height > 0f);
            assertTrue("a plate must not be drawn above the padding",
                    label.y + label.height <= label.leaderY + EPS);
        }
    }
}