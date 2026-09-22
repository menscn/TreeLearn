package com.treelearn.data.db.dao;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteStatement;

import com.treelearn.core.domain.GenerationModel;
import com.treelearn.core.domain.GenerationOperation;
import com.treelearn.core.domain.GenerationState;
import com.treelearn.core.domain.PromptPurpose;
import com.treelearn.data.db.TreeLearnSchema;

import java.util.ArrayList;
import java.util.List;

/**
 * Row access for {@code generation_operations}.
 *
 * <p>The table is read and written in <strong>row</strong> form ({@link GenerationOperationRow}) and
 * never rehydrated into an attempt: an attempt's buffers are fed by the stream, so a second object
 * holding the same content would be a second writer nobody observes. Writes therefore have two
 * sources, and the split is deliberate:
 *
 * <ul>
 *   <li>{@link #insert} takes the attempt, because starting one is exactly what the domain entity is
 *       for and its state and buffers are the authority at that moment.
 *   <li>{@link #recordProgress} takes deltas and a state, so the streaming write stays a single
 *       statement against the row: no read-modify-write window exists in which a delta could be lost
 *       or re-ordered.
 * </ul>
 *
 * <p>Nothing here decides whether an attempt <em>may</em> move: the state graph belongs to
 * {@link GenerationOperation#advanceTo} and {@link GenerationState#canTransitionTo}, and the one
 * transition with side effects — reaching {@code STABLE} — belongs to
 * {@link GenerationStabilizer#stabilize}, which writes the revision in the same transaction.
 * {@link #markTerminal} therefore refuses that target rather than offering a second way in, and
 * {@link #markStable} is not public for the same reason.
 */
public final class GenerationOperationDao {

    private GenerationOperationDao() {
    }

    /**
     * Stores a freshly started attempt.
     *
     * <p>{@code updated_at} starts equal to {@code created_at}: nothing has happened to the row yet.
     * Whatever state and buffers the attempt already holds are written as they are, so an attempt that
     * was driven before being persisted — as the fixtures do — is stored faithfully.
     *
     * <p>A second in-flight attempt for the same branch is refused by
     * {@code uq_generation_operations_active}: §7 allows one active generation per branch, and two
     * attempts being started where one was intended is a caller bug, so the refusal propagates as
     * {@link android.database.sqlite.SQLiteConstraintException} rather than being dressed up as a
     * business outcome. Terminal attempts are unaffected — the index is partial, and a branch must stay
     * generatable into after its first attempt ends.
     */
    public static void insert(SQLiteDatabase database, GenerationOperation operation) {
        if (operation == null) {
            throw new IllegalArgumentException("operation must not be null");
        }
        database.insertOrThrow(TreeLearnSchema.GenerationOperations.TABLE, null, values(operation));
    }

