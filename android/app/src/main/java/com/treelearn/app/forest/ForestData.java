package com.treelearn.app.forest;

import com.treelearn.core.domain.Guard;
import com.treelearn.core.domain.Persona;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The forest page's read port (T-068), with no Android type in sight.
 *
 * <p>The topics themselves are the library's own list - {@code LibraryData.topics()} is the one
 * collection of "which trees exist", and this page must not grow a second one. Only the tallies the
 * library does not already carry travel through this port: the whole branch count, its archived
 * subset, and the styling. {@code countKnown == false} is the degradation of the whole forest
 * projection failing: the tree is still drawn, as one segment, and every total that would need the
 * missing number is dropped rather than summed short.
 */
public interface ForestData {

    Snapshot read();

    /** One topic as the ground draws it. */
    final class Topic {

        public final String topicId;
        public final String resumeBranchId;
        public final String title;
        /** Every branch of the topic, archived included; at least 1 while known. */
        public final int branchCount;
        /** The archived subset of {@link #branchCount}; -1 while unknown. */
        public final int archivedCount;
        public final long lastUsedAt;
        /** The topic's stored style; never null when the read succeeded. */
        public final Persona persona;
        /** False when the forest projection could not be read for this topic. */
        public final boolean countKnown;

        public Topic(String topicId, String resumeBranchId, String title, int branchCount,
                int archivedCount, long lastUsedAt, Persona persona, boolean countKnown) {
            this.topicId = Guard.id(topicId, "topicId");
            this.resumeBranchId = Guard.id(resumeBranchId, "resumeBranchId");
            this.title = Guard.nonBlank(title, "title");
            if (countKnown) {
                if (branchCount < 1) {
                    throw new IllegalArgumentException(
                            "a known topic has at least its root branch, got " + branchCount);
                }
                if (archivedCount < 0 || archivedCount > branchCount) {
                    throw new IllegalArgumentException("archivedCount " + archivedCount
                            + " must sit within branchCount " + branchCount);
                }
            }
            this.branchCount = branchCount;
            this.archivedCount = archivedCount;
            this.lastUsedAt = Guard.epochMillis(lastUsedAt, "lastUsedAt");
            this.persona = persona;
            this.countKnown = countKnown;
        }

        /** The count the ground draws with: unknown is one segment, never zero. */
        public int drawableBranchCount() {
            return countKnown && branchCount > 0 ? branchCount : 1;
        }
    }

    /** One read of the whole forest; {@code streakDays} is null when the stats read failed. */
    final class Snapshot {

        public final List<Topic> topics;
        public final Integer streakDays;

        public Snapshot(List<Topic> topics, Integer streakDays) {
            if (topics == null) {
                this.topics = Collections.emptyList();
            } else {
                List<Topic> copy = new ArrayList<Topic>(topics.size());
                for (Topic topic : topics) {
                    copy.add(Guard.object(topic, "topic"));
                }
                this.topics = Collections.unmodifiableList(copy);
            }
            this.streakDays = streakDays;
        }
    }
}
