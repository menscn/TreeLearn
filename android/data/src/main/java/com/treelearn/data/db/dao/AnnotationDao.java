package com.treelearn.data.db.dao;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.treelearn.core.domain.Annotation;
import com.treelearn.core.domain.Guard;
import com.treelearn.data.db.TreeLearnSchema;

import java.util.ArrayList;
import java.util.List;

/**
 * Row access for {@code message_annotations} (T-026): the write verbs the highlight/note flows are
 * built from, plus the digest-clearing read that makes regeneration self-healing (方案 V1.1 §3.3).
 *
 * <p><strong>The clear-on-mismatch read is one write path.</strong> {@link #listFor} deletes every
 * row of the message whose {@code content_digest} no longer matches the digest of the answer
 * currently displayed, then returns the survivors — deletion, never relocation (拍板 2026-09-28).
 * No code path has to remember to invalidate: whichever flow replaced the answer, the next read
 * heals the table. The delete and the select run inside one transaction and the select filters on
 * the digest itself (reviewer F2, 2026-09-28), so no interleaving write can smuggle a mismatched
 * row into a returned list. Android's nested-transaction semantics make this safe to call inside a
 * {@code DatabaseTransactionRunner} transaction: the inner begin/end join the caller's transaction
 * instead of committing early.
 *
 * <p><strong>Caller's duty on insert</strong> (reviewer F3, 2026-09-28): a mark is written only
 * while the answer is stable — {@code message.isStable()} — and the digest passed here is the one
 * computed from the revision currently displayed. Writing during streaming would store marks
 * against a digest that never becomes the stable one, and the next read would (correctly) clear
 * them.
 *
 * <p>Time points are the caller's application-written UTC epoch millis (rules/sql.md): the DAO
 * never reads a clock.
 */
public final class AnnotationDao {

    private AnnotationDao() {
    }

    /**
     * Stores one mark row.
     *
     * @throws IllegalArgumentException for a blank message id or digest, a null annotation, or a
     *                                  null database — judged here, before the DDL has to
     * @throws android.database.sqlite.SQLiteConstraintException when the row violates the anchor
     *                                                           uniqueness, a CHECK, or the message
     *                                                           foreign key
     */
    public static void insert(SQLiteDatabase database, String messageId, String contentDigest,
                              Annotation annotation, long at) {
        if (database == null) {
            throw new IllegalArgumentException("database must not be null");
        }
        Guard.id(messageId, "messageId");
        Guard.nonBlank(contentDigest, "contentDigest");
        Guard.object(annotation, "annotation");

        ContentValues values = new ContentValues();
        values.put(TreeLearnSchema.Annotations.ID, annotation.id);
        values.put(TreeLearnSchema.Annotations.MESSAGE_ID, messageId);
        values.put(TreeLearnSchema.Annotations.CONTAINER, annotation.locator.container.name());
        values.put(TreeLearnSchema.Annotations.PROSE_INDEX, annotation.locator.proseIndex);
        values.put(TreeLearnSchema.Annotations.TABLE_INDEX, annotation.locator.tableIndex);
        values.put(TreeLearnSchema.Annotations.ROW_INDEX, annotation.locator.rowIndex);
        values.put(TreeLearnSchema.Annotations.COL_INDEX, annotation.locator.colIndex);
        values.put(TreeLearnSchema.Annotations.START_OFFSET, annotation.span.start);
        values.put(TreeLearnSchema.Annotations.END_OFFSET, annotation.span.end);
        values.put(TreeLearnSchema.Annotations.KIND, annotation.kind.name());
        values.put(TreeLearnSchema.Annotations.CONTENT_DIGEST, contentDigest);
        values.put(TreeLearnSchema.Annotations.BODY, annotation.body);
        values.put(TreeLearnSchema.Annotations.CREATED_AT, at);
        values.put(TreeLearnSchema.Annotations.UPDATED_AT, at);
        // insertOrThrow, deliberately not insert(): the plain insert swallows a constraint
        // violation and returns -1, which would turn the unique-anchor dedup and every CHECK into
        // a silent no-op — the refusal must reach the caller as the exception it is.
        database.insertOrThrow(TreeLearnSchema.Annotations.TABLE, null, values);
    }

    /**
     * The marks of one message whose digest still matches the displayed answer, in anchor order —
     * and, first, the removal of every row of this message whose digest does not (方案 §3.3).
     *
     * @return the surviving marks, ordered by container and anchor (never null)
     * @throws IllegalArgumentException for a blank message id or a null digest
     */
    public static List<Annotation> listFor(SQLiteDatabase database, String messageId,
                                           String contentDigest) {
        if (database == null) {
            throw new IllegalArgumentException("database must not be null");
        }
        Guard.id(messageId, "messageId");
        Guard.nonBlank(contentDigest, "contentDigest");

        database.beginTransaction();
        try {
            database.delete(TreeLearnSchema.Annotations.TABLE,
                    TreeLearnSchema.Annotations.MESSAGE_ID + "=? AND "
                            + TreeLearnSchema.Annotations.CONTENT_DIGEST + " <> ?",
                    new String[]{messageId, contentDigest});
            List<Annotation> marks = new ArrayList<>();
            try (Cursor cursor = database.query(TreeLearnSchema.Annotations.TABLE, null,
                    TreeLearnSchema.Annotations.MESSAGE_ID + "=? AND "
                            + TreeLearnSchema.Annotations.CONTENT_DIGEST + "=?",
                    new String[]{messageId, contentDigest}, null, null, ANCHOR_ORDER)) {
                while (cursor.moveToNext()) {
                    marks.add(read(cursor));
                }
            }
            database.setTransactionSuccessful();
            return marks;
        } finally {
            database.endTransaction();
        }
    }

