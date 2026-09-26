/*
 * Decompiled with CFR 0.152.
 */
package com.treelearn.app.generation;

import com.treelearn.app.generation.LiveAttempt;
import java.util.LinkedHashMap;
import java.util.Map;

final class LiveAttempts {
    private final Map<String, LiveAttempt> byCommand = new LinkedHashMap<String, LiveAttempt>();

    LiveAttempts() {
    }

    void open(LiveAttempt attempt) {
        this.byCommand.put(attempt.command.id, attempt);
    }

    void close(String commandId) {
        this.byCommand.remove(commandId);
    }

    LiveAttempt find(String commandId) {
        return this.byCommand.get(commandId);
    }

    LiveAttempt byOperation(String operationId) {
        for (LiveAttempt attempt : this.byCommand.values()) {
            if (!attempt.operation.id.equals(operationId)) continue;
            return attempt;
        }
        return null;
    }

    boolean isCurrent(LiveAttempt attempt) {
        return this.byCommand.get(attempt.command.id) == attempt;
    }
}

