package com.treelearn.treelayout;

/**
 * One node of the immutable layout input snapshot.
 *
 * <p>This is the layout engine's own small pure-Java record rather than a reference to
 * {@code :core}: the module dependency contract forbids {@code :tree-layout} from depending on
 * anything, so the caller (the canvas) projects a domain branch onto this type. Nothing here is a
 * domain decision — no branch may be created through it, and no invariant of {@code Branch} is
 * re-decided. What it carries is exactly what the geometry needs:
 *
 * <ul>
 *   <li>{@code rounds} — the committed user messages of this branch, which is what makes a segment
 *       longer (retries and resets add no user message, so they add no length);
 *   <li>{@code ownUnread} — this branch's own unseen stable answers;
 *   <li>{@code collapsed} — whether this branch shows as a bud;
 *   <li>{@code archived} — whether this branch and its whole subtree leave the default layout.
 * </ul>
 */
public final class LayoutNode {

    /** Stable branch identity; the only thing callers may key on. */
    public final String id;
    /** Null exactly for the root of a snapshot. */
    public final String parentId;
    /** Branch title; a null input becomes the empty string, never a placeholder. */
    public final String title;
    /** Summary first line shown only in the near tier; empty when the branch has none yet. */
    public final String summary;
    /** Committed user messages of this branch. */
    public final int rounds;
    /** This branch's own unseen stable answers. */
    public final int ownUnread;
    public final boolean collapsed;
    public final boolean archived;

    public LayoutNode(String id, String parentId, String title, String summary, int rounds,
            int ownUnread, boolean collapsed, boolean archived) {
        this.id = requireText(id, "id");
        this.parentId = parentId == null ? null : requireText(parentId, "parentId");
        if (this.parentId != null && this.parentId.equals(this.id)) {
            throw new IllegalArgumentException("a node must not be its own parent: " + this.id);
        }
        this.title = title == null ? "" : title;
        this.summary = summary == null ? "" : summary;
        this.rounds = requireNonNegative(rounds, "rounds");
        this.ownUnread = requireNonNegative(ownUnread, "ownUnread");
        this.collapsed = collapsed;
        this.archived = archived;
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }

    private static int requireNonNegative(int value, String name) {
        if (value < 0) {
            throw new IllegalArgumentException(name + " must not be negative: " + value);
        }
        return value;
    }

    @Override
    public String toString() {
        return "LayoutNode[" + id + " parent=" + parentId + " rounds=" + rounds
                + " unread=" + ownUnread + (collapsed ? " collapsed" : "")
                + (archived ? " archived" : "") + "]";
    }
}
