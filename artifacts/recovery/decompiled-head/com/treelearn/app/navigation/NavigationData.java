/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.treelearn.core.domain.Guard
 */
package com.treelearn.app.navigation;

import com.treelearn.core.domain.Guard;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public interface NavigationData {
    public String topicOf(String var1);

    public Snapshot load(String var1);

    public Summary summary(String var1);

    public void setArchived(String var1, boolean var2, long var3);

    public int cancelInFlight(List<String> var1);

    public void delete(Plan var1);

    /*
     * WARNING - void declaration
     */
    public static Plan plan(String topicId, Snapshot tree, String target, boolean wholeTopic) {
        void var5_8;
        if (topicId == null || tree == null || target == null || tree.node(target) == null) {
            return null;
        }
        for (Node node : tree.nodes) {
            if (node.parentBranchId == null || tree.node(node.parentBranchId) != null) continue;
            return null;
        }
        ArrayList<String> ids = new ArrayList<String>();
        if (wholeTopic) {
            for (Node node : tree.nodes) {
                ids.add(node.branchId);
            }
            return new Plan(topicId, target, ids, true);
        }
        ids.add(target);
        boolean bl = false;
        while (var5_8 < ids.size()) {
            for (Node node : tree.nodes) {
                if (!((String)ids.get((int)var5_8)).equals(node.parentBranchId) || ids.contains(node.branchId)) continue;
                ids.add(node.branchId);
            }
            ++var5_8;
        }
        return new Plan(topicId, target, ids, false);
    }

    public static final class Snapshot {
        public final String topicId;
        public final List<Node> nodes;

        public Snapshot(String topicId, List<Node> nodes) {
            this.topicId = Guard.id((String)topicId, (String)"topicId");
            this.nodes = Collections.unmodifiableList(new ArrayList((Collection)Guard.object(nodes, (String)"nodes")));
        }

        public Node node(String branchId) {
            for (Node node : this.nodes) {
                if (!node.branchId.equals(branchId)) continue;
                return node;
            }
            return null;
        }
    }

    public static final class Node {
        public final String branchId;
        public final String parentBranchId;
        public final String title;
        public final boolean archived;

        public Node(String branchId, String parentBranchId, String title, boolean archived) {
            this.branchId = Guard.id((String)branchId, (String)"branchId");
            this.parentBranchId = Guard.idOrNull((String)parentBranchId, (String)"parentBranchId");
            this.title = Guard.text((String)title, (String)"title");
            this.archived = archived;
        }

        public boolean isRoot() {
            return this.parentBranchId == null;
        }
    }

    public static final class Plan {
        public final String topicId;
        public final String targetBranchId;
        public final List<String> branchIds;
        public final boolean wholeTopic;

        public Plan(String topicId, String targetBranchId, List<String> branchIds, boolean wholeTopic) {
            this.topicId = Guard.id((String)topicId, (String)"topicId");
            this.targetBranchId = Guard.id((String)targetBranchId, (String)"targetBranchId");
            this.branchIds = Collections.unmodifiableList(new ArrayList((Collection)Guard.object(branchIds, (String)"branchIds")));
            if (this.branchIds.isEmpty()) {
                throw new IllegalArgumentException("a delete plan must name at least one branch; an empty scope would ask the learner to confirm a deletion that removes nothing");
            }
            this.wholeTopic = wholeTopic;
        }

        public int branchCount() {
            return this.branchIds.size();
        }
    }

    public static final class Summary {
        public final String branchId;
        public final String text;
        public final List<String> keyPoints;

        public Summary(String branchId, String text, List<String> keyPoints) {
            this.branchId = Guard.id((String)branchId, (String)"branchId");
            this.text = Guard.text((String)text, (String)"text");
            this.keyPoints = Collections.unmodifiableList(new ArrayList((Collection)Guard.object(keyPoints, (String)"keyPoints")));
        }
    }
}

