package com.treelearn.app.ui;

import com.treelearn.core.domain.Branch;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Everything the canvas reads, behind one port.
 *
 * <p>The canvas needs two things the navigation list does not carry: the branch itself, because its
 * unread pair is what the badge is computed from, and the two derived values that only the AI slice
 * produces - the summary first line and the number of committed rounds, which is what the segment
 * length is drawn from.
 *
 * <p>The read hands back domain objects rather than rows of its own. {@link Branch} already holds the
 * seen/unseen sequence pair, the fold flag and the archive timestamp, so a canvas that re-stated them
 * in a second shape would be the second entry point that disagrees with the first.
 */
public interface TreeCanvasData {

    /** One branch of the topic, with the two derived values the geometry reads. */
    final class Row {

        public final Branch branch;
        /** The branch's summary first line; empty when it has none yet. Never null. */
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

    /** One topic's branches in the caller's order; the root is the row without a parent. */
    final class Tree {

        public final String topicId;
        public final List<Row> rows;

        public Tree(String topicId, List<Row> rows) {
            if (topicId == null || topicId.isEmpty()) {
                throw new IllegalArgumentException("topicId must not be blank");
            }
            this.topicId = topicId;
            this.rows = Collections.unmodifiableList(
                    new ArrayList<Row>(rows == null ? Collections.<Row>emptyList() : rows));
        }
    }

    /** @return the topic a branch belongs to, or null when the branch is gone */
    String topicOf(String branchId);

    /** The whole tree of one topic. Throws when the store cannot be read. */
    Tree load(String topicId);
}