/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  android.database.sqlite.SQLiteDatabase
 *  android.database.sqlite.SQLiteException
 *  com.treelearn.ai.gateway.AiGatewayError$Kind
 *  com.treelearn.ai.gateway.GenerationSink
 *  com.treelearn.ai.gateway.StreamOutcome
 *  com.treelearn.ai.prompt.DeepSeekRequest
 *  com.treelearn.ai.prompt.PromptBuildException
 *  com.treelearn.ai.prompt.PromptBuildException$Reason
 *  com.treelearn.core.domain.AnswerRevision
 *  com.treelearn.core.domain.Branch
 *  com.treelearn.core.domain.DomainResult
 *  com.treelearn.core.domain.GenerationCommand
 *  com.treelearn.core.domain.GenerationOperation
 *  com.treelearn.core.domain.GenerationState
 *  com.treelearn.core.domain.Message
 *  com.treelearn.data.db.DatabaseTransactionRunner
 *  com.treelearn.data.db.DatabaseTransactionRunner$Work
 *  com.treelearn.data.db.TreeLearnDatabaseHelper
 *  com.treelearn.data.db.dao.AnswerRevisionDao
 *  com.treelearn.data.db.dao.BranchDao
 *  com.treelearn.data.db.dao.GenerationOperationDao
 *  com.treelearn.data.db.dao.GenerationOperationRow
 *  com.treelearn.data.db.dao.GenerationStabilizer
 *  com.treelearn.data.db.dao.MessageDao
 */
package com.treelearn.app.generation;

import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import com.treelearn.ai.gateway.AiGatewayError;
import com.treelearn.ai.gateway.GenerationSink;
import com.treelearn.ai.gateway.StreamOutcome;
import com.treelearn.ai.prompt.DeepSeekRequest;
import com.treelearn.ai.prompt.PromptBuildException;
import com.treelearn.app.generation.GenerationGateway;
import com.treelearn.app.generation.GenerationScheduler;
import com.treelearn.app.generation.GenerationTelemetry;
import com.treelearn.app.generation.LiveAttempt;
import com.treelearn.app.generation.LiveAttempts;
import com.treelearn.app.generation.RecoveryScan;
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

public final class GenerationCoordinator {
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

    public GenerationCoordinator(TreeLearnDatabaseHelper helper, DatabaseTransactionRunner runner, GenerationGateway gateway, Clock clock, GenerationTelemetry telemetry) {
        this.helper = GenerationCoordinator.require(helper, "helper");
        this.runner = GenerationCoordinator.require(runner, "runner");
        this.stabilizer = new GenerationStabilizer(runner);
        this.gateway = GenerationCoordinator.require(gateway, "gateway");
        this.clock = GenerationCoordinator.require(clock, "clock");
        this.telemetry = GenerationCoordinator.require(telemetry, "telemetry");
    }

    public SubmitOutcome submit(GenerationCommand command, Assembler assembler) {
        return this.submit(command, assembler, null);
    }

