package com.treelearn.app.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.treelearn.ai.gateway.AiGatewayError;
import com.treelearn.core.domain.GenerationModel;

import org.junit.Before;
import org.junit.Test;

import java.util.Collections;
import java.util.Arrays;

/**
 * The home presenter's behaviour, asserted on the states a screen would draw.
 *
 * <p>The acceptance criteria this covers: the four layout phases, a failed recommendation that does
 * not block the page, the key guard, and the draft surviving every refusal while being cleared on the
 * one accepted path (PRD 6.2.14, 6.2.16, 6.2.17).
 */
public final class HomePresenterTest {

    private TestDoubles.RecordingHomeSink sink;
    private TestDoubles.InlineUi ui;
    private TestDoubles.InlineExecutor background;
    private TestDoubles.FakeKeyVault key;
    private TestDoubles.FakeHomeData data;
    private TestDoubles.FakeSubmitter submitter;
    private TestDoubles.FakeSettingsStore store;

    private HomePresenter presenter;

    @Before
    public void setUp() {
        sink = new TestDoubles.RecordingHomeSink();
        ui = new TestDoubles.InlineUi();
        background = new TestDoubles.InlineExecutor();
        key = new TestDoubles.FakeKeyVault(true);
        data = new TestDoubles.FakeHomeData();
        submitter = new TestDoubles.FakeSubmitter();
        store = new TestDoubles.FakeSettingsStore();
        presenter = new HomePresenter(sink, ui, background, key, data, submitter, store);
    }

    @Test
    public void startsByShowingTheListPhaseAndReadsTheStoreOffTheUiThread() {
        presenter.start();

        assertTrue("the first state must be the loading phase",
                sink.states.get(0).phase == HomeViewState.Phase.LOADING);
        assertEquals("the store read must happen on the background executor",
                1, background.runs);
        assertTrue("an empty store must land in the empty phase",
                sink.last().phase == HomeViewState.Phase.EMPTY);
    }

    @Test
    public void withTopicsTheHomeIsReadyAndCarriesThemInOrder() {
        data.topics = Arrays.asList(TestDoubles.topic(TestDoubles.TOPIC_1, "第一棵"), TestDoubles.topic(TestDoubles.TOPIC_2, "第二棵"));

        presenter.start();

        assertEquals(HomeViewState.Phase.READY, sink.last().phase);
        assertEquals(2, sink.last().topics.size());
        assertEquals("第一棵", sink.last().topics.get(0).title);
    }

    @Test
    public void anUnreadableStoreShowsTheErrorPhaseAndKeepsTheListItHad() {
        data.topics = Collections.singletonList(TestDoubles.topic(TestDoubles.TOPIC_1, "已有"));
        presenter.start();
        data.topicsThrow = true;

        presenter.onResumed();

        assertEquals(HomeViewState.Phase.ERROR, sink.last().phase);
        assertEquals(HomeViewState.Notice.STORAGE, sink.last().notice);
        assertEquals(1, sink.last().topics.size());
    }

    @Test
    public void aFailedRecommendationDropsTheStripAndLeavesThePageUsable() {
        data.topics = Collections.singletonList(TestDoubles.topic(TestDoubles.TOPIC_1, "已有"));
        data.recommendationsThrow = true;

        presenter.start();

        assertEquals(HomeViewState.Phase.READY, sink.last().phase);
        assertEquals(1, sink.last().topics.size());
        assertTrue("a failed strip must not be shown as an error", sink.last().recommendations.isEmpty());
        assertNull(sink.last().notice);
    }

    @Test
    public void theStarterStripIsShownWhenThereIsNoTopic() {
        presenter.start();

        assertEquals(HomeViewState.Phase.EMPTY, sink.last().phase);
        assertFalse(sink.last().recommendations.isEmpty());
    }

    @Test
    public void aMissingKeyBlocksTheSendOpensSettingsAndKeepsTheDraft() {
        key.configured = false;
        presenter.start();
        presenter.onDraftChanged("冷链断链风险有哪些");

        presenter.onSubmit();

        assertEquals("no submission may reach the store without a key", 0, submitter.calls);
        assertEquals(1, sink.settingsOpened);
        assertEquals("冷链断链风险有哪些", sink.last().draft);
        assertEquals(HomeViewState.Notice.KEY_MISSING, sink.last().notice);
    }

