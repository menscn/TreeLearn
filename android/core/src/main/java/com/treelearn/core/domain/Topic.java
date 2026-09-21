package com.treelearn.core.domain;

/**
 * Aggregate root identity of one learning tree.
 *
 * <p>A topic deliberately carries no name of its own: the tree's label is always derived from the
 * root branch's title, so there is exactly one place a tree name can come from. Deleting a topic
 * cascades to its whole tree.
 */
public final class Topic {

    public final String id;
    /** Last branch the learner opened; null until the tree has been entered. */
    public final String lastVisitedBranchId;
    public final long createdAt;
    public final long updatedAt;

    public Topic(String id, String lastVisitedBranchId, long createdAt, long updatedAt) {
        this.id = Guard.id(id, "id");
        this.lastVisitedBranchId = Guard.idOrNull(lastVisitedBranchId, "lastVisitedBranchId");
        this.createdAt = Guard.epochMillis(createdAt, "createdAt");
        this.updatedAt = Guard.epochMillis(updatedAt, "updatedAt");
    }

    public static Topic create(long now) {
        return new Topic(Guard.newId(), null, now, now);
    }

    public Topic withLastVisitedBranch(String branchId, long now) {
        return new Topic(id, Guard.id(branchId, "branchId"), createdAt, stamp(now));
    }

    public Topic touched(long now) {
        return new Topic(id, lastVisitedBranchId, createdAt, stamp(now));
    }

    /** A timestamp older than creation is a caller bug, not a valid update. */
    private long stamp(long now) {
        long value = Guard.epochMillis(now, "now");
        if (value < createdAt) {
            throw new IllegalArgumentException(
                    "updatedAt must not predate createdAt: " + value + " < " + createdAt);
        }
        return value;
    }
}
