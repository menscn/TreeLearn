package com.treelearn.core.domain;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds the ordered {@link ContextPlan} of one request from the branch's immutable birth snapshot
 * and its own stable turns (technical design 5.3, 9.2, 9.4).
 *
 * <p>The two halves are read through different authorities on purpose. The birth half follows the
 * snapshot's pinned references and nothing else — never {@link Message#activeRevisionId()} — because a
 * reset in the parent branch must not rewrite what an already-created child branch means. The branch's
 * own turns follow the revision each position currently displays, because those turns <em>are</em> this
 * branch's present.
 *
 * <p><strong>Fail closed, never trim.</strong> A reference the store cannot resolve, a summary the
 * compression never produced, or a context that no longer fits the capacity the caller reserved all
 * refuse the whole request. Dropping reasoning to make a request fit would silently change what the
 * model was told and is never a fallback here.
 *
 * <p><strong>Caller obligations this class cannot observe</strong> (stated because a rule an upstream
 * module owns must not look like one this class has checked):
 *
 * <ul>
 *   <li>{@code historyMessages} must be this branch's <em>already persisted</em> turns, in ordinal
 *       order, and must <em>not</em> include the current question: the request assembler sends that
 *       one separately as the final user message. Including it here would duplicate it.
 *   <li>{@code budget.reservedChars} must already cover everything the caller will add around the
 *       history — the system prompt, the current question, the minimum answer space <em>and</em> the
 *       framing the context encoder adds. This class measures only {@link ContextPlan#chars()}.
 *   <li>{@code sources} must be able to see the rows the snapshot pins even though they belong to the
 *       parent branch; visibility is the caller's query.
 * </ul>
 *
 * <p>An answer position without a displayed revision simply contributes no turn: a running attempt is
 * not a context source, and there is no stable content to lose. The caller must still not pass the
 * attempt that is currently streaming.
 */
public final class BuildContextPlanUseCase {

    /**
     * The capacity reservation of one request, as declared by the caller.
     *
     * <p>{@link #reservedChars} is not optional and has no default: an interface that measured only its
     * own half would make "the system prompt is free" the implicit assumption, which is exactly the
     * mistake the design forbids.
     */
    public static final class Budget {

        public final int capacityChars;
        public final int reservedChars;

        /**
         * @param capacityChars the whole request capacity in characters; at least 1
         * @param reservedChars what the caller has already spoken for; at least 0 and strictly less
         *        than {@code capacityChars} — a budget with nothing left cannot hold any context, and
         *        accepting it would only defer the refusal to a call that also has to build a plan
         * @throws IllegalArgumentException on either argument being out of range: a caller bug
         */
        public Budget(int capacityChars, int reservedChars) {
            this.capacityChars = Guard.atLeast(capacityChars, 1, "capacityChars");
            if (reservedChars < 0) {
                throw new IllegalArgumentException("reservedChars must not be negative: "
                        + reservedChars);
            }
            if (reservedChars >= capacityChars) {
                throw new IllegalArgumentException("reservedChars (" + reservedChars
                        + ") must leave room inside capacityChars (" + capacityChars + ")");
            }
            this.reservedChars = reservedChars;
        }
    }

    /**
     * The caller's view of the rows a snapshot pins.
     *
     * <p>Every method returns {@code null} for "this branch cannot see that row", which this class
     * reports as a refusal rather than treating as an empty value: an invisible source and an empty
     * source are different facts, and only one of them is safe to send.
     */
    public interface Sources {

        /** @return the learner text of a message, or null when it is not readable */
        String userMessage(String messageId);

        /** @return a stable revision, or null when it is not readable */
        AnswerRevision revision(String revisionId);

        /**
         * @return the persisted summary text, or null when the compression has not produced one. A
         *         missing summary is a hard refusal: the older turns it was meant to cover would
         *         otherwise disappear from the context without anyone deciding that.
         */
        String summary(String summaryId);
    }

    private BuildContextPlanUseCase() {
    }

    /**
     * @param historyMessages this branch's own persisted turns, in order, excluding the current
     *        question (see the class comment for why that is a caller obligation)
     * @return the plan, or a failure with {@link DomainErrorCode#CONTEXT_SOURCE_MISSING},
     *         {@link DomainErrorCode#CONTEXT_SUMMARY_MISSING} or
     *         {@link DomainErrorCode#CONTEXT_EXCEEDS_BUDGET}. Refusals are decided in one fixed order —
     *         the birth sources, then this branch's own turns, then capacity — so a plan that is wrong
     *         in several ways always reports the same first defect.
     * @throws IllegalArgumentException on a null argument or a null history entry: a caller bug
     */
    public static DomainResult<ContextPlan> build(ContextSnapshot snapshot,
            List<Message> historyMessages, Sources sources, Budget budget) {
        Guard.object(snapshot, "snapshot");
        List<Message> history = Guard.object(historyMessages, "historyMessages");
        Sources store = Guard.object(sources, "sources");
        Budget capacity = Guard.object(budget, "budget");
        // Argument shapes before any business decision, in the spirit of the package rule: a call
        // carrying both a malformed argument and an unusable source must still report the caller bug
        // rather than the business code, so a caller never has to guess which of the two it is
        // looking at.
        for (Message message : history) {
            Guard.object(message, "history message");
        }

        List<ContextPlan.Entry> entries = new ArrayList<ContextPlan.Entry>();
        for (ContextSnapshot.Source source : snapshot.sources) {
            DomainResult<ContextPlan.Entry> resolved = fromBirth(source, store);
            if (resolved.isFailure()) {
                return DomainResult.fail(resolved.error());
            }
            entries.add(resolved.value());
        }
        for (Message message : history) {
            if (message.isAi() && message.activeRevisionId() == null) {
                continue;
            }
            DomainResult<ContextPlan.Entry> resolved = fromOwnTurn(message, store);
            if (resolved.isFailure()) {
                return DomainResult.fail(resolved.error());
            }
            entries.add(resolved.value());
        }

        ContextPlan plan = new ContextPlan(entries);
        int needed = plan.chars() + capacity.reservedChars;
        if (needed > capacity.capacityChars) {
            return DomainResult.fail(DomainErrorCode.CONTEXT_EXCEEDS_BUDGET,
                    "the context needs " + plan.chars() + " chars and the caller reserved "
                            + capacity.reservedChars + ", which exceeds the " + capacity.capacityChars
                            + " char capacity; history is never silently dropped, truncated or "
                            + "summarised here");
        }
        return DomainResult.ok(plan);
    }

    private static DomainResult<ContextPlan.Entry> fromBirth(ContextSnapshot.Source source,
            Sources store) {
        if (source.kind == ContextSnapshot.SourceKind.USER_MESSAGE) {
            String text = store.userMessage(source.refId);
            if (text == null) {
                return missing("user message", source.refId);
            }
            return DomainResult.ok(ContextPlan.Entry.userMessage(source.refId, text));
        }
        if (source.kind == ContextSnapshot.SourceKind.STABLE_REVISION) {
            AnswerRevision revision = store.revision(source.refId);
            if (revision == null) {
                return missing("stable revision", source.refId);
            }
            return DomainResult.ok(ContextPlan.Entry.stableRevision(source.refId,
                    revision.reasoningContent, revision.answerContent));
        }
        String summary = store.summary(source.refId);
        if (summary == null) {
            return DomainResult.fail(DomainErrorCode.CONTEXT_SUMMARY_MISSING,
                    "the snapshot pins summary " + source.refId + " but no finished summary is "
                            + "readable; the turns it covers would have to be dropped, so the request "
                            + "is refused instead (compression failure is a retryable failure, not a "
                            + "reason to shorten the context)");
        }
        return DomainResult.ok(ContextPlan.Entry.summary(source.refId, summary));
    }

    private static DomainResult<ContextPlan.Entry> fromOwnTurn(Message message, Sources store) {
        if (message.isUser()) {
            return DomainResult.ok(ContextPlan.Entry.userMessage(message.id, message.userContent));
        }
        AnswerRevision revision = store.revision(message.activeRevisionId());
        if (revision == null) {
            return missing("stable revision", message.activeRevisionId());
        }
        return DomainResult.ok(ContextPlan.Entry.stableRevision(revision.id, revision.reasoningContent,
                revision.answerContent));
    }

    private static DomainResult<ContextPlan.Entry> missing(String what, String refId) {
        return DomainResult.fail(DomainErrorCode.CONTEXT_SOURCE_MISSING,
                "the request names " + what + " " + refId + " but the store cannot resolve it; an "
                        + "unresolvable context source is a refusal, never a silently shorter context");
    }
}