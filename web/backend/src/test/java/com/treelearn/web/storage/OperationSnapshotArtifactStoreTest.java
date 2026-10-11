package com.treelearn.web.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.treelearn.core.domain.AnswerRevision;
import com.treelearn.core.domain.ContextSnapshot;
import com.treelearn.core.domain.ContextSnapshotCodec;
import com.treelearn.core.domain.GenerationModel;
import com.treelearn.core.domain.GenerationState;
import com.treelearn.core.domain.Guard;
import com.treelearn.core.domain.Message;
import com.treelearn.core.domain.PromptPurpose;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * T-2026-10-10-011: generation operations, birth snapshots and derived artifacts on real sqlite-jdbc.
 * The operation state, the interruption targets and the terminal-write protection all come from the
 * shared {@code :core} domain — the store only persists what the domain decided.
 */
class OperationSnapshotArtifactStoreTest extends AbstractStoreTest {

    @Test
    void aFreshAttemptIsStoredAsStagedAndLooksUpByIdentityAndCommandId() {
        TopicStore.Created created = topics.create(T0);
        Message answer = messages.appendAiPosition(created.rootBranch.id, 0, T0);
        OperationStore.Operation stored = startOperation(created.rootBranch.id, answer.id, T0);

        OperationStore.Operation read = operations.byId(stored.id).orElseThrow();
        assertEquals(stored.id, read.id);
        assertEquals(stored.commandId, read.commandId);
        assertEquals(created.rootBranch.id, read.branchId);
        assertEquals(answer.id, read.messageId);
        assertEquals(PromptPurpose.LEARNING_ANSWER, read.purpose);
        assertEquals(LEARNING_ANSWER_POLICY, read.promptPolicyVersion);
        assertEquals(GenerationState.STAGED, read.state);
        assertTrue(read.state.isActive());
        assertEquals("", read.partialReasoning);
        assertEquals("", read.partialAnswer);
        assertEquals(T0, read.createdAt);
        assertEquals(T0, read.updatedAt);

        assertEquals(stored.id, operations.byCommandId(stored.commandId).orElseThrow().id,
                "a command id lookup must return the same attempt identity it was inserted with");
    }

    @Test
    void aSecondInFlightAttemptForOneBranchIsRefusedByThePartialIndex() {
        TopicStore.Created created = topics.create(T0);
        Message answer = messages.appendAiPosition(created.rootBranch.id, 0, T0);
        startOperation(created.rootBranch.id, answer.id, T0);

        assertThrows(StorageException.class,
                () -> startOperation(created.rootBranch.id, answer.id, T0 + 1),
                "uq_generation_operations_active admits one in-flight attempt per branch");
    }

    @Test
    void activeForBranchSeesOnlyTheInFlightAttemptAndForgetsItOnceTerminal() {
        TopicStore.Created created = topics.create(T0);
        Message answer = messages.appendAiPosition(created.rootBranch.id, 0, T0);
        OperationStore.Operation running = startOperation(created.rootBranch.id, answer.id, T0);

        assertEquals(running.id, operations.activeForBranch(created.rootBranch.id).orElseThrow().id);
        assertTrue(operations.listActive().stream().anyMatch(o -> o.id.equals(running.id)));

        operations.markTerminal(running.id, GenerationState.CANCELED, null, T0 + 5);

        assertFalse(operations.activeForBranch(created.rootBranch.id).isPresent());
        assertTrue(operations.listActive().stream().noneMatch(o -> o.id.equals(running.id)),
                "a terminal attempt must no longer count as active");
    }

