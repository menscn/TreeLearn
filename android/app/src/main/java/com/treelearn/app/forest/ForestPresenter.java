package com.treelearn.app.forest;

import com.treelearn.app.ui.UiThread;
import com.treelearn.core.domain.Guard;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

/**
 * The forest page's state machine (T-068): one read becomes one of four phases.
 *
 * <p>The presenter owns three judgements, and nothing else:
 * <ul>
 *   <li><strong>What is a total.</strong> Every count was read, or the totals are null. A short sum
 *       would quietly claim the forest is smaller than it is.</li>
 *   <li><strong>What a tap means.</strong> A tree selects; the sheet's arrow opens that tree's own
 *       resume branch through the host's existing conversation door. Looking at the forest never
 *       creates a second navigation target.</li>
 *   <li><strong>What survives a re-read.</strong> A selection stays only while its tree does; a
 *       vanished tree takes the sheet with it.</li>
 * </ul>
 *
 * <p>The ground's geometry is {@link ForestLayout}'s, not this class's: a position is a pure
 * function of the topic id, so reading the forest again cannot move a tree.
 */
public final class ForestPresenter {

    /** The render and navigation ports; opening a tree is the host's own room method. */
    public interface Sink {

        void render(ForestViewState next);

        void openBranch(String branchId);
    }

    private final Sink sink;
    private final ForestData data;
    private final UiThread ui;
    private final Executor background;
    private ForestViewState state;

    public ForestPresenter(Sink sink, ForestData data) {
        this(sink, data, null, null);
    }

    public ForestPresenter(Sink sink, ForestData data, UiThread ui, Executor background) {
        this.sink = Guard.object(sink, "sink");
        this.data = Guard.object(data, "data");
        this.ui = ui;
        this.background = background;
        this.state = ForestViewState.loading();
    }

    public ForestViewState state() {
        return state;
    }

    /** First load: the initial state is the loading phase; the read replaces it when it lands. */
    public void start() {
        load();
    }

    /** A retry, and every entry back into the page: both go through the same read. */
    public void refresh() {
        load();
    }

    /** A tap selects; it never opens. The arrow below is the only way into a tree. */
    public void onTreeTapped(String topicId) {
        Guard.id(topicId, "topicId");
        if (state.phase != ForestViewState.Phase.READY || find(topicId) == null) {
            throw new IllegalArgumentException("no such tree on the ground: " + topicId);
        }
        render(state.withSelected(topicId));
    }

    /** The sheet's arrow: the selected tree's own resume branch, through the host's chat door. */
    public void onSelectedOpened() {
        ForestViewState.Tree selected = state.selected();
        if (selected != null) {
            sink.openBranch(selected.resumeBranchId);
        }
    }

    private void load() {
        if (ui != null && background != null) {
            background.execute(() -> {
                ForestViewState next = readForest();
                ui.post(() -> render(next));
            });
            return;
        }
        render(readForest());
    }

    private ForestViewState readForest() {
        try {
            return build(data.read());
        } catch (RuntimeException broken) {
            // A broken topic read is the page's error phase, never a crash and never a silent empty
            // ground: the learner can tell "nothing planted" from "could not be read".
            return ForestViewState.error();
        }
    }

    private ForestViewState build(ForestData.Snapshot snapshot) {
        if (snapshot == null || snapshot.topics.isEmpty()) {
            return ForestViewState.empty();
        }
        List<String> topicIds = new ArrayList<String>(snapshot.topics.size());
        List<Integer> counts = new ArrayList<Integer>(snapshot.topics.size());
        Integer segments = Integer.valueOf(0);
        Integer archived = Integer.valueOf(0);
        boolean everyCountKnown = true;
        for (ForestData.Topic topic : snapshot.topics) {
            topicIds.add(topic.topicId);
            counts.add(topic.drawableBranchCount());
            if (!topic.countKnown) {
                everyCountKnown = false;
                continue;
            }
            segments = Integer.valueOf(segments.intValue() + topic.branchCount);
            archived = Integer.valueOf(archived.intValue() + Math.max(0, topic.archivedCount));
        }
        if (!everyCountKnown) {
            segments = null;
            archived = null;
        }

        List<ForestLayout.Placement> placements = ForestLayout.compute(topicIds, counts);
        List<ForestViewState.Tree> trees = new ArrayList<ForestViewState.Tree>(topicIds.size());
        for (int i = 0; i < topicIds.size(); i++) {
            ForestData.Topic topic = snapshot.topics.get(i);
            trees.add(new ForestViewState.Tree(topic.topicId, topic.resumeBranchId, topic.title,
                    topic.drawableBranchCount(), topic.countKnown ? topic.archivedCount : -1,
                    topic.lastUsedAt, topic.persona, placements.get(i)));
        }
        String selection = state == null ? null : state.selectedTopicId;
        if (selection != null && !hasTree(trees, selection)) {
            selection = null;
        }
        return ForestViewState.ready(trees, segments, archived, snapshot.streakDays, selection);
    }

    private static boolean hasTree(List<ForestViewState.Tree> trees, String topicId) {
        for (ForestViewState.Tree tree : trees) {
            if (tree.topicId.equals(topicId)) {
                return true;
            }
        }
        return false;
    }

    private ForestViewState.Tree find(String topicId) {
        for (ForestViewState.Tree tree : state.trees) {
            if (tree.topicId.equals(topicId)) {
                return tree;
            }
        }
        return null;
    }

    private void render(ForestViewState next) {
        state = Guard.object(next, "next");
        if (ui != null && background != null) {
            ui.post(() -> sink.render(state));
            return;
        }
        sink.render(state);
    }
}
