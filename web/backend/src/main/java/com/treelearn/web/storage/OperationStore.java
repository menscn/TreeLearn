package com.treelearn.web.storage;

import static com.treelearn.web.storage.DesktopSchema.GenerationOperations.BRANCH_ID;
import static com.treelearn.web.storage.DesktopSchema.GenerationOperations.COMMAND_ID;
import static com.treelearn.web.storage.DesktopSchema.GenerationOperations.CREATED_AT;
import static com.treelearn.web.storage.DesktopSchema.GenerationOperations.ERROR_CODE;
import static com.treelearn.web.storage.DesktopSchema.GenerationOperations.ID;
import static com.treelearn.web.storage.DesktopSchema.GenerationOperations.MESSAGE_ID;
import static com.treelearn.web.storage.DesktopSchema.GenerationOperations.MODEL;
import static com.treelearn.web.storage.DesktopSchema.GenerationOperations.PARTIAL_ANSWER;
import static com.treelearn.web.storage.DesktopSchema.GenerationOperations.PARTIAL_REASONING;
import static com.treelearn.web.storage.DesktopSchema.GenerationOperations.PROMPT_POLICY_VERSION;
import static com.treelearn.web.storage.DesktopSchema.GenerationOperations.PROMPT_PURPOSE;
import static com.treelearn.web.storage.DesktopSchema.GenerationOperations.STATE;
import static com.treelearn.web.storage.DesktopSchema.GenerationOperations.TABLE;
import static com.treelearn.web.storage.DesktopSchema.GenerationOperations.THINKING_ENABLED;
import static com.treelearn.web.storage.DesktopSchema.GenerationOperations.UPDATED_AT;

import com.treelearn.core.domain.DomainResult;
import com.treelearn.core.domain.GenerationModel;
import com.treelearn.core.domain.GenerationOperation;
import com.treelearn.core.domain.GenerationState;
import com.treelearn.core.domain.PromptPurpose;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.List;
import java.util.Optional;

/**
 * Row access for {@code generation_operations} (task T-2026-10-10-011).
 *
 * <p>Semantic reference — the Android {@code com.treelearn.data.db.dao} implementation it mirrors:
 * <ul>
 *   <li>{@code GenerationOperationDao.insert} → {@link #insert(GenerationOperation)}: the attempt
 *       entity decides its initial state and any content it already holds; {@code updated_at} starts
 *       equal to {@code created_at}.</li>
 *   <li>{@code GenerationOperationDao.recordProgress} → {@link #updateProgress}: deltas are appended
 *       <em>in SQLite</em> ({@code col = col || ?}) so a batch lands as one visible change with no
 *       read-modify-write window, and the state moves in the same statement.</li>
 *   <li>{@code GenerationOperationDao.markTerminal} → {@link #markTerminal}: the row-level writer
 *       recovery needs, because a dead process cannot rebuild a {@link GenerationOperation} (there is
 *       no rehydration entry point, on purpose). The partial buffers are left exactly as they were.</li>
 *   <li>{@code GenerationOperationDao.findById} / {@code findActiveByBranch} → {@link #byId} /
 *       {@link #activeForBranch}.</li>
 * </ul>
 *
 * <p><strong>The state set is the domain's, never copied here.</strong> {@link #activeForBranch} and
 * {@link #listActive} ask {@link GenerationState#isActive()} for the in-flight set, and
 * {@link #markTerminal} asks {@link GenerationState#transitionTo} whether the move is legal, so a state
 * added to the lifecycle is in-flight everywhere or nowhere and no second state machine exists.
 *
 * <p><strong>Terminal write protection is decided here first and by the trigger second.</strong> A row
 * that already ended refuses both {@link #updateProgress} and {@link #markTerminal} through
 * {@link GenerationState#isTerminal()} before any statement runs, and T-009's
 * {@code trg_generation_operations_terminal_immutable} backstops the same rule at the store, so a write
 * that somehow reached SQL still fails loudly.
 *
 * <p><strong>{@code STABLE} is written only through the Phase 4 stabilization path, not here.</strong>
 * {@link #markTerminal} refuses that target outright: reaching it means creating the immutable revision
 * that makes it true, which this row-level writer does not provide, so an attempt can never be ended as
 * {@code STABLE} with no revision to point at.
 */
public final class OperationStore {

