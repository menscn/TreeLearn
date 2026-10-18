package com.treelearn.app.navigation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.treelearn.app.ui.UiThread;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executor;

/**
 * The navigation presenter's decisions, asserted on the states a screen would draw.
 *
 * <p>The acceptance criteria this covers: an archived branch leaving the default list while staying
 * recoverable, a permanent deletion that names its exact scope and cancels the scoped attempts
 * before one atomic cascade, a selection that falls back to the nearest visible ancestor, a
 * selection restored from a branch id alone, and a summary that is always the current branch's own.
 *
 * <p>The two doubles that matter are the manual executor and the fake store: nothing is
 * asynchronous here, so a test can decide the exact order in which a background read lands - which
 * is the only way to prove that a read dispatched before a deletion cannot rebuild the deleted row.
 */
public final class NavigationPresenterTest {

    private static final String TOPIC = "11111111-1111-4111-8111-111111111111";
    private static final String ROOT = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa";
    private static final String BRANCH_A = "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb";
    private static final String BRANCH_A1 = "cccccccc-cccc-4ccc-8ccc-cccccccccccc";
    private static final String BRANCH_B = "dddddddd-dddd-4ddd-8ddd-dddddddddddd";
    private static final String SIBLING = "eeeeeeee-eeee-4eee-8eee-eeeeeeeeeeee";
    private static final String ORPHAN = "99999999-9999-4999-8999-999999999999";
    private static final String ABSENT_PARENT = "00000000-0000-4000-8000-000000000000";
    private static final String UNTITLED = "eeeeeeee-eeee-4eee-8eee-eeeeeeeeeeee";

    private FakeStore store;
    private RecordingSink sink;
    private InlineUi ui;
    private ManualExecutor background;
    private NavigationPresenter presenter;

    @Before
    public void setUp() {
        store = new FakeStore();
        sink = new RecordingSink();
        ui = new InlineUi();
        background = new ManualExecutor();
        presenter = new NavigationPresenter(sink, ui, background, store, TOPIC, null);
    }

    @Test
    public void startReadsTheStoreOffTheUiThreadAndShowsTheLoadingPhaseFirst() {
        presenter.start();

        assertEquals(NavigationViewState.Phase.LOADING, sink.states.get(0).phase);
        assertEquals("the read must be dispatched, not taken on the calling thread",
                1, background.size());

        background.drain();

        assertEquals(NavigationViewState.Phase.READY, sink.last().phase);
        assertEquals(4, sink.last().visible.size());
        assertEquals("an archived branch leaves the default list", 1, sink.last().archived.size());
        assertEquals(SIBLING, sink.last().archived.get(0).branchId);
    }

    @Test
    public void archivingHidesTheBranchWithoutCancellingOrDeletingAnything() {
        settle();
        presenter.onBranchSelected(BRANCH_B);
        presenter.onArchive(BRANCH_B);
        background.drain();

        assertEquals(Collections.singletonList(BRANCH_B + "=true"), store.archiveCalls);
        assertTrue("archiving is not deleting", store.deleteCalls.isEmpty());
        assertTrue("archiving never cancels a running attempt", store.cancelCalls.isEmpty());
        assertFalse(shows(sink.last().visible, BRANCH_B));
        assertTrue("an archived branch stays recoverable", shows(sink.last().archived, BRANCH_B));

        presenter.onRestore(BRANCH_B);
        background.drain();

        assertEquals(Arrays.asList(BRANCH_B + "=true", BRANCH_B + "=false"), store.archiveCalls);
        assertTrue(shows(sink.last().visible, BRANCH_B));
    }

    @Test
    public void archivingSomethingAlreadyArchivedIsRefusedAndWritesNothing() {
        settle();

        presenter.onArchive(SIBLING);

        assertEquals(NavigationViewState.Notice.ALREADY_ARCHIVED, sink.last().notice);
        assertTrue(store.order.isEmpty());
    }

    @Test
    public void restoringSomethingNotArchivedIsRefusedAndWritesNothing() {
        settle();

        presenter.onRestore(BRANCH_B);

        assertEquals(NavigationViewState.Notice.NOT_ARCHIVED, sink.last().notice);
        assertTrue(store.order.isEmpty());
    }

