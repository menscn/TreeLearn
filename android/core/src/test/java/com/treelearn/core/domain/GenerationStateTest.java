package com.treelearn.core.domain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

/**
 * The generation state machine (technical design 7). The two properties that matter are that an
 * illegal or late transition is reported as a domain error, and that nothing moves out of a
 * terminal state.
 */
public class GenerationStateTest {

    @Test
    public void happyPathTransitionsAreLegal() {
        assertTrue(GenerationState.STAGED.canTransitionTo(GenerationState.VALIDATING));
        assertTrue(GenerationState.VALIDATING.canTransitionTo(GenerationState.SUBMITTING));
        assertTrue(GenerationState.SUBMITTING.canTransitionTo(GenerationState.STREAMING_REASONING));
        assertTrue(GenerationState.STREAMING_REASONING.canTransitionTo(GenerationState.STREAMING_ANSWER));
        assertTrue(GenerationState.STREAMING_ANSWER.canTransitionTo(GenerationState.STABLE));
        assertTrue(GenerationState.SUBMITTING.canTransitionTo(GenerationState.STREAMING_ANSWER));
    }

    /**
     * Replaces one previous assertion.
     *
     * <p>Deleted: {@code assertTrue(STREAMING_REASONING.canTransitionTo(STABLE))}, whose comment
     * claimed "a model that returns the answer without a separate reasoning phase is legal too".
     * That shape is {@code SUBMITTING -> STREAMING_ANSWER}, asserted in
     * {@link #happyPathTransitionsAreLegal}; the edge the assertion actually covered had a
     * different meaning — a reasoning phase that ends without any answer deltas — and could not be
     * reached at all. Stabilizing requires a non-empty answer, and
     * {@code GenerationOperation.appendAnswer} is the only writer of answer deltas and moves the
     * attempt to {@code STREAMING_ANSWER} first. Equivalent behaviour coverage: the reachable
     * reasoning-free path ({@code SUBMITTING -> STREAMING_ANSWER -> STABLE}) is asserted here, and
     * the removal itself is locked down by the assertion below so the edge cannot silently return.
     */
    @Test
    public void aReasoningPhaseThatProducedNoAnswerCannotBecomeStable() {
        assertFalse(GenerationState.STREAMING_REASONING.canTransitionTo(GenerationState.STABLE));
        // The only legal way onward from a reasoning phase is into the answer phase.
        assertTrue(GenerationState.STREAMING_REASONING.canTransitionTo(GenerationState.STREAMING_ANSWER));
    }

    @Test
    public void anyUnfinishedStateCanFailBeInterruptedOrBeCanceled() {
        GenerationState[] unfinished = {
                GenerationState.STAGED,
                GenerationState.VALIDATING,
                GenerationState.SUBMITTING,
                GenerationState.STREAMING_REASONING,
                GenerationState.STREAMING_ANSWER,
        };
        for (GenerationState state : unfinished) {
            assertTrue(state + " -> FAILED", state.canTransitionTo(GenerationState.FAILED));
            assertTrue(state + " -> INTERRUPTED", state.canTransitionTo(GenerationState.INTERRUPTED));
            assertTrue(state + " -> CANCELED", state.canTransitionTo(GenerationState.CANCELED));
        }
    }

    @Test
    public void skippingForwardIsRefused() {
        assertFalse(GenerationState.STAGED.canTransitionTo(GenerationState.SUBMITTING));
        assertFalse(GenerationState.STAGED.canTransitionTo(GenerationState.STABLE));
        assertFalse(GenerationState.VALIDATING.canTransitionTo(GenerationState.STABLE));
        assertFalse(GenerationState.SUBMITTING.canTransitionTo(GenerationState.STABLE));
        assertFalse(GenerationState.STAGED.canTransitionTo(GenerationState.STREAMING_ANSWER));
    }

    @Test
    public void movingBackwardsIsRefused() {
        assertFalse(GenerationState.STREAMING_ANSWER.canTransitionTo(GenerationState.STREAMING_REASONING));
        assertFalse(GenerationState.SUBMITTING.canTransitionTo(GenerationState.VALIDATING));
        assertFalse(GenerationState.VALIDATING.canTransitionTo(GenerationState.STAGED));
    }

    @Test
    public void terminalStatesNeverChangeAgain() {
        GenerationState[] terminal = {
                GenerationState.STABLE,
                GenerationState.FAILED,
                GenerationState.INTERRUPTED,
                GenerationState.CANCELED,
        };
        for (GenerationState state : terminal) {
            assertTrue(state + " must be terminal", state.isTerminal());
            assertFalse(state + " must not be active", state.isActive());
            for (GenerationState next : GenerationState.values()) {
                assertFalse(state + " -> " + next + " must be refused",
                        state.canTransitionTo(next));
            }
        }
    }

