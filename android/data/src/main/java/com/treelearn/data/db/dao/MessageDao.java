package com.treelearn.data.db.dao;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.treelearn.core.domain.Message;
import com.treelearn.core.domain.MessageRole;
import com.treelearn.data.db.TreeLearnSchema;

import java.util.ArrayList;
import java.util.List;

/**
 * Row access for {@code messages}.
 *
 * <p>A message is a <em>position</em>: a USER row carries immutable learner text, an AI row carries
 * only the revision and attempt it currently points at. The DAO writes whichever of those the row's
 * role allows and never "normalises" a role/content mismatch, because a silent fix would hide a
 * caller bug that the store's CHECK is there to catch.
 *
 * <p>The learner's text is never updated: {@code user_content} is immutable by trigger (T-004), and
 * this class offers no method that would try.
 *
 * <p>§6.2.19: a USER row may additionally carry the passage of the answer it asks about. The
 * domain has one spelling for "no quote" — the empty string — and the store has another —
 * {@code NULL} — so this class owns <em>both</em> directions of that one translation. Keeping them
 * together is the point: a second copy elsewhere would write an empty string where this class
 * writes {@code NULL}, and every reader would have to know which row came from where. Whether a
 * quote is allowed at all is the store's rule, not a validation here (see the trigger in
 * {@code TreeLearnSchema}).
 */
public final class MessageDao {

    private MessageDao() {
    }

    public static void insert(SQLiteDatabase database, Message message) {
        if (message == null) {
            throw new IllegalArgumentException("message must not be null");
        }
        database.insertOrThrow(TreeLearnSchema.Messages.TABLE, null, values(message));
    }

    /**
     * Writes only the two references an AI position may move: the revision currently displayed and
     * the attempt currently running. Resetting an answer repoints this row; it never rewrites one.
     */
    public static void updateActiveReferences(SQLiteDatabase database, Message message) {
        if (message == null) {
            throw new IllegalArgumentException("message must not be null");
        }
        ContentValues values = new ContentValues();
        values.put(TreeLearnSchema.Messages.ACTIVE_REVISION_ID, message.activeRevisionId());
        values.put(TreeLearnSchema.Messages.ACTIVE_OPERATION_ID, message.activeOperationId());
        int updated = database.update(TreeLearnSchema.Messages.TABLE, values,
                TreeLearnSchema.Messages.ID + "=?", new String[]{message.id});
        if (updated != 1) {
            throw new IllegalStateException("updating message " + message.id + " affected "
                    + updated + " row(s); a message update must touch exactly its own row");
        }
    }

    /** @return the message, or null when no row carries this id */
    public static Message findById(SQLiteDatabase database, String messageId) {
        if (messageId == null || messageId.isEmpty()) {
            throw new IllegalArgumentException("messageId must not be blank");
        }
        try (Cursor cursor = database.query(TreeLearnSchema.Messages.TABLE, null,
                TreeLearnSchema.Messages.ID + "=?", new String[]{messageId}, null, null, null)) {
            return cursor.moveToFirst() ? read(cursor) : null;
        }
    }

    /** @return every position of a branch in conversation order */
    public static List<Message> findByBranch(SQLiteDatabase database, String branchId) {
        if (branchId == null || branchId.isEmpty()) {
            throw new IllegalArgumentException("branchId must not be blank");
        }
        List<Message> messages = new ArrayList<>();
        try (Cursor cursor = database.query(TreeLearnSchema.Messages.TABLE, null,
                TreeLearnSchema.Messages.BRANCH_ID + "=?", new String[]{branchId}, null, null,
                TreeLearnSchema.Messages.ORDINAL + " ASC")) {
            while (cursor.moveToNext()) {
                messages.add(read(cursor));
            }
        }
        return messages;
    }

    private static Message read(Cursor cursor) {
        String role = Rows.string(cursor, TreeLearnSchema.Messages.ROLE);
        return Message.restore(
                Rows.string(cursor, TreeLearnSchema.Messages.ID),
                Rows.string(cursor, TreeLearnSchema.Messages.BRANCH_ID),
                MessageRole.valueOf(role),
                Rows.intOf(cursor, TreeLearnSchema.Messages.ORDINAL),
                Rows.string(cursor, TreeLearnSchema.Messages.USER_CONTENT),
                quoteFromColumn(Rows.string(cursor, TreeLearnSchema.Messages.QUOTED_TEXT)),
                Rows.longOf(cursor, TreeLearnSchema.Messages.CREATED_AT),
                Rows.string(cursor, TreeLearnSchema.Messages.ACTIVE_REVISION_ID),
                Rows.string(cursor, TreeLearnSchema.Messages.ACTIVE_OPERATION_ID));
    }

    /**
     * Translates the domain's "no quote" (the empty string) into the column's ({@code NULL}).
     *
     * <p>A null argument is a caller bug rather than "no quote": the domain never hands one out,
     * and quietly storing {@code NULL} for it would hide whichever caller produced it.
     */
    static String quoteColumnValue(String quotedText) {
        if (quotedText == null) {
            throw new IllegalArgumentException("quotedText must not be null; the domain spells"
                    + " \"no quote\" as the empty string");
        }
        return quotedText.isEmpty() ? null : quotedText;
    }

    /** The other direction: a stored {@code NULL} reads back as "no quote", never as null. */
    static String quoteFromColumn(String stored) {
        return stored == null ? "" : stored;
    }

    private static ContentValues values(Message message) {
        ContentValues values = new ContentValues();
        values.put(TreeLearnSchema.Messages.ID, message.id);
        values.put(TreeLearnSchema.Messages.BRANCH_ID, message.branchId);
        values.put(TreeLearnSchema.Messages.ROLE, message.role.name());
        values.put(TreeLearnSchema.Messages.ORDINAL, message.ordinal);
        // Only a USER row stores content; an AI row is a logical position and the store rejects it
        // otherwise. Writing null here is not a coercion — it is the only legal value for AI.
        values.put(TreeLearnSchema.Messages.USER_CONTENT, message.isUser() ? message.userContent
                : null);
        // "No quote" is one value in the domain and one value in the column; the translation is
        // this method and its sibling below, never a second copy at a call site.
        values.put(TreeLearnSchema.Messages.QUOTED_TEXT, quoteColumnValue(message.quotedText()));
        values.put(TreeLearnSchema.Messages.CREATED_AT, message.createdAt);
        values.put(TreeLearnSchema.Messages.ACTIVE_REVISION_ID, message.activeRevisionId());
        values.put(TreeLearnSchema.Messages.ACTIVE_OPERATION_ID, message.activeOperationId());
        return values;
    }
}