    @Test
    public void deletingAsksForConfirmationAndNamesTheExactSubtreeBeforeAnythingIsWritten() {
        settle();
        presenter.onBranchSelected(BRANCH_A1);

        presenter.onDeleteRequested(BRANCH_A, NavigationActions.DeleteScope.SUBTREE);

        NavigationData.Plan plan = sink.last().pendingDelete;
        assertNotNull("the confirmation must name the scope it will remove", plan);
        assertEquals(BRANCH_A, plan.targetBranchId);
        assertEquals(Arrays.asList(BRANCH_A, BRANCH_A1), plan.branchIds);
        assertEquals(2, plan.branchCount());
        assertFalse(plan.wholeTopic);
        assertTrue("opening a confirmation must write nothing", store.order.isEmpty());
    }

    @Test
    public void deletingAWholeTopicNamesEveryBranchItRemoves() {
        settle();

        presenter.onDeleteRequested(ROOT, NavigationActions.DeleteScope.TOPIC);

        NavigationData.Plan plan = sink.last().pendingDelete;
        assertNotNull(plan);
        assertTrue(plan.wholeTopic);
        assertEquals(5, plan.branchCount());
        assertTrue(plan.branchIds.containsAll(
                Arrays.asList(ROOT, BRANCH_A, BRANCH_A1, BRANCH_B, SIBLING)));
    }

    @Test
    public void cancellingTheConfirmationLeavesTheStoreUntouched() {
        settle();
        presenter.onDeleteRequested(BRANCH_A, NavigationActions.DeleteScope.SUBTREE);

        presenter.onDeleteCancelled();

        assertNull(sink.last().pendingDelete);
        assertTrue(store.order.isEmpty());
    }

    @Test
    public void confirmingCancelsTheScopedAttemptsBeforeItDeletesThemInOneCall() {
        settle();
        presenter.onDeleteRequested(BRANCH_A, NavigationActions.DeleteScope.SUBTREE);

        presenter.onDeleteConfirmed();
        background.drain();

        assertEquals("the scoped work is cancelled first, then one atomic cascade removes it",
                Arrays.asList("cancel", "delete"), store.order);
        assertEquals("the whole scope goes in one call, never row by row",
                Collections.singletonList(BRANCH_A + "," + BRANCH_A1), store.deleteCalls);
    }

    @Test
    public void deletingTheCurrentBranchNavigatesToTheNearestVisibleAncestor() {
        settle();
        presenter.onBranchSelected(BRANCH_A1);
        presenter.onDeleteRequested(BRANCH_A1, NavigationActions.DeleteScope.SUBTREE);

        presenter.onDeleteConfirmed();
        background.drain();

        assertEquals(BRANCH_A, sink.last().selectedBranchId);
        assertEquals(NavigationViewState.Phase.READY, sink.last().phase);
        assertFalse(shows(sink.last().visible, BRANCH_A1));
        assertEquals("the tree still has a page to show, so the screen stays", 0, sink.left);
    }

    @Test
    public void deletingWhenNoVisibleAncestorRemainsLeavesTheTree() {
        settle();
        presenter.onBranchSelected(BRANCH_A1);
        presenter.onArchive(ROOT);
        background.drain();
        presenter.onArchive(BRANCH_A);
        background.drain();
        presenter.onDeleteRequested(BRANCH_A1, NavigationActions.DeleteScope.SUBTREE);

        presenter.onDeleteConfirmed();
        background.drain();

        assertEquals("every ancestor is archived, so no visible node is left to select",
                1, sink.left);
    }

    @Test
    public void deletingTheWholeTopicLeavesTheTree() {
        settle();
        presenter.onDeleteRequested(ROOT, NavigationActions.DeleteScope.TOPIC);

        presenter.onDeleteConfirmed();
        background.drain();

        assertEquals(1, sink.left);
        assertEquals(NavigationViewState.Phase.MISSING, sink.last().phase);
    }

    @Test
    public void anUnresolvableTreeShapeFailsClosedInsteadOfOfferingAShortConfirmation() {
        store.orphanNode = true;
        settle();

        presenter.onDeleteRequested(BRANCH_A, NavigationActions.DeleteScope.SUBTREE);

        assertEquals(NavigationViewState.Notice.SCOPE_UNRESOLVABLE, sink.last().notice);
        assertNull("a scope that cannot be counted must not be offered", sink.last().pendingDelete);
        assertTrue(store.order.isEmpty());
    }

