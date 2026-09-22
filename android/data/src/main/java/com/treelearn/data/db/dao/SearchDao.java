package com.treelearn.data.db.dao;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.treelearn.core.domain.GenerationState;
import com.treelearn.core.domain.MessageRole;
import com.treelearn.core.domain.SearchHit;
import com.treelearn.core.domain.SearchQuery;
import com.treelearn.data.db.TreeLearnSchema;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * The search statement, and the only SQL in this build that reads more than one table at a time
 * (technical design 6.3).
 *
 * <p>Seven {@code UNION ALL} arms, one per searchable field: the branch title, the learner's
 * question, the quote a fork was
 * anchored on, the answer and the reasoning of the revision each position <em>displays</em>, and the
 * partial content of a position's current failed attempt. Everything is bound as a parameter — the
 * pattern, never the statement — and every comparison declares {@code ESCAPE} explicitly, so a stored
 * {@code %} is a character rather than "anything".
 *
 * <p><strong>Why a stale revision cannot be returned by accident.</strong> The answer and reasoning
 * arms join through {@code messages.active_revision_id}, so a revision no position displays is
 * unreachable by construction rather than by a filter somebody could forget. That is the rule in
 * §6.3: the same logical answer must not match twice. A revision that is still reachable — because a
 * child branch forked from it and stores it as its source — is found through that branch's anchor
 * arm, which is also where opening it takes the learner.
 *
 * <p><strong>Archived branches are not searchable.</strong> Their text is still stored, but the tree
 * does not show them, so a hit would be a result that cannot be opened.
 *
 * <p>No index helps a leading-wildcard {@code LIKE}; the design accepts a full scan at personal
 * scale, and the repository above this class is the seam where an n-gram index would replace it
 * without the screen or the use cases noticing.
 *
 * <p>The union's column names come from the first arm, and two tables both call their key {@code id},
 * so the branch, message and revision keys are aliased ({@code branch_id} / {@code message_id} /
 * {@code revision_id}) rather than left to collide: an unaliased union would silently make
 * {@code Rows.string(cursor, "id")} return the branch key where the reader wanted the message key.
 */
public final class SearchDao {

    /** Statement-local labels: they name columns of this statement, not of any table. */
    private static final String MATCHED_FIELD = "matched_field";

    private static final String SNIPPET = "snippet";

    private static final String BRANCH_ID = "branch_id";

    private static final String MESSAGE_ID = "message_id";

    private static final String REVISION_ID = "revision_id";

    private static final String STATEMENT = buildStatement();

    /** Bound values, in the order the statement's placeholders appear. */
    private static final int ARGUMENT_COUNT = 7;

    private SearchDao() {
    }

    /** The statement, exposed so a reviewer or a test can read it without a device. */
    public static String statement() {
        return STATEMENT;
    }

    /**
     * The values to bind, one per arm.
     *
     * @throws IllegalStateException when the query is blank — {@code LIKE '%%'} would return every
     *         row in the tree, and callers are expected to have answered that case before reaching
     *         the store ({@link #search} does)
     */
    public static String[] arguments(SearchQuery query) {
        if (query == null) {
            throw new IllegalArgumentException("query must not be null");
        }
        String[] arguments = new String[ARGUMENT_COUNT];
        Arrays.fill(arguments, query.likePattern());
        return arguments;
    }

    /**
     * Returns the matching hits in {@link SearchHit#DISPLAY_ORDER}, never null.
     *
     * <p>A blank query is answered without touching the database: there is no pattern for it, and the
     * screen has already decided to show its idle state.
     *
     * <p>Failures propagate. A store that cannot be read is a state the screen shows as an error, not
     * one this class hides behind an empty list.
     */
    public static List<SearchHit> search(SQLiteDatabase database, SearchQuery query) {
        if (database == null) {
            throw new IllegalArgumentException("database must not be null");
        }
        if (query == null) {
            throw new IllegalArgumentException("query must not be null");
        }
        if (query.isEmpty()) {
            return Collections.emptyList();
        }
        List<SearchHit> hits = new ArrayList<SearchHit>();
        try (Cursor cursor = database.rawQuery(STATEMENT, arguments(query))) {
            while (cursor.moveToNext()) {
                hits.add(read(cursor));
            }
        }
        Collections.sort(hits, SearchHit.DISPLAY_ORDER);
        return Collections.unmodifiableList(hits);
    }

