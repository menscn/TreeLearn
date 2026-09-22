package com.treelearn.app.generation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.treelearn.core.domain.GenerationState;

import org.junit.Test;

/**
 * T-008 plain-JVM evidence for the recovery decision of technical design 7.
 *
 * <p>The mapping itself stays the domain's ({@link GenerationState#recoveryTarget()}); what this
 * suite pins is the coordinator's half: every state the startup scan is handed ends up terminal and
 * reportable, and a row that should already have been reaped is refused instead of being given an
 * invented outcome. Applying the decision inside one transaction needs SQLite, so that half stays in
 * the instrumentation source and is exercised compile-only this round.
 */
public final class RecoveryScanTest {

    @Test
    public void aLeftoverThatNeverReachedTheProviderIsCanceledAndTheRestAreInterrupted() {
        assertEquals("nothing was sent, so there is nothing to interrupt",
                GenerationState.CANCELED, RecoveryScan.terminalFor(GenerationState.STAGED));
        assertEquals(GenerationState.INTERRUPTED,
                RecoveryScan.terminalFor(GenerationState.VALIDATING));
        assertEquals(GenerationState.INTERRUPTED,
                RecoveryScan.terminalFor(GenerationState.SUBMITTING));
        assertEquals(GenerationState.INTERRUPTED,
                RecoveryScan.terminalFor(GenerationState.STREAMING_REASONING));
        assertEquals("partially streamed content is kept for an in-place retry",
                GenerationState.INTERRUPTED,
                RecoveryScan.terminalFor(GenerationState.STREAMING_ANSWER));
    }

    @Test
    public void everyStateTheScanIsHandedBecomesTerminalAndReportable() {
        for (GenerationState leftover : GenerationState.processRecoverableStates()) {
            GenerationState outcome = RecoveryScan.terminalFor(leftover);
            assertTrue(leftover + " must not survive the scan as an active state",
                    outcome.isTerminal());
            GenerationTelemetry.Event event = RecoveryScan.eventFor(outcome);
            switch (outcome) {
                case CANCELED:
                    assertEquals(GenerationTelemetry.Event.CANCELED, event);
                    break;
                case INTERRUPTED:
                    assertEquals(GenerationTelemetry.Event.INTERRUPTED, event);
                    break;
                default:
                    throw new AssertionError("recovery produced " + outcome);
            }
        }
    }

    @Test
    public void aRowThatShouldAlreadyHaveBeenReapedIsRefusedNotRelabelled() {
        assertThrows(IllegalStateException.class,
                () -> RecoveryScan.terminalFor(GenerationState.STABLE));
        assertThrows(IllegalStateException.class,
                () -> RecoveryScan.terminalFor(GenerationState.FAILED));
        assertThrows("the scan reports only the two outcomes it produces",
                IllegalArgumentException.class,
                () -> RecoveryScan.eventFor(GenerationState.STABLE));
    }
}