    private static final String INSERT = "INSERT INTO " + TABLE + "(" + ID + ", " + BRANCH_ID + ", "
            + MESSAGE_ID + ", " + COMMAND_ID + ", " + PROMPT_PURPOSE + ", " + MODEL + ", "
            + THINKING_ENABLED + ", " + PROMPT_POLICY_VERSION + ", " + STATE + ", " + PARTIAL_REASONING
            + ", " + PARTIAL_ANSWER + ", " + ERROR_CODE + ", " + CREATED_AT + ", " + UPDATED_AT
            + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
    private static final String SELECT_ONE = "SELECT * FROM " + TABLE + " WHERE " + ID + " = ?";
    private static final String SELECT_BY_COMMAND = "SELECT * FROM " + TABLE + " WHERE " + COMMAND_ID
            + " = ?";
    private static final String SELECT_ACTIVE_BY_BRANCH = "SELECT * FROM " + TABLE + " WHERE "
            + BRANCH_ID + " = ? AND " + STATE + " IN (" + activeStatesSql() + ")";
    private static final String SELECT_ALL_ACTIVE = "SELECT * FROM " + TABLE + " WHERE " + STATE
            + " IN (" + activeStatesSql() + ") ORDER BY " + CREATED_AT + " ASC, " + ID + " ASC";
    private static final String UPDATE_PROGRESS = "UPDATE " + TABLE + " SET " + PARTIAL_REASONING
            + " = " + PARTIAL_REASONING + " || ?, " + PARTIAL_ANSWER + " = " + PARTIAL_ANSWER
            + " || ?, " + STATE + " = ?, " + UPDATED_AT + " = ? WHERE " + ID + " = ?";
    private static final String WRITE_OUTCOME = "UPDATE " + TABLE + " SET " + STATE + " = ?, "
            + ERROR_CODE + " = ?, " + UPDATED_AT + " = ? WHERE " + ID + " = ?";
    private static final String MARK_STABLE = "UPDATE " + TABLE + " SET " + STATE + " = 'STABLE', "
            + ERROR_CODE + " = NULL, " + UPDATED_AT + " = ? WHERE " + ID + " = ?";

    private final JdbcSupport jdbc;

    public OperationStore(JdbcSupport jdbc) {
        this.jdbc = jdbc;
    }

    /** Stores a freshly started attempt; a second in-flight attempt for the branch is refused by the
     * partial unique index {@code uq_generation_operations_active} (technical design §7). */
    public Operation insert(GenerationOperation operation) {
        return jdbc.runInTransaction(connection -> insert(connection, operation));
    }

    /**
     * Transaction-scoped insert: the caller owns the boundary and passes its own connection, so the
     * attempt can be written beside the topic, branch and positions the first-question accept commits
     * in one transaction (contract §6.2, mirroring Android {@code FirstTopicCreation}).
     */
    public Operation insert(Connection connection, GenerationOperation operation) {
        jdbc.update(connection, INSERT, s -> {
            s.setString(1, operation.id); s.setString(2, operation.branchId);
            s.setString(3, operation.messageId); s.setString(4, operation.commandId);
            s.setString(5, operation.purpose.policyPrefix); s.setString(6, operation.model.wireId);
            s.setInt(7, operation.thinkingEnabled ? 1 : 0);
            s.setString(8, operation.promptPolicyVersion); s.setString(9, operation.state().name());
            s.setString(10, operation.partialReasoning()); s.setString(11, operation.partialAnswer());
            s.setNull(12, Types.VARCHAR); s.setLong(13, operation.createdAt);
            s.setLong(14, operation.createdAt);
        });
        return new Operation(operation.id, operation.commandId, operation.branchId, operation.messageId,
                operation.purpose, operation.model, operation.thinkingEnabled,
                operation.promptPolicyVersion, operation.state(), operation.partialReasoning(),
                operation.partialAnswer(), null, operation.createdAt, operation.createdAt);
    }

    public Optional<Operation> byId(String operationId) {
        return jdbc.queryOne(SELECT_ONE, s -> s.setString(1, operationId), OperationStore::read);
    }

    /** Transaction-scoped read of one attempt, for a caller that already holds the connection. */
    public Optional<Operation> byId(Connection connection, String operationId) {
        return jdbc.queryOne(connection, SELECT_ONE, s -> s.setString(1, operationId),
                OperationStore::read);
    }

    /** The attempt a command pinned, so a repeated submit can find the one already stored. */
    public Optional<Operation> byCommandId(String commandId) {
        return jdbc.queryOne(SELECT_BY_COMMAND, s -> s.setString(1, commandId), OperationStore::read);
    }

    /** The branch's in-flight attempt, or empty when it has none. */
    public Optional<Operation> activeForBranch(String branchId) {
        return jdbc.queryOne(SELECT_ACTIVE_BY_BRANCH, s -> s.setString(1, branchId),
                OperationStore::read);
    }

    /** Every in-flight attempt, oldest first — the set a startup reap scans. */
    public List<Operation> listActive() {
        return jdbc.queryList(SELECT_ALL_ACTIVE, JdbcSupport.NO_PARAMS, OperationStore::read);
    }

