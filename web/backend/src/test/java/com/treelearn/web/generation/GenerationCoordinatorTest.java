package com.treelearn.web.generation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.treelearn.ai.gateway.AiGatewayError;
import com.treelearn.ai.gateway.GenerationSink;
import com.treelearn.ai.gateway.StreamOutcome;
import com.treelearn.ai.prompt.DeepSeekRequest;
import com.treelearn.ai.prompt.DeepSeekRequestAssembler;
import com.treelearn.ai.prompt.PromptBudget;
import com.treelearn.core.domain.Branch;
import com.treelearn.core.domain.GenerationCommand;
import com.treelearn.core.domain.GenerationModel;
import com.treelearn.core.domain.GenerationState;
import com.treelearn.core.domain.Message;
import com.treelearn.core.domain.PromptPurpose;
import com.treelearn.core.domain.Topic;
import com.treelearn.web.storage.AnswerRevisionStore;
import com.treelearn.web.storage.BranchStore;
import com.treelearn.web.storage.DatabaseMigrator;
import com.treelearn.web.storage.DesktopDatabase;
import com.treelearn.web.storage.JdbcSupport;
import com.treelearn.web.storage.MessageStore;
import com.treelearn.web.storage.OperationStore;
import com.treelearn.web.storage.TopicStore;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * T-2026-10-10-024: the desktop coordinator on real sqlite-jdbc, driven through an injectable fake
 * gateway so each failure combination is produced on demand. The scenarios cover the accept writes and
 * the single-branch mutex, {@code commandId} idempotency (no second model call), streamed deltas with
 * the 256-char write window, every terminal classification, and the late-frame / cancel-race guards.
 */
class GenerationCoordinatorTest {

    private static final long T0 = 1_752_000_000_000L;

    @TempDir
    Path dataDir;

    JdbcSupport jdbc;
    TopicStore topics;
    BranchStore branches;
    MessageStore messages;
    AnswerRevisionStore revisions;
    OperationStore operations;
    GenerationEventBus bus;
    FakeGateway gateway;
    Clock clock;
    DeepSeekRequest request;
    DesktopGenerationCoordinator coordinator;

    @BeforeEach
    void setUp() {
        new DatabaseMigrator().migrate(dataDir.resolve("treelearn.db"));
        jdbc = new JdbcSupport(new DesktopDatabase(dataDir));
        topics = new TopicStore(jdbc);
        branches = new BranchStore(jdbc);
        messages = new MessageStore(jdbc);
        revisions = new AnswerRevisionStore(jdbc);
        operations = new OperationStore(jdbc);
        bus = new GenerationEventBus();
        gateway = new FakeGateway();
        clock = new MutableClock(T0);
        request = DeepSeekRequestAssembler.assemble(PromptPurpose.LEARNING_ANSWER, false,
                List.of(), "问题", new PromptBudget(1_000_000, 512));
        coordinator = new DesktopGenerationCoordinator(jdbc, topics, branches, messages, operations,
                bus, gateway, clock);
    }

    /** Opens the stream with the request whose pinned policy version equals the command's. */
    private DesktopGenerationCoordinator.SubmitOutcome submitFirst(GenerationCommand command) {
        return coordinator.submit(command, forCommand -> request);
    }

    // ------------------------------------------------------------------
    // Fixtures: a new topic and a follow-up, each with a command pinned against its answer position.
    // ------------------------------------------------------------------

    private static final class NewTopic {

        final Topic topic;
        final Branch root;
        final Message user;
        final Message ai;
        final GenerationCommand command;

        NewTopic(long now, String policy) {
            this.topic = Topic.create(now);
            this.root = Branch.root(topic.id, now);
            this.user = Message.user(root.id, 0, "问题", "", now);
            this.ai = Message.ai(root.id, 1, now);
            this.command = GenerationCommand.create(root.id, ai.id, PromptPurpose.LEARNING_ANSWER,
                    policy, GenerationModel.FLASH, false, now).value();
        }
    }

    private static final class FollowUp {

        final Message user;
        final Message ai;
        final GenerationCommand command;