    @Test
    public void aListReadThatLandsAfterTheDeletionCannotRebuildTheDeletedRow() {
        settle();
        presenter.onBranchSelected(BRANCH_A1);
        presenter.onResumed();
        int staleRead = background.last();
        presenter.onDeleteRequested(BRANCH_A1, NavigationActions.DeleteScope.SUBTREE);
        presenter.onDeleteConfirmed();
        int deletion = background.last();

        background.runIndex(deletion);
        int reload = background.last();
        background.runIndex(reload);
        background.runIndex(staleRead);

        assertFalse("a read dispatched before the deletion must not put the row back",
                shows(sink.last().visible, BRANCH_A1));
        assertEquals(BRANCH_A, sink.last().selectedBranchId);
    }

    @Test
    public void theSummaryShownIsTheCurrentBranchsOwnAndAFailedReadKeepsTheLastGoodOne() {
        store.summaryText = "合成小结";
        store.summaryPoints = Arrays.asList("合成要点一", "合成要点二");
        settle();

        presenter.onBranchSelected(BRANCH_A);
        background.drain();

        assertEquals(BRANCH_A, sink.last().summaryFor);
        assertEquals("合成小结", sink.last().summary);
        assertEquals(Arrays.asList("合成要点一", "合成要点二"), sink.last().keyPoints);
        assertFalse(sink.last().summaryStale);

        store.summaryThrows = true;
        presenter.onResumed();
        background.drain();

        assertEquals("a failed read keeps the last good result on screen",
                "合成小结", sink.last().summary);
        assertTrue(sink.last().summaryStale);
        assertEquals(BRANCH_A, sink.last().summaryFor);

        presenter.onBranchSelected(BRANCH_B);
        background.drain();

        assertNull("another branch's summary must never be shown here",
                sink.last().summaryFor);
        assertEquals("", sink.last().summary);
        assertTrue(sink.last().keyPoints.isEmpty());
        for (String asked : store.summaryAsked) {
            assertTrue("no sibling and no descendant may ever be asked for a summary: " + asked,
                    asked.equals(ROOT) || asked.equals(BRANCH_A) || asked.equals(BRANCH_B));
        }
        assertEquals("the current branch is what the last read was about",
                BRANCH_B, store.summaryAsked.get(store.summaryAsked.size() - 1));
    }

    @Test
    public void theSelectionIsRestoredFromTheBranchIdAlone() {
        settle();
        presenter.onBranchSelected(BRANCH_A1);
        background.drain();

        assertEquals("the only thing a rotation has to keep is the id",
                BRANCH_A1, presenter.selectedBranchId());

        NavigationPresenter restored = new NavigationPresenter(sink, ui, background, store, TOPIC,
                presenter.selectedBranchId());
        restored.start();
        background.drain();

        assertEquals(BRANCH_A1, sink.last().selectedBranchId);
    }

    @Test
    public void aSeedThatNoLongerExistsResolvesToTheRootInsteadOfAStaleReference() {
        store.nodes.removeIf(node -> node.branchId.equals(BRANCH_A1));
        NavigationPresenter restored = new NavigationPresenter(sink, ui, background, store, TOPIC,
                BRANCH_A1);

        restored.start();
        background.drain();

        assertEquals(ROOT, sink.last().selectedBranchId);
        assertEquals(ROOT, restored.selectedBranchId());
    }

    @Test
    public void aTopicThatNoLongerExistsLeavesTheTree() {
        store.nodes.clear();

        presenter.start();
        background.drain();

        assertEquals(NavigationViewState.Phase.MISSING, sink.last().phase);
        assertEquals("there is nothing left to navigate", 1, sink.left);
    }

    @Test
    public void anUnreadableStoreShowsTheErrorPhaseAndKeepsTheSelection() {
        settle();
        presenter.onBranchSelected(BRANCH_A);
        store.loadThrows = true;

        presenter.onResumed();
        background.drain();

        assertEquals(NavigationViewState.Phase.ERROR, sink.last().phase);
        assertEquals(NavigationViewState.Notice.STORE_UNREADABLE, sink.last().notice);
        assertEquals("the selection is kept so a retry does not lose the learner's place",
                BRANCH_A, sink.last().selectedBranchId);
    }

