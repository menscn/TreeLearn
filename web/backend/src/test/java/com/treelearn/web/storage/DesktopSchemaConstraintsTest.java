package com.treelearn.web.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * T-2026-10-10-009: representative constraints of the final desktop shape, executed on the real
 * sqlite-jdbc engine with foreign keys enforced. Every case asserts that the store refuses an
 * illegal write and accepts the legal counterpart, so the DDL cannot silently drift from the
 * Android V5 semantics it mirrors.
 */
class DesktopSchemaConstraintsTest {

    private static final String SEEDED_PROVIDER_ID = DesktopSchema.BUILTIN_DEEPSEEK_PROVIDER_ID;

    @TempDir
    Path dataDir;

    private Connection connection;

    @BeforeEach
    void openMigratedDatabase() throws Exception {
        Path dbPath = dataDir.resolve("treelearn.db");
        new DatabaseMigrator().migrate(dbPath);
        connection = new DesktopDatabase(dataDir).openConnection();
    }

    @AfterEach
    void closeDatabase() throws Exception {
        connection.close();
    }

    @Test
    void deletingATopicCascadesToItsBranchesAndMessages() throws Exception {
        insertTopic("T");
        insertRootBranch("B", "T");
        insertUserMessage("MU", "B", 0, "hello");

        exec("DELETE FROM topics WHERE id = 'T'");

        assertEquals(0, countRows("branches"), "branches must cascade with their topic");
        assertEquals(0, countRows("messages"), "messages must cascade with their branch");
    }

    @Test
    void oneRootBranchPerTopicWhileChildrenRemainInsertable() throws Exception {
        insertTopic("T");
        insertRootBranch("B", "T");
        insertAiMessage("MA", "B", 1);
        insertRevision("R", "MA");
        insertUserMessage("MU", "B", 0, "q");

        assertThrows(SQLException.class, () -> insertRootBranch("B2", "T"),
                "a second root branch in one topic must be rejected");

        exec("INSERT INTO branches(id, topic_id, parent_id, source_message_id, source_revision_id,"
                + " created_at, updated_at) VALUES('BC', 'T', 'B', 'MA', 'R', 1, 1)");
        assertEquals(2, countRows("branches"), "a child branch with a full fork reference is fine");
    }

    @Test
    void oneActiveOperationPerBranchWhileTerminalRowsCoexist() throws Exception {
        insertTopic("T");
        insertRootBranch("B", "T");
        insertAiMessage("MA", "B", 0);
        insertOperation("O1", "B", "MA", "STREAMING_ANSWER");

        assertThrows(SQLException.class,
                () -> insertOperation("O2", "B", "MA", "STREAMING_ANSWER"),
                "two in-flight operations on one branch must be rejected");

        insertOperation("O3", "B", "MA", "STABLE");
        assertEquals(2, countRows("generation_operations"),
                "a terminal row may coexist with the one active row");
    }

    @Test
    void quoteInvariantRejectsAiQuoteAndEmptyUserQuoteButAcceptsANonEmptyQuote() throws Exception {
        insertTopic("T");
        insertRootBranch("B", "T");

        assertThrows(SQLException.class,
                () -> exec("INSERT INTO messages(id, branch_id, ordinal, role, quoted_text,"
                        + " created_at) VALUES('MQ', 'B', 0, 'AI', 'quoted', 1)"),
                "an AI row may never carry a quote");
        assertThrows(SQLException.class,
                () -> exec("INSERT INTO messages(id, branch_id, ordinal, role, user_content,"
                        + " quoted_text, created_at) VALUES('MQ', 'B', 0, 'USER', 'body', '', 1)"),
                "an empty quoted_text is never stored");
        exec("INSERT INTO messages(id, branch_id, ordinal, role, user_content, quoted_text,"
                + " created_at) VALUES('MQ', 'B', 0, 'USER', 'body', 'quoted', 1)");
        assertEquals(1, countRows("messages"), "a non-empty quote on a USER row is legal");
    }

    @Test
    void quoteInvariantAlsoRejectsAQuoteAddedToAnAiRowByUpdate() throws Exception {
        insertTopic("T");
        insertRootBranch("B", "T");
        insertAiMessage("MA", "B", 0);

        assertThrows(SQLException.class,
                () -> exec("UPDATE messages SET quoted_text = 'late' WHERE id = 'MA'"),
                "the invariant must fire on UPDATE, not only on INSERT");
    }

