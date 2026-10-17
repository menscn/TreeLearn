package com.treelearn.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.treelearn.ai.gateway.AiGatewayError;
import com.treelearn.ai.gateway.GenerationSink;
import com.treelearn.ai.gateway.StreamOutcome;
import com.treelearn.ai.prompt.DeepSeekMessage;
import com.treelearn.ai.prompt.DeepSeekRequest;
import com.treelearn.ai.prompt.DeepSeekPromptRegistry;
import com.treelearn.ai.prompt.DeepSeekRequestAssembler;
import com.treelearn.ai.prompt.PromptBudget;
import com.treelearn.ai.prompt.PromptBuildException;
import com.treelearn.app.generation.GenerationCoordinator;
import com.treelearn.app.generation.GenerationGateway;
import com.treelearn.app.generation.GenerationTelemetry;
import com.treelearn.app.generation.StreamBatch;
import com.treelearn.core.domain.AnswerRevision;
import com.treelearn.core.domain.Branch;
import com.treelearn.core.domain.DomainResult;
import com.treelearn.core.domain.GenerationCommand;
import com.treelearn.core.domain.GenerationModel;
import com.treelearn.core.domain.GenerationOperation;
import com.treelearn.core.domain.GenerationState;
import com.treelearn.core.domain.Message;
import com.treelearn.core.domain.PromptPurpose;
import com.treelearn.core.domain.Topic;
import com.treelearn.data.db.DatabaseTransactionRunner;
import com.treelearn.data.db.TreeLearnDatabaseHelper;
import com.treelearn.data.db.dao.AnswerRevisionDao;
import com.treelearn.data.db.dao.BranchDao;
import com.treelearn.data.db.dao.GenerationOperationDao;
import com.treelearn.data.db.dao.GenerationOperationRow;
import com.treelearn.data.db.dao.MessageDao;
import com.treelearn.data.db.dao.TopicDao;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * T-008 acceptance evidence for the application-scoped generation coordinator, written against real
 * Android framework SQLite (API 36) with a scripted gateway.
 *
 * <p><strong>Not executed in the 2026-09-21 round.</strong> The project owner's binding instruction
 * for this round forbids using the emulator, so this class is kept as a compiling source only: it is
 * verified by {@code :app:assembleDebugAndroidTest} (and a forced recompile with
 * {@code --rerun-tasks --no-build-cache}) and every criterion below is recorded as
 * {@code deferred — emulator not used this round (user instruction 2026-09-21)} in the task file and
 * in {@code android/README.md}, never as a pass. The guarantees that do not need a device are carried
 * by the plain-JVM suites ({@code GenerationSchedulerTest}, {@code StreamBatchTest},
 * {@code LiveAttemptsTest}, {@code RecoveryScanTest}).
 *
 * <p>The gateway is a double, not a mock framework: {@link FakeGateway} records the call and hands
 * back a handle that the test drives by hand, so "two calls are open" is a fact about the
 * coordinator rather than a race the test hopes to win. The double refuses to deliver a delta before
 * the accepted callback returned true, and the attempt row is already durable by the time the first
 * call is opened - which is what the 2026-10-16 rewrite of 8's first-topic rule asks both paths to
 * keep.
 *
 * <p>Three properties get the most attention, because a plausible-looking implementation gets them
 * wrong: the cap and the FIFO order (7), the batching window (7), and the disposal of events that
 * arrive after their attempt stopped being the active one.
 *
 * <p>All content here is synthetic and no credential appears: the double never receives one.
 */
@RunWith(AndroidJUnit4.class)
public final class GenerationCoordinatorInstrumentedTest {

    private static final long T0 = 1_756_000_000_000L;
    /**
     * Derived, never pinned: {@link DeepSeekRequestAssembler} copies the registry's version onto the
     * request and {@code submit} refuses a command whose pinned version disagrees with it, so a literal
     * here goes stale the moment the registry bumps - and this suite is compiled but not run by
     * {@code check}, so the staleness has no other witness.
     */
    private static final String POLICY_VERSION = DeepSeekPromptRegistry
            .spec(PromptPurpose.LEARNING_ANSWER).policyVersion;
    private static final int BUDGET_CHARS = 8_000;
    private static final int MIN_ANSWER_CHARS = 512;

