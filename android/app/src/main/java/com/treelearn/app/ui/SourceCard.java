package com.treelearn.app.ui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The read-only provenance card a non-root conversation draws at its top (T-065, PRD §6.2.5).
 *
 * <p>Collapsed it names where the branch came from - the anchored sentence when the fork carried
 * one, an excerpt of the source answer otherwise. Expanded it lists the upstream conversation the
 * branch was born with, in the order the birth snapshot pinned it. The rows are rendered only; the
 * card offers no action, and nothing here can be asked a follow-up - the upstream branch is the one
 * place those turns stay live (§6.2.5: the reading area of this branch shows this card and its own
 * rows, never the live rows of another branch).
 */
public final class SourceCard {

    /** The collapsed line: the anchored sentence, or an excerpt of the source answer. */
    public final String summary;

    /** The upstream history in snapshot order. Never null; unmodifiable. */
    public final List<Entry> history;

    public SourceCard(String summary, List<Entry> history) {
        this.summary = summary == null ? "" : summary;
        this.history = Collections.unmodifiableList(new ArrayList<Entry>(history));
    }

    /** One rendered line of the upstream history: who said it, and the text as stored. */
    public static final class Entry {

        /** True for a learner message, false for a stable answer version. */
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
