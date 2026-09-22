package com.treelearn.app.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.treelearn.ai.gateway.AiGatewayError;
import com.treelearn.core.domain.GenerationModel;
import com.treelearn.data.db.dao.FirstTopicCreation;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * The home state's own rules: the failure mapping is exhaustive, the send control's enablement is a
 * function of the draft and the attempt, and the lists handed out cannot be mutated behind the view's
 * back.
 */
public final class HomeViewStateTest {

    @Test
    public void everyGatewayCategoryHasExactlyOneNotice() {
        AiGatewayError.Kind[] kinds = AiGatewayError.Kind.values();
        for (AiGatewayError.Kind kind : kinds) {
            assertTrue("no notice for " + kind, HomeViewState.Notice.from(kind) != null);
        }
        assertEquals(HomeViewState.Notice.KEY_REJECTED,
                HomeViewState.Notice.from(AiGatewayError.Kind.AUTH_INVALID));
        assertEquals(HomeViewState.Notice.BALANCE_UNAVAILABLE,
                HomeViewState.Notice.from(AiGatewayError.Kind.BALANCE_UNAVAILABLE));
        assertEquals(HomeViewState.Notice.REQUEST_REJECTED,
                HomeViewState.Notice.from(AiGatewayError.Kind.REQUEST_INVALID));
        assertEquals(HomeViewState.Notice.RATE_LIMITED,
                HomeViewState.Notice.from(AiGatewayError.Kind.RATE_LIMITED));
        assertEquals(HomeViewState.Notice.PROVIDER_UNAVAILABLE,
                HomeViewState.Notice.from(AiGatewayError.Kind.PROVIDER_UNAVAILABLE));
        assertEquals(HomeViewState.Notice.NETWORK_INTERRUPTED,
                HomeViewState.Notice.from(AiGatewayError.Kind.NETWORK_INTERRUPTED));
    }

    @Test
    public void theHttpStatusThePrdNamesMapsToTheNoticeItNames() {
        assertEquals(HomeViewState.Notice.KEY_REJECTED,
                HomeViewState.Notice.from(AiGatewayError.forHttpStatus(401).kind));
        assertEquals(HomeViewState.Notice.BALANCE_UNAVAILABLE,
                HomeViewState.Notice.from(AiGatewayError.forHttpStatus(402).kind));
        assertEquals(HomeViewState.Notice.REQUEST_REJECTED,
                HomeViewState.Notice.from(AiGatewayError.forHttpStatus(422).kind));
        assertEquals(HomeViewState.Notice.RATE_LIMITED,
                HomeViewState.Notice.from(AiGatewayError.forHttpStatus(429).kind));
        assertEquals(HomeViewState.Notice.PROVIDER_UNAVAILABLE,
                HomeViewState.Notice.from(AiGatewayError.forHttpStatus(503).kind));
    }

    @Test
    public void everyFirstTopicRefusalBecomesAStorageNotice() {
        for (FirstTopicCreation.Refusal refusal : FirstTopicCreation.Refusal.values()) {
            assertEquals("refusal " + refusal, HomeViewState.Notice.STORAGE,
                    HomeViewState.Notice.from(refusal));
        }
    }

    @Test
    public void theSendControlNeedsADraftAndNoAttemptInFlight() {
        HomeViewState idle = HomeViewState.initial(GenerationModel.FLASH, false, true);
        assertFalse(idle.canSubmit());

        assertTrue(idle.withDraft("问题").canSubmit());
        assertFalse("whitespace is not a question", idle.withDraft("   ").canSubmit());
        assertFalse(idle.withDraft("问题").withSubmitting(true).canSubmit());
    }

    @Test
    public void theListsHandedToTheViewCannotBeChangedFromOutside() {
        HomeViewState state = HomeViewState.initial(GenerationModel.FLASH, false, true)
                .withTopics(Collections.singletonList(TestDoubles.topic(TestDoubles.TOPIC_1, "标题")));
        try {
            state.topics.add(TestDoubles.topic(TestDoubles.TOPIC_2, "另一棵"));
            fail("the topic list must be unmodifiable");
        } catch (UnsupportedOperationException expected) {
            assertEquals(1, state.topics.size());
        }
    }

    @Test
    public void aStripWithoutStartingPointsIsRefused() {
        List<Recommendation> empty = Arrays.asList();
        try {
            HomeData.Recommendations.starterFallback(empty);
            fail("an empty strip must not be constructible");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("at least one starting point"));
        }
    }

    @Test
    public void theStarterSetIsUsableAsBothAStartingPointAndAFallback() {
        List<Recommendation> starters = StarterRecommendations.all();
        assertFalse(starters.isEmpty());
        for (Recommendation item : starters) {
            assertEquals(Recommendation.Kind.STARTER, item.kind);
            assertFalse(item.question.trim().isEmpty());
        }
        assertEquals(starters.size(),
                HomeData.Recommendations.starterFallback(starters).items.size());
    }
}