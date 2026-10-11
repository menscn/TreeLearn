package com.treelearn.web.generation;

import com.treelearn.ai.prompt.PromptBudget;
import com.treelearn.core.domain.AnswerRevision;
import com.treelearn.core.domain.ContextPlan;
import com.treelearn.core.domain.ContextSnapshot;
import com.treelearn.core.domain.DomainErrorCode;
import com.treelearn.core.domain.DomainResult;
import com.treelearn.core.domain.Guard;
import com.treelearn.core.domain.JsonText;
import com.treelearn.core.domain.Message;
import com.treelearn.core.domain.PromptPurpose;
import com.treelearn.web.storage.AnswerRevisionStore;
import com.treelearn.web.storage.ArtifactStore;
import com.treelearn.web.storage.MessageStore;
import com.treelearn.web.storage.SnapshotStore;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Desktop context assembly: the vendor-neutral {@link ContextPlan} of one request (task
 * T-2026-10-10-023, contract §5.10 / §6.5).
 *
 * <p>It is the desktop mirror of three Android units, named here so the two cannot drift without a
 * reader noticing:
 *
 * <ul>
 *   <li>{@code com.treelearn.app.wiring.SqliteChatStore.history(branchId, questionOrdinal)} — the
 *       branch's own rows strictly before the current question, learner text as a user turn and each
 *       displayed stable revision as an assistant turn; an AI position with no displayed revision
 *       contributes nothing.</li>
 *   <li>{@code SnapshotHistoryMerger.merge(snapshot, branchRows, rows)} — a non-root branch's birth
 *       snapshot is read through {@code ContextSnapshot.fromStored} (the store already rehydrates and
 *       digest-verifies it here) and its references are resolved row by row <em>in front of</em> the
 *       branch's own turns. A reference the store cannot resolve is a refusal, never a shorter
 *       context; the desktop has no summary read path yet, so a {@code SUMMARY} reference is refused
 *       with {@link DomainErrorCode#CONTEXT_SUMMARY_MISSING} exactly as Android does.</li>
 *   <li>{@code ChatWiring.historyForRequest(...)} — over {@code KEPT_STABLE_ROUNDS} complete rounds,
 *       the older ones are replaced by one derived summary entry whose position and baseline mirror
 *       the Android split.</li>
 * </ul>
 *
 * <p><strong>Deliberate difference, until attachments have a desktop read path (T-018):</strong>
 * Android's {@code SqliteChatStore.history} appends an attachment's extracted text to the user turn
 * ({@code attachmentSuffix}); the desktop assembler does not yet, because attachments are neither
 * stored nor read on the desktop side. When the desktop attachment stores land, the history builder
 * must add the same suffix so the two contexts converge again.
 *
 * <p><strong>No second context or budget judgement.</strong> The plan is built only from the shared
 * {@link ContextPlan} entry constructors, the snapshot decode is the shared domain's, and the capacity
 * decision reuses the shared {@link PromptBudget} and the shared
 * {@link DomainErrorCode#CONTEXT_EXCEEDS_BUDGET}, applying the same rule as
 * {@link com.treelearn.core.domain.BuildContextPlanUseCase#build}: the content characters plus the
 * reservation the caller has already spoken for must fit {@code capacityChars}. Like Android's
 * reserve, history is never silently dropped or truncated to fit — the request is refused authoritatively.
 */
public final class GenerationContextAssembler {

    /** Mirror Android {@code ChatWiring.historyForRequest}: keep this many newest complete rounds. */
    static final int KEPT_STABLE_ROUNDS = 10;

    /** Contract §5.10: {@code ai_artifacts.kind} of a context compression. */
    static final String COMPRESSION_KIND = "context_compression";

    private final MessageStore messages;
    private final AnswerRevisionStore revisions;
    private final SnapshotStore snapshots;
    private final ArtifactStore artifacts;
    private final PromptBudget budget;

    public GenerationContextAssembler(MessageStore messages, AnswerRevisionStore revisions,
            SnapshotStore snapshots, ArtifactStore artifacts, PromptBudget budget) {
        this.messages = Guard.object(messages, "messages");
        this.revisions = Guard.object(revisions, "revisions");
        this.snapshots = Guard.object(snapshots, "snapshots");
        this.artifacts = Guard.object(artifacts, "artifacts");
        this.budget = Guard.object(budget, "budget");
    }

    /**
     * Assembles the ordered history of a request.
     *
     * @param branchId the branch the question belongs to
     * @param questionOrdinal the ordinal of the current question; it is the request's current input
     *        and must not appear in the history (only rows strictly before it are replayed)
     * @param reservedChars what the caller has already reserved inside the capacity (the system
     *        prompt, the current input and the minimum answer); at least 0
     * @return the plan, or a failure with {@link DomainErrorCode#CONTEXT_SOURCE_MISSING},
     *         {@link DomainErrorCode#CONTEXT_SUMMARY_MISSING} or
     *         {@link DomainErrorCode#CONTEXT_EXCEEDS_BUDGET}
     */
    public DomainResult<ContextPlan> assemble(String branchId, int questionOrdinal, int reservedChars) {
        Guard.id(branchId, "branchId");
        Guard.atLeast(questionOrdinal, 0, "questionOrdinal");
        Guard.atLeast(reservedChars, 0, "reservedChars");

        List<ContextPlan.Entry> own = new ArrayList<>();
        for (MessageStore.Position position : messages.listForBranch(branchId)) {
            if (position.message.ordinal >= questionOrdinal) {
                break;
            }
            if (position.message.isUser()) {
                own.add(ContextPlan.Entry.userMessage(position.message.id,
                        position.message.userContent));
            } else if (position.hasActiveRevision()) {
                own.add(ContextPlan.Entry.stableRevision(position.message.activeRevisionId(),
                        position.reasoningContent, position.answerContent));
            }
        }

        List<ContextPlan.Entry> all = new ArrayList<>();
        Optional<ContextSnapshot> snapshot = snapshots.findForBranch(branchId);
        if (snapshot.isPresent()) {
            for (ContextSnapshot.Source source : snapshot.get().sources) {
                DomainResult<ContextPlan.Entry> resolved = birthEntry(source);
                if (resolved.isFailure()) {
                    return DomainResult.fail(resolved.error());
                }
                all.add(resolved.value());
            }
        }
        all.addAll(own);

        ContextPlan plan = new ContextPlan(compress(branchId, all));
        int needed = plan.chars() + reservedChars;
        if (needed > budget.capacityChars) {
            return DomainResult.fail(DomainErrorCode.CONTEXT_EXCEEDS_BUDGET,
                    "the assembled context needs " + plan.chars() + " chars plus " + reservedChars
                            + " reserved, which exceeds the " + budget.capacityChars + " char capacity; "
                            + "history is never silently dropped, truncated or summarised here");
        }
        return DomainResult.ok(plan);
    }

    /** One pinned birth reference, resolved through the same stores the branch's own rows use. */
    private DomainResult<ContextPlan.Entry> birthEntry(ContextSnapshot.Source source) {
        if (source.kind == ContextSnapshot.SourceKind.USER_MESSAGE) {
            Optional<Message> row = messages.get(source.refId);
            if (row.isEmpty() || !row.get().isUser()) {
                return missing("learner message", source.refId);
            }
            return DomainResult.ok(ContextPlan.Entry.userMessage(row.get().id, row.get().userContent));
        }
        if (source.kind == ContextSnapshot.SourceKind.STABLE_REVISION) {
            Optional<AnswerRevision> revision = revisions.get(source.refId);
            if (revision.isEmpty()) {
                return missing("stable revision", source.refId);
            }
            return DomainResult.ok(ContextPlan.Entry.stableRevision(revision.get().id,
                    revision.get().reasoningContent, revision.get().answerContent));
        }
        return DomainResult.fail(DomainErrorCode.CONTEXT_SUMMARY_MISSING,
                "the birth snapshot pins summary " + source.refId + ", which this build cannot resolve "
                        + "yet; inventing its content would put words in the upstream conversation");
    }

    private static DomainResult<ContextPlan.Entry> missing(String what, String refId) {
        return DomainResult.fail(DomainErrorCode.CONTEXT_SOURCE_MISSING,
                "the request names " + what + " " + refId + " but the store cannot resolve it; an "
                        + "unresolvable context source is a refusal, never a silently shorter context");
    }

    /**
     * Mirrors Android {@code ChatWiring.historyForRequest}: once more than {@link #KEPT_STABLE_ROUNDS}
     * stable rounds exist, the older ones become one stored summary. The split keeps the newest ten
     * rounds and never separates a round's question from its answer; a missing or stale compression
     * artifact leaves the full history in place, exactly as Android falls back to the uncompressed list.
     */
    private List<ContextPlan.Entry> compress(String branchId, List<ContextPlan.Entry> all) {
        int stableCount = 0;
        for (ContextPlan.Entry entry : all) {
            if (entry.kind == ContextPlan.EntryKind.STABLE_REVISION) {
                stableCount++;
            }
        }
        if (stableCount <= KEPT_STABLE_ROUNDS) {
            return all;
        }
        int split = all.size();
        int rounds = 0;
        for (int index = all.size() - 1; index >= 0; index--) {
            if (all.get(index).kind == ContextPlan.EntryKind.STABLE_REVISION) {
                rounds++;
                if (rounds == KEPT_STABLE_ROUNDS) {
                    split = index;
                    break;
                }
            }
        }
        if (split <= 0 || split >= all.size()) {
            return all;
        }
        if (all.get(split).kind == ContextPlan.EntryKind.STABLE_REVISION
                && all.get(split - 1).kind == ContextPlan.EntryKind.USER_MESSAGE) {
            split--;
        }
        Optional<ContextPlan.Entry> summary = compressionSummary(branchId, latestRevision(all));
        if (summary.isEmpty()) {
            return all;
        }
        List<ContextPlan.Entry> compressed = new ArrayList<>();
        compressed.add(summary.get());
        compressed.addAll(all.subList(split, all.size()));
        return compressed;
    }

    private static String latestRevision(List<ContextPlan.Entry> all) {
        for (int index = all.size() - 1; index >= 0; index--) {
            if (all.get(index).kind == ContextPlan.EntryKind.STABLE_REVISION) {
                return all.get(index).refId;
            }
        }
        return null;
    }

    /**
     * The stored compression summary as one plan entry, or empty when it is absent or was derived
     * from another revision. Contract §5.10 makes the artifact revision-bound, so a summary naming a
     * different newest revision is stale and reads as none rather than as the current text.
     */
    private Optional<ContextPlan.Entry> compressionSummary(String branchId, String latestRevisionId) {
        if (latestRevisionId == null) {
            return Optional.empty();
        }
        Optional<ArtifactStore.Artifact> stored = artifacts.find(COMPRESSION_KIND, branchId);
        if (stored.isEmpty()) {
            return Optional.empty();
        }
        ArtifactStore.Artifact artifact = stored.get();
        if (artifact.purpose != PromptPurpose.CONTEXT_COMPRESSION
                || !latestRevisionId.equals(artifact.basedOnRevisionId)) {
            return Optional.empty();
        }
        String text = summaryText(artifact.payload);
        return text == null ? Optional.<ContextPlan.Entry>empty()
                : Optional.of(ContextPlan.Entry.summary(artifact.id, text));
    }

    /** Contract §5.10: the compression payload is {@code {"summary": "..."}}; read via the shared parser. */
    private static String summaryText(String payload) {
        try {
            if (JsonText.read(payload) instanceof Map<?, ?> fields
                    && fields.get("summary") instanceof String text && !text.trim().isEmpty()) {
                return text;
            }
        } catch (RuntimeException unreadable) {
            return null;
        }
        return null;
    }
}
