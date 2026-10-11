package com.treelearn.web.storage;

import com.treelearn.core.domain.GenerationState;
import com.treelearn.core.domain.Guard;
import java.time.Clock;

/**
 * Settles what a dead process left behind (task T-2026-10-10-011; technical design §7).
 *
 * <p>Semantic reference — Android {@code GenerationCoordinator.recover()}: every leftover in-flight
 * attempt is moved to its recovery target and nothing is resent. The set of states that cannot survive
 * a restart comes from {@link GenerationState#processRecoverableStates()} and the target from
 * {@link GenerationState#recoveryTarget()}, so a state added to the lifecycle is reaped by the
 * domain's own rule rather than by a list written here. The partial buffers are kept, which is what an
 * in-place retry reads, and {@code updated_at} is stamped with the injected {@link Clock} (UTC).
 *
 * <p>{@link #sweep()} is idempotent: a source that already ended is not in the active set, so a second
 * run finds nothing to do. It is meant to run once, after migration and before the service is ready —
 * a failure propagates and the service refuses to start rather than serving unsettled rows.
 */
public final class InterruptionSweeper {

    private final OperationStore operations;
    private final Clock clock;

    public InterruptionSweeper(OperationStore operations, Clock clock) {
        this.operations = Guard.object(operations, "operations");
        this.clock = Guard.object(clock, "clock");
    }

    /**
     * Moves every still-active attempt to its recovery target, keeping its partial content.
     *
     * @return how many rows were settled
     */
    public int sweep() {
        long now = clock.millis();
        int settled = 0;
        for (OperationStore.Operation operation : operations.listActive()) {
            GenerationState target = operation.state.recoveryTarget();
            operations.markTerminal(operation.id, target, null, now);
            settled++;
        }
        return settled;
    }
}