    @Test
    public void aRefusedSubmissionKeepsTheDraftAndReportsTheCategory() {
        presenter.start();
        presenter.onDraftChanged("  冷链断链风险有哪些  ");
        submitter.result = SubmitResult.refused(HomeViewState.Notice.BALANCE_UNAVAILABLE);

        presenter.onSubmit();

        assertEquals(1, submitter.calls);
        assertEquals("the question travels trimmed", "冷链断链风险有哪些", submitter.lastQuestion);
        assertEquals(HomeViewState.Notice.BALANCE_UNAVAILABLE, sink.last().notice);
        assertEquals("  冷链断链风险有哪些  ", sink.last().draft);
        assertFalse(sink.last().submitting);
        assertTrue("nothing may be opened when nothing was created", sink.branchesOpened.isEmpty());
    }

    @Test
    public void aStoreFailureDuringSubmissionIsReportedAsStorageAndKeepsTheDraft() {
        presenter.start();
        presenter.onDraftChanged("冷链断链风险有哪些");
        submitter.throwsInstead = true;

        presenter.onSubmit();

        assertEquals(HomeViewState.Notice.STORAGE, sink.last().notice);
        assertEquals("冷链断链风险有哪些", sink.last().draft);
    }

    @Test
    public void anAcceptedSubmissionClearsTheDraftAndEntersTheRootBranch() {
        presenter.start();
        presenter.onDraftChanged("冷链断链风险有哪些");
        submitter.result = SubmitResult.accepted(TestDoubles.BRANCH_ROOT);

        presenter.onSubmit();

        assertEquals("", sink.last().draft);
        assertNull(sink.last().notice);
        assertFalse(sink.last().submitting);
        assertEquals(Collections.singletonList(TestDoubles.BRANCH_ROOT), sink.branchesOpened);
    }

    @Test
    public void everyProviderFailureCategoryReachesTheHomeAsItsOwnNotice() {
        presenter.start();
        presenter.onDraftChanged("问题");
        int[] statuses = {401, 402, 422, 429, 503};

        for (int status : statuses) {
            submitter.result = SubmitResult.refused(
                    HomeViewState.Notice.from(AiGatewayError.forHttpStatus(status).kind));
            presenter.onSubmit();
            assertEquals("status " + status + " must keep the question",
                    "问题", sink.last().draft);
            assertTrue("status " + status + " must report a failure", sink.last().notice != null);
        }
        assertTrue(sink.branchesOpened.isEmpty());
    }

    @Test
    public void aTappedRecommendationBecomesTheDraftAndUsesTheOneSubmitPath() {
        presenter.start();
        Recommendation tapped = new Recommendation("从零理解向量检索的原理",
                Recommendation.Kind.STARTER);
        submitter.result = SubmitResult.refused(HomeViewState.Notice.RATE_LIMITED);

        presenter.onRecommendationTapped(tapped);

        assertEquals(1, submitter.calls);
        assertEquals("从零理解向量检索的原理", submitter.lastQuestion);
        assertEquals("a blocked recommendation stays in the composer",
                "从零理解向量检索的原理", sink.last().draft);
    }

    @Test
    public void aTappedTopicResumesAtItsLastBranch() {
        presenter.start();
        TopicCard card = new TopicCard(TestDoubles.TOPIC_1, TestDoubles.BRANCH_RESUME, "标题", "摘要", 3, 1_700_000_000_000L);

        presenter.onTopicTapped(card);

        assertEquals(Collections.singletonList(TestDoubles.BRANCH_RESUME), sink.branchesOpened);
    }

    @Test
    public void typingClearsAStaleNoticeAndNeverTouchesTheList() {
        data.topics = Collections.singletonList(TestDoubles.topic(TestDoubles.TOPIC_1, "已有"));
        presenter.start();
        submitter.result = SubmitResult.refused(HomeViewState.Notice.NETWORK_INTERRUPTED);
        presenter.onDraftChanged("问题");
        presenter.onSubmit();

        presenter.onDraftChanged("问题二");

        assertNull(sink.last().notice);
        assertEquals(HomeViewState.Phase.READY, sink.last().phase);
        assertEquals(1, sink.last().topics.size());
        assertEquals("问题二", sink.last().draft);
    }

