package com.treelearn.core.domain;

/**
 * One node of the learning tree.
 *
 * <p>A root branch has no parent and no provenance; every non-root branch must carry a
 * {@link BranchSource}, which is why {@link #child} refuses to build one without it. The unread
 * count is <em>derived</em> from two sequences rather than stored as an independent flag: a new
 * stable answer bumps {@code latestStableSequence}, and the position only clears once that answer
 * really entered the viewport. This is what stops a title or summary refresh from producing a
 * phantom unread badge.
 */
public final class Branch {

    public final String id;
    public final String topicId;
    /** Null exactly for the single root branch of a topic. */
    public final String parentBranchId;
    /** Null exactly for the root branch. */
    public final BranchSource source;
    public final long createdAt;

    private String title;
    private long updatedAt;
    private long latestStableSequence;
    private long lastSeenStableSequence;
    private boolean collapsed;
    private Long archivedAt;

    private Branch(
            String id,
            String topicId,
            String parentBranchId,
            BranchSource source,
            String title,
            long createdAt,
            long updatedAt,
            long latestStableSequence,
            long lastSeenStableSequence,
            boolean collapsed,
            Long archivedAt) {
        this.id = Guard.id(id, "id");
        this.topicId = Guard.id(topicId, "topicId");
        this.parentBranchId = Guard.idOrNull(parentBranchId, "parentBranchId");
        this.source = source;
        this.title = title;
        this.createdAt = Guard.epochMillis(createdAt, "createdAt");
        this.updatedAt = Guard.epochMillis(updatedAt, "updatedAt");
        if (this.updatedAt < this.createdAt) {
            // Previously the constructor pinned updatedAt to createdAt, so this invariant was not
            // expressible inside the type at all. Now that restore carries a persisted updatedAt,
            // the type can — and must — refuse a row that claims to have been modified before it
            // existed, instead of silently clamping it.
            throw new IllegalArgumentException(
                    "updatedAt must not predate createdAt: " + this.updatedAt + " < "
                            + this.createdAt);
        }
        this.latestStableSequence = Guard.nonNegative(latestStableSequence, "latestStableSequence");
        this.lastSeenStableSequence = Guard.nonNegative(lastSeenStableSequence, "lastSeenStableSequence");
        this.collapsed = collapsed;
        this.archivedAt = archivedAt;
        if (latestStableSequence < lastSeenStableSequence) {
            throw new IllegalArgumentException(
                    "lastSeenStableSequence must not exceed latestStableSequence");
        }
        if (parentBranchId == null) {
            if (source != null) {
                throw new IllegalArgumentException("a root branch must not carry provenance");
            }
        } else if (source == null) {
            throw new IllegalArgumentException("a non-root branch must carry its provenance");
        }
    }

    /** Creates the single root branch of a topic. */
    public static Branch root(String topicId, long now) {
        return new Branch(Guard.newId(), topicId, null, null, null, now, now, 0L, 0L, false, null);
    }

    /**
     * Creates a child branch from an already-validated {@link BranchSource}.
     *
     * <p>Provenance is mandatory, and it is a mandatory <em>argument</em>: the type system states the
     * rule, so an omitted source is a caller bug rather than a business outcome. Use {@link #root}
     * for a topic's single root branch. Per the error-channel rule in {@link Guard} this throws rather
     * than returning a failure — a round-4-independent audit found it returned the (now removed)
     * {@code BRANCH_SOURCE_INVALID} code for null while
     * {@link BranchSource#fromOperation} threw for the same class of mistake, and that constant had no
     * other producer, so it was removed instead of being kept as a second way to say the same thing.
     * The name is written as {@code BRANCH_SOURCE_INVALID} rather than as a javadoc link on purpose: a
     * link would assert the constant still exists, and a round-5-independent audit found exactly that
     * mistake here — the dangling link made {@code :core:javadoc} fail while the unit tests stayed
     * green, because javadoc is not on the test task's dependency chain.
     *
     * @throws IllegalArgumentException if {@code topicId} is not a canonical UUID string, or if
     *         {@code source} is null
     */
    public static DomainResult<Branch> child(String topicId, BranchSource source, long now) {
        if (source == null) {
            throw new IllegalArgumentException(
                    "a non-root branch requires the provenance of the answer it was forked from; "
                            + "use Branch.root(topicId, now) for a topic's root branch");
        }
        return DomainResult.ok(new Branch(
                Guard.newId(), topicId, source.parentBranchId, source, null, now, now, 0L, 0L,
                false, null));
    }

