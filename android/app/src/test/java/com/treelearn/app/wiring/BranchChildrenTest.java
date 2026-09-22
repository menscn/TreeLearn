package com.treelearn.app.wiring;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.treelearn.core.domain.Branch;
import com.treelearn.core.domain.BranchAnchor;
import com.treelearn.core.domain.BranchSource;
import com.treelearn.core.domain.DomainResult;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.junit.Test;

/**
 * The one rule behind an answer's child-branch list and its child count (PRD 6.2.20 point 7).
 *
 * <p>Two readers ask the same question - the entry that says how many children an answer has, and the
 * popup that lists them - so both are driven here from one fixture. A count that included an archived
 * child while the list left it out would be an entry that opens nothing, which is the defect this
 * suite is written against.
 *
 * <p>PRD 6.2.20 point 7 also fixes the population: the branches an answer forked <em>directly</em>,
 * unarchived, in creation order. A grandchild belongs to the answer inside its own branch, and an
 * archived child belongs to nobody's list.
 */
public final class BranchChildrenTest {

    private static final String TOPIC = WiringDoubles.TOPIC_ID;
    private static final String ROOT = WiringDoubles.BRANCH_ID;
    private static final String ANSWER = WiringDoubles.ANSWER_ID;
    private static final String LATER_ANSWER = WiringDoubles.LATER_ANSWER_ID;
    private static final String ANSWER_IN_CHILD = "19191919-1919-4919-8919-191919191919";
    private static final String ONLY_ARCHIVED = "1a1a1a1a-1a1a-4a1a-8a1a-1a1a1a1a1a1a";
    private static final String CHILD = "15151515-1515-4515-8515-151515151515";
    private static final String ARCHIVED_CHILD = "16161616-1616-4616-8616-161616161616";
    private static final String GRANDCHILD = "18181818-1818-4818-8818-181818181818";

    @Test
    public void onlyTheUnarchivedChildrenOfThatAnswerAreListed() {
        assertEquals(Arrays.asList(CHILD), ids(BranchChildren.of(topic(), ANSWER)));
    }

    @Test
    public void aGrandchildBelongsToItsOwnAnswerAndNotTheOneAboveIt() {
        List<Branch> grandchildren = BranchChildren.of(topic(), ANSWER_IN_CHILD);

        assertEquals(Arrays.asList(GRANDCHILD), ids(grandchildren));
        assertTrue("a grandchild belongs under the answer inside its own branch",
                !ids(BranchChildren.of(topic(), ANSWER)).contains(GRANDCHILD));
        assertNull("a branch id is not a message id, so it names nobody's answer",
                BranchChildren.countsOf(topic()).get(CHILD));
    }

    @Test
    public void theCountAndTheListLeaveOutExactlyTheSameChildren() {
        Map<String, Integer> counts = BranchChildren.countsOf(topic());

        for (String messageId : Arrays.asList(ANSWER, ANSWER_IN_CHILD, LATER_ANSWER, ONLY_ARCHIVED)) {
            int listed = BranchChildren.of(topic(), messageId).size();
            Integer counted = counts.get(messageId);
            assertEquals("count and list must agree for " + messageId, listed,
                    counted == null ? 0 : counted.intValue());
        }
        assertEquals(1, BranchChildren.of(topic(), ANSWER).size());
        assertEquals(Integer.valueOf(1), counts.get(ANSWER));
        assertEquals(Integer.valueOf(1), counts.get(ANSWER_IN_CHILD));
        assertNull("an answer nobody forked from is not a key at all", counts.get(LATER_ANSWER));
    }

    @Test
    public void anAnswerWhoseOnlyChildrenAreArchivedHasNoListAtAll() {
        assertTrue(BranchChildren.of(topic(), ONLY_ARCHIVED).isEmpty());
        assertNull("an entry that would open nothing must not be counted in",
                BranchChildren.countsOf(topic()).get(ONLY_ARCHIVED));
    }

    @Test
    public void theRootBranchIsNeverAnybodysChild() {
        assertTrue(BranchChildren.of(topic(), ROOT).isEmpty());
        assertNull(BranchChildren.countsOf(topic()).get(ROOT));
        assertEquals("only the two answers that were forked from are keys", 2,
                BranchChildren.countsOf(topic()).size());
    }

    private static List<String> ids(List<Branch> branches) {
        List<String> ids = new ArrayList<String>();
        for (Branch branch : branches) {
            ids.add(branch.id);
        }
        return ids;
    }

    /**
     * One topic: a root, an answer with one unarchived and one archived child, a child with a
     * grandchild of its own, an answer nobody forked from, and an answer whose only child is archived.
     */
    private static List<Branch> topic() {
        return Arrays.asList(Branch.root(TOPIC, WiringDoubles.NOW),
                child(CHILD, ROOT, ANSWER, false),
                child(ARCHIVED_CHILD, ROOT, ANSWER, true),
                child(GRANDCHILD, CHILD, ANSWER_IN_CHILD, false),
                child("17171717-1717-4717-8717-171717171717", ROOT, ONLY_ARCHIVED, true));
    }

    private static Branch child(String id, String parentBranchId, String sourceMessageId,
            boolean archived) {
        DomainResult<Branch> created = Branch.child(TOPIC, BranchSource.restore(parentBranchId,
                sourceMessageId, WiringDoubles.REVISION_ID, BranchAnchor.wholeAnswer(),
                WiringDoubles.OPERATION_ID), WiringDoubles.NOW);
        assertTrue("the fixture must be a valid child branch: " + created, created.isOk());
        Branch branch = Branch.restore(id, TOPIC, parentBranchId, created.value().source,
                "a child of " + sourceMessageId, WiringDoubles.NOW, WiringDoubles.NOW, 0L, 0L, false,
                null);
        if (archived) {
            branch.archive(WiringDoubles.NOW);
        }
        return branch;
    }
}
