package com.treelearn.app.generation;

import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;

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
import com.treelearn.data.db.DatabaseTransactionRunner;
import com.treelearn.data.db.TreeLearnDatabaseHelper;
import com.treelearn.data.db.dao.AnswerRevisionDao;
import com.treelearn.data.db.dao.BranchDao;
import com.treelearn.data.db.dao.GenerationOperationDao;
import com.treelearn.data.db.dao.GenerationOperationRow;
import com.treelearn.data.db.dao.GenerationStabilizer;
import com.treelearn.data.db.dao.MessageDao;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;

/**
 * The application-scoped generation coordinator (technical design 7, 8, 9.3).
 *
 * <p><strong>Authority.</strong> Every durable fact about a generation lives in SQLite. The
 * in-memory map below is a scheduling cache only: it answers "which call is in flight right now",
 * and {@link #recover()} rebuilds the durable answer from the rows, never from this map. Restart the
 * process and the map is empty while the rows still say what happened.
 *
 * <p><strong>What it decides, and what it deliberately does not.</strong> Ordering (FIFO above the
 * two-slot cap), the write window, dropping events that are no longer the active attempt's, and the
 * local-write-before-send rule of 9.2 are decided here. The one judgement it must <em>not</em> make is
 * "this answer is finished": 9.3 allows a revision only when the stream terminated normally
 * <em>and</em> the final answer is non-empty, and both halves belong elsewhere — the first to the
 * gateway's terminal outcome, the second to {@code GenerationOperation.stabilize} and to
 * {@link GenerationStabilizer#stabilize}. So {@link #complete} asks and reports what it is told; it
 * never inspects the answer to decide whether it is good enough.
 *
 * <p><strong>Nothing here carries conversation content.</strong> The deltas go straight from the
 * gateway callback into {@link StreamBatch} and then into the DAO's own append statement, so no
 * question, answer, reasoning text, prompt body or snapshot is ever held as a field, passed to an
 * observer or handed to {@link GenerationTelemetry}.
 *
 * <p><strong>Threading.</strong> The gateway calls back from its reader thread, one thread per
 * in-flight call, so every mutation is taken under one lock. Observers and the telemetry sink are
 * invoked <em>outside</em> that lock, from the calling thread, after the state they describe is
 * already committed.
 */
public final class GenerationCoordinator {

    /** Turns one immutable command into the request that will be sent (the T-020 assembly). */
    public interface Assembler {

        DeepSeekRequest assemble(GenerationCommand command);
    }

    /**
     * The rows an attempt needs that the coordinator cannot write itself, committed before its call is
     * opened. The first topic's five row groups are the only production user (8).
     */
    public interface LocalWrite {

        /** @return true when the rows are durable and the call may be opened */
        boolean persist(GenerationOperation operation, long now);
    }

    /** An observer of attempt state changes. Called outside the coordinator lock. */
    public interface Observer {

        void onChanged(String operationId, String branchId, GenerationState state);
    }

    /** Why a submission was refused. */
    public enum Refusal {
        /** The branch already has an attempt, so a second one would break the ordering of 7. */
        BRANCH_BUSY,
        /** The request could not be built, so no call may be made. */
        PROMPT_BUILD,
        /** The command and the assembled request disagree about the pinned policy version. */
        VERSION_MISMATCH,
        /** The attempt could not be persisted, so the request must not be sent. */
        STORAGE
    }

    /** The outcome of one submission. */
    public static final class SubmitOutcome {

        /** The attempt, or null when the submission was refused. */
        public final String operationId;
        /** True when the attempt was admitted and its call opened. */
        public final boolean started;
        /** The refusal category, or null when the submission was accepted. */
        public final Refusal refusal;
        /** The refusal detail for {@link Refusal#PROMPT_BUILD}, else null. */
        public final PromptBuildException.Reason promptReason;

        private SubmitOutcome(String operationId, boolean started, Refusal refusal,
                PromptBuildException.Reason promptReason) {
            this.operationId = operationId;
            this.started = started;
            this.refusal = refusal;
            this.promptReason = promptReason;
        }

