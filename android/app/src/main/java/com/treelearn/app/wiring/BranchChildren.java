package com.treelearn.app.wiring;

import com.treelearn.core.domain.Branch;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The one rule behind an answer's child branches: which of a topic's branches hang directly off one
 * answer, and how many there are (PRD 6.2.20 point 7).
 *
 * <p>Two readers ask it - the action row's 「子分支 N」 entry and the popup that lists them - so it
 * lives in one place. A copy that counted an archived child while the list left it out would produce
 * an entry claiming children it cannot show, and a copy that counted one level too deep would open a
 * list of branches that do not belong to the answer that was tapped.
 *
 * <p>What counts as a child: not the root (a root has no source), forked (so it carries a source
 * message) and not archived. Creation order is the topic read's own order, which is
 * {@code created_at ASC}, so the popup lists children in the order they were made.
 */
final class BranchChildren {

    private BranchChildren() {
    }

    /** The branches forked directly from {@code messageId}, unarchived, in the order they were made. */
    static List<Branch> of(List<Branch> topicBranches, String messageId) {
        List<Branch> children = new ArrayList<Branch>();
        if (topicBranches == null || messageId == null || messageId.isEmpty()) {
            return children;
        }
        for (Branch branch : topicBranches) {
            if (isChild(branch) && messageId.equals(branch.source.sourceMessageId)) {
                children.add(branch);
            }
        }
        return children;
    }

    /** How many children each position of the topic has, keyed by the position's message id. */
    static Map<String, Integer> countsOf(List<Branch> topicBranches) {
        Map<String, Integer> counts = new HashMap<String, Integer>();
        if (topicBranches == null) {
            return counts;
        }
        for (Branch branch : topicBranches) {
            if (!isChild(branch)) {
                continue;
            }
            String source = branch.source.sourceMessageId;
            Integer seen = counts.get(source);
            counts.put(source, seen == null ? 1 : seen + 1);
        }
        return counts;
    }

    /** A branch that can appear in somebody's child list: forked, and still in the tree. */
    private static boolean isChild(Branch branch) {
        return branch != null && !branch.isRoot() && branch.source != null
                && branch.source.sourceMessageId != null && !branch.isArchived();
    }
}
