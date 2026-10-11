package com.treelearn.web.generation;

import com.treelearn.ai.gateway.AiGatewayError;
import com.treelearn.ai.gateway.GenerationSink;
import com.treelearn.ai.gateway.StreamOutcome;
import com.treelearn.ai.prompt.DeepSeekRequest;
import com.treelearn.ai.prompt.PromptBuildException;
import com.treelearn.core.domain.AnswerRevision;
import com.treelearn.core.domain.Branch;
import com.treelearn.core.domain.DomainResult;
import com.treelearn.core.domain.GenerationCommand;
import com.treelearn.core.domain.GenerationOperation;
import com.treelearn.core.domain.GenerationState;
import com.treelearn.core.domain.Message;
import com.treelearn.core.domain.Topic;
import com.treelearn.web.storage.AnswerRevisionStore;
import com.treelearn.web.storage.BranchStore;
import com.treelearn.web.storage.DesktopSchema;
import com.treelearn.web.storage.GenerationStabilizer;
import com.treelearn.web.storage.JdbcSupport;
import com.treelearn.web.storage.MessageStore;
import com.treelearn.web.storage.OperationStore;
import com.treelearn.web.storage.StorageException;
import com.treelearn.web.storage.TopicStore;
import java.sql.Connection;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The application-scoped desktop generation coordinator (technical design §7, §8, §9.3; task
 * T-2026-10-10-024).
 *
 * <p>Semantic reference — the Android {@code com.treelearn.app.generation.GenerationCoordinator} it
 * mirrors, with the same authority rule: every durable fact about an attempt lives in SQLite, and the
 * in-memory map below is a scheduling cache only, answering "which call is in flight right now". A
 * restart empties the map while the rows still say what happened ({@code OperationStore.listActive} +
 * {@code InterruptionSweeper} rebuild the outcome, never this map).
 *
 * <p><strong>What it decides, and what it deliberately does not.</strong> The accept writes, the
 * single-branch mutex, {@code commandId} idempotency, the write window (256 characters or 250 ms,
 * technical design §7), dropping events that are no longer the active attempt's, and the terminal
 * mapping are decided here. The one judgement it must not make is "this answer is finished": §9.3
 * allows a revision only when the stream terminated normally <em>and</em> the final answer is
 * non-empty, so completion is handed to {@link GenerationStabilizer} and {@link OperationStore} and
 * reported as told — never inspected here.
 *
 * <p><strong>Nothing here holds conversation content as a field or a log line.</strong> Deltas go from
 * the gateway callback straight into the store's own append statement; the diagnostic log carries
 * counts, identities, the model tier and error categories only — never a question, an answer, a
 * reasoning text, a prompt or a key (technical design §15). The event bus is published while the
 * coordinator lock is held: publishing is synchronous on the calling thread, so a listener that calls
 * back into the coordinator re-enters the same thread and cannot deadlock it.
 */
public final class DesktopGenerationCoordinator {

    private static final Logger LOG = LoggerFactory.getLogger(DesktopGenerationCoordinator.class);

    /** Flush window: this many accumulated characters across both channels (technical design §7). */
    private static final int FLUSH_CHARS = 256;
    /** Flush window: this many milliseconds since the window opened, whichever comes first. */
    private static final long FLUSH_MILLIS = 250L;

    /** Turns one immutable command into the request that will be sent (the T-023 assembly). */
    public interface Assembler {

        DeepSeekRequest assemble(GenerationCommand command);
    }

    /** Why a submission or an accept was refused. The tokens are the contract's (§3.3). */
    public enum Refusal {
        /** The branch already has an in-flight attempt, so a second one would break §7. */
        BRANCH_BUSY,
        /** A row already carries this topic id, so a second root for one command is refused. */
        TOPIC_ID_ALREADY_EXISTS,
        /** The request could not be built, so no call may be made. */
        PROMPT_BUILD,
        /** The command and the assembled request disagree about the pinned policy version. */
        VERSION_MISMATCH,
        /** The local write was refused, so no request must be sent. */
        STORAGE
    }

    /** The committed identities of one accept. Exactly one of {@code accepted}/{@code refusal} is set. */
    public static final class Accepted {

        public final String topicId;
        public final String rootBranchId;
        public final String userMessageId;
        public final String aiMessageId;
        public final String operationId;
        public final String commandId;
        /** True when this accept matched an already-stored operation for the same command. */
        public final boolean replayed;