    /** A well-formed UUID no row carries, so "the store refused it" is tested and not "malformed". */
    private static final String MISSING_MESSAGE = "99999999-9999-4999-8999-999999999999";

    private TreeLearnDatabaseHelper helper;
    private DatabaseTransactionRunner runner;
    private FakeGateway gateway;
    private FakeClock clock;
    private RecordingTelemetry telemetry;
    private GenerationCoordinator coordinator;

    @Before
    public void setUp() {
        helper = new TreeLearnDatabaseHelper(context());
        context().deleteDatabase(TreeLearnDatabaseHelper.DATABASE_NAME);
        runner = new DatabaseTransactionRunner();
        gateway = new FakeGateway();
        clock = new FakeClock(T0);
        telemetry = new RecordingTelemetry();
        coordinator = new GenerationCoordinator(helper, runner, gateway, clock, telemetry);
    }

    @After
    public void tearDown() {
        if (helper != null) {
            helper.close();
        }
        context().deleteDatabase(TreeLearnDatabaseHelper.DATABASE_NAME);
    }

    private static android.content.Context context() {
        return InstrumentationRegistry.getInstrumentation().getTargetContext();
    }

    // ------------------------------------------------------------------
    // 1. Scheduling: the cap, the order, and the refusal.
    // ------------------------------------------------------------------

    @Test
    public void theAttemptRowIsDurableBeforeTheCallOpens() {
        Position position = seedPosition();

        GenerationCoordinator.SubmitOutcome outcome =
                coordinator.submit(commandFor(position), asking("why is the sky blue?"));

        assertTrue(outcome.isAccepted());
        assertTrue("an attempt for a free branch starts immediately", outcome.started);
        assertEquals("exactly one call was opened", 1, gateway.openCount());
        GenerationOperationRow stored = row(outcome.operationId);
        assertNotNull("9.2: the attempt must already be durable when the call opens", stored);
        assertEquals("the row carries the version the command pinned", POLICY_VERSION,
                stored.promptPolicyVersion);
        assertEquals(GenerationState.STAGED, stored.state);
        assertEquals(position.branch.id, stored.branchId);
        assertEquals(position.message.id, stored.messageId);
        assertTrue("a started attempt holds one of the two slots", coordinator.activeCount() == 1);
        assertEquals(0, coordinator.waitingCount());
    }

    @Test
    public void theThirdBranchWaitsAndStartsOnlyWhenASlotIsGivenBack() {
        SQLiteDatabase database = helper.getWritableDatabase();
        Position one = seedPosition(database);
        Position two = seedPosition(database);
        Position three = seedPosition(database);

        GenerationCoordinator.SubmitOutcome first = coordinator.submit(commandFor(one), asking("q one"));
        GenerationCoordinator.SubmitOutcome second = coordinator.submit(commandFor(two), asking("q two"));
        GenerationCoordinator.SubmitOutcome third =
                coordinator.submit(commandFor(three), asking("q three"));

        assertEquals("7: the application runs at most two generations", 2, gateway.openCount());
        assertEquals(2, coordinator.activeCount());
        assertEquals(1, coordinator.waitingCount());
        assertTrue(third.isAccepted());
        assertFalse("the third branch waits, it is not dropped and it is not started", third.started);
        GenerationOperationRow waiting = row(third.operationId);
        assertNotNull("the waiting attempt is durable too", waiting);
        assertEquals("7: more requests stay at STAGED", GenerationState.STAGED, waiting.state);

        gateway.handle(0).accept();
        gateway.handle(0).delta("", "the first answer");
        gateway.handle(0).terminal(StreamOutcome.Kind.COMPLETED);

        assertEquals("the freed slot goes to the oldest waiting command", 3, gateway.openCount());
        assertEquals(0, coordinator.waitingCount());
        assertEquals(2, coordinator.activeCount());
        assertEquals(GenerationState.STABLE, row(first.operationId).state);
        assertNotNull("and the second attempt is untouched by the first one finishing",
                row(second.operationId));
    }

