package com.treelearn.app.ui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Everything the home screen reads from the store, behind one port.
 *
 * <p>The port exists because the home screen must be drawable and testable without a database: the
 * presenter asks these two questions and renders the answers, and a JVM test answers them from a
 * list.
 *
 * <p><strong>Failures.</strong> {@link #recentTopics()} may throw - a store that cannot be read is a
 * real state, and the presenter shows its error phase with the draft untouched. {@link
 * #recommendations(boolean)} must <em>not</em> throw for a generation failure: PRD 6.2.17 declares
 * that a failed recommendation is answered with the fallback starter set, so it never reaches the
 * screen as an error. The presenter still tolerates a throw from it - a store-side failure is not a
 * generation failure - and then drops the strip, which is the PRD's other allowed answer.
 */
public interface HomeData {

    /** Recently used topics, newest first. Never null; empty means "no topic yet". */
    List<TopicCard> recentTopics();

    /**
     * The recommendation strip for the home screen.
     *
     * @param hasTopics true when the learner already has topics, which is what selects the
     *        history-derived set over the starter set (PRD 6.2.17 point 3).
     */
    Recommendations recommendations(boolean hasTopics);

    default HomeStats stats() {
        throw new UnsupportedOperationException("stats is not implemented by this data port");
    }

    /**
     * A non-empty strip plus the fact the screen uses to pick its heading.
     *
     * <p>Both factories refuse an empty strip, so "the recommendation area is blank" is not
     * expressible here: PRD 6.2.17 point 2 and its exception 2 require a usable starting point
     * whenever the strip is shown at all.
     */
    final class Recommendations {

        /** Never empty; unmodifiable. */
        public final List<Recommendation> items;
        /** True when derived from the learner's own topics, false for the starter fallback. */
        public final boolean fromHistory;

        private Recommendations(List<Recommendation> items, boolean fromHistory) {
            if (items == null || items.isEmpty()) {
                throw new IllegalArgumentException(
                        "a recommendation strip must offer at least one starting point");
            }
            this.items = Collections.unmodifiableList(new ArrayList<Recommendation>(items));
            this.fromHistory = fromHistory;
        }

        public static Recommendations fromHistory(List<Recommendation> items) {
            return new Recommendations(items, true);
        }

        public static Recommendations starterFallback(List<Recommendation> items) {
            return new Recommendations(items, false);
        }
    }
}