        Accepted(String topicId, String rootBranchId, String userMessageId, String aiMessageId,
                String operationId, String commandId, boolean replayed) {
            this.topicId = topicId; this.rootBranchId = rootBranchId; this.userMessageId = userMessageId;
            this.aiMessageId = aiMessageId; this.operationId = operationId; this.commandId = commandId;
            this.replayed = replayed;
        }
    }

    /** The outcome of one accept. */
    public static final class AcceptOutcome {

        public final Accepted accepted;
        public final Refusal refusal;
        public final String detail;

        private AcceptOutcome(Accepted accepted, Refusal refusal, String detail) {
            this.accepted = accepted; this.refusal = refusal; this.detail = detail;
        }

        public boolean isAccepted() {
            return refusal == null;
        }
    }

    /** The outcome of one submission. */
    public static final class SubmitOutcome {

        public final String operationId;
        /** True when this call opened the stream; a replay never opens a second one. */
        public final boolean started;
        /** True when an operation already existed for this command. */
        public final boolean replayed;
        public final Refusal refusal;
        public final PromptBuildException.Reason promptReason;

        private SubmitOutcome(String operationId, boolean started, boolean replayed, Refusal refusal,
                PromptBuildException.Reason promptReason) {
            this.operationId = operationId; this.started = started; this.replayed = replayed;
            this.refusal = refusal; this.promptReason = promptReason;
        }

        public boolean isAccepted() {
            return refusal == null;
        }
    }

    /** The body of one accept's transaction, given the connection the coordinator owns. */
    private interface Commit {

        void run(Connection connection);
    }

    private final JdbcSupport jdbc;
    private final TopicStore topics;
    private final BranchStore branches;
    private final MessageStore messages;
    private final OperationStore operations;
    private final GenerationStabilizer stabilizer;
    private final GenerationEventBus bus;
    private final DesktopGenerationGateway gateway;
    private final Clock clock;

    private final Object lock = new Object();
    private final Map<String, Attempt> attempts = new LinkedHashMap<>();

    public DesktopGenerationCoordinator(JdbcSupport jdbc, TopicStore topics, BranchStore branches,
            MessageStore messages, OperationStore operations, GenerationEventBus bus,
            DesktopGenerationGateway gateway, Clock clock) {
        this.jdbc = require(jdbc, "jdbc"); this.topics = require(topics, "topics");
        this.branches = require(branches, "branches"); this.messages = require(messages, "messages");
        this.operations = require(operations, "operations"); this.bus = require(bus, "bus");
        this.gateway = require(gateway, "gateway"); this.clock = require(clock, "clock");
        this.stabilizer = new GenerationStabilizer(jdbc, operations, new AnswerRevisionStore(jdbc),
                messages, branches);
    }

    /** The fan-out a subscriber attaches to, so T-026 can serve the SSE stream from it. */
    public GenerationEventBus eventBus() {
        return bus;
    }

    /**
     * Accepts the first question of a new conversation: the topic, its root branch, the learner's
     * question, the answer position and the attempt, in one transaction, or none. The caller builds the
     * topic, the root branch and both positions (so it can pin the command against the answer
     * position's id), mirroring Android's {@code FirstTopicCreation}; a repeated {@code commandId}
     * returns the already-stored identity and writes nothing.
     */
    public AcceptOutcome acceptForNewTopic(GenerationCommand command, Topic topic, Branch rootBranch,
            Message userMessage, Message aiMessage) {
        require(command, "command"); require(topic, "topic"); require(rootBranch, "rootBranch");
        requireLinked(command, userMessage, aiMessage);
        synchronized (lock) {
            AcceptOutcome replay = replay(command);
            if (replay != null) {
                return replay;
            }
            GenerationOperation operation = GenerationOperation.start(command, clock.millis());
            AcceptOutcome failure = commit(command, operation, connection -> {
                topics.insert(connection, topic);
                branches.insertRoot(connection, rootBranch);
                messages.insert(connection, userMessage);
                messages.insert(connection, aiMessage);
                operations.insert(connection, operation);
                bind(connection, aiMessage.id, operation.id);
            });
            return failure != null ? failure
                    : accepted(topic.id, rootBranch.id, userMessage.id, aiMessage.id, operation.id,
                            command.id, false);
        }
    }

