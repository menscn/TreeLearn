package com.treelearn.core.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The unread badge each branch shows, aggregated over the tree as it is visible right now
 * (technical design 11).
 *
 * <p>{@link Branch#unreadCount()} answers for one branch. This answers for the screen, and the two
 * ways it differs are both rules rather than arithmetic:
 *
 * <ul>
 *   <li><strong>A collapsed ancestor hides its descendants, so it carries their unread.</strong>
 *       Otherwise a collapsed subtree with three new answers would show a zero badge, and the learner
 *       would have no reason to open it. An expanded ancestor shows each branch's own badge and no
 *       sum — the count belongs where it can be acted on.
 *   <li><strong>An archived branch is not in the visible tree at all</strong>, and neither is its
 *       subtree. Its unread must not leak into its parent's badge: that would be a count for
 *       something the learner cannot open without unarchiving it first.
 * </ul>
 *
 * <p>Nothing here is persisted. §11 forbids a stored unread authority, and this map is recomputed
 * from the sequence pair every time the tree is drawn, which is what makes a title or summary refresh
 * unable to produce a badge: no code path that changes a title can reach this function's inputs.
 */
public final class UnreadRollup {

    private UnreadRollup() {
    }

    /**
     * @param branches every branch of the visible tree; order does not matter and a branch whose
     *        parent is not in the list is treated as a top of this snapshot rather than dropped
     * @return branch id to badge, for the branches the tree actually shows, unmodifiable
     */
    public static Map<String, Long> visibleUnread(List<Branch> branches) {
        Guard.object(branches, "branches");
        Map<String, Branch> byId = new LinkedHashMap<String, Branch>();
        for (Branch branch : branches) {
            byId.put(branch.id, branch);
        }
        Map<String, List<Branch>> children = new HashMap<String, List<Branch>>();
        for (Branch branch : branches) {
            String parentId = branch.parentBranchId;
            if (parentId != null && byId.containsKey(parentId)) {
                List<Branch> siblings = children.get(parentId);
                if (siblings == null) {
                    siblings = new ArrayList<Branch>();
                    children.put(parentId, siblings);
                }
                siblings.add(branch);
            }
        }

        Map<String, Long> visible = new LinkedHashMap<String, Long>();
        Set<String> walked = new HashSet<String>();
        for (Branch branch : branches) {
            String parentId = branch.parentBranchId;
            if (parentId != null && byId.containsKey(parentId)) {
                continue;
            }
            badge(branch, children, false, visible, walked);
        }
        // A branch that the walk never reached — a parent chain that cycles, which a corrupt row can
        // produce — still gets a badge of its own rather than vanishing from the screen.
        for (Branch branch : branches) {
            if (walked.add(branch.id)) {
                visible.put(branch.id, Long.valueOf(branch.unreadCount()));
            }
        }
        return Collections.unmodifiableMap(visible);
    }

    /**
     * One branch's badge, and its descendants' when they are hidden behind it.
     *
     * <p>Three things are tracked separately on purpose, because collapsing them into one "is this
     * visible" flag is what makes the arithmetic wrong: whether the branch is <em>published</em>,
     * whether its children are hidden behind it, and whether its own count <em>counts at all</em>. An
     * archived branch is the case that separates the last two: its unread must not reach its parent,
     * while a branch merely hidden behind a collapsed ancestor must — the ancestor is carrying it.
     */
    private static long badge(Branch branch, Map<String, List<Branch>> children, boolean hidden,
            Map<String, Long> visible, Set<String> walked) {
        if (!walked.add(branch.id)) {
            return 0L;
        }
        boolean archived = branch.isArchived();
        boolean published = !hidden && !archived;
        boolean childrenHidden = hidden || archived || branch.isCollapsed();
        long total = archived ? 0L : branch.unreadCount();
        List<Branch> nested = children.get(branch.id);
        if (nested != null) {
            for (Branch child : nested) {
                long childBadge = badge(child, children, childrenHidden, visible, walked);
                if (childrenHidden) {
                    total += childBadge;
                }
            }
        }
        if (published) {
            visible.put(branch.id, Long.valueOf(total));
        }
        return total;
    }
}