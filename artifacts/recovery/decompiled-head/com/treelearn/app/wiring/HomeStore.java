/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.treelearn.core.domain.Branch
 *  com.treelearn.core.domain.GenerationOperation
 *  com.treelearn.core.domain.Message
 *  com.treelearn.core.domain.Topic
 *  com.treelearn.data.db.dao.FirstTopicCreation$Outcome
 */
package com.treelearn.app.wiring;

import com.treelearn.core.domain.Branch;
import com.treelearn.core.domain.GenerationOperation;
import com.treelearn.core.domain.Message;
import com.treelearn.core.domain.Topic;
import com.treelearn.data.db.dao.FirstTopicCreation;
import java.util.List;

public interface HomeStore {
    public List<Row> recentTopics();

    default public void renameTopic(String topicId, String title, long now) {
        throw new UnsupportedOperationException("renameTopic is not implemented by this store");
    }

    default public void deleteTopic(String topicId) {
        throw new UnsupportedOperationException("deleteTopic is not implemented by this store");
    }

    public FirstTopicCreation.Outcome createFirstTopic(Topic var1, Branch var2, Message var3, Message var4, GenerationOperation var5);

    public static final class Row {
        public final String topicId;
        public final String resumeBranchId;
        public final String rootTitle;
        public final String rootQuestion;
        public final String summaryPayload;
        public final int branchCount;
        public final long unreadCount;
        public final long lastUsedAt;

        public Row(String topicId, String resumeBranchId, String rootTitle, String rootQuestion, String summaryPayload, int branchCount, long lastUsedAt) {
            this(topicId, resumeBranchId, rootTitle, rootQuestion, summaryPayload, branchCount, 0L, lastUsedAt);
        }

        public Row(String topicId, String resumeBranchId, String rootTitle, String rootQuestion, String summaryPayload, int branchCount, long unreadCount, long lastUsedAt) {
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

