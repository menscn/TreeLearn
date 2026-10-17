package com.treelearn.app.generation;

import com.treelearn.ai.prompt.DeepSeekRequest;
import com.treelearn.core.domain.GenerationCommand;
import com.treelearn.core.domain.GenerationOperation;

/**
 * One attempt the coordinator is driving: the immutable command, the attempt itself, and the
 * scheduling cache around them.
 *
 * <p>No Android type appears here, which is what lets the registry below be exercised without a
 * device. The fields are filled in as the attempt progresses: {@code persisted} says the store
 * already carries the row, {@code streaming} says the first delta was reported, and {@code stream}
 * is the handle of the open call.
 */
final class LiveAttempt {

    final GenerationCommand command;
    final GenerationOperation operation;
    final DeepSeekRequest request;
    final GenerationCoordinator.LocalWrite localWrite;
    final StreamBatch batch = new StreamBatch();
    GenerationGateway.Stream stream;
    boolean persisted;
    boolean streaming;

    LiveAttempt(GenerationCommand command, GenerationOperation operation, DeepSeekRequest request,
            GenerationCoordinator.LocalWrite localWrite) {
        this.command = command;
        this.operation = operation;
        this.request = request;
        this.localWrite = localWrite;
    }
}
