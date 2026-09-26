/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.treelearn.core.domain.Guard
 */
package com.treelearn.app.ui;

import com.treelearn.core.domain.Guard;

public final class TopicCard {
    public final String topicId;
    public final String resumeBranchId;
    public final String title;
    public final String summary;
    public final int branchCount;
    public final long unreadCount;
    public final long lastUsedAt;

    public TopicCard(String topicId, String resumeBranchId, String title, String summary, int branchCount, long lastUsedAt) {
        this(topicId, resumeBranchId, title, summary, branchCount, 0L, lastUsedAt);
    }

    public TopicCard(String topicId, String resumeBranchId, String title, String summary, int branchCount, long unreadCount, long lastUsedAt) {
        this.topicId = Guard.id((String)topicId, (String)"topicId");
        this.resumeBranchId = Guard.id((String)resumeBranchId, (String)"resumeBranchId");
        this.title = Guard.nonBlank((String)title, (String)"title");
        this.summary = summary == null ? "" : summary;
        this.branchCount = Guard.atLeast((int)branchCount, (int)0, (String)"branchCount");
        this.unreadCount = Guard.nonNegative((long)unreadCount, (String)"unreadCount");
        this.lastUsedAt = Guard.epochMillis((long)lastUsedAt, (String)"lastUsedAt");
    }
}

