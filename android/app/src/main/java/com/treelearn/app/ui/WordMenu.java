package com.treelearn.app.ui;

import java.util.ArrayList;
import java.util.List;

/**
 * The menu a long press opens: which of the four operations it offers, which of them are pressable,
 * and where it lands (PRD 6.2.19).
 *
 * <p><strong>The enablement is read, never decided here.</strong> Every item takes its flag from the
 * message's own predicate, so the menu offers exactly what the store would accept: 新建分支 is
 * <em>absent</em> while an answer is unfinished, which is PRD 6.2.19 exception 1 rather than a
 * rendering choice, and 引用 is drawn but greyed, so a tap the store would refuse is visibly refused
 * instead of silently swallowed.
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

    /** The semantic split PRD 6.2.19 point 2 draws: what the app does with the answer, and what the system does. */
    public enum Group {
        AI,
        SYSTEM
    }

    public enum Item {
        QUOTE(Group.AI),
        FORK(Group.AI),
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

    /** Gap kept between the menu and the window edge, and between it and the selected sentence. */
    public static final float MARGIN = 8f;

    private WordMenu() {
    }

    /** The items to draw for one answer, in menu order, with the flag each item is pressable at. */
    public static List<Entry> entriesFor(ChatMessage message, String selection) {
        boolean selected = selection != null && !selection.trim().isEmpty();
        List<Entry> entries = new ArrayList<Entry>(4);
        entries.add(new Entry(Item.QUOTE, message.canQuote() && selected));
        if (message.canFork()) {
            entries.add(new Entry(Item.FORK, message.canFork() && selected));
        }
        entries.add(new Entry(Item.COPY, selected));
        entries.add(new Entry(Item.SELECT_ALL, true));
        return entries;
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
