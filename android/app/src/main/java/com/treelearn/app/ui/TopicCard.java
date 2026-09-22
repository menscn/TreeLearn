package com.treelearn.app.ui;

import com.treelearn.core.domain.Guard;

/**
 * One row of the home topic list (PRD 6.2.16 point 4).
 *
 * <p>The display identity is the <em>root branch title</em>, never a name of the topic's own: the
 * domain deliberately has exactly one place a tree name can come from (BASE-06).
 *
 * <p>{@link #summary} is empty while the root summary has not been produced - a topic exists and is
 * readable before any AI-derived artifact does, so an empty summary is an ordinary state and not a
 * missing field.
 */
public final class TopicCard {

    public final String topicId;
    /** Where a tap resumes: the branch the learner last opened, or the root branch if none. */
    public final String resumeBranchId;
    public final String title;
    /** Empty until the root summary exists. */
    public final String summary;
    public final int branchCount;
    /** Sum of each branch's derived unread gap; zero is a real, already-read state. */
    public final long unreadCount;
    /** Epoch millis of the last use; the screen formats it in the device's own zone. */
    public final long lastUsedAt;

    public TopicCard(String topicId, String resumeBranchId, String title, String summary,
            int branchCount, long lastUsedAt) {
        this(topicId, resumeBranchId, title, summary, branchCount, 0L, lastUsedAt);
    }

    public TopicCard(String topicId, String resumeBranchId, String title, String summary,
            int branchCount, long unreadCount, long lastUsedAt) {
        this.topicId = Guard.id(topicId, "topicId");
        this.resumeBranchId = Guard.id(resumeBranchId, "resumeBranchId");
        this.title = Guard.nonBlank(title, "title");
        this.summary = summary == null ? "" : summary;
        this.branchCount = Guard.atLeast(branchCount, 0, "branchCount");
        this.unreadCount = Guard.nonNegative(unreadCount, "unreadCount");
        this.lastUsedAt = Guard.epochMillis(lastUsedAt, "lastUsedAt");
    }
}
