/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.treelearn.core.domain.GenerationState
 */
package com.treelearn.app.generation;

import com.treelearn.app.generation.GenerationTelemetry;
import com.treelearn.core.domain.GenerationState;

final class RecoveryScan {
    RecoveryScan() {
    }

    static GenerationState terminalFor(GenerationState leftover) {
        return leftover.recoveryTarget();
    }

    static GenerationTelemetry.Event eventFor(GenerationState terminal) {
        switch (terminal) {
            case CANCELED: {
                return GenerationTelemetry.Event.CANCELED;
            }
            case INTERRUPTED: {
                return GenerationTelemetry.Event.INTERRUPTED;
            }
        }
        throw new IllegalArgumentException("recovery only ever produces CANCELED or INTERRUPTED, not " + terminal);
    }
}

