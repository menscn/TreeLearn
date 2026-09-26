/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.treelearn.core.domain.GenerationCommand
 */
package com.treelearn.app.wiring;

import com.treelearn.app.generation.GenerationCoordinator;
import com.treelearn.app.wiring.GenerationPort;
import com.treelearn.core.domain.GenerationCommand;

public final class CoordinatorGenerationPort
implements GenerationPort {
    private final GenerationCoordinator coordinator;

    public CoordinatorGenerationPort(GenerationCoordinator coordinator) {
        if (coordinator == null) {
            throw new IllegalArgumentException("coordinator must not be null");
        }
        this.coordinator = coordinator;
    }

    @Override
    public GenerationCoordinator.SubmitOutcome submit(GenerationCommand command, GenerationCoordinator.Assembler assembler) {
        return this.coordinator.submit(command, assembler);
    }

    @Override
    public GenerationCoordinator.SubmitOutcome submitFirstTopic(GenerationCommand command, GenerationCoordinator.Assembler assembler, GenerationCoordinator.AcceptedWrite acceptedWrite) {
        return this.coordinator.submitFirstTopic(command, assembler, acceptedWrite);
    }

    @Override
    public void addObserver(GenerationCoordinator.Observer observer) {
        this.coordinator.addObserver(observer);
    }

    @Override
    public void removeObserver(GenerationCoordinator.Observer observer) {
        this.coordinator.removeObserver(observer);
    }
}