        public boolean isAccepted() {
            return refusal == null;
        }

        public static SubmitOutcome accepted(String operationId, boolean started) {
            return new SubmitOutcome(operationId, started, null, null);
        }

        public static SubmitOutcome refused(Refusal refusal, PromptBuildException.Reason reason) {
            if (refusal == null) {
                throw new IllegalArgumentException("refusal must not be null");
            }
            return new SubmitOutcome(null, false, refusal, reason);
        }

        @Override
        public String toString() {
            return refusal == null
                    ? "SubmitOutcome[started=" + started + ", operation=" + operationId + "]"
                    : "SubmitOutcome[refused=" + refusal + "]";
        }
    }

    private final TreeLearnDatabaseHelper helper;
    private final DatabaseTransactionRunner runner;
    private final GenerationStabilizer stabilizer;
    private final GenerationGateway gateway;
    private final Clock clock;
    private final GenerationTelemetry telemetry;

    private final GenerationScheduler scheduler = new GenerationScheduler();
    private final LiveAttempts attempts = new LiveAttempts();
    private final List<Observer> observers = new ArrayList<Observer>();
    private final Object lock = new Object();

    public GenerationCoordinator(TreeLearnDatabaseHelper helper, DatabaseTransactionRunner runner,
            GenerationGateway gateway, Clock clock, GenerationTelemetry telemetry) {
        this.helper = require(helper, "helper");
        this.runner = require(runner, "runner");
        this.stabilizer = new GenerationStabilizer(runner);
        this.gateway = require(gateway, "gateway");
        this.clock = require(clock, "clock");
        this.telemetry = require(telemetry, "telemetry");
    }

    /**
     * Submits an attempt for an existing position: the operation row is written before the request is
     * sent, which is what 9.2 asks for ("有主题在请求前持久化 operation").
     */
    public SubmitOutcome submit(GenerationCommand command, Assembler assembler) {
        return submit(command, assembler, null);
    }

    /**
     * Submits an attempt whose topic, branch or answer position do not exist yet.
     *
     * <p>{@code localWrite} creates them together with the operation row, in the caller's own
     * transaction, and it runs <em>before</em> the call is opened — the same order the follow-up path
     * has always kept. This is a deliberate departure from 8's "the write waits for the provider's 2xx"
     * (user decision 2026-10-16, after a 401 on a first topic left a branch with no rows and a screen
     * that could only read it as unreadable): a refusal now leaves a visible question with a failed
     * answer position that can be retried in place, instead of a dead end. The cost is that a failed
     * first send leaves an empty topic behind, which is what the home list and the recovery scan are
     * for.
     */
    public SubmitOutcome submit(GenerationCommand command, Assembler assembler, LocalWrite localWrite) {
        require(command, "command");
        require(assembler, "assembler");
        DeepSeekRequest request;
        try {
            request = assembler.assemble(command);
        } catch (PromptBuildException refused) {
            telemetry.record(GenerationTelemetry.Event.PROMPT_REFUSED, null, command.branchId, 0,
                    null);
            return SubmitOutcome.refused(Refusal.PROMPT_BUILD, refused.reason);
        }
        // The command pins the version and the assembler copies it onto the request. They are
        // produced by different modules, so a disagreement means one of them moved and the row would
        // otherwise record a version that was not the one sent.
        if (request == null || !command.promptPolicyVersion.equals(request.policyVersion)) {
            telemetry.record(GenerationTelemetry.Event.PROMPT_REFUSED, null, command.branchId, 0,
                    null);
            return SubmitOutcome.refused(Refusal.VERSION_MISMATCH, null);
        }
        List<Runnable> after = new ArrayList<Runnable>();
        SubmitOutcome outcome;
        synchronized (lock) {
            outcome = admit(command, request, localWrite, after);
        }
        run(after);
        return outcome;
    }

