package com.treelearn.data.db.dao;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.treelearn.core.domain.ReadingAnchor;
import com.treelearn.data.db.TreeLearnSchema;

/**
 * The four {@code branches} columns that hold the semantic reading position (technical design 11).
 *
 * <p>It is deliberately not part of {@code BranchDao}: the position is a property of a <em>screen
 * state</em>, written when the learner leaves a branch, while {@code BranchDao} writes the entity the
 * rest of the domain rehydrates. Keeping them apart also means a position update cannot rewrite a
 * branch's identity, provenance or unread sequences by accident.
 *
 * <p><strong>Which column decides whether there is a position.</strong> The message reference does.
 * The store clears it when its message is deleted ({@code ON DELETE SET NULL}), so a row whose message
 * is gone means "no reading position" — even though the two offsets are still there, because the
 * engine clears only the columns with a foreign key. {@link #read} therefore answers null rather than
 * treating those leftovers as a position, which is also why the offsets are written together with the
 * message and never on their own.
 *
 * <p>The opposite case — a message reference with a missing offset — cannot be produced by any
 * supported path (there is no foreign key on the offsets, and {@link #write} always writes all four),
 * so it is a corrupt row and fails loudly instead of being repaired into a plausible position.
 */
public final class ReadingPositionDao {

    private ReadingPositionDao() {
    }

    /**
     * @return the stored position, or null when this branch has none (never stored, or cleared with
     *         its message)
     */
    public static ReadingAnchor read(SQLiteDatabase database, String branchId) {
        requireBranchId(branchId);
        if (database == null) {
            throw new IllegalArgumentException("database must not be null");
        }
        try (Cursor cursor = database.query(TreeLearnSchema.Branches.TABLE, new String[]{
                        TreeLearnSchema.Branches.READING_ANCHOR_MESSAGE_ID,
                        TreeLearnSchema.Branches.READING_ANCHOR_REVISION_ID,
                        TreeLearnSchema.Branches.READING_LIST_ITEM_OFFSET,
                        TreeLearnSchema.Branches.READING_VIEWPORT_OFFSET},
                TreeLearnSchema.Branches.ID + "=?", new String[]{branchId}, null, null, null)) {
            if (!cursor.moveToFirst()) {
                return null;
            }
            String messageId = Rows.string(cursor, TreeLearnSchema.Branches.READING_ANCHOR_MESSAGE_ID);
            if (messageId == null) {
                return null;
            }
            Integer listItemOffset =
                    Rows.nullableInt(cursor, TreeLearnSchema.Branches.READING_LIST_ITEM_OFFSET);
            Integer viewportOffset =
                    Rows.nullableInt(cursor, TreeLearnSchema.Branches.READING_VIEWPORT_OFFSET);
            if (listItemOffset == null || viewportOffset == null) {
                throw new IllegalStateException("this branches row points at reading_anchor_message_id "
                        + messageId + " but has no offset (listItem=" + listItemOffset
                        + ", viewport=" + viewportOffset + "); the two offsets are written with the"
                        + " message and never alone, so the row was written around the DAO and cannot"
                        + " be read back as a position");
            }
            return ReadingAnchor.of(messageId,
                    Rows.string(cursor, TreeLearnSchema.Branches.READING_ANCHOR_REVISION_ID),
                    listItemOffset.intValue(), viewportOffset.intValue());
        }
    }

    /**
     * Stores the position, replacing whatever the branch had.
     *
     * @throws IllegalStateException when no row carries {@code branchId} — a position cannot be
     *         attached to a branch the store does not hold, and silently creating one is not this
     *         method's job
     */
    public static void write(SQLiteDatabase database, String branchId, ReadingAnchor anchor) {
        requireBranchId(branchId);
        if (database == null) {
            throw new IllegalArgumentException("database must not be null");
        }
        if (anchor == null) {
            throw new IllegalArgumentException("anchor must not be null");
        }
        ContentValues values = new ContentValues();
        values.put(TreeLearnSchema.Branches.READING_ANCHOR_MESSAGE_ID, anchor.messageId);
        values.put(TreeLearnSchema.Branches.READING_ANCHOR_REVISION_ID, anchor.revisionId);
        values.put(TreeLearnSchema.Branches.READING_LIST_ITEM_OFFSET, anchor.listItemOffset);
        values.put(TreeLearnSchema.Branches.READING_VIEWPORT_OFFSET, anchor.viewportOffset);
        int updated = database.update(TreeLearnSchema.Branches.TABLE, values,
                TreeLearnSchema.Branches.ID + "=?", new String[]{branchId});
        if (updated != 1) {
            throw new IllegalStateException("writing the reading position of branch " + branchId
                    + " affected " + updated + " row(s); it must touch exactly that branch");
        }
    }

    /**
     * Empties the position.
     *
     * <p>All four columns, not just the message: leaving an offset behind would be a position the next
     * reader has to guess the meaning of, and {@link #read} is written to refuse exactly that shape.
     */
    public static void clear(SQLiteDatabase database, String branchId) {
        requireBranchId(branchId);
        if (database == null) {
            throw new IllegalArgumentException("database must not be null");
        }
        ContentValues values = new ContentValues();
        values.putNull(TreeLearnSchema.Branches.READING_ANCHOR_MESSAGE_ID);
        values.putNull(TreeLearnSchema.Branches.READING_ANCHOR_REVISION_ID);
        values.putNull(TreeLearnSchema.Branches.READING_LIST_ITEM_OFFSET);
        values.putNull(TreeLearnSchema.Branches.READING_VIEWPORT_OFFSET);
        int updated = database.update(TreeLearnSchema.Branches.TABLE, values,
                TreeLearnSchema.Branches.ID + "=?", new String[]{branchId});
        if (updated != 1) {
            throw new IllegalStateException("clearing the reading position of branch " + branchId
                    + " affected " + updated + " row(s); it must touch exactly that branch");
        }
    }

    private static void requireBranchId(String branchId) {
        if (branchId == null || branchId.isEmpty()) {
            throw new IllegalArgumentException("branchId must not be blank");
        }
    }
}