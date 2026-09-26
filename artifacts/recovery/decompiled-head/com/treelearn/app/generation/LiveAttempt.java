/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.treelearn.ai.prompt.DeepSeekRequest
 *  com.treelearn.core.domain.GenerationCommand
 *  com.treelearn.core.domain.GenerationOperation
 */
package com.treelearn.app.generation;

import com.treelearn.ai.prompt.DeepSeekRequest;
import com.treelearn.app.generation.GenerationCoordinator;
import com.treelearn.app.generation.GenerationGateway;
import com.treelearn.app.generation.StreamBatch;
import com.treelearn.core.domain.GenerationCommand;
import com.treelearn.core.domain.GenerationOperation;

final class LiveAttempt {
    final GenerationCommand command;
    final GenerationOperation operation;
    final DeepSeekRequest request;
    final GenerationCoordinator.AcceptedWrite acceptedWrite;
    final StreamBatch batch = new StreamBatch();
    GenerationGateway.Stream stream;
    boolean persisted;
    boolean streaming;

    LiveAttempt(GenerationCommand command, GenerationOperation operation, DeepSeekRequest request, GenerationCoordinator.AcceptedWrite acceptedWrite) {
        this.command = command;
        this.operation = operation;
        this.request = request;
        this.acceptedWrite = acceptedWrite;
    }
}

