package com.treelearn.app.generation;

import com.treelearn.core.domain.GenerationCommand;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * In-process scheduling cache for the concurrency rule of technical design 7.
 *
 * <p>At most {@link #MAX_ACTIVE} attempts run at once, and everything else waits its turn in arrival
 * order, so a burst of requests is bounded by the application rather than by the device. A branch may
 * have one attempt here at a time — running <em>or</em> waiting — because 7 allows one active
 * generation per branch and a queued attempt would otherwise break that rule the moment its slot
 * arrived.
 *
 * <p>Not the authority: SQLite is. This class only decides who runs now; it holds no content, and a
 * process that dies takes it with it, which is why recovery rebuilds from the rows instead.
 */
public final class GenerationScheduler {

    /** The application-wide cap of 7. */
    public static final int MAX_ACTIVE = 2;

    /** What happened to one submission. */
    public enum Admission {
        /** A slot was free, so the attempt runs now. */
        STARTED,
        /** Both slots are taken, so the attempt waits for one. */
        QUEUED,
        /** The branch already has an attempt, running or waiting. */
        BRANCH_BUSY
    }

    private final Map<String, GenerationCommand> active =
            new LinkedHashMap<String, GenerationCommand>();
    private final Map<String, GenerationCommand> waiting =
            new LinkedHashMap<String, GenerationCommand>();
    private final Set<String> busyBranches = new HashSet<String>();

    /** Offers one command a place, or refuses it because its branch is already taken. */
    public Admission admit(GenerationCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("command must not be null");
        }
        if (busyBranches.contains(command.branchId)) {
            return Admission.BRANCH_BUSY;
        }
        busyBranches.add(command.branchId);
        if (active.size() < MAX_ACTIVE) {
            active.put(command.id, command);
            return Admission.STARTED;
        }
        waiting.put(command.id, command);
        return Admission.QUEUED;
    }

    /**
     * Reports that one attempt left, whatever its outcome.
     *
     * <p>A refusal changes nothing: it never took a slot or a queue place, so there is nothing to give
     * back. Only an attempt that was running frees a slot, and a freed slot is what starts the oldest
     * waiting command.
     *
     * @return the oldest waiting command when this freed a slot and someone was waiting for it, else
     *         null
     */
    public GenerationCommand finish(String branchId, String commandId) {
        GenerationCommand finished = take(active, branchId, commandId);
        if (finished != null) {
            busyBranches.remove(finished.branchId);
            return startOldestWaiting();
        }
        finished = take(waiting, branchId, commandId);
        if (finished != null) {
            busyBranches.remove(finished.branchId);
        }
        return null;
    }

    /** @return how many calls may be open right now */
    public int activeCount() {
        return active.size();
    }

    /** @return how many attempts are admitted but not started */
    public int waitingCount() {
        return waiting.size();
    }

    private GenerationCommand startOldestWaiting() {
        if (waiting.isEmpty()) {
            return null;
        }
        String oldest = waiting.keySet().iterator().next();
        GenerationCommand next = waiting.remove(oldest);
        active.put(next.id, next);
        return next;
    }

    /**
     * Removes the attempt the caller names, and refuses to guess when the branch disagrees: both
     * identities come from the same command, so a mismatch is a caller bug rather than an outcome.
     */
    private static GenerationCommand take(Map<String, GenerationCommand> from, String branchId,
            String commandId) {
        GenerationCommand found = from.get(commandId);
        if (found == null) {
            return null;
        }
        if (!found.branchId.equals(branchId)) {
            throw new IllegalArgumentException("attempt " + commandId + " belongs to branch "
                    + found.branchId + ", not " + branchId);
        }
        from.remove(commandId);
        return found;
    }
}
