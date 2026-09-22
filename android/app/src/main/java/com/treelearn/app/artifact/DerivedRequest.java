package com.treelearn.app.artifact;

import com.treelearn.ai.prompt.DeepSeekMessage;
import com.treelearn.ai.prompt.DeepSeekPromptRegistry;
import com.treelearn.ai.prompt.DeepSeekRequest;
import com.treelearn.ai.prompt.DeepSeekRequestAssembler;
import com.treelearn.ai.prompt.PromptBudget;
import com.treelearn.core.domain.ContextPlan;
import com.treelearn.core.domain.ContextSnapshotCodec;
import com.treelearn.core.domain.GenerationModel;
import com.treelearn.core.domain.Guard;
import java.util.Collections;
import java.util.List;

/**
 * One derived use case, ready to call: which artifact kind it derives, which baseline it was derived
 * from, the structured envelope it will send, and the usage/version pinned on it (technical design
 * 10; prompt design 3.2, 5, 6).
 *
 * <p>The version is not a caller's choice: it is read from the purpose's own registered spec, so a
 * title request cannot carry the summary version, and no caller can pin the teaching version on a
 * derived call. The system body is the purpose's own registered body as well — the teaching prompt is
 * never reachable from here.
 *
 * <p><strong>The baseline.</strong> {@link #baselineRef()} is what the write-back re-checks against
 * the live scope: the stable revision for a revision-bound kind, and the input's canonical digest for
 * a kind whose input <em>is</em> its baseline (a title's input is immutable, and a home
 * recommendation's is the outline digest). {@link #inputDigest()} is recorded on every stored artifact.
 *
 * <p>The envelope travels as the last user message of one assembled request, and the assembler
 * refuses a derived input that is not one JSON object, so "the payload is data, not a second prompt"
 * is a property of the request rather than of the caller's diligence.
 */
public final class DerivedRequest {

    /** A shape with no output array has no ceiling; {@link #itemCeiling()} then means "no limit". */
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

    private DerivedRequest(ArtifactKind kind, String scopeId, String basedOnRevisionId, String inputJson,
            int itemCeiling, int compressionSourceChars, boolean coversReasoning, GenerationModel model,
            PromptBudget budget) {
        this.kind = Guard.object(kind, "kind");
        this.scopeId = Guard.id(scopeId, "scopeId");
        if (kind.revisionBound()) {
            this.basedOnRevisionId = Guard.id(basedOnRevisionId, "basedOnRevisionId");
        } else if (basedOnRevisionId != null) {
            throw new IllegalArgumentException("basedOnRevisionId must be absent for " + kind
                    + "; only a revision-bound kind names the answer it describes");
        } else {
            this.basedOnRevisionId = null;
        }
        this.inputJson = Guard.nonBlank(inputJson, "inputJson");
        this.inputDigest = ContextSnapshotCodec.digestOf(inputJson);
        this.itemCeiling = Guard.atLeast(itemCeiling, 1, "itemCeiling");
        this.compressionSourceChars = compressionSourceChars;
        this.coversReasoning = coversReasoning;
        this.model = Guard.object(model, "model");
        this.budget = Guard.object(budget, "budget");
    }

    /** A root title: the root question alone, so its input never changes (prompt design 6.1). */
    public static DerivedRequest rootTitle(String scopeId, String rootQuestion, GenerationModel model,
            PromptBudget budget) {
        return new DerivedRequest(ArtifactKind.ROOT_TITLE, scopeId, null,
                ArtifactInputs.rootTitle(rootQuestion), NO_ITEM_CEILING, 0, true, model, budget);
    }

    /** A non-root title: the source anchor and the first question of the branch (prompt design 6.1). */
    public static DerivedRequest branchTitle(String scopeId, String sourceQuote, String firstQuestion,
            GenerationModel model, PromptBudget budget) {
        return new DerivedRequest(ArtifactKind.BRANCH_TITLE, scopeId, null,
                ArtifactInputs.branchTitle(sourceQuote, firstQuestion), NO_ITEM_CEILING, 0, true, model,
                budget);
    }

    /** A branch summary and its key points, bound to the revision the branch now shows. */
    public static DerivedRequest branchSummary(String scopeId, String basedOnRevisionId,
            List<ContextPlan.Entry> birthContext, List<ContextPlan.Entry> branchMessages,
            GenerationModel model, PromptBudget budget) {
        return new DerivedRequest(ArtifactKind.BRANCH_SUMMARY, scopeId, basedOnRevisionId,
                ArtifactInputs.branchSummary(birthContext, branchMessages), NO_ITEM_CEILING, 0, true, model,
                budget);
    }