    /**
     * Accepts a follow-up in an existing branch: the learner position, the answer position and the
     * attempt, in one transaction. A branch that already has an in-flight attempt is refused with
     * {@link Refusal#BRANCH_BUSY} — checked first through {@code activeForBranch} and backstopped by the
     * partial unique index {@code uq_generation_operations_active} — and a repeated {@code commandId}
     * returns the already-stored identity and writes nothing.
     */
    public AcceptOutcome acceptForBranch(GenerationCommand command, Message userMessage,
            Message aiMessage) {
        require(command, "command");
        requireLinked(command, userMessage, aiMessage);
        synchronized (lock) {
            AcceptOutcome replay = replay(command);
            if (replay != null) {
                return replay;
            }
            if (operations.activeForBranch(command.branchId).isPresent()) {
                return new AcceptOutcome(null, Refusal.BRANCH_BUSY,
                        "branch " + command.branchId + " already has an in-flight attempt");
            }
            GenerationOperation operation = GenerationOperation.start(command, clock.millis());
            AcceptOutcome failure = commit(command, operation, connection -> {
                messages.insert(connection, userMessage);
                messages.insert(connection, aiMessage);
                operations.insert(connection, operation);
                bind(connection, aiMessage.id, operation.id);
            });
            return failure != null ? failure
                    : accepted(null, command.branchId, userMessage.id, aiMessage.id, operation.id,
                            command.id, false);
        }
    }

    /**
     * Accepts an in-place replacement at an existing AI position (retry, contract §6.4): a new attempt
     * row bound to the same position, in one transaction. No message is written, so the position's
     * {@code ordinal} and the question/answer pair are untouched; a repeated {@code commandId} returns
     * the already-stored identity. The branch mutex and idempotency rules are the same as a follow-up.
     */
    public AcceptOutcome acceptForRetry(GenerationCommand command) {
        require(command, "command");
        synchronized (lock) {
            AcceptOutcome replay = replay(command);
            if (replay != null) {
                return replay;
            }
            if (operations.activeForBranch(command.branchId).isPresent()) {
                return new AcceptOutcome(null, Refusal.BRANCH_BUSY,
                        "branch " + command.branchId + " already has an in-flight attempt");
            }
            GenerationOperation operation = GenerationOperation.start(command, clock.millis());
            AcceptOutcome failure = commit(command, operation, connection -> {
                operations.insert(connection, operation);
                bind(connection, command.messageId, operation.id);
            });
            return failure != null ? failure
                    : accepted(null, command.branchId, null, command.messageId, operation.id,
                            command.id, false);
        }
    }

    /**
     * Opens the stream for an attempt an accept already admitted: the request is assembled, then the
     * gateway is opened and the deltas are fanned out and persisted. A command that already has an
     * operation (a replay, or a second submit while the call is live) returns that identity and opens no
     * second call.
     */
    public SubmitOutcome submit(GenerationCommand command, Assembler assembler) {
        require(command, "command"); require(assembler, "assembler");
        DeepSeekRequest request;
        try {
            request = assembler.assemble(command);
        } catch (PromptBuildException refused) {
            return submitRefused(Refusal.PROMPT_BUILD, refused.reason);
        }
        if (request == null || !command.promptPolicyVersion.equals(request.policyVersion)) {
            return submitRefused(Refusal.VERSION_MISMATCH, null);
        }
        synchronized (lock) {
            Attempt attempt = attempts.get(command.id);
            if (attempt == null) {
                Optional<OperationStore.Operation> stored = operations.byCommandId(command.id);
                return stored.isPresent() ? submitReplayed(stored.get().id)
                        : submitRefused(Refusal.STORAGE, null);
            }
            if (attempt.opened) {
                return submitReplayed(attempt.operation.id);
            }
            attempt.request = request;
            attempt.opened = true;
            attempt.stream = gateway.open(command.model, request, new Sink(attempt));
            return new SubmitOutcome(attempt.operation.id, true, false, null, null);
        }
    }

    /**
     * Cancels one attempt: the call is aborted and the outcome recorded as {@code CANCELED}, keeping the
     * partial content (contract §6.4), mirroring Android's {@code GenerationCoordinator.cancel}.
     *
     * @return true when the attempt was still known to the coordinator
     */
    public boolean cancel(String operationId) {
        require(operationId, "operationId");
        synchronized (lock) {
            Attempt attempt = byOperation(operationId);
            if (attempt == null) {
                return false;
            }
            flush(attempt);
            settle(attempt, GenerationState.CANCELED, null, null);
            return true;
        }
    }

