/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.treelearn.core.domain.Guard
 */
package com.treelearn.app.library;

import com.treelearn.app.navigation.NavigationData;
import com.treelearn.core.domain.Guard;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class LibraryViewState {
    public final Phase phase;
    public final Segment segment;
    public final List<Row> rows;
    public final List<ArchiveRow> archived;
    public final NavigationData.Plan pendingDelete;

    private LibraryViewState(Phase phase, Segment segment, List<Row> rows, List<ArchiveRow> archived, NavigationData.Plan pendingDelete) {
        this.phase = phase;
        this.segment = segment;
        this.rows = Collections.unmodifiableList(new ArrayList<Row>(rows));
        this.archived = Collections.unmodifiableList(new ArrayList<ArchiveRow>(archived));
        this.pendingDelete = pendingDelete;
    }

    public static LibraryViewState initial() {
        return new LibraryViewState(Phase.LOADING, Segment.IN_PROGRESS, Collections.emptyList(), Collections.emptyList(), null);
    }

    public LibraryViewState withPhase(Phase value) {
        return new LibraryViewState(value, this.segment, this.rows, this.archived, this.pendingDelete);
    }

    public LibraryViewState withTopics(List<Row> topics) {
        List read = (List)Guard.object(topics, (String)"topics");
        return new LibraryViewState(read.isEmpty() ? Phase.EMPTY : Phase.READY, this.segment, read, this.archived, this.pendingDelete);
    }

    public LibraryViewState withArchived(List<ArchiveRow> value) {
        return new LibraryViewState(this.phase, this.segment, this.rows, (List)Guard.object(value, (String)"archived"), this.pendingDelete);
    }

    public LibraryViewState withSegment(Segment value) {
        return new LibraryViewState(this.phase, (Segment)((Object)Guard.object((Object)((Object)value), (String)"segment")), this.rows, this.archived, this.pendingDelete);
    }

    public LibraryViewState withPendingDelete(NavigationData.Plan value) {
        return new LibraryViewState(this.phase, this.segment, this.rows, this.archived, value);
    }

    public static enum Phase {
        LOADING,
        READY,
        EMPTY,
        ERROR;

    }

    public static enum Segment {
        IN_PROGRESS,
        DONE;

    }

    public static final class ArchiveRow {
        public final String branchId;
        public final String topicId;
        public final String title;
        public final String sourcePath;
        public final String summary;

        public ArchiveRow(String branchId, String topicId, String title, String sourcePath, String summary) {
            this.branchId = Guard.id((String)branchId, (String)"branchId");
            this.topicId = Guard.id((String)topicId, (String)"topicId");
            this.title = Guard.text((String)title, (String)"title");
            this.sourcePath = Guard.text((String)sourcePath, (String)"sourcePath");
            this.summary = summary;
        }
    }

    public static final class Row {
        public final String topicId;
        public final String resumeBranchId;
        public final String title;
        public final int branchCount;
        public final long unreadCount;
        public final long lastUsedAt;

        public Row(String topicId, String resumeBranchId, String title, int branchCount, long unreadCount, long lastUsedAt) {
            this.topicId = Guard.id((String)topicId, (String)"topicId");
            this.resumeBranchId = Guard.id((String)resumeBranchId, (String)"resumeBranchId");
            this.title = Guard.text((String)title, (String)"title");
            this.branchCount = Guard.atLeast((int)branchCount, (int)0, (String)"branchCount");
            this.unreadCount = Guard.nonNegative((long)unreadCount, (String)"unreadCount");
            this.lastUsedAt = Guard.epochMillis((long)lastUsedAt, (String)"lastUsedAt");
        }
    }
}