    private SubmitOutcome admit(GenerationCommand command, DeepSeekRequest request,
            LocalWrite localWrite, List<Runnable> after) {
        GenerationScheduler.Admission admission = scheduler.admit(command);
        if (admission == GenerationScheduler.Admission.BRANCH_BUSY) {
            return SubmitOutcome.refused(Refusal.BRANCH_BUSY, null);
        }
        GenerationOperation operation = GenerationOperation.start(command, clock.millis());
        LiveAttempt attempt = new LiveAttempt(command, operation, request, localWrite);
        attempts.open(attempt);
        // Nothing is sent before the rows are durable, so every terminal outcome below has a row to
        // land in. A local persistence failure therefore must not spend a remote slot: the slot is
        // given back and no call is opened, so the caller keeps its input and its position for a
        // manual retry. Handing the slot back is also what starts the oldest waiting command, so this
        // goes through the same release path every other outcome uses.
        if (!persistLocal(attempt)) {
            release(attempt, after);
            telemetry.record(GenerationTelemetry.Event.STORAGE_REFUSED, null, command.branchId, 0,
                    null);
            return SubmitOutcome.refused(Refusal.STORAGE, null);
        }
        telemetry.record(GenerationTelemetry.Event.START, operation.id, command.branchId, 0, null);
        after.add(changed(operation.id, command.branchId, operation.state()));
        if (admission == GenerationScheduler.Admission.STARTED) {
            attempt.stream = gateway.open(command.model, request, new Sink(attempt));
        }
        return SubmitOutcome.accepted(operation.id,
                admission == GenerationScheduler.Admission.STARTED);
    }

    /**
     * Commits what this attempt needs locally, in one decision shared by both entry points.
     *
     * <p>{@code false} means "no call may be opened": either the caller's own write refused, or the
     * store refused the attempt row. The two are reported the same way because the caller's obligation
     * is identical — give the input back and offer a retry.
     */
    private boolean persistLocal(LiveAttempt attempt) {
        try {
            if (attempt.localWrite != null) {
                attempt.persisted = attempt.localWrite.persist(attempt.operation, clock.millis());
                return attempt.persisted;
            }
            GenerationOperationDao.insert(helper.getWritableDatabase(), attempt.operation);
            attempt.persisted = true;
            return true;
        } catch (SQLiteException refusedByStore) {
            return false;
        }
    }

    /**
     * Cancels one attempt: the call is aborted first, then the outcome is recorded.
     *
     * @return true when the attempt was still known to the coordinator
     */
    public boolean cancel(String operationId) {
        require(operationId, "operationId");
        List<Runnable> after = new ArrayList<Runnable>();
        boolean cancelled;
        synchronized (lock) {
            LiveAttempt attempt = attempts.byOperation(operationId);
            cancelled = attempt != null
                    && settle(attempt, GenerationState.CANCELED, null, null, after);
        }
        run(after);
        return cancelled;
    }

    /**
     * Reaps what a dead process left behind (7): every leftover in-flight attempt is moved to its
     * recovery target, in one transaction, and nothing is resent.
     *
     * @return how many rows were reaped
     */
    public int recover() {
        final long now = clock.millis();
        final List<Runnable> after = new ArrayList<Runnable>();
        int reaped = runner.run(helper.getWritableDatabase(), new DatabaseTransactionRunner.Work<Integer>() {
            @Override
            public Integer execute(SQLiteDatabase database) {
                int count = 0;
                for (GenerationOperationRow row : GenerationOperationDao.findRecoverable(database)) {
                    final GenerationState target = RecoveryScan.terminalFor(row.state);
                    GenerationOperationDao.markTerminal(database, row.id, target, null, now);
                    final String id = row.id;
                    final String branchId = row.branchId;
                    after.add(new Runnable() {
                        @Override
                        public void run() {
                            telemetry.record(RecoveryScan.eventFor(target), id, branchId, 0, null);
                        }
                    });
                    count++;
                }
                return Integer.valueOf(count);
            }
        });
        run(after);
        return reaped;
    }

    public void addObserver(Observer observer) {
        synchronized (lock) {
            observers.add(require(observer, "observer"));
        }
    }

