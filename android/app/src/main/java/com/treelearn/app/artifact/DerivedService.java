package com.treelearn.app.artifact;

import android.database.sqlite.SQLiteDatabase;

import com.treelearn.ai.prompt.PromptBudget;
import com.treelearn.app.generation.GenerationGateway;
import com.treelearn.core.domain.Branch;
import com.treelearn.core.domain.GenerationModel;
import com.treelearn.core.domain.Guard;
import com.treelearn.core.domain.ContextPlan;
import com.treelearn.core.domain.AnswerRevision;
import com.treelearn.app.wiring.ChatStore;
import com.treelearn.data.db.DatabaseTransactionRunner;
import com.treelearn.data.db.TreeLearnDatabaseHelper;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Production owner for derived artifact admission and write-back. */
public final class DerivedService
implements HomeDerivedPort {

    /**
     * The ceiling the codec accepts for follow-up questions. One constant for both directions: three
     * questions written and three questions read is the same number, and two literals would be two
     * numbers that happen to agree today.
     */
    static final int ANSWER_RECOMMENDATION_LIMIT = 3;

    private final TreeLearnDatabaseHelper helper;
    private final DatabaseTransactionRunner transactions;
    private final GenerationGateway callerGateway;
    private final PromptBudget budget;
    private final Clock clock;

    public DerivedService(TreeLearnDatabaseHelper helper, DatabaseTransactionRunner transactions,
            GenerationGateway callerGateway, PromptBudget budget, Clock clock) {
        this.helper = Guard.object(helper, "helper");
        this.transactions = Guard.object(transactions, "transactions");
        this.callerGateway = Guard.object(callerGateway, "callerGateway");
        this.budget = Guard.object(budget, "budget");
        this.clock = Guard.object(clock, "clock");
    }

    /** Derives the root title once the first topic transaction has created its branch. */
    @Override
    public ArtifactOutcome rootTitle(Branch root, String question, GenerationModel model) {
        Guard.object(root, "root");
        Guard.nonBlank(question, "question");
        Guard.object(model, "model");
        DerivedRequest request = DerivedRequest.rootTitle(root.id, question, model, budget);
        return run(request, root.topicId);
    }

    /** Ensures the current stable branch revision has a summary and follow-up questions. */
    @Override
    public void ensureConversationArtifacts(ChatStore.Conversation conversation,
            GenerationModel model, boolean answerSuggestions) {
        if (conversation == null || conversation.branch == null || model == null) {
            return;
        }
        String revisionId = null;
        String latestAnswer = null;
        List<ContextPlan.Entry> entries = new ArrayList<ContextPlan.Entry>();
        for (ChatStore.Position position : conversation.positions) {
            if (position.message.isUser()) {
                entries.add(ContextPlan.Entry.userMessage(position.message.id,
                        position.message.userContent));
            } else if (position.activeRevision != null) {
                AnswerRevision revision = position.activeRevision;
                entries.add(ContextPlan.Entry.stableRevision(revision.id,
                        revision.reasoningContent, revision.answerContent));
                revisionId = revision.id;
                latestAnswer = revision.answerContent;
            }
        }
        if (revisionId == null || latestAnswer == null) {
            return;
        }
        DerivedRequest title = branchTitleRequest(conversation, model, budget);
        if (title != null) {
            runIfMissing(title, conversation.branch.topicId);
        }
        DerivedRequest summary = DerivedRequest.branchSummary(conversation.branch.id, revisionId,
                Collections.<ContextPlan.Entry>emptyList(), entries, model, budget);
        runIfMissing(summary, conversation.branch.topicId);
        if (!answerSuggestions) {
            return;
        }
        DerivedRequest recommendations = DerivedRequest.answerRecommendations(conversation.branch.id,
                revisionId, latestAnswer, Collections.emptyList(), ANSWER_RECOMMENDATION_LIMIT, model,
                budget);
        runIfMissing(recommendations, conversation.branch.topicId);
    }

    /** Compresses older history and returns the exact replacement plan, or null on any refusal. */
    @Override
    public ContextPlan compressHistory(String branchId, String latestRevisionId,
            List<ContextPlan.Entry> olderEntries, List<ContextPlan.Entry> recentEntries,
            GenerationModel model) {
        if (olderEntries == null || olderEntries.isEmpty() || latestRevisionId == null) {
            return null;
        }
        DerivedRequest request = DerivedRequest.contextCompressionPlan(branchId, latestRevisionId,
                olderEntries, model, budget);
        runIfMissing(request, branchId);
        android.database.sqlite.SQLiteDatabase database = helper.getWritableDatabase();
        com.treelearn.data.db.dao.AiArtifactDao.Row row =
                com.treelearn.data.db.dao.AiArtifactDao.findByKindAndScope(database,
                        request.kind().wireId, branchId);
        if (row == null || !latestRevisionId.equals(row.basedOnRevisionId)
                || !request.inputDigest().equals(row.inputDigest)) {
            return null;
        }
        DerivedResult parsed = ArtifactPayloadCodec.parse(request.kind(), row.payload,
                Integer.MAX_VALUE);
        if (!parsed.isUsable() || parsed.payload().summary == null
                || parsed.payload().summary.trim().isEmpty()) {
            return null;
        }
        List<ContextPlan.Entry> replacement = new ArrayList<ContextPlan.Entry>();
        replacement.add(ContextPlan.Entry.summary(row.id, parsed.payload().summary));
        replacement.addAll(recentEntries);
        return new ContextPlan(replacement);
    }

    /**
     * Reads back the stored follow-up questions for one branch's displayed answer, or none.
     *
     * <p><strong>A read, not a derivation.</strong> {@link #ensureConversationArtifacts} is what
     * produces the row, on the background thread the screen already kicks off; asking the model again
     * here would spend a call the read does not own and, worse, would answer a slightly different
     * question than the one {@code based_on_revision_id} binds the stored strip to. The only caller
     * with a baseline in hand - {@code ChatWiring.read} - passes the revision the newest row is
     * showing, so the comparison stays on the read path where it belongs.
     *
     * <p>The digest is deliberately not recomputed. Deriving it needs the outline the write path
     * assembled, and assembling it a second time here is exactly the drift the store's
     * compare-and-swap exists to prevent; the CAS already dropped any write whose digest no longer
     * matched. Binding on the revision alone is the weaker check, but it is the one this side can
     * make honestly, and the presenter treats a mismatch as "no strip" rather than stale text.
     *
     * @param branchId the branch whose newest stable revision owns a strip
     * @param basedOnRevisionId the revision the caller is displaying; a row naming any other version
     *        is not this conversation's strip, so it reads as none
     * @return the stored questions, empty when none was derived, the baseline moved on, or the
     *         payload no longer parses
     */
    @Override
    public List<String> answerRecommendations(String branchId, String basedOnRevisionId) {
        Guard.id(branchId, "branchId");
        if (basedOnRevisionId == null || basedOnRevisionId.isEmpty()) {
            return Collections.emptyList();
        }
        SQLiteDatabase database = helper.getWritableDatabase();
        com.treelearn.data.db.dao.AiArtifactDao.Row row =
                com.treelearn.data.db.dao.AiArtifactDao.findByKindAndScope(database,
                        ArtifactKind.ANSWER_RECOMMENDATIONS.wireId, branchId);
        if (row == null || !basedOnRevisionId.equals(row.basedOnRevisionId)) {
            return Collections.emptyList();
        }
        DerivedResult parsed = ArtifactPayloadCodec.parse(ArtifactKind.ANSWER_RECOMMENDATIONS,
                row.payload, ANSWER_RECOMMENDATION_LIMIT);
        List<String> items = parsed.isUsable() ? parsed.payload().items : Collections.<String>emptyList();
        return items.isEmpty() ? Collections.<String>emptyList() : items;
    }

    /** Derives topic-level recommendations from the outline only. */
    @Override
    public List<String> homeRecommendations(String topicId, List<TreeOutline.Entry> outline,
            GenerationModel model) {
        DerivedRequest request = DerivedRequest.homeRecommendations(topicId, outline, 4, model, budget);
        runIfMissing(request, topicId);
        SQLiteDatabase database = helper.getWritableDatabase();
        com.treelearn.data.db.dao.AiArtifactDao.Row row =
                com.treelearn.data.db.dao.AiArtifactDao.findByKindAndScope(database,
                        request.kind().wireId, topicId);
        if (row == null || !request.inputDigest().equals(row.inputDigest)) {
            return Collections.emptyList();
        }
        DerivedResult parsed = ArtifactPayloadCodec.parse(request.kind(), row.payload, 4);
        return parsed.isUsable() ? parsed.payload().items : Collections.emptyList();
    }

    /**
     * Builds the branch-title request from the anchor quote plus the first user question, or null
     * when the branch carries no quotable origin - a title is only derived, never invented.
     */
    static DerivedRequest branchTitleRequest(ChatStore.Conversation conversation,
            GenerationModel model, PromptBudget budget) {
        if (conversation == null || conversation.branch == null || conversation.branch.isRoot()
                || conversation.branch.source == null
                || conversation.branch.source.anchor == null) {
            return null;
        }
        String quote = conversation.branch.source.anchor.quote;
        if (quote == null || quote.trim().isEmpty()) {
            return null;
        }
        String firstQuestion = null;
        for (ChatStore.Position position : conversation.positions) {
            if (!position.message.isUser()) {
                continue;
            }
            firstQuestion = position.message.userContent;
            break;
        }
        if (firstQuestion == null || firstQuestion.trim().isEmpty()) {
            return null;
        }
        return DerivedRequest.branchTitle(conversation.branch.id, quote, firstQuestion, model,
                budget);
    }

    private ArtifactOutcome run(DerivedRequest request, String topicId) {
        SQLiteDatabase database = helper.getWritableDatabase();
        SqliteDerivedStore store = new SqliteDerivedStore(database, transactions,
                ignored -> request.baselineRef());
        DerivedTelemetry telemetry = (event, kind, attemptId, digestPrefix) ->
                android.util.Log.d("TreeLearnDerived",
                        event + " " + kind.wireId + " " + digestPrefix);
        return new DerivedOperation(store, telemetry,
                new BlockingDerivedCaller(callerGateway), request, clock.millis()).run();
    }

    private ArtifactOutcome runIfMissing(DerivedRequest request, String topicId) {
        SQLiteDatabase database = helper.getWritableDatabase();
        com.treelearn.data.db.dao.AiArtifactDao.Row existing =
                com.treelearn.data.db.dao.AiArtifactDao.findByKindAndScope(database,
                        request.kind().wireId, request.scopeId());
        if (existing != null
                && java.util.Objects.equals(request.basedOnRevisionId(), existing.basedOnRevisionId)
                && request.inputDigest().equals(existing.inputDigest)) {
            return ArtifactOutcome.APPLIED;
        }
        return run(request, topicId);
    }
}