    private AcceptOutcome replay(GenerationCommand command) {
        Optional<OperationStore.Operation> existing = operations.byCommandId(command.id);
        if (existing.isEmpty()) {
            return null;
        }
        OperationStore.Operation operation = existing.get();
        Optional<Branch> branch = branches.get(operation.branchId);
        return accepted(branch.map(value -> value.topicId).orElse(null),
                branch.filter(Branch::isRoot).map(value -> value.id).orElse(null), null,
                operation.messageId, operation.id, operation.commandId, true);
    }

    private AcceptOutcome commit(GenerationCommand command, GenerationOperation operation,
            Commit body) {
        try {
            jdbc.runInTransaction(connection -> {
                body.run(connection);
                return null;
            });
        } catch (StorageException refused) {
            return new AcceptOutcome(null, classify(refused), refused.getMessage());
        }
        attempts.put(command.id, new Attempt(command, operation));
        LOG.info("generation accepted: operation={} branch={} model={} thinking={}", operation.id,
                command.branchId, command.model.wireId, command.thinkingEnabled);
        return null;
    }

    private static AcceptOutcome accepted(String topicId, String rootBranchId, String userMessageId,
            String aiMessageId, String operationId, String commandId, boolean replayed) {
        return new AcceptOutcome(new Accepted(topicId, rootBranchId, userMessageId, aiMessageId,
                operationId, commandId, replayed), null, null);
    }

    private static SubmitOutcome submitRefused(Refusal refusal, PromptBuildException.Reason reason) {
        return new SubmitOutcome(null, false, false, refusal, reason);
    }

    private static SubmitOutcome submitReplayed(String operationId) {
        return new SubmitOutcome(operationId, false, true, null, null);
    }

    private void bind(Connection connection, String messageId, String operationId) {
        Message position = messages.get(connection, messageId).orElseThrow(
                () -> new StorageException("no message row carries id " + messageId));
        DomainResult<Void> bound = position.bindOperation(operationId);
        if (bound.isFailure()) {
            throw new StorageException("cannot bind the running attempt of message " + messageId + ": "
                    + bound.error());
        }
        messages.persistActiveReferences(connection, position);
    }

    // ------------------------------------------------------------------
    // Gateway callbacks. Each runs on the call's reader thread.
    // ------------------------------------------------------------------

    private final class Sink implements GenerationSink {

        private final Attempt attempt;

        Sink(Attempt attempt) {
            this.attempt = attempt;
        }

        @Override
        public boolean onAccepted(int httpStatus) {
            synchronized (lock) {
                if (!isCurrent(attempt)) {
                    return false;
                }
                // STAGED -> VALIDATING -> SUBMITTING through the domain's own graph, not a second copy.
                if (attempt.operation.advanceTo(GenerationState.VALIDATING).isFailure()
                        || attempt.operation.advanceTo(GenerationState.SUBMITTING).isFailure()) {
                    return false;
                }
                try {
                    operations.updateProgress(attempt.operation.id, "", "",
                            attempt.operation.state(), clock.millis());
                } catch (StorageException refused) {
                    settle(attempt, GenerationState.FAILED, null, null);
                    return false;
                }
                publish(attempt, GenerationEventBus.Type.STATE,
                        Map.of("state", attempt.operation.state().name()));
                return true;
            }
        }

        @Override
        public void onDelta(String reasoningChunk, String contentChunk) {
            synchronized (lock) {
                if (!isCurrent(attempt)) {
                    return;
                }
                // The domain decides whether a delta of this channel may land; a refusal is a late
                // event, not a crash.
                if (attempt.operation.appendReasoning(reasoningChunk).isFailure()
                        || attempt.operation.appendAnswer(contentChunk).isFailure()) {
                    return;
                }
                long now = clock.millis();
                boolean openedStreaming = !attempt.streaming;
                attempt.streaming = true;
                attempt.append(reasoningChunk, contentChunk, now);
                if (reasoningChunk != null && !reasoningChunk.isEmpty()) {
                    publish(attempt, GenerationEventBus.Type.REASONING_DELTA,
                            Map.of("delta", reasoningChunk));
                }
                if (contentChunk != null && !contentChunk.isEmpty()) {
                    publish(attempt, GenerationEventBus.Type.ANSWER_DELTA, Map.of("delta", contentChunk));
                }
                if (openedStreaming) {
                    publish(attempt, GenerationEventBus.Type.STATE,
                            Map.of("state", attempt.operation.state().name()));
                }
                if (attempt.due(now)) {
                    flush(attempt);
                }
            }
        }