        FollowUp(Branch branch, int ordinal, long now, String policy) {
            this.user = Message.user(branch.id, ordinal, "追问", "", now);
            this.ai = Message.ai(branch.id, ordinal + 1, now);
            this.command = GenerationCommand.create(branch.id, ai.id, PromptPurpose.LEARNING_ANSWER,
                    policy, GenerationModel.FLASH, false, now).value();
        }
    }

    private NewTopic newTopic() {
        return new NewTopic(T0, request.policyVersion);
    }

    private FollowUp followUp(Branch branch) {
        return new FollowUp(branch, messages.nextOrdinal(branch.id), T0, request.policyVersion);
    }

    private DesktopGenerationCoordinator.Accepted accept(NewTopic topic) {
        DesktopGenerationCoordinator.AcceptOutcome outcome = coordinator.acceptForNewTopic(
                topic.command, topic.topic, topic.root, topic.user, topic.ai);
        assertTrue(outcome.isAccepted(), () -> "accept refused: " + outcome.refusal);
        return outcome.accepted;
    }

    private DesktopGenerationCoordinator.Accepted accept(FollowUp followUp) {
        DesktopGenerationCoordinator.AcceptOutcome outcome = coordinator.acceptForBranch(
                followUp.command, followUp.user, followUp.ai);
        assertTrue(outcome.isAccepted(), () -> "accept refused: " + outcome.refusal);
        return outcome.accepted;
    }

    private OperationStore.Operation operation(String commandId) {
        return operations.byCommandId(commandId).orElseThrow();
    }

    /** The injected clock, advanceable so the 250 ms half of the write window becomes reachable. */
    private MutableClock mutableClock() {
        return (MutableClock) clock;
    }

    // ------------------------------------------------------------------
    // Cases.
    // ------------------------------------------------------------------

    @Test
    void acceptForNewTopicCommitsFiveRowGroupsAndSubmitOpensOneCall() {
        NewTopic topic = newTopic();

        DesktopGenerationCoordinator.Accepted accepted = accept(topic);

        assertEquals(topic.topic.id, accepted.topicId);
        assertEquals(topic.root.id, accepted.rootBranchId);
        assertEquals(topic.command.id, accepted.commandId);
        assertFalse(accepted.replayed);
        assertTrue(topics.get(topic.topic.id).isPresent());
        assertTrue(messages.get(topic.user.id).isPresent());
        OperationStore.Operation stored = operation(topic.command.id);
        assertEquals(GenerationState.STAGED, stored.state);
        assertTrue(operations.activeForBranch(topic.root.id).isPresent(),
                "the first question's attempt is the branch's single in-flight operation");
        assertEquals(stored.id, messages.get(topic.ai.id).orElseThrow().activeOperationId(),
                "the answer position points at the running attempt");

        DesktopGenerationCoordinator.SubmitOutcome submitted = submitFirst(topic.command);
        assertTrue(submitted.started);
        assertEquals(accepted.operationId, submitted.operationId);
        assertEquals(1, gateway.openCount());
    }

    @Test
    void aRepeatedCommandIdReturnsTheSameOperationAndNeverOpensASecondCall() {
        NewTopic topic = newTopic();
        DesktopGenerationCoordinator.Accepted accepted = accept(topic);
        submitFirst(topic.command);
        gateway.last().accept();
        gateway.last().delta("", "最终回答");
        gateway.last().complete();

        DesktopGenerationCoordinator.AcceptOutcome replay = coordinator.acceptForNewTopic(
                topic.command, topic.topic, topic.root, topic.user, topic.ai);

        assertTrue(replay.isAccepted());
        assertTrue(replay.accepted.replayed, "the second accept must be recognised as a replay");
        assertEquals(accepted.operationId, replay.accepted.operationId,
                "the replay returns the operation identity of the first accept");
        assertFalse(operations.activeForBranch(topic.root.id).isPresent());
        assertEquals(2, messages.listForBranch(topic.root.id).size(),
                "a replay writes no second pair of positions");

        DesktopGenerationCoordinator.SubmitOutcome second = submitFirst(topic.command);
        assertTrue(second.replayed);
        assertEquals(accepted.operationId, second.operationId);
        assertEquals(1, gateway.openCount(), "a replay must never open a second model call");
    }

