/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  android.database.sqlite.SQLiteDatabase
 *  android.util.Log
 *  com.treelearn.ai.prompt.PromptBudget
 *  com.treelearn.core.domain.AnswerRevision
 *  com.treelearn.core.domain.Branch
 *  com.treelearn.core.domain.ContextPlan
 *  com.treelearn.core.domain.ContextPlan$Entry
 *  com.treelearn.core.domain.GenerationModel
 *  com.treelearn.core.domain.Guard
 *  com.treelearn.data.db.DatabaseTransactionRunner
 *  com.treelearn.data.db.TreeLearnDatabaseHelper
 *  com.treelearn.data.db.dao.AiArtifactDao
 *  com.treelearn.data.db.dao.AiArtifactDao$Row
 */
package com.treelearn.app.artifact;

import android.database.sqlite.SQLiteDatabase;
import android.util.Log;
import com.treelearn.ai.prompt.PromptBudget;
import com.treelearn.app.artifact.ArtifactOutcome;
import com.treelearn.app.artifact.ArtifactPayloadCodec;
import com.treelearn.app.artifact.BlockingDerivedCaller;
import com.treelearn.app.artifact.DerivedOperation;
import com.treelearn.app.artifact.DerivedRequest;
import com.treelearn.app.artifact.DerivedResult;
import com.treelearn.app.artifact.DerivedTelemetry;
import com.treelearn.app.artifact.HomeDerivedPort;
import com.treelearn.app.artifact.SqliteDerivedStore;
import com.treelearn.app.artifact.TreeOutline;
import com.treelearn.app.generation.GenerationGateway;
import com.treelearn.app.wiring.ChatStore;
import com.treelearn.core.domain.AnswerRevision;
import com.treelearn.core.domain.Branch;
import com.treelearn.core.domain.ContextPlan;
import com.treelearn.core.domain.GenerationModel;
import com.treelearn.core.domain.Guard;
import com.treelearn.data.db.DatabaseTransactionRunner;
import com.treelearn.data.db.TreeLearnDatabaseHelper;
import com.treelearn.data.db.dao.AiArtifactDao;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class DerivedService
implements HomeDerivedPort {
    private final TreeLearnDatabaseHelper helper;
    private final DatabaseTransactionRunner transactions;
    private final GenerationGateway callerGateway;
    private final PromptBudget budget;
    private final Clock clock;

    public DerivedService(TreeLearnDatabaseHelper helper, DatabaseTransactionRunner transactions, GenerationGateway callerGateway, PromptBudget budget, Clock clock) {
        this.helper = (TreeLearnDatabaseHelper)Guard.object((Object)helper, (String)"helper");
        this.transactions = (DatabaseTransactionRunner)Guard.object((Object)transactions, (String)"transactions");
        this.callerGateway = (GenerationGateway)Guard.object((Object)callerGateway, (String)"callerGateway");
        this.budget = (PromptBudget)Guard.object((Object)budget, (String)"budget");
        this.clock = (Clock)Guard.object((Object)clock, (String)"clock");
    }

    @Override
    public ArtifactOutcome rootTitle(Branch root, String question, GenerationModel model) {
        Guard.object((Object)root, (String)"root");
        Guard.nonBlank((String)question, (String)"question");
        Guard.object((Object)model, (String)"model");
        DerivedRequest request = DerivedRequest.rootTitle(root.id, question, model, this.budget);
        return this.run(request, root.topicId);
    }

    public void ensureConversationArtifacts(ChatStore.Conversation conversation, GenerationModel model, boolean answerSuggestions) {
        if (conversation == null || conversation.branch == null || model == null) {
            return;
        }
        String revisionId = null;
        String latestAnswer = null;
        ArrayList<ContextPlan.Entry> entries = new ArrayList<ContextPlan.Entry>();
        for (ChatStore.Position position : conversation.positions) {
            if (position.message.isUser()) {
                entries.add(ContextPlan.Entry.userMessage((String)position.message.id, (String)position.message.userContent));
                continue;
            }
            if (position.activeRevision == null) continue;
            AnswerRevision revision = position.activeRevision;
            entries.add(ContextPlan.Entry.stableRevision((String)revision.id, (String)revision.reasoningContent, (String)revision.answerContent));
            revisionId = revision.id;
            latestAnswer = revision.answerContent;
        }
        if (revisionId == null || latestAnswer == null) {
            return;
        }
        DerivedRequest title = DerivedService.branchTitleRequest(conversation, model, this.budget);
        if (title != null) {
            this.runIfMissing(title, conversation.branch.topicId);
        }
        DerivedRequest summary = DerivedRequest.branchSummary(conversation.branch.id, revisionId, Collections.emptyList(), entries, model, this.budget);
        this.runIfMissing(summary, conversation.branch.topicId);
        if (!answerSuggestions) {
            return;
        }
        DerivedRequest recommendations = DerivedRequest.answerRecommendations(conversation.branch.id, revisionId, latestAnswer, Collections.emptyList(), 3, model, this.budget);
        this.runIfMissing(recommendations, conversation.branch.topicId);
    }

    public ContextPlan compressHistory(String branchId, String latestRevisionId, List<ContextPlan.Entry> olderEntries, List<ContextPlan.Entry> recentEntries, GenerationModel model) {
        if (olderEntries == null || olderEntries.isEmpty() || latestRevisionId == null) {
            return null;
        }
        DerivedRequest request = DerivedRequest.contextCompressionPlan(branchId, latestRevisionId, olderEntries, model, this.budget);
        this.runIfMissing(request, branchId);
        SQLiteDatabase database = this.helper.getWritableDatabase();
        AiArtifactDao.Row row = AiArtifactDao.findByKindAndScope((SQLiteDatabase)database, (String)request.kind().wireId, (String)branchId);
        if (row == null || !latestRevisionId.equals(row.basedOnRevisionId) || !request.inputDigest().equals(row.inputDigest)) {
            return null;
        }
        DerivedResult parsed = ArtifactPayloadCodec.parse(request.kind(), row.payload, Integer.MAX_VALUE);
        if (!parsed.isUsable() || parsed.payload().summary == null || parsed.payload().summary.trim().isEmpty()) {
            return null;
        }
        ArrayList<ContextPlan.Entry> replacement = new ArrayList<ContextPlan.Entry>();
        replacement.add(ContextPlan.Entry.summary((String)row.id, (String)parsed.payload().summary));
        replacement.addAll(recentEntries);
        return new ContextPlan(replacement);
    }

    @Override
    public List<String> homeRecommendations(String topicId, List<TreeOutline.Entry> outline, GenerationModel model) {
        DerivedRequest request = DerivedRequest.homeRecommendations(topicId, outline, 4, model, this.budget);
        this.runIfMissing(request, topicId);
        SQLiteDatabase database = this.helper.getWritableDatabase();
        AiArtifactDao.Row row = AiArtifactDao.findByKindAndScope((SQLiteDatabase)database, (String)request.kind().wireId, (String)topicId);
        if (row == null || !request.inputDigest().equals(row.inputDigest)) {
            return Collections.emptyList();
        }
        DerivedResult parsed = ArtifactPayloadCodec.parse(request.kind(), row.payload, 4);
        return parsed.isUsable() ? parsed.payload().items : Collections.emptyList();
    }

    static DerivedRequest branchTitleRequest(ChatStore.Conversation conversation, GenerationModel model, PromptBudget budget) {
        if (conversation == null || conversation.branch == null || conversation.branch.isRoot() || conversation.branch.source == null || conversation.branch.source.anchor == null) {
            return null;
        }
        String quote = conversation.branch.source.anchor.quote;
        if (quote == null || quote.trim().isEmpty()) {
            return null;
        }
        String firstQuestion = null;
        for (ChatStore.Position position : conversation.positions) {
            if (!position.message.isUser()) continue;
            firstQuestion = position.message.userContent;
            break;
        }
        if (firstQuestion == null || firstQuestion.trim().isEmpty()) {
            return null;
        }
        return DerivedRequest.branchTitle(conversation.branch.id, quote, firstQuestion, model, budget);
    }

    private ArtifactOutcome run(DerivedRequest request, String topicId) {
        SQLiteDatabase database = this.helper.getWritableDatabase();
        SqliteDerivedStore store = new SqliteDerivedStore(database, this.transactions, ignored -> request.baselineRef());
        DerivedTelemetry telemetry = (event, kind, attemptId, digestPrefix) -> Log.d((String)"TreeLearnDerived", (String)((Object)((Object)event) + " " + kind.wireId + " " + digestPrefix));
        return new DerivedOperation(store, telemetry, new BlockingDerivedCaller(this.callerGateway), request, this.clock.millis()).run();
    }

    private ArtifactOutcome runIfMissing(DerivedRequest request, String topicId) {
        SQLiteDatabase database = this.helper.getWritableDatabase();
        AiArtifactDao.Row existing = AiArtifactDao.findByKindAndScope((SQLiteDatabase)database, (String)request.kind().wireId, (String)request.scopeId());
        if (existing != null && Objects.equals(request.basedOnRevisionId(), existing.basedOnRevisionId) && request.inputDigest().equals(existing.inputDigest)) {
            return ArtifactOutcome.APPLIED;
        }
        return this.run(request, topicId);
    }
}