    /**
     * Appends the arrived deltas and records the attempt's current state, in one statement.
     *
     * <p>A write to an attempt that already reached a terminal state is refused here and by the store's
     * trigger: a delta arriving after the user has seen an outcome must not write back.
     *
     * <p><strong>The carried state must be a non-terminal one.</strong> {@code STABLE} and every other
     * terminal state are refused, so this row-level progress writer cannot become a second way to end an
     * attempt — {@code STABLE} belongs to the stabilization path alone ({@link #markStable}), and the
     * other terminals to {@link #markTerminal}. The in-flight set is the shared domain's
     * {@link GenerationState#isTerminal()} rather than a copy written here.
     *
     * @throws IllegalArgumentException when {@code state} is null — an omitted state is a caller bug
     * @throws StorageException when {@code state} is terminal, or the row is missing or already terminal
     */
    public void updateProgress(String operationId, String reasoningDelta, String answerDelta,
            GenerationState state, long now) {
        if (state == null) {
            throw new IllegalArgumentException("state must not be null");
        }
        if (state.isTerminal()) {
            throw new StorageException("cannot record progress of generation operation " + operationId
                    + ": " + state + " is terminal; STABLE is written only through the stabilization "
                    + "path and the other terminal states through markTerminal, so a progress update must "
                    + "carry a non-terminal state (a STREAMING_* / VALIDATING / SUBMITTING / STAGED one)");
        }
        jdbc.runInTransaction(connection -> {
            requireActive(connection, operationId, "a progress update must not write back");
            JdbcSupport.requireSingleRow(jdbc.update(connection, UPDATE_PROGRESS, s -> {
                s.setString(1, reasoningDelta); s.setString(2, answerDelta);
                s.setString(3, state.name()); s.setLong(4, now); s.setString(5, operationId);
            }), "recording progress of generation operation " + operationId);
            return null;
        });
    }

    /**
     * Records an outcome that produces no stable revision: {@code FAILED}, {@code INTERRUPTED} or
     * {@code CANCELED}, with its mapped error code. The partial content stays exactly as it was, which
     * is what an in-place retry reads.
     *
     * <p>{@code STABLE} is <strong>not</strong> one of them. It is written only through the Phase 4
     * stabilization path, which creates the immutable revision and sets the state in the same act; this
     * method does not provide it. Offering it here would be a second way to freeze an attempt with no
     * revision to point at, which is exactly what the shared {@link GenerationOperation#advanceTo} also
     * refuses, so the domain's "STABLE implies a revision beside it" invariant is kept.
     *
     * @throws IllegalArgumentException when {@code terminal} is null or is not terminal — a caller bug
     * @throws StorageException when {@code terminal} is {@code STABLE}, the row is already terminal, or
     *         the shared transition graph refuses the move
     */
    public void markTerminal(String operationId, GenerationState terminal, String errorCode, long now) {
        if (terminal == null) {
            throw new IllegalArgumentException("terminal must not be null");
        }
        if (terminal == GenerationState.STABLE) {
            throw new StorageException("cannot end generation operation " + operationId + ": STABLE is "
                    + "written only through the Phase 4 stabilization path, which creates the immutable "
                    + "revision in the same act; markTerminal records outcomes that produce no revision, "
                    + "so it must not leave a finished attempt with no revision to point at");
        }
        if (!terminal.isTerminal()) {
            throw new IllegalArgumentException(terminal + " is not a terminal state; an in-flight"
                    + " attempt is written with updateProgress(...), which carries its deltas");
        }
        jdbc.runInTransaction(connection -> {
            Operation stored = requireActive(connection, operationId, "a terminal row is final");
            DomainResult<GenerationState> moved = stored.state.transitionTo(terminal);
            if (moved.isFailure()) {
                throw new StorageException("cannot end generation operation " + operationId + ": "
                        + moved.error());
            }
            JdbcSupport.requireSingleRow(jdbc.update(connection, WRITE_OUTCOME, s -> {
                s.setString(1, terminal.name()); s.setString(2, errorCode);
                s.setLong(3, now); s.setString(4, operationId);
            }), "ending generation operation " + operationId);
            return null;
        });
    }

    /**
     * The <strong>only</strong> storage entry that writes {@code STABLE}, with a self-contained
     * transaction. It exists for the stabilization path, which owns the boundary that creates the
     * immutable revision in the same act; every other writer ({@link #markTerminal},
     * {@link #updateProgress}) refuses the target.
     *
     * @throws StorageException when no row carries {@code operationId}, when the attempt is not in a
     *         streaming state that may become {@code STABLE} (so a second stabilization or an attempt
     *         that never reached the answer phase is refused), or when the final answer is empty
     */
    public void markStable(String operationId, long now) {
        jdbc.runInTransaction(connection -> {
            markStable(connection, operationId, now);
            return null;
        });
    }