    @Test
    void aBranchWithAnInFlightAttemptRefusesANewCommand() {
        NewTopic topic = newTopic();
        accept(topic);
        submitFirst(topic.command);
        // Finish the first question's attempt so the branch is free for a follow-up.
        gateway.last().accept();
        gateway.last().delta("", "首问回答");
        gateway.last().complete();

        FollowUp first = followUp(topic.root);
        accept(first);

        FollowUp second = followUp(topic.root);
        DesktopGenerationCoordinator.AcceptOutcome busy = coordinator.acceptForBranch(
                second.command, second.user, second.ai);

        assertEquals(DesktopGenerationCoordinator.Refusal.BRANCH_BUSY, busy.refusal);
        assertFalse(messages.get(second.ai.id).isPresent(), "a refused accept writes nothing");
    }

    @Test
    void anAttemptLeftByARestartStillRefusesANewCommandOnItsBranch() {
        NewTopic topic = newTopic();
        accept(topic);
        // A fresh coordinator has an empty in-memory registry; the refusal must come from the rows.
        DesktopGenerationCoordinator restarted = new DesktopGenerationCoordinator(jdbc, topics,
                branches, messages, operations, new GenerationEventBus(), gateway, clock);

        FollowUp follow = followUp(topic.root);
        DesktopGenerationCoordinator.AcceptOutcome busy = restarted.acceptForBranch(
                follow.command, follow.user, follow.ai);

        assertEquals(DesktopGenerationCoordinator.Refusal.BRANCH_BUSY, busy.refusal,
                "the single-branch mutex is durable, not a property of the in-memory cache");
    }

    @Test
    void deltasFanOutToTheBusAndPersistOnlyOnceTheWindowIsDue() {
        NewTopic topic = newTopic();
        accept(topic);
        submitFirst(topic.command);
        Recorder recorder = new Recorder();
        bus.subscribe(operation(topic.command.id).id, recorder);

        FakeGateway.Call call = gateway.last();
        call.accept();
        call.delta("想", "");

        OperationStore.Operation small = operation(topic.command.id);
        assertEquals("", small.partialAnswer,
                "a one-character delta is below the 256-char window and must not be written yet");

        call.delta("", "答".repeat(300));

        OperationStore.Operation flushed = operation(topic.command.id);
        assertEquals(300, flushed.partialAnswer.length(),
                "300 accumulated characters exceed the window and must be persisted");
        assertEquals(GenerationState.STREAMING_ANSWER, flushed.state);
        assertTrue(recorder.saw(GenerationEventBus.Type.REASONING_DELTA));
        assertTrue(recorder.saw(GenerationEventBus.Type.ANSWER_DELTA));
    }

    @Test
    void theTimeHalfOfTheWriteWindowPersistsWithoutReachingTheCharThreshold() {
        NewTopic topic = newTopic();
        accept(topic);
        submitFirst(topic.command);

        FakeGateway.Call call = gateway.last();
        call.accept();
        call.delta("", "甲");
        assertEquals("", operation(topic.command.id).partialAnswer,
                "one character, no elapsed time: below both thresholds");

        mutableClock().advance(300);
        call.delta("", "乙");

        assertEquals("甲乙", operation(topic.command.id).partialAnswer,
                "300 ms past the window start exceeds the 250 ms threshold and must persist");
        assertEquals(GenerationState.STREAMING_ANSWER, operation(topic.command.id).state);
    }

    @Test
    void anAcceptThatFailsMidTransactionRollsBackEveryRow() {
        long now = T0;
        Topic topic = Topic.create(now);
        Branch root = Branch.root(topic.id, now);
        // Two positions on the same ordinal: inserting the second violates uq_messages_branch_ordinal,
        // so the accept fails after the topic and the root branch have already been written.
        Message user = Message.user(root.id, 0, "问题", "", now);
        Message ai = Message.ai(root.id, 0, now);
        GenerationCommand command = GenerationCommand.create(root.id, ai.id,
                PromptPurpose.LEARNING_ANSWER, request.policyVersion, GenerationModel.FLASH, false, now)
                .value();

        DesktopGenerationCoordinator.AcceptOutcome outcome =
                coordinator.acceptForNewTopic(command, topic, root, user, ai);

        assertFalse(outcome.isAccepted(), "a mid-transaction refusal must be reported, not committed");
        assertTrue(topics.get(topic.id).isEmpty(), "the topic row must be rolled back");
        assertTrue(branches.get(root.id).isEmpty(), "the root branch must be rolled back");
        assertTrue(messages.get(user.id).isEmpty(), "the learner position must be rolled back");
        assertTrue(messages.get(ai.id).isEmpty(), "the answer position must be rolled back");
        assertTrue(operations.byCommandId(command.id).isEmpty(), "the attempt must be rolled back");
        assertFalse(operations.activeForBranch(root.id).isPresent());
    }

