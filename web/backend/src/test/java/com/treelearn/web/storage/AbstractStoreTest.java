package com.treelearn.web.storage;

import com.treelearn.core.domain.AnswerRevision;
import com.treelearn.core.domain.Branch;
import com.treelearn.core.domain.BranchAnchor;
import com.treelearn.core.domain.BranchSource;
import com.treelearn.core.domain.GenerationCommand;
import com.treelearn.core.domain.GenerationModel;
import com.treelearn.core.domain.GenerationOperation;
import com.treelearn.core.domain.GenerationState;
import com.treelearn.core.domain.Message;
import com.treelearn.core.domain.PromptPurpose;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;

/**
 * Shared fixture for the desktop store tests (task T-2026-10-10-010).
 *
 * <p>Every case runs on the real sqlite-jdbc engine in a JUnit temporary directory, migrated through
 * the production {@link DatabaseMigrator}, and never touches the real {@code %LOCALAPPDATA%} data
 * directory. A fork helper drives the full child-branch write so the tests exercise the same three
 * steps the production use case must (branch row, birth snapshot row, pointer).
 */
abstract class AbstractStoreTest {

    /** A fixed, non-zero epoch-millis instant so a rounded timestamp is visible. */
    static final long T0 = 1_752_000_000_000L;

    static final String LEARNING_ANSWER_POLICY = "tl-learning-answer/1.1.0";

    @TempDir
    Path dataDir;

    DesktopDatabase database;
    JdbcSupport jdbc;
    TopicStore topics;
    BranchStore branches;
    MessageStore messages;
    AnswerRevisionStore revisions;
    OperationStore operations;
    SnapshotStore snapshots;
    ArtifactStore artifacts;

    @BeforeEach
    void migrateAndOpenStores() {
        new DatabaseMigrator().migrate(dataDir.resolve("treelearn.db"));
        database = new DesktopDatabase(dataDir);
        jdbc = new JdbcSupport(database);
        topics = new TopicStore(jdbc);
        branches = new BranchStore(jdbc);
        messages = new MessageStore(jdbc);
        revisions = new AnswerRevisionStore(jdbc);
        operations = new OperationStore(jdbc);
        snapshots = new SnapshotStore(jdbc);
        artifacts = new ArtifactStore(jdbc);
    }

    /**
     * Starts and stores a fresh STAGED attempt for a branch/position through the shared domain, so a
     * test gets a real {@code generation_operations} row without hand-writing its columns.
     */
    OperationStore.Operation startOperation(String branchId, String messageId, long now) {
        GenerationCommand command = GenerationCommand.create(branchId, messageId,
                PromptPurpose.LEARNING_ANSWER, LEARNING_ANSWER_POLICY, GenerationModel.FLASH, false, now)
                .value();
        return operations.insert(GenerationOperation.start(command, now));
    }

    /**
     * Forks a readable child branch from {@code parent}: the branch row (with its source columns and
     * anchor), then a birth snapshot row, then the pointer that completes the write.
     */
    Branch forkChild(String topicId, Branch parent, long now) {
        Message answer = messages.appendAiPosition(parent.id, messages.nextOrdinal(parent.id), now);
        AnswerRevision revision = revisions.insert(answer.id, "", "answer-" + now,
                GenerationModel.FLASH, false, LEARNING_ANSWER_POLICY, now);
        String snapshotId = UUID.randomUUID().toString();
        BranchSource source = BranchSource.fromOperation(parent.id, answer.id, snapshotId,
                GenerationState.STABLE, revision.id, BranchAnchor.wholeAnswer()).value();
        Branch child = branches.createChild(topicId, source, now);
        insertSnapshotRow(snapshotId, child.id, now);
        branches.attachContextSnapshot(child.id, snapshotId);
        return child;
    }

    /** Seeds a {@code context_snapshots} row. That table's own store belongs to a later task (T-011). */
    void insertSnapshotRow(String snapshotId, String branchId, long now) {
        jdbc.update("INSERT INTO " + DesktopSchema.ContextSnapshots.TABLE + "("
                        + DesktopSchema.ContextSnapshots.ID + ", "
                        + DesktopSchema.ContextSnapshots.BRANCH_ID + ", "
                        + DesktopSchema.ContextSnapshots.SCHEMA_VERSION + ", "
                        + DesktopSchema.ContextSnapshots.DIGEST + ", "
                        + DesktopSchema.ContextSnapshots.PAYLOAD + ", "
                        + DesktopSchema.ContextSnapshots.CREATED_AT
                        + ") VALUES (?, ?, 1, 'digest', '{}', ?)",
                s -> {
                    s.setString(1, snapshotId);
                    s.setString(2, branchId);
                    s.setLong(3, now);
                });
    }

    /**
     * Seeds a live {@code generation_operations} row so a position can be brought into the
     * "attempt running" state. That table's own store belongs to a later task (T-011); here it only
     * supplies the foreign key {@code messages.active_operation_id} needs.
     *
     * @return the seeded operation id
     */
    String insertActiveOperationRow(String branchId, String messageId, long now) {
        String operationId = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO " + DesktopSchema.GenerationOperations.TABLE + "("
                        + DesktopSchema.GenerationOperations.ID + ", "
                        + DesktopSchema.GenerationOperations.BRANCH_ID + ", "
                        + DesktopSchema.GenerationOperations.MESSAGE_ID + ", "
                        + DesktopSchema.GenerationOperations.COMMAND_ID + ", "
                        + DesktopSchema.GenerationOperations.PROMPT_PURPOSE + ", "
                        + DesktopSchema.GenerationOperations.MODEL + ", "
                        + DesktopSchema.GenerationOperations.THINKING_ENABLED + ", "
                        + DesktopSchema.GenerationOperations.PROMPT_POLICY_VERSION + ", "
                        + DesktopSchema.GenerationOperations.STATE + ", "
                        + DesktopSchema.GenerationOperations.CREATED_AT + ", "
                        + DesktopSchema.GenerationOperations.UPDATED_AT
                        + ") VALUES (?, ?, ?, ?, ?, ?, 0, ?, 'STAGED', ?, ?)",
                s -> {
                    s.setString(1, operationId);
                    s.setString(2, branchId);
                    s.setString(3, messageId);
                    s.setString(4, UUID.randomUUID().toString());
                    s.setString(5, PromptPurpose.LEARNING_ANSWER.policyPrefix);
                    s.setString(6, GenerationModel.FLASH.wireId);
                    s.setString(7, LEARNING_ANSWER_POLICY);
                    s.setLong(8, now);
                    s.setLong(9, now);
                });
        return operationId;
    }

    void deleteMessage(String messageId) {
        JdbcSupport.requireSingleRow(
                jdbc.update("DELETE FROM " + DesktopSchema.Messages.TABLE + " WHERE "
                        + DesktopSchema.Messages.ID + " = ?", s -> s.setString(1, messageId)),
                "deleting message " + messageId);
    }
}
