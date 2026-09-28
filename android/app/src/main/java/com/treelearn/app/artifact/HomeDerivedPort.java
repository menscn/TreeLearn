package com.treelearn.app.artifact;

import com.treelearn.app.artifact.ArtifactOutcome;
import com.treelearn.app.artifact.TreeOutline;
import com.treelearn.app.wiring.ChatStore;
import com.treelearn.core.domain.Branch;
import com.treelearn.core.domain.ContextPlan;
import com.treelearn.core.domain.GenerationModel;
import java.util.List;

public interface HomeDerivedPort {
    public ArtifactOutcome rootTitle(Branch var1, String var2, GenerationModel var3);

    public List<String> homeRecommendations(String var1, List<TreeOutline.Entry> var2, GenerationModel var3);

    /**
     * Ensures the current stable branch revision has a summary and its follow-up questions.
     *
     * <p>Declared here so the conversation's read path can depend on the port rather than on the
     * concrete service: the wiring's decision is what a test needs to pin, and a decision reachable
     * only through a real SQLite helper is a decision no JVM test can make.
     */
    default void ensureConversationArtifacts(ChatStore.Conversation conversation,
            GenerationModel model, boolean answerSuggestions) {
    }

    /**
     * Compresses older history and returns the exact replacement plan, or null on any refusal.
     *
     * <p>On the port for the same reason as {@link #ensureConversationArtifacts}: the request path in
     * {@code ChatWiring} is what decides whether the compressed plan is adopted, and that decision is
     * tested without a database.
     */
    default ContextPlan compressHistory(String branchId, String latestRevisionId,
            List<ContextPlan.Entry> olderEntries, List<ContextPlan.Entry> recentEntries,
            GenerationModel model) {
        return null;
    }

    /**
     * Reads back the stored follow-up strip for one branch's displayed answer, empty when none.
     *
     * <p>On the port rather than called on {@code DerivedService} directly, for the reason the other
     * two entries are here: the conversation's read path decides what the screen shows, and a
     * decision tested only against a real SQLite helper is a decision that cannot be tested without
     * a device. The store half is proved in the instrumented suite; a caller that only renders the
     * result needs no more than this method's answer.
     *
     * @param branchId the branch whose newest stable revision owns a strip
     * @param basedOnRevisionId the revision the caller is displaying
     */
    default List<String> answerRecommendations(String branchId, String basedOnRevisionId) {
        return java.util.Collections.emptyList();
    }
}

