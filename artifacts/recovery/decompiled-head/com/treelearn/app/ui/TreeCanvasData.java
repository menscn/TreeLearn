/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.treelearn.core.domain.Branch
 */
package com.treelearn.app.ui;

import com.treelearn.core.domain.Branch;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public interface TreeCanvasData {
    public String topicOf(String var1);

    public Tree load(String var1);

    public static final class Tree {
        public final String topicId;
        public final List<Row> rows;

        public Tree(String topicId, List<Row> rows) {
            if (topicId == null || topicId.isEmpty()) {
                throw new IllegalArgumentException("topicId must not be blank");
            }
            this.topicId = topicId;
            this.rows = Collections.unmodifiableList(new ArrayList(rows == null ? Collections.emptyList() : rows));
        }
    }

    public static final class Row {
        public final Branch branch;
        public final String summary;
        public final int rounds;

        public Row(Branch branch, String summary, int rounds) {
            if (branch == null) {
                throw new IllegalArgumentException("branch must not be null");
            }
            if (rounds < 0) {
                throw new IllegalArgumentException("rounds must not be negative: " + rounds);
            }
            this.branch = branch;
            this.summary = summary == null ? "" : summary;
            this.rounds = rounds;
        }
    }
}