    /**
     * Appends the deltas that have arrived and records the attempt's current state, in one statement.
     *
     * <p>Appending happens <em>in SQLite</em> ({@code partial_reasoning = partial_reasoning || ?})
     * rather than by reading the row into memory and writing it back, which is what makes a batch of
     * deltas land as one visible change: there is no window between the read and the write for a
     * second writer to be lost in, and a reader sees either the whole batch or none of it. The
     * statement is parameterised, so model output that looks like SQL is data.
     *
     * <p>An empty delta on a channel is legal and means "nothing arrived on this one": the state still
     * moves, which is how {@code VALIDATING} and {@code SUBMITTING} are recorded before any content
     * exists.
     *
     * <p>A write to an attempt that has already reached a terminal state is refused by the store's
     * trigger, not by this method. That is the point: a delta arriving after the user has seen an
     * outcome fails loudly at the write itself, whatever code path produced it.
     *
     * @throws IllegalStateException if no row carries {@code operationId} — an attempt cannot be
     *         updated through an identity the store does not hold.
     */
    public static void recordProgress(SQLiteDatabase database, String operationId,
                                      String reasoningDelta, String answerDelta,
                                      GenerationState state, long now) {
        requireId(operationId, "operationId");
        requireDelta(reasoningDelta, "reasoningDelta");
        requireDelta(answerDelta, "answerDelta");
        if (state == null) {
            throw new IllegalArgumentException("state must not be null");
        }
        if (now < 0) {
            throw new IllegalArgumentException("now must not be negative: " + now);
        }
        int updated;
        try (SQLiteStatement statement = database.compileStatement(
                "UPDATE " + TreeLearnSchema.GenerationOperations.TABLE + " SET "
                        + TreeLearnSchema.GenerationOperations.PARTIAL_REASONING + "="
                        + TreeLearnSchema.GenerationOperations.PARTIAL_REASONING + "||?, "
                        + TreeLearnSchema.GenerationOperations.PARTIAL_ANSWER + "="
                        + TreeLearnSchema.GenerationOperations.PARTIAL_ANSWER + "||?, "
                        + TreeLearnSchema.GenerationOperations.STATE + "=?, "
                        + TreeLearnSchema.GenerationOperations.UPDATED_AT + "=? WHERE "
                        + TreeLearnSchema.GenerationOperations.ID + "=?")) {
            statement.bindString(1, reasoningDelta);
            statement.bindString(2, answerDelta);
            statement.bindString(3, state.name());
            statement.bindLong(4, now);
            statement.bindString(5, operationId);
            updated = statement.executeUpdateDelete();
        }
        requireOneRow(updated, "attempt " + operationId);
    }

    /**
     * Records an outcome that produces no stable revision: {@code FAILED}, {@code INTERRUPTED} or
     * {@code CANCELED}. The partial content stays exactly as it was, which is what an in-place retry
     * reads.
     *
     * <p>This is the row-level writer recovery needs: a process that died cannot rebuild
     * {@code GenerationOperation} (there is no rehydration entry point, on purpose), so the startup
     * reap moves leftover rows to {@link GenerationState#recoveryTarget()} through here.
     *
     * <p>{@code STABLE} is refused, because reaching it means also creating the revision that makes it
     * true. Allowing it here would be a second way to mark an attempt finished with nothing to show —
     * exactly what {@code GenerationOperation.advanceTo} refuses to do.
     *
     * <p>Whether this write is legal is judged by the store: a second outcome on an attempt that has
     * already ended is refused by {@code trg_generation_operations_terminal_immutable} and propagates
     * as a constraint failure.
     *
     * @param errorCode the mapped provider error, or null when the outcome has none
     * @throws IllegalArgumentException if {@code terminal} is null or is {@code STABLE}
     * @throws IllegalStateException if no row carries {@code operationId}
     */
    public static void markTerminal(SQLiteDatabase database, String operationId,
                                    GenerationState terminal, String errorCode, long now) {
        requireId(operationId, "operationId");
        if (terminal == null) {
            throw new IllegalArgumentException("terminal must not be null");
        }
        if (terminal == GenerationState.STABLE) {
            throw new IllegalArgumentException("STABLE is reached only through "
                    + "GenerationStabilizer.stabilize(...), which creates the stable revision in the "
                    + "same act; marking a row finished without one would leave a frozen attempt with "
                    + "no version to point at");
        }
        if (!terminal.isTerminal()) {
            throw new IllegalArgumentException(terminal + " is not a terminal state; an in-flight "
                    + "attempt is written with recordProgress(...), which carries its deltas");
        }
        if (now < 0) {
            throw new IllegalArgumentException("now must not be negative: " + now);
        }
        writeState(database, operationId, terminal, errorCode, now);
    }

    /**
     * Moves an attempt to {@code STABLE}. <strong>Not public on purpose.</strong>
     *
     * <p>It is the second half of {@link GenerationStabilizer#stabilize} and is only correct inside
     * that transaction, beside the revision insert. A public writer here would be a way to freeze an
     * attempt with no revision — the state the whole entry point exists to make impossible.
     */
    static void markStable(SQLiteDatabase database, String operationId, long now) {
        requireId(operationId, "operationId");
        writeState(database, operationId, GenerationState.STABLE, null, now);
    }