    @Test
    public void aSecondAttemptForABusyBranchIsRefusedAndWritesNoRow() {
        Position position = seedPosition();
        coordinator.submit(commandFor(position), asking("q one"));
        int written = countRows("generation_operations", "");

        GenerationCoordinator.SubmitOutcome refused =
                coordinator.submit(commandFor(position), asking("q two"));

        assertEquals(GenerationCoordinator.Refusal.BRANCH_BUSY, refused.refusal);
        assertEquals("a refused submission must not leave a row behind",
                written, countRows("generation_operations", ""));
        assertEquals("and it must not open a call", 1, gateway.openCount());
    }

    // ------------------------------------------------------------------
    // 2. The write window and the events that arrive too late.
    // ------------------------------------------------------------------

    @Test
    public void deltasArePersistedOnlyWhenTheWriteWindowIsReached() {
        Position position = seedPosition();
        GenerationCoordinator.SubmitOutcome outcome =
                coordinator.submit(commandFor(position), asking("q"));
        Handle handle = gateway.handle(0);
        assertTrue(handle.accept());
        assertEquals("accepting records the submission, not any content",
                GenerationState.SUBMITTING, row(outcome.operationId).state);

        handle.delta("", "0123456789");
        assertEquals("ten characters are below the 256 character window", "",
                row(outcome.operationId).partialAnswer);

        clock.advance(250);
        handle.delta("", "X");

        assertEquals("the next delta arrives after 250 ms, so the whole batch lands",
                "0123456789X", row(outcome.operationId).partialAnswer);
        assertEquals(GenerationState.STREAMING_ANSWER, row(outcome.operationId).state);
    }

    @Test
    public void cancellingDiscardsLateEventsAndRecordsThemRedacted() {
        Position position = seedPosition();
        GenerationCoordinator.SubmitOutcome outcome =
                coordinator.submit(commandFor(position), asking("q"));
        Handle handle = gateway.handle(0);
        handle.accept();

        assertTrue("the coordinator knows the attempt", coordinator.cancel(outcome.operationId));
        assertTrue("cancelling aborts the call before anything else", handle.isCancelled());
        assertEquals(GenerationState.CANCELED, row(outcome.operationId).state);
        assertEquals(0, coordinator.activeCount());

        handle.delta("", "a delta that arrived after the cancellation");
        handle.terminal(StreamOutcome.Kind.COMPLETED);

        assertEquals("a late delta must not write back over a terminal row",
                GenerationState.CANCELED, row(outcome.operationId).state);
        assertEquals("", row(outcome.operationId).partialAnswer);
        assertEquals("a cancelled attempt must never become history",
                0, countRows("answer_revisions", ""));
        assertEquals("both late events are recorded, with no content on them",
                2, telemetry.count(GenerationTelemetry.Event.STALE_DISCARDED));
    }

    @Test
    public void aFailedStreamKeepsThePartialContentAndCreatesNoRevision() {
        Position position = seedPosition();
        GenerationCoordinator.SubmitOutcome outcome =
                coordinator.submit(commandFor(position), asking("q"));
        Handle handle = gateway.handle(0);
        handle.accept();
        handle.delta("partial reasoning", "partial answer");
        handle.terminal(StreamOutcome.Kind.FAILED, AiGatewayError.forHttpStatus(429));

        GenerationOperationRow stored = row(outcome.operationId);
        assertEquals(GenerationState.FAILED, stored.state);
        assertEquals("the partial content stays readable so the attempt can be retried in place",
                "partial answer", stored.partialAnswer);
        assertEquals("the provider failure is recorded by category, never by its text",
                "RATE_LIMITED", stored.errorCode);
        assertEquals(0, countRows("answer_revisions", ""));
        assertEquals(0, coordinator.activeCount());
    }

