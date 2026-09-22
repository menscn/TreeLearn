package com.treelearn.app.library;

import com.treelearn.app.navigation.NavigationData;

import java.util.List;

/**
 * Everything the library reads and writes, behind one port.
 *
 * <p>The port exists because the screen must be drawable and testable without a database: the
 * presenter asks these questions and renders the answers, and a JVM test answers them from a list.
 *
 * <p><strong>The two writes are borrowed, not re-implemented.</strong> Restoring is the navigation
 * port's {@code setArchived}, and deleting takes a {@link NavigationData.Plan} the presenter counted
 * with {@link NavigationData#plan} and hands to the navigation port's {@code delete}. The library owns
 * no second way to decide which branches a subtree is - see {@code NavigationData.plan}, whose only
 * reason for living on the port is that both screens must be handed the same set.
 *
 * <p>Reads throw when the store cannot be read. That is the state the presenter answers with its error
 * phase, not a defect of the port.
 */
public interface LibraryData {

    /** The topics the library draws, newest first. Never null; empty means "no topic yet". */
    List<LibraryViewState.Row> topics();

    /** The archived branches the completed segment draws, flattened out of every topic. */
    List<LibraryViewState.ArchiveRow> archived();

    /** One topic's branches, as the navigation port shapes them: the deletion scope's only input. */
    NavigationData.Snapshot snapshot(String topicId);

    /** Puts one archived branch back. Flips the archived bit and re-attaches nothing. */
    void restore(String branchId, long now);

    /** @return how many in-flight attempts of these branches were cancelled */
    int cancelInFlight(List<String> branchIds);

    /** Removes the plan's branches in one transaction. Irreversible. */
    void delete(NavigationData.Plan plan);
}
