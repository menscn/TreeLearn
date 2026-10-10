package com.treelearn.app.screen;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class ChildBranchPopupPlacementTest {

    @Test
    public void prefersAboveAnchorWhenBottomSpaceIsInsufficient() {
        assertEquals(180, ChildBranchPopup.topFor(500, 544, 312, 520, 8));
    }

    @Test
    public void clampsPopupInsideWindowWhenItDoesNotFitEitherSide() {
        assertEquals(8, ChildBranchPopup.topFor(120, 164, 600, 520, 8));
        assertEquals(8, ChildBranchPopup.leftFor(4, 360, 520, 8));
        assertEquals(152, ChildBranchPopup.leftFor(500, 360, 520, 8));
    }
}
