package com.treelearn.web.storage;

import com.treelearn.core.domain.DomainResult;
import com.treelearn.core.domain.Message;
import com.treelearn.core.domain.MessageRole;
import com.treelearn.web.storage.DesktopSchema.AnswerRevisions;
import com.treelearn.web.storage.DesktopSchema.Messages;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.List;
import java.util.Optional;

/**
 * Row access for {@code messages} (task T-2026-10-10-010).
 *
 * <p>Semantic reference — the Android {@code com.treelearn.data.db.dao} implementation it mirrors:
 * <ul>
 *   <li>{@code MessageDao.insert} / {@code findById} / {@code findByBranch} →
 *       {@link #appendUserMessage} / {@link #appendAiPosition} / {@link #get(String)} /
 *       {@link #listForBranch(String)}.</li>
 *   <li>{@code MessageDao.updateActiveReferences} → {@link #setActiveRevision} /
 *       {@link #setActiveOperation}: a position keeps showing its old answer until a new one is
 *       activated, and resetting repoints the row rather than rewriting it. Both load the position and
 *       move it through {@link Message}'s own transitions before persisting, so the domain — not a
 *       direct column write — decides whether the change is legal.</li>
 *   <li>{@code MessageDao.quoteColumnValue} / {@code quoteFromColumn} → the one translation between
 *       the domain's empty string and the store's {@code NULL} for §6.2.19 quotes, owned here in both
 *       directions and nowhere else.</li>
 * </ul>
 *
 * <p>{@link #listForBranch(String)} carries the active revision's content alongside each position, so
 * the chat list is one query rather than a read per message. A position whose {@code
 * active_revision_id} is null returns null content — "no displayed answer yet" is an ordinary state.
 */
public final class MessageStore {

    private static final String INSERT_MESSAGE = "INSERT INTO " + Messages.TABLE + "(" + Messages.ID
            + ", " + Messages.BRANCH_ID + ", " + Messages.ORDINAL + ", " + Messages.ROLE + ", "
            + Messages.USER_CONTENT + ", " + Messages.QUOTED_TEXT + ", " + Messages.CREATED_AT
            + ") VALUES (?, ?, ?, ?, ?, ?, ?)";
    private static final String SELECT_ONE = "SELECT * FROM " + Messages.TABLE + " WHERE " + Messages.ID
            + " = ?";
    private static final String A_ANSWER = "active_answer";
    private static final String A_REASONING = "active_reasoning";
    private static final String A_REVISION_NO = "active_revision_no";
    private static final String SELECT_FOR_BRANCH = "SELECT m.*, r." + AnswerRevisions.ANSWER_CONTENT
            + " AS " + A_ANSWER + ", r." + AnswerRevisions.REASONING_CONTENT + " AS " + A_REASONING
            + ", r." + AnswerRevisions.REVISION_NO + " AS " + A_REVISION_NO + " FROM " + Messages.TABLE
            + " m LEFT JOIN " + AnswerRevisions.TABLE + " r ON r." + AnswerRevisions.ID + " = m."
            + Messages.ACTIVE_REVISION_ID + " WHERE m." + Messages.BRANCH_ID + " = ? ORDER BY m."
            + Messages.ORDINAL + " ASC";
    private static final String SET_ACTIVE_REFERENCES = "UPDATE " + Messages.TABLE + " SET "
            + Messages.ACTIVE_REVISION_ID + " = ?, " + Messages.ACTIVE_OPERATION_ID
            + " = ? WHERE " + Messages.ID + " = ?";
    private static final String NEXT_ORDINAL = "SELECT COALESCE(MAX(" + Messages.ORDINAL + "), -1) + 1"
            + " FROM " + Messages.TABLE + " WHERE " + Messages.BRANCH_ID + " = ?";
    private static final String SELECT_USER_CONTENT = "SELECT " + Messages.USER_CONTENT + " FROM "
            + Messages.TABLE + " WHERE " + Messages.BRANCH_ID + " = ? AND " + Messages.ROLE + " = '"
            + MessageRole.USER.name() + "' ORDER BY " + Messages.ORDINAL + " ASC";