    public void removeObserver(Observer observer) {
        synchronized (lock) {
            observers.remove(observer);
        }
    }

    /** @return how many calls are open right now */
    public int activeCount() {
        synchronized (lock) {
            return scheduler.activeCount();
        }
    }

    /** @return how many attempts are admitted but not started */
    public int waitingCount() {
        synchronized (lock) {
            return scheduler.waitingCount();
        }
    }

    // ------------------------------------------------------------------
    // Gateway callbacks. Every one of them runs on the call's reader thread.
    // ------------------------------------------------------------------

    private final class Sink implements GenerationSink {

        private final LiveAttempt attempt;

        Sink(LiveAttempt attempt) {
            this.attempt = attempt;
        }

        @Override
        public boolean onAccepted(int httpStatus) {
            List<Runnable> after = new ArrayList<Runnable>();
            boolean durable;
            synchronized (lock) {
                if (!attempts.isCurrent(attempt)) {
                    return false;
                }
                // STAGED -> VALIDATING -> SUBMITTING, through the domain's own graph rather than a
                // second copy of it.
                if (attempt.operation.advanceTo(GenerationState.VALIDATING).isFailure()
                        || attempt.operation.advanceTo(GenerationState.SUBMITTING).isFailure()) {
                    return false;
                }
                durable = persistAccepted(attempt);
                if (!durable) {
                    settle(attempt, GenerationState.FAILED, null, null, after);
                } else {
                    telemetry.record(GenerationTelemetry.Event.ACCEPTED, attempt.operation.id,
                            attempt.command.branchId, 0, null);
                    after.add(changed(attempt.operation.id, attempt.command.branchId,
                            attempt.operation.state()));
                }
            }
            run(after);
            return durable;
        }

        @Override
        public void onDelta(String reasoningChunk, String contentChunk) {
            List<Runnable> after = new ArrayList<Runnable>();
            synchronized (lock) {
                if (!attempts.isCurrent(attempt)) {
                    stale(attempt, after);
                    run(after);
                    return;
                }
                // The domain decides whether a delta of this channel may still land; a refusal here
                // is a late event, not a crash.
                if (attempt.operation.appendReasoning(reasoningChunk).isFailure()
                        || attempt.operation.appendAnswer(contentChunk).isFailure()) {
                    stale(attempt, after);
                    run(after);
                    return;
                }
                long now = clock.millis();
                attempt.batch.append(reasoningChunk, contentChunk, now);
                if (!attempt.streaming) {
                    attempt.streaming = true;
                    telemetry.record(GenerationTelemetry.Event.STREAMING, attempt.operation.id,
                            attempt.command.branchId, 0, null);
                    after.add(changed(attempt.operation.id, attempt.command.branchId,
                            attempt.operation.state()));
                }
                if (attempt.batch.isDue(now)) {
                    flush(attempt, now);
                    after.add(changed(attempt.operation.id, attempt.command.branchId,
                            attempt.operation.state()));
                }
            }
            run(after);
        }

        @Override
        public void onTerminal(StreamOutcome outcome) {
            List<Runnable> after = new ArrayList<Runnable>();
            synchronized (lock) {
                if (!attempts.isCurrent(attempt)) {
                    stale(attempt, after);
                    run(after);
                    return;
                }
                long now = clock.millis();
                flush(attempt, now);
                switch (outcome.kind) {
                    case COMPLETED:
                        complete(attempt, after);
                        break;
                    case INTERRUPTED:
                        settle(attempt, GenerationState.INTERRUPTED, null, null, after);
                        break;
                    case CANCELED:
                        settle(attempt, GenerationState.CANCELED, null, null, after);
                        break;
                    case FAILED:
                    default:
                        settle(attempt, GenerationState.FAILED, null,
                                outcome.error == null ? null : outcome.error.kind, after);
                        break;
                }
            }
            run(after);
        }
    }