    @Test
    void aNormalCompletionWithANonEmptyAnswerBecomesStableAndClosesTheStream() {
        NewTopic topic = newTopic();
        accept(topic);
        submitFirst(topic.command);
        Recorder recorder = new Recorder();
        String operationId = operation(topic.command.id).id;
        bus.subscribe(operationId, recorder);

        FakeGateway.Call call = gateway.last();
        call.accept();
        call.delta("想", "最终回答");
        call.complete();

        assertEquals(GenerationState.STABLE, operation(topic.command.id).state);
        assertEquals(1, revisions.countForMessage(topic.ai.id));
        assertEquals(1L, branches.get(topic.root.id).orElseThrow().latestStableSequence());
        assertEquals(revisions.findActiveForMessage(topic.ai.id).orElseThrow().id,
                messages.get(topic.ai.id).orElseThrow().activeRevisionId());
        assertTrue(recorder.saw(GenerationEventBus.Type.STABLE));
        assertTrue(bus.isClosed(operationId));
    }

    @Test
    void anEmptyAnswerEndsAsFailedAndWritesNoRevision() {
        assertCompletedWithoutAnswerBecomesFailed("", "no content at all");
    }

    @Test
    void aReasoningOnlyAnswerEndsAsFailedMirroringAndroid() {
        assertCompletedWithoutAnswerBecomesFailed("只有推理，没有回答", "reasoning-only stream");
    }

    private void assertCompletedWithoutAnswerBecomesFailed(String reasoning, String why) {
        NewTopic topic = newTopic();
        accept(topic);
        submitFirst(topic.command);
        String operationId = operation(topic.command.id).id;
        Recorder recorder = new Recorder();
        bus.subscribe(operationId, recorder);

        FakeGateway.Call call = gateway.last();
        call.accept();
        if (!reasoning.isEmpty()) {
            call.delta(reasoning, "");
        }
        call.complete();

        assertEquals(GenerationState.FAILED, operation(topic.command.id).state, why);
        assertEquals(0, revisions.countForMessage(topic.ai.id), why);
        assertNull(messages.get(topic.ai.id).orElseThrow().activeRevisionId());
        assertTrue(recorder.saw(GenerationEventBus.Type.FAILED));
        assertTrue(recorder.closed);
    }

    @Test
    void aProviderFailureIsFailedWithItsErrorCategory() {
        NewTopic topic = newTopic();
        accept(topic);
        submitFirst(topic.command);

        FakeGateway.Call call = gateway.last();
        call.accept();
        call.fail(AiGatewayError.Kind.PROVIDER_UNAVAILABLE);

        OperationStore.Operation failed = operation(topic.command.id);
        assertEquals(GenerationState.FAILED, failed.state);
        assertEquals("PROVIDER_UNAVAILABLE", failed.errorCode);
    }

    @Test
    void anInterruptedStreamKeepsItsPartialContent() {
        NewTopic topic = newTopic();
        accept(topic);
        submitFirst(topic.command);

        FakeGateway.Call call = gateway.last();
        call.accept();
        call.delta("", "半句");
        call.interrupt();

        OperationStore.Operation interrupted = operation(topic.command.id);
        assertEquals(GenerationState.INTERRUPTED, interrupted.state);
        assertEquals("半句", interrupted.partialAnswer, "an interrupted attempt keeps what it streamed");
        assertEquals(0, revisions.countForMessage(topic.ai.id));
    }