    private final JdbcSupport jdbc;

    public MessageStore(JdbcSupport jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Appends an immutable learner position. {@code quotedText} may be null — null and the empty
     * string both mean "quotes nothing", and the store holds that as {@code NULL} (§6.2.19).
     */
    public Message appendUserMessage(String branchId, int ordinal, String content, String quotedText,
            long now) {
        Message message = Message.user(branchId, ordinal, content, quotedText == null ? "" : quotedText,
                now);
        jdbc.update(INSERT_MESSAGE, insertBinder(message));
        return message;
    }

    /** Appends an AI position: a fixed slot for an answer, carrying no content of its own. */
    public Message appendAiPosition(String branchId, int ordinal, long now) {
        Message message = Message.ai(branchId, ordinal, now);
        jdbc.update(INSERT_MESSAGE, insertBinder(message));
        return message;
    }

    /**
     * Transaction-scoped insert of an already-built position: the caller owns the boundary, so the two
     * positions a follow-up or first question commits land beside their attempt in one transaction.
     */
    public void insert(Connection connection, Message message) {
        jdbc.update(connection, INSERT_MESSAGE, insertBinder(message));
    }

    /** The column binding of {@link #INSERT_MESSAGE}, shared by the transactional and standalone writers. */
    private static JdbcSupport.Binder insertBinder(Message message) {
        return s -> {
            s.setString(1, message.id); s.setString(2, message.branchId); s.setInt(3, message.ordinal);
            s.setString(4, message.role.name());
            if (message.isUser()) {
                s.setString(5, message.userContent);
                s.setString(6, message.quotedText().isEmpty() ? null : message.quotedText());
            } else {
                s.setNull(5, Types.VARCHAR); s.setNull(6, Types.VARCHAR);
            }
            s.setLong(7, message.createdAt);
        };
    }

    public Optional<Message> get(String messageId) {
        return jdbc.queryOne(SELECT_ONE, s -> s.setString(1, messageId), MessageStore::readMessage);
    }

    /** Transaction-scoped read of one position, for a caller that already holds the connection. */
    public Optional<Message> get(Connection connection, String messageId) {
        return jdbc.queryOne(connection, SELECT_ONE, s -> s.setString(1, messageId),
                MessageStore::readMessage);
    }

    /** Every position of a branch in conversation order, each with its displayed revision content. */
    public List<Position> listForBranch(String branchId) {
        return jdbc.queryList(SELECT_FOR_BRANCH, s -> s.setString(1, branchId),
                MessageStore::readPosition);
    }

    /**
     * Switches which stable revision a position displays.
     *
     * <p>{@link Message#activateRevision(String)} is the invariant authority, mirroring Android's
     * {@code MessageDao.updateActiveReferences} which persists the references of a domain aggregate the
     * caller has already moved through the shared type. This method loads the position, lets the shared
     * type decide — a USER row cannot display a revision, an in-flight attempt must be ended first, and
     * a revision already displayed is refused — and only then persists both references. A refused
     * transition surfaces as {@link StorageException} naming the message and the reason, rather than the
     * row being written around the rule.
     */
    public void setActiveRevision(String messageId, String revisionId) {
        Message message = requireMessage(messageId);
        DomainResult<Void> outcome = message.activateRevision(revisionId);
        if (outcome.isFailure()) {
            throw new StorageException("cannot switch the active revision of message " + messageId
                    + ": " + outcome.error());
        }
        persistActiveReferences(message);
    }

    /**
     * Binds the attempt currently running at a position, or clears it when {@code operationId} is null.
     *
     * <p>{@link Message#bindOperation(String)} / {@link Message#clearActiveOperation()} are the
     * authority here, the same load→decide→persist shape as any other mutation: an attempt cannot start
     * while another is in flight, and only an AI position can run one. A refused bind surfaces as
     * {@link StorageException} naming the message and the reason.
     */
    public void setActiveOperation(String messageId, String operationId) {
        Message message = requireMessage(messageId);
        if (operationId == null) {
            message.clearActiveOperation();
        } else {
            DomainResult<Void> outcome = message.bindOperation(operationId);
            if (outcome.isFailure()) {
                throw new StorageException("cannot bind the active operation of message " + messageId
                        + ": " + outcome.error());
            }
        }
        persistActiveReferences(message);
    }

    /**
     * Clears the running attempt of a position without touching the displayed revision: the write a
     * non-stable outcome (failed, interrupted, canceled) leaves behind, so the position no longer points
     * at an attempt that has ended. An already-gone row is refused, which the coordinator treats as a
     * discard rather than an error.
     */
    public void clearActiveOperation(String messageId) {
        Message message = requireMessage(messageId);
        message.clearActiveOperation();
        persistActiveReferences(message);
    }

    private Message requireMessage(String messageId) {
        return get(messageId).orElseThrow(
                () -> new StorageException("no message row carries id " + messageId));
    }

    /** Persists both active references at once, mirroring Android's {@code updateActiveReferences}. */
    private void persistActiveReferences(Message message) {
        JdbcSupport.requireSingleRow(jdbc.update(SET_ACTIVE_REFERENCES, referencesBinder(message)),
                "updating the active references of message " + message.id);
    }

    /** Transaction-scoped half of {@link #persistActiveReferences(Message)}, for the stabilizer. */
    public void persistActiveReferences(Connection connection, Message message) {
        JdbcSupport.requireSingleRow(jdbc.update(connection, SET_ACTIVE_REFERENCES,
                referencesBinder(message)), "updating the active references of message " + message.id);
    }

    private static JdbcSupport.Binder referencesBinder(Message message) {
        return s -> {
            s.setString(1, message.activeRevisionId());
            s.setString(2, message.activeOperationId());
            s.setString(3, message.id);
        };
    }

    /** The next free ordinal in a branch, read from the rows so it survives a restart. */
    public int nextOrdinal(String branchId) {
        return jdbc.queryOne(NEXT_ORDINAL, s -> s.setString(1, branchId),
                rows -> Integer.valueOf(rows.getInt(1))).orElse(0);
    }

    /** The learner's own text of a branch, in order — for statistics and search. */
    public List<String> listUserContents(String branchId) {
        return jdbc.queryList(SELECT_USER_CONTENT, s -> s.setString(1, branchId),
                rows -> rows.getString(1));
    }

    private static Message readMessage(ResultSet rows) throws SQLException {
        String quoted = rows.getString(Messages.QUOTED_TEXT);
        return Message.restore(rows.getString(Messages.ID), rows.getString(Messages.BRANCH_ID),
                MessageRole.valueOf(rows.getString(Messages.ROLE)), rows.getInt(Messages.ORDINAL),
                rows.getString(Messages.USER_CONTENT), quoted == null ? "" : quoted,
                rows.getLong(Messages.CREATED_AT), rows.getString(Messages.ACTIVE_REVISION_ID),
                rows.getString(Messages.ACTIVE_OPERATION_ID));
    }

    private static Position readPosition(ResultSet rows) throws SQLException {
        return new Position(readMessage(rows), rows.getString(A_ANSWER), rows.getString(A_REASONING),
                JdbcSupport.nullableInt(rows, A_REVISION_NO));
    }

    /** One conversation row: the position plus the content of the revision it currently displays. */
    public static final class Position {

        public final Message message;
        /** The displayed revision's final answer, or null when the position displays none. */
        public final String answerContent;
        /** The displayed revision's reasoning, or null when the position displays none. */
        public final String reasoningContent;
        /** The displayed revision's number, or null when the position displays none. */
        public final Integer revisionNo;

        Position(Message message, String answerContent, String reasoningContent, Integer revisionNo) {
            this.message = message; this.answerContent = answerContent;
            this.reasoningContent = reasoningContent; this.revisionNo = revisionNo;
        }

        public boolean hasActiveRevision() {
            return answerContent != null;
        }
    }
}
