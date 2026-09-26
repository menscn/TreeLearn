package com.treelearn.data.db.dao;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.treelearn.data.db.TreeLearnSchema;

import java.util.ArrayList;
import java.util.List;

/**
 * Row access for {@code attachments} and {@code message_attachments} (T-096).
 *
 * <p>Narrow on purpose: this is the only place that knows the two tables' column names, and it
 * offers no query the product does not need yet. The transaction boundary is <em>not</em> opened
 * here — callers that write more than one row wrap the calls in
 * {@code DatabaseTransactionRunner}.
 *
 * <p>Reads return {@code null} for "no such row" rather than an empty object: absence is a normal
 * answer to a lookup, not a failure. Reference reads are LEFT JOINs by construction, so a
 * reference whose attachment row is gone resolves with {@code null} record fields — that is the
 * missing-annotation answer, never an error.
 */
public final class AttachmentDao {

    private AttachmentDao() {
    }

    /** One stored attachment record. The body lives in the private directory, not in the row. */
    public static final class Record {

        public final String id;
        /** SHA-256 hex of the stored bytes; the idempotent dedup key. */
        public final String contentHash;
        public final String displayName;
        public final String mimeType;
        public final long sizeBytes;
        /** The parsed-text cache (097); null until a parse has succeeded. */
        public final String extractedText;
        /** {@code pending} / {@code parsed} / {@code parse_failed}. */
        public final String parseState;
        public final long createdAt;
        public final long updatedAt;

        public Record(String id, String contentHash, String displayName, String mimeType,
                long sizeBytes, String extractedText, String parseState, long createdAt,
                long updatedAt) {
            this.id = id;
            this.contentHash = contentHash;
            this.displayName = displayName;
            this.mimeType = mimeType;
            this.sizeBytes = sizeBytes;
            this.extractedText = extractedText;
            this.parseState = parseState;
            this.createdAt = createdAt;
            this.updatedAt = updatedAt;
        }
    }

    /** One resolved message reference: the reference row plus whatever its attachment still is. */
    public static final class ResolvedReference {

        public final String messageId;
        public final String attachmentId;
        /** The referenced record, or null once the attachment row has been deleted. */
        public final Record record;

        ResolvedReference(String messageId, String attachmentId, Record record) {
            this.messageId = messageId;
            this.attachmentId = attachmentId;
            this.record = record;
        }
    }

    public static void insert(SQLiteDatabase database, Record record) {
        if (database == null) {
            throw new IllegalArgumentException("database must not be null");
        }
        if (record == null) {
            throw new IllegalArgumentException("record must not be null");
        }
        ContentValues values = new ContentValues();
        values.put(TreeLearnSchema.Attachments.ID, record.id);
        values.put(TreeLearnSchema.Attachments.CONTENT_HASH, record.contentHash);
        values.put(TreeLearnSchema.Attachments.DISPLAY_NAME, record.displayName);
        values.put(TreeLearnSchema.Attachments.MIME_TYPE, record.mimeType);
        values.put(TreeLearnSchema.Attachments.SIZE_BYTES, record.sizeBytes);
        values.put(TreeLearnSchema.Attachments.EXTRACTED_TEXT, record.extractedText);
        values.put(TreeLearnSchema.Attachments.PARSE_STATE, record.parseState);
        values.put(TreeLearnSchema.Attachments.CREATED_AT, record.createdAt);
        values.put(TreeLearnSchema.Attachments.UPDATED_AT, record.updatedAt);
        database.insertOrThrow(TreeLearnSchema.Attachments.TABLE, null, values);
    }

    /** @return the row with this content hash, or null — the dedup lookup. */
    public static Record findByContentHash(SQLiteDatabase database, String contentHash) {
        if (database == null) {
            throw new IllegalArgumentException("database must not be null");
        }
        if (contentHash == null || contentHash.isEmpty()) {
            throw new IllegalArgumentException("contentHash must not be blank");
        }
        try (Cursor cursor = database.rawQuery("SELECT " + ATTACHMENT_COLUMNS + " FROM "
                + TreeLearnSchema.Attachments.TABLE + " WHERE "
                + TreeLearnSchema.Attachments.CONTENT_HASH + "=?", new String[]{contentHash})) {
            return cursor.moveToFirst() ? record(cursor) : null;
        }
    }

    /** @return the row with this id, or null. */
    public static Record findById(SQLiteDatabase database, String id) {
        if (database == null) {
            throw new IllegalArgumentException("database must not be null");
        }
        if (id == null || id.isEmpty()) {
            throw new IllegalArgumentException("id must not be blank");
        }
        try (Cursor cursor = database.rawQuery("SELECT " + ATTACHMENT_COLUMNS + " FROM "
                + TreeLearnSchema.Attachments.TABLE + " WHERE "
                + TreeLearnSchema.Attachments.ID + "=?", new String[]{id})) {
            return cursor.moveToFirst() ? record(cursor) : null;
        }
    }

