package com.treelearn.treelayout;

import java.util.ArrayList;
import java.util.List;

/**
 * Test fixtures and viewport stand-ins for the pure-JVM tree layout.
 *
 * <p>Not production code. Everything here is deterministic: the same call returns the same tree, so
 * every failing assertion names the same input.
 */
final class Fixtures {

    /** Phone portrait content area (the prototype draws into a 412 px wide phone with a 24 px gutter). */
    static final ViewportConstraints PHONE = ViewportConstraints.of(412f, 700f);

    /** Tablet landscape content area of the dual-pane shell. */
    static final ViewportConstraints TABLET = ViewportConstraints.of(1024f, 640f);

    private Fixtures() {
    }

    static N n(String id, String parent) {
        return new N(id, parent);
    }

    /** Mutable builder for one {@link LayoutNode}; keeps the test bodies readable. */
    static final class N {
        private final String id;
        private final String parent;
        private final String title = "节点";
        private String summary = "";
        private int rounds;
        private int unread;
        private boolean collapsed;
        private boolean archived;

        private N(String id, String parent) {
            this.id = id;
            this.parent = parent;
        }

        N rounds(int value) {
            this.rounds = value;
            return this;
        }

        N unread(int value) {
            this.unread = value;
            return this;
        }

        N summary(String value) {
            this.summary = value;
            return this;
        }

        N collapsed() {
            this.collapsed = true;
            return this;
        }

        N archived() {
            this.archived = true;
            return this;
        }

        LayoutNode build() {
            return new LayoutNode(id, parent, title, summary, rounds, unread, collapsed, archived);
        }
    }

    static TreeSnapshot snapshot(String rootId, N... nodes) {
        List<LayoutNode> built = new ArrayList<>();
        for (N node : nodes) {
            built.add(node.build());
        }
        return TreeSnapshot.of(rootId, built);
    }

    /**
     * The fixed snapshot the golden geometry assertions are computed from:
     *
     * <pre>
     * r (unread 1)
     * |- a  rounds 1
     * |  |- a1  unread 2, summary               -> the only visible leaf
     * |- b  rounds 2, unread 1                  -> BUD when collapsed, LIMB when expanded
     * |  |- b1  rounds 1, unread 2
     * |  |- b2  unread 1
     * |- c  archived                            -> leaves the default layout
     *    |- c1  rounds 3
     * </pre>
     */
    static TreeSnapshot fixtureA(boolean bCollapsed) {
        N b = n("b", "r").rounds(2).unread(1);
        if (bCollapsed) {
            b.collapsed();
        }
        return snapshot("r",
                n("r", null).unread(1),
                n("a", "r").rounds(1),
                n("a1", "a").unread(2).summary("摘要一行"),
                b,
                n("b1", "b").rounds(1).unread(2),
                n("b2", "b").unread(1),
                n("c", "r").archived(),
                n("c1", "c").rounds(3));
    }

    static TreeSnapshot fixtureA() {
        return fixtureA(true);
    }

    /** The same root with the summary removed, to prove the near tier does not invent a line. */
    static TreeSnapshot fixtureAWithoutSummary() {
        return snapshot("r",
                n("r", null).unread(1),
                n("a", "r").rounds(1),
                n("a1", "a").unread(2),
                n("b", "r").rounds(2).unread(1).collapsed(),
                n("b1", "b").rounds(1).unread(2),
                n("b2", "b").unread(1));
    }

    /** Two sibling leaves under the root: the fan-collapse delta fixture. */
    static TreeSnapshot twoLeaves(boolean qArchived) {
        N q = n("q", "r");
        if (qArchived) {
            q.archived();
        }
        return snapshot("r", n("r", null), n("p", "r"), q);
    }

    /** {@code count} leaf children under the root, each with the given number of committed rounds. */
    static TreeSnapshot wideLeaves(int count, int rounds) {
        List<N> nodes = new ArrayList<>();
        nodes.add(n("r", null));
        for (int i = 0; i < count; i++) {
            nodes.add(n("w" + i, "r").rounds(rounds));
        }
        return snapshot("r", nodes.toArray(new N[0]));
    }

    /** A regular {@code branching}-ary tree of the given depth, built breadth-first. */
    static TreeSnapshot balanced(int branching, int depth) {
        List<N> nodes = new ArrayList<>();
        nodes.add(n("k0", null));
        int next = 1;
        int levelStart = 0;
        int levelEnd = 1;
        for (int level = 1; level <= depth; level++) {
            int nextLevelStart = next;
            for (int parent = levelStart; parent < levelEnd; parent++) {
                for (int child = 0; child < branching; child++) {
                    nodes.add(n("k" + next, "k" + parent));
                    next++;
                }
            }
            levelStart = nextLevelStart;
            levelEnd = next;
        }
        return snapshot("k0", nodes.toArray(new N[0]));
    }

    /** A single chain of {@code length} branches, one child each. */
    static TreeSnapshot chain(int length) {
        List<N> nodes = new ArrayList<>();
        for (int i = 0; i < length; i++) {
            nodes.add(n("n" + i, i == 0 ? null : "n" + (i - 1)));
        }
        return snapshot("n0", nodes.toArray(new N[0]));
    }

    static String lastId(TreeSnapshot snapshot) {
        List<LayoutNode> nodes = snapshot.nodes();
        return nodes.get(nodes.size() - 1).id;
    }

    /**
     * The leaf shape as an axis-aligned box, computed here from the prototype's own constants
     * (leaf length 34, leaf width 15) instead of from a value the layout hands back, so that
     * "labels never cover a leaf" stays an independent judgement rather than a restatement.
     */
    static float[] leafBox(BranchGeometry branch) {
        float forwardX = (float) Math.sin(branch.direction);
        float forwardY = -(float) Math.cos(branch.direction);
        float sideX = (float) Math.cos(branch.direction);
        float sideY = (float) Math.sin(branch.direction);
        float halfWidth = 7.5f;
        float tipX = branch.tipX + forwardX * 34f;
        float tipY = branch.tipY + forwardY * 34f;
        float[] xs = {
                branch.tipX + sideX * halfWidth, branch.tipX - sideX * halfWidth,
                tipX + sideX * halfWidth, tipX - sideX * halfWidth};
        float[] ys = {
                branch.tipY + sideY * halfWidth, branch.tipY - sideY * halfWidth,
                tipY + sideY * halfWidth, tipY - sideY * halfWidth};
        float minX = xs[0];
        float maxX = xs[0];
        float minY = ys[0];
        float maxY = ys[0];
        for (int i = 1; i < 4; i++) {
            minX = Math.min(minX, xs[i]);
            maxX = Math.max(maxX, xs[i]);
            minY = Math.min(minY, ys[i]);
            maxY = Math.max(maxY, ys[i]);
        }
        return new float[] {minX, minY, maxX, maxY};
    }

    static float leafTipX(BranchGeometry branch) {
        return branch.tipX + (float) Math.sin(branch.direction) * 34f;
    }

    static float leafTipY(BranchGeometry branch) {
        return branch.tipY - (float) Math.cos(branch.direction) * 34f;
    }

    static boolean overlaps(float[] a, float[] b) {
        return a[0] < b[2] && a[2] > b[0] && a[1] < b[3] && a[3] > b[1];
    }
}