    @Test
    public void aBranchNotYetNamedIsStillReadableAndStaysInTheList() {
        // 手机上的真实形状：child branch 出生时没有标题，标题要等派生工件写回（Branch.child 不收标题）。
        // 画布早已按空标题画出这类节点，分支导航不能因为同一个形状把整棵树读成「本机数据读不出来」。
        store.nodes.add(new NavigationData.Node(UNTITLED, BRANCH_A, null, false));
        presenter.start();
        background.drain();

        assertEquals(NavigationViewState.Phase.READY, sink.last().phase);
        assertNull("a readable tree draws no error notice", sink.last().notice);
        assertTrue("an unnamed branch is still a branch", shows(sink.last().visible, UNTITLED));
        assertEquals("an unnamed branch reads as an empty label, not as a crash",
                "", rowOf(sink.last().visible, UNTITLED).title);
    }

    @Test
    public void anUnreadableStoreNamesTheCauseToTheSinkAndNotOnlyToTheScreen() {
        settle();
        store.loadThrows = true;
        presenter.onResumed();
        background.drain();

        assertEquals(NavigationViewState.Notice.STORE_UNREADABLE, sink.last().notice);
        assertEquals("the cause must reach the diagnostics port exactly once per failed read",
                1, sink.unreadable.size());
        assertTrue("the report names the exception class, so a log line identifies the failure",
                sink.unreadable.get(0).contains("IllegalStateException"));
    }

    @Test
    public void foldingAGroupHidesItsDescendantsButKeepsTheGroupItself() {
        settle();
        assertEquals("the seed tree starts unfolded", Collections.<String>emptyList(),
                sink.last().collapsed);
        assertTrue(shows(sink.last().visible, BRANCH_A1));

        presenter.onGroupToggled(ROOT);

        assertTrue("the folded group itself stays on the list", shows(sink.last().visible, ROOT));
        assertFalse("a folded group hides its children", shows(sink.last().visible, BRANCH_A));
        assertFalse("and their descendants too", shows(sink.last().visible, BRANCH_A1));
        assertFalse(shows(sink.last().visible, BRANCH_B));
        assertEquals(Collections.singletonList(ROOT), sink.last().collapsed);
        assertTrue("folding is a view fact, never a write", store.order.isEmpty());

        presenter.onGroupToggled(ROOT);

        assertTrue(shows(sink.last().visible, BRANCH_A1));
        assertTrue(sink.last().collapsed.isEmpty());
    }

    @Test
    public void foldingANestedGroupHidesOnlyItsOwnSubtree() {
        settle();

        presenter.onGroupToggled(BRANCH_A);

        assertTrue(shows(sink.last().visible, BRANCH_A));
        assertFalse(shows(sink.last().visible, BRANCH_A1));
        assertTrue("another group is not affected by this fold",
                shows(sink.last().visible, BRANCH_B));
        assertTrue("the root stays expanded", shows(sink.last().visible, ROOT));
    }

    @Test
    public void foldingTheSummaryStripKeepsTheTextAndOnlyHidesIt() {
        settle();
        store.summaryText = "SYNTHETIC-SUMMARY";
        presenter.onBranchSelected(BRANCH_A);
        background.drain();

        presenter.onSummaryToggled();

        assertTrue(sink.last().summaryCollapsed);
        assertEquals("folding the strip must not drop the text it holds",
                "SYNTHETIC-SUMMARY", sink.last().summary);

        presenter.onSummaryToggled();

        assertFalse(sink.last().summaryCollapsed);
        assertEquals("SYNTHETIC-SUMMARY", sink.last().summary);
    }

    private void settle() {
        presenter.start();
        background.drain();
    }

    private static NavigationViewState.Row rowOf(List<NavigationViewState.Row> rows,
            String branchId) {
        for (NavigationViewState.Row row : rows) {
            if (row.branchId.equals(branchId)) {
                return row;
            }
        }
        throw new AssertionError("no row for " + branchId);
    }

