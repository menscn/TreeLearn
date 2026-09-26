/*
 * Decompiled with CFR 0.152.
 */
package com.treelearn.app.ui;

import com.treelearn.app.ui.HomeStats;
import com.treelearn.app.ui.Recommendation;
import com.treelearn.app.ui.TopicCard;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public interface HomeData {
    default public void renameTopic(String topicId, String title, long now) {
        throw new UnsupportedOperationException("renameTopic is not implemented by this data port");
    }

    default public void deleteTopic(String topicId) {
        throw new UnsupportedOperationException("deleteTopic is not implemented by this data port");
    }

    public List<TopicCard> recentTopics();

    public Recommendations recommendations(boolean var1);

    default public HomeStats stats() {
        throw new UnsupportedOperationException("stats is not implemented by this data port");
    }

    public static final class Recommendations {
        public final List<Recommendation> items;
        public final boolean fromHistory;

        private Recommendations(List<Recommendation> items, boolean fromHistory) {
            if (items == null || items.isEmpty()) {
                throw new IllegalArgumentException("a recommendation strip must offer at least one starting point");
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

