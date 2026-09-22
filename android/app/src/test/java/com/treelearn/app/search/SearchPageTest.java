package com.treelearn.app.search;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.treelearn.core.domain.SearchHit;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;

/**
 * T-040 acceptance: what the search page is handed to draw.
 *
 * <p>The page object exists so the four states a learner actually sees - nothing typed, a query in
 * flight, an answer, a store that could not be read - are decided in one place that a JVM test can
 * reach, and so a result list cannot be quietly filtered or reordered on its way to the screen. The
 * order the store returned is the order the learner reads; that is the whole of the "the screen does
 * not re-decide the search" rule, and it is asserted here rather than hoped for.
 *
 * <p>A hit carries no branch title: the search statement answers with ids and a snippet only. Naming
 * the branch a hit came from is therefore a second question, asked of whatever owns branch rows -
 * never answered by the search store, and never asked twice for the same branch.
 */
public final class SearchPageTest {

    private static final String TOPIC = "11111111-1111-4111-8111-111111111111";
    private static final String BRANCH_A = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa";
    private static final String BRANCH_B = "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb";
    private static final String MESSAGE = "cccccccc-cccc-4ccc-8ccc-cccccccccccc";
    private static final String REVISION = "dddddddd-dddd-4ddd-8ddd-dddddddddddd";

    private final CountingTitles titles = new CountingTitles();

    @Test
    public void nothingTypedShowsTheGuideAndAsksForNothing() {
        SearchPage page = SearchPage.of(SearchViewState.initial(), titles);

        assertEquals(SearchPage.Pane.GUIDE, page.pane);
        assertTrue(page.rows.isEmpty());
        assertEquals("an idle page must not ask for a single title", 0, titles.lookups);
    }

    @Test
    public void aQueryInFlightShowsTheSearchingPane() {
        SearchViewState state = SearchViewState.initial().withDraft("tree")
                .withPhase(SearchViewState.Phase.SEARCHING);

        assertEquals(SearchPage.Pane.SEARCHING, SearchPage.of(state, titles).pane);
    }

    @Test
    public void anEmptyResultIsAStateOfItsOwnRatherThanAnEmptyList() {
        SearchViewState state = SearchViewState.initial().withDraft("nothing")
                .withPhase(SearchViewState.Phase.RESULTS);

        SearchPage page = SearchPage.of(state, titles);

        assertEquals(SearchPage.Pane.EMPTY, page.pane);
        assertTrue(page.rows.isEmpty());
    }

    @Test
    public void anUnreadableStoreShowsTheErrorPane() {
        SearchViewState state = SearchViewState.initial().withDraft("tree")
                .withPhase(SearchViewState.Phase.ERROR);

        assertEquals(SearchPage.Pane.ERROR, SearchPage.of(state, titles).pane);
    }

    @Test
    public void resultsAreListedInTheOrderTheStoreReturnedThem() {
        SearchHit first = answerHit(BRANCH_A);
        SearchHit second = titleHit(BRANCH_B);
        SearchHit third = answerHit(BRANCH_A);

        SearchPage page = SearchPage.of(results(Arrays.asList(first, second, third)), titles);

        assertEquals(SearchPage.Pane.LIST, page.pane);
        assertEquals(3, page.rows.size());
        assertSame("the page must not reorder or drop what the store returned",
                first, page.rows.get(0).hit);
        assertSame(second, page.rows.get(1).hit);
        assertSame(third, page.rows.get(2).hit);
    }

    @Test
    public void everyRowNamesTheBranchItCameFrom() {
        SearchPage page = SearchPage.of(results(Arrays.asList(answerHit(BRANCH_A), titleHit(BRANCH_B))),
                titles);

        assertEquals("Branch A", page.rows.get(0).branchTitle);
        assertEquals("Branch B", page.rows.get(1).branchTitle);
        assertEquals("two branches, two questions", 2, titles.lookups);
    }

    @Test
    public void twoHitsInOneBranchResolveTheTitleOnce() {
        SearchPage page = SearchPage.of(results(Arrays.asList(answerHit(BRANCH_A), answerHit(BRANCH_A))),
                titles);

        assertEquals(2, page.rows.size());
        assertEquals("a repeated branch is one question to the store", 1, titles.lookups);
    }

    @Test
    public void aHitWhoseBranchCannotBeNamedIsStillListed() {
        SearchHit hit = answerHit("99999999-9999-4999-8999-999999999999");

        SearchPage page = SearchPage.of(results(Collections.singletonList(hit)), titles);

        assertEquals("a row without a name still points somewhere", 1, page.rows.size());
        assertSame(hit, page.rows.get(0).hit);
        assertEquals("and its title is empty rather than null", "", page.rows.get(0).branchTitle);
    }

    private static SearchViewState results(List<SearchHit> hits) {
        return SearchViewState.initial().withDraft("tree").withHits(hits)
                .withPhase(SearchViewState.Phase.RESULTS);
    }

    private static SearchHit answerHit(String branchId) {
        return SearchHit.of(SearchHit.Field.ANSWER, TOPIC, branchId, MESSAGE, 3, REVISION, 2,
                "synthetic answer text");
    }

    private static SearchHit titleHit(String branchId) {
        return SearchHit.of(SearchHit.Field.TITLE, TOPIC, branchId, null, -1, null, 0,
                "synthetic title");
    }

    /** Two known branches and a count, so "the same branch is not asked twice" can be asserted. */
    private static final class CountingTitles implements BranchTitles {

        int lookups;

        private final Map<String, String> byId = new HashMap<String, String>();

        CountingTitles() {
            byId.put(BRANCH_A, "Branch A");
            byId.put(BRANCH_B, "Branch B");
        }

        @Override
        public String titleOf(String branchId) {
            lookups++;
            return byId.get(branchId);
        }
    }
}
