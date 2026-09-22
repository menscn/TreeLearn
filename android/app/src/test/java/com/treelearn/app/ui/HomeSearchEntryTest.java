package com.treelearn.app.ui;

import static org.junit.Assert.assertEquals;

import org.junit.Before;
import org.junit.Test;

/**
 * T-040 acceptance: the home page's search entry is reachable before any topic exists.
 *
 * <p>Unlike the archive entry, which browses one topic's tree and therefore needs a topic to have
 * something to browse, search spans every branch. An empty home is exactly the case where a learner
 * has something to look for and nothing to browse, so gating the entry on the topic list would be
 * the same dead button under a new name. The third case is the other half of that decision: adding
 * the search entry must not quietly widen the archive entry's own guard.
 */
public final class HomeSearchEntryTest {

    private TestDoubles.RecordingHomeSink sink;
    private HomePresenter presenter;

    @Before
    public void setUp() {
        sink = new TestDoubles.RecordingHomeSink();
        presenter = new HomePresenter(sink, new TestDoubles.InlineUi(),
                new TestDoubles.InlineExecutor(), new TestDoubles.FakeKeyVault(true),
                new TestDoubles.FakeHomeData(), new TestDoubles.FakeSubmitter(),
                new TestDoubles.FakeSettingsStore());
    }

    @Test
    public void tappingSearchAsksTheShellToOpenTheSearchPage() {
        presenter.start();

        presenter.onSearchPressed();

        assertEquals(1, sink.searches);
    }

    @Test
    public void anEmptyHomeStillOpensSearch() {
        presenter.start();

        presenter.onSearchPressed();

        assertEquals("search spans every branch: an empty home must still open it", 1,
                sink.searches);
    }

    @Test
    public void theArchiveEntryStillNeedsATopic() {
        presenter.start();

        presenter.onArchivedPressed();

        assertEquals("the search entry must not have widened the archive entry", 0,
                sink.archivedRequested);
    }
}
