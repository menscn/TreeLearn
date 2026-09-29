package com.treelearn.app.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;

/**
 * The long-press selection menu: which of the six primary operations it offers, which of them are pressable,
 * where it lands, and how it splits when the window has no room for all six (PRD 6.2.19, 方案 V1.1
 * §2.1 for the mark pair).
 *
 * <p>"Not offered" and "not pressable" are different answers here, and the difference is a
 * requirement rather than a rendering choice: PRD 6.2.19 exception 1 takes 新建主题 off a
 * half-finished answer entirely, while 引用 is still drawn for one - greyed, because the store would
 * refuse it - so that the menu does not silently swallow the tap. The mark pair (高亮 / 批注) rides
 * the same quote rule while the answer streams (方案 §2.1), and needs a selection like every other
 * mark once the answer is stable.
 *
 * <p>The six cells are the terminal state and 更多 is a fallback, not a menu: 更多 is deliberately
 * absent from {@link WordMenu.Item}, and the fold it stands for is decided from numbers alone, so a
 * narrow phone's row is asserted here without a device.
 */
public final class WordMenuTest {

    private static final String SELECTED = "the dot products grow with d";
    private static final float HEIGHT = 200f;
    private static final float WINDOW_H = 2000f;

    /** The card's own measures, as popup_word_action.xml draws them: cell, hairline, padding. */
    private static final int CELL = 44;
    private static final int SEPARATOR = 1;
    private static final int PADDING = 4;

