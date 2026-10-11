package com.treelearn.web.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.treelearn.core.domain.GenerationCommand;
import com.treelearn.core.domain.GenerationModel;
import com.treelearn.core.domain.GenerationOperation;
import com.treelearn.core.domain.GenerationState;
import com.treelearn.core.domain.Message;
import com.treelearn.core.domain.PromptPurpose;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * T-2026-10-10-011: the startup interruption sweep on real sqlite-jdbc. Recovery targets and the
 * "which states cannot survive a restart" set both come from the shared {@code :core} domain, so the
 * sweep mirrors Android {@code GenerationCoordinator.recover()} without naming a state itself.
 */
class InterruptionSweeperTest extends AbstractStoreTest {

    private static final long SWEEP_NOW = T0 + 500;

    private InterruptionSweeper sweeper() {
        return new InterruptionSweeper(operations,
                Clock.fixed(Instant.ofEpochMilli(SWEEP_NOW), ZoneOffset.UTC));
    }

    @Test
    void sweepSettlesAStreamingAttemptToInterruptedKeepingItsPartialContent() {
        TopicStore.Created created = topics.create(T0);
        Message answer = messages.appendAiPosition(created.rootBranch.id, 0, T0);
        OperationStore.Operation running = startOperation(created.rootBranch.id, answer.id, T0);
        operations.updateProgress(running.id, "keep-reasoning", "keep-answer",
                GenerationState.STREAMING_ANSWER, T0 + 1);

        assertEquals(1, sweeper().sweep());

        OperationStore.Operation settled = operations.byId(running.id).orElseThrow();
        assertEquals(GenerationState.INTERRUPTED, settled.state);
        assertEquals("keep-reasoning", settled.partialReasoning);
        assertEquals("keep-answer", settled.partialAnswer);
        assertEquals(SWEEP_NOW, settled.updatedAt, "the sweep stamps the row with the injected clock");
        assertTrue(operations.activeForBranch(created.rootBranch.id).isEmpty());
    }

    @Test
    void sweepIsIdempotentAndLeavesTerminalRowsAlone() {
        TopicStore.Created created = topics.create(T0);
        Message answer = messages.appendAiPosition(created.rootBranch.id, 0, T0);
        OperationStore.Operation ended = startOperation(created.rootBranch.id, answer.id, T0);
        operations.markTerminal(ended.id, GenerationState.CANCELED, null, T0 + 1);

        assertEquals(0, sweeper().sweep(), "a terminal row is not recoverable");
        assertEquals(0, sweeper().sweep(), "a second sweep has nothing left to do");
        assertEquals(GenerationState.CANCELED, operations.byId(ended.id).orElseThrow().state);
    }

    @Test
    void aStagedRowTakesTheDomainsRecoveryTarget() {
        TopicStore.Created created = topics.create(T0);
        Message answer = messages.appendAiPosition(created.rootBranch.id, 0, T0);
        OperationStore.Operation staged = startOperation(created.rootBranch.id, answer.id, T0);

        assertEquals(1, sweeper().sweep());
        assertEquals(GenerationState.CANCELED, operations.byId(staged.id).orElseThrow().state,
                "the shared domain maps a never-submitted STAGED attempt to CANCELED, not INTERRUPTED");
        assertEquals(GenerationState.STAGED.recoveryTarget(),
                operations.byId(staged.id).orElseThrow().state);
    }

    @Test
    void sweepSettlesEveryActiveRowAcrossBranchesInOnePass() {
        TopicStore.Created first = topics.create(T0);
        TopicStore.Created second = topics.create(T0 + 1);
        startOperation(first.rootBranch.id, messages.appendAiPosition(first.rootBranch.id, 0, T0).id,
                T0);
        startOperation(second.rootBranch.id, messages.appendAiPosition(second.rootBranch.id, 0, T0).id,
                T0);

        assertEquals(2, sweeper().sweep());
        assertTrue(operations.listActive().isEmpty());
    }

    @Test
    void startupMigratesThenSweepsBeforeTheContextIsReady() throws IOException {
        Path dir = Files.createTempDirectory("treelearn-011-startup");
        new DatabaseMigrator().migrate(dir.resolve("treelearn.db"));
        DesktopDatabase prepared = new DesktopDatabase(dir);
        JdbcSupport preparedJdbc = new JdbcSupport(prepared);
        TopicStore preparedTopics = new TopicStore(preparedJdbc);
        MessageStore preparedMessages = new MessageStore(preparedJdbc);
        OperationStore preparedOperations = new OperationStore(preparedJdbc);
        TopicStore.Created created = preparedTopics.create(T0);
        Message answer = preparedMessages.appendAiPosition(created.rootBranch.id, 0, T0);
        GenerationCommand command = GenerationCommand.create(created.rootBranch.id, answer.id,
                PromptPurpose.LEARNING_ANSWER, LEARNING_ANSWER_POLICY, GenerationModel.FLASH, false, T0)
                .value();
        String operationId = preparedOperations.insert(GenerationOperation.start(command, T0)).id;

        new ApplicationContextRunner()
                .withUserConfiguration(StorageConfiguration.class)
                .withPropertyValues("treelearn.data-dir="
                        + dir.toAbsolutePath().toString().replace('\\', '/'))
                .run(context -> {
                    assertNull(context.getStartupFailure(),
                            "migration then the interruption sweep must both succeed before ready");
                    GenerationState settled = new OperationStore(new JdbcSupport(prepared))
                            .byId(operationId).orElseThrow().state;
                    assertEquals(GenerationState.CANCELED, settled,
                            "the startup sweep must have settled the leftover attempt");
                });
    }
}
