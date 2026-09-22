package com.treelearn.app.search;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.treelearn.app.ui.UiThread;
import com.treelearn.core.domain.ReadingAnchor;
import com.treelearn.core.domain.SearchHit;
import com.treelearn.core.domain.SearchQuery;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executor;

import org.junit.Test;

/**
 * T-015 acceptance: the search screen's decisions, with no Android type in sight.
 *
 * <p>Three of them are requirements rather than rendering choices. A blank query never reaches the
 * store — the pattern for "nothing typed" would match every row in the tree. A failure keeps the
 * draft, so the learner can fix a typo instead of retyping. And a hit that no longer exists is
 * dropped rather than opened: the result list is a snapshot, and a branch can be deleted between
 * the query and the tap.
 */
public class SearchPresenterTest {

    private static final String TOPIC = "11111111-1111-4111-8111-111111111111";
    private static final String BRANCH = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa";
    private static final String MESSAGE = "cccccccc-cccc-4ccc-8ccc-cccccccccccc";
    private static final String REVISION = "dddddddd-dddd-4ddd-8ddd-dddddddddddd";

    @Test
    public void aBlankQueryRendersIdleAndNeverTouchesTheStore() {
        Fixture fixture = new Fixture();

        fixture.presenter.onQueryChanged("   \t ");

        assertEquals(SearchViewState.Phase.IDLE, fixture.presenter.state().phase);
        assertTrue(fixture.presenter.state().hits.isEmpty());
        assertEquals(0, fixture.repository.calls);
    }

    @Test
    public void aQueryIsAnsweredOnTheUiThreadAndRenderedWithItsDraft() {
        Fixture fixture = new Fixture();
        fixture.repository.hits = Collections.singletonList(answerHit());

        fixture.presenter.onQueryChanged("  树  ");

        assertEquals(SearchViewState.Phase.RESULTS, fixture.presenter.state().phase);
        assertEquals("  树  ", fixture.presenter.state().draft);
        assertEquals(1, fixture.presenter.state().hits.size());
        assertEquals(1, fixture.repository.calls);
        assertEquals("树", fixture.repository.lastQuery.normalized());
        assertTrue("the state must have been handed to the view on the UI thread",
                fixture.ui.posted > 0);
    }

    @Test
    public void aStoreFailureRendersTheErrorPhaseAndKeepsTheDraft() {
        Fixture fixture = new Fixture();
        fixture.repository.throwsInstead = true;

        fixture.presenter.onQueryChanged("搜索");

        assertEquals(SearchViewState.Phase.ERROR, fixture.presenter.state().phase);
        assertEquals("搜索", fixture.presenter.state().draft);
    }

    @Test
    public void openingAnAnswerHitOpensThatExactMessageAndRevision() {
        Fixture fixture = new Fixture();
        SearchHit hit = answerHit();

        fixture.presenter.onHitOpened(hit);

        assertEquals(BRANCH, fixture.sink.openedBranch);
        assertEquals(MESSAGE, fixture.sink.openedAnchor.messageId);
        assertEquals(REVISION, fixture.sink.openedAnchor.revisionId);
        assertEquals(1, fixture.sink.opens);
    }

    @Test
    public void openingATitleHitOpensTheBranchWithoutAPosition() {
        Fixture fixture = new Fixture();

        fixture.presenter.onHitOpened(SearchHit.of(SearchHit.Field.TITLE, TOPIC, BRANCH, null, -1,
                null, 0, "synthetic title"));

        assertEquals(BRANCH, fixture.sink.openedBranch);
        assertEquals(null, fixture.sink.openedAnchor);
    }

    @Test
    public void aHitWhoseTargetDisappearedIsDroppedInsteadOfOpened() {
        Fixture fixture = new Fixture();
        fixture.repository.hits = new ArrayList<SearchHit>(Arrays.asList(answerHit(), answerHit()));
        fixture.presenter.onQueryChanged("树");
        assertEquals(2, fixture.presenter.state().hits.size());
        fixture.repository.reachable = false;

        fixture.presenter.onHitOpened(fixture.presenter.state().hits.get(0));

        assertEquals("nothing may be opened once the target is gone", 0, fixture.sink.opens);
        assertEquals(1, fixture.presenter.state().hits.size());
        assertEquals(1, fixture.repository.reachabilityChecks);
    }

    @Test
    public void aResultForAnAbandonedQueryNeverReplacesTheNewerQuery() {
        Fixture fixture = new Fixture();
        fixture.background.pause();
        fixture.repository.hits = Collections.singletonList(answerHit());

        fixture.presenter.onQueryChanged("第一");
        fixture.presenter.onQueryChanged("第二");
        fixture.background.runAll();

        assertEquals("第二", fixture.presenter.state().draft);
        assertEquals(SearchViewState.Phase.RESULTS, fixture.presenter.state().phase);
        assertEquals(2, fixture.repository.calls);
        assertEquals("第二", fixture.repository.lastQuery.normalized());
    }

    @Test
    public void resumingKeepsTheDraftAndAnswersItAgain() {
        Fixture fixture = new Fixture();
        fixture.presenter.onQueryChanged("树");
        assertEquals(1, fixture.repository.calls);

        fixture.presenter.onResumed();

        assertEquals(2, fixture.repository.calls);
        assertEquals("树", fixture.presenter.state().draft);
    }

    private static SearchHit answerHit() {
        return SearchHit.of(SearchHit.Field.ANSWER, TOPIC, BRANCH, MESSAGE, 3, REVISION, 2,
                "synthetic answer text");
    }

    private static final class Fixture {

        final RecordingSink sink = new RecordingSink();
        final InlineUi ui = new InlineUi();
        final PausingExecutor background = new PausingExecutor();
        final FakeRepository repository = new FakeRepository();
        final SearchPresenter presenter;

        Fixture() {
            this.presenter = new SearchPresenter(sink, ui, background, repository);
        }
    }

    private static final class InlineUi implements UiThread {

        int posted;

        @Override
        public void post(Runnable action) {
            posted++;
            action.run();
        }
    }

    /** Background work is queued unless the test explicitly asks it to run. */
    private static final class PausingExecutor implements Executor {

        private final List<Runnable> queued = new ArrayList<Runnable>();
        private boolean paused;

        void pause() {
            paused = true;
        }

        void runAll() {
            paused = false;
            while (!queued.isEmpty()) {
                queued.remove(0).run();
            }
        }

        @Override
        public void execute(Runnable command) {
            if (paused) {
                queued.add(command);
            } else {
                command.run();
            }
        }
    }

    private static final class FakeRepository implements SearchRepository {

        List<SearchHit> hits = Collections.emptyList();
        boolean throwsInstead;
        boolean reachable = true;
        int calls;
        int reachabilityChecks;
        SearchQuery lastQuery;

        @Override
        public List<SearchHit> search(SearchQuery query) {
            calls++;
            lastQuery = query;
            if (throwsInstead) {
                throw new IllegalStateException("the store could not be read");
            }
            return hits;
        }

        @Override
        public boolean isStillReachable(SearchHit hit) {
            reachabilityChecks++;
            return reachable;
        }
    }

    private static final class RecordingSink implements SearchPresenter.Sink {

        int opens;
        String openedBranch;
        ReadingAnchor openedAnchor;

        @Override
        public void render(SearchViewState state) {
        }

        @Override
        public void openAnchor(String topicId, String branchId, ReadingAnchor anchor) {
            opens++;
            openedBranch = branchId;
            openedAnchor = anchor;
        }
    }
}