    @Test
    public void theSixPrimaryOperationsAreOfferedInTheOrderThePrototypeDraws() {
        List<WordMenu.Entry> entries = WordMenu.entriesFor(stable(), SELECTED);

        // 2026-09-28 terminal state: 问AI | 新建主题 | 高亮 | 批注 | 复制 | 全选. 更多 is not among them:
        // it is the card a narrow window folds the tail into, never an operation of its own.
        assertEquals(Arrays.asList(WordMenu.Item.QUOTE, WordMenu.Item.FORK,
                WordMenu.Item.HIGHLIGHT, WordMenu.Item.NOTE, WordMenu.Item.COPY,
                WordMenu.Item.SELECT_ALL), items(entries));
        assertEquals("更多 must not be an operation of the menu", 6, WordMenu.Item.values().length);
        assertEquals(WordMenu.Group.AI, WordMenu.Item.QUOTE.group);
        assertEquals(WordMenu.Group.AI, WordMenu.Item.FORK.group);
        assertEquals(WordMenu.Group.MARK, WordMenu.Item.HIGHLIGHT.group);
        assertEquals(WordMenu.Group.MARK, WordMenu.Item.NOTE.group);
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
            assertEquals("新建主题 is the one cell a partial answer loses", 5, entries.size());
            assertFalse(enabled(entries, WordMenu.Item.QUOTE));
            assertTrue("the mark items are drawn grey, not hidden (the quote rule, 方案 §2.1)",
                    items(entries).contains(WordMenu.Item.HIGHLIGHT)
                            && items(entries).contains(WordMenu.Item.NOTE));
            assertFalse("a partial answer cannot be marked yet (the store would refuse it)",
                    enabled(entries, WordMenu.Item.HIGHLIGHT));
            assertFalse(enabled(entries, WordMenu.Item.NOTE));
            assertTrue("copy survives on a partial answer - exception 1 keeps a selection copyable",
                    enabled(entries, WordMenu.Item.COPY));
            assertTrue("全选 needs neither a selection nor a finished answer",
                    enabled(entries, WordMenu.Item.SELECT_ALL));
        }
    }

    @Test
    public void quoteCopyAndForkAllFollowTheSelectionThatIsActuallyThere() {
        List<WordMenu.Entry> entries = WordMenu.entriesFor(stable(), "");

        assertFalse(enabled(entries, WordMenu.Item.QUOTE));
        assertFalse("no selection, no mark to place",
                enabled(entries, WordMenu.Item.HIGHLIGHT));
        assertFalse(enabled(entries, WordMenu.Item.NOTE));
        assertFalse("copy copies the passage, so with no passage there is nothing to copy",
                enabled(entries, WordMenu.Item.COPY));
        assertTrue("the fork item exists, it just has nothing to fork from yet",
                items(entries).contains(WordMenu.Item.FORK));
        assertFalse(enabled(entries, WordMenu.Item.FORK));
        assertTrue("全选 is how a learner gets a selection, so it is pressable before there is one",
                enabled(entries, WordMenu.Item.SELECT_ALL));
    }

    @Test
    public void aRowThatFitsTheWindowIsDrawnWholeRatherThanFolded() {
        List<WordMenu.Entry> entries = WordMenu.entriesFor(stable(), SELECTED);
        int room = WordMenu.rowFor(entries.size(), CELL, SEPARATOR, PADDING);

        WordMenu.Fit fit = WordMenu.fitFor(entries, room, CELL, SEPARATOR, PADDING);

        assertFalse("更多 is a width fallback, so a row that fits must not ask for it", fit.folded());
        assertEquals(items(entries), items(fit.primary));
        assertTrue(fit.overflow.isEmpty());
    }

    @Test
    public void aNarrowWindowFoldsTheTailIntoMoreAndKeepsEveryOperation() {
        List<WordMenu.Entry> entries = WordMenu.entriesFor(stable(), SELECTED);
        // One pixel short of the six cells: the row has to give up a cell, and gives up the last one.
        WordMenu.Fit fit = WordMenu.fitFor(entries,
                WordMenu.rowFor(entries.size(), CELL, SEPARATOR, PADDING) - 1,
                CELL, SEPARATOR, PADDING);

        assertTrue(fit.folded());
        assertEquals(Arrays.asList(WordMenu.Item.QUOTE, WordMenu.Item.FORK,
                WordMenu.Item.HIGHLIGHT, WordMenu.Item.NOTE), items(fit.primary));
        assertEquals(Arrays.asList(WordMenu.Item.COPY, WordMenu.Item.SELECT_ALL),
                items(fit.overflow));
    }

    @Test
    public void everyWidthKeepsTheSixOperationsInOrderAndLosesNoneOfThem() {
        List<WordMenu.Entry> entries = WordMenu.entriesFor(stable(), SELECTED);
        int widest = WordMenu.rowFor(entries.size(), CELL, SEPARATOR, PADDING);

        for (int available = widest + CELL; available >= 0; available -= 3) {
            WordMenu.Fit fit = WordMenu.fitFor(entries, available, CELL, SEPARATOR, PADDING);
            List<WordMenu.Item> drawn = new ArrayList<WordMenu.Item>(items(fit.primary));
            drawn.addAll(items(fit.overflow));

            assertEquals("a " + available + "px window must still offer every operation",
                    items(entries), drawn);
            assertTrue("the row keeps at least one cell at any width", !fit.primary.isEmpty());
            assertEquals("问 AI is the first cell and the last thing the fold gives up",
                    WordMenu.Item.QUOTE, fit.primary.get(0).item);
            int row = WordMenu.rowFor(fit.primary.size() + (fit.folded() ? 1 : 0),
                    CELL, SEPARATOR, PADDING);
            assertTrue("the row it chose must actually fit in " + available + "px: " + row,
                    row <= available || fit.primary.size() == 1);
            assertTrue("a row that fits must never be folded",
                    widest > available || !fit.folded());
        }
    }

    @Test
    public void theRowKeepsOneCellEvenWhenTheWindowIsTooNarrowForTwo() {
        List<WordMenu.Entry> entries = WordMenu.entriesFor(stable(), SELECTED);

        WordMenu.Fit fit = WordMenu.fitFor(entries, CELL, CELL, SEPARATOR, PADDING);

        assertEquals("the last answer is a single cell plus 更多, never an empty row",
                Arrays.asList(WordMenu.Item.QUOTE), items(fit.primary));
        assertEquals(entries.size() - 1, fit.overflow.size());
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