    /**
     * Records the provider's 2xx on the row the attempt was already written to.
     *
     * <p>A row that refuses it has been deleted or frozen underneath us, and reading a body into
     * nowhere is exactly what the returned false prevents.
     *
     * @return whether the progress landed, which is what decides if the body is read
     */
    private boolean persistAccepted(LiveAttempt attempt) {
        try {
            GenerationOperationDao.recordProgress(helper.getWritableDatabase(),
                    attempt.operation.id, "", "", attempt.operation.state(), clock.millis());
            return true;
        } catch (SQLiteException refusedByStore) {
            return false;
        }
    }

    /**
     * The normally finished case: ask the store to freeze the attempt and report what it said.
     *
     * <p>Nothing here looks at the answer. An attempt that ended normally with nothing in it is
     * refused by the domain's non-empty rule, and the refusal is reported as a failure of the attempt
     * rather than turned into a revision (9.3).
     */
    private void complete(LiveAttempt attempt, List<Runnable> after) {
        long now = clock.millis();
        if (!attempt.persisted) {
            settle(attempt, GenerationState.FAILED, null, null, after);
            return;
        }
        SQLiteDatabase database = helper.getWritableDatabase();
        int revisionNo = AnswerRevisionDao.nextRevisionNo(database, attempt.operation.messageId);
        DomainResult<AnswerRevision> stabilized;
        try {
            stabilized = stabilizer.stabilize(database, attempt.operation.id, revisionNo, now);
        } catch (SQLiteException refusedByStore) {
            settle(attempt, GenerationState.FAILED, null, null, after);
            return;
        } catch (IllegalStateException refusedByStore) {
            settle(attempt, GenerationState.FAILED, null, null, after);
            return;
        }
        if (stabilized.isFailure()) {
            settle(attempt, GenerationState.FAILED, null, null, after);
            return;
        }
        settle(attempt, null, stabilized.value(), null, after);
        telemetry.record(GenerationTelemetry.Event.STABLE, attempt.operation.id,
                attempt.command.branchId, attempt.operation.partialAnswer().length(), null);
    }

    /**
     * Writes one outcome and gives the slot back. Every terminal path goes through here, so the
     * "exactly one outcome per attempt" rule has one home.
     */
    private boolean settle(LiveAttempt attempt, GenerationState terminal, AnswerRevision revision,
            AiGatewayError.Kind errorKind, List<Runnable> after) {
        if (!attempts.isCurrent(attempt)) {
            return false;
        }
        if (attempt.stream != null) {
            attempt.stream.cancel();
        }
        if (terminal != null) {
            if (attempt.operation.advanceTo(terminal).isFailure()) {
                return false;
            }
            if (attempt.persisted) {
                try {
                    GenerationOperationDao.markTerminal(helper.getWritableDatabase(),
                            attempt.operation.id, terminal,
                            errorKind == null ? null : errorKind.name(), clock.millis());
                } catch (SQLiteException refusedByStore) {
                    // The row is gone (its branch or message was deleted) or already frozen. Either
                    // way there is nothing to write back, and the attempt is still released below.
                    stale(attempt, after);
                } catch (IllegalStateException refusedByStore) {
                    stale(attempt, after);
                }
            }
        }
        settlePosition(attempt, revision, clock.millis(), after);
        GenerationState visibleState = revision == null ? terminal : GenerationState.STABLE;
        if (visibleState != null) {
            after.add(changed(attempt.operation.id, attempt.command.branchId, visibleState));
        }
        // complete() is the one caller that passes no terminal state, because it reports the
        // stabilization it just performed rather than an outcome handed to this method. Switching on
        // that null would throw, so this guard is what lets a successful attempt finish at all.
        if (terminal != null) {
            telemetry.record(eventFor(terminal), attempt.operation.id, attempt.command.branchId,
                    attempt.operation.partialAnswer().length(), errorKind);
        }
        release(attempt, after);
        return true;
    }

