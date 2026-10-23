package com.treelearn.core.domain;

/**
 * Aggregate root identity of one learning tree.
 *
 * <p>A topic deliberately carries no name of its own: the tree's label is always derived from the
 * root branch's title, so there is exactly one place a tree name can come from. Deleting a topic
 * cascades to its whole tree.
 *
 * <p>The topic also owns the current answer style (SPEC-2026-09-21-ai-persona-preference). It is
 * read at send/retry/reset time, copied into the command, and from there frozen into the attempt and
 * the revision; changing it never rewrites an answer that already exists.
 */
public final class Topic {

    public final String id;
    /** Last branch the learner opened; null until the tree has been entered. */
    public final String lastVisitedBranchId;
    public final Persona persona;
    public final long createdAt;
    public final long updatedAt;

    /** The pre-persona shape; kept so existing callers keep meaning the default style. */
    public Topic(String id, String lastVisitedBranchId, long createdAt, long updatedAt) {
        this(id, lastVisitedBranchId, Persona.DEFAULT, createdAt, updatedAt);
    }

    public Topic(String id, String lastVisitedBranchId, Persona persona, long createdAt,
            long updatedAt) {
        this.id = Guard.id(id, "id");
        this.lastVisitedBranchId = Guard.idOrNull(lastVisitedBranchId, "lastVisitedBranchId");
        this.persona = Guard.object(persona, "persona");
        this.createdAt = Guard.epochMillis(createdAt, "createdAt");
        this.updatedAt = Guard.epochMillis(updatedAt, "updatedAt");
    }

    public static Topic create(long now) {
        return new Topic(Guard.newId(), null, Persona.DEFAULT, now, now);
    }

    public Topic withLastVisitedBranch(String branchId, long now) {
        return new Topic(id, Guard.id(branchId, "branchId"), persona, createdAt, stamp(now));
    }

    /**
     * Switches the style without touching either timestamp: a style choice is not learning activity,
     * and the home list is ordered by {@code updatedAt}.
     */
    public Topic withPersona(Persona next) {
        return new Topic(id, lastVisitedBranchId, next, createdAt, updatedAt);
    }

    public Topic touched(long now) {
        return new Topic(id, lastVisitedBranchId, persona, createdAt, stamp(now));
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