    @Test
    void answerRevisionsAreImmutableAndDeleteOnlyWithTheirMessage() throws Exception {
        insertTopic("T");
        insertRootBranch("B", "T");
        insertAiMessage("MA", "B", 0);
        insertRevision("R", "MA");

        assertThrows(SQLException.class,
                () -> exec("UPDATE answer_revisions SET answer_content = 'changed' WHERE id = 'R'"),
                "a stable revision must refuse UPDATE");
        assertThrows(SQLException.class,
                () -> exec("DELETE FROM answer_revisions WHERE id = 'R'"),
                "a revision must not be deleted on its own while its message exists");

        exec("DELETE FROM messages WHERE id = 'MA'");
        assertEquals(0, countRows("answer_revisions"),
                "deleting the owning message must cascade the revision away");
    }

    @Test
    void contextSnapshotsAreImmutableAndDeleteOnlyWithTheirBranch() throws Exception {
        insertTopic("T");
        insertRootBranch("B", "T");
        insertSnapshot("S", "B");

        assertThrows(SQLException.class,
                () -> exec("UPDATE context_snapshots SET digest = 'x' WHERE id = 'S'"),
                "a birth snapshot must refuse UPDATE");
        assertThrows(SQLException.class,
                () -> exec("DELETE FROM context_snapshots WHERE id = 'S'"),
                "a snapshot must not be deleted on its own while its branch exists");

        exec("DELETE FROM branches WHERE id = 'B'");
        assertEquals(0, countRows("context_snapshots"),
                "deleting the owning branch must cascade the snapshot away");
    }

    @Test
    void terminalGenerationOperationRefusesUpdate() throws Exception {
        insertTopic("T");
        insertRootBranch("B", "T");
        insertAiMessage("MA", "B", 0);
        insertOperation("O1", "B", "MA", "STABLE");

        assertThrows(SQLException.class,
                () -> exec("UPDATE generation_operations SET state = 'FAILED' WHERE id = 'O1'"),
                "a terminal operation is final");
    }

    @Test
    void userMessageContentRefusesUpdate() throws Exception {
        insertTopic("T");
        insertRootBranch("B", "T");
        insertUserMessage("MU", "B", 0, "original");

        assertThrows(SQLException.class,
                () -> exec("UPDATE messages SET user_content = 'edited' WHERE id = 'MU'"),
                "a user message body is immutable");
    }

    @Test
    void annotationContainerCheckRejectsMismatchedShapes() throws Exception {
        insertTopic("T");
        insertRootBranch("B", "T");
        insertAiMessage("MA", "B", 0);

        assertThrows(SQLException.class,
                () -> insertAnnotation("A1", "MA", "PROSE", 0, 0, -1, -1, "HIGHLIGHT", ""),
                "a PROSE row must not carry a table_index");
        assertThrows(SQLException.class,
                () -> insertAnnotation("A2", "MA", "CELL", 0, 0, 0, 0, "HIGHLIGHT", ""),
                "a CELL row must not carry a prose_index");
        assertThrows(SQLException.class,
                () -> insertAnnotation("A3", "MA", "PROSE", 0, -1, -1, -1, "HIGHLIGHT", "text"),
                "a highlight's body is empty by DDL");
    }

    @Test
    void legalAnnotationShapesAreAccepted() throws Exception {
        insertTopic("T");
        insertRootBranch("B", "T");
        insertAiMessage("MA", "B", 0);

        insertAnnotation("A1", "MA", "PROSE", 0, -1, -1, -1, "HIGHLIGHT", "");
        insertAnnotation("A2", "MA", "CELL", -1, 0, 0, 0, "NOTE", "note text");
        assertEquals(2, countRows("message_annotations"));
    }

    @Test
    void attachmentParseStateCheckRejectsUnknownState() throws Exception {
        assertThrows(SQLException.class,
                () -> insertAttachment("AT", "hash", "bogus"),
                "parse_state only admits pending/parsed/parse_failed");
        insertAttachment("AT", "hash", "pending");
        assertEquals(1, countRows("attachments"));
    }

    @Test
    void modelUniqueIndexIsCaseExact() throws Exception {
        insertModel("m1", "WireId");
        assertThrows(SQLException.class, () -> insertModel("m2", "WireId"),
                "the same provider/model pair must dedup exactly");
        insertModel("m3", "wireid");
        assertEquals(3, countRows("models"),
                "a case-only difference is a different model id under BINARY collation");
    }

