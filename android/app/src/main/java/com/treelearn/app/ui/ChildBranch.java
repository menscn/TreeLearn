package com.treelearn.app.ui;

import com.treelearn.core.domain.Guard;

/**
 * One row of an answer's inline child-branch stack: where it goes, what it is called and what it
 * still owes (PRD 6.2.20 point 7).
 *
 * <p>The label is resolved where the branch's own rows are readable - the branch title rule lives in
 * the wiring package - so the stack draws a name it was handed instead of deriving a second one from a
 * stored title that may still be empty.
 */
public final class ChildBranch {

    public final String branchId;
    public final String title;
    public final long unreadCount;

    public ChildBranch(String branchId, String title, long unreadCount) {
        this.branchId = Guard.id(branchId, "branchId");
        this.title = title == null ? "" : title.trim();
        this.unreadCount = Math.max(unreadCount, 0L);
    }
}
