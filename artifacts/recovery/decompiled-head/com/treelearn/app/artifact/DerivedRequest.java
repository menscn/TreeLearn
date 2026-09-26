/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.treelearn.ai.prompt.DeepSeekPromptRegistry
 *  com.treelearn.ai.prompt.DeepSeekRequest
 *  com.treelearn.ai.prompt.DeepSeekRequestAssembler
 *  com.treelearn.ai.prompt.PromptBudget
 *  com.treelearn.core.domain.ContextPlan
 *  com.treelearn.core.domain.ContextPlan$Entry
 *  com.treelearn.core.domain.ContextPlan$EntryKind
 *  com.treelearn.core.domain.ContextSnapshotCodec
 *  com.treelearn.core.domain.GenerationModel
 *  com.treelearn.core.domain.Guard
 *  com.treelearn.core.domain.PromptPurpose
 */
package com.treelearn.app.artifact;

import com.treelearn.ai.prompt.DeepSeekPromptRegistry;
import com.treelearn.ai.prompt.DeepSeekRequest;
import com.treelearn.ai.prompt.DeepSeekRequestAssembler;
import com.treelearn.ai.prompt.PromptBudget;
import com.treelearn.app.artifact.ArtifactInputs;
import com.treelearn.app.artifact.ArtifactKind;
import com.treelearn.app.artifact.TreeOutline;
import com.treelearn.core.domain.ContextPlan;
import com.treelearn.core.domain.ContextSnapshotCodec;
import com.treelearn.core.domain.GenerationModel;
import com.treelearn.core.domain.Guard;
import com.treelearn.core.domain.PromptPurpose;
import java.util.Collections;
import java.util.List;

public final class DerivedRequest {
    private static final int NO_ITEM_CEILING = Integer.MAX_VALUE;
    private final ArtifactKind kind;
    private final String scopeId;
    private final String basedOnRevisionId;
    private final String inputJson;
    private final String inputDigest;
    private final int itemCeiling;
    private final int compressionSourceChars;
    private final boolean coversReasoning;
    private final GenerationModel model;
    private final PromptBudget budget;

    private DerivedRequest(ArtifactKind kind, String scopeId, String basedOnRevisionId, String inputJson, int itemCeiling, int compressionSourceChars, boolean coversReasoning, GenerationModel model, PromptBudget budget) {
        this.kind = (ArtifactKind)((Object)Guard.object((Object)((Object)kind), (String)"kind"));
        this.scopeId = Guard.id((String)scopeId, (String)"scopeId");
        if (kind.revisionBound()) {
            this.basedOnRevisionId = Guard.id((String)basedOnRevisionId, (String)"basedOnRevisionId");
        } else {
            if (basedOnRevisionId != null) {
                throw new IllegalArgumentException("basedOnRevisionId must be absent for " + (Object)((Object)kind) + "; only a revision-bound kind names the answer it describes");
            }
            this.basedOnRevisionId = null;
        }
        this.inputJson = Guard.nonBlank((String)inputJson, (String)"inputJson");
        this.inputDigest = ContextSnapshotCodec.digestOf((String)inputJson);
        this.itemCeiling = Guard.atLeast((int)itemCeiling, (int)1, (String)"itemCeiling");
        this.compressionSourceChars = compressionSourceChars;
        this.coversReasoning = coversReasoning;
        this.model = (GenerationModel)Guard.object((Object)model, (String)"model");
        this.budget = (PromptBudget)Guard.object((Object)budget, (String)"budget");
    }

    public static DerivedRequest rootTitle(String scopeId, String rootQuestion, GenerationModel model, PromptBudget budget) {
        return new DerivedRequest(ArtifactKind.ROOT_TITLE, scopeId, null, ArtifactInputs.rootTitle(rootQuestion), Integer.MAX_VALUE, 0, true, model, budget);
    }

    public static DerivedRequest branchTitle(String scopeId, String sourceQuote, String firstQuestion, GenerationModel model, PromptBudget budget) {
        return new DerivedRequest(ArtifactKind.BRANCH_TITLE, scopeId, null, ArtifactInputs.branchTitle(sourceQuote, firstQuestion), Integer.MAX_VALUE, 0, true, model, budget);
    }

