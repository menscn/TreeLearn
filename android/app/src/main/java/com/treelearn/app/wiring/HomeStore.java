package com.treelearn.app.wiring;

import com.treelearn.core.domain.Branch;
import com.treelearn.core.domain.GenerationOperation;
import com.treelearn.core.domain.Message;
import com.treelearn.core.domain.Topic;
import com.treelearn.data.db.dao.FirstTopicCreation;

import java.util.List;

/**
 * The home path's rows, behind one port, without a database handle.
 *
 * <p>It exists for one reason: the deciding half of this path - which title a card shows, which
 * notice a refusal becomes, whether a send may start at all - has to run on a plain JVM, and
 * {@code SQLiteDatabase} cannot be constructed there. The SQL half lives in
 * {@link SqliteHomeStore}, which is the only implementation that names a table, and this interface
 * carries domain values so a test can answer it from a list.
 *
 * <p>Reads throw when the store cannot be read. That is not a defect of this port: it is the state
 * {@link com.treelearn.app.ui.HomeData#recentTopics()} documents, and the presenter answers it with
 * its error phase.
 */
public interface HomeStore {

    /** Recently used topics, newest first. Never null; empty means "no topic yet". */
    List<Row> recentTopics();

    default void renameTopic(String topicId, String title, long now) {
        throw new UnsupportedOperationException("renameTopic is not implemented by this store");
    }

    default void deleteTopic(String topicId) {
        throw new UnsupportedOperationException("deleteTopic is not implemented by this store");
    }

    /**
     * The first topic's five row groups in one transaction, or a classified refusal that leaves no
     * row of that topic behind.
     */
    FirstTopicCreation.Outcome createFirstTopic(Topic topic, Branch rootBranch, Message userMessage,
            Message aiMessage, GenerationOperation operation);

    /**
     * One topic as the home card needs it.
     *
     * <p>The two derived columns are nullable on purpose: a topic exists and is readable before any
     * AI-derived artifact does, which is an ordinary state and not a missing field. The root
     * question is carried alongside because it is the title's fallback (PRD 6.2.2).
     */
    final class Row {

        public final String topicId;
        /** The branch a tap resumes: the last one opened, or the root when none was. */
        public final String resumeBranchId;
        /**
         * The root branch's stored title, or null while it has not been derived. The branch row is
         * the only place a tree's display name lives (PRD 6.2.2 point 5), so the home card and the
         * conversation heading read the same column and cannot disagree.
         */
        public final String rootTitle;
        /**
         * The root branch's first learner message, or null when the root branch stores none. It is
         * the heading's fallback, which is why it travels alongside the title.
         */
        public final String rootQuestion;
        /** The root summary's stored payload text, or null while it has not been derived. */
        public final String summaryPayload;
        public final int branchCount;
        public final long unreadCount;
        public final long lastUsedAt;

        public Row(String topicId, String resumeBranchId, String rootTitle, String rootQuestion,
                String summaryPayload, int branchCount, long lastUsedAt) {
            this(topicId, resumeBranchId, rootTitle, rootQuestion, summaryPayload, branchCount, 0L,
                    lastUsedAt);
        }

        public Row(String topicId, String resumeBranchId, String rootTitle, String rootQuestion,
                String summaryPayload, int branchCount, long unreadCount, long lastUsedAt) {
            this.topicId = topicId;
            this.resumeBranchId = resumeBranchId;
            this.rootTitle = rootTitle;
            this.rootQuestion = rootQuestion;
            this.summaryPayload = summaryPayload;
            this.branchCount = branchCount;
            this.unreadCount = unreadCount;
            this.lastUsedAt = lastUsedAt;
        }
    }
}