    @Test
    public void aNormallyCompletedStreamWithoutAnAnswerIsRefusedByTheDomainNotByTheCoordinator() {
        Position position = seedPosition();
        GenerationCoordinator.SubmitOutcome outcome =
                coordinator.submit(commandFor(position), asking("q"));
        Handle handle = gateway.handle(0);
        handle.accept();

        handle.terminal(StreamOutcome.Kind.COMPLETED);

        assertEquals("9.3: a stable revision needs a non-empty answer, so this attempt ends failed",
                GenerationState.FAILED, row(outcome.operationId).state);
        assertEquals(0, countRows("answer_revisions", ""));
    }

    @Test
    public void aNormallyCompletedStreamStabilizesAndCopiesThePinnedVersionVerbatim() {
        Position position = seedPosition();
        GenerationCoordinator.SubmitOutcome outcome =
                coordinator.submit(commandFor(position), asking("q"));
        Handle handle = gateway.handle(0);
        handle.accept();
        handle.delta("because the air scatters light", "the answer");
        handle.terminal(StreamOutcome.Kind.COMPLETED);

        GenerationOperationRow stored = row(outcome.operationId);
        assertEquals(GenerationState.STABLE, stored.state);
        assertEquals(POLICY_VERSION, stored.promptPolicyVersion);

        List<AnswerRevision> revisions =
                AnswerRevisionDao.findByMessage(helper.getWritableDatabase(), position.message.id);
        assertEquals(1, revisions.size());
        AnswerRevision revision = revisions.get(0);
        assertEquals("the version is copied from the row, not re-derived from a live registry",
                POLICY_VERSION, revision.promptPolicyVersion);
        assertEquals("the answer is frozen verbatim",
                "the answer", revision.answerContent);
        assertEquals("because the air scatters light", revision.reasoningContent);
        assertEquals(1, revision.revisionNo);

        Message displayed = MessageDao.findById(helper.getWritableDatabase(), position.message.id);
        assertEquals("the position now displays the frozen revision",
                revision.id, displayed.activeRevisionId());
        assertNull("and no attempt is running at that position any more",
                displayed.activeOperationId());
        assertEquals(0, coordinator.activeCount());
        assertEquals("a stabilized attempt is reported as stable, and not also as failed",
                0, telemetry.count(GenerationTelemetry.Event.FAILED));
    }

    // ------------------------------------------------------------------
    // 3. A refused build or a refused write never spends a remote slot (8, 9.2).
    // ------------------------------------------------------------------

    @Test
    public void aPromptBuildRefusalNeverReachesTheGatewayAndLeavesTheSlotFree() {
        Position position = seedPosition();

        GenerationCoordinator.SubmitOutcome refused =
                coordinator.submit(commandFor(position), asking("   "));

        assertEquals(GenerationCoordinator.Refusal.PROMPT_BUILD, refused.refusal);
        assertEquals("the refusal category comes from :ai, not from a copy of it",
                PromptBuildException.Reason.BLANK_CURRENT_INPUT, refused.promptReason);
        assertEquals("no call may be made for a request that was never built",
                0, gateway.openCount());
        assertEquals(0, coordinator.activeCount());
        assertEquals("nothing was admitted, so nothing was queued either",
                0, coordinator.waitingCount());
        assertEquals("and nothing was written", 0, countRows("generation_operations", ""));
        assertEquals(1, telemetry.count(GenerationTelemetry.Event.PROMPT_REFUSED));

        assertTrue("the slot is still free afterwards",
                coordinator.submit(commandFor(position), asking("q")).started);
        assertEquals(1, gateway.openCount());
    }

    @Test
    public void aCommandAndARequestThatDisagreeAboutTheVersionAreRefused() {
        Position position = seedPosition();

        GenerationCoordinator.SubmitOutcome refused = coordinator.submit(commandFor(position),
                command -> DeepSeekRequestAssembler.assemble(PromptPurpose.BRANCH_TITLE, false,
                        Collections.<DeepSeekMessage>emptyList(), "{\"kind\":\"title\"}",
                        new PromptBudget(BUDGET_CHARS, MIN_ANSWER_CHARS)));

        assertEquals(GenerationCoordinator.Refusal.VERSION_MISMATCH, refused.refusal);
        assertEquals("a request that would record a version other than the pinned one is not sent",
                0, gateway.openCount());
        assertEquals(0, countRows("generation_operations", ""));
        assertEquals(0, coordinator.activeCount());
    }

