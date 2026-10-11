package com.treelearn.core.domain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import org.junit.Test;

/**
 * One generation attempt (technical design §5.1, §7, §9.3).
 *
 * <p>The attempt is the only mutable thing in this package: it carries two appendable buffers while
 * the stream is open. Every test here exists to keep that mutability from leaking into the immutable
 * contract — the buffers must never be handed out as a stable version, the pinned policy version must
 * never move, and a terminal attempt must never accept another write.
 */
public class GenerationOperationTest {

    private static final String BRANCH = "11111111-1111-4111-8111-111111111111";
    private static final String MESSAGE = "22222222-2222-4222-8222-222222222222";
    private static final String LEARNING = "tl-learning-answer/1.0.0";
    private static final long T0 = 1_700_000_000_000L;

    private static GenerationCommand aCommand() {
        return GenerationCommand.create(
                BRANCH, MESSAGE, PromptPurpose.LEARNING_ANSWER, LEARNING,
                GenerationModel.PRO, true, T0).value();
    }

    private static GenerationOperation aStartedOperation() {
        return GenerationOperation.start(aCommand(), T0 + 1);
    }

    /** Walks the attempt to a normal end with a non-empty answer. */
    private static GenerationOperation streamedToAnswer(GenerationOperation operation) {
        operation.advanceTo(GenerationState.VALIDATING);
        operation.advanceTo(GenerationState.SUBMITTING);
        operation.appendReasoning("let me think");
        operation.appendAnswer("the answer");
        return operation;
    }

    @Test
    public void anOperationInheritsThePinnedVersionAndIdentityFromItsCommand() {
        GenerationCommand command = aCommand();
        GenerationOperation operation = GenerationOperation.start(command, T0 + 5);
        assertEquals(command.id, operation.commandId);
        assertEquals(BRANCH, operation.branchId);
        assertEquals(MESSAGE, operation.messageId);
        assertEquals(PromptPurpose.LEARNING_ANSWER, operation.purpose);
        assertEquals(LEARNING, operation.promptPolicyVersion);
        assertEquals(GenerationModel.PRO, operation.model);
        assertTrue(operation.thinkingEnabled);
        assertEquals(T0 + 5, operation.createdAt);
        assertEquals(GenerationState.STAGED, operation.state());
        assertEquals("", operation.partialReasoning());
        assertEquals("", operation.partialAnswer());
        assertFalse(operation.hasPartialContent());
    }

    /**
     * Criterion 2 as a mechanical check rather than a promise: the pin is a {@code final} field and
     * the type exposes no setter at all, so no future call site can rewrite it.
     */
    @Test
    public void thePinnedVersionCannotBeRewrittenForTheLifeOfTheOperation() throws Exception {
        Field field = GenerationOperation.class.getField("promptPolicyVersion");
        assertTrue("promptPolicyVersion must be final", Modifier.isFinal(field.getModifiers()));
        for (Method method : GenerationOperation.class.getDeclaredMethods()) {
            assertFalse("no setter may exist on an attempt: " + method.getName(),
                    method.getName().toLowerCase().startsWith("set"));
        }
        GenerationOperation operation = streamedToAnswer(aStartedOperation());
        assertEquals("transitions and appends must leave the pin untouched",
                LEARNING, operation.promptPolicyVersion);
    }

    @Test
    public void aReasoningDeltaOpensTheReasoningPhase() {
        GenerationOperation operation = aStartedOperation();
        operation.advanceTo(GenerationState.VALIDATING);
        operation.advanceTo(GenerationState.SUBMITTING);
        DomainResult<GenerationState> result = operation.appendReasoning("first");
        assertTrue("a delta arriving in SUBMITTING must open the reasoning phase: " + result,
                result.isOk());
        assertEquals(GenerationState.STREAMING_REASONING, operation.state());
        assertEquals("first", operation.partialReasoning());
        assertTrue(operation.hasPartialContent());
    }