    /**
     * Terminal bookkeeping on the position and its branch (5.1, 7).
     *
     * <p>A separate transaction from the stabilization, and that is a recorded deviation: the
     * stabilizer owns its boundary, so the pair cannot be one transaction without a nested commit.
     * The window between them is a durable revision that the position does not display yet.
     */
    private void settlePosition(LiveAttempt attempt, AnswerRevision revision, long now,
            List<Runnable> after) {
        final String messageId = attempt.operation.messageId;
        final String branchId = attempt.operation.branchId;
        final String revisionId = revision == null ? null : revision.id;
        try {
            runner.run(helper.getWritableDatabase(), new DatabaseTransactionRunner.Work<Void>() {
                @Override
                public Void execute(SQLiteDatabase database) {
                    Message position = MessageDao.findById(database, messageId);
                    if (position == null) {
                        return null;
                    }
                    position.clearActiveOperation();
                    if (revisionId != null) {
                        position.activateRevision(revisionId);
                    }
                    MessageDao.updateActiveReferences(database, position);
                    if (revisionId != null) {
                        Branch branch = BranchDao.findById(database, branchId);
                        if (branch != null) {
                            branch.onStableAnswer(now);
                            BranchDao.update(database, branch);
                        }
                    }
                    return null;
                }
            });
        } catch (SQLiteException refusedByStore) {
            telemetry.record(GenerationTelemetry.Event.FAILED, attempt.operation.id, branchId, 0,
                    null);
        } catch (IllegalStateException refusedByStore) {
            telemetry.record(GenerationTelemetry.Event.FAILED, attempt.operation.id, branchId, 0,
                    null);
        }
    }

    private void flush(LiveAttempt attempt, long now) {
        if (attempt.batch.isEmpty() || !attempt.persisted) {
            return;
        }
        String reasoning = attempt.batch.takeReasoning();
        String answer = attempt.batch.takeAnswer();
        attempt.batch.onFlushed(now);
        try {
            GenerationOperationDao.recordProgress(helper.getWritableDatabase(),
                    attempt.operation.id, reasoning, answer, attempt.operation.state(), now);
        } catch (SQLiteException refusedByStore) {
            staleNow(attempt);
        } catch (IllegalStateException refusedByStore) {
            staleNow(attempt);
        }
    }

    private void staleNow(LiveAttempt attempt) {
        telemetry.record(GenerationTelemetry.Event.STALE_DISCARDED, attempt.operation.id,
                attempt.command.branchId, 0, null);
    }

    private void release(LiveAttempt attempt, List<Runnable> after) {
        attempts.close(attempt.command.id);
        GenerationCommand next =
                scheduler.finish(attempt.command.branchId, attempt.command.id);
        if (next == null) {
            return;
        }
        LiveAttempt following = attempts.find(next.id);
        following.stream = gateway.open(following.command.model, following.request,
                new Sink(following));
        after.add(changed(following.operation.id, following.command.branchId,
                following.operation.state()));
    }

    private void stale(LiveAttempt attempt, List<Runnable> after) {
        final String id = attempt.operation.id;
        final String branchId = attempt.command.branchId;
        after.add(new Runnable() {
            @Override
            public void run() {
                telemetry.record(GenerationTelemetry.Event.STALE_DISCARDED, id, branchId, 0, null);
            }
        });
    }

    private Runnable changed(final String operationId, final String branchId,
            final GenerationState state) {
        return new Runnable() {
            @Override
            public void run() {
                for (Observer observer : observers.toArray(new Observer[0])) {
                    observer.onChanged(operationId, branchId, state);
                }
            }
        };
    }

    private static GenerationTelemetry.Event eventFor(GenerationState terminal) {
        switch (terminal) {
            case STABLE:
                return GenerationTelemetry.Event.STABLE;
            case INTERRUPTED:
                return GenerationTelemetry.Event.INTERRUPTED;
            case CANCELED:
                return GenerationTelemetry.Event.CANCELED;
            default:
                return GenerationTelemetry.Event.FAILED;
        }
    }

    private void run(List<Runnable> after) {
        for (Runnable action : after) {
            action.run();
        }
    }

    private static <T> T require(T value, String field) {
        if (value == null) {
            throw new IllegalArgumentException(field + " must not be null");
        }
        return value;
    }

}
