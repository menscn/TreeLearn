/*
 * Decompiled with CFR 0.152.
 */
package com.treelearn.app.ui;

import com.treelearn.app.ui.ChatMessage;
import java.util.ArrayList;
import java.util.List;

public final class WordMenu {
    public static final float MARGIN = 8.0f;

    private WordMenu() {
    }

    public static List<Entry> entriesFor(ChatMessage message, String selection) {
        boolean selected = selection != null && !selection.trim().isEmpty();
        ArrayList<Entry> entries = new ArrayList<Entry>(4);
        entries.add(new Entry(Item.QUOTE, message.canQuote() && selected));
        if (message.canFork()) {
            entries.add(new Entry(Item.FORK, message.canFork() && selected));
        }
        entries.add(new Entry(Item.COPY, selected));
        entries.add(new Entry(Item.SELECT_ALL, true));
        return entries;
    }

    public static float topFor(float selectionTop, float selectionBottom, float menuHeight, float windowHeight) {
        float above = selectionTop - menuHeight - 8.0f;
        float top = above >= 8.0f ? above : selectionBottom + 8.0f;
        return WordMenu.clamp(top, 8.0f, windowHeight - menuHeight - 8.0f);
    }

    public static float leftFor(float selectionCentreX, float menuWidth, float windowWidth) {
        return WordMenu.clamp(selectionCentreX - menuWidth / 2.0f, 8.0f, windowWidth - menuWidth - 8.0f);
    }

    private static float clamp(float value, float minimum, float maximum) {
        if (maximum < minimum) {
            return minimum;
        }
        return Math.max(minimum, Math.min(maximum, value));
    }

    public static final class Entry {
        public final Item item;
        public final boolean enabled;

        public Entry(Item item, boolean enabled) {
            this.item = item;
            this.enabled = enabled;
        }
    }

    public static enum Item {
        QUOTE(Group.AI),
        FORK(Group.AI),
        COPY(Group.SYSTEM),
        SELECT_ALL(Group.SYSTEM);

        public final Group group;

        private Item(Group group) {
            this.group = group;
        }
    }

    public static enum Group {
        AI,
        SYSTEM;

    }
}