    @Test
    void aTerminalRowRefusesEveryLaterWrite() {
        TopicStore.Created created = topics.create(T0);
        Message answer = messages.appendAiPosition(created.rootBranch.id, 0, T0);
        OperationStore.Operation running = startOperation(created.rootBranch.id, answer.id, T0);
        operations.updateProgress(running.id, "", "part", GenerationState.STREAMING_ANSWER, T0 + 1);
        operations.markTerminal(running.id, GenerationState.FAILED, "PROVIDER_FAILED", T0 + 2);

        assertThrows(StorageException.class, () -> operations.updateProgress(running.id, "late", "",
                GenerationState.STREAMING_ANSWER, T0 + 3),
                "a delta arriving after a terminal state must be refused, not written back");
        assertThrows(StorageException.class, () -> operations.markTerminal(running.id,
                GenerationState.INTERRUPTED, null, T0 + 4),
                "a second outcome on a finished attempt must be refused");

        OperationStore.Operation read = operations.byId(running.id).orElseThrow();
        assertEquals(GenerationState.FAILED, read.state);
        assertEquals("PROVIDER_FAILED", read.errorCode);
        assertEquals("part", read.partialAnswer, "the refused writes must have changed nothing");
    }

    @Test
    void progressAppendsItsDeltasAndMovesTheState() {
        TopicStore.Created created = topics.create(T0);
        Message answer = messages.appendAiPosition(created.rootBranch.id, 0, T0);
        OperationStore.Operation running = startOperation(created.rootBranch.id, answer.id, T0);

        operations.updateProgress(running.id, "think", "", GenerationState.STREAMING_REASONING, T0 + 1);
        operations.updateProgress(running.id, "", "ans", GenerationState.STREAMING_ANSWER, T0 + 2);

        OperationStore.Operation read = operations.byId(running.id).orElseThrow();
        assertEquals("think", read.partialReasoning, "deltas accumulate across calls");
        assertEquals("ans", read.partialAnswer);
        assertEquals(GenerationState.STREAMING_ANSWER, read.state);
        assertEquals(T0 + 2, read.updatedAt);
    }

    @Test
    void markTerminalFollowsTheDomainsTransitionRule() {
        TopicStore.Created created = topics.create(T0);
        Message answer = messages.appendAiPosition(created.rootBranch.id, 0, T0);
        OperationStore.Operation running = startOperation(created.rootBranch.id, answer.id, T0);

        assertThrows(IllegalArgumentException.class, () -> operations.markTerminal(running.id,
                GenerationState.STREAMING_ANSWER, null, T0 + 1),
                "a non-terminal target is a caller bug, not a business outcome");

        operations.updateProgress(running.id, "", "answer", GenerationState.STREAMING_ANSWER, T0 + 2);
        // STABLE is not writable through markTerminal: it is the Phase 4 stabilization path's act,
        // which creates the immutable revision and sets the state together. The accepted path is
        // covered with a revision-free outcome instead.
        operations.markTerminal(running.id, GenerationState.FAILED, "PROVIDER_FAILED", T0 + 3);
        OperationStore.Operation read = operations.byId(running.id).orElseThrow();
        assertEquals(GenerationState.FAILED, read.state);
        assertEquals("PROVIDER_FAILED", read.errorCode);
    }

    @Test
    void markTerminalRefusesStableAndLeavesTheRowUntouched() {
        TopicStore.Created created = topics.create(T0);
        Message answer = messages.appendAiPosition(created.rootBranch.id, 0, T0);
        OperationStore.Operation running = startOperation(created.rootBranch.id, answer.id, T0);
        operations.updateProgress(running.id, "", "answer", GenerationState.STREAMING_ANSWER, T0 + 1);

        StorageException refused = assertThrows(StorageException.class,
                () -> operations.markTerminal(running.id, GenerationState.STABLE, null, T0 + 2),
                "STABLE belongs to the Phase 4 stabilizer path, which creates the immutable revision "
                        + "in the same act; this row-level writer must not leave a finished attempt "
                        + "with no revision to point at");

        assertTrue(refused.getMessage().contains(running.id),
                "the refusal must name the operation it refused");
        assertTrue(refused.getMessage().contains("STABLE"),
                "the refusal must state the reason");

        OperationStore.Operation read = operations.byId(running.id).orElseThrow();
        assertEquals(GenerationState.STREAMING_ANSWER, read.state,
                "a refused STABLE write must leave the row state unchanged");
        assertTrue(operations.activeForBranch(created.rootBranch.id).isPresent(),
                "the refused attempt is still in flight");
    }

