package com.treelearn.app.search;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import com.treelearn.app.ui.UiThread;
import com.treelearn.core.domain.ReadingAnchor;
import com.treelearn.core.domain.SearchHit;
import com.treelearn.core.domain.SearchQuery;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executor;

import org.junit.Test;

/**
 * T-040 acceptance: opening a listed hit asks for exactly the branch and position that row names.
 *
 * <p>The chain asserted here is the one the learner walks - a keystroke, an answer, a tap - and the
 * point of walking it end to end is that the position handed to the shell comes from the row that was
 * on screen rather than from a second lookup. The shell's own move (open the conversation, hand the
 * anchor to the reading store) is the T-042 seam and is not re-decided here; what is decided here is
 * which branch, and where in it, that move is asked for.
 */
public final class SearchJumpTest {

    private static final String TOPIC = "11111111-1111-4111-8111-111111111111";
    private static final String BRANCH = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa";
    private static final String MESSAGE = "cccccccc-cccc-4ccc-8ccc-cccccccccccc";
    private static final String REVISION = "dddddddd-dddd-4ddd-8ddd-dddddddddddd";

    @Test
    public void oneKeystrokeIsOneStoreReadAndTheTappedRowCarriesItsOwnPosition() {
        Fixture fixture = new Fixture();
        fixture.repository.hits = Collections.singletonList(answerHit());

        fixture.presenter.onQueryChanged("tree");

        assertEquals("one keystroke is one store read", 1, fixture.repository.calls);
        assertEquals(SearchViewState.Phase.RESULTS, fixture.presenter.state().phase);

        fixture.presenter.onHitOpened(fixture.presenter.state().hits.get(0));

        assertEquals(TOPIC, fixture.sink.openedTopic);
        assertEquals(BRANCH, fixture.sink.openedBranch);
        assertNotNull("an answer row carries the position it was found at", fixture.sink.openedAnchor);
        assertEquals(MESSAGE, fixture.sink.openedAnchor.messageId);
        assertEquals(REVISION, fixture.sink.openedAnchor.revisionId);
        assertEquals("exactly one move was asked for", 1, fixture.sink.opens);
    }

    @Test
    public void aTitleRowOpensItsBranchWithoutStakingOutAPosition() {
        Fixture fixture = new Fixture();
        fixture.repository.hits = Collections.singletonList(SearchHit.of(SearchHit.Field.TITLE, TOPIC,
                BRANCH, null, -1, null, 0, "synthetic title"));

        fixture.presenter.onQueryChanged("tree");
        fixture.presenter.onHitOpened(fixture.presenter.state().hits.get(0));

        assertEquals(BRANCH, fixture.sink.openedBranch);
        assertNull("a title match has no position of its own to restore", fixture.sink.openedAnchor);
    }

    private static SearchHit answerHit() {
        return SearchHit.of(SearchHit.Field.ANSWER, TOPIC, BRANCH, MESSAGE, 3, REVISION, 2,
                "synthetic answer text");
    }

    private static final class Fixture {

        final RecordingSink sink = new RecordingSink();
        final FakeRepository repository = new FakeRepository();
        final SearchPresenter presenter = new SearchPresenter(sink, new InlineUi(),
                new InlineExecutor(), repository);
    }

    private static final class RecordingSink implements SearchPresenter.Sink {

        int opens;
        String openedTopic;
        String openedBranch;
        ReadingAnchor openedAnchor;

        @Override
        public void render(SearchViewState state) {
        }

        @Override
        public void openAnchor(String topicId, String branchId, ReadingAnchor anchor) {
            opens++;
            openedTopic = topicId;
            openedBranch = branchId;
            openedAnchor = anchor;
        }
    }

    private static final class FakeRepository implements SearchRepository {

        List<SearchHit> hits = Collections.emptyList();
        int calls;

        @Override
        public List<SearchHit> search(SearchQuery query) {
            calls++;
            return hits;
        }

        @Override
        public boolean isStillReachable(SearchHit hit) {
            return true;
        }
    }

    private static final class InlineUi implements UiThread {

        @Override
        public void post(Runnable action) {
            action.run();
        }
    }

    private static final class InlineExecutor implements Executor {

        @Override
        public void execute(Runnable command) {
            command.run();
        }
    }
}