    @Test
    void messageRoleAndContentChecksRejectInvalidRows() throws Exception {
        insertTopic("T");
        insertRootBranch("B", "T");

        assertThrows(SQLException.class,
                () -> exec("INSERT INTO messages(id, branch_id, ordinal, role, user_content,"
                        + " created_at) VALUES('M1', 'B', 0, 'AI', 'not allowed', 1)"),
                "an AI row must not carry user content");
        assertThrows(SQLException.class,
                () -> exec("INSERT INTO messages(id, branch_id, ordinal, role, user_content,"
                        + " created_at) VALUES('M2', 'B', 0, 'USER', '', 1)"),
                "a USER row must carry non-empty content");
    }

    // ------------------------------------------------------------------
    // Insert helpers — one statement each, so a failing case names its own reason.
    // ------------------------------------------------------------------

    private void insertTopic(String id) throws SQLException {
        exec("INSERT INTO topics(id, created_at, updated_at) VALUES('" + id + "', 1, 1)");
    }

    private void insertRootBranch(String id, String topicId) throws SQLException {
        exec("INSERT INTO branches(id, topic_id, created_at, updated_at) VALUES('" + id + "', '"
                + topicId + "', 1, 1)");
    }

    private void insertUserMessage(String id, String branchId, int ordinal, String content)
            throws SQLException {
        exec("INSERT INTO messages(id, branch_id, ordinal, role, user_content, created_at) VALUES('"
                + id + "', '" + branchId + "', " + ordinal + ", 'USER', '" + content + "', 1)");
    }

    private void insertAiMessage(String id, String branchId, int ordinal) throws SQLException {
        exec("INSERT INTO messages(id, branch_id, ordinal, role, created_at) VALUES('" + id + "', '"
                + branchId + "', " + ordinal + ", 'AI', 1)");
    }

    private void insertRevision(String id, String messageId) throws SQLException {
        exec("INSERT INTO answer_revisions(id, message_id, revision_no, answer_content, model,"
                + " thinking_enabled, prompt_policy_version, created_at) VALUES('" + id + "', '"
                + messageId + "', 1, 'answer', 'deepseek-flash', 0, 'p/1', 1)");
    }

    private void insertOperation(String id, String branchId, String messageId, String state)
            throws SQLException {
        exec("INSERT INTO generation_operations(id, branch_id, message_id, command_id,"
                + " prompt_purpose, model, thinking_enabled, prompt_policy_version, state,"
                + " created_at, updated_at) VALUES('" + id + "', '" + branchId + "', '" + messageId
                + "', 'cmd', 'TITLE', 'deepseek-flash', 0, 'p/1', '" + state + "', 1, 1)");
    }

    private void insertSnapshot(String id, String branchId) throws SQLException {
        exec("INSERT INTO context_snapshots(id, branch_id, schema_version, digest, payload,"
                + " created_at) VALUES('" + id + "', '" + branchId + "', 1, 'd', '{}', 1)");
    }

    private void insertAttachment(String id, String hash, String parseState) throws SQLException {
        exec("INSERT INTO attachments(id, content_hash, display_name, mime_type, size_bytes,"
                + " parse_state, created_at, updated_at) VALUES('" + id + "', '" + hash
                + "', 'f', 'text/plain', 0, '" + parseState + "', 1, 1)");
    }

    private void insertModel(String id, String modelId) throws SQLException {
        exec("INSERT INTO models(id, provider_id, model_id, display_name, created_at) VALUES('" + id
                + "', '" + SEEDED_PROVIDER_ID + "', '" + modelId + "', '" + modelId + "', 1)");
    }

    private void insertAnnotation(String id, String messageId, String container, int proseIndex,
            int tableIndex, int rowIndex, int colIndex, String kind, String body)
            throws SQLException {
        exec("INSERT INTO message_annotations(id, message_id, container, prose_index, table_index,"
                + " row_index, col_index, start_offset, end_offset, kind, content_digest, body,"
                + " created_at, updated_at) VALUES('" + id + "', '" + messageId + "', '" + container
                + "', " + proseIndex + ", " + tableIndex + ", " + rowIndex + ", " + colIndex
                + ", 0, 1, '" + kind + "', 'digest', '" + body + "', 1, 1)");
    }

    private void exec(String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate(sql);
        }
    }

    private int countRows(String table) throws SQLException {
        return DesktopSchemaFreshInstallTest.count(connection, table);
    }
}