    /**
     * Rehydrates a branch that was already persisted, including the state a session may have
     * changed after creation.
     *
     * <p>This is not a creation path: on reload the row is the authority, so no business rule is
     * re-decided. Identities, timestamps and the provenance/root pairing are still validated, so a
     * corrupt row fails loudly instead of producing a half-built branch.
     *
     * <p>It exists because the mutable fields — title, collapse, archive, the unread sequence pair
     * and {@code updatedAt} — cannot otherwise be carried back out of the store: every other entry
     * point starts a branch from scratch. Without it a DAO could only rehydrate a branch by
     * replaying mutations, which would silently rewrite {@code updatedAt} to "now" on every read.
     */
    public static Branch restore(
            String id,
            String topicId,
            String parentBranchId,
            BranchSource source,
            String title,
            long createdAt,
            long updatedAt,
            long latestStableSequence,
            long lastSeenStableSequence,
            boolean collapsed,
            Long archivedAt) {
        return new Branch(id, topicId, parentBranchId, source, title, createdAt, updatedAt,
                latestStableSequence, lastSeenStableSequence, collapsed, archivedAt);
    }

    public boolean isRoot() {
        return parentBranchId == null;
    }

    public String title() {
        return title;
    }

    public long updatedAt() {
        return updatedAt;
    }

    /**
     * Validates a mutation timestamp <em>without</em> applying it.
     *
     * <p>Callers must validate before they mutate anything. A round-3-independent audit found that
     * validating last left a rejected call half-applied — {@code setTitle("X", staleNow)} changed the
     * title and then threw, so the object kept a change the caller was told had failed.
     */
    private long validateStamp(long now) {
        long stamp = Guard.epochMillis(now, "now");
        if (stamp < createdAt) {
            throw new IllegalArgumentException(
                    "updatedAt must not predate createdAt: " + stamp + " < " + createdAt);
        }
        return stamp;
    }

    private void apply(long stamp) {
        this.updatedAt = stamp;
    }

    /** Title is a derived AI product, so it may be refined later without touching conversation facts. */
    public void setTitle(String title, long now) {
        String validated = title == null ? null : Guard.nonBlank(title, "title");
        long stamp = validateStamp(now);
        this.title = validated;
        apply(stamp);
    }

    public boolean isCollapsed() {
        return collapsed;
    }

    /** Collapse state is a view concern and never changes the unread sequences. */
    public void setCollapsed(boolean collapsed, long now) {
        long stamp = validateStamp(now);
        this.collapsed = collapsed;
        apply(stamp);
    }

    public boolean isArchived() {
        return archivedAt != null;
    }

    public Long archivedAt() {
        return archivedAt;
    }

    /** Archiving never cancels a running attempt and never hides the branch's own history. */
    public void archive(long now) {
        long stamp = validateStamp(now);
        this.archivedAt = Long.valueOf(stamp);
        apply(stamp);
    }

    public void unarchive(long now) {
        long stamp = validateStamp(now);
        this.archivedAt = null;
        apply(stamp);
    }

    public long latestStableSequence() {
        return latestStableSequence;
    }

    public long lastSeenStableSequence() {
        return lastSeenStableSequence;
    }

    /** Unread derives from the sequence gap; there is no independent unread boolean anywhere. */
    public long unreadCount() {
        long gap = latestStableSequence - lastSeenStableSequence;
        return gap > 0L ? gap : 0L;
    }

    /** Called when this branch gains a new stable answer. */
    public void onStableAnswer(long now) {
        long stamp = validateStamp(now);
        this.latestStableSequence++;
        apply(stamp);
    }

    /**
     * Marks answers as read. Only ever moves forward, and never past what actually exists, so a
     * stale UI callback cannot mark a not-yet-arrived answer as read.
     */
    public DomainResult<Void> markSeenThrough(long sequence) {
        if (sequence < lastSeenStableSequence) {
            return DomainResult.fail(DomainErrorCode.SEQUENCE_OUT_OF_ORDER,
                    "reading position must not move backwards: " + sequence + " < " + lastSeenStableSequence);
        }
        if (sequence > latestStableSequence) {
            return DomainResult.fail(DomainErrorCode.SEQUENCE_OUT_OF_ORDER,
                    "cannot mark sequence " + sequence + " as read; only " + latestStableSequence + " exists");
        }
        this.lastSeenStableSequence = sequence;
        return DomainResult.ok();
    }
}