    /** §9.1's {@code thinking.disabled} shape: the very first delta is an answer delta. */
    @Test
    public void anAnswerDeltaMovesSubmittingStraightIntoTheAnswerPhase() {
        GenerationOperation operation = aStartedOperation();
        operation.advanceTo(GenerationState.VALIDATING);
        operation.advanceTo(GenerationState.SUBMITTING);
        assertTrue(operation.appendAnswer("answer only").isOk());
        assertEquals(GenerationState.STREAMING_ANSWER, operation.state());
        assertEquals("", operation.partialReasoning());
    }

    /**
     * Criterion 5. §9.3 does not promise that the two fields are framed together, so a
     * {@code reasoning_content} delta can legitimately arrive after answer deltas have started. It
     * must be appended, and it must not be mistaken for a step back into the reasoning phase.
     */
    @Test
    public void aLateReasoningDeltaIsAppendedWithoutSteppingBack() {
        GenerationOperation operation = aStartedOperation();
        operation.advanceTo(GenerationState.VALIDATING);
        operation.advanceTo(GenerationState.SUBMITTING);
        operation.appendAnswer("part one ");
        assertEquals(GenerationState.STREAMING_ANSWER, operation.state());
        DomainResult<GenerationState> result = operation.appendReasoning("late thought");
        assertTrue("a late reasoning delta must be accepted: " + result, result.isOk());
        assertEquals("the state must stay in the answer phase",
                GenerationState.STREAMING_ANSWER, operation.state());
        assertEquals("late thought", operation.partialReasoning());
        assertTrue(operation.appendAnswer("part two").isOk());
        assertEquals("part one part two", operation.partialAnswer());
        assertEquals(GenerationState.STREAMING_ANSWER, operation.state());
    }

    @Test
    public void reachingStableDirectlyIsRefusedBecauseStabilizeOwnsThatMove() {
        GenerationOperation operation = streamedToAnswer(aStartedOperation());
        DomainResult<GenerationState> result = operation.advanceTo(GenerationState.STABLE);
        assertTrue("STABLE must not be reachable through a plain transition", result.isFailure());
        assertEquals(DomainErrorCode.ILLEGAL_STATE_TRANSITION, result.error().code);
        assertEquals("a refused transition must not move the attempt",
                GenerationState.STREAMING_ANSWER, operation.state());
    }

    @Test
    public void illegalTransitionsAreReportedAsDomainErrors() {
        GenerationOperation operation = aStartedOperation();
        assertEquals(DomainErrorCode.ILLEGAL_STATE_TRANSITION,
                operation.advanceTo(GenerationState.SUBMITTING).error().code);
        assertEquals(GenerationState.STAGED, operation.state());
        operation.advanceTo(GenerationState.VALIDATING);
        operation.advanceTo(GenerationState.SUBMITTING);
        operation.appendAnswer("done");
        assertEquals("moving backwards must be refused",
                DomainErrorCode.ILLEGAL_STATE_TRANSITION,
                operation.advanceTo(GenerationState.STREAMING_REASONING).error().code);
    }

