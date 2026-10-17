package com.treelearn.app.screen;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.treelearn.app.R;
import com.treelearn.app.ui.HomeViewState;

import org.junit.Test;

/**
 * Which categories are drawn as a failure and which as a note. The bar used to be one red line for
 * every category, so "the chosen model is gone but this answer still came" read with the same alarm
 * as "storage refused the write". The quiet set is pinned by name: a category added to it, or left
 * out of it, fails here rather than only looking different on a screen.
 */
public class NoticeColourTest {

    @Test
    public void theQuietSetIsExactlyTheNoteThatReportsNothingLost() {
        for (HomeViewState.Notice value : HomeViewState.Notice.values()) {
            int expected = value == HomeViewState.Notice.CHAT_MODEL_FALLBACK
                    ? R.color.tl_ink3 : R.color.tl_red;
            assertEquals(value.name(), expected, NoticeText.colorResOf(value));
        }
    }

    @Test
    public void everyCategoryGetsADefinedColourAndText() {
        for (HomeViewState.Notice value : HomeViewState.Notice.values()) {
            int colour = NoticeText.colorResOf(value);
            assertEquals(value.name() + " is drawn in neither of the two colours",
                    R.color.tl_red == colour || R.color.tl_ink3 == colour, true);
            assertTrue(value.name() + " has no sentence", NoticeText.of(value) != 0);
        }
    }

    @Test
    public void theModelFallbackLineIsANoteNotAnError() {
        assertEquals(R.color.tl_ink3,
                NoticeText.colorResOf(HomeViewState.Notice.CHAT_MODEL_FALLBACK));
    }

    @Test
    public void aRefusedWriteIsStillRed() {
        assertEquals(R.color.tl_red, NoticeText.colorResOf(HomeViewState.Notice.STORAGE));
        assertEquals(R.color.tl_red, NoticeText.colorResOf(HomeViewState.Notice.KEY_MISSING));
        assertEquals(R.color.tl_red,
                NoticeText.colorResOf(HomeViewState.Notice.ATTACHMENT_TOO_LARGE));
    }
}
