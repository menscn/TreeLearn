package com.treelearn.app.generation;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The in-memory registry behind "does this event still belong to the active attempt?" (7, 9.3).
 *
 * <p>An attempt leaves the registry the moment it has an outcome, whatever that outcome was. Every
 * callback that arrives afterwards is therefore not current, and the coordinator discards it instead
 * of writing it back over a result the user has already seen. One command has at most one live
 * attempt (the scheduler refuses a second one for a busy branch), so retrying in place puts a new
 * operation under the same command and makes the previous one stale.
 *
 * <p>This is a scheduling cache, not the authority: SQLite is. A process that dies takes this with
 * it, and recovery rebuilds the outcome from the rows.
 */
final class LiveAttempts {

    private final Map<String, LiveAttempt> byCommand = new LinkedHashMap<String, LiveAttempt>();

    /** Registers one attempt. The caller has already established that its command is not live. */
    void open(LiveAttempt attempt) {
        byCommand.put(attempt.command.id, attempt);
    }

    /** Removes the attempt of this command, if it was still live. */
    void close(String commandId) {
        byCommand.remove(commandId);
    }

    /** @return the live attempt for this command, or null when there is none */
    LiveAttempt find(String commandId) {
        return byCommand.get(commandId);
    }

    /** @return the live attempt holding this operation, or null when none does */
    LiveAttempt byOperation(String operationId) {
        for (LiveAttempt attempt : byCommand.values()) {
            if (attempt.operation.id.equals(operationId)) {
                return attempt;
            }
        }
        return null;
    }

    /** @return true when this very attempt is still the live one, so its event may be applied */
    boolean isCurrent(LiveAttempt attempt) {
        return byCommand.get(attempt.command.id) == attempt;
    }

}