    @Test
    public void aNullTransitionTargetIsACallerBugNotABusinessFailure() {
        try {
            aStartedOperation().advanceTo(null);
            fail("a null target must throw rather than report a business refusal");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("must not be null"));
        }
    }

    @Test
    public void aDeltaThatArrivedBeforeStreamingIsRefused() {
        GenerationOperation operation = aStartedOperation();
        assertEquals("a delta while still STAGED is out of order",
                DomainErrorCode.ILLEGAL_STATE_TRANSITION,
                operation.appendAnswer("too early").error().code);
        assertEquals("", operation.partialAnswer());
    }

    @Test
    public void stabilizeRequiresANonEmptyAnswer() {
        GenerationOperation operation = aStartedOperation();
        operation.advanceTo(GenerationState.VALIDATING);
        operation.advanceTo(GenerationState.SUBMITTING);
        operation.appendReasoning("thinking, but the answer never arrived");
        DomainResult<AnswerRevision> result = operation.stabilize(1, T0 + 10);
        assertTrue("an attempt without a final answer must not stabilize", result.isFailure());
        assertEquals(DomainErrorCode.OPERATION_NOT_COMPLETABLE, result.error().code);
        assertEquals("a refused stabilization must be atomic: no intermediate STABLE",
                GenerationState.STREAMING_REASONING, operation.state());
        assertTrue("whitespace alone is not an answer",
                operation.appendAnswer("   ").isOk());
        assertEquals(DomainErrorCode.OPERATION_NOT_COMPLETABLE,
                operation.stabilize(1, T0 + 10).error().code);
        assertEquals(GenerationState.STREAMING_ANSWER, operation.state());
    }

    @Test
    public void stabilizeCopiesTheBuffersAndThePinnedVersionVerbatim() {
        GenerationOperation operation = aStartedOperation();
        operation.advanceTo(GenerationState.VALIDATING);
        operation.advanceTo(GenerationState.SUBMITTING);
        operation.appendReasoning("  reasoning with  padding\n");
        operation.appendAnswer("  final answer  ");
        DomainResult<AnswerRevision> result = operation.stabilize(3, T0 + 20);
        assertTrue("a normally finished attempt must stabilize: " + result, result.isOk());
        AnswerRevision revision = result.value();
        assertEquals(MESSAGE, revision.messageId);
        assertEquals(3, revision.revisionNo);
        assertEquals("reasoning must be copied byte for byte, not tidied",
                operation.partialReasoning(), revision.reasoningContent);
        assertEquals("the answer must be copied byte for byte, not tidied",
                "  final answer  ", revision.answerContent);
        assertEquals("the version must reach the stable revision unchanged",
                LEARNING, revision.promptPolicyVersion);
        assertEquals(GenerationModel.PRO, revision.model);
        assertTrue(revision.thinkingEnabled);
        assertEquals(T0 + 20, revision.createdAt);
        assertEquals(GenerationState.STABLE, operation.state());
    }

    @Test
    public void aThinkingDisabledAttemptStabilizesWithEmptyReasoning() {
        GenerationOperation operation = aStartedOperation();
        operation.advanceTo(GenerationState.VALIDATING);
        operation.advanceTo(GenerationState.SUBMITTING);
        operation.appendAnswer("answer without reasoning");
        AnswerRevision revision = operation.stabilize(1, T0 + 30).value();
        assertEquals("", revision.reasoningContent);
        assertFalse(revision.hasReasoning());
        assertEquals("answer without reasoning", revision.answerContent);
    }

    @Test
    public void aSecondStabilizationIsRefused() {
        GenerationOperation operation = streamedToAnswer(aStartedOperation());
        assertTrue(operation.stabilize(1, T0 + 40).isOk());
        DomainResult<AnswerRevision> again = operation.stabilize(2, T0 + 41);
        assertTrue("an attempt stabilizes exactly once", again.isFailure());
        assertEquals(DomainErrorCode.ILLEGAL_STATE_TRANSITION, again.error().code);
    }

    /**
     * Criterion 4. A failure or an interruption keeps the partial content readable — the user
     * retries in place and still sees what arrived — but nothing may turn that content into history.
     */
    @Test
    public void aFailedAttemptRejectsLateCallbacksAndCannotBeStabilized() {
        GenerationOperation operation = aStartedOperation();
        operation.advanceTo(GenerationState.VALIDATING);
        operation.advanceTo(GenerationState.SUBMITTING);
        operation.appendReasoning("half a thought");
        operation.appendAnswer("half an ans");
        assertTrue(operation.advanceTo(GenerationState.FAILED).isOk());

        assertEquals("partial content must stay readable for an in-place retry",
                "half a thought", operation.partialReasoning());
        assertEquals("half an ans", operation.partialAnswer());

        DomainResult<GenerationState> lateDelta = operation.appendAnswer("wer");
        assertTrue("a late delta must be rejected", lateDelta.isFailure());
        assertEquals(DomainErrorCode.ILLEGAL_STATE_TRANSITION, lateDelta.error().code);
        DomainResult<AnswerRevision> lateStabilize = operation.stabilize(1, T0 + 50);
        assertTrue("partial content must never become a stable version",
                lateStabilize.isFailure());
        assertEquals(DomainErrorCode.ILLEGAL_STATE_TRANSITION, lateStabilize.error().code);
        assertEquals("the late writes must not have changed anything",
                "half an ans", operation.partialAnswer());
        assertEquals(GenerationState.FAILED, operation.state());
    }

    @Test
    public void anInterruptedAttemptBehavesLikeAFailedOne() {
        GenerationOperation operation = aStartedOperation();
        operation.advanceTo(GenerationState.VALIDATING);
        operation.advanceTo(GenerationState.SUBMITTING);
        operation.appendAnswer("cut off mid");
        assertTrue(operation.advanceTo(GenerationState.INTERRUPTED).isOk());
        assertEquals("cut off mid", operation.partialAnswer());
        assertEquals(DomainErrorCode.ILLEGAL_STATE_TRANSITION,
                operation.appendReasoning("late").error().code);
        assertEquals(DomainErrorCode.ILLEGAL_STATE_TRANSITION,
                operation.stabilize(1, T0 + 60).error().code);
    }

    @Test
    public void anEmptyDeltaIsAcceptedWithoutOpeningAChannel() {
        GenerationOperation operation = aStartedOperation();
        operation.advanceTo(GenerationState.VALIDATING);
        operation.advanceTo(GenerationState.SUBMITTING);
        assertTrue("an empty delta is well formed and must not be an error",
                operation.appendReasoning("").isOk());
        assertTrue("an empty delta must not be treated as a reasoning phase opening",
                operation.appendAnswer("").isOk());
        assertEquals(GenerationState.SUBMITTING, operation.state());
        assertEquals("", operation.partialReasoning());
        assertEquals("", operation.partialAnswer());
        assertFalse(operation.hasPartialContent());
    }

    @Test
    public void aNullDeltaIsACallerBug() {
        GenerationOperation operation = streamedToAnswer(aStartedOperation());
        try {
            operation.appendAnswer(null);
            fail("a null delta must throw");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("delta"));
        }
    }

    @Test
    public void aBadRevisionNumberIsRefusedByTheRevisionItself() {
        GenerationOperation operation = streamedToAnswer(aStartedOperation());
        try {
            operation.stabilize(0, T0 + 70);
            fail("revision numbers start at 1");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("revisionNo"));
        }
        assertEquals("a rejected revision number must leave the attempt in place",
                GenerationState.STREAMING_ANSWER, operation.state());
    }

    /**
     * The declared check order of the package, applied to this entry point: argument shapes are
     * reported before the domain decision, so a call that carries <em>both</em> a malformed argument
     * and a state that forbids the operation still surfaces the caller bug.
     *
     * <p>Without this order a malformed {@code revisionNo} would be answered with
     * {@code ILLEGAL_STATE_TRANSITION}, which tells the caller "your sequence number may be fine, the
     * attempt is just finished" — and the caller would keep the bug. A round-1-independent audit of
     * T-021 found the same shadowing in {@code GenerationCommand.create}.
     */
    @Test
    public void aCallerBugIsReportedBeforeTheAttemptStateIsConsulted() {
        GenerationOperation operation = aStartedOperation();
        operation.advanceTo(GenerationState.VALIDATING);
        operation.advanceTo(GenerationState.SUBMITTING);
        operation.appendAnswer("cut off mid");
        assertTrue(operation.advanceTo(GenerationState.INTERRUPTED).isOk());

        try {
            operation.stabilize(0, T0 + 71);
            fail("a malformed revision number must throw even on a terminal attempt");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("revisionNo"));
        }
        try {
            operation.stabilize(1, -1L);
            fail("a negative completion time must throw even on a terminal attempt");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("createdAt"));
        }
        assertEquals("the refused calls must not have touched the attempt",
                GenerationState.INTERRUPTED, operation.state());
    }

    @Test
    public void theOperationDescriptionNamesTheStateAndTheBuffers() {
        GenerationOperation operation = streamedToAnswer(aStartedOperation());
        String description = operation.toString();
        assertNotNull(description);
        assertTrue("the description must name the state: " + description,
                description.contains("STREAMING_ANSWER"));
        assertTrue("the description must show how much has arrived: " + description,
                description.contains("answer=" + operation.partialAnswer().length()));
        assertFalse("the description must not leak conversation content: " + description,
                description.contains("the answer"));
        assertFalse(description.contains("let me think"));
    }
}
