package com.treelearn.app.ui;

import com.treelearn.core.domain.Branch;
import com.treelearn.core.domain.Guard;
import com.treelearn.core.domain.Message;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Everything the conversation reads from the store, behind one port (technical design 13).
 *
 * <p>One read answers the whole screen: the rows in order, the branch title, and the recommendations
 * T-011 derived for one version. It is deliberately a single call - a screen that assembled itself
 * from several queries would show a half-updated conversation between them, and every one of those
 * reads would have to agree about which version is displayed.
 *
 * <p>{@link #read} may throw: a branch that cannot be read is a real state and the presenter answers
 * it with the error phase, keeping the composer and the draft. Recommendation failures are not part
 * of that: T-011's artifact simply comes back empty, which the presenter renders as "no strip".
 */
public interface ChatData {

    /** Registers an immediate refresh hook for stream state changes; default keeps test fakes simple. */
    default void addGenerationListener(String branchId, Runnable listener) {
    }

    default void removeGenerationListener(String branchId, Runnable listener) {
    }

    /** @throws RuntimeException when the store could not be read; the presenter shows the error phase */
    Snapshot read(String branchId);

    /**
     * The branches forked directly from one answer of one branch, unarchived and in creation order
     * (PRD 6.2.20 point 7).
     *
     * <p>It is a read of its own rather than part of {@link #read} because only the child list needs it:
     * the row is drawn with the child count the snapshot already carries, and the popup is opened on a
     * tap. Like every other read here it runs on the caller's background executor.
     */
    List<ChildBranch> children(String branchId, String messageId);

    /** One conversation, as read. Every list is copied and unmodifiable. */
    final class Snapshot {

        /** Empty until the branch title has been derived; a branch is readable before that. */
        public final String branchTitle;
        /** In conversation order, learner and answer rows interleaved. Never null. */
        public final List<ChatMessage> messages;
        /** T-011's {@code ANSWER_RECOMMENDATIONS} for one version; empty when there is none. */
        public final List<Recommendation> answerRecommendations;
        /** The version the strip describes; empty when there is no strip. */
        public final String recommendationsRevisionId;
        /**
         * The branch row the read came from, or null for a read that never held one.
         *
         * <p>The reading session decides with the branch's own unread sequence pair, and that row is
         * already in hand where a conversation is read; asking for it again would be a second answer
         * to "which branch is this". Where the learner was, what the viewport is showing and how far
         * the unread sequence has come all belong to it.
         */
        public final Branch branch;
        /**
         * The stored positions in reading order, one per {@link #messages} entry; empty for a read
         * that never held a branch row.
         *
         * <p>A second list, not a second read: the screen draws {@link ChatMessage}s and the reading
         * rules resolve against {@link Message}s, and both come from the one query
         * {@code ChatStore.read} already ran. Neither can therefore describe a different
         * conversation than the other.
         */
        public final List<Message> readingRows;

        private Snapshot(String branchTitle, List<ChatMessage> messages,
                List<Recommendation> answerRecommendations, String recommendationsRevisionId,
                Branch branch, List<Message> readingRows) {
            this.branchTitle = branchTitle == null ? "" : branchTitle;
            this.messages = Collections.unmodifiableList(new ArrayList<ChatMessage>(messages));
            this.answerRecommendations = Collections.unmodifiableList(
                    new ArrayList<Recommendation>(answerRecommendations));
            this.recommendationsRevisionId = recommendationsRevisionId == null
                    ? "" : recommendationsRevisionId;
            this.branch = branch;
            this.readingRows = Collections.unmodifiableList(new ArrayList<Message>(readingRows));
        }

        public static Snapshot of(String branchTitle, List<ChatMessage> messages,
                List<Recommendation> answerRecommendations, String recommendationsRevisionId,
                Branch branch, List<Message> readingRows) {
            Guard.object(messages, "messages");
            Guard.object(answerRecommendations, "answerRecommendations");
            Guard.object(readingRows, "readingRows");
            return new Snapshot(branchTitle, messages, answerRecommendations,
                    recommendationsRevisionId, branch, readingRows);
        }

    }
}
