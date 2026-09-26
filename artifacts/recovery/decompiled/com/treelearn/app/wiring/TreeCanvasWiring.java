/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.treelearn.core.domain.Branch
 *  com.treelearn.core.domain.DomainResult
 *  com.treelearn.core.domain.Guard
 */
package com.treelearn.app.wiring;

import com.treelearn.app.artifact.ArtifactKind;
import com.treelearn.app.artifact.ArtifactPayload;
import com.treelearn.app.artifact.ArtifactPayloadCodec;
import com.treelearn.app.artifact.DerivedResult;
import com.treelearn.app.ui.TreeCanvasCommands;
import com.treelearn.app.ui.TreeCanvasData;
import com.treelearn.app.wiring.TreeCanvasStore;
import com.treelearn.core.domain.Branch;
import com.treelearn.core.domain.DomainResult;
import com.treelearn.core.domain.Guard;
import java.util.ArrayList;

public final class TreeCanvasWiring
implements TreeCanvasData,
TreeCanvasCommands {
    private static final int SUMMARY_ITEM_CEILING = 8;
    private final TreeCanvasStore store;

    public TreeCanvasWiring(TreeCanvasStore store) {
        this.store = (TreeCanvasStore)Guard.object((Object)store, (String)"store");
    }

    @Override
    public String topicOf(String branchId) {
        return this.store.topicOf(branchId);
    }

    @Override
    public TreeCanvasData.Tree load(String topicId) {
        ArrayList<TreeCanvasData.Row> rows = new ArrayList<TreeCanvasData.Row>();
        for (Branch branch : this.store.branchesOf(topicId)) {
            if (branch.isArchived()) continue;
            rows.add(new TreeCanvasData.Row(branch, TreeCanvasWiring.firstLine(this.store.summaryPayload(branch.id)), this.store.committedRounds(branch.id)));
        }
        return new TreeCanvasData.Tree(topicId, rows);
    }

    @Override
    public DomainResult<Void> setCollapsed(String branchId, boolean collapsed, long now) {
        return this.store.setCollapsed(branchId, collapsed, now);
    }

    private static String firstLine(String payload) {
        if (payload == null || payload.isEmpty()) {
            return "";
        }
        DerivedResult parsed = ArtifactPayloadCodec.parse(ArtifactKind.BRANCH_SUMMARY, payload, 8);
        if (!parsed.isUsable()) {
            return "";
        }
        ArtifactPayload artifact = parsed.payload();
        String text = artifact == null || artifact.summary == null ? "" : artifact.summary;
        int end = text.indexOf(10);
        return end < 0 ? text : text.substring(0, end);
    }
}

