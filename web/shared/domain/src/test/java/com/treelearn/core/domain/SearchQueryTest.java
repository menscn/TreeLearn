package com.treelearn.core.domain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * T-015 acceptance: the search query is the single place a LIKE pattern is built (technical design
 * 6.3, 11).
 *
 * <p>Chinese substring search cannot rely on SQLite's default tokenizer, so the first release
 * searches with a parameterised LIKE. That makes this class the only thing standing between a
 * learner's text and the pattern the store executes: every {@code %} and {@code _} in the query must
 * arrive as a literal, and a query made only of wildcards must never become "match every row".
 *
 * <p>Normalisation is folding, not rewriting: case is folded, runs of whitespace collapse to one
 * space, and CJK text is passed through untouched (it has no case, and any "translation" here would
 * be the store inventing characters the learner did not type).
 */
public class SearchQueryTest {

    @Test
    public void normalisationTrimsCollapsesWhitespaceAndFoldsCase() {
        SearchQuery query = SearchQuery.of("  Hello   World  ");

        assertEquals("hello world", query.normalized());
        assertEquals("%hello world%", query.likePattern());
        assertFalse(query.isEmpty());
    }

    @Test
    public void chineseTextIsKeptVerbatim() {
        SearchQuery query = SearchQuery.of("  搜索   关键词  ");

        assertEquals("搜索 关键词", query.normalized());
        assertEquals("%搜索 关键词%", query.likePattern());
    }

    @Test
    public void percentAndUnderscoreAreEscapedAsLiterals() {
        assertEquals("%100\\%%", SearchQuery.of("100%").likePattern());
        assertEquals("%a\\_b%", SearchQuery.of("a_b").likePattern());
        assertEquals("%\\%%", SearchQuery.of("%").likePattern());
        assertEquals("%\\_%", SearchQuery.of("_").likePattern());
    }

    @Test
    public void theEscapeCharacterItselfIsEscaped() {
        assertEquals("%c\\\\d%", SearchQuery.of("c\\d").likePattern());
        assertEquals("\\\\", SearchQuery.escapePattern("\\"));
    }

    @Test
    public void aBlankQueryIsEmptyAndNeverBecomesAWildcardScan() {
        SearchQuery blank = SearchQuery.of("   \t  ");

        assertTrue(blank.isEmpty());
        // '%%' would match every row in the tree. The pattern is refused instead of being built, so
        // a caller that forgot isEmpty() gets a loud failure rather than the learner's whole store.
        assertThrows(IllegalStateException.class, blank::likePattern);
    }

    @Test
    public void aNullOrNegativeLengthQueryIsACallerBug() {
        assertThrows(IllegalArgumentException.class, () -> SearchQuery.of(null));
    }

    @Test
    public void theSameTextAlwaysProducesTheSamePattern() {
        // Determinism is what makes "results are stable" a property of the query and not of luck:
        // two spellings of the same question differ only in their surrounding whitespace.
        assertEquals(SearchQuery.of(" TreeLearn ").likePattern(),
                SearchQuery.of("treelearn").likePattern());
    }
}