    /** @return the attempt's row, or null when no row carries this id */
    public static GenerationOperationRow findById(SQLiteDatabase database, String operationId) {
        requireId(operationId, "operationId");
        try (Cursor cursor = database.query(TreeLearnSchema.GenerationOperations.TABLE, null,
                TreeLearnSchema.GenerationOperations.ID + "=?", new String[]{operationId},
                null, null, null)) {
            return cursor.moveToFirst() ? read(cursor) : null;
        }
    }

    /**
     * @return the branch's in-flight attempt, or null when the branch has none.
     *
     * <p>At most one row can match: {@code uq_generation_operations_active} is a partial unique index
     * over exactly the states this query asks for. The set is taken from the domain's
     * {@link GenerationState#isActive()} rather than copied here, so a state added to the lifecycle is
     * either in-flight everywhere or nowhere.
     */
    public static GenerationOperationRow findActiveByBranch(SQLiteDatabase database, String branchId) {
        if (branchId == null || branchId.isEmpty()) {
            throw new IllegalArgumentException("branchId must not be blank");
        }
        List<GenerationState> active = new ArrayList<>();
        for (GenerationState state : GenerationState.values()) {
            if (state.isActive()) {
                active.add(state);
            }
        }
        try (Cursor cursor = database.query(TreeLearnSchema.GenerationOperations.TABLE, null,
                TreeLearnSchema.GenerationOperations.BRANCH_ID + "=? AND "
                        + TreeLearnSchema.GenerationOperations.STATE + " IN ("
                        + stateListSql(active) + ")",
                new String[]{branchId}, null, null, null)) {
            return cursor.moveToFirst() ? read(cursor) : null;
        }
    }

    /**
     * @return every attempt a process restart cannot leave behind, oldest first.
     *
     * <p>The set comes from {@link GenerationState#processRecoverableStates()} — the domain's answer,
     * including {@code STAGED}, which §7's prose omits but which nothing would ever schedule again
     * after a restart. Callers get rows, not attempts, because recovery only needs each row's state to
     * apply {@link GenerationState#recoveryTarget()}.
     */
    public static List<GenerationOperationRow> findRecoverable(SQLiteDatabase database) {
        GenerationState[] recoverable = GenerationState.processRecoverableStates();
        List<GenerationState> states = new ArrayList<>();
        for (GenerationState state : recoverable) {
            states.add(state);
        }
        List<GenerationOperationRow> rows = new ArrayList<>();
        try (Cursor cursor = database.query(TreeLearnSchema.GenerationOperations.TABLE, null,
                TreeLearnSchema.GenerationOperations.STATE + " IN (" + stateListSql(states) + ")",
                null, null, null, TreeLearnSchema.GenerationOperations.CREATED_AT + " ASC")) {
            while (cursor.moveToNext()) {
                rows.add(read(cursor));
            }
        }
        return rows;
    }

    private static void writeState(SQLiteDatabase database, String operationId,
                                   GenerationState state, String errorCode, long now) {
        ContentValues values = new ContentValues();
        values.put(TreeLearnSchema.GenerationOperations.STATE, state.name());
        if (errorCode == null) {
            values.putNull(TreeLearnSchema.GenerationOperations.ERROR_CODE);
        } else {
            values.put(TreeLearnSchema.GenerationOperations.ERROR_CODE, errorCode);
        }
        values.put(TreeLearnSchema.GenerationOperations.UPDATED_AT, now);
        int updated = database.update(TreeLearnSchema.GenerationOperations.TABLE, values,
                TreeLearnSchema.GenerationOperations.ID + "=?", new String[]{operationId});
        requireOneRow(updated, "attempt " + operationId);
    }

