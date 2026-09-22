package com.treelearn.core.domain;

import java.util.List;
import java.util.Set;

/**
 * When the unread badge is allowed to clear (technical design 11).
 *
 * <p>The rule is one sentence in the design — {@code lastSeenStableSequence} moves only after the
 * latest unread answer actually entered the viewport — and it is implemented once, here, because both
 * obvious shortcuts are wrong in a way the learner sees. "The chat screen opened" marks answers read
 * that were never on screen. "Some answer is visible" clears the badge while the newest one is still
 * below the fold, which is exactly the case the badge exists for.
 *
 * <p>A sequence is cleared through the branch's own authority ({@link Branch#markSeenThrough}), never
 * by writing a second counter: §11 forbids an independent unread value, so this class answers
 * <em>whether</em> to advance and the branch decides <em>how far</em>.
 */
public final class ViewportSeen {

    private ViewportSeen() {
    }

    /**
     * Applies one viewport event.
     *
     * <p><strong>Caller obligation this type cannot observe:</strong> {@code messages} must be the
     * branch's messages in reading order, and {@code visibleMessageIds} must be the messages the
     * screen is actually showing. "Newest answer" is the last AI position carrying a revision, so a
     * shuffled list would pick the wrong one.
     *
     * <p>A branch whose newest committed answer has no resolvable message is left alone rather than
     * treated as visible: unread is a promise to show the learner something, and the fail-safe
     * direction is to keep the badge until the store can name the answer.
     *
     * @return true when the position moved, so the caller knows a write is owed
     */
    public static boolean onViewport(Branch branch, List<Message> messages,
            Set<String> visibleMessageIds) {
        Guard.object(branch, "branch");
        Guard.object(messages, "messages");
        Guard.object(visibleMessageIds, "visibleMessageIds");
        if (branch.unreadCount() == 0L) {
            return false;
        }
        String newestAnswer = newestStableAnswer(messages);
        if (newestAnswer == null || !visibleMessageIds.contains(newestAnswer)) {
            return false;
        }
        return branch.markSeenThrough(branch.latestStableSequence()).isOk();
    }

    private static String newestStableAnswer(List<Message> messages) {
        for (int i = messages.size() - 1; i >= 0; i--) {
            Message message = messages.get(i);
            if (message.isAi() && message.activeRevisionId() != null) {
                return message.id;
            }
        }
        return null;
    }
}
