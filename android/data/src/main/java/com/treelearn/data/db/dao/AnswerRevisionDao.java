package com.treelearn.data.db.dao;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.treelearn.core.domain.AnswerRevision;
import com.treelearn.core.domain.GenerationModel;
import com.treelearn.data.db.TreeLearnSchema;

import java.util.ArrayList;
import java.util.List;

/**
 * Row access for {@code answer_revisions}.
 *
 * <p>A revision is written once and never again. The table has no {@code update} and no {@code delete}
 * here, the store's triggers refuse both, and the only way a row disappears is together with the
 * message it belongs to — so this class offers no method that would try.
 *
 * <p>Writes go through {@link AnswerRevision#create}, the invariant authority: it validates the shape
 * and refuses a version no purpose owns. The single {@link AnswerRevision#restore} call in the
 * production sources sits in the read mapping below; rehydrating on a write path would skip the
 * "may this version be recorded" question entirely, which is why that name is deliberately
 * conspicuous.
 */
public final class AnswerRevisionDao {

    private AnswerRevisionDao() {
    }

    /**
     * Writes a stable revision.
     *
     * <p>{@code revision_no} comes from the caller — {@link #nextRevisionNo} is where that number is
     * read from — so a number that is already used is refused by
     * {@code uq_answer_revisions_message_no} rather than detected here. For
     * {@link GenerationStabilizer} that refusal is a caller-visible outcome and is translated; for a
     * direct caller it propagates as {@link android.database.sqlite.SQLiteConstraintException}, the
     * same way a duplicate message ordinal does in {@link MessageDao}.
     */
    public static void insert(SQLiteDatabase database, AnswerRevision revision) {
        if (revision == null) {
            throw new IllegalArgumentException("revision must not be null");
        }
        database.insertOrThrow(TreeLearnSchema.AnswerRevisions.TABLE, null, values(revision));
    }

    /** @return the revision, or null when no row carries this id */
    public static AnswerRevision findById(SQLiteDatabase database, String revisionId) {
        if (revisionId == null || revisionId.isEmpty()) {
            throw new IllegalArgumentException("revisionId must not be blank");
        }
        try (Cursor cursor = database.query(TreeLearnSchema.AnswerRevisions.TABLE, null,
                TreeLearnSchema.AnswerRevisions.ID + "=?", new String[]{revisionId},
                null, null, null)) {
            return cursor.moveToFirst() ? read(cursor) : null;
        }
    }

    /** @return every revision of one message position, lowest number first */
    public static List<AnswerRevision> findByMessage(SQLiteDatabase database, String messageId) {
        if (messageId == null || messageId.isEmpty()) {
            throw new IllegalArgumentException("messageId must not be blank");
        }
        List<AnswerRevision> revisions = new ArrayList<>();
        try (Cursor cursor = database.query(TreeLearnSchema.AnswerRevisions.TABLE, null,
                TreeLearnSchema.AnswerRevisions.MESSAGE_ID + "=?", new String[]{messageId},
                null, null, TreeLearnSchema.AnswerRevisions.REVISION_NO + " ASC")) {
            while (cursor.moveToNext()) {
                revisions.add(read(cursor));
            }
        }
        return revisions;
    }

    /**
     * The next {@code revision_no} for a position: one above the highest already stored, or 1.
     *
     * <p>This is the repository fact {@code GenerationOperation.stabilize} leaves to its caller,
     * because an attempt cannot know its position's revision sequence on its own. It is read from the
     * stored rows rather than from a counter in memory, so it survives a restart. Two callers that
     * race still reach the same number, and then the unique index decides: the loser is told to move
     * on instead of overwriting a revision somebody has already seen.
     */
    public static int nextRevisionNo(SQLiteDatabase database, String messageId) {
        if (messageId == null || messageId.isEmpty()) {
            throw new IllegalArgumentException("messageId must not be blank");
        }
        try (Cursor cursor = database.query(TreeLearnSchema.AnswerRevisions.TABLE,
                new String[]{"MAX(" + TreeLearnSchema.AnswerRevisions.REVISION_NO + ")"},
                TreeLearnSchema.AnswerRevisions.MESSAGE_ID + "=?", new String[]{messageId},
                null, null, null)) {
            if (cursor.moveToFirst() && !cursor.isNull(0)) {
                return cursor.getInt(0) + 1;
            }
            return 1;
        }
    }

    /**
     * The read mapping, and the only production {@link AnswerRevision#restore} call site.
     *
     * <p>It re-asserts nothing about the stored version, for the reason
     * {@code AnswerRevision.restore} states: on reload the row is the authority. Shape checks still
     * run inside the constructor, so a corrupt row fails loudly. One boundary worth naming: the store
     * only requires {@code length(answer_content) > 0} while the domain requires it to be non-blank,
     * so a whitespace-only answer is a row the store admits and this mapping refuses with
     * {@link IllegalArgumentException} — loud, and never a silent empty answer.
     */
    private static AnswerRevision read(Cursor cursor) {
        return AnswerRevision.restore(
                Rows.string(cursor, TreeLearnSchema.AnswerRevisions.ID),
                Rows.string(cursor, TreeLearnSchema.AnswerRevisions.MESSAGE_ID),
                Rows.intOf(cursor, TreeLearnSchema.AnswerRevisions.REVISION_NO),
                Rows.string(cursor, TreeLearnSchema.AnswerRevisions.REASONING_CONTENT),
                Rows.string(cursor, TreeLearnSchema.AnswerRevisions.ANSWER_CONTENT),
                GenerationModel.fromWireId(
                        Rows.string(cursor, TreeLearnSchema.AnswerRevisions.MODEL)),
                Rows.bool(cursor, TreeLearnSchema.AnswerRevisions.THINKING_ENABLED),
                Rows.string(cursor, TreeLearnSchema.AnswerRevisions.PROMPT_POLICY_VERSION),
                Rows.longOf(cursor, TreeLearnSchema.AnswerRevisions.CREATED_AT));
    }

    private static ContentValues values(AnswerRevision revision) {
        ContentValues values = new ContentValues();
        values.put(TreeLearnSchema.AnswerRevisions.ID, revision.id);
        values.put(TreeLearnSchema.AnswerRevisions.MESSAGE_ID, revision.messageId);
        values.put(TreeLearnSchema.AnswerRevisions.REVISION_NO, revision.revisionNo);
        values.put(TreeLearnSchema.AnswerRevisions.REASONING_CONTENT, revision.reasoningContent);
        values.put(TreeLearnSchema.AnswerRevisions.ANSWER_CONTENT, revision.answerContent);
        values.put(TreeLearnSchema.AnswerRevisions.MODEL, revision.model.wireId);
        values.put(TreeLearnSchema.AnswerRevisions.THINKING_ENABLED, revision.thinkingEnabled ? 1 : 0);
        values.put(TreeLearnSchema.AnswerRevisions.PROMPT_POLICY_VERSION,
                revision.promptPolicyVersion);
        values.put(TreeLearnSchema.AnswerRevisions.CREATED_AT, revision.createdAt);
        return values;
    }
}