    private static SearchHit read(Cursor cursor) {
        String messageId = Rows.string(cursor, MESSAGE_ID);
        return SearchHit.of(
                // valueOf is the fail-loud half: an arm added to the statement without a matching
                // enum value throws here instead of arriving at the screen unlabelled.
                SearchHit.Field.valueOf(Rows.string(cursor, MATCHED_FIELD)),
                Rows.string(cursor, TreeLearnSchema.Branches.TOPIC_ID),
                Rows.string(cursor, BRANCH_ID),
                messageId,
                messageId == null ? -1 : Rows.intOf(cursor, TreeLearnSchema.Messages.ORDINAL),
                Rows.string(cursor, REVISION_ID),
                Rows.intOf(cursor, TreeLearnSchema.AnswerRevisions.REVISION_NO),
                Rows.string(cursor, SNIPPET));
    }

    private static String buildStatement() {
        final String branch = "b";
        final String message = "m";
        final String revision = "ar";
        final String operation = "go";
        final String source = "sm";

        String fromBranches = TreeLearnSchema.Branches.TABLE + " " + branch;
        String fromBranchAndMessages = fromBranches + " JOIN " + TreeLearnSchema.Messages.TABLE + " "
                + message + " ON " + col(message, TreeLearnSchema.Messages.BRANCH_ID) + " = "
                + col(branch, TreeLearnSchema.Branches.ID);
        String fromMessagesAndRevisions = fromBranchAndMessages + " JOIN "
                + TreeLearnSchema.AnswerRevisions.TABLE + " " + revision + " ON "
                + col(revision, TreeLearnSchema.AnswerRevisions.ID) + " = "
                + col(message, TreeLearnSchema.Messages.ACTIVE_REVISION_ID);
        String fromMessagesAndOperations = fromBranchAndMessages + " JOIN "
                + TreeLearnSchema.GenerationOperations.TABLE + " " + operation + " ON "
                + col(operation, TreeLearnSchema.GenerationOperations.ID) + " = "
                + col(message, TreeLearnSchema.Messages.ACTIVE_OPERATION_ID);
        String fromBranchesAndSources = fromBranches + " JOIN " + TreeLearnSchema.Messages.TABLE + " "
                + source + " ON " + col(source, TreeLearnSchema.Messages.ID) + " = "
                + col(branch, TreeLearnSchema.Branches.SOURCE_MESSAGE_ID);

        String answer = col(revision, TreeLearnSchema.AnswerRevisions.ANSWER_CONTENT);
        String reasoning = col(revision, TreeLearnSchema.AnswerRevisions.REASONING_CONTENT);
        String partialAnswer = col(operation, TreeLearnSchema.GenerationOperations.PARTIAL_ANSWER);
        String partialReasoning =
                col(operation, TreeLearnSchema.GenerationOperations.PARTIAL_REASONING);
        String title = col(branch, TreeLearnSchema.Branches.TITLE);
        String quote = col(branch, TreeLearnSchema.Branches.ANCHOR_QUOTE);

        List<String> arms = new ArrayList<String>();
        arms.add(arm(SearchHit.Field.TITLE, fromBranches, "NULL", "-1", "NULL", "0", title,
                like(title)));
        arms.add(arm(SearchHit.Field.QUESTION, fromBranchAndMessages,
                col(message, TreeLearnSchema.Messages.ID),
                col(message, TreeLearnSchema.Messages.ORDINAL), "NULL", "0",
                col(message, TreeLearnSchema.Messages.USER_CONTENT),
                col(message, TreeLearnSchema.Messages.ROLE) + " = '" + MessageRole.USER.name()
                        + "' AND " + like(col(message, TreeLearnSchema.Messages.USER_CONTENT))));
        arms.add(arm(SearchHit.Field.ANCHOR, fromBranchesAndSources,
                col(source, TreeLearnSchema.Messages.ID),
                col(source, TreeLearnSchema.Messages.ORDINAL),
                col(branch, TreeLearnSchema.Branches.SOURCE_REVISION_ID), "0", quote, like(quote)));
        arms.add(arm(SearchHit.Field.ANSWER, fromMessagesAndRevisions,
                col(message, TreeLearnSchema.Messages.ID),
                col(message, TreeLearnSchema.Messages.ORDINAL),
                col(revision, TreeLearnSchema.AnswerRevisions.ID),
                col(revision, TreeLearnSchema.AnswerRevisions.REVISION_NO), answer, like(answer)));
        arms.add(arm(SearchHit.Field.REASONING, fromMessagesAndRevisions,
                col(message, TreeLearnSchema.Messages.ID),
                col(message, TreeLearnSchema.Messages.ORDINAL),
                col(revision, TreeLearnSchema.AnswerRevisions.ID),
                col(revision, TreeLearnSchema.AnswerRevisions.REVISION_NO), reasoning,
                like(reasoning)));
        arms.add(arm(SearchHit.Field.PARTIAL_ANSWER, fromMessagesAndOperations,
                col(message, TreeLearnSchema.Messages.ID),
                col(message, TreeLearnSchema.Messages.ORDINAL), "NULL", "0", partialAnswer,
                failedAttempt(operation, partialAnswer)));
        arms.add(arm(SearchHit.Field.PARTIAL_REASONING, fromMessagesAndOperations,
                col(message, TreeLearnSchema.Messages.ID),
                col(message, TreeLearnSchema.Messages.ORDINAL), "NULL", "0", partialReasoning,
                failedAttempt(operation, partialReasoning)));

        return String.join(" UNION ALL ", arms);
    }