    public SubmitOutcome submitFirstTopic(GenerationCommand command, Assembler assembler, AcceptedWrite acceptedWrite) {
        return this.submit(command, assembler, GenerationCoordinator.require(acceptedWrite, "acceptedWrite"));
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    private SubmitOutcome submit(GenerationCommand command, Assembler assembler, AcceptedWrite acceptedWrite) {
        SubmitOutcome outcome;
        DeepSeekRequest request;
        GenerationCoordinator.require(command, "command");
        GenerationCoordinator.require(assembler, "assembler");
        try {
            request = assembler.assemble(command);
        }
        catch (PromptBuildException refused) {
            this.telemetry.record(GenerationTelemetry.Event.PROMPT_REFUSED, null, command.branchId, 0, null);
            return SubmitOutcome.refused(Refusal.PROMPT_BUILD, refused.reason);
        }
        if (request == null || !command.promptPolicyVersion.equals(request.policyVersion)) {
            this.telemetry.record(GenerationTelemetry.Event.PROMPT_REFUSED, null, command.branchId, 0, null);
            return SubmitOutcome.refused(Refusal.VERSION_MISMATCH, null);
        }
        ArrayList<Runnable> after = new ArrayList<Runnable>();
        Object object = this.lock;
        synchronized (object) {
            outcome = this.admit(command, request, acceptedWrite, after);
        }
        this.run(after);
        return outcome;
    }

    private SubmitOutcome admit(GenerationCommand command, DeepSeekRequest request, AcceptedWrite acceptedWrite, List<Runnable> after) {
        GenerationScheduler.Admission admission = this.scheduler.admit(command);
        if (admission == GenerationScheduler.Admission.BRANCH_BUSY) {
            return SubmitOutcome.refused(Refusal.BRANCH_BUSY, null);
        }
        GenerationOperation operation = GenerationOperation.start((GenerationCommand)command, (long)this.clock.millis());
        LiveAttempt attempt = new LiveAttempt(command, operation, request, acceptedWrite);
        this.attempts.open(attempt);
        if (acceptedWrite == null) {
            try {
                GenerationOperationDao.insert((SQLiteDatabase)this.helper.getWritableDatabase(), (GenerationOperation)operation);
                attempt.persisted = true;
            }
            catch (SQLiteException refusedByStore) {
                this.release(attempt, after);
                this.telemetry.record(GenerationTelemetry.Event.STORAGE_REFUSED, null, command.branchId, 0, null);
                return SubmitOutcome.refused(Refusal.STORAGE, null);
            }
        }
        this.telemetry.record(GenerationTelemetry.Event.START, operation.id, command.branchId, 0, null);
        after.add(this.changed(operation.id, command.branchId, operation.state()));
        if (admission == GenerationScheduler.Admission.STARTED) {
            attempt.stream = this.gateway.open(command.model, request, new Sink(attempt));
        }
        return SubmitOutcome.accepted(operation.id, admission == GenerationScheduler.Admission.STARTED);
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public boolean cancel(String operationId) {
        boolean cancelled;
        GenerationCoordinator.require(operationId, "operationId");
        ArrayList<Runnable> after = new ArrayList<Runnable>();
        Object object = this.lock;
        synchronized (object) {
            LiveAttempt attempt = this.attempts.byOperation(operationId);
            cancelled = attempt != null && this.settle(attempt, GenerationState.CANCELED, null, null, after);
        }
        this.run(after);
        return cancelled;
    }

    public int recover() {
        final long now = this.clock.millis();
        final ArrayList<Runnable> after = new ArrayList<Runnable>();
        int reaped = (Integer)this.runner.run(this.helper.getWritableDatabase(), (DatabaseTransactionRunner.Work)new DatabaseTransactionRunner.Work<Integer>(){

            public Integer execute(SQLiteDatabase database) {
                int count = 0;
                for (GenerationOperationRow row : GenerationOperationDao.findRecoverable((SQLiteDatabase)database)) {
                    final GenerationState target = RecoveryScan.terminalFor(row.state);
                    GenerationOperationDao.markTerminal((SQLiteDatabase)database, (String)row.id, (GenerationState)target, null, (long)now);
                    final String id2 = row.id;
                    final String branchId = row.branchId;
                    after.add(new Runnable(){

                        @Override
                        public void run() {
                            GenerationCoordinator.this.telemetry.record(RecoveryScan.eventFor(target), id2, branchId, 0, null);
                        }
                    });
                    ++count;
                }
                return count;
            }
        });
        this.run(after);
        return reaped;
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public void addObserver(Observer observer) {
        Object object = this.lock;
        synchronized (object) {
            this.observers.add(GenerationCoordinator.require(observer, "observer"));
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public void removeObserver(Observer observer) {
        Object object = this.lock;
        synchronized (object) {
            this.observers.remove(observer);
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public int activeCount() {
        Object object = this.lock;
        synchronized (object) {
            return this.scheduler.activeCount();
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public int waitingCount() {
        Object object = this.lock;
        synchronized (object) {
            return this.scheduler.waitingCount();
        }
    }

    private boolean persistAccepted(LiveAttempt attempt) {
        if (attempt.acceptedWrite != null) {
            attempt.persisted = attempt.acceptedWrite.persist(attempt.operation, this.clock.millis());
            return attempt.persisted;
        }
        try {
            GenerationOperationDao.recordProgress((SQLiteDatabase)this.helper.getWritableDatabase(), (String)attempt.operation.id, (String)"", (String)"", (GenerationState)attempt.operation.state(), (long)this.clock.millis());
            return true;
        }
        catch (SQLiteException refusedByStore) {
            return false;
        }
    }

    private void complete(LiveAttempt attempt, List<Runnable> after) {
        DomainResult stabilized;
        long now = this.clock.millis();
        if (!attempt.persisted) {
            this.settle(attempt, GenerationState.FAILED, null, null, after);
            return;
        }
        SQLiteDatabase database = this.helper.getWritableDatabase();
        int revisionNo = AnswerRevisionDao.nextRevisionNo((SQLiteDatabase)database, (String)attempt.operation.messageId);
        try {
            stabilized = this.stabilizer.stabilize(database, attempt.operation.id, revisionNo, now);
        }
        catch (SQLiteException refusedByStore) {
            this.settle(attempt, GenerationState.FAILED, null, null, after);
            return;
        }
        catch (IllegalStateException refusedByStore) {
            this.settle(attempt, GenerationState.FAILED, null, null, after);
            return;
        }
        if (stabilized.isFailure()) {
            this.settle(attempt, GenerationState.FAILED, null, null, after);
            return;
        }
        this.settle(attempt, null, (AnswerRevision)stabilized.value(), null, after);
        this.telemetry.record(GenerationTelemetry.Event.STABLE, attempt.operation.id, attempt.command.branchId, attempt.operation.partialAnswer().length(), null);
    }

    private boolean settle(LiveAttempt attempt, GenerationState terminal, AnswerRevision revision, AiGatewayError.Kind errorKind, List<Runnable> after) {
        GenerationState visibleState;
        if (!this.attempts.isCurrent(attempt)) {
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
                    GenerationOperationDao.markTerminal((SQLiteDatabase)this.helper.getWritableDatabase(), (String)attempt.operation.id, (GenerationState)terminal, (String)(errorKind == null ? null : errorKind.name()), (long)this.clock.millis());
                }
                catch (SQLiteException refusedByStore) {
                    this.stale(attempt, after);
                }
                catch (IllegalStateException refusedByStore) {
                    this.stale(attempt, after);
                }
            }
        }
        this.settlePosition(attempt, revision, this.clock.millis(), after);
        GenerationState generationState = visibleState = revision == null ? terminal : GenerationState.STABLE;
        if (visibleState != null) {
            after.add(this.changed(attempt.operation.id, attempt.command.branchId, visibleState));
        }
        if (terminal != null) {
            this.telemetry.record(GenerationCoordinator.eventFor(terminal), attempt.operation.id, attempt.command.branchId, attempt.operation.partialAnswer().length(), errorKind);
        }
        this.release(attempt, after);
        return true;
    }

    private void settlePosition(LiveAttempt attempt, AnswerRevision revision, final long now, List<Runnable> after) {
        final String messageId = attempt.operation.messageId;
        final String branchId = attempt.operation.branchId;
        final String revisionId = revision == null ? null : revision.id;
        try {
            this.runner.run(this.helper.getWritableDatabase(), (DatabaseTransactionRunner.Work)new DatabaseTransactionRunner.Work<Void>(){

                public Void execute(SQLiteDatabase database) {
                    Branch branch;
                    Message position = MessageDao.findById((SQLiteDatabase)database, (String)messageId);
                    if (position == null) {
                        return null;
                    }
                    position.clearActiveOperation();
                    if (revisionId != null) {
                        position.activateRevision(revisionId);
                    }
                    MessageDao.updateActiveReferences((SQLiteDatabase)database, (Message)position);
                    if (revisionId != null && (branch = BranchDao.findById((SQLiteDatabase)database, (String)branchId)) != null) {
                        branch.onStableAnswer(now);
                        BranchDao.update((SQLiteDatabase)database, (Branch)branch);
                    }
                    return null;
                }
            });
        }
        catch (SQLiteException refusedByStore) {
            this.telemetry.record(GenerationTelemetry.Event.FAILED, attempt.operation.id, branchId, 0, null);
        }
        catch (IllegalStateException refusedByStore) {
            this.telemetry.record(GenerationTelemetry.Event.FAILED, attempt.operation.id, branchId, 0, null);
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
            GenerationOperationDao.recordProgress((SQLiteDatabase)this.helper.getWritableDatabase(), (String)attempt.operation.id, (String)reasoning, (String)answer, (GenerationState)attempt.operation.state(), (long)now);
        }
        catch (SQLiteException refusedByStore) {
            this.staleNow(attempt);
        }
        catch (IllegalStateException refusedByStore) {
            this.staleNow(attempt);
        }
    }

    private void staleNow(LiveAttempt attempt) {
        this.telemetry.record(GenerationTelemetry.Event.STALE_DISCARDED, attempt.operation.id, attempt.command.branchId, 0, null);
    }

    private void release(LiveAttempt attempt, List<Runnable> after) {
        this.attempts.close(attempt.command.id);
        GenerationCommand next = this.scheduler.finish(attempt.command.branchId, attempt.command.id);
        if (next == null) {
            return;
        }
        LiveAttempt following = this.attempts.find(next.id);
        following.stream = this.gateway.open(following.command.model, following.request, new Sink(following));
        after.add(this.changed(following.operation.id, following.command.branchId, following.operation.state()));
    }

    private void stale(LiveAttempt attempt, List<Runnable> after) {
        final String id2 = attempt.operation.id;
        final String branchId = attempt.command.branchId;
        after.add(new Runnable(){

            @Override
            public void run() {
                GenerationCoordinator.this.telemetry.record(GenerationTelemetry.Event.STALE_DISCARDED, id2, branchId, 0, null);
            }
        });
    }

    private Runnable changed(final String operationId, final String branchId, final GenerationState state) {
        return new Runnable(){

            @Override
            public void run() {
                for (Observer observer : GenerationCoordinator.this.observers.toArray(new Observer[0])) {
                    observer.onChanged(operationId, branchId, state);
                }
            }
        };
    }

    private static GenerationTelemetry.Event eventFor(GenerationState terminal) {
        switch (terminal) {
            case STABLE: {
                return GenerationTelemetry.Event.STABLE;
            }
            case INTERRUPTED: {
                return GenerationTelemetry.Event.INTERRUPTED;
            }
            case CANCELED: {
                return GenerationTelemetry.Event.CANCELED;
            }
        }
        return GenerationTelemetry.Event.FAILED;
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

    public static interface Assembler {
        public DeepSeekRequest assemble(GenerationCommand var1);
    }

    public static interface AcceptedWrite {
        public boolean persist(GenerationOperation var1, long var2);
    }

    public static final class SubmitOutcome {
        public final String operationId;
        public final boolean started;
        public final Refusal refusal;
        public final PromptBuildException.Reason promptReason;

        private SubmitOutcome(String operationId, boolean started, Refusal refusal, PromptBuildException.Reason promptReason) {
            this.operationId = operationId;
            this.started = started;
            this.refusal = refusal;
            this.promptReason = promptReason;
        }

        public boolean isAccepted() {
            return this.refusal == null;
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

        public String toString() {
            return this.refusal == null ? "SubmitOutcome[started=" + this.started + ", operation=" + this.operationId + "]" : "SubmitOutcome[refused=" + (Object)((Object)this.refusal) + "]";
        }
    }

    public static enum Refusal {
        BRANCH_BUSY,
        PROMPT_BUILD,
        VERSION_MISMATCH,
        STORAGE;

    }

    private final class Sink
    implements GenerationSink {
        private final LiveAttempt attempt;

        Sink(LiveAttempt attempt) {
            this.attempt = attempt;
        }

        /*
         * WARNING - Removed try catching itself - possible behaviour change.
         */
        public boolean onAccepted(int httpStatus) {
            boolean durable;
            ArrayList<Runnable> after = new ArrayList<Runnable>();
            Object object = GenerationCoordinator.this.lock;
            synchronized (object) {
                if (!GenerationCoordinator.this.attempts.isCurrent(this.attempt)) {
                    return false;
                }
                if (this.attempt.operation.advanceTo(GenerationState.VALIDATING).isFailure() || this.attempt.operation.advanceTo(GenerationState.SUBMITTING).isFailure()) {
                    return false;
                }
                durable = GenerationCoordinator.this.persistAccepted(this.attempt);
                if (!durable) {
                    GenerationCoordinator.this.settle(this.attempt, GenerationState.FAILED, null, null, after);
                } else {
                    GenerationCoordinator.this.telemetry.record(GenerationTelemetry.Event.ACCEPTED, this.attempt.operation.id, this.attempt.command.branchId, 0, null);
                    after.add(GenerationCoordinator.this.changed(this.attempt.operation.id, this.attempt.command.branchId, this.attempt.operation.state()));
                }
            }
            GenerationCoordinator.this.run(after);
            return durable;
        }

        /*
         * WARNING - Removed try catching itself - possible behaviour change.
         */
        public void onDelta(String reasoningChunk, String contentChunk) {
            ArrayList<Runnable> after = new ArrayList<Runnable>();
            Object object = GenerationCoordinator.this.lock;
            synchronized (object) {
                if (!GenerationCoordinator.this.attempts.isCurrent(this.attempt)) {
                    GenerationCoordinator.this.stale(this.attempt, after);
                    GenerationCoordinator.this.run(after);
                    return;
                }
                if (this.attempt.operation.appendReasoning(reasoningChunk).isFailure() || this.attempt.operation.appendAnswer(contentChunk).isFailure()) {
                    GenerationCoordinator.this.stale(this.attempt, after);
                    GenerationCoordinator.this.run(after);
                    return;
                }
                long now = GenerationCoordinator.this.clock.millis();
                this.attempt.batch.append(reasoningChunk, contentChunk, now);
                if (!this.attempt.streaming) {
                    this.attempt.streaming = true;
                    GenerationCoordinator.this.telemetry.record(GenerationTelemetry.Event.STREAMING, this.attempt.operation.id, this.attempt.command.branchId, 0, null);
                    after.add(GenerationCoordinator.this.changed(this.attempt.operation.id, this.attempt.command.branchId, this.attempt.operation.state()));
                }
                if (this.attempt.batch.isDue(now)) {
                    GenerationCoordinator.this.flush(this.attempt, now);
                    after.add(GenerationCoordinator.this.changed(this.attempt.operation.id, this.attempt.command.branchId, this.attempt.operation.state()));
                }
            }
            GenerationCoordinator.this.run(after);
        }

        /*
         * WARNING - Removed try catching itself - possible behaviour change.
         */
        public void onTerminal(StreamOutcome outcome) {
            ArrayList<Runnable> after = new ArrayList<Runnable>();
            Object object = GenerationCoordinator.this.lock;
            synchronized (object) {
                if (!GenerationCoordinator.this.attempts.isCurrent(this.attempt)) {
                    GenerationCoordinator.this.stale(this.attempt, after);
                    GenerationCoordinator.this.run(after);
                    return;
                }
                long now = GenerationCoordinator.this.clock.millis();
                GenerationCoordinator.this.flush(this.attempt, now);
                switch (outcome.kind) {
                    case COMPLETED: {
                        GenerationCoordinator.this.complete(this.attempt, after);
                        break;
                    }
                    case INTERRUPTED: {
                        GenerationCoordinator.this.settle(this.attempt, GenerationState.INTERRUPTED, null, null, after);
                        break;
                    }
                    case CANCELED: {
                        GenerationCoordinator.this.settle(this.attempt, GenerationState.CANCELED, null, null, after);
                        break;
                    }
                    default: {
                        GenerationCoordinator.this.settle(this.attempt, GenerationState.FAILED, null, outcome.error == null ? null : outcome.error.kind, after);
                    }
                }
            }
            GenerationCoordinator.this.run(after);
        }
    }

    public static interface Observer {
        public void onChanged(String var1, String var2, GenerationState var3);
    }
}

