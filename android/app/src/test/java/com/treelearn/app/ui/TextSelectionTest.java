package com.treelearn.app.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * The two things the word menu asks of a selection: whether there is one at all, and what "全选"
 * selects (PRD 6.2.19 points 1 and 6).
 *
 * <p>A text view that has never been touched answers {@code -1} for both ends, so the empty case is
 * a shape the view really produces and not a defensive branch.
 */
public final class TextSelectionTest {

    @Test
    public void aViewWithNothingSelectedReadsAsTheEmptyRange() {
        assertTrue(TextSelection.of(-1, -1).isEmpty());
        assertTrue("a start that has not moved past the end is not a selection", 
                TextSelection.of(5, 5).isEmpty());
        assertTrue(TextSelection.of(9, 4).isEmpty());
    }

    @Test
    public void aSelectionReadsExactlyTheCharactersItCovers() {
        assertEquals("cde", TextSelection.of(2, 5).read("abcdefg"));
    }

    @Test
    public void theEmptyRangeReadsAsTheEmptyString() {
        assertEquals("", TextSelection.of(-1, -1).read("abcdefg"));
        assertEquals("", TextSelection.whole(0).read("")); 
    }

    @Test
    public void selectAllIsTheWholeAnswer() {
        String answer = "because the dot products grow with d";

        TextSelection whole = TextSelection.whole(answer.length());

        assertEquals(0, whole.start);
        assertEquals(answer.length(), whole.end);
        assertEquals(answer, whole.read(answer));
        assertFalse(whole.isEmpty());
    }

    @Test
    public void aSelectionThatOutranTheTextReadsAsNothing() {
        assertEquals("", TextSelection.of(2, 99).read("abc"));
    }

    @Test
    public void aLongPressSelectsTheSentenceItLandedIn() {
        String answer = "缩放是因为点积随 d 增长。其余项不变。";

        assertEquals("缩放是因为点积随 d 增长。",
                TextSelection.sentence(answer, answer.indexOf("缩放")).read(answer));
        assertEquals("其余项不变。",
                TextSelection.sentence(answer, answer.indexOf("其余")).read(answer));
    }

    @Test
    public void aSentenceEndsAtALineBreakOrAClosingAsciiStop() {
        String lines = "first line\nsecond line";
        assertEquals("first line", TextSelection.sentence(lines, 3).read(lines));

        String prose = "It grows with d. That is why.";
        assertEquals("It grows with d.", TextSelection.sentence(prose, 3).read(prose));
    }

    @Test
    public void anAsciiStopInsideAWordDoesNotEndASentence() {
        String decimal = "版本为 V3.1 的说明";

        assertEquals(decimal, TextSelection.sentence(decimal, 6).read(decimal));
    }

    @Test
    public void anEmptyAnswerHasNoSentenceToSelect() {
        assertTrue(TextSelection.sentence("", 0).isEmpty());
        assertTrue(TextSelection.sentence(null, 4).isEmpty());
    }
}
