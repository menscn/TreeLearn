package com.treelearn.app.wiring;

import com.treelearn.app.generation.GenerationCoordinator;
import com.treelearn.core.domain.GenerationCommand;

/**
 * The two ways this app admits a generation attempt, without the Android types the coordinator needs.
 *
 * <p>The coordinator takes a {@code TreeLearnDatabaseHelper}, so it cannot be constructed on a plain
 * JVM and a test of the paths above it could not run. This port is that seam and nothing else: it
 * decides nothing, and its production implementation forwards to the coordinator method of the same
 * name, so no rule is stated twice.
 */
public interface GenerationPort {

    default void addObserver(GenerationCoordinator.Observer observer) {
    }

    default void removeObserver(GenerationCoordinator.Observer observer) {
    }

    /** Admits an attempt for a position that already exists. */
    GenerationCoordinator.SubmitOutcome submit(GenerationCommand command,
            GenerationCoordinator.Assembler assembler);

    /**
     * Admits the first topic's attempt, where no row may exist before the provider accepted the
     * request: {@code acceptedWrite} runs inside the accepted callback.
     */
    GenerationCoordinator.SubmitOutcome submitFirstTopic(GenerationCommand command,
            GenerationCoordinator.Assembler assembler, GenerationCoordinator.AcceptedWrite acceptedWrite);
}
