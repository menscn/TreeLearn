package com.treelearn.app.generation;

import com.treelearn.core.domain.GenerationState;

/**
 * The recovery decision of 7, split from the transaction that applies it.
 *
 * <p>The mapping from a leftover attempt to its outcome stays the domain's
 * ({@link GenerationState#recoveryTarget()}): an attempt that reached the provider becomes
 * {@link GenerationState#INTERRUPTED} and keeps its partial content for an in-place retry, while one
 * that never left {@link GenerationState#STAGED} becomes {@link GenerationState#CANCELED}. What this
 * class adds is the redacted event each outcome is reported as, and a refusal to invent a target for
 * anything the store should never have handed over.
 */
final class RecoveryScan {

    /** @return the terminal state a leftover attempt becomes */
    static GenerationState terminalFor(GenerationState leftover) {
        return leftover.recoveryTarget();
    }

    /** @return the redacted event that reports this outcome */
    static GenerationTelemetry.Event eventFor(GenerationState terminal) {
        switch (terminal) {
            case CANCELED:
                return GenerationTelemetry.Event.CANCELED;
            case INTERRUPTED:
                return GenerationTelemetry.Event.INTERRUPTED;
            default:
                throw new IllegalArgumentException(
                        "recovery only ever produces CANCELED or INTERRUPTED, not " + terminal);
        }
    }
}
