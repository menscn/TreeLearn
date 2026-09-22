package com.treelearn.app.ui;

import com.treelearn.core.domain.Branch;
import com.treelearn.core.domain.BranchAnchor;
import com.treelearn.core.domain.BranchSource;
import com.treelearn.core.domain.DomainErrorCode;
import com.treelearn.core.domain.DomainResult;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

/**
 * Fixtures for the tree canvas suite: domain branches with controlled unread, and the three ports the
 * presenter talks to.
 *
 * <p>The branches are built through {@code Branch.restore} rather than through {@code Branch.child},
 * because unread is a sequence gap and a fixture has to be able to state that gap directly: two
 * answers with one seen is one unread, and no amount of calling {@code onStableAnswer} expresses
 * "and the learner already read one of them".
 *
 * <p>{@link FakeCommands} writes the collapse through to {@link FakeData}, so a test can assert the
 * whole loop - command, re-read, new geometry - instead of only that a method was called.
 */
final class TreeCanvasDoubles {

    static final String TOPIC = "0f0f0f0f-0f0f-4f0f-8f0f-0f0f0f0f0f0f";
    static final String ROOT = "11111111-1111-4111-8111-111111111111";
    static final String LEAF_A = "22222222-2222-4222-8222-222222222222";
    static final String LEAF_B = "33333333-3333-4333-8333-333333333333";
    static final String BUD = "44444444-4444-4444-8444-444444444444";
    static final String HIDDEN = "55555555-5555-4555-8555-555555555555";
    static final String ARCHIVED = "66666666-6666-4666-8666-666666666666";
    static final String SNAPSHOT = "77777777-7777-4777-8777-777777777777";
    static final long T0 = 1_700_000_000_000L;

    /** A phone portrait canvas, the configuration the prototype draws for. */
    static final float PHONE_W = 412f;
    static final float PHONE_H = 700f;
    /** A tablet landscape canvas wide enough for the two-column shell. */
    static final float TABLET_W = 1024f;
    static final float TABLET_H = 640f;

    private TreeCanvasDoubles() {
    }

    /** Runs the callback now: the presenter's threading split is not what these tests are about. */
    static final class InlineUi implements UiThread {

        @Override
        public void post(Runnable action) {
            action.run();
        }
    }

    static final class DirectExecutor implements Executor {

        @Override
        public void execute(Runnable command) {
            command.run();
        }
    }

    /** A root with {@code unread} unseen stable answers. */
    static Branch root(String title, int unread) {
        Branch branch = Branch.root(TOPIC, T0);
        for (int i = 0; i < unread; i++) {
            branch.onStableAnswer(T0 + 1 + i);
        }
        branch.setTitle(title, T0 + 1);
        return branch;
    }

    /** A child whose unread is exactly {@code unread}, whatever the base sequence is. */
    static Branch child(String id, String parentId, String title, int unread, boolean collapsed,
            boolean archived) {
        BranchSource source = BranchSource.restore(parentId, LEAF_B, LEAF_A, BranchAnchor.wholeAnswer(),
                SNAPSHOT);
        long latest = 5 + unread;
        return Branch.restore(id, TOPIC, parentId, source, title, T0, T0 + 1, latest, 5, collapsed,
                archived ? Long.valueOf(T0 + 2) : null);
    }

    static final class FakeData implements TreeCanvasData {

        final List<Branch> branches = new ArrayList<>();
        final Map<String, String> summaries = new LinkedHashMap<>();
        final Map<String, Integer> rounds = new LinkedHashMap<>();
        RuntimeException failure;
        /** Null models a branch whose topic row is gone; the canvas then has nothing to draw. */
        String topic = TOPIC;
        int loads;

        @Override
        public String topicOf(String branchId) {
            if (failure != null) {
                throw failure;
            }
            return topic;
        }

        @Override
        public Tree load(String topicId) {
            loads++;
            if (failure != null) {
                throw failure;
            }
            List<Row> rows = new ArrayList<>();
            for (Branch branch : branches) {
                String summary = summaries.get(branch.id);
                Integer count = rounds.get(branch.id);
                rows.add(new Row(branch, summary == null ? "" : summary,
                        count == null ? 0 : count.intValue()));
            }
            return new Tree(topicId, rows);
        }
    }

    /** Writes the collapse through to the fake store, or refuses, so both arms are assertable. */
    static final class FakeCommands implements TreeCanvasCommands {

        final List<String> calls = new ArrayList<>();
        final FakeData data;
        boolean refuse;

        FakeCommands(FakeData data) {
            this.data = data;
        }

        @Override
        public DomainResult<Void> setCollapsed(String branchId, boolean collapsed, long now) {
            calls.add(branchId + ":" + collapsed);
            if (refuse) {
                return DomainResult.fail(DomainErrorCode.OPERATION_NOT_COMPLETABLE, "fixture refusal");
            }
            for (Branch branch : data.branches) {
                if (branch.id.equals(branchId)) {
                    branch.setCollapsed(collapsed, now);
                }
            }
            return DomainResult.ok();
        }
    }

    static final class FakeSink implements TreeCanvasSink {

        TreeCanvasViewState last;
        final List<String> opened = new ArrayList<>();
        boolean leftTree;
        final List<String> navigationsOpened = new ArrayList<>();
        int renders;

        @Override
        public void render(TreeCanvasViewState state) {
            last = state;
            renders++;
        }

        @Override
        public void openBranch(String branchId) {
            opened.add(branchId);
        }

        @Override
        public void openNavigation(String branchId) {
            navigationsOpened.add(branchId);
        }

        @Override
        public void leaveTree() {
            leftTree = true;
        }
    }

    /**
     * The one tree every suite works from, so a failure names the same shape everywhere:
     *
     * <pre>
     * root      unread 1
     * |- A      rounds 2, unread 0, summary      -> the only visible leaf with a plate
     * |- B      collapsed, one hidden child      -> BUD carrying 2 unseen answers
     * |  |- H   unread 2                         -> never visible
     * |- C      archived, unread 3               -> never visible, and its unread never leaks
     * </pre>
     */
    static FakeData standard() {
        FakeData data = new FakeData();
        Branch root = root("根问题", 1);
        data.branches.add(root);
        data.branches.add(child(LEAF_A, root.id, "叶子 A", 0, false, false));
        data.branches.add(child(LEAF_B, root.id, "枝 B", 0, true, false));
        data.branches.add(child(HIDDEN, LEAF_B, "藏在芽里", 2, false, false));
        data.branches.add(child(ARCHIVED, root.id, "已归档", 3, false, true));
        data.summaries.put(LEAF_A, "摘要一行");
        data.rounds.put(LEAF_A, 2);
        return data;
    }

    /** The single branch of {@code data} that has no parent. */
    static String rootIdOf(FakeData data) {
        for (Branch branch : data.branches) {
            if (branch.parentBranchId == null) {
                return branch.id;
            }
        }
        return null;
    }

    /** A presenter on {@link #standard()}, wired to run every read and write inline. */
    static TreeCanvasPresenter presenterOf(FakeSink sink, FakeData data, FakeCommands commands,
            float width, float height, String branchId) {
        return new TreeCanvasPresenter(sink, new InlineUi(), new DirectExecutor(), data, commands,
                com.treelearn.treelayout.ViewportConstraints.of(width, height), 2.0f, branchId);
    }
}
