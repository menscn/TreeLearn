/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.treelearn.core.domain.Branch
 */
package com.treelearn.app.wiring;

import com.treelearn.core.domain.Branch;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class BranchChildren {
    private BranchChildren() {
    }

    static List<Branch> of(List<Branch> topicBranches, String messageId) {
        ArrayList<Branch> children = new ArrayList<Branch>();
        if (topicBranches == null || messageId == null || messageId.isEmpty()) {
            return children;
        }
        for (Branch branch : topicBranches) {
            if (!BranchChildren.isChild(branch) || !messageId.equals(branch.source.sourceMessageId)) continue;
            children.add(branch);
        }
        return children;
    }

    static Map<String, Integer> countsOf(List<Branch> topicBranches) {
        HashMap<String, Integer> counts = new HashMap<String, Integer>();
        if (topicBranches == null) {
            return counts;
        }
        for (Branch branch : topicBranches) {
            String source;
            if (!BranchChildren.isChild(branch)) continue;
            Integer seen = (Integer)counts.get(source = branch.source.sourceMessageId);
            counts.put(source, seen == null ? 1 : seen + 1);
        }
        return counts;
    }

    private static boolean isChild(Branch branch) {
        return branch != null && !branch.isRoot() && branch.source != null && branch.source.sourceMessageId != null && !branch.isArchived();
    }
}

