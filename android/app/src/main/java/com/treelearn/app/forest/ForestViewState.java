package com.treelearn.app.forest;

import com.treelearn.core.domain.Guard;
import com.treelearn.core.domain.Persona;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Everything the forest page may draw (T-068), computed once by the presenter.
 *
 * <p>The four phases are the page's whole vocabulary. A <em>ready</em> forest carries its real
 * totals only when every count was read; a single unknown count drops both totals instead of
 * summing a short list. The selection is an id, never a position: a re-read that reorders nothing
 * still has to prove the selected tree exists before the sheet may name it.
 */
public final class ForestViewState {

    public enum Phase {
        LOADING,
        READY,
        EMPTY,
        ERROR
    }

    public final Phase phase;
    /** The trees in the read's order; placements are normalised ground coordinates. */
    public final List<Tree> trees;
    /** The selected topic, or null before the learner's first tap. */
    public final String selectedTopicId;
    /** Sum of every tree's segments, or null when any count is unknown. */
    public final Integer segmentTotal;
    /** Sum of every tree's archived segments, or null when any count is unknown. */
    public final Integer archivedTotal;
    /** The stats entry's current streak, or null when that read failed. */
    public final Integer streakDays;

    private ForestViewState(Phase phase, List<Tree> trees, String selectedTopicId,
            Integer segmentTotal, Integer archivedTotal, Integer streakDays) {
        this.phase = Guard.object(phase, "phase");
        this.trees = trees == null ? Collections.<Tree>emptyList()
                : Collections.unmodifiableList(new ArrayList<Tree>(trees));
        this.selectedTopicId = selectedTopicId;
        this.segmentTotal = segmentTotal;
        this.archivedTotal = archivedTotal;
        this.streakDays = streakDays;
        if (selectedTopicId != null && selected() == null) {
            throw new IllegalArgumentException(
                    "the selection must name a tree on the ground: " + selectedTopicId);
        }
    }

    public static ForestViewState loading() {
        return new ForestViewState(Phase.LOADING, null, null, null, null, null);
    }

    public static ForestViewState empty() {
        return new ForestViewState(Phase.EMPTY, null, null, null, null, null);
    }

    public static ForestViewState error() {
        return new ForestViewState(Phase.ERROR, null, null, null, null, null);
    }

    public static ForestViewState ready(List<Tree> trees, Integer segmentTotal,
            Integer archivedTotal, Integer streakDays, String selectedTopicId) {
        return new ForestViewState(Phase.READY, trees, selectedTopicId, segmentTotal,
                archivedTotal, streakDays);
    }

    /** Only a tree already on the ground can become (or stay) the selection. */
    public ForestViewState withSelected(String topicId) {
        String selection = topicId;
        if (selection != null && find(selection) == null) {
            throw new IllegalArgumentException("no such tree on the ground: " + topicId);
        }
        return new ForestViewState(phase, trees, selection, segmentTotal, archivedTotal,
                streakDays);
    }

    /** @return the selected tree, or null when nothing is selected */
    public Tree selected() {
        return selectedTopicId == null ? null : find(selectedTopicId);
    }

    private Tree find(String topicId) {
        for (Tree tree : trees) {
            if (tree.topicId.equals(topicId)) {
                return tree;
            }
        }
        return null;
    }

    /** One tree: the topic's identity, its honest tallies, its style and its spot on the ground. */
    public static final class Tree {

        public final String topicId;
        public final String resumeBranchId;
        public final String title;
        /** Always at least 1: an unknown count is drawn as one segment. */
        public final int branchCount;
        /** -1 while unknown; never summed into a total. */
        public final int archivedCount;
        public final long lastUsedAt;
        public final Persona persona;
        public final ForestLayout.Placement placement;

        public Tree(String topicId, String resumeBranchId, String title, int branchCount,
                int archivedCount, long lastUsedAt, Persona persona,
                ForestLayout.Placement placement) {
            this.topicId = Guard.id(topicId, "topicId");
            this.resumeBranchId = Guard.id(resumeBranchId, "resumeBranchId");
            this.title = Guard.nonBlank(title, "title");
            if (branchCount < 1) {
                throw new IllegalArgumentException("a tree always has its root branch");
            }
            this.branchCount = branchCount;
            this.archivedCount = archivedCount;
            this.lastUsedAt = Guard.epochMillis(lastUsedAt, "lastUsedAt");
            this.persona = persona;
            this.placement = Guard.object(placement, "placement");
        }
    }
}