    @Test
    void aBirthSnapshotRoundTripsThroughItsCodec() {
        TopicStore.Created created = topics.create(T0);
        Message question = messages.appendUserMessage(created.rootBranch.id, 0, "问题", null, T0);
        ContextSnapshot stored = snapshots.insert(created.rootBranch.id,
                List.of(ContextSnapshot.Source.userMessage(question.id)), T0);

        ContextSnapshot read = snapshots.findForBranch(created.rootBranch.id).orElseThrow();
        assertEquals(stored.id, read.id);
        assertEquals(ContextSnapshot.SCHEMA_VERSION, read.schemaVersion);
        assertEquals(stored.digest, read.digest);
        assertEquals(List.of(question.id), read.sources.stream().map(source -> source.refId).toList());
    }

    @Test
    void aSecondBirthSnapshotForOneBranchIsRefused() {
        TopicStore.Created created = topics.create(T0);
        Message question = messages.appendUserMessage(created.rootBranch.id, 0, "问题", null, T0);
        snapshots.insert(created.rootBranch.id, List.of(ContextSnapshot.Source.userMessage(question.id)),
                T0);

        assertThrows(StorageException.class, () -> snapshots.insert(created.rootBranch.id,
                List.of(ContextSnapshot.Source.userMessage(question.id)), T0 + 1),
                "uq_context_snapshots_branch admits one snapshot per branch");
    }

    @Test
    void aSnapshotWhosePayloadDoesNotHashToItsDigestIsRefused() {
        TopicStore.Created created = topics.create(T0);
        Message question = messages.appendUserMessage(created.rootBranch.id, 0, "问题", null, T0);
        String payload = ContextSnapshotCodec.encode(List.of(ContextSnapshot.Source.userMessage(
                question.id)));
        // A row written around the codec: the payload is canonical but the digest disagrees, which no
        // store path ever produces.
        jdbc.update("INSERT INTO " + DesktopSchema.ContextSnapshots.TABLE + "("
                        + DesktopSchema.ContextSnapshots.ID + ", "
                        + DesktopSchema.ContextSnapshots.BRANCH_ID + ", "
                        + DesktopSchema.ContextSnapshots.SCHEMA_VERSION + ", "
                        + DesktopSchema.ContextSnapshots.DIGEST + ", "
                        + DesktopSchema.ContextSnapshots.PAYLOAD + ", "
                        + DesktopSchema.ContextSnapshots.CREATED_AT + ") VALUES (?, ?, 1, 'deadbeef', ?, ?)",
                s -> {
                    s.setString(1, Guard.newId());
                    s.setString(2, created.rootBranch.id);
                    s.setString(3, payload);
                    s.setLong(4, T0);
                });

        assertThrows(StorageException.class, () -> snapshots.findForBranch(created.rootBranch.id),
                "a snapshot whose payload does not hash to its digest must stop the read");
    }

    @Test
    void anArtifactRoundTripsAndASecondInsertForThePairIsRefused() {
        String branchId = topics.create(T0).rootBranch.id;
        ArtifactStore.Artifact stored = artifacts.insert(ArtifactStore.Artifact.create(
                "branch_summary", branchId, PromptPurpose.BRANCH_SUMMARY, "tl-branch-summary/1.0.0",
                "{\"summary\":\"s\"}", null, "digest-1", T0));

        ArtifactStore.Artifact read = artifacts.find("branch_summary", branchId).orElseThrow();
        assertEquals(stored.id, read.id);
        assertEquals("digest-1", read.inputDigest);
        assertEquals(PromptPurpose.BRANCH_SUMMARY, read.purpose);

        assertThrows(StorageException.class, () -> artifacts.insert(ArtifactStore.Artifact.create(
                "branch_summary", branchId, PromptPurpose.BRANCH_SUMMARY, "tl-branch-summary/1.0.0",
                "{}", null, "digest-2", T0 + 1)),
                "one current artifact per (kind, scope) is the rule the CAS depends on");
    }