    /** The stable read order: by anchor, then by write time. Renderer order is the caller's job. */
    private static final String ANCHOR_ORDER =
            TreeLearnSchema.Annotations.CONTAINER + ", " + TreeLearnSchema.Annotations.PROSE_INDEX
                    + ", " + TreeLearnSchema.Annotations.TABLE_INDEX + ", "
                    + TreeLearnSchema.Annotations.ROW_INDEX + ", "
                    + TreeLearnSchema.Annotations.COL_INDEX + ", "
                    + TreeLearnSchema.Annotations.START_OFFSET + ", "
                    + TreeLearnSchema.Annotations.END_OFFSET + ", "
                    + TreeLearnSchema.Annotations.CREATED_AT;

    /**
     * Removes one mark. Idempotent on purpose: a double tap on "remove" and a re-run of an
     * absorbed-row cleanup are both ordinary, so deleting an absent id is a no-op, not an error.
     */
    public static void delete(SQLiteDatabase database, String id) {
        if (database == null) {
            throw new IllegalArgumentException("database must not be null");
        }
        Guard.id(id, "id");
        database.delete(TreeLearnSchema.Annotations.TABLE, TreeLearnSchema.Annotations.ID + "=?",
                new String[]{id});
    }

    /**
     * Rewrites a note's body.
     *
     * @throws IllegalArgumentException for a blank body — a note without text is not a note
     * @throws IllegalStateException    when no row carries {@code id}, or the row is a highlight
     *                                  (whose body is empty by DDL, reviewer F1 — the DAO refuses
     *                                  before the CHECK has to)
     */
    public static void updateBody(SQLiteDatabase database, String id, String body, long updatedAt) {
        if (database == null) {
            throw new IllegalArgumentException("database must not be null");
        }
        Guard.id(id, "id");
        if (body == null || body.trim().isEmpty()) {
            throw new IllegalArgumentException("a note's body must not be blank");
        }

        String kind = rawKind(database, id);
        if (kind == null) {
            throw new IllegalStateException("no annotation row carries " + id
                    + "; a body update must touch exactly one stored note");
        }
        if (Annotation.Kind.HIGHLIGHT.name().equals(kind)) {
            throw new IllegalStateException("annotation " + id + " is a highlight; its body is"
                    + " empty by DDL and a body update is a note-only operation");
        }
        ContentValues values = new ContentValues();
        values.put(TreeLearnSchema.Annotations.BODY, body);
        values.put(TreeLearnSchema.Annotations.UPDATED_AT, updatedAt);
        int updated = database.update(TreeLearnSchema.Annotations.TABLE, values,
                TreeLearnSchema.Annotations.ID + "=?", new String[]{id});
        if (updated != 1) {
            throw new IllegalStateException("updating the body of annotation " + id + " affected "
                    + updated + " row(s); it must touch exactly one");
        }
    }

    private static String rawKind(SQLiteDatabase database, String id) {
        try (Cursor cursor = database.query(TreeLearnSchema.Annotations.TABLE,
                new String[]{TreeLearnSchema.Annotations.KIND},
                TreeLearnSchema.Annotations.ID + "=?", new String[]{id}, null, null, null)) {
            return cursor.moveToFirst()
                    ? Rows.string(cursor, TreeLearnSchema.Annotations.KIND)
                    : null;
        }
    }

    private static Annotation read(Cursor cursor) {
        String container = Rows.string(cursor, TreeLearnSchema.Annotations.CONTAINER);
        Annotation.Locator locator;
        if (Annotation.Container.PROSE.name().equals(container)) {
            locator = Annotation.Locator.prose(
                    Rows.intOf(cursor, TreeLearnSchema.Annotations.PROSE_INDEX));
        } else if (Annotation.Container.CELL.name().equals(container)) {
            locator = Annotation.Locator.cell(
                    Rows.intOf(cursor, TreeLearnSchema.Annotations.TABLE_INDEX),
                    Rows.intOf(cursor, TreeLearnSchema.Annotations.ROW_INDEX),
                    Rows.intOf(cursor, TreeLearnSchema.Annotations.COL_INDEX));
        } else {
            // Unreachable through the DDL's container CHECK; a row written around it fails loudly
            // rather than silently reshaping itself into a locator.
            throw new IllegalStateException("annotation row carries an unknown container: "
                    + container);
        }
        Annotation.Span span = Annotation.Span.of(
                Rows.intOf(cursor, TreeLearnSchema.Annotations.START_OFFSET),
                Rows.intOf(cursor, TreeLearnSchema.Annotations.END_OFFSET));
        // Rebuilt through the public factories, not a private constructor: the DDL guarantees a
        // highlight's body is '' and a note's is non-blank (F1), which is exactly what the
        // factories admit, so no second construction path is needed.
        Annotation.Kind kind = Annotation.Kind.valueOf(
                Rows.string(cursor, TreeLearnSchema.Annotations.KIND));
        String body = Rows.string(cursor, TreeLearnSchema.Annotations.BODY);
        String id = Rows.string(cursor, TreeLearnSchema.Annotations.ID);
        return kind == Annotation.Kind.NOTE
                ? Annotation.note(id, locator, span.start, span.end, body)
                : Annotation.highlight(id, locator, span.start, span.end);
    }
}
