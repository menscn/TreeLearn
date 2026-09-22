package com.treelearn.data.db.dao;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.treelearn.core.domain.SearchQuery;

import org.junit.Test;

/**
 * T-015 acceptance, JVM half: the search statement is built here, once, and it binds the learner's
 * text as a parameter (technical design 6.3).
 *
 * <p>This suite deliberately does not execute SQL. Real execution belongs to
 * {@code SearchUnreadReadingInstrumentedTest} on Android SQLite (rules/sql.md); what can be proven
 * without a device is the <em>shape</em> of the statement, and that shape is where the two defects
 * this task must not ship would live: an inlined pattern (a stored {@code %} turning into "match
 * everything", or a quoted {@code '} breaking the statement) and a stale revision reached without
 * the message or fork that still references it.
 */
public class SearchDaoStatementTest {

    @Test
    public void everySearchTermIsBoundAndNoneIsInlined() {
        String statement = SearchDao.statement();

        assertEquals(7, countOf(statement, "LIKE ? ESCAPE"));
        assertEquals(7, SearchDao.arguments(SearchQuery.of("需求")).length);
        assertFalse("the statement must never contain a query's text",
                statement.contains("需求"));
        assertFalse(statement.contains("'%"));
    }

    @Test
    public void theEscapeCharacterIsDeclaredOnEveryComparison() {
        // Without ESCAPE, a backslash in a LIKE is a literal backslash on some engines and an escape
        // on others: the same statement would mean two different things.
        assertEquals(7, countOf(SearchDao.statement(), "ESCAPE '\\'"));
    }

    @Test
    public void theSamePatternIsBoundForEveryField() {
        SearchQuery query = SearchQuery.of(" 100% ");

        String[] arguments = SearchDao.arguments(query);

        for (String argument : arguments) {
            assertEquals(query.likePattern(), argument);
        }
        assertEquals("%100\\%%", arguments[0]);
    }

    @Test
    public void aBlankQueryNeverBecomesAWildcardScan() {
        assertThrows(IllegalStateException.class,
                () -> SearchDao.arguments(SearchQuery.of("   ")));
    }

    @Test
    public void answerAndReasoningAreReadOnlyThroughTheRevisionThePositionDisplays() {
        // The design forbids returning an expired revision that no branch references: the same
        // logical answer would match twice. A stale revision stays reachable through the fork that
        // pins it (the ANCHOR arm) and through nothing else.
        assertEquals(2, countOf(SearchDao.statement(), "answer_revisions ar"));
        assertEquals(2, countOf(SearchDao.statement(),
                "answer_revisions ar ON ar.id = m.active_revision_id"));
        assertTrue("a fork's own revision must stay reachable through its anchor",
                SearchDao.statement().contains("b.source_revision_id"));
        assertFalse("failed-attempt columns must not be qualified twice",
                SearchDao.statement().contains("go.go."));
    }

    @Test
    public void archivedBranchesAreNotSearchable() {
        assertEquals(7, countOf(SearchDao.statement(), "b.archived_at IS NULL"));
    }

    @Test
    public void theQuestionArmReadsUserContentAndLabelsIt() {
        String statement = SearchDao.statement();

        assertTrue(statement.contains("'QUESTION' AS matched_field"));
        assertTrue(statement.contains("m.role = 'USER'"));
        assertTrue(statement.contains("m.user_content LIKE ? ESCAPE"));
    }

    @Test
    public void everyGoverningTableIsNamedThroughTheSchemaConstants() {
        // No literal column name may appear here: a renamed column must break compilation, not
        // silently stop matching.
        String statement = SearchDao.statement();
        assertTrue(statement.contains("branches"));
        assertFalse("column literals belong to TreeLearnSchema",
                statement.contains("reading_anchor_message_id"));
    }

    private static int countOf(String haystack, String needle) {
        int count = 0;
        int index = haystack.indexOf(needle);
        while (index >= 0) {
            count++;
            index = haystack.indexOf(needle, index + needle.length());
        }
        return count;
    }
}
