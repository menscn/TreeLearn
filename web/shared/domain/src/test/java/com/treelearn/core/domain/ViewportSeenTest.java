package com.treelearn.core.domain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.Test;

/**
 * T-015 acceptance: the unread badge clears only when the newest unread answer really entered the
 * viewport (technical design 11).
 *
 * <p>The rule exists because the obvious alternative is wrong in both directions. Advancing on
 * "the chat screen was opened" marks answers read that were never shown; advancing on "any answer is
 * visible" clears a badge while the newest answer is still below the fold. A title refresh, a
 * summary or a recommendation can never clear it at all: only a message that carries a stable
 * revision is an answer, so nothing else is even a candidate.
 */
public class ViewportSeenTest {

    private static final long NOW = 1_700_000_000_000L;
    private static final String TOPIC = "11111111-1111-4111-8111-111111111111";
    private static final String BRANCH = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa";

    @Test
    public void theNewestUnreadAnswerEnteringTheViewportClearsTheUnread() {
        Branch branch = Branch.root(TOPIC, NOW);
        List<Message> messages = new ArrayList<Message>(Arrays.asList(
                user(0), answer(1), user(2), answer(3)));
        stableAnswers(branch, 4);
        assertTrue(branch.markSeenThrough(1).isOk());

        boolean advanced = ViewportSeen.onViewport(branch, messages,
                visible(messages.get(3).id));

        assertTrue(advanced);
        assertEquals(4, branch.lastSeenStableSequence());
        assertEquals(0, branch.unreadCount());
    }

    @Test
    public void anOlderAnswerEnteringTheViewportChangesNothing() {
        Branch branch = Branch.root(TOPIC, NOW);
        List<Message> messages = new ArrayList<Message>(Arrays.asList(
                user(0), answer(1), user(2), answer(3)));
        stableAnswers(branch, 4);
        assertTrue(branch.markSeenThrough(1).isOk());

        boolean advanced = ViewportSeen.onViewport(branch, messages,
                visible(messages.get(0).id, messages.get(1).id));

        assertFalse(advanced);
        assertEquals(1, branch.lastSeenStableSequence());
        assertEquals(3, branch.unreadCount());
    }

    @Test
    public void aUserQuestionScrollingPastClearsNothing() {
        // The text the learner typed is not an answer. Only a position carrying a stable revision is.
        Branch branch = Branch.root(TOPIC, NOW);
        List<Message> messages = new ArrayList<Message>(Arrays.asList(user(0), answer(1)));
        stableAnswers(branch, 1);

        assertFalse(ViewportSeen.onViewport(branch, messages, visible(messages.get(0).id)));
        assertEquals(0, branch.lastSeenStableSequence());
    }

    @Test
    public void nothingIsClearedWhileNoAnswerHasAStableRevision() {
        Branch branch = Branch.root(TOPIC, NOW);
        List<Message> messages = new ArrayList<Message>(Arrays.asList(
                user(0), Message.ai(BRANCH, 1, NOW)));
        stableAnswers(branch, 2);

        assertFalse(ViewportSeen.onViewport(branch, messages, visible()));
        assertEquals(0, branch.lastSeenStableSequence());
    }

    @Test
    public void anAlreadyReadBranchIsUntouched() {
        Branch branch = Branch.root(TOPIC, NOW);
        List<Message> messages = new ArrayList<Message>(Arrays.asList(user(0), answer(1)));
        stableAnswers(branch, 1);
        assertTrue(branch.markSeenThrough(1).isOk());

        assertFalse(ViewportSeen.onViewport(branch, messages, visible(messages.get(1).id)));
        assertEquals(1, branch.lastSeenStableSequence());
    }

    @Test
    public void anEmptyViewportNeverAdvancesAnything() {
        Branch branch = Branch.root(TOPIC, NOW);
        List<Message> messages = new ArrayList<Message>(Arrays.asList(user(0), answer(1)));
        stableAnswers(branch, 1);

        assertFalse(ViewportSeen.onViewport(branch, messages, visible()));
        assertFalse(ViewportSeen.onViewport(branch, Collections.<Message>emptyList(), visible()));
        assertEquals(0, branch.lastSeenStableSequence());
    }

    private static void stableAnswers(Branch branch, int count) {
        for (int i = 0; i < count; i++) {
            branch.onStableAnswer(NOW);
        }
    }

    private static Message user(int ordinal) {
        return Message.user(BRANCH, ordinal, "synthetic question " + ordinal, NOW);
    }

    private static Message answer(int ordinal) {
        return Message.restore(Guard.newId(), BRANCH, MessageRole.AI, ordinal, null, NOW,
                Guard.newId(), null);
    }

    private static Set<String> visible(String... messageIds) {
        return new HashSet<String>(Arrays.asList(messageIds));
    }
}