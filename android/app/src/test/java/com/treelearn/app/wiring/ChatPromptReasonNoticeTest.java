package com.treelearn.app.wiring;

import static org.junit.Assert.assertEquals;

import com.treelearn.ai.prompt.PromptBuildException;
import com.treelearn.app.generation.GenerationCoordinator;
import com.treelearn.app.ui.ChatCommandResult;
import com.treelearn.app.ui.HomeViewState;
import com.treelearn.app.ui.SubmitResult;
import org.junit.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

/**
 * T-047's foreman ruling: {@code SubmitOutcome.promptReason} must have a real consumer, and the
 * refusal-to-notice mapping must stay in the one place ({@link RefusalNotices}) so the home page and
 * the conversation cannot describe the same refusal two different ways.
 *
 * <p>The history-budget refusal is the one case where the generic "check the model and the content"
 * wording is the wrong advice: after T-047 a retry really can succeed, because the compression this
 * task wired produces a smaller plan and the next send picks it up.
 */
public class ChatPromptReasonNoticeTest {

    private static final String QUESTION = "explain the attention mechanism";

    private final WiringDoubles.Key key = new WiringDoubles.Key();
    private final WiringDoubles.Homes homes = new WiringDoubles.Homes();
    private final WiringDoubles.Chats chats = new WiringDoubles.Chats();
    private final WiringDoubles.Generations generation = new WiringDoubles.Generations();

    private HomeWiring homeWiring() {
        return new HomeWiring(homes, generation, key,
                Clock.fixed(Instant.ofEpochMilli(WiringDoubles.NOW), ZoneOffset.UTC),
                PromptLimits.budget());
    }

    private ChatWiring chatWiring() {
        return new ChatWiring(chats, generation, key,
                Clock.fixed(Instant.ofEpochMilli(WiringDoubles.NOW), ZoneOffset.UTC),
                PromptLimits.budget());
    }

    @Test
    public void aHistoryBudgetRefusalAfterCompressionProducedSaysSoInsteadOfBlamingTheContent() {
        assertEquals(HomeViewState.Notice.HISTORY_COMPRESSED, RefusalNotices.of(
                GenerationCoordinator.Refusal.PROMPT_BUILD,
                PromptBuildException.Reason.HISTORY_EXCEEDS_BUDGET, true));
    }

    @Test
    public void aHistoryBudgetRefusalWithoutCompressionKeepsTheFailClosedWording() {
        assertEquals(HomeViewState.Notice.REQUEST_REJECTED, RefusalNotices.of(
                GenerationCoordinator.Refusal.PROMPT_BUILD,
                PromptBuildException.Reason.HISTORY_EXCEEDS_BUDGET, false));
    }

    @Test
    public void aReserveBudgetRefusalKeepsTheGenericWordingWhateverCompressionDid() {
        assertEquals(HomeViewState.Notice.REQUEST_REJECTED, RefusalNotices.of(
                GenerationCoordinator.Refusal.PROMPT_BUILD,
                PromptBuildException.Reason.RESERVE_EXCEEDS_BUDGET, true));
        assertEquals(HomeViewState.Notice.REQUEST_REJECTED, RefusalNotices.of(
                GenerationCoordinator.Refusal.PROMPT_BUILD,
                PromptBuildException.Reason.RESERVE_EXCEEDS_BUDGET, false));
    }

    @Test
    public void theOneArgumentFormStillClassifiesAPlainPromptBuildRefusal() {
        assertEquals(HomeViewState.Notice.REQUEST_REJECTED,
                RefusalNotices.of(GenerationCoordinator.Refusal.PROMPT_BUILD));
    }

    /** The ruling's cross-entry arm: the same refusal inputs must reach both entries alike. */
    @Test
    public void theChatAndTheHomeMapTheSamePromptBuildRefusalToTheSameNotice() {
        key.configured = true;
        generation.submitOutcome = GenerationCoordinator.SubmitOutcome.refused(
                GenerationCoordinator.Refusal.PROMPT_BUILD,
                PromptBuildException.Reason.HISTORY_EXCEEDS_BUDGET);
        generation.firstOutcome = generation.submitOutcome;

        SubmitResult homeResult = homeWiring().submit(QUESTION, WiringDoubles.MODEL, false);
        ChatCommandResult chatResult = chatWiring().send(WiringDoubles.BRANCH_ID, "a follow-up", "", null,
                WiringDoubles.MODEL, false);

        assertEquals(HomeViewState.Notice.REQUEST_REJECTED, homeResult.notice());
        // Neither entry compresses here (the home has no history at all; this chat wiring derives
        // nothing), so both must land on the same fail-closed wording - the same conclusion the
        // direct RefusalNotices assertions above pin for the compression-produced arm.
        assertEquals(homeResult.notice(), chatResult.notice());
    }
}
