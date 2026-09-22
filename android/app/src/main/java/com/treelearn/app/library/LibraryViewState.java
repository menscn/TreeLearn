package com.treelearn.app.library;

import com.treelearn.app.navigation.NavigationData;
import com.treelearn.core.domain.Guard;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Everything the library view draws, as one immutable value.
 *
 * <p>The screen holds no state of its own: whether the list is loading, empty, listed or unreadable,
 * which segment is selected, what every row says and which deletion is waiting for a confirmation all
 * live here, so a JVM test can assert what the learner would see without inflating a view.
 *
 * <p><strong>An empty store is its own phase, not an empty list.</strong> "There is nothing here yet"
 * and "here is your library" are different facts, and a renderer that had to infer one from
 * {@code rows.isEmpty()} would sooner or later draw a blank page instead of the sentence that tells
 * the learner what to do next (PRD 6.2.16 point 5). The two segments answer that question separately:
 * an empty 「已完成」 is not an empty 「进行中」, and neither borrows the other's wording.
 */
public final class LibraryViewState {

    /** Which of the library's panes is on screen. */
    public enum Phase {
        /** The store is being read. */
        LOADING,
        /** A list is on screen. */
        READY,
        /** The store holds no topic yet. */
        EMPTY,
        /** The store could not be read. */
        ERROR
    }

    /** Which segment of the library the learner is looking at. */
    public enum Segment {
        /** The topics still being worked on. */
        IN_PROGRESS,
        /** The archived branches, flattened out of every topic. */
        DONE
    }

    /** One drawable row: identity, size, the unread hint and the last use. */
    public static final class Row {

        public final String topicId;
        /** Where a tap resumes: the branch the learner last opened, or the root branch if none. */
        public final String resumeBranchId;
        public final String title;
        public final int branchCount;
        /** Sum of each branch's derived unread gap; zero is a real, already-read state. */
        public final long unreadCount;
        /** Epoch millis of the last use; the screen formats it in the device's own zone. */
        public final long lastUsedAt;

        public Row(String topicId, String resumeBranchId, String title, int branchCount,
                long unreadCount, long lastUsedAt) {
            this.topicId = Guard.id(topicId, "topicId");
            this.resumeBranchId = Guard.id(resumeBranchId, "resumeBranchId");
            this.title = Guard.text(title, "title");
            this.branchCount = Guard.atLeast(branchCount, 0, "branchCount");
            this.unreadCount = Guard.nonNegative(unreadCount, "unreadCount");
            this.lastUsedAt = Guard.epochMillis(lastUsedAt, "lastUsedAt");
        }
    }

    /**
     * One archived branch, flattened out of the topic it belongs to.
     *
     * <p><strong>The source path is read, not guessed.</strong> It is the chain of ancestors from the
     * topic's root down to the branch's own parent, taken from the same snapshot that said the branch
     * is archived - so "where did this come from" cannot disagree with the tree it was counted from.
     *
     * <p><strong>An absent summary is one line fewer, never a placeholder.</strong> {@code summary} is
     * null until the derived service has written one, and the row drops its line rather than standing
     * an explanation in its place (PRD item 7, which names the branch row as one of the surfaces where
     * "摘要将随本分支对话自动更新" is forbidden).
     */
    public static final class ArchiveRow {

        public final String branchId;
        public final String topicId;
        public final String title;
        /** The topic's root title down to the branch's parent, joined; never empty. */
        public final String sourcePath;
        /** The branch's own summary, or null while it has none. */
        public final String summary;

        public ArchiveRow(String branchId, String topicId, String title, String sourcePath,
                String summary) {
            this.branchId = Guard.id(branchId, "branchId");
            this.topicId = Guard.id(topicId, "topicId");
            this.title = Guard.text(title, "title");
            this.sourcePath = Guard.text(sourcePath, "sourcePath");
            this.summary = summary;
        }
    }

    public final Phase phase;
    public final Segment segment;
    public final List<Row> rows;
    public final List<ArchiveRow> archived;
    /** The deletion waiting for a confirmation, or null while none is. */
    public final NavigationData.Plan pendingDelete;

    private LibraryViewState(Phase phase, Segment segment, List<Row> rows, List<ArchiveRow> archived,
            NavigationData.Plan pendingDelete) {
        this.phase = phase;
        this.segment = segment;
        this.rows = Collections.unmodifiableList(new ArrayList<Row>(rows));
        this.archived = Collections.unmodifiableList(new ArrayList<ArchiveRow>(archived));
        this.pendingDelete = pendingDelete;
    }

    public static LibraryViewState initial() {
        return new LibraryViewState(Phase.LOADING, Segment.IN_PROGRESS, Collections.<Row>emptyList(),
                Collections.<ArchiveRow>emptyList(), null);
    }

    public LibraryViewState withPhase(Phase value) {
        return new LibraryViewState(value, segment, rows, archived, pendingDelete);
    }

    /** Adopts one read of the store: no topic is the empty phase, never a listed blank page. */
    public LibraryViewState withTopics(List<Row> topics) {
        List<Row> read = Guard.object(topics, "topics");
        return new LibraryViewState(read.isEmpty() ? Phase.EMPTY : Phase.READY, segment, read,
                archived, pendingDelete);
    }

    /** Adopts one read of the archived branches. An empty completed segment stays a listed phase. */
    public LibraryViewState withArchived(List<ArchiveRow> value) {
        return new LibraryViewState(phase, segment, rows, Guard.object(value, "archived"),
                pendingDelete);
    }

    /** Which segment the learner selected; the other one's list is kept, never re-read. */
    public LibraryViewState withSegment(Segment value) {
        return new LibraryViewState(phase, Guard.object(value, "segment"), rows, archived,
                pendingDelete);
    }

    /** The deletion the learner is being asked to confirm; null clears the row's confirmation. */
    public LibraryViewState withPendingDelete(NavigationData.Plan value) {
        return new LibraryViewState(phase, segment, rows, archived, value);
    }
}
