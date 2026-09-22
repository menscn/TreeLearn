package com.treelearn.app.wiring;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.treelearn.app.generation.GenerationCoordinator;
import com.treelearn.app.ui.HomeData;
import com.treelearn.app.ui.HomeViewState;
import com.treelearn.app.ui.SubmitResult;
import com.treelearn.app.ui.TopicCard;
import com.treelearn.data.db.dao.FirstTopicCreation;

import org.junit.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

/**
 * The home path's real data path: what the list reads, what the first submission writes, and what
 * every refusal shows.
 *
 * <p>These run on a plain JVM because the database sits behind {@link HomeStore}; the SQL itself is
 * exercised by the instrumented tests, and the request is built by the production assembler here, so
 * what is asserted is the joining rather than a stand-in for it.
 */
public class HomeWiringTest {

    private static final String QUESTION = "explain the attention mechanism";
    private static final String FALLBACK_QUESTION = "the question the tree was created from";

    private final WiringDoubles.Key key = new WiringDoubles.Key();
    private final WiringDoubles.Homes store = new WiringDoubles.Homes();
    private final WiringDoubles.Generations generation = new WiringDoubles.Generations();

    private HomeWiring wiring() {
        return new HomeWiring(store, generation, key,
                Clock.fixed(Instant.ofEpochMilli(WiringDoubles.NOW), ZoneOffset.UTC),
                PromptLimits.budget());
    }

    // ---------------------------------------------------------------- reads

    @Test
    public void recentTopicsFallsBackToTheRootQuestionWhenNoTitleIsStored() {
        store.rows.add(new HomeStore.Row(WiringDoubles.TOPIC_ID, WiringDoubles.RESUME_BRANCH_ID, null,
                FALLBACK_QUESTION, null, 1, WiringDoubles.NOW));

        List<TopicCard> cards = wiring().recentTopics();

        assertEquals(1, cards.size());
        assertEquals(WiringDoubles.TOPIC_ID, cards.get(0).topicId);
        assertEquals(WiringDoubles.RESUME_BRANCH_ID, cards.get(0).resumeBranchId);
        assertEquals(FALLBACK_QUESTION, cards.get(0).title);
        assertEquals("", cards.get(0).summary);
    }

    @Test
    public void recentTopicsPrefersTheStoredTitleOverTheQuestion() {
        store.rows.add(new HomeStore.Row(WiringDoubles.TOPIC_ID, WiringDoubles.RESUME_BRANCH_ID,
                "a derived title", FALLBACK_QUESTION, null, 1, WiringDoubles.NOW));

        assertEquals("a derived title", wiring().recentTopics().get(0).title);
    }

    @Test
    public void recentTopicsDecodesTheStoredSummaryEnvelope() {
        store.rows.add(new HomeStore.Row(WiringDoubles.TOPIC_ID, WiringDoubles.RESUME_BRANCH_ID,
                "title", FALLBACK_QUESTION, WiringDoubles.SUMMARY_JSON, 3, WiringDoubles.NOW));

        TopicCard card = wiring().recentTopics().get(0);

        assertEquals("a stored summary", card.summary);
        assertEquals(3, card.branchCount);
        assertEquals(WiringDoubles.NOW, card.lastUsedAt);
    }

    @Test
    public void recentTopicsIsEmptyBeforeAnyTopicExists() {
        assertTrue(wiring().recentTopics().isEmpty());
    }

    @Test
    public void recentTopicsDoesNotInventASummaryForAnUnreadableEnvelope() {
        store.rows.add(new HomeStore.Row(WiringDoubles.TOPIC_ID, WiringDoubles.RESUME_BRANCH_ID,
                "title", FALLBACK_QUESTION, "not json at all", 1, WiringDoubles.NOW));

        assertEquals("", wiring().recentTopics().get(0).summary);
    }

    // ---------------------------------------------------------------- writes