    public static DerivedRequest branchSummary(String scopeId, String basedOnRevisionId, List<ContextPlan.Entry> birthContext, List<ContextPlan.Entry> branchMessages, GenerationModel model, PromptBudget budget) {
        return new DerivedRequest(ArtifactKind.BRANCH_SUMMARY, scopeId, basedOnRevisionId, ArtifactInputs.branchSummary(birthContext, branchMessages), Integer.MAX_VALUE, 0, true, model, budget);
    }

    public static DerivedRequest answerRecommendations(String scopeId, String basedOnRevisionId, String latestAnswer, List<TreeOutline.Entry> outline, int maxItems, GenerationModel model, PromptBudget budget) {
        return new DerivedRequest(ArtifactKind.ANSWER_RECOMMENDATIONS, scopeId, basedOnRevisionId, ArtifactInputs.answerRecommendations(latestAnswer, outline, maxItems), maxItems, 0, true, model, budget);
    }

    public static DerivedRequest homeRecommendations(String scopeId, List<TreeOutline.Entry> outline, int maxItems, GenerationModel model, PromptBudget budget) {
        return new DerivedRequest(ArtifactKind.HOME_RECOMMENDATIONS, scopeId, null, ArtifactInputs.homeRecommendations(outline, maxItems), maxItems, 0, true, model, budget);
    }

    public static DerivedRequest contextCompression(String scopeId, String basedOnRevisionId, List<ArtifactInputs.CompressionTurn> turns, GenerationModel model, PromptBudget budget) {
        List covered = (List)Guard.object(turns, (String)"turns");
        int sourceChars = 0;
        boolean coversReasoning = !covered.isEmpty();
        for (ArtifactInputs.CompressionTurn turn : covered) {
            sourceChars += turn.chars();
            coversReasoning = coversReasoning && turn.coversReasoning();
        }
        return new DerivedRequest(ArtifactKind.CONTEXT_COMPRESSION, scopeId, basedOnRevisionId, ArtifactInputs.contextCompression(covered), Integer.MAX_VALUE, sourceChars, coversReasoning, model, budget);
    }

    public static DerivedRequest contextCompressionPlan(String scopeId, String basedOnRevisionId, List<ContextPlan.Entry> entries, GenerationModel model, PromptBudget budget) {
        ContextPlan plan = new ContextPlan((List)Guard.object(entries, (String)"entries"));
        boolean coversReasoning = true;
        boolean hasRevision = false;
        for (ContextPlan.Entry entry : plan.entries()) {
            if (entry.kind != ContextPlan.EntryKind.STABLE_REVISION) continue;
            hasRevision = true;
            coversReasoning = coversReasoning && !entry.reasoningText.trim().isEmpty();
        }
        return new DerivedRequest(ArtifactKind.CONTEXT_COMPRESSION, scopeId, basedOnRevisionId, ArtifactInputs.contextCompressionEntries(plan.entries()), Integer.MAX_VALUE, plan.chars(), hasRevision && coversReasoning, model, budget);
    }

    public ArtifactKind kind() {
        return this.kind;
    }

    public String scopeId() {
        return this.scopeId;
    }

    public String basedOnRevisionId() {
        return this.basedOnRevisionId;
    }

    public String inputJson() {
        return this.inputJson;
    }

    public String inputDigest() {
        return this.inputDigest;
    }

    public int itemCeiling() {
        return this.itemCeiling;
    }

    public int compressionSourceChars() {
        return this.compressionSourceChars;
    }

    public boolean inputCoversReasoning() {
        return this.coversReasoning;
    }

    public GenerationModel model() {
        return this.model;
    }

    public String baselineRef() {
        return this.kind.revisionBound() ? this.basedOnRevisionId : this.inputDigest;
    }

    public String policyVersion() {
        return DeepSeekPromptRegistry.spec((PromptPurpose)this.kind.purpose).policyVersion;
    }

    public DeepSeekRequest assemble() {
        return DeepSeekRequestAssembler.assemble((PromptPurpose)this.kind.purpose, (boolean)false, Collections.emptyList(), (String)this.inputJson, (PromptBudget)this.budget);
    }
}