    /**
     * Transaction-scoped half of {@link #markStable(String, long)}: the desktop {@code
     * GenerationStabilizer} calls it inside the same transaction that inserts the revision and moves
     * the position and the branch, so the attempt is only {@code STABLE} once its revision is durable.
     *
     * <p>The guard is the reason this is not a plain column write. {@code STABLE} is reachable only
     * from {@code STREAMING_ANSWER} (asked of the shared {@link GenerationState#canTransitionTo}, never
     * copied here) and requires a non-empty final answer (§9.3), so an attempt that already ended, one
     * that never reached the provider, or a reasoning-only stream cannot be frozen into a revision.
     */
    public void markStable(Connection connection, String operationId, long now) {
        Operation stored = jdbc.queryOne(connection, SELECT_ONE, s -> s.setString(1, operationId),
                OperationStore::read).orElseThrow(() -> new StorageException(
                        "no generation operation carries id " + operationId + "; there is nothing to "
                                + "stabilize"));
        if (!stored.state.canTransitionTo(GenerationState.STABLE)) {
            throw new StorageException("cannot stabilize generation operation " + operationId + ": it is "
                    + stored.state + ", and STABLE is reachable only from STREAMING_ANSWER; an attempt "
                    + "that already ended must not rewrite a reported outcome, and one that never "
                    + "reached the answer phase has nothing to freeze");
        }
        if (stored.partialAnswer == null || stored.partialAnswer.trim().isEmpty()) {
            throw new StorageException("cannot stabilize generation operation " + operationId + ": it "
                    + "holds no final answer, and §9.3 requires a non-empty final answer before a "
                    + "stable revision may be created; the partial content is kept for an in-place retry");
        }
        JdbcSupport.requireSingleRow(jdbc.update(connection, MARK_STABLE, s -> {
            s.setLong(1, now); s.setString(2, operationId);
        }), "stabilizing generation operation " + operationId);
    }

    /** Loads the row and refuses when it is missing or already terminal, before any write runs. */
    private Operation requireActive(java.sql.Connection connection, String operationId, String reason) {
        Operation stored = jdbc.queryOne(connection, SELECT_ONE, s -> s.setString(1, operationId),
                OperationStore::read).orElseThrow(
                        () -> new StorageException("no generation operation carries id " + operationId));
        if (stored.state.isTerminal()) {
            throw new StorageException("generation operation " + operationId + " already ended as "
                    + stored.state + "; " + reason);
        }
        return stored;
    }

    private static String activeStatesSql() {
        StringBuilder list = new StringBuilder();
        for (GenerationState state : GenerationState.values()) {
            if (!state.isActive()) {
                continue;
            }
            if (list.length() > 0) {
                list.append(", ");
            }
            list.append('\'').append(state.name()).append('\'');
        }
        return list.toString();
    }

    private static Operation read(ResultSet rows) throws SQLException {
        return new Operation(rows.getString(ID), rows.getString(COMMAND_ID), rows.getString(BRANCH_ID),
                rows.getString(MESSAGE_ID), PromptPurpose.fromPrefix(rows.getString(PROMPT_PURPOSE)),
                GenerationModel.fromWireId(rows.getString(MODEL)), JdbcSupport.flag(rows, THINKING_ENABLED),
                rows.getString(PROMPT_POLICY_VERSION), GenerationState.valueOf(rows.getString(STATE)),
                rows.getString(PARTIAL_REASONING), rows.getString(PARTIAL_ANSWER),
                rows.getString(ERROR_CODE), rows.getLong(CREATED_AT), rows.getLong(UPDATED_AT));
    }

    /**
     * One {@code generation_operations} row, exactly as stored. A row, not an attempt: a rehydrated
     * attempt would be a second writer of content no stream is feeding, so reads carry the state the
     * recovery rule needs and nothing more.
     */
    public static final class Operation {

        public final String id, commandId, branchId, messageId;
        public final PromptPurpose purpose;
        public final GenerationModel model;
        public final boolean thinkingEnabled;
        public final String promptPolicyVersion;
        public final GenerationState state;
        public final String partialReasoning, partialAnswer;
        /** The mapped provider error of a FAILED attempt; null otherwise. */
        public final String errorCode;
        public final long createdAt, updatedAt;

        Operation(String id, String commandId, String branchId, String messageId, PromptPurpose purpose,
                GenerationModel model, boolean thinkingEnabled, String promptPolicyVersion,
                GenerationState state, String partialReasoning, String partialAnswer, String errorCode,
                long createdAt, long updatedAt) {
            this.id = id; this.commandId = commandId; this.branchId = branchId; this.messageId = messageId;
            this.purpose = purpose; this.model = model; this.thinkingEnabled = thinkingEnabled;
            this.promptPolicyVersion = promptPolicyVersion; this.state = state;
            this.partialReasoning = partialReasoning; this.partialAnswer = partialAnswer;
            this.errorCode = errorCode; this.createdAt = createdAt; this.updatedAt = updatedAt;
        }
    }
}