    /**
     * Every derived predicate is asserted over <em>all</em> states, not a sample. A
     * round-2-independent audit of T-021 found {@code isStreaming()} had no consumer anywhere — not in
     * production, not in a test — so nothing would have failed if its meaning drifted; the runtime
     * consumer is T-008 (the coordinator suspends or resumes persistence per phase). The exhaustive
     * table below is what makes it a claim the repository can be held to.
     */
    @Test
    public void theDerivedPredicatesCoverEveryState() {
        Object[][] expected = {
                // state, active, terminal, stable, streaming, retryable
                { GenerationState.STAGED, true, false, false, false, false },
                { GenerationState.VALIDATING, true, false, false, false, false },
                { GenerationState.SUBMITTING, true, false, false, false, false },
                { GenerationState.STREAMING_REASONING, true, false, false, true, false },
                { GenerationState.STREAMING_ANSWER, true, false, false, true, false },
                { GenerationState.STABLE, false, true, true, false, false },
                { GenerationState.FAILED, false, true, false, false, true },
                { GenerationState.INTERRUPTED, false, true, false, false, true },
                { GenerationState.CANCELED, false, true, false, false, false },
        };
        assertEquals("every state must appear exactly once in this table: a new state needs a row",
                GenerationState.values().length, expected.length);
        for (Object[] row : expected) {
            GenerationState state = (GenerationState) row[0];
            assertEquals(state + ".isActive()", row[1], state.isActive());
            assertEquals(state + ".isTerminal()", row[2], state.isTerminal());
            assertEquals(state + ".isStable()", row[3], state.isStable());
            assertEquals(state + ".isStreaming()", row[4], state.isStreaming());
            assertEquals(state + ".isRetryable()", row[5], state.isRetryable());
        }
    }

    @Test
    public void illegalTransitionIsReportedAsADomainError() {
        DomainResult<GenerationState> result =
                GenerationState.STAGED.transitionTo(GenerationState.STABLE);
        assertTrue(result.isFailure());
        assertEquals(DomainErrorCode.ILLEGAL_STATE_TRANSITION, result.error().code);
        assertTrue(result.error().detail.contains("STAGED"));
    }

    @Test
    public void lateCallbackOnATerminalStateIsReportedAsADomainError() {
        DomainResult<GenerationState> result =
                GenerationState.STABLE.transitionTo(GenerationState.STREAMING_ANSWER);
        assertTrue(result.isFailure());
        assertEquals(DomainErrorCode.ILLEGAL_STATE_TRANSITION, result.error().code);
        assertTrue("the reason must say the state is terminal",
                result.error().detail.contains("terminal"));
    }

    @Test
    public void legalTransitionReturnsTheNextState() {
        DomainResult<GenerationState> result =
                GenerationState.SUBMITTING.transitionTo(GenerationState.STREAMING_REASONING);
        assertTrue(result.isOk());
        assertEquals(GenerationState.STREAMING_REASONING, result.value());
    }

    @Test
    public void aNullTransitionTargetIsACallerBugNotABusinessFailure() {
        // A round-4-independent audit found this returned ILLEGAL_STATE_TRANSITION for null while
        // BranchSource.fromOperation threw for the same class of mistake. Per the error-channel rule
        // in Guard, an omitted argument is a bug on the throwing channel.
        try {
            GenerationState.STAGED.transitionTo(null);
            fail("a null target must not be reported as a business refusal");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("target state"));
        }
    }

    @Test
    public void onlyStableCountsAsStable() {
        for (GenerationState state : GenerationState.values()) {
            assertEquals("isStable() for " + state, state == GenerationState.STABLE, state.isStable());
        }
    }

    @Test
    public void onlyFailedAndInterruptedAreRetryableInPlace() {
        for (GenerationState state : GenerationState.values()) {
            boolean expected = state == GenerationState.FAILED || state == GenerationState.INTERRUPTED;
            assertEquals("isRetryable() for " + state, expected, state.isRetryable());
        }
    }

    @Test
    public void processRecoveryCoversEveryStateThatCannotSurviveARestart() {
        GenerationState[] recoverable = GenerationState.processRecoverableStates();
        assertEquals("STAGED must be reaped too, or it blocks its branch forever",
                5, recoverable.length);
        for (GenerationState state : recoverable) {
            assertTrue(state + " must be active/unfinished", state.isActive());
        }
        assertFalse("STABLE must never be rewritten by recovery",
                contains(recoverable, GenerationState.STABLE));
        assertFalse("FAILED is already terminal", contains(recoverable, GenerationState.FAILED));
        assertFalse("INTERRUPTED is already terminal",
                contains(recoverable, GenerationState.INTERRUPTED));
        assertFalse("CANCELED is already terminal",
                contains(recoverable, GenerationState.CANCELED));
    }

    @Test
    public void everyRecoverableStateMapsToExactlyOneTerminalTarget() {
        assertEquals("nothing was ever sent, so there is nothing to interrupt",
                GenerationState.CANCELED, GenerationState.STAGED.recoveryTarget());
        for (GenerationState state : new GenerationState[] {
                GenerationState.VALIDATING,
                GenerationState.SUBMITTING,
                GenerationState.STREAMING_REASONING,
                GenerationState.STREAMING_ANSWER,
        }) {
            assertEquals(state + " reached the provider, so it is INTERRUPTED",
                    GenerationState.INTERRUPTED, state.recoveryTarget());
            assertTrue("recovery target must be reachable",
                    state.canTransitionTo(state.recoveryTarget()));
        }
    }

    @Test
    public void terminalStatesHaveNoRecoveryTarget() {
        for (GenerationState state : new GenerationState[] {
                GenerationState.STABLE,
                GenerationState.FAILED,
                GenerationState.INTERRUPTED,
                GenerationState.CANCELED,
        }) {
            try {
                state.recoveryTarget();
                org.junit.Assert.fail(state + " is terminal and must not be recovered");
            } catch (IllegalStateException expected) {
                assertTrue(expected.getMessage().contains("terminal"));
            }
        }
    }

    private static boolean contains(GenerationState[] states, GenerationState needle) {
        for (GenerationState state : states) {
            if (state == needle) {
                return true;
            }
        }
        return false;
    }
}