    @Test
    public void submitWritesTheFirstTopicAndAdmitsTheAttempt() {
        key.configured = true;

        SubmitResult result = wiring().submit(QUESTION, WiringDoubles.MODEL, true);

        // The five row groups went through the one entry point that writes them atomically.
        assertEquals(1, store.createCalls);
        assertEquals(1, generation.firstCalls);
        assertEquals(0, generation.submitCalls);
        assertNotNull(store.createdTopic);
        assertNotNull(store.createdOperation);
        assertTrue(store.createdBranch.isRoot());
        assertEquals(store.createdTopic.id, store.createdBranch.topicId);
        assertEquals(0, store.createdUser.ordinal);
        assertEquals(1, store.createdAi.ordinal);
        assertEquals(QUESTION, store.createdUser.userContent);
        assertEquals("", store.createdUser.quotedText());
        assertEquals(store.createdBranch.id, store.createdUser.branchId);
        assertEquals(store.createdAi.id, store.createdOperation.messageId);

        // The attempt pins the one usage and the one version, and it answers the answer position.
        assertNotNull(generation.lastCommand);
        assertEquals(store.createdAi.id, generation.lastCommand.messageId);
        assertEquals(AnswerPolicy.PURPOSE, generation.lastCommand.purpose);
        assertEquals(AnswerPolicy.version(), generation.lastCommand.promptPolicyVersion);
        assertEquals(WiringDoubles.MODEL, generation.lastCommand.model);
        assertTrue(generation.lastCommand.thinkingEnabled);

        // The request is the production assembler's build: system + the question, and nothing else.
        assertNotNull(generation.lastRequest);
        assertTrue(generation.lastRequest.systemChars > 0);
        assertEquals(2, generation.lastRequest.messages.size());
        assertEquals(0, generation.lastRequest.historyChars);
        assertEquals(QUESTION.length(), generation.lastRequest.currentInputChars);

        assertTrue(result.isAccepted());
        assertEquals(store.createdBranch.id, result.rootBranchId());
    }

    // ------------------------------------------------------- visible refusals

    @Test
    public void submitRefusesWithoutWritingWhenNoKeyIsStored() {
        key.configured = false;

        SubmitResult result = wiring().submit(QUESTION, WiringDoubles.MODEL, false);

        assertFalse(result.isAccepted());
        assertEquals(HomeViewState.Notice.KEY_MISSING, result.notice());
        assertEquals(0, store.createCalls);
        assertEquals(0, generation.firstCalls);
        assertEquals(0, generation.submitCalls);
    }

    @Test
    public void submitSurfacesARefusedFirstTopicWriteAsStorage() {
        key.configured = true;
        store.refusal = FirstTopicCreation.Refusal.TOPIC_ID_ALREADY_EXISTS;

        SubmitResult result = wiring().submit(QUESTION, WiringDoubles.MODEL, false);

        assertEquals(1, store.createCalls);
        assertFalse(result.isAccepted());
        assertEquals(HomeViewState.Notice.STORAGE, result.notice());
    }

    @Test
    public void submitSurfacesABusyCoordinatorAsStorage() {
        key.configured = true;
        generation.firstOutcome = GenerationCoordinator.SubmitOutcome.refused(
                GenerationCoordinator.Refusal.BRANCH_BUSY, null);

        SubmitResult result = wiring().submit(QUESTION, WiringDoubles.MODEL, false);

        assertFalse(result.isAccepted());
        assertEquals(HomeViewState.Notice.STORAGE, result.notice());
    }

    @Test
    public void submitSurfacesAnUnbuildableRequestAsRequestRejected() {
        key.configured = true;
        generation.firstOutcome = GenerationCoordinator.SubmitOutcome.refused(
                GenerationCoordinator.Refusal.PROMPT_BUILD, null);

        SubmitResult result = wiring().submit(QUESTION, WiringDoubles.MODEL, false);

        assertFalse(result.isAccepted());
        assertEquals(HomeViewState.Notice.REQUEST_REJECTED, result.notice());
    }

    @Test
    public void submitSurfacesAStalePinnedVersionAsStorage() {
        key.configured = true;
        generation.firstOutcome = GenerationCoordinator.SubmitOutcome.refused(
                GenerationCoordinator.Refusal.VERSION_MISMATCH, null);

        SubmitResult result = wiring().submit(QUESTION, WiringDoubles.MODEL, false);

        assertEquals(HomeViewState.Notice.STORAGE, result.notice());
    }

    // ------------------------------------------------------- recommendations

    @Test
    public void recommendationsAreTheStarterFallbackForAnEmptyHistory() {
        HomeData.Recommendations strip = wiring().recommendations(false);

        assertFalse(strip.fromHistory);
        assertEquals(4, strip.items.size());
    }

    @Test
    public void recommendationsStayTheStarterFallbackUntilADerivedSetHasAProducer() {
        HomeData.Recommendations strip = wiring().recommendations(true);

        assertFalse(strip.fromHistory);
        assertEquals(4, strip.items.size());
    }
}