    @Test
    public void aStorageRefusalLeavesNoAttemptAndFreesTheBranch() {
        Position position = seedPosition();
        DomainResult<GenerationCommand> orphan = GenerationCommand.create(position.branch.id,
                MISSING_MESSAGE, PromptPurpose.LEARNING_ANSWER, POLICY_VERSION,
                GenerationModel.FLASH, true, T0);
        assertTrue("the fixture command is well formed; only its message does not exist: " + orphan,
                orphan.isOk());

        GenerationCoordinator.SubmitOutcome refused =
                coordinator.submit(orphan.value(), asking("q"));

        assertEquals("8: a local persistence failure is reported, not swallowed",
                GenerationCoordinator.Refusal.STORAGE, refused.refusal);
        assertEquals("and no call is made", 0, gateway.openCount());
        assertEquals(0, coordinator.activeCount());
        assertEquals("the slot and the queue are left as they were",
                0, coordinator.waitingCount());
        assertEquals(0, countRows("generation_operations", ""));
        assertTrue("the branch is free again, so the learner can retry in place",
                coordinator.submit(commandFor(position), asking("q")).started);
    }

    @Test
    public void theFirstTopicWriteLandsBeforeTheCallIsOpened() {
        Position position = seedPosition();
        final List<String> localWrites = new ArrayList<String>();

        GenerationCoordinator.SubmitOutcome outcome = coordinator.submit(
                commandFor(position), asking("the first question"),
                (operation, now) -> {
                    localWrites.add(operation.promptPolicyVersion);
                    GenerationOperationDao.insert(helper.getWritableDatabase(), operation);
                    return true;
                });

        // The first topic used to wait for the provider's 2xx before writing anything, which left a
        // 401 with no rows at all and a screen that could only say the conversation was unreadable
        // (user decision 2026-10-16: 首话题与追问对称). One entry now, before the call.
        assertEquals("the write ran once, before anyone was asked", 1, localWrites.size());
        assertEquals(POLICY_VERSION, localWrites.get(0));
        assertTrue(outcome.isAccepted());
        assertEquals("the call opened beside an already-durable attempt", 1, gateway.openCount());
        GenerationOperationRow stored = row(outcome.operationId);
        assertNotNull("9.2 applies to the first topic too", stored);
        assertEquals("and it carries the version the command pinned", POLICY_VERSION,
                stored.promptPolicyVersion);
        assertEquals(GenerationState.STAGED, stored.state);

        Handle handle = gateway.handle(0);
        assertTrue(handle.accept());
        assertEquals("the accepted callback writes onto the row the local write created",
                GenerationState.SUBMITTING, row(outcome.operationId).state);

        handle.delta("thinking", "");
        clock.advance(StreamBatch.MAX_MILLIS);
        handle.delta("", "the answer");
        // The window is 250 ms / 256 characters, so a delta is only observable once it is crossed:
        // what this pins is that the row the local write created is the one it lands in.
        assertEquals("the body was read into the row that was already there",
                "the answer", row(outcome.operationId).partialAnswer);
    }

    @Test
    public void aRefusedLocalWriteOpensNoCallAndFreesTheSlot() {
        Position position = seedPosition();
        final List<String> localWrites = new ArrayList<String>();

        GenerationCoordinator.SubmitOutcome outcome = coordinator.submit(
                commandFor(position), asking("the first question"), (operation, now) -> {
                    localWrites.add(operation.id);
                    return false;
                });

        assertEquals("the refusal is the caller's, and it names the local write", 1,
                localWrites.size());
        assertFalse(outcome.isAccepted());
        assertEquals(GenerationCoordinator.Refusal.STORAGE, outcome.refusal);
        assertEquals("a local refusal must not spend a remote call", 0, gateway.openCount());
        assertEquals("and the half-written topic leaves nothing behind",
                0, countRows("generation_operations", ""));
        assertEquals(0, coordinator.activeCount());
        assertEquals("the slot is free for the retry the learner is offered",
                0, coordinator.waitingCount());
        assertEquals(1, telemetry.count(GenerationTelemetry.Event.STORAGE_REFUSED));
        assertEquals("nothing was sent, so no stream ever failed", 0,
                telemetry.count(GenerationTelemetry.Event.FAILED));
        assertTrue("a retry of the same position starts normally",
                coordinator.submit(commandFor(position), asking("the question again")).started);
    }