        @Override
        public void onTerminal(StreamOutcome outcome) {
            synchronized (lock) {
                if (!isCurrent(attempt)) {
                    return;
                }
                flush(attempt);
                switch (outcome.kind) {
                    case COMPLETED:
                        complete(attempt);
                        break;
                    case INTERRUPTED:
                        settle(attempt, GenerationState.INTERRUPTED, null, null);
                        break;
                    case CANCELED:
                        settle(attempt, GenerationState.CANCELED, null, null);
                        break;
                    case FAILED:
                    default:
                        settle(attempt, GenerationState.FAILED, null, outcome.error);
                        break;
                }
            }
        }
    }

    /**
     * The normally finished case: ask the stabilizer to freeze the attempt and report what it said.
     * Nothing here inspects the answer — an attempt that ended normally with an empty answer is refused
     * by the stabilizer's non-empty rule and reported as a failure, not turned into a revision (§9.3).
     */
    private void complete(Attempt attempt) {
        DomainResult<AnswerRevision> stabilized;
        try {
            stabilized = stabilizer.stabilize(attempt.operation.id, clock.millis());
        } catch (StorageException refused) {
            settle(attempt, GenerationState.FAILED, null, null);
            return;
        }
        if (stabilized.isFailure()) {
            settle(attempt, GenerationState.FAILED, null, null);
            return;
        }
        settle(attempt, null, stabilized.value(), null);
    }

    /**
     * Writes one outcome and drops the attempt from the registry, so "exactly one outcome per attempt"
     * has one home. The registry is emptied <em>first</em>, so a gateway that delivers its cancel
     * callback synchronously is already stale and cannot re-settle.
     */
    private void settle(Attempt attempt, GenerationState terminal, AnswerRevision revision,
            AiGatewayError error) {
        if (!isCurrent(attempt)) {
            return;
        }
        attempts.remove(attempt.command.id);
        attempt.opened = false;
        if (attempt.stream != null) {
            attempt.stream.cancel();
        }
        String errorCode = error == null ? null : error.kind.name();
        if (terminal != null) {
            if (attempt.operation.advanceTo(terminal).isFailure()) {
                return;
            }
            try {
                operations.markTerminal(attempt.operation.id, terminal, errorCode, clock.millis());
            } catch (StorageException refused) {
                // The row is gone (its branch or message was deleted) or already terminal: nothing to
                // write back, and the attempt is still released.
            }
            try {
                messages.clearActiveOperation(attempt.command.messageId);
            } catch (StorageException refused) {
                // The position is gone with its branch; there is nothing to clear.
            }
        }
        GenerationEventBus.Type type = revision != null ? GenerationEventBus.Type.STABLE
                : terminalEvent(terminal);
        Map<String, Object> data = new LinkedHashMap<>();
        if (revision != null) {
            data.put("revisionId", revision.id);
        }
        if (terminal == GenerationState.FAILED && errorCode != null) {
            data.put("errorCode", errorCode);
            // §7.2/§3.3: a failed frame carries the safe params object. The provider status is the
            // one interpolation a gateway error can supply; when the call never got a response the
            // minimal set is empty.
            Map<String, Object> params = new LinkedHashMap<>();
            if (error.httpStatus > 0) {
                params.put("httpStatus", error.httpStatus);
            }
            data.put("params", params);
        }
        publish(attempt, type, data);
        LOG.info("generation {}: operation={} branch={} answerChars={}", type, attempt.operation.id,
                attempt.command.branchId, attempt.operation.partialAnswer().length());
    }

    private void flush(Attempt attempt) {
        if (!attempt.hasPending()) {
            return;
        }
        long now = clock.millis();
        String reasoning = attempt.takeReasoning();
        String answer = attempt.takeAnswer();
        attempt.flushed(now);
        try {
            operations.updateProgress(attempt.operation.id, reasoning, answer,
                    attempt.operation.state(), now);
        } catch (StorageException refused) {
            // The row is gone or already terminal: a late batch must not write back.
            LOG.debug("generation batch discarded as stale: operation={}", attempt.operation.id);
        }
    }

