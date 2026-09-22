package com.treelearn.app.wiring;

import com.treelearn.core.domain.Branch;
import com.treelearn.core.domain.DomainResult;

import java.util.List;

/**
 * The canvas's rows and its one write, behind one port.
 *
 * <p><strong>This port owns no rule.</strong> Which branches are on the canvas, what a summary reads as
 * and whether a fold is allowed are decided by {@link TreeCanvasWiring}; this interface only names the
 * four reads and the one write the SQLite side has to answer, which is what lets the deciding half run
 * in a plain JVM test against a list.
 *
 * <p>Two of the reads exist because the geometry needs values that live in other slices: the summary
 * first line comes from the derived {@code BRANCH_SUMMARY} artifact, and the round count is what the
 * branch's segment is drawn from. Asking for them by branch id keeps the canvas from reaching into the
 * artifact and message tables itself.
 */
public interface TreeCanvasStore {

    /** @return the topic a branch belongs to, or null when no row carries that id */
    String topicOf(String branchId);

    /**
     * Every branch of a topic in the store's order, archived rows <em>included</em>.
     *
     * <p>Archived is deliberately not filtered here. Dropping it is a canvas rule - the geometry has no
     * shape for an archived branch - and a store that narrowed the read on its own would hide that
     * decision from the half that is supposed to make it.
     */
    List<Branch> branchesOf(String topicId);

    /** The branch's stored {@code BRANCH_SUMMARY} envelope as it was written, or null when it has none. */
    String summaryPayload(String branchId);

    /** How many learner turns the branch holds; the canvas draws the segment from it. */
    int committedRounds(String branchId);

    /** @return the refusal, or {@code ok} once {@code branches.collapsed} carries the new value */
    DomainResult<Void> setCollapsed(String branchId, boolean collapsed, long now);
}