    // ------------------------------------------------------------------
    // 4. Recovery, and an observer that let go of the attempt.
    // ------------------------------------------------------------------

    @Test
    public void startupRecoveryReapsLeftoverAttemptsAtomicallyAndKeepsPartialContent() {
        SQLiteDatabase database = helper.getWritableDatabase();
        Position interrupted = seedPosition(database);
        Position staged = seedPosition(database);
        GenerationOperation streaming =
                streamingOperation(interrupted, "kept reasoning", "kept answer");
        GenerationOperationDao.insert(database, streaming);
        GenerationOperation neverStarted = GenerationOperation.start(commandFor(staged), T0);
        GenerationOperationDao.insert(database, neverStarted);

        assertEquals(2, coordinator.recover());

        GenerationOperationRow reaped = row(streaming.id);
        assertEquals("7: everything that reached the provider becomes INTERRUPTED",
                GenerationState.INTERRUPTED, reaped.state);
        assertEquals("kept reasoning", reaped.partialReasoning);
        assertEquals("kept answer", reaped.partialAnswer);
        assertEquals("an attempt that never reached the provider is CANCELED instead",
                GenerationState.CANCELED, row(neverStarted.id).state);
        assertEquals("recovery must not freeze anything into history",
                0, countRows("answer_revisions", ""));
        assertEquals("and it must not resend", 0, gateway.openCount());
        assertEquals("running it again finds nothing, so it is idempotent", 0, coordinator.recover());
        assertEquals(1, telemetry.count(GenerationTelemetry.Event.INTERRUPTED));
        assertEquals(1, telemetry.count(GenerationTelemetry.Event.CANCELED));
    }

    @Test
    public void detachingAnObserverDoesNotCancelTheAttempt() {
        final List<GenerationState> seen = new ArrayList<GenerationState>();
        GenerationCoordinator.Observer observer =
                (operationId, branchId, state) -> seen.add(state);
        coordinator.addObserver(observer);

        Position position = seedPosition();
        GenerationCoordinator.SubmitOutcome outcome =
                coordinator.submit(commandFor(position), asking("q"));
        Handle handle = gateway.handle(0);
        handle.accept();
        handle.delta("", "the answer");
        assertTrue("the attached observer hears about the running attempt",
                seen.contains(GenerationState.STREAMING_ANSWER));

        coordinator.removeObserver(observer);
        int heardBefore = seen.size();
        clock.advance(250);
        handle.delta("", " continues");

        assertEquals("a detached observer stops hearing", heardBefore, seen.size());
        assertEquals("but the attempt keeps running: leaving a page is not a cancellation",
                1, coordinator.activeCount());
        assertEquals("and its content still reaches the store",
                "the answer continues", row(outcome.operationId).partialAnswer);

        handle.terminal(StreamOutcome.Kind.COMPLETED);
        assertEquals(GenerationState.STABLE, row(outcome.operationId).state);
        assertEquals("the finished attempt is not announced to an observer that let go",
                heardBefore, seen.size());
    }

    // ------------------------------------------------------------------
    // Fixtures and doubles.
    // ------------------------------------------------------------------

    private static GenerationCoordinator.Assembler asking(final String question) {
        return command -> DeepSeekRequestAssembler.assemble(command.purpose,
                command.thinkingEnabled, Collections.<DeepSeekMessage>emptyList(), question,
                new PromptBudget(BUDGET_CHARS, MIN_ANSWER_CHARS));
    }

    private static GenerationCommand commandFor(Position position) {
        DomainResult<GenerationCommand> created = GenerationCommand.create(position.branch.id,
                position.message.id, PromptPurpose.LEARNING_ANSWER, POLICY_VERSION,
                GenerationModel.FLASH, true, T0);
        assertTrue("fixture command must be creatable: " + created, created.isOk());
        return created.value();
    }