    @Test
    void aStaleInputDoesNotMasqueradeAsTheCurrentResult() {
        String branchId = topics.create(T0).rootBranch.id;
        artifacts.insert(ArtifactStore.Artifact.create("branch_summary", branchId,
                PromptPurpose.BRANCH_SUMMARY, "tl-branch-summary/1.0.0", "{\"summary\":\"old\"}",
                null, "input-1", T0));

        assertTrue(artifacts.findFresh("branch_summary", branchId, "input-1").isPresent());
        assertFalse(artifacts.findFresh("branch_summary", branchId, "input-2").isPresent(),
                "a result derived from different input must not be served for the new baseline");
    }

    @Test
    void applyIfBaselineMatchesUpdatesInPlaceAndDropsAStaleWrite() {
        TopicStore.Created created = topics.create(T0);
        Message answer = messages.appendAiPosition(created.rootBranch.id, 0, T0);
        AnswerRevision revision = revisions.insert(answer.id, "", "answer", GenerationModel.FLASH,
                false, LEARNING_ANSWER_POLICY, T0);
        String branchId = created.rootBranch.id;
        ArtifactStore.Artifact stored = artifacts.insert(ArtifactStore.Artifact.create(
                "branch_summary", branchId, PromptPurpose.BRANCH_SUMMARY, "tl-branch-summary/1.0.0",
                "{\"summary\":\"p1\"}", revision.id, "digest-1", T0));

        assertEquals(ArtifactStore.Writeback.APPLIED, artifacts.applyIfBaselineMatches(
                ArtifactStore.Artifact.create("branch_summary", branchId, PromptPurpose.BRANCH_SUMMARY,
                        "tl-branch-summary/1.0.0", "{\"summary\":\"p2\"}", revision.id, "digest-1",
                        T0 + 1)),
                "the same baseline is rewritten in place");
        ArtifactStore.Artifact updated = artifacts.find("branch_summary", branchId).orElseThrow();
        assertEquals("{\"summary\":\"p2\"}", updated.payload);
        assertEquals(stored.id, updated.id);

        assertEquals(ArtifactStore.Writeback.DISCARDED_STALE, artifacts.applyIfBaselineMatches(
                ArtifactStore.Artifact.create("branch_summary", branchId, PromptPurpose.BRANCH_SUMMARY,
                        "tl-branch-summary/1.0.0", "{\"summary\":\"p3\"}", revision.id, "digest-2",
                        T0 + 2)),
                "a moved-baseline write-back is dropped");
        assertEquals("{\"summary\":\"p2\"}",
                artifacts.find("branch_summary", branchId).orElseThrow().payload,
                "a stale write-back must leave the stored payload untouched");
    }

    @Test
    void replaceSwapsTheStoredArtifactAndDeleteCleansTheScope() {
        String branchId = topics.create(T0).rootBranch.id;
        artifacts.insert(ArtifactStore.Artifact.create("branch_summary", branchId,
                PromptPurpose.BRANCH_SUMMARY, "tl-branch-summary/1.0.0", "{\"summary\":\"p1\"}",
                null, "digest-1", T0));

        ArtifactStore.Artifact replacement = artifacts.replace(ArtifactStore.Artifact.create(
                "branch_summary", branchId, PromptPurpose.BRANCH_SUMMARY, "tl-branch-summary/1.0.0",
                "{\"summary\":\"p2\"}", null, "digest-2", T0 + 1));

        assertEquals(1, artifacts.listForScope(branchId).size());
        assertEquals(replacement.id, artifacts.find("branch_summary", branchId).orElseThrow().id);
        assertEquals(1, artifacts.deleteForScope("branch_summary", branchId));
        assertFalse(artifacts.find("branch_summary", branchId).isPresent());
    }
}