    private void publish(Attempt attempt, GenerationEventBus.Type type, Map<String, ?> data) {
        bus.publish(attempt.operation.id, type, data);
    }

    private boolean isCurrent(Attempt attempt) {
        return attempts.get(attempt.command.id) == attempt;
    }

    private Attempt byOperation(String operationId) {
        for (Attempt attempt : attempts.values()) {
            if (attempt.operation.id.equals(operationId)) {
                return attempt;
            }
        }
        return null;
    }

    private static GenerationEventBus.Type terminalEvent(GenerationState terminal) {
        switch (terminal) {
            case INTERRUPTED:
                return GenerationEventBus.Type.INTERRUPTED;
            case CANCELED:
                return GenerationEventBus.Type.CANCELED;
            default:
                return GenerationEventBus.Type.FAILED;
        }
    }

    /** Separates "the branch is busy" and "the topic id is taken" from refusals this entry point cannot name. */
    private static Refusal classify(StorageException refused) {
        String text = describeFailure(refused);
        if (text.contains(DesktopSchema.GenerationOperations.TABLE + "."
                + DesktopSchema.GenerationOperations.BRANCH_ID)) {
            return Refusal.BRANCH_BUSY;
        }
        if (text.contains(DesktopSchema.Topics.TABLE + "." + DesktopSchema.Topics.ID)) {
            return Refusal.TOPIC_ID_ALREADY_EXISTS;
        }
        return Refusal.STORAGE;
    }

    /** Collects the messages of a failure and its causes; the classified text, never row content. */
    private static String describeFailure(Throwable failure) {
        StringBuilder text = new StringBuilder();
        for (Throwable current = failure; current != null; current = current.getCause()) {
            text.append(current.getMessage()).append(' ');
        }
        return text.toString();
    }

    private static void requireLinked(GenerationCommand command, Message userMessage,
            Message aiMessage) {
        require(userMessage, "userMessage"); require(aiMessage, "aiMessage");
        if (!command.branchId.equals(userMessage.branchId)
                || !command.branchId.equals(aiMessage.branchId)) {
            throw new IllegalArgumentException("both positions must belong to branch "
                    + command.branchId + "; the command and the accept disagree");
        }
        if (!aiMessage.id.equals(command.messageId)) {
            throw new IllegalArgumentException("the command pins answer position " + command.messageId
                    + ", not " + aiMessage.id + "; the operation must record the position it answers into");
        }
    }

    private static <T> T require(T value, String field) {
        if (value == null) {
            throw new IllegalArgumentException(field + " must not be null");
        }
        return value;
    }

    /** One attempt the coordinator is driving: the command, the attempt, and its batching window. */
    private static final class Attempt {

        final GenerationCommand command;
        final GenerationOperation operation;
        private final StringBuilder reasoning = new StringBuilder();
        private final StringBuilder answer = new StringBuilder();
        private boolean windowOpen;
        private long windowStart;
        DeepSeekRequest request;
        DesktopGenerationGateway.Stream stream;
        boolean opened;
        boolean streaming;

        Attempt(GenerationCommand command, GenerationOperation operation) {
            this.command = command;
            this.operation = operation;
        }

        void append(String reasoningDelta, String answerDelta, long now) {
            String reasoningChunk = reasoningDelta == null ? "" : reasoningDelta;
            String answerChunk = answerDelta == null ? "" : answerDelta;
            if (!windowOpen && reasoningChunk.length() + answerChunk.length() > 0) {
                windowOpen = true;
                windowStart = now;
            }
            reasoning.append(reasoningChunk);
            answer.append(answerChunk);
        }

        boolean due(long now) {
            int pending = reasoning.length() + answer.length();
            return pending > 0 && (pending >= FLUSH_CHARS || now - windowStart >= FLUSH_MILLIS);
        }

        boolean hasPending() {
            return reasoning.length() + answer.length() > 0;
        }

        String takeReasoning() {
            String value = reasoning.toString();
            reasoning.setLength(0);
            return value;
        }

        String takeAnswer() {
            String value = answer.toString();
            answer.setLength(0);
            return value;
        }

        void flushed(long now) {
            reasoning.setLength(0);
            answer.setLength(0);
            windowOpen = false;
            windowStart = now;
        }
    }
}
