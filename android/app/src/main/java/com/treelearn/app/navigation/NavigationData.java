package com.treelearn.app.navigation;

import com.treelearn.core.domain.Guard;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Everything the navigation view reads and writes, behind one port (technical design §12).
 *
 * <p>The port exists because the screen must be drawable and testable without a database: the
 * presenter asks these questions and renders the answers, and a JVM test answers them from a list.
 *
 * <p><strong>Archive and delete are different acts, and this port keeps them apart.</strong>
 * {@link #setArchived} only flips {@code archived_at}: the branch stays, its history stays, and a
 * running attempt is never touched. {@link #delete} is the irreversible one, and the presenter owes
 * it {@link #cancelInFlight} first - so "we cancelled what was running, then removed what the
 * confirmation named" is two calls a test can order, not one opaque method.
 *
 * <p><strong>The deletion set is computed from a tree, not from the UI.</strong> {@link Plan} is
 * built by the presenter out of one {@link Snapshot}; the store then removes exactly that set in one
 * transaction. A cascade that walked the UI row by row would leave a half-deleted tree behind if the
 * screen went away mid-loop.
 */
public interface NavigationData {

    /** One branch as the list needs it: identity, position and title. Never a message body. */
    final class Node {

        public final String branchId;
        /** Null exactly for a topic's single root branch. */
        public final String parentBranchId;
        /**
         * The branch's own title, or "" while it has none yet.
         *
         * <p>A child branch is born unnamed — {@code Branch.child} takes no title and the derived
         * title artifact writes one later, if it ever arrives — so a null here is a designed state,
         * not a corrupt row. The canvas already draws such a node ({@code LayoutNode} normalizes the
         * same way, and {@code TreeCanvasViewState} renders "" for a missing title), so the list must
         * read it too rather than lose the whole tree over one unnamed branch. This mirrors
         * {@code ChildBranch}, the third place that names a branch for display.
         */
        public final String title;
        public final boolean archived;

        public Node(String branchId, String parentBranchId, String title, boolean archived) {
            this.branchId = Guard.id(branchId, "branchId");
            this.parentBranchId = Guard.idOrNull(parentBranchId, "parentBranchId");
            this.title = title == null ? "" : title.trim();
            this.archived = archived;
        }

        public boolean isRoot() {
            return parentBranchId == null;
        }
    }

    /**
     * The current branch's own {@code BRANCH_SUMMARY} artifact, decoded.
     *
     * <p>Only that kind reaches this type: the store looks the row up by
     * {@code ArtifactKind.BRANCH_SUMMARY.wireId}, so a title, a recommendation or a compression of
     * some other usage cannot be shown here even by accident.
     */
    final class Summary {

        public final String branchId;
        public final String text;
        public final List<String> keyPoints;

        public Summary(String branchId, String text, List<String> keyPoints) {
            this.branchId = Guard.id(branchId, "branchId");
            this.text = Guard.text(text, "text");
            this.keyPoints = Collections.unmodifiableList(
                    new ArrayList<String>(Guard.object(keyPoints, "keyPoints")));
        }
    }

    /** One topic's branches, in store order (the root first). */
    final class Snapshot {

        public final String topicId;
        public final List<Node> nodes;

        public Snapshot(String topicId, List<Node> nodes) {
            this.topicId = Guard.id(topicId, "topicId");
            this.nodes = Collections.unmodifiableList(
                    new ArrayList<Node>(Guard.object(nodes, "nodes")));
        }

        /** @return the node carrying {@code branchId}, or null when this tree does not hold it */
        public Node node(String branchId) {
            for (Node node : nodes) {
                if (node.branchId.equals(branchId)) {
                    return node;
                }
            }
            return null;
        }
    }

    /** The exact set one confirmation covers, counted before it is offered. */
    final class Plan {

        public final String topicId;
        /** The branch whose action asked for this deletion. */
        public final String targetBranchId;
        /** Every branch the deletion removes, parents before their children. Never empty. */
        public final List<String> branchIds;
        public final boolean wholeTopic;

        public Plan(String topicId, String targetBranchId, List<String> branchIds,
                boolean wholeTopic) {
            this.topicId = Guard.id(topicId, "topicId");
            this.targetBranchId = Guard.id(targetBranchId, "targetBranchId");
            this.branchIds = Collections.unmodifiableList(
                    new ArrayList<String>(Guard.object(branchIds, "branchIds")));
            if (this.branchIds.isEmpty()) {
                throw new IllegalArgumentException(
                        "a delete plan must name at least one branch; an empty scope would ask the "
                                + "learner to confirm a deletion that removes nothing");
            }
            this.wholeTopic = wholeTopic;
        }

        public int branchCount() {
            return branchIds.size();
        }
    }

    /** @return the topic a branch belongs to, or null when the branch is gone */
    String topicOf(String branchId);

    /** The whole tree of one topic. Throws when the store cannot be read. */
    Snapshot load(String topicId);

    /** @return the branch's own summary, or null when it has none yet; throws when unreadable */
    Summary summary(String branchId);

    /** Flips archived_at on one branch. Never cancels an attempt; never removes history. */
    void setArchived(String branchId, boolean archived, long now);

    /** @return how many in-flight attempts of these branches were cancelled */
    int cancelInFlight(List<String> branchIds);

    /** Removes the plan's branches and everything hanging off them, in one transaction. */
    void delete(Plan plan);

    /**
     * Counts the exact set one confirmation would cover out of one tree.
     *
     * <p><strong>One implementation, two callers.</strong> The navigation page and the library's
     * completed segment both ask "which branches would this remove", and both must be handed the same
     * answer. A second walk would be a second opinion about what a subtree is, and the two disagreeing
     * is an irreversible deletion of the wrong set - which is why this lives on the port both screens
     * already share rather than in either presenter.
     *
     * <p>It fails closed: a target the tree does not hold, no tree at all, or a node naming a parent
     * the tree does not hold all answer null. A tree that does not describe itself cannot say how many
     * branches a deletion removes, and a wrong number under a permanent-delete button is worse than no
     * button.
     *
     * @return the plan, or null when the scope cannot be counted
     */
    static Plan plan(String topicId, Snapshot tree, String target, boolean wholeTopic) {
        if (topicId == null || tree == null || target == null || tree.node(target) == null) {
            return null;
        }
        for (Node node : tree.nodes) {
            if (node.parentBranchId != null && tree.node(node.parentBranchId) == null) {
                return null;
            }
        }
        List<String> ids = new ArrayList<String>();
        if (wholeTopic) {
            for (Node node : tree.nodes) {
                ids.add(node.branchId);
            }
            return new Plan(topicId, target, ids, true);
        }
        ids.add(target);
        for (int i = 0; i < ids.size(); i++) {
            for (Node node : tree.nodes) {
                if (ids.get(i).equals(node.parentBranchId) && !ids.contains(node.branchId)) {
                    ids.add(node.branchId);
                }
            }
        }
        return new Plan(topicId, target, ids, false);
    }
}