    @Test
    public void theChosenModelAndThinkingSwitchArePersistedAndCarriedIntoTheSubmission() {
        presenter.start();

        presenter.onModelSelected(GenerationModel.PRO);
        presenter.onThinkingToggled(true);
        presenter.onDraftChanged("问题");
        presenter.onSubmit();

        assertEquals(GenerationModel.PRO, store.model);
        assertTrue(store.thinking);
        assertEquals(GenerationModel.PRO, sink.last().model);
        assertTrue(sink.last().thinkingEnabled);
        assertEquals(GenerationModel.PRO, submitter.lastModel);
        assertTrue(submitter.lastThinking);
    }

    @Test
    public void aDraftTypedWhileTheListIsLoadingSurvivesTheLoad() {
        presenter.onDraftChanged("还没加载完就输入的问题");

        presenter.start();

        assertEquals("还没加载完就输入的问题", sink.last().draft);
        assertEquals(HomeViewState.Phase.EMPTY, sink.last().phase);
    }

    @Test
    public void anEmptyDraftIsNeverSubmitted() {
        presenter.start();

        presenter.onSubmit();

        assertEquals(0, submitter.calls);
        assertNull(sink.last().notice);
    }

    @Test
    public void theKeyHintFollowsTheVaultWhenTheScreenComesBack() {
        key.configured = false;
        presenter.start();
        assertFalse(sink.last().keyConfigured);

        key.configured = true;
        presenter.onResumed();

        assertTrue(sink.last().keyConfigured);
    }

    /**
     * 2026-09-28: coming back from a conversation must not wait for the strip.
     *
     * <p>Producing the strip is a derived artifact that can block on a model stream for up to
     * {@code BlockingDerivedCaller.TIMEOUT_SECONDS}, so the topic list is posted as soon as it is
     * read and the strip follows in a second post. The assertion is on the order, not on the arrival:
     * by the time the strip is asked for, the history cards are already on the screen.
     */
    @Test
    public void theHistoryListReachesTheScreenBeforeTheStripIsProduced() {
        data.topics = Collections.singletonList(TestDoubles.topic(TestDoubles.TOPIC_1, "已有"));
        data.observeStripRead(sink);

        presenter.start();

        assertTrue("the strip must have been read", data.sinkStatesWhenStripWasRead >= 0);
        assertEquals("the list was already drawn when the strip was produced",
                2, data.sinkStatesWhenStripWasRead);
    }

    /**
     * The first post is a finished page on its own: it carries the phase the read implies, so the
     * screen is not left in loading while the strip is in flight. The second post only replaces the
     * strip and must not disturb the phase or the topics it was given.
     *
     * <p>The state the screen held when the strip arrived is found by name rather than by index:
     * the sink is recorded at the moment {@code recommendations(...)} is called, and the last state
     * recorded before that call is the one the first post produced.
     */
    @Test
    public void theFirstPostIsAlreadyAFinishedPageAndTheSecondOnlyAddsTheStrip() {
        data.topics = Collections.singletonList(TestDoubles.topic(TestDoubles.TOPIC_1, "已有"));
        data.observeStripRead(sink);

        presenter.start();

        assertTrue("the strip must have been read", data.sinkStatesWhenStripWasRead > 0);
        HomeViewState whenStripWasRead = sink.states.get(data.sinkStatesWhenStripWasRead - 1);
        assertEquals(HomeViewState.Phase.READY, whenStripWasRead.phase);
        assertEquals(1, whenStripWasRead.topics.size());
        assertTrue("the strip is not part of the first post", whenStripWasRead.recommendations.isEmpty());

        assertEquals("and the last post only added it",
                HomeViewState.Phase.READY, sink.last().phase);
        assertEquals(1, sink.last().topics.size());
        assertFalse(sink.last().recommendations.isEmpty());
    }

    /**
     * A strip that fails after the list has already been drawn leaves the page exactly as the first
     * post made it: usable, ready, with a notice-free history. This is PRD 6.2.17 exception 3 read
     * against the split - the failure now happens in its own post rather than alongside the list.
     */
    @Test
    public void aStripThatFailsAfterTheListWasDrawnStillLeavesThePageUsable() {
        data.topics = Collections.singletonList(TestDoubles.topic(TestDoubles.TOPIC_1, "已有"));
        data.recommendationsThrow = true;

        presenter.start();

        assertEquals(HomeViewState.Phase.READY, sink.last().phase);
        assertEquals(1, sink.last().topics.size());
        assertTrue(sink.last().recommendations.isEmpty());
        assertNull("a failed strip is not an error", sink.last().notice);
    }
}
