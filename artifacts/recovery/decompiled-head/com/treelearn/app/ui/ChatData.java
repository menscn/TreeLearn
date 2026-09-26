/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.treelearn.core.domain.Branch
 *  com.treelearn.core.domain.Guard
 *  com.treelearn.core.domain.Message
 */
package com.treelearn.app.ui;

import com.treelearn.app.ui.ChatMessage;
import com.treelearn.app.ui.ChildBranch;
import com.treelearn.app.ui.Recommendation;
import com.treelearn.app.ui.SourceCard;
import com.treelearn.core.domain.Branch;
import com.treelearn.core.domain.Guard;
import com.treelearn.core.domain.Message;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public interface ChatData {
    default public void addGenerationListener(String branchId, Runnable listener) {
    }

    default public void removeGenerationListener(String branchId, Runnable listener) {
    }

    public Snapshot read(String var1);

    public List<ChildBranch> children(String var1, String var2);

    public static final class Snapshot {
        public final String branchTitle;
        public final List<ChatMessage> messages;
        public final List<Recommendation> answerRecommendations;
        public final String recommendationsRevisionId;
        public final Branch branch;
        public final List<Message> readingRows;
        public final SourceCard sourceCard;

        private Snapshot(String branchTitle, List<ChatMessage> messages, List<Recommendation> answerRecommendations, String recommendationsRevisionId, Branch branch, List<Message> readingRows, SourceCard sourceCard) {
            this.branchTitle = branchTitle == null ? "" : branchTitle;
            this.messages = Collections.unmodifiableList(new ArrayList<ChatMessage>(messages));
            this.answerRecommendations = Collections.unmodifiableList(new ArrayList<Recommendation>(answerRecommendations));
            this.recommendationsRevisionId = recommendationsRevisionId == null ? "" : recommendationsRevisionId;
            this.branch = branch;
            this.readingRows = Collections.unmodifiableList(new ArrayList<Message>(readingRows));
            this.sourceCard = sourceCard;
        }

        public static Snapshot of(String branchTitle, List<ChatMessage> messages, List<Recommendation> answerRecommendations, String recommendationsRevisionId, Branch branch, List<Message> readingRows, SourceCard sourceCard) {
            Guard.object(messages, (String)"messages");
            Guard.object(answerRecommendations, (String)"answerRecommendations");
            Guard.object(readingRows, (String)"readingRows");
            return new Snapshot(branchTitle, messages, answerRecommendations, recommendationsRevisionId, branch, readingRows, sourceCard);
        }
    }
}

