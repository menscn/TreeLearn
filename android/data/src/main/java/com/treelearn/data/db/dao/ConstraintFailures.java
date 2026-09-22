package com.treelearn.data.db.dao;

import android.database.sqlite.SQLiteConstraintException;

import com.treelearn.core.domain.DomainError;
import com.treelearn.core.domain.DomainErrorCode;
import com.treelearn.data.db.ConstraintExtendedCode;
import com.treelearn.data.db.TreeLearnSchema;

/**
 * The one place a store refusal becomes the domain error a caller has to branch on.
 *
 * <p><strong>There is deliberately no blanket "extended code → {@link DomainErrorCode}" table.</strong>
 * The same code carries different meanings depending on which index produced it: {@code 2067} is a
 * taken revision number in {@code answer_revisions} and a second in-flight attempt in
 * {@code generation_operations}. A lookup keyed on the code alone would therefore answer at least one
 * of them wrongly, which is the same defect as writing one rule twice — the second copy drifts. So
 * the translation happens per call site, through one shared implementation, and this class owns the
 * second half of every such translation — recognising which constraint spoke — while the first half,
 * reading the code, is delegated to {@link ConstraintExtendedCode} so that reading it once is the
 * only way to read it.
 *
 * <p><strong>How the code is read.</strong> Android's {@link SQLiteConstraintException} exposes no
 * accessor for the extended result code — verified against {@code android-36/android.jar}, whose
 * class declares three constructors and nothing else — so the code has to be read out of the message
 * text Android built for the failure. That text is <em>not</em> a stable format across releases, which
 * this class originally assumed it was: API 36 writes {@code (code 2067 ...)} where Android 13 writes
 * {@code (Sqlite code 2067 ...)} for the same refusal, so reading a fixed prefix answered "no code"
 * on the device and the given classification silently degraded to "storage refused". The reading rule
 * therefore lives in one shared, prefix-free place, {@link ConstraintExtendedCode}, and this class
 * only delegates to it. The codes themselves were measured on device rather than assumed: the API 36
 * probe is {@code artifacts/evidence/T-004-constraint-code-probe.txt}, and the Android 13 spelling is
 * recorded in {@code artifacts/evidence/T-059-constraint-codes.txt}.
 *
 * <p>Only the code this package has to name is declared here. Those probe files record the rest
 * (CHECK {@code 275}, NOT NULL {@code 1299}, foreign key {@code 787}, trigger {@code 1811}), and they
 * are deliberately <em>not</em> copied in yet: a constant with no reader is the same dead surface as a
 * {@link DomainErrorCode} with no producer, and the first caller that needs to name one should add it
 * together with that call site.
 *
 * <p>A refusal this class cannot name must be rethrown by the caller, not reported as something it is
 * not: a business code that hides a caller bug is worse than an exception that names it.
 */
final class ConstraintFailures {

    /** {@code SQLITE_CONSTRAINT_UNIQUE}, measured on API 36. */
    static final int CODE_UNIQUE = 2067;

    /**
     * The columns of {@code uq_answer_revisions_message_no}, spelled the way SQLite spells them in a
     * refusal instance. Built from the schema constants so a renamed column cannot leave this match
     * pointing at a name that no longer exists.
     */
    static final String REVISIONS_MESSAGE_NO =
            TreeLearnSchema.AnswerRevisions.TABLE + "." + TreeLearnSchema.AnswerRevisions.MESSAGE_ID
                    + ", " + TreeLearnSchema.AnswerRevisions.TABLE + "."
                    + TreeLearnSchema.AnswerRevisions.REVISION_NO;

    private static final String UNIQUE_PREFIX = "UNIQUE constraint failed: ";

    private ConstraintFailures() {
    }

    /**
     * @return the extended result code the store reported, or {@code 0} when the text carries none.
     *         Zero is not a SQLite result code, so it reads as "no code", never as a valid one.
     */
    static int extendedCode(SQLiteConstraintException failure) {
        return ConstraintExtendedCode.of(message(failure));
    }

    /**
     * @return whether the store refused specifically because {@code columns} were not unique, judged
     *         by both the code and the column list rather than by a message substring alone: a
     *         NOT NULL or CHECK refusal can name the same column, and treating that as a uniqueness
     *         collision would report the wrong cause to the caller.
     */
    static boolean isUniqueOn(SQLiteConstraintException failure, String columns) {
        return extendedCode(failure) == CODE_UNIQUE
                && message(failure).contains(UNIQUE_PREFIX + columns);
    }

    /**
     * @return the domain error for "this revision number is already used for this message position".
     *
     * <p>{@link DomainErrorCode#SEQUENCE_OUT_OF_ORDER} is the domain's own code for a sequence that did
     * not move forward, which is exactly what a taken number is. The alternative — reporting a store
     * condition — would make the caller parse SQLite text, and the alternative of overwriting is
     * refused by design: a stable revision is never rewritten, so the second writer has to move to a
     * number that is free.
     */
    static DomainError revisionNumberTaken(String messageId, int revisionNo) {
        return DomainError.of(DomainErrorCode.SEQUENCE_OUT_OF_ORDER,
                "revision number " + revisionNo + " is already used for message " + messageId
                        + "; a stable revision is never overwritten, so the writer has to take the "
                        + "next free number");
    }

    private static String message(SQLiteConstraintException failure) {
        if (failure == null) {
            throw new IllegalArgumentException("failure must not be null");
        }
        return failure.getMessage() == null ? "" : failure.getMessage();
    }
}
