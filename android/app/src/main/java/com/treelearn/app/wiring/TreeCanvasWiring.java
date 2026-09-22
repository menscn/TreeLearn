package com.treelearn.app.wiring;

import com.treelearn.app.artifact.ArtifactKind;
import com.treelearn.app.artifact.ArtifactPayload;
import com.treelearn.app.artifact.ArtifactPayloadCodec;
import com.treelearn.app.artifact.DerivedResult;
import com.treelearn.app.ui.TreeCanvasCommands;
import com.treelearn.app.ui.TreeCanvasData;
import com.treelearn.core.domain.Branch;
import com.treelearn.core.domain.DomainResult;
import com.treelearn.core.domain.Guard;

import java.util.ArrayList;
import java.util.List;

/**
 * The canvas's real data path: one topic read as the rows the geometry draws, and the one write.
 *
 * <p><strong>The canvas's two rules live here, and only here.</strong> An archived branch is not part of
 * the canvas - the geometry has no shape for one, and the read is the one place that can decide it once
 * for the phone and the tablet alike - and a summary is its first line, because a leaf has room for one
 * line and the derived artifact carries a paragraph.
 *
 * <p><strong>Nothing here is a second implementation.</strong> The round count comes from the store's
 * own question count, the fold is delegated unchanged, and the decode is
 * {@link ArtifactPayloadCodec}'s: a payload the codec refuses reads as an absent summary rather than
 * failing the canvas, which is the same answer the home list gives for the same row (T-032). The screen
 * never sees a payload, a revision id or a message body - only the {@link Row} the geometry consumes.
 */
public final class TreeCanvasWiring implements TreeCanvasData, TreeCanvasCommands {

    /**
     * The ceiling for a recommendation-shaped payload. A branch summary carries no recommendation
     * array, so this number is never reached; it is the codec's required floor, stated once rather than
     * passed as a bare literal at the call site (the same value {@link HomeWiring} uses).
     */
    private static final int SUMMARY_ITEM_CEILING = 8;

    private final TreeCanvasStore store;

    public TreeCanvasWiring(TreeCanvasStore store) {
        this.store = Guard.object(store, "store");
    }

    @Override
    public String topicOf(String branchId) {
        return store.topicOf(branchId);
    }

    /**
     * The topic's live branches in the store's order, each with the two derived values the geometry
     * needs.
     *
     * <p>Every row travels with its own {@link Branch} rather than with a re-stated copy: the badge is
     * computed from the domain's seen/unseen pair and the fold flag is read off the same object, so the
     * canvas cannot disagree with the conversation about either.
     */
    @Override
    public Tree load(String topicId) {
        List<Row> rows = new ArrayList<Row>();
        for (Branch branch : store.branchesOf(topicId)) {
            if (branch.isArchived()) {
                continue;
            }
            rows.add(new Row(branch, firstLine(store.summaryPayload(branch.id)),
                    store.committedRounds(branch.id)));
        }
        return new Tree(topicId, rows);
    }

    @Override
    public DomainResult<Void> setCollapsed(String branchId, boolean collapsed, long now) {
        return store.setCollapsed(branchId, collapsed, now);
    }

    /**
     * The first line of the stored summary, or the empty string.
     *
     * <p>Empty covers all three absences the canvas treats alike: no artifact yet, a payload the codec
     * refuses, and a summary whose own text is empty. A leaf draws no line for any of them (PRD 6.2.8
     * point 7: an absent summary is not a placeholder), so the caller has nothing to branch on.
     */
    private static String firstLine(String payload) {
        if (payload == null || payload.isEmpty()) {
            return "";
        }
        DerivedResult parsed = ArtifactPayloadCodec.parse(ArtifactKind.BRANCH_SUMMARY, payload,
                SUMMARY_ITEM_CEILING);
        if (!parsed.isUsable()) {
            return "";
        }
        ArtifactPayload artifact = parsed.payload();
        String text = artifact == null || artifact.summary == null ? "" : artifact.summary;
        int end = text.indexOf('\n');
        return end < 0 ? text : text.substring(0, end);
    }
}
