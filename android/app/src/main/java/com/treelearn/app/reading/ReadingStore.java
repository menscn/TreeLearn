package com.treelearn.app.reading;

import com.treelearn.core.domain.Branch;
import com.treelearn.core.domain.ReadingAnchor;

/**
 * What the reading session reads and writes, behind one port.
 *
 * <p>Three operations, because a session does exactly three things with the store: it asks where the
 * learner was, it records where they are when they leave, and it records that they have seen the
 * newest answer. Anything wider — "save the branch", "read the branch" — would let this layer start
 * deciding what else a branch is, which belongs to the repository that owns the entity.
 */
public interface ReadingStore {

    /** The stored position, or null when the branch has none. */
    ReadingAnchor anchorOf(String branchId);

    /** Replaces the stored position. */
    void saveAnchor(String branchId, ReadingAnchor anchor);

    /**
     * Persists the branch's unread sequences after {@link Branch#markSeenThrough} moved them.
     *
     * <p>The sequence pair is the only unread authority (§11), so this is the whole of "mark as read":
     * there is no second counter to keep in step.
     */
    void saveSeen(Branch branch);
}