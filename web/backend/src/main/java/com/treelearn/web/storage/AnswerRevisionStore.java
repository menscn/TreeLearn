package com.treelearn.web.storage;

import com.treelearn.core.domain.AnswerRevision;
import com.treelearn.core.domain.DomainResult;
import com.treelearn.core.domain.GenerationModel;
import com.treelearn.web.storage.DesktopSchema.AnswerRevisions;
import com.treelearn.web.storage.DesktopSchema.Messages;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/**
 * Row access for {@code answer_revisions} (task T-2026-10-10-010).
 *
 * <p>Semantic reference — the Android {@code com.treelearn.data.db.dao} implementation it mirrors:
 * <ul>
 *   <li>{@code AnswerRevisionDao.insert} plus {@code nextRevisionNo} → {@link #insert}: the number is
 *       read from the stored rows (max + 1), inside the same transaction as the write, so it survives a
 *       restart and a race is still settled by {@code uq_answer_revisions_message_no}. The gap Android
 *       leaves to its caller is closed here, because this task makes the store the owner.</li>
 *   <li>{@code AnswerRevisionDao.findById} / {@code findByMessage} → {@link #get(String)} /
 *       {@link #listForMessage(String)}.</li>
 *   <li>The active revision of a position ({@code messages.active_revision_id}) →
 *       {@link #findActiveForMessage(String)}: the position, not the highest number, decides which
 *       revision it displays.</li>
 *   <li>A revision is written once and never again: there is no update and no delete, and the store's
 *       triggers refuse both, so no method here offers one.</li>
 * </ul>
 *
 * <p>{@link AnswerRevision#create} remains the invariant authority: it validates the shape and refuses
 * a version no purpose owns on the way in, so a revision with an unknown policy version is never
 * stored rather than being detected later.
 */
public final class AnswerRevisionStore {

    private static final String INSERT_REVISION = "INSERT INTO " + AnswerRevisions.TABLE + "("
            + AnswerRevisions.ID + ", " + AnswerRevisions.MESSAGE_ID + ", " + AnswerRevisions.REVISION_NO
            + ", " + AnswerRevisions.REASONING_CONTENT + ", " + AnswerRevisions.ANSWER_CONTENT + ", "
            + AnswerRevisions.MODEL + ", " + AnswerRevisions.THINKING_ENABLED + ", "
            + AnswerRevisions.PROMPT_POLICY_VERSION + ", " + AnswerRevisions.CREATED_AT
            + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
    private static final String SELECT_ONE = "SELECT * FROM " + AnswerRevisions.TABLE + " WHERE "
            + AnswerRevisions.ID + " = ?";
    private static final String SELECT_FOR_MESSAGE = "SELECT * FROM " + AnswerRevisions.TABLE + " WHERE "
            + AnswerRevisions.MESSAGE_ID + " = ? ORDER BY " + AnswerRevisions.REVISION_NO + " ASC";
    private static final String SELECT_ACTIVE = "SELECT r.* FROM " + AnswerRevisions.TABLE + " r JOIN "
            + Messages.TABLE + " m ON m." + Messages.ACTIVE_REVISION_ID + " = r." + AnswerRevisions.ID
            + " WHERE m." + Messages.ID + " = ?";
    private static final String NEXT_REVISION_NO = "SELECT COALESCE(MAX(" + AnswerRevisions.REVISION_NO
            + "), 0) + 1 FROM " + AnswerRevisions.TABLE + " WHERE " + AnswerRevisions.MESSAGE_ID + " = ?";
    private static final String COUNT_FOR_MESSAGE = "SELECT COUNT(*) FROM " + AnswerRevisions.TABLE
            + " WHERE " + AnswerRevisions.MESSAGE_ID + " = ?";

    private final JdbcSupport jdbc;

    public AnswerRevisionStore(JdbcSupport jdbc) {
        this.jdbc = jdbc;
    }

