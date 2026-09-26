/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.treelearn.core.domain.GenerationCommand
 */
package com.treelearn.app.generation;

import com.treelearn.core.domain.GenerationCommand;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public final class GenerationScheduler {
    public static final int MAX_ACTIVE = 2;
    private final Map<String, GenerationCommand> active = new LinkedHashMap<String, GenerationCommand>();
    private final Map<String, GenerationCommand> waiting = new LinkedHashMap<String, GenerationCommand>();
    private final Set<String> busyBranches = new HashSet<String>();

    public Admission admit(GenerationCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("command must not be null");
        }
        if (this.busyBranches.contains(command.branchId)) {
            return Admission.BRANCH_BUSY;
        }
        this.busyBranches.add(command.branchId);
        if (this.active.size() < 2) {
            this.active.put(command.id, command);
            return Admission.STARTED;
        }
        this.waiting.put(command.id, command);
        return Admission.QUEUED;
    }

    public GenerationCommand finish(String branchId, String commandId) {
        GenerationCommand finished = GenerationScheduler.take(this.active, branchId, commandId);
        if (finished != null) {
            this.busyBranches.remove(finished.branchId);
            return this.startOldestWaiting();
        }
        finished = GenerationScheduler.take(this.waiting, branchId, commandId);
        if (finished != null) {
            this.busyBranches.remove(finished.branchId);
        }
        return null;
    }

    public int activeCount() {
        return this.active.size();
    }

    public int waitingCount() {
        return this.waiting.size();
    }

    private GenerationCommand startOldestWaiting() {
        if (this.waiting.isEmpty()) {
            return null;
        }
        String oldest = this.waiting.keySet().iterator().next();
        GenerationCommand next = this.waiting.remove(oldest);
        this.active.put(next.id, next);
        return next;
    }

    private static GenerationCommand take(Map<String, GenerationCommand> from, String branchId, String commandId) {
        GenerationCommand found = from.get(commandId);
        if (found == null) {
            return null;
        }
        if (!found.branchId.equals(branchId)) {
            throw new IllegalArgumentException("attempt " + commandId + " belongs to branch " + found.branchId + ", not " + branchId);
        }
        from.remove(commandId);
        return found;
    }

    public static enum Admission {
        STARTED,
        QUEUED,
        BRANCH_BUSY;

    }
}

