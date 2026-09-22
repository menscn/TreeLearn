package com.treelearn.app.navigation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The two drawing facts this slice adds to the navigation view state.
 *
 * <p>The screen draws a row's archive and delete controls from the state and never from a decision of
 * its own (T-039 implementation note). Two facts therefore have to travel with each row: whether the
 * branch may be archived at all - BASE-14 keeps the root out of archiving, because archiving the root
 * takes the whole tree off the canvas and out of the lists with nothing left to restore it from - and
 * how many live branches hang directly off it, which is what a row's own line reports.
 *
 * <p>The presenter's own rules (which rows are visible, what a fold hides, that an archive survives as
 * a restore, that a deletion is confirmed before anything is written and then falls back to a visible
 * ancestor) are asserted next door in {@link NavigationPresenterTest}, which runs in the same command
 * as this class.
 */
public final class NavigationRootGateTest {

    private static final String TOPIC = "11111111-1111-4111-8111-111111111111";
    private static final String ROOT = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa";
    private static final String CHILD = "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb";
    private static final String GRANDCHILD = "cccccccc-cccc-4ccc-8ccc-cccccccccccc";
    private static final String LEAF = "dddddddd-dddd-4ddd-8ddd-dddddddddddd";
    private static final String GONE = "eeeeeeee-eeee-4eee-8eee-eeeeeeeeeeee";

    @Test
    public void onlyTheRootRowRefusesArchiving() {
        NavigationViewState state = state();

        assertFalse("BASE-14: the root cannot be archived, so its row offers no archive control",
                row(state.visible, ROOT).archivable);
        assertTrue("every other live branch is archivable", row(state.visible, CHILD).archivable);
        assertTrue(row(state.visible, GRANDCHILD).archivable);
        assertTrue(row(state.visible, LEAF).archivable);
    }

    @Test
    public void anArchivedRowCarriesTheSameGateSoRestoringItOffersArchivingAgain() {
        NavigationViewState state = state();

        assertEquals("an archived branch keeps its own list", 1, state.archived.size());
        assertTrue(row(state.archived, GONE).archivable);
    }

    @Test
    public void aRowCountsOnlyTheLiveBranchesHangingDirectlyOffIt() {
        NavigationViewState state = state();

        assertEquals("the root's own children line", 2, row(state.visible, ROOT).childCount);
        assertEquals(1, row(state.visible, CHILD).childCount);
        assertEquals(0, row(state.visible, LEAF).childCount);
        assertEquals("an archived child is off the live tree, so it is not counted",
                0, row(state.archived, GONE).childCount);
    }

    @Test
    public void aFoldHidesRowsWithoutChangingWhatARowCounts() {
        NavigationViewState folded = state().withCollapsed(Arrays.asList(CHILD));

        assertFalse("folding a group hides its descendants", shows(folded.visible, GRANDCHILD));
        assertTrue("the folded group itself stays", shows(folded.visible, CHILD));
        assertEquals("folding is a view fact, not a change to the tree", 2,
                row(folded.visible, ROOT).childCount);
    }

    @Test
    public void aRowWhoseParentWasNotReadIsNotTreatedAsARoot() {
        NavigationData.Snapshot partial = new NavigationData.Snapshot(TOPIC, Arrays.<NavigationData.Node>asList(
                new NavigationData.Node(CHILD, ROOT, "orphan", false)));
        NavigationViewState state = NavigationViewState.initial(TOPIC, CHILD)
                .withSnapshot(partial, CHILD);

        assertTrue("only a branch that names no parent is the root, so a partially read tree fails open",
                row(state.visible, CHILD).archivable);
        assertEquals(0, row(state.visible, CHILD).childCount);
    }

    private static NavigationViewState state() {
        return NavigationViewState.initial(TOPIC, ROOT).withSnapshot(tree(), ROOT);
    }

    private static NavigationData.Snapshot tree() {
        List<NavigationData.Node> nodes = new ArrayList<NavigationData.Node>();
        nodes.add(new NavigationData.Node(ROOT, null, "root", false));
        nodes.add(new NavigationData.Node(CHILD, ROOT, "child", false));
        nodes.add(new NavigationData.Node(GRANDCHILD, CHILD, "grandchild", false));
        nodes.add(new NavigationData.Node(LEAF, ROOT, "leaf", false));
        nodes.add(new NavigationData.Node(GONE, ROOT, "archived child", true));
        return new NavigationData.Snapshot(TOPIC, nodes);
    }

    private static NavigationViewState.Row row(List<NavigationViewState.Row> rows, String branchId) {
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
}