    /** Every stored record, newest first. */
    public static List<Record> listAll(SQLiteDatabase database) {
        if (database == null) {
            throw new IllegalArgumentException("database must not be null");
        }
        List<Record> records = new ArrayList<>();
        try (Cursor cursor = database.rawQuery("SELECT " + ATTACHMENT_COLUMNS + " FROM "
                + TreeLearnSchema.Attachments.TABLE + " ORDER BY "
                + TreeLearnSchema.Attachments.CREATED_AT + " DESC, "
                + TreeLearnSchema.Attachments.ID + " ASC", null)) {
            while (cursor.moveToNext()) {
                records.add(record(cursor));
            }
        }
        return records;
    }

    /** Deletes the record row. Its reference rows are semantic and deliberately survive it. */
    public static void delete(SQLiteDatabase database, String id) {
        if (database == null) {
            throw new IllegalArgumentException("database must not be null");
        }
        int deleted = deleteIfPresent(database, id);
        if (deleted != 1) {
            throw new IllegalStateException("deleting attachment " + id + " affected " + deleted
                    + " row(s); a record delete must touch exactly its own row");
        }
    }

    /**
     * Deletes the record row only if it is there, returning whether it was (T-098). Cleanup is
     * idempotent by contract: a double tap or a concurrent delete must be a no-op, never a crash.
     */
    public static int deleteIfPresent(SQLiteDatabase database, String id) {
        if (database == null) {
            throw new IllegalArgumentException("database must not be null");
        }
        if (id == null || id.isEmpty()) {
            throw new IllegalArgumentException("id must not be blank");
        }
        return database.delete(TreeLearnSchema.Attachments.TABLE,
                TreeLearnSchema.Attachments.ID + "=?", new String[]{id});
    }

    /**
     * The attachment ids no message references any more (T-098). This query is the orphan
     * judgement's <strong>single decision point</strong>: with foreign keys on (the helper's
     * {@code onConfigure}) and {@code message_id} cascading its reference rows away, every row in
     * {@code message_attachments} provably points at a live message — so "absent from the
     * reference table" is exactly the task's double confirmation, "zero reference rows AND no
     * message row", expressed once instead of twice.
     */
    public static List<String> orphanIds(SQLiteDatabase database) {
        if (database == null) {
            throw new IllegalArgumentException("database must not be null");
        }
        List<String> ids = new ArrayList<>();
        try (Cursor cursor = database.rawQuery("SELECT " + TreeLearnSchema.Attachments.ID
                + " FROM " + TreeLearnSchema.Attachments.TABLE + " WHERE "
                + TreeLearnSchema.Attachments.ID + " NOT IN (SELECT "
                + TreeLearnSchema.MessageAttachments.ATTACHMENT_ID + " FROM "
                + TreeLearnSchema.MessageAttachments.TABLE + ")", null)) {
            while (cursor.moveToNext()) {
                ids.add(cursor.getString(0));
            }
        }
        return ids;
    }

    /**
     * Lands one parse outcome on the record (T-097): the state becomes {@code parsed} or
     * {@code parse_failed}, and only a parsed record carries text. A pending record is the only
     * state this may be called from — a finished parse is final and must not be re-landed.
     */
    public static void updateParseOutcome(SQLiteDatabase database, String id, String parseState,
            String extractedText, long now) {
        if (database == null) {
            throw new IllegalArgumentException("database must not be null");
        }
        if (!TreeLearnSchema.Attachments.PARSE_STATES.contains(parseState)) {
            throw new IllegalArgumentException("unknown parse state: " + parseState);
        }
        Record current = findById(database, id);
        if (current == null) {
            throw new IllegalArgumentException("cannot parse a non-existent attachment " + id);
        }
        if (!"pending".equals(current.parseState)) {
            throw new IllegalStateException("attachment " + id + " already finished its parse as "
                    + current.parseState);
        }
        ContentValues values = new ContentValues();
        values.put(TreeLearnSchema.Attachments.PARSE_STATE, parseState);
        values.put(TreeLearnSchema.Attachments.EXTRACTED_TEXT, extractedText);
        values.put(TreeLearnSchema.Attachments.UPDATED_AT, now);
        int updated = database.update(TreeLearnSchema.Attachments.TABLE, values,
                TreeLearnSchema.Attachments.ID + "=?", new String[]{id});
        if (updated != 1) {
            throw new IllegalStateException("landing the parse of " + id + " touched " + updated
                    + " row(s)");
        }
    }

