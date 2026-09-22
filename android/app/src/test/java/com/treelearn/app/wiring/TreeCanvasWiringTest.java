package com.treelearn.app.wiring;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.treelearn.app.ui.HomeViewState;
import com.treelearn.app.ui.TreeCanvasData;
import com.treelearn.app.ui.TreeCanvasPresenter;
import com.treelearn.app.ui.TreeCanvasSink;
import com.treelearn.app.ui.TreeCanvasViewState;
import com.treelearn.app.ui.UiThread;
import com.treelearn.core.domain.Branch;
import com.treelearn.core.domain.BranchAnchor;
import com.treelearn.core.domain.BranchSource;
import com.treelearn.core.domain.DomainErrorCode;
import com.treelearn.core.domain.DomainResult;
import com.treelearn.treelayout.ViewportConstraints;

import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

/**
 * The tree canvas's real data path: what one topic reads as, what a fold writes, and what a refused
 * read shows.
 *
 * <p>The rules under test are the production ones - {@code TreeCanvasWiring} is the class the
 * application assembles, not a test copy of it. Only the rows are a list, which is what lets the
 * deciding half run without a database. The SQL half, {@code SqliteTreeCanvasStore}, is not covered
 * here: its instrumented suite compiles but never runs, so its SQL is unproven until a device does.
 *
 * <p>The three facts asserted here are the canvas's whole contract with the store: a topic reads as
 * its live branches with the two derived values the geometry needs, an archived branch is not part of
 * the canvas at all, and a fold is the one write - stored as the branch's own flag and readable back.
 * The last case drives the production wiring through the real presenter, because "the learner sees
 * something" is a property of the pair, not of either half alone.
 */
public class TreeCanvasWiringTest {

    private static final String TOPIC = "11111111-1111-4111-8111-111111111111";
    private static final String ROOT = "22222222-2222-4222-8222-222222222222";
    private static final String LEAF = "33333333-3333-4333-8333-333333333333";
    private static final String ARCHIVED = "44444444-4444-4444-8444-444444444444";
    private static final String SNAPSHOT = "55555555-5555-4555-8555-555555555555";
    private static final String ANCHOR_ANSWER = "66666666-6666-4666-8666-666666666666";
    private static final String ANCHOR_REVISION = "77777777-7777-4777-8777-777777777777";
    private static final long NOW = 1760000000000L;

    private static final String SUMMARY_PAYLOAD =
            "{\"summary\":\"第一行摘要\",\"key_points\":[\"要点一\"]}";

    private final Store store = new Store();
    private final TreeCanvasWiring wiring = new TreeCanvasWiring(store);

    // ------------------------------------------------------------------ reads

    @Test
    public void loadReadsTheTopicsLiveBranchesWithTheirSummaryAndRounds() {
        store.add(root("根问题", 0));
        store.add(child(LEAF, ROOT, "叶子", 2));
        store.summary(LEAF, SUMMARY_PAYLOAD);
        store.rounds(LEAF, 3);

        TreeCanvasData.Tree tree = wiring.load(TOPIC);

        assertEquals(TOPIC, tree.topicId);
        assertEquals(2, tree.rows.size());
        assertEquals(ROOT, tree.rows.get(0).branch.id);
        assertEquals("", tree.rows.get(0).summary);
        assertEquals(0, tree.rows.get(0).rounds);
        assertEquals(LEAF, tree.rows.get(1).branch.id);
        assertEquals("第一行摘要", tree.rows.get(1).summary);
        assertEquals(3, tree.rows.get(1).rounds);
        // The branch itself travels, not a re-stated copy: the badge and the fold flag are read off
        // the domain object, so the canvas cannot disagree with the conversation about them.
        assertEquals(2, tree.rows.get(1).branch.unreadCount());
    }

    @Test
    public void anArchivedBranchIsNotPartOfTheCanvas() {
        store.add(root("根问题", 0));
        store.add(child(LEAF, ROOT, "叶子", 0));
        store.add(archived(ARCHIVED, ROOT, "已归档"));

        TreeCanvasData.Tree tree = wiring.load(TOPIC);

        assertEquals(2, tree.rows.size());
        for (TreeCanvasData.Row row : tree.rows) {
            assertFalse(row.branch.isArchived());
            assertFalse(ARCHIVED.equals(row.branch.id));
        }
    }

    @Test
    public void aSummaryIsTheFirstLineOfWhatWasDerived() {
        store.add(root("根问题", 0));
        store.summary(ROOT, "{\"summary\":\"第一行\\n第二行\",\"key_points\":[]}");

        assertEquals("第一行", wiring.load(TOPIC).rows.get(0).summary);
    }

    @Test
    public void aSummaryTheCodecRefusesReadsAsEmptyRatherThanFailingTheCanvas() {
        store.add(root("根问题", 0));
        store.summary(ROOT, "not JSON at all");

        assertEquals("", wiring.load(TOPIC).rows.get(0).summary);
    }

    @Test
    public void topicOfAnswersTheBranchsTopicAndNullWhenTheBranchIsGone() {
        store.add(root("根问题", 0));

        assertEquals(TOPIC, wiring.topicOf(ROOT));
        assertNull(wiring.topicOf(LEAF));
    }

    @Test
    public void aReadThatFailsIsCarriedOutRatherThanTurnedIntoAnEmptyTree() {
        store.fail(new IllegalStateException("the store is unreadable"));

        try {
            wiring.load(TOPIC);
            throw new AssertionError("an unreadable store must not read as an empty topic");
        } catch (RuntimeException expected) {
            assertNotNull(expected.getMessage());
        }
    }

    // ----------------------------------------------------------------- writes