    /** Records a new stable revision; its {@code revision_no} is one above the message's highest. */
    public AnswerRevision insert(String messageId, String reasoningContent, String answerContent,
            GenerationModel model, boolean thinkingEnabled, String promptPolicyVersion, long now) {
        return jdbc.runInTransaction(connection -> {
            int revisionNo = nextRevisionNo(connection, messageId);
            DomainResult<AnswerRevision> created = AnswerRevision.create(messageId, revisionNo,
                    reasoningContent, answerContent, model, thinkingEnabled, promptPolicyVersion, now);
            if (created.isFailure()) {
                throw new StorageException("the revision could not be recorded: " + created.error());
            }
            AnswerRevision revision = created.value();
            insertRevision(connection, revision);
            return revision;
        });
    }

    /**
     * Transaction-scoped next {@code revision_no} for a position: read from the stored rows so it
     * survives a restart, and read inside the caller's transaction so the number and the revision that
     * uses it commit together (a taken number is still settled by {@code uq_answer_revisions_message_no}).
     */
    public int nextRevisionNo(Connection connection, String messageId) {
        return jdbc.queryOne(connection, NEXT_REVISION_NO, s -> s.setString(1, messageId),
                rows -> Integer.valueOf(rows.getInt(1))).orElse(1);
    }

    /**
     * Transaction-scoped insert of an already-built revision. The caller (the desktop
     * {@code GenerationStabilizer}) has validated and numbered it through {@link AnswerRevision#create}
     * inside the same transaction that moves the attempt to {@code STABLE}.
     */
    public void insertRevision(Connection connection, AnswerRevision revision) {
        jdbc.update(connection, INSERT_REVISION, s -> {
            s.setString(1, revision.id); s.setString(2, revision.messageId);
            s.setInt(3, revision.revisionNo); s.setString(4, revision.reasoningContent);
            s.setString(5, revision.answerContent); s.setString(6, revision.model.wireId);
            s.setInt(7, revision.thinkingEnabled ? 1 : 0); s.setString(8, revision.promptPolicyVersion);
            s.setLong(9, revision.createdAt);
        });
    }

    public Optional<AnswerRevision> get(String revisionId) {
        return jdbc.queryOne(SELECT_ONE, s -> s.setString(1, revisionId),
                AnswerRevisionStore::readRevision);
    }

    /** Every revision of one position, lowest number first. */
    public List<AnswerRevision> listForMessage(String messageId) {
        return jdbc.queryList(SELECT_FOR_MESSAGE, s -> s.setString(1, messageId),
                AnswerRevisionStore::readRevision);
    }

    /**
     * The revision the position currently displays, or empty while it displays none.
     *
     * <p>It reads the join on {@code messages.active_revision_id}, so the position — not the highest
     * revision number — decides which revision is active. The name follows Android's
     * {@code ...findActiveBy...} vocabulary ({@code GenerationOperationDao.findActiveByBranch}):
     * "active", not "latest", because a later revision number was never necessarily displayed.
     */
    public Optional<AnswerRevision> findActiveForMessage(String messageId) {
        return jdbc.queryOne(SELECT_ACTIVE, s -> s.setString(1, messageId),
                AnswerRevisionStore::readRevision);
    }

    public int countForMessage(String messageId) {
        return jdbc.queryOne(COUNT_FOR_MESSAGE, s -> s.setString(1, messageId),
                rows -> Integer.valueOf(rows.getInt(1))).orElse(0);
    }

    private static AnswerRevision readRevision(ResultSet rows) throws SQLException {
        return AnswerRevision.restore(rows.getString(AnswerRevisions.ID),
                rows.getString(AnswerRevisions.MESSAGE_ID), rows.getInt(AnswerRevisions.REVISION_NO),
                rows.getString(AnswerRevisions.REASONING_CONTENT),
                rows.getString(AnswerRevisions.ANSWER_CONTENT),
                GenerationModel.fromWireId(rows.getString(AnswerRevisions.MODEL)),
                JdbcSupport.flag(rows, AnswerRevisions.THINKING_ENABLED),
                rows.getString(AnswerRevisions.PROMPT_POLICY_VERSION),
                rows.getLong(AnswerRevisions.CREATED_AT));
    }
}