    public static void insertReference(SQLiteDatabase database, String referenceId,
            String messageId, String attachmentId, long now) {
        if (database == null) {
            throw new IllegalArgumentException("database must not be null");
        }
        if (referenceId == null || messageId == null || attachmentId == null) {
            throw new IllegalArgumentException("reference ids must not be null");
        }
        ContentValues values = new ContentValues();
        values.put(TreeLearnSchema.MessageAttachments.ID, referenceId);
        values.put(TreeLearnSchema.MessageAttachments.MESSAGE_ID, messageId);
        values.put(TreeLearnSchema.MessageAttachments.ATTACHMENT_ID, attachmentId);
        values.put(TreeLearnSchema.MessageAttachments.CREATED_AT, now);
        database.insertOrThrow(TreeLearnSchema.MessageAttachments.TABLE, null, values);
    }

    /** Every reference one message holds, resolved against the (possibly deleted) records. */
    public static List<ResolvedReference> referencesOfMessage(SQLiteDatabase database,
            String messageId) {
        if (database == null) {
            throw new IllegalArgumentException("database must not be null");
        }
        if (messageId == null || messageId.isEmpty()) {
            throw new IllegalArgumentException("messageId must not be blank");
        }
        List<ResolvedReference> references = new ArrayList<>();
        try (Cursor cursor = database.rawQuery("SELECT " + ATTACHMENT_COLUMNS + ", r."
                        + TreeLearnSchema.MessageAttachments.MESSAGE_ID + ", r."
                        + TreeLearnSchema.MessageAttachments.ATTACHMENT_ID + " FROM "
                        + TreeLearnSchema.MessageAttachments.TABLE + " r LEFT JOIN "
                        + TreeLearnSchema.Attachments.TABLE + " ON r."
                        + TreeLearnSchema.MessageAttachments.ATTACHMENT_ID + "="
                        + TreeLearnSchema.Attachments.TABLE + "." + TreeLearnSchema.Attachments.ID
                        + " WHERE r." + TreeLearnSchema.MessageAttachments.MESSAGE_ID + "=?"
                        + " ORDER BY r." + TreeLearnSchema.MessageAttachments.CREATED_AT + " ASC",
                new String[]{messageId})) {
            while (cursor.moveToNext()) {
                String attachmentId = cursor.getString(10);
                references.add(new ResolvedReference(messageId, attachmentId, record(cursor)));
            }
        }
        return references;
    }

    /** How many messages reference one attachment; the orphan scan's first half. */
    public static int referenceCount(SQLiteDatabase database, String attachmentId) {
        if (database == null) {
            throw new IllegalArgumentException("database must not be null");
        }
        try (Cursor cursor = database.rawQuery("SELECT COUNT(*) FROM "
                + TreeLearnSchema.MessageAttachments.TABLE + " WHERE "
                + TreeLearnSchema.MessageAttachments.ATTACHMENT_ID + "=?",
                new String[]{attachmentId})) {
            cursor.moveToFirst();
            return cursor.getInt(0);
        }
    }

    private static final String ATTACHMENT_COLUMNS =
            TreeLearnSchema.Attachments.TABLE + "." + TreeLearnSchema.Attachments.ID + ", "
                    + TreeLearnSchema.Attachments.TABLE + "." + TreeLearnSchema.Attachments.CONTENT_HASH
                    + ", " + TreeLearnSchema.Attachments.TABLE + "."
                    + TreeLearnSchema.Attachments.DISPLAY_NAME + ", "
                    + TreeLearnSchema.Attachments.TABLE + "." + TreeLearnSchema.Attachments.MIME_TYPE
                    + ", " + TreeLearnSchema.Attachments.TABLE + "."
                    + TreeLearnSchema.Attachments.SIZE_BYTES + ", "
                    + TreeLearnSchema.Attachments.TABLE + "."
                    + TreeLearnSchema.Attachments.EXTRACTED_TEXT + ", "
                    + TreeLearnSchema.Attachments.TABLE + "." + TreeLearnSchema.Attachments.PARSE_STATE
                    + ", " + TreeLearnSchema.Attachments.TABLE + "."
                    + TreeLearnSchema.Attachments.CREATED_AT + ", "
                    + TreeLearnSchema.Attachments.TABLE + "." + TreeLearnSchema.Attachments.UPDATED_AT;

    /**
     * Reads one record off a cursor positioned on the joined shape. Columns are read by position,
     * not by name: SQLite names joined result columns without the table prefix, so the prefixed
     * name is not addressable and the fixed order of {@link #ATTACHMENT_COLUMNS} is the contract
     * both queries are written against. A LEFT JOIN miss yields NULL at index 0, which reads as
     * null — the missing annotation.
     */
    private static Record record(Cursor cursor) {
        if (cursor.isNull(0)) {
            return null;
        }
        return new Record(
                cursor.getString(0), cursor.getString(1), cursor.getString(2), cursor.getString(3),
                cursor.getLong(4), cursor.isNull(5) ? null : cursor.getString(5),
                cursor.getString(6), cursor.getLong(7), cursor.getLong(8));
    }
}
