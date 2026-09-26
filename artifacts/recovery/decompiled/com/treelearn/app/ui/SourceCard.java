/*
 * Decompiled with CFR 0.152.
 */
package com.treelearn.app.ui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class SourceCard {
    public final String summary;
    public final List<Entry> history;

    public SourceCard(String summary, List<Entry> history) {
        this.summary = summary == null ? "" : summary;
        this.history = Collections.unmodifiableList(new ArrayList<Entry>(history));
    }

    public static final class Entry {
        public final boolean user;
        public final String text;

        private Entry(boolean user, String text) {
            this.user = user;
            this.text = text == null ? "" : text;
        }

        public static Entry user(String text) {
            return new Entry(true, text);
        }

        public static Entry answer(String text) {
            return new Entry(false, text);
        }
    }
}

