package com.treelearn.web.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.treelearn.core.domain.GenerationState;
import com.treelearn.core.domain.Message;
import org.junit.jupiter.api.Test;

/**
 * T-2026-10-10-024: {@code OperationStore.markStable} is the single storage entry that writes {@code
 * STABLE}, and its guard is what makes the state honest. Every case runs on real sqlite-jdbc: the
 * reachable set comes from the shared {@code GenerationState.canTransitionTo}, and the empty-answer
 * refusal is the §9.3 rule the stabilizer also relies on.
 */
class OperationStoreStablePathTest extends AbstractStoreTest {

    private OperationStore.Operation streaming(String reasoning, String answer) {
        TopicStore.Created created = topics.create(T0);
        Message position = messages.appendAiPosition(created.rootBranch.id, 0, T0);
        OperationStore.Operation running = startOperation(created.rootBranch.id, position.id, T0);
        operations.updateProgress(running.id, reasoning, answer, GenerationState.STREAMING_ANSWER,
                T0 + 1);
        return running;
    }

    @Test
    void markStableRefusesAnAttemptThatNeverReachedTheAnswerPhase() {
        TopicStore.Created created = topics.create(T0);
        Message position = messages.appendAiPosition(created.rootBranch.id, 0, T0);
        OperationStore.Operation staged = startOperation(created.rootBranch.id, position.id, T0);

        StorageException refused = assertThrows(StorageException.class,
                () -> operations.markStable(staged.id, T0 + 2));

        assertTrue(refused.getMessage().contains(staged.id));
        assertTrue(refused.getMessage().contains("STREAMING_ANSWER"),
                "the refusal must name the only state STABLE is reachable from");
        assertEquals(GenerationState.STAGED, operations.byId(staged.id).orElseThrow().state,
                "a refused STABLE write must leave the row unchanged");
    }

    @Test
    void markStableRefusesAnEmptyFinalAnswer() {
        OperationStore.Operation reasoningOnly = streaming("只有推理", "");

        StorageException refused = assertThrows(StorageException.class,
                () -> operations.markStable(reasoningOnly.id, T0 + 2));

        assertTrue(refused.getMessage().contains("no final answer"));
        OperationStore.Operation read = operations.byId(reasoningOnly.id).orElseThrow();
        assertEquals(GenerationState.STREAMING_ANSWER, read.state);
        assertEquals("只有推理", read.partialReasoning, "the partial content is kept for an in-place retry");
    }

    @Test
    void markStableRefusesAnAttemptThatAlreadyEnded() {
        OperationStore.Operation running = streaming("", "answer");
        operations.markTerminal(running.id, GenerationState.FAILED, "PROVIDER_FAILED", T0 + 2);

        assertThrows(StorageException.class, () -> operations.markStable(running.id, T0 + 3),
                "a late stabilization must not rewrite an outcome already reported");
        assertEquals(GenerationState.FAILED, operations.byId(running.id).orElseThrow().state);
    }

    @Test
    void markStableMovesAStreamingAnswerToStableAndClearsAnyPriorError() {
        OperationStore.Operation running = streaming("想", "答");

        operations.markStable(running.id, T0 + 2);

        OperationStore.Operation read = operations.byId(running.id).orElseThrow();
        assertEquals(GenerationState.STABLE, read.state);
        assertNull(read.errorCode);
        assertEquals("答", read.partialAnswer, "the buffers are left exactly as the stream filled them");
        assertEquals(T0 + 2, read.updatedAt);
        assertFalse(operations.activeForBranch(running.branchId).isPresent(),
                "a STABLE attempt is terminal and no longer counts as in flight");
    }

    @Test
    void updateProgressRefusesStableAndEveryOtherTerminalTarget() {
        OperationStore.Operation running = streaming("", "答");

        assertThrows(StorageException.class,
                () -> operations.updateProgress(running.id, "", "", GenerationState.STABLE, T0 + 2),
                "updateProgress must not be a second way to write STABLE");
        assertThrows(StorageException.class,
                () -> operations.updateProgress(running.id, "", "", GenerationState.FAILED, T0 + 2),
                "a progress write must carry a non-terminal state");
        assertThrows(StorageException.class,
                () -> operations.updateProgress(running.id, "", "", GenerationState.INTERRUPTED, T0 + 2));
        assertThrows(StorageException.class,
                () -> operations.updateProgress(running.id, "", "", GenerationState.CANCELED, T0 + 2));

        OperationStore.Operation unchanged = operations.byId(running.id).orElseThrow();
        assertEquals(GenerationState.STREAMING_ANSWER, unchanged.state);
        assertEquals("答", unchanged.partialAnswer, "a refused progress write must change nothing");

        // A legal progress state is still accepted.
        operations.updateProgress(running.id, "更", "", GenerationState.STREAMING_REASONING, T0 + 3);
        OperationStore.Operation advanced = operations.byId(running.id).orElseThrow();
        assertEquals("更", advanced.partialReasoning);
        assertEquals(GenerationState.STREAMING_REASONING, advanced.state);
    }

    @Test
    void markTerminalStillRefusesStableSoThereIsExactlyOneWriter() {
        OperationStore.Operation running = streaming("", "答");

        assertThrows(StorageException.class, () -> operations.markTerminal(running.id,
                GenerationState.STABLE, null, T0 + 2),
                "STABLE belongs to the stabilization path, which creates the revision in the same act");
        assertEquals(GenerationState.STREAMING_ANSWER, operations.byId(running.id).orElseThrow().state);
    }
}