    private static GenerationOperation streamingOperation(Position position, String reasoning,
            String answer) {
        GenerationOperation operation = GenerationOperation.start(commandFor(position), T0);
        operation.advanceTo(GenerationState.VALIDATING);
        operation.advanceTo(GenerationState.SUBMITTING);
        operation.appendReasoning(reasoning);
        operation.appendAnswer(answer);
        return operation;
    }

    private Position seedPosition() {
        return seedPosition(helper.getWritableDatabase());
    }

    private static Position seedPosition(SQLiteDatabase database) {
        Topic topic = Topic.create(T0);
        TopicDao.insert(database, topic);
        Branch branch = Branch.root(topic.id, T0);
        BranchDao.insert(database, branch);
        Message position = Message.ai(branch.id, 0, T0);
        MessageDao.insert(database, position);
        return new Position(topic, branch, position);
    }

    private GenerationOperationRow row(String operationId) {
        return GenerationOperationDao.findById(helper.getWritableDatabase(), operationId);
    }

    private int countRows(String table, String where) {
        String sql = "SELECT COUNT(*) FROM " + table
                + (where.isEmpty() ? "" : " WHERE " + where);
        try (Cursor cursor = helper.getWritableDatabase().rawQuery(sql, null)) {
            cursor.moveToFirst();
            return cursor.getInt(0);
        }
    }

    private static final class Position {

        final Topic topic;
        final Branch branch;
        final Message message;

        Position(Topic topic, Branch branch, Message message) {
            this.topic = topic;
            this.branch = branch;
            this.message = message;
        }
    }

    /** A scripted gateway: it opens nothing, it only remembers, and the test drives the call. */
    private static final class FakeGateway implements GenerationGateway {

        private final List<Handle> handles = new ArrayList<Handle>();

        @Override
        public GenerationGateway.Stream open(GenerationModel model, DeepSeekRequest request,
                GenerationSink sink) {
            Handle handle = new Handle(request, sink);
            handles.add(handle);
            return handle;
        }

        int openCount() {
            return handles.size();
        }

        Handle handle(int index) {
            assertTrue("no call was opened at index " + index, handles.size() > index);
            return handles.get(index);
        }
    }

    /** One scripted call, delivering exactly what the real gateway would, in the same order. */
    private static final class Handle implements GenerationGateway.Stream {

        final DeepSeekRequest request;
        private final GenerationSink sink;
        private boolean accepted;
        private boolean cancelled;

        Handle(DeepSeekRequest request, GenerationSink sink) {
            this.request = request;
            this.sink = sink;
        }

        boolean accept() {
            accepted = sink.onAccepted(200);
            return accepted;
        }

        void delta(String reasoningChunk, String contentChunk) {
            assertTrue("a delta may only arrive after the accepted write returned true",
                    accepted);
            sink.onDelta(reasoningChunk, contentChunk);
        }

        void terminal(StreamOutcome.Kind kind) {
            terminal(kind, null);
        }

        void terminal(StreamOutcome.Kind kind, AiGatewayError error) {
            sink.onTerminal(new StreamOutcome(kind, error, 200, 0, 0));
        }

        @Override
        public void cancel() {
            cancelled = true;
        }

        @Override
        public boolean isCancelled() {
            return cancelled;
        }
    }

    /** A clock the test moves by hand, so the write window is asserted rather than waited for. */
    private static final class FakeClock extends Clock {

        private long millis;

        FakeClock(long millis) {
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

    /** Records the redacted events, and nothing else: the interface cannot carry content. */
    private static final class RecordingTelemetry implements GenerationTelemetry {

        private final List<Event> events = new ArrayList<Event>();

        @Override
        public void record(Event event, String operationId, String branchId, int chars,
                AiGatewayError.Kind errorKind) {
            events.add(event);
        }

        int count(Event event) {
            int total = 0;
            for (Event recorded : events) {
                if (recorded == event) {
                    total++;
                }
            }
            return total;
        }
    }
}
