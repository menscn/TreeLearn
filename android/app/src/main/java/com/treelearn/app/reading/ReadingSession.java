package com.treelearn.app.reading;

import com.treelearn.core.domain.Branch;
import com.treelearn.core.domain.Guard;
import com.treelearn.core.domain.Message;
import com.treelearn.core.domain.ReadingAnchor;
import com.treelearn.core.domain.ReadingPosition;
import com.treelearn.core.domain.ViewportSeen;

import java.util.List;
import java.util.Set;

/**
 * One visit to a branch: open it where the learner left it, and record what they saw (design 11).
 *
 * <p>This is the production consumer of the two rules that would otherwise live only in tests —
 * {@link ReadingAnchor#resolve} and {@link ViewportSeen#onViewport} — and it is deliberately the
 * whole of the session's state: it holds no position of its own, because the stored anchor is the
 * authority and a second copy in memory would be a second answer to "where was I".
 *
 * <p><strong>What the chat screen owes this class.</strong> {@code messages} must be the branch's
 * messages in reading order (what {@code MessageDao.findByBranch} returns), and
 * {@code visibleMessageIds} must be the positions actually on screen. Both are things only a view can
 * know, and neither can be checked here — a shuffled list would resolve to a legal-looking position at
 * the wrong end.
 */
public final class ReadingSession {

    private final ReadingStore store;

    public ReadingSession(ReadingStore store) {
        this.store = Guard.object(store, "store");
    }

    /**
     * Where to scroll when the branch opens.
     *
     * <p>A branch with nothing stored opens at the live end, and so does one whose anchor cannot be
     * used any more; the returned position says which of the two happened, because "we restored your
     * place" and "we could not find it, here is the newest answer" are different things to be told.
     *
     * <p>Rebasing a stale revision is part of resolving, and it is deliberately not written back here:
     * a read must not become a state change. The position is stored again the next time the learner
     * leaves the branch.
     */
    public ReadingPosition open(Branch branch, List<Message> messages, int itemHeightPx) {
        Guard.object(branch, "branch");
        Guard.object(messages, "messages");
        ReadingAnchor stored = store.anchorOf(branch.id);
        if (stored == null) {
            return ReadingPosition.liveEnd(messages);
        }
        return stored.resolve(messages, itemHeightPx);
    }

    /**
     * Reports what the viewport is showing now.
     *
     * @return true when the seen sequence moved and was written, so the caller can re-read the badge
     */
    public boolean onViewport(Branch branch, List<Message> messages, Set<String> visibleMessageIds) {
        Guard.object(branch, "branch");
        if (!ViewportSeen.onViewport(branch, messages, visibleMessageIds)) {
            return false;
        }
        store.saveSeen(branch);
        return true;
    }

    /** Records where the learner is now, in the semantic form the store keeps. */
    public void remember(Branch branch, ReadingAnchor anchor) {
        Guard.object(branch, "branch");
        store.saveAnchor(branch.id, Guard.object(anchor, "anchor"));
    }
}