    /**
     * The read mapping. The purpose is read from its own column rather than derived from the version
     * column, so a row whose two columns contradict each other is not quietly resolved into whichever
     * one the mapper happened to prefer.
     */
    private static GenerationOperationRow read(Cursor cursor) {
        return new GenerationOperationRow(
                Rows.string(cursor, TreeLearnSchema.GenerationOperations.ID),
                Rows.string(cursor, TreeLearnSchema.GenerationOperations.COMMAND_ID),
                Rows.string(cursor, TreeLearnSchema.GenerationOperations.BRANCH_ID),
                Rows.string(cursor, TreeLearnSchema.GenerationOperations.MESSAGE_ID),
                PromptPurpose.fromPrefix(
                        Rows.string(cursor, TreeLearnSchema.GenerationOperations.PROMPT_PURPOSE)),
                GenerationModel.fromWireId(
                        Rows.string(cursor, TreeLearnSchema.GenerationOperations.MODEL)),
                Rows.bool(cursor, TreeLearnSchema.GenerationOperations.THINKING_ENABLED),
                Rows.string(cursor, TreeLearnSchema.GenerationOperations.PROMPT_POLICY_VERSION),
                GenerationState.valueOf(
                        Rows.string(cursor, TreeLearnSchema.GenerationOperations.STATE)),
                Rows.string(cursor, TreeLearnSchema.GenerationOperations.PARTIAL_REASONING),
                Rows.string(cursor, TreeLearnSchema.GenerationOperations.PARTIAL_ANSWER),
                Rows.string(cursor, TreeLearnSchema.GenerationOperations.ERROR_CODE),
                Rows.longOf(cursor, TreeLearnSchema.GenerationOperations.CREATED_AT),
                Rows.longOf(cursor, TreeLearnSchema.GenerationOperations.UPDATED_AT));
    }

    private static ContentValues values(GenerationOperation operation) {
        ContentValues values = new ContentValues();
        values.put(TreeLearnSchema.GenerationOperations.ID, operation.id);
        values.put(TreeLearnSchema.GenerationOperations.COMMAND_ID, operation.commandId);
        values.put(TreeLearnSchema.GenerationOperations.BRANCH_ID, operation.branchId);
        values.put(TreeLearnSchema.GenerationOperations.MESSAGE_ID, operation.messageId);
        values.put(TreeLearnSchema.GenerationOperations.PROMPT_PURPOSE,
                operation.purpose.policyPrefix);
        values.put(TreeLearnSchema.GenerationOperations.MODEL, operation.model.wireId);
        values.put(TreeLearnSchema.GenerationOperations.THINKING_ENABLED,
                operation.thinkingEnabled ? 1 : 0);
        values.put(TreeLearnSchema.GenerationOperations.PROMPT_POLICY_VERSION,
                operation.promptPolicyVersion);
        values.put(TreeLearnSchema.GenerationOperations.STATE, operation.state().name());
        values.put(TreeLearnSchema.GenerationOperations.PARTIAL_REASONING,
                operation.partialReasoning());
        values.put(TreeLearnSchema.GenerationOperations.PARTIAL_ANSWER, operation.partialAnswer());
        values.put(TreeLearnSchema.GenerationOperations.CREATED_AT, operation.createdAt);
        values.put(TreeLearnSchema.GenerationOperations.UPDATED_AT, operation.createdAt);
        return values;
    }

    /** Builds an {@code IN} list from domain constants — never from user input. */
    private static String stateListSql(List<GenerationState> states) {
        StringBuilder list = new StringBuilder();
        for (GenerationState state : states) {
            if (list.length() > 0) {
                list.append(", ");
            }
            list.append('\'').append(state.name()).append('\'');
        }
        return list.toString();
    }

    private static void requireId(String value, String field) {
        if (value == null || value.isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }

    private static void requireDelta(String delta, String field) {
        if (delta == null) {
            throw new IllegalArgumentException(field + " must not be null; an empty string means "
                    + "\"nothing arrived on this channel\"");
        }
    }

    private static void requireOneRow(int updated, String what) {
        if (updated != 1) {
            throw new IllegalStateException("writing " + what + " affected " + updated
                    + " row(s); a generation operation write must touch exactly its own row");
        }
    }
}
