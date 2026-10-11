package com.treelearn.web.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.treelearn.core.domain.AnswerRevision;
import com.treelearn.core.domain.DomainErrorCode;
import com.treelearn.core.domain.DomainResult;
import com.treelearn.core.domain.GenerationState;
import com.treelearn.core.domain.Message;
import org.junit.jupiter.api.Test;

/**
 * T-2026-10-10-024: the desktop {@code GenerationStabilizer} on real sqlite-jdbc. One transaction
 * writes the immutable revision, moves the attempt to {@code STABLE}, repoints the position and advances
 * the branch's stable sequence, or none of them; the empty-answer and non-streaming refusals are the
 * domain's, and a late second stabilization rewrites nothing.
 */
class GenerationStabilizerTest extends AbstractStoreTest {

    private GenerationStabilizer stabilizer() {
        return new GenerationStabilizer(jdbc, operations, revisions, messages, branches);
    }

    private OperationStore.Operation streaming(String reasoning, String answer) {
        TopicStore.Created created = topics.create(T0);
        Message position = messages.appendAiPosition(created.rootBranch.id, 0, T0);
        OperationStore.Operation running = startOperation(created.rootBranch.id, position.id, T0);
        operations.updateProgress(running.id, reasoning, answer, GenerationState.STREAMING_ANSWER,
                T0 + 1);
        return running;
    }

    @Test
    void stabilizeWritesRevisionActiveReferenceAndSequenceTogether() {
        OperationStore.Operation running = streaming("推理正文", "最终回答");

        DomainResult<AnswerRevision> stabilized = stabilizer().stabilize(running.id, T0 + 2);

        assertTrue(stabilized.isOk(), "a normally finished, non-empty attempt must stabilize");
        AnswerRevision revision = stabilized.value();
        assertEquals(1, revision.revisionNo);
        assertEquals("最终回答", revision.answerContent);
        assertEquals("推理正文", revision.reasoningContent);
        assertEquals(LEARNING_ANSWER_POLICY, revision.promptPolicyVersion,
                "the pinned policy version is copied from the stored row");

        assertEquals(GenerationState.STABLE, operations.byId(running.id).orElseThrow().state);
        assertEquals(revision.id, messages.get(running.messageId).orElseThrow().activeRevisionId(),
                "the position now displays the new revision");
        assertEquals(revision.id, revisions.findActiveForMessage(running.messageId).orElseThrow().id);
        assertEquals(1L, branches.get(running.branchId).orElseThrow().latestStableSequence(),
                "the branch's stable sequence advanced so the unread gap counts the answer");
        assertEquals(1, revisions.countForMessage(running.messageId));
    }

    @Test
    void aSecondStabilizationOfTheSamePositionGetsTheNextRevisionNumber() {
        OperationStore.Operation first = streaming("", "第一版");
        stabilizer().stabilize(first.id, T0 + 2);

        // A new attempt at the same position is what a reset produces; the first is terminal, so the
        // partial index admits it. The stabilizer must number the revision one above the stored maximum.
        OperationStore.Operation second = startOperation(first.branchId, first.messageId, T0 + 3);
        operations.updateProgress(second.id, "", "第二版", GenerationState.STREAMING_ANSWER, T0 + 4);

        DomainResult<AnswerRevision> stabilized = stabilizer().stabilize(second.id, T0 + 5);

        assertTrue(stabilized.isOk());
        assertEquals(2, stabilized.value().revisionNo);
        assertEquals(2, revisions.countForMessage(first.messageId));
        assertEquals(stabilized.value().id,
                messages.get(first.messageId).orElseThrow().activeRevisionId());
        assertEquals(2L, branches.get(first.branchId).orElseThrow().latestStableSequence());
    }

    @Test
    void stabilizeRefusesAnEmptyAnswerAndKeepsTheAttemptRetryable() {
        OperationStore.Operation reasoningOnly = streaming("只有推理", "");

        DomainResult<AnswerRevision> refused = stabilizer().stabilize(reasoningOnly.id, T0 + 2);

        assertTrue(refused.isFailure(), "§9.3 forbids freezing a reasoning-only stream");
        assertEquals(DomainErrorCode.OPERATION_NOT_COMPLETABLE, refused.error().code);
        assertEquals(0, revisions.countForMessage(reasoningOnly.messageId), "no revision may be written");
        assertEquals(GenerationState.STREAMING_ANSWER, operations.byId(reasoningOnly.id).orElseThrow().state,
                "the attempt is left exactly as it was so it can be retried in place");
        assertNull(messages.get(reasoningOnly.messageId).orElseThrow().activeRevisionId(),
                "the position must still display no revision");
    }

    @Test
    void stabilizeRefusesAnAttemptThatNeverReachedTheAnswerPhase() {
        TopicStore.Created created = topics.create(T0);
        Message position = messages.appendAiPosition(created.rootBranch.id, 0, T0);
        OperationStore.Operation staged = startOperation(created.rootBranch.id, position.id, T0);

        DomainResult<AnswerRevision> refused = stabilizer().stabilize(staged.id, T0 + 2);

        assertTrue(refused.isFailure());
        assertEquals(DomainErrorCode.ILLEGAL_STATE_TRANSITION, refused.error().code);
        assertEquals(0, revisions.countForMessage(position.id));
    }

    @Test
    void aLateSecondStabilizationIsRefusedAndWritesNothing() {
        OperationStore.Operation running = streaming("", "回答");
        DomainResult<AnswerRevision> first = stabilizer().stabilize(running.id, T0 + 2);
        assertTrue(first.isOk());
        long sequenceAfterFirst = branches.get(running.branchId).orElseThrow().latestStableSequence();

        DomainResult<AnswerRevision> second = stabilizer().stabilize(running.id, T0 + 3);

        assertTrue(second.isFailure(), "an already-STABLE attempt accepts no second stabilization");
        assertEquals(DomainErrorCode.ILLEGAL_STATE_TRANSITION, second.error().code);
        assertEquals(1, revisions.countForMessage(running.messageId));
        assertEquals(sequenceAfterFirst,
                branches.get(running.branchId).orElseThrow().latestStableSequence(),
                "a refused stabilization must not advance the branch again");
    }
}