    private static String like(String column) {
        return column + " LIKE ? ESCAPE '\\'";
    }

    /** Partial content of an attempt that ended without a revision, so it is bound to no revision. */
    private static String failedAttempt(String operation, String partialColumn) {
        return col(operation, TreeLearnSchema.GenerationOperations.STATE) + " IN ("
                + "'" + GenerationState.FAILED.name() + "','" + GenerationState.INTERRUPTED.name()
                + "','" + GenerationState.CANCELED.name() + "') AND "
                + partialColumn + " LIKE ? ESCAPE '\\'";
    }

    /**
     * One arm: the text a hit quotes, and the predicate that decides whether it matched.
     *
     * <p>The two are separate parameters rather than one expression because the failed-attempt arms
     * need a predicate that is more than "this column matches"; passing one expression for both would
     * splice the state filter into the SELECT list, and a statement that reads
     * {@code … LIKE ? ESCAPE '\' AS snippet WHERE … LIKE ? ESCAPE '\' LIKE ? ESCAPE '\'} is not SQL.
     */
    private static String arm(SearchHit.Field field, String from, String messageId, String ordinal,
            String revisionId, String revisionNo, String snippetColumn, String predicate) {
        return "SELECT '" + field.name() + "' AS " + MATCHED_FIELD + ", "
                + col("b", TreeLearnSchema.Branches.TOPIC_ID) + ", "
                + col("b", TreeLearnSchema.Branches.ID) + " AS " + BRANCH_ID + ", "
                + messageId + " AS " + MESSAGE_ID + ", "
                + ordinal + " AS " + TreeLearnSchema.Messages.ORDINAL + ", "
                + revisionId + " AS " + REVISION_ID + ", "
                + revisionNo + " AS " + TreeLearnSchema.AnswerRevisions.REVISION_NO + ", "
                + snippetColumn + " AS " + SNIPPET
                + " FROM " + from
                + " WHERE " + col("b", TreeLearnSchema.Branches.ARCHIVED_AT) + " IS NULL AND "
                + predicate;
    }

    private static String col(String alias, String column) {
        return alias + "." + column;
    }
}