    private static boolean shows(List<NavigationViewState.Row> rows, String branchId) {
        for (NavigationViewState.Row row : rows) {
            if (row.branchId.equals(branchId)) {
                return true;
            }
        }
        return false;
    }

    private static final class RecordingSink implements NavigationSink {

        final List<NavigationViewState> states = new ArrayList<NavigationViewState>();
        final List<String> unreadable = new ArrayList<String>();
        int left;

        @Override
        public void reportUnreadable(String scope, String cause) {
            unreadable.add(scope + " <- " + cause);
        }

        @Override
        public void render(NavigationViewState state) {
            states.add(state);
        }

        @Override
        public void leaveTree() {
            left++;
        }

        NavigationViewState last() {
            return states.get(states.size() - 1);
        }
    }

    private static final class InlineUi implements UiThread {

        @Override
        public void post(Runnable action) {
            action.run();
        }
    }

    /** Queues every dispatch, so a test chooses the order the answers arrive in. */
    private static final class ManualExecutor implements Executor {

        private final List<Runnable> tasks = new ArrayList<Runnable>();
        private final List<Integer> done = new ArrayList<Integer>();

        @Override
        public void execute(Runnable command) {
            tasks.add(command);
        }

        int size() {
            return tasks.size();
        }

        int last() {
            return tasks.size() - 1;
        }

        void runIndex(int index) {
            if (done.contains(index)) {
                return;
            }
            done.add(index);
            tasks.get(index).run();
        }

        void drain() {
            while (done.size() < tasks.size()) {
                runIndex(done.size());
            }
        }
    }

    /** An in-memory tree plus the record of every write the presenter asked for. */
    private static final class FakeStore implements NavigationData {

        final List<Node> nodes = new ArrayList<Node>(Arrays.asList(
                new Node(ROOT, null, "根分支", false),
                new Node(BRANCH_A, ROOT, "A", false),
                new Node(BRANCH_A1, BRANCH_A, "A1", false),
                new Node(BRANCH_B, ROOT, "B", false),
                new Node(SIBLING, ROOT, "S", true)));

        final List<String> archiveCalls = new ArrayList<String>();
        final List<String> cancelCalls = new ArrayList<String>();
        final List<String> deleteCalls = new ArrayList<String>();
        final List<String> summaryAsked = new ArrayList<String>();
        final List<String> order = new ArrayList<String>();
        String summaryText = "";
        List<String> summaryPoints = Collections.emptyList();
        boolean summaryThrows;
        boolean loadThrows;
        boolean orphanNode;

        @Override
        public String topicOf(String branchId) {
            for (Node node : nodes) {
                if (node.branchId.equals(branchId)) {
                    return TOPIC;
                }
            }
            return null;
        }

        @Override
        public Snapshot load(String topicId) {
            if (loadThrows) {
                throw new IllegalStateException("the tree could not be read");
            }
            List<Node> copy = new ArrayList<Node>(nodes);
            if (orphanNode) {
                copy.add(new Node(ORPHAN, ABSENT_PARENT, "孤儿", false));
            }
            return new Snapshot(topicId, copy);
        }

        @Override
        public Summary summary(String branchId) {
            summaryAsked.add(branchId);
            if (summaryThrows) {
                throw new IllegalStateException("the artifact could not be read");
            }
            if (summaryText.isEmpty()) {
                return null;
            }
            return new Summary(branchId, summaryText, summaryPoints);
        }

        @Override
        public void setArchived(String branchId, boolean archived, long now) {
            archiveCalls.add(branchId + "=" + archived);
            order.add("archive");
            for (int i = 0; i < nodes.size(); i++) {
                Node node = nodes.get(i);
                if (node.branchId.equals(branchId)) {
                    nodes.set(i, new Node(node.branchId, node.parentBranchId, node.title, archived));
                }
            }
        }

        @Override
        public int cancelInFlight(List<String> branchIds) {
            cancelCalls.add(String.join(",", branchIds));
            order.add("cancel");
            return 0;
        }

        @Override
        public void delete(Plan plan) {
            deleteCalls.add(String.join(",", plan.branchIds));
            order.add("delete");
            nodes.removeIf(node -> plan.branchIds.contains(node.branchId));
        }
    }
}
