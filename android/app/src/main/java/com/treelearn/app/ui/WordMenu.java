package com.treelearn.app.ui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The menu a long press opens: which of the six primary operations it offers, which of them are
 * pressable, and where it lands (PRD 6.2.19, 方案 V1.1 §2.1 for the mark pair).
 *
 * <p><strong>Six cells in one row is the terminal state; 更多 is a width fallback, not a menu.</strong>
 * 2026-09-28 user decision: 复制 and 全选 are their own cells in the row - 问 AI, 新建主题, 高亮, 批注,
 * 复制, 全选 - and there is no 更多 submenu any more. What survives of 更多 is a fallback for the one
 * case the six cells cannot be drawn whole in: a narrow phone, a split-screen window, a folded
 * device. There the row keeps a prefix of the operations and 更多 carries the tail, so the learner
 * loses order and depth before they lose an operation. Which of the two shapes the row takes is
 * decided here, by {@link #fitFor}, from numbers alone - the width the window actually has, and the
 * cell the layout actually draws - so it is the same decision on every device, and it can be asserted
 * without one.
 *
 * <p><strong>The enablement is read, never decided here.</strong> Every item takes its flag from the
 * message's own predicate, so the menu offers exactly what the store would accept: 新建主题 is
 * <em>absent</em> while an answer is unfinished, which is PRD 6.2.19 exception 1 rather than a
 * rendering choice, and 引用 is drawn but greyed, so a tap the store would refuse is visibly refused
 * instead of silently swallowed. The mark pair (高亮 / 批注) rides the same rule - the store only
 * accepts annotation writes on a stable answer (T-026's caller duty) - so they are drawn but greyed
 * while the answer streams.
 *
 * <p><strong>复制 here is not the answer row's copy.</strong> PRD 6.2.19 point 5 copies the passage
 * the learner selected, so it is offered whenever there is a selection - including on a partial
 * answer, which exception 1 explicitly keeps copyable. The row's own 复制 copies the whole answer
 * including its reasoning and therefore needs a finished version (PRD 6.2.20 point 2).
 *
 * <p>The placement is here for the same reason the enablement is: a {@code PopupWindow} shows where
 * it is told to and the window it must stay inside is only known once it is attached, so the flip and
 * the clamp would otherwise be a rule reachable only from a device.
 */
public final class WordMenu {

    /**
     * The semantic split the prototype draws: what the app does with the answer, what the learner
     * marks on it, and what the system does (方案 V1.1 §2.1).
     */
    public enum Group {
        AI,
        MARK,
        SYSTEM
    }

    /**
     * The six operations the menu offers, in the order the row draws them.
     *
     * <p>更多 is deliberately not one of them: it is not an operation, it is the card that holds the
     * operations the row had no room for, and it never carries meaning of its own.
     */
    public enum Item {
        QUOTE(Group.AI),
        FORK(Group.AI),
        HIGHLIGHT(Group.MARK),
        NOTE(Group.MARK),
        COPY(Group.SYSTEM),
        SELECT_ALL(Group.SYSTEM);

        public final Group group;

        Item(Group group) {
            this.group = group;
        }
    }

    /** One row of the menu. "Not offered" and "offered but not pressable" are different answers. */
    public static final class Entry {

        public final Item item;
        public final boolean enabled;

        public Entry(Item item, boolean enabled) {
            this.item = item;
            this.enabled = enabled;
        }
    }

    /**
     * How the operations split between the row and 更多 at one width: the six of them in the row when
     * the row fits, a prefix plus a tail when it does not.
     */
    public static final class Fit {

        /** The entries the row draws, in menu order. */
        public final List<Entry> primary;

        /** The entries 更多 holds, in the same order; empty whenever the row is drawn whole. */
        public final List<Entry> overflow;

        private Fit(List<Entry> primary, List<Entry> overflow) {
            this.primary = primary;
            this.overflow = overflow;
        }

        /** True when the row had no room for every entry and 更多 stands in for the tail. */
        public boolean folded() {
            return !overflow.isEmpty();
        }
    }

    /** Gap kept between the menu and the window edge, and between it and the selected sentence. */
    public static final float MARGIN = 8f;

    private WordMenu() {
    }

    /** The items to draw for one answer, in menu order, with the flag each item is pressable at. */
    public static List<Entry> entriesFor(ChatMessage message, String selection) {
        boolean selected = selection != null && !selection.trim().isEmpty();
        boolean markable = message.canQuote() && selected;
        List<Entry> entries = new ArrayList<Entry>(6);
        entries.add(new Entry(Item.QUOTE, message.canQuote() && selected));
        if (message.canFork()) {
            entries.add(new Entry(Item.FORK, message.canFork() && selected));
        }
        entries.add(new Entry(Item.HIGHLIGHT, markable));
        entries.add(new Entry(Item.NOTE, markable));
        entries.add(new Entry(Item.COPY, selected));
        entries.add(new Entry(Item.SELECT_ALL, true));
        return entries;
    }

    /**
     * How the row splits at one width: every entry in the row when the row fits, and otherwise the
     * longest prefix that still leaves room for 更多 beside it, with the remaining tail in 更多.
     *
     * <p><strong>Every operation survives the fold.</strong> What the learner loses on a narrow screen
     * is depth - one more tap to reach 复制 - never an operation, which is why the overflow is the
     * tail in the same order rather than a hand-picked pair. The row always keeps at least one cell,
     * so a window too narrow for even 更多 plus one is still answered rather than drawn off screen.
     */
    public static Fit fitFor(List<Entry> entries, int available, int cell, int separator,
            int padding) {
        if (rowFor(entries.size(), cell, separator, padding) <= available) {
            return new Fit(entries, Collections.<Entry>emptyList());
        }
        int keep = 1;
        for (int k = entries.size() - 1; k >= 1; k--) {
            if (rowFor(k + 1, cell, separator, padding) <= available) {
                keep = k;
                break;
            }
        }
        return new Fit(new ArrayList<Entry>(entries.subList(0, keep)),
                new ArrayList<Entry>(entries.subList(keep, entries.size())));
    }

    /**
     * The width {@code cells} cells take inside the card: the card's own padding, the cells, and the
     * hairline between two neighbours. The same arithmetic the layout draws, so a row that measures
     * as fitting here does fit on screen.
     */
    public static int rowFor(int cells, int cell, int separator, int padding) {
        if (cells <= 0) {
            return 2 * padding;
        }
        return 2 * padding + cells * cell + (cells - 1) * separator;
    }

    /**
     * Where the menu opens vertically: above the selection when the room above it fits the menu whole,
     * below it otherwise, and clamped inside the window either way.
     */
    public static float topFor(float selectionTop, float selectionBottom, float menuHeight,
            float windowHeight) {
        float above = selectionTop - menuHeight - MARGIN;
        float top = above >= MARGIN ? above : selectionBottom + MARGIN;
        return clamp(top, MARGIN, windowHeight - menuHeight - MARGIN);
    }

    /** Where the menu opens horizontally: centred on the selection, and clamped inside the window. */
    public static float leftFor(float selectionCentreX, float menuWidth, float windowWidth) {
        return clamp(selectionCentreX - menuWidth / 2f, MARGIN, windowWidth - menuWidth - MARGIN);
    }

    /**
     * A menu larger than the window cannot be clamped inside it; it then sits at the margin rather than
     * at a negative coordinate, which is the one answer that keeps it on screen at all.
     */
    private static float clamp(float value, float minimum, float maximum) {
        if (maximum < minimum) {
            return minimum;
        }
        return Math.max(minimum, Math.min(maximum, value));
    }
}