    @Test
    void cancelKeepsThePartialContentAndALateCompletionChangesNothing() {
        NewTopic topic = newTopic();
        accept(topic);
        submitFirst(topic.command);
        String operationId = operation(topic.command.id).id;

        FakeGateway.Call call = gateway.last();
        call.accept();
        call.delta("", "部分内容");
        call.cancelWithCallback(); // the fake echoes the cancel back synchronously

        assertTrue(coordinator.cancel(operationId));

        OperationStore.Operation canceled = operation(topic.command.id);
        assertEquals(GenerationState.CANCELED, canceled.state);
        assertEquals("部分内容", canceled.partialAnswer, "a cancel flushes what already arrived");

        call.complete();
        call.delta("", "晚了");

        OperationStore.Operation after = operation(topic.command.id);
        assertEquals(GenerationState.CANCELED, after.state, "a late completion must not rewrite CANCELED");
        assertEquals("部分内容", after.partialAnswer, "a late delta must not write back");
        assertEquals(0, revisions.countForMessage(topic.ai.id));
    }

    @Test
    void submitWithoutAnAcceptedAttemptIsRefused() {
        NewTopic topic = newTopic();

        DesktopGenerationCoordinator.SubmitOutcome outcome = coordinator.submit(topic.command,
                forCommand -> request);

        assertEquals(DesktopGenerationCoordinator.Refusal.STORAGE, outcome.refusal,
                "there is no accepted attempt to open a call for");
        assertEquals(0, gateway.openCount());
    }

    /** A UTC clock the test can advance, so the flush window's time half is exercised deterministically. */
    private static final class MutableClock extends Clock {

        private long millis;

        MutableClock(long millis) {
            this.millis = millis;
        }

        void advance(long deltaMillis) {
            millis += deltaMillis;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return Instant.ofEpochMilli(millis);
        }

        @Override
        public long millis() {
            return millis;
        }
    }

    /** Records the bus frames one subscription saw, so a case can assert order and closure. */
    private static final class Recorder implements GenerationEventBus.Listener {

        final List<GenerationEventBus.Event> events = new ArrayList<>();
        boolean closed;

        @Override
        public void onEvent(GenerationEventBus.Event event) {
            events.add(event);
        }

        @Override
        public void onClosed() {
            closed = true;
        }

        boolean saw(GenerationEventBus.Type type) {
            return events.stream().anyMatch(event -> event.type == type);
        }
    }

    /** A gateway the test drives by hand: it counts opens and exposes the last call's sink. */
    static final class FakeGateway implements DesktopGenerationGateway {

        private final List<Call> calls = new ArrayList<>();

        @Override
        public Stream open(GenerationModel model, DeepSeekRequest request, GenerationSink sink) {
            Call call = new Call(sink);
            calls.add(call);
            return call;
        }

        int openCount() {
            return calls.size();
        }

        Call last() {
            return calls.get(calls.size() - 1);
        }

        /** One hand-driven call. {@code cancelWithCallback} makes cancel echo a CANCELED frame back. */
        static final class Call implements DesktopGenerationGateway.Stream {

            private final GenerationSink sink;
            private boolean accepted;
            private boolean cancelled;
            private boolean fireCancelCallback;

            Call(GenerationSink sink) {
                this.sink = sink;
            }

            void accept() {
                accepted = sink.onAccepted(200);
            }

            void delta(String reasoning, String content) {
                if (accepted) {
                    sink.onDelta(reasoning, content);
                }
            }

            void complete() {
                sink.onTerminal(new StreamOutcome(StreamOutcome.Kind.COMPLETED, null, 200, 0, 0));
            }

            void fail(AiGatewayError.Kind kind) {
                sink.onTerminal(new StreamOutcome(StreamOutcome.Kind.FAILED,
                        AiGatewayError.of(kind, 500, "provider failed"), 500, 0, 0));
            }

            void interrupt() {
                sink.onTerminal(new StreamOutcome(StreamOutcome.Kind.INTERRUPTED, null, 0, 0, 0));
            }

            void cancelWithCallback() {
                fireCancelCallback = true;
            }

            @Override
            public void cancel() {
                cancelled = true;
                if (fireCancelCallback) {
                    sink.onTerminal(new StreamOutcome(StreamOutcome.Kind.CANCELED, null, 0, 0, 0));
                }
            }

            @Override
            public boolean isCancelled() {
                return cancelled;
            }
        }
    }
}
