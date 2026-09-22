package com.treelearn.app.wiring;

import com.treelearn.app.generation.GenerationCoordinator;
import com.treelearn.core.domain.GenerationCommand;

/** The production {@link GenerationPort}: it forwards, and it decides nothing. */
public final class CoordinatorGenerationPort implements GenerationPort {

    private final GenerationCoordinator coordinator;

    public CoordinatorGenerationPort(GenerationCoordinator coordinator) {
        if (coordinator == null) {
            throw new IllegalArgumentException("coordinator must not be null");
        }
        this.coordinator = coordinator;
    }

    @Override
    public GenerationCoordinator.SubmitOutcome submit(GenerationCommand command,
            GenerationCoordinator.Assembler assembler) {
        return coordinator.submit(command, assembler);
    }

    @Override
    public GenerationCoordinator.SubmitOutcome submitFirstTopic(GenerationCommand command,
            GenerationCoordinator.Assembler assembler,
            GenerationCoordinator.AcceptedWrite acceptedWrite) {
        return coordinator.submitFirstTopic(command, assembler, acceptedWrite);
    }

    @Override
    public void addObserver(GenerationCoordinator.Observer observer) {
        coordinator.addObserver(observer);
    }

    @Override
    public void removeObserver(GenerationCoordinator.Observer observer) {
        coordinator.removeObserver(observer);
    }
}
