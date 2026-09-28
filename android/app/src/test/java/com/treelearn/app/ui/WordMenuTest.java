package com.treelearn.app.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;

/**
 * The long-press selection menu: which of the four operations it offers, which of them are pressable,
 * and where it lands (PRD 6.2.19).
 *
 * <p>"Not offered" and "not pressable" are different answers here, and the difference is a
 * requirement rather than a rendering choice: PRD 6.2.19 exception 1 takes 新建主题 off a
 * half-finished answer entirely, while 引用 is still drawn for one - greyed, because the store would
 * refuse it - so that the menu does not silently swallow the tap.
 */
public final class WordMenuTest {

    private static final String SELECTED = "the dot products grow with d";
    private static final float HEIGHT = 200f;
    private static final float WINDOW_H = 2000f;

    @Test
    public void theFourOperationsAreOfferedInTheTwoColumnsThePrototypeDraws() {
        List<WordMenu.Entry> entries = WordMenu.entriesFor(stable(), SELECTED);

        assertEquals(Arrays.asList(WordMenu.Item.QUOTE, WordMenu.Item.FORK, WordMenu.Item.COPY,
                WordMenu.Item.SELECT_ALL), items(entries));
        assertEquals(WordMenu.Group.AI, WordMenu.Item.QUOTE.group);
        assertEquals(WordMenu.Group.AI, WordMenu.Item.FORK.group);
        assertEquals(WordMenu.Group.SYSTEM, WordMenu.Item.COPY.group);
        assertEquals(WordMenu.Group.SYSTEM, WordMenu.Item.SELECT_ALL.group);
        for (WordMenu.Entry entry : entries) {
            assertTrue(entry.item + " must be pressable on a finished answer", entry.enabled);
        }
    }

    @Test
    public void aHalfFinishedAnswerIsNotOfferedANewTopicAtAll() {
        for (ChatMessage unfinished : Arrays.asList(generating(), failed())) {
            List<WordMenu.Entry> entries = WordMenu.entriesFor(unfinished, SELECTED);

            assertFalse("a partial answer must not offer a new topic (新建主题)",
                    items(entries).contains(WordMenu.Item.FORK));
            assertFalse(enabled(entries, WordMenu.Item.QUOTE));
            assertTrue("a partial answer is still readable material",
                    enabled(entries, WordMenu.Item.COPY));
            assertTrue(enabled(entries, WordMenu.Item.SELECT_ALL));
        }
    }

    @Test
    public void quoteCopyAndForkAllFollowTheSelectionThatIsActuallyThere() {
        List<WordMenu.Entry> entries = WordMenu.entriesFor(stable(), "");

        assertFalse(enabled(entries, WordMenu.Item.QUOTE));
        assertFalse(enabled(entries, WordMenu.Item.COPY));
        assertTrue("the fork item exists, it just has nothing to fork from yet",
                items(entries).contains(WordMenu.Item.FORK));
        assertFalse(enabled(entries, WordMenu.Item.FORK));
        assertTrue("select-all needs no selection to start from",
                enabled(entries, WordMenu.Item.SELECT_ALL));
    }

    @Test
    public void theMenuOpensAboveTheSelectionWhenThereIsRoomThere() {
        float top = WordMenu.topFor(1000f, 1040f, HEIGHT, WINDOW_H);

        assertEquals(792f, top, 0.001f);
        assertTrue("the menu must not cover the sentence that opened it",
                top + HEIGHT <= 1000f);
    }

    @Test
    public void theMenuDropsBelowTheSelectionWhenThereIsNoRoomAbove() {
        float top = WordMenu.topFor(20f, 60f, HEIGHT, WINDOW_H);

        assertEquals(68f, top, 0.001f);
        assertTrue("with no room above the menu opens instead of overlapping",
                top >= 60f);
    }

    @Test
    public void theMenuNeverCoversTheSelectionAtAnyAnchor() {
        for (float selectionTop = 0f; selectionTop < WINDOW_H; selectionTop += 37f) {
            float selectionBottom = selectionTop + 40f;
            float top = WordMenu.topFor(selectionTop, selectionBottom, HEIGHT, WINDOW_H);

            assertTrue("menu at " + top + " covers a selection at " + selectionTop,
                    top + HEIGHT <= selectionTop + 0.001f || top >= selectionBottom - 0.001f);
        }
    }

    @Test
    public void theMenuStaysInsideTheWindowOnBothAxes() {
        float top = WordMenu.topFor(WINDOW_H - 30f, WINDOW_H - 10f, HEIGHT, WINDOW_H);

        assertTrue(top >= WordMenu.MARGIN);
        assertTrue("a menu that fits must be drawn whole", top + HEIGHT <= WINDOW_H - WordMenu.MARGIN);
        assertEquals(WordMenu.MARGIN, WordMenu.leftFor(5f, 240f, 1000f), 0.001f);
        assertEquals(1000f - 240f - WordMenu.MARGIN, WordMenu.leftFor(990f, 240f, 1000f), 0.001f);
    }

    private static List<WordMenu.Item> items(List<WordMenu.Entry> entries) {
        List<WordMenu.Item> items = new ArrayList<WordMenu.Item>();
        for (WordMenu.Entry entry : entries) {
            items.add(entry.item);
        }
        return items;
    }

    private static boolean enabled(List<WordMenu.Entry> entries, WordMenu.Item item) {
        for (WordMenu.Entry entry : entries) {
            if (entry.item == item) {
                return entry.enabled;
            }
        }
        return false;
    }

    private static ChatMessage stable() {
        return ChatTestDoubles.stableAnswer(ChatTestDoubles.M_ANSWER, ChatTestDoubles.ANSWER);
    }

    private static ChatMessage generating() {
        return ChatMessage.ai(ChatTestDoubles.M_ANSWER, "half an answer", "half a thought",
                ChatMessage.Status.GENERATING, ChatMessage.Phase.ANSWER, "", 0, 0, true, 1L);
    }

    private static ChatMessage failed() {
        return ChatMessage.ai(ChatTestDoubles.M_ANSWER, "half an answer", "half a thought",
                ChatMessage.Status.FAILED, ChatMessage.Phase.ANSWER, "", 0, 0, false, 1L);
    }
}