    /** Follow-up questions bound to one specific stable revision, with the caller's ceiling. */
    public static DerivedRequest answerRecommendations(String scopeId, String basedOnRevisionId,
            String latestAnswer, List<TreeOutline.Entry> outline, int maxItems, GenerationModel model,
            PromptBudget budget) {
        return new DerivedRequest(ArtifactKind.ANSWER_RECOMMENDATIONS, scopeId, basedOnRevisionId,
                ArtifactInputs.answerRecommendations(latestAnswer, outline, maxItems), maxItems, 0, true,
                model, budget);
    }

    /** Home recommendations: the topic-tree outline digest is the baseline; no revision is involved. */
    public static DerivedRequest homeRecommendations(String scopeId, List<TreeOutline.Entry> outline,
            int maxItems, GenerationModel model, PromptBudget budget) {
        return new DerivedRequest(ArtifactKind.HOME_RECOMMENDATIONS, scopeId, null,
                ArtifactInputs.homeRecommendations(outline, maxItems), maxItems, 0, true, model, budget);
    }

    /** A compression that covers the question, the reasoning and the answer of every older turn. */
    public static DerivedRequest contextCompression(String scopeId, String basedOnRevisionId,
            List<ArtifactInputs.CompressionTurn> turns, GenerationModel model, PromptBudget budget) {
        List<ArtifactInputs.CompressionTurn> covered = Guard.object(turns, "turns");
        int sourceChars = 0;
        boolean coversReasoning = !covered.isEmpty();
        for (ArtifactInputs.CompressionTurn turn : covered) {
            sourceChars += turn.chars();
            coversReasoning = coversReasoning && turn.coversReasoning();
        }
        return new DerivedRequest(ArtifactKind.CONTEXT_COMPRESSION, scopeId, basedOnRevisionId,
                ArtifactInputs.contextCompression(covered), NO_ITEM_CEILING, sourceChars, coversReasoning,
                model, budget);
    }

    /** Compression over the exact older plan entries that will be replaced by one summary entry. */
    public static DerivedRequest contextCompressionPlan(String scopeId, String basedOnRevisionId,
            List<ContextPlan.Entry> entries, GenerationModel model, PromptBudget budget) {
        ContextPlan plan = new ContextPlan(Guard.object(entries, "entries"));
        boolean coversReasoning = true;
        boolean hasRevision = false;
        for (ContextPlan.Entry entry : plan.entries()) {
            if (entry.kind == ContextPlan.EntryKind.STABLE_REVISION) {
                hasRevision = true;
                coversReasoning = coversReasoning && !entry.reasoningText.trim().isEmpty();
            }
        }
        return new DerivedRequest(ArtifactKind.CONTEXT_COMPRESSION, scopeId, basedOnRevisionId,
                ArtifactInputs.contextCompressionEntries(plan.entries()), NO_ITEM_CEILING,
                plan.chars(), hasRevision && coversReasoning, model, budget);
    }

    public ArtifactKind kind() {
        return kind;
    }

    public String scopeId() {
        return scopeId;
    }

    /** Null unless the kind is revision-bound. */
    public String basedOnRevisionId() {
        return basedOnRevisionId;
    }

    /** The envelope that will be sent, canonically encoded. */
    public String inputJson() {
        return inputJson;
    }

    /** The canonical digest of {@link #inputJson()}, recorded on the stored artifact. */
    public String inputDigest() {
        return inputDigest;
    }

    /** The caller's {@code max_items} for a recommendation shape; no limit for a shape without an array. */
    public int itemCeiling() {
        return itemCeiling;
    }

    /** Content characters of the turns a compression covers; 0 for every other kind. */
    public int compressionSourceChars() {
        return compressionSourceChars;
    }

    /** False when a compression was asked to cover a turn whose reasoning is missing. */
    public boolean inputCoversReasoning() {
        return coversReasoning;
    }

    public GenerationModel model() {
        return model;
    }

    /** The baseline the write-back re-checks: the revision, or the input digest when the input is it. */
    public String baselineRef() {
        return kind.revisionBound() ? basedOnRevisionId : inputDigest;
    }

    /** The version the purpose's own registered spec pins; never the teaching version. */
    public String policyVersion() {
        return DeepSeekPromptRegistry.spec(kind.purpose).policyVersion;
    }

    /** Assembles the one call this request makes: system body first, the envelope as the only user turn. */
    public DeepSeekRequest assemble() {
        return DeepSeekRequestAssembler.assemble(kind.purpose, false,
                Collections.<DeepSeekMessage>emptyList(), inputJson, budget);
    }
}
