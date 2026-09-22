package com.treelearn.core.domain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.junit.Test;

/**
 * T-015 acceptance: the badge is live aggregation over the visible tree, not a stored counter
 * (technical design 11).
 *
 * <p>Two rules make the number on screen honest. A collapsed ancestor <em>hides</em> its
 * descendants, so their unread would otherwise be invisible with the badge showing zero — it is
 * rolled up instead. An archived branch is not in the visible tree at all, so its unread must not
 * leak into its parent's badge; it would be a count for something the learner cannot open.
 */
public class UnreadRollupTest {

    private static final long NOW = 1_700_000_000_000L;

    @Test
    public void aCollapsedAncestorCarriesTheUnreadItHides() {
        String topic = Guard.newId();
        Branch root = Branch.root(topic, NOW);
        Branch child = child(topic, root);
        Branch grandchild = child(topic, child);

        unread(child, 2);
        unread(grandchild, 1);
        root.setCollapsed(true, NOW);

        Map<String, Long> visible = UnreadRollup.visibleUnread(Arrays.asList(root, child, grandchild));

        assertEquals(Long.valueOf(3L), visible.get(root.id));
        assertFalse("a collapsed branch's descendants are not in the visible tree",
                visible.containsKey(child.id));
        assertFalse(visible.containsKey(grandchild.id));
    }

    @Test
    public void anExpandedAncestorShowsEachBranchsOwnBadge() {
        String topic = Guard.newId();
        Branch root = Branch.root(topic, NOW);
        Branch child = child(topic, root);
        Branch grandchild = child(topic, child);

        unread(child, 2);
        unread(grandchild, 1);

        Map<String, Long> visible = UnreadRollup.visibleUnread(Arrays.asList(root, child, grandchild));

        assertEquals(Long.valueOf(0L), visible.get(root.id));
        assertEquals(Long.valueOf(2L), visible.get(child.id));
        assertEquals(Long.valueOf(1L), visible.get(grandchild.id));
    }

    @Test
    public void anArchivedBranchAndItsSubtreeLeaveTheVisibleTreeEntirely() {
        String topic = Guard.newId();
        Branch root = Branch.root(topic, NOW);
        Branch archived = child(topic, root);
        Branch hidden = child(topic, archived);

        unread(archived, 4);
        unread(hidden, 5);
        archived.archive(NOW);

        Map<String, Long> visible =
                UnreadRollup.visibleUnread(Arrays.asList(root, archived, hidden));

        assertEquals(Long.valueOf(0L), visible.get(root.id));
        assertFalse(visible.containsKey(archived.id));
        assertFalse(visible.containsKey(hidden.id));
    }

    @Test
    public void aCollapsedBranchThatHidesOnlyArchivedChildrenKeepsItsOwnBadge() {
        String topic = Guard.newId();
        Branch root = Branch.root(topic, NOW);
        Branch archived = child(topic, root);

        unread(root, 1);
        unread(archived, 9);
        archived.archive(NOW);
        root.setCollapsed(true, NOW);

        Map<String, Long> visible = UnreadRollup.visibleUnread(Arrays.asList(root, archived));

        assertEquals(Long.valueOf(1L), visible.get(root.id));
    }

    @Test
    public void aBranchWithNothingUnreadIsStillListed() {
        Branch root = Branch.root(Guard.newId(), NOW);

        Map<String, Long> visible = UnreadRollup.visibleUnread(Collections.singletonList(root));

        assertEquals(1, visible.size());
        assertEquals(Long.valueOf(0L), visible.get(root.id));
    }

    @Test
    public void aBranchWhoseParentIsNotInTheSnapshotIsStillShown() {
        // The canvas may be handed one subtree, or rows may arrive out of order; a missing parent
        // must not make a branch disappear from the badge map.
        String topic = Guard.newId();
        Branch orphan = child(topic, Branch.root(topic, NOW));
        unread(orphan, 2);

        Map<String, Long> visible = UnreadRollup.visibleUnread(Collections.singletonList(orphan));

        assertEquals(Long.valueOf(2L), visible.get(orphan.id));
    }

    private static Branch child(String topicId, Branch parent) {
        BranchSource source = BranchSource.fromOperation(parent.id, Guard.newId(), Guard.newId(),
                GenerationState.STABLE, Guard.newId(), BranchAnchor.wholeAnswer()).value();
        return Branch.child(topicId, source, NOW).value();
    }

    private static void unread(Branch branch, int count) {
        for (int i = 0; i < count; i++) {
            branch.onStableAnswer(NOW);
        }
    }
}