    @Test
    public void aFoldIsStoredOnTheBranchAndReadsBack() {
        store.add(root("根问题", 0));

        DomainResult<Void> result = wiring.setCollapsed(ROOT, true, NOW);

        assertTrue(result.isOk());
        assertEquals(ROOT, store.foldedBranchId);
        assertTrue(store.foldCalled);
        assertTrue(store.foldedTo);
        assertEquals(NOW, store.foldedAt);
        assertTrue(store.branchesOf(TOPIC).get(0).isCollapsed());

        wiring.setCollapsed(ROOT, false, NOW + 1);
        assertFalse(store.branchesOf(TOPIC).get(0).isCollapsed());
    }

    @Test
    public void aRefusedFoldComesBackAsAFailureRatherThanBeingSwallowed() {
        store.add(root("根问题", 0));
        store.refuseFold = true;

        DomainResult<Void> result = wiring.setCollapsed(ROOT, true, NOW);

        assertTrue(result.isFailure());
        assertEquals(DomainErrorCode.OPERATION_NOT_COMPLETABLE, result.error().code);
        assertFalse(store.branchesOf(TOPIC).get(0).isCollapsed());
    }

    // ------------------------------------------------- what the learner sees

    @Test
    public void aFailedReadShowsTheCanvassNoticeInsteadOfABlankCanvas() {
        store.fail(new IllegalStateException("the store is unreadable"));
        RecordingSink sink = new RecordingSink();

        presenterOn(sink).start();

        assertEquals(TreeCanvasViewState.Phase.ERROR, sink.last.phase);
        assertEquals(HomeViewState.Notice.STORAGE, sink.last.notice);
    }

    private TreeCanvasPresenter presenterOn(TreeCanvasSink sink) {
        return new TreeCanvasPresenter(sink, new InlineUi(), new InlineExecutor(), wiring, wiring,
                ViewportConstraints.of(412f, 700f), 2f, LEAF);
    }

    // ------------------------------------------------------------------- rows

    private static Branch root(String title, int unread) {
        // A restored row, not Branch.root(): the create path mints its own id, and this fixture is
        // the branch the store already holds, so the test has to be able to name it. The unread pair
        // is stated the way the domain derives the badge (latest minus seen), and updatedAt is the
        // stamp setTitle() would have written on the same row.
        return Branch.restore(
                ROOT, TOPIC, null, null, title, NOW, NOW + 1, unread, 0, false, null);
    }

    private static Branch child(String id, String parentId, String title, int unread) {
        return Branch.restore(id, TOPIC, parentId, source(parentId), title, NOW, NOW + 1, 5 + unread,
                5, false, null);
    }

    private static Branch archived(String id, String parentId, String title) {
        return Branch.restore(id, TOPIC, parentId, source(parentId), title, NOW, NOW + 1, 5, 5,
                false, Long.valueOf(NOW + 2));
    }

    private static BranchSource source(String parentId) {
        return BranchSource.restore(parentId, ANCHOR_ANSWER, ANCHOR_REVISION,
                BranchAnchor.wholeAnswer(), SNAPSHOT);
    }

    /** The store as a list, so the deciding half runs without a database. */
    private static final class Store implements TreeCanvasStore {

        final List<Branch> branches = new ArrayList<Branch>();
        final Map<String, String> summaries = new HashMap<String, String>();
        final Map<String, Integer> rounds = new HashMap<String, Integer>();
        RuntimeException failure;
        boolean refuseFold;
        String foldedBranchId;
        boolean foldCalled;
        boolean foldedTo;
        long foldedAt;

        void add(Branch branch) {
            branches.add(branch);
        }

        void summary(String branchId, String payload) {
            summaries.put(branchId, payload);
        }

        void rounds(String branchId, int count) {
            rounds.put(branchId, Integer.valueOf(count));
        }

        void fail(RuntimeException unreadable) {
            this.failure = unreadable;
        }

        @Override
        public String topicOf(String branchId) {
            readable();
            for (Branch branch : branches) {
                if (branch.id.equals(branchId)) {
                    return branch.topicId;
                }
            }
            return null;
        }

        @Override
        public List<Branch> branchesOf(String topicId) {
            readable();
            return branches;
        }

        @Override
        public String summaryPayload(String branchId) {
            readable();
            return summaries.get(branchId);
        }

        @Override
        public int committedRounds(String branchId) {
            readable();
            Integer count = rounds.get(branchId);
            return count == null ? 0 : count.intValue();
        }

        @Override
        public DomainResult<Void> setCollapsed(String branchId, boolean collapsed, long now) {
            readable();
            foldedBranchId = branchId;
            foldCalled = true;
            foldedTo = collapsed;
            foldedAt = now;
            if (refuseFold) {
                return DomainResult.fail(DomainErrorCode.OPERATION_NOT_COMPLETABLE, "fixture refusal");
            }
            for (Branch branch : branches) {
                if (branch.id.equals(branchId)) {
                    branch.setCollapsed(collapsed, now);
                }
            }
            return DomainResult.ok();
        }

        private void readable() {
            if (failure != null) {
                throw failure;
            }
        }
    }

    /** Runs the callback now: the presenter's threading split is not what this test is about. */
    private static final class InlineUi implements UiThread {

        @Override
        public void post(Runnable action) {
            action.run();
        }
    }

    private static final class InlineExecutor implements Executor {

        @Override
        public void execute(Runnable command) {
            command.run();
        }
    }

    private static final class RecordingSink implements TreeCanvasSink {

        TreeCanvasViewState last;

        @Override
        public void render(TreeCanvasViewState state) {
            last = state;
        }

        @Override
        public void openBranch(String branchId) {
        }

        @Override
        public void openNavigation(String branchId) {
        }

        @Override
        public void leaveTree() {
        }
    }
}
