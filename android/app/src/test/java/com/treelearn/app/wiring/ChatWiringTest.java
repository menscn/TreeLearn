package com.treelearn.app.wiring;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.treelearn.ai.prompt.DeepSeekRequest;
import com.treelearn.app.generation.GenerationCoordinator;
import com.treelearn.app.ui.ChatCommandResult;
import com.treelearn.app.ui.ChatData;
import com.treelearn.app.ui.ChatMessage;
import com.treelearn.app.ui.HomeViewState;
import com.treelearn.app.ui.SourceCard;
import com.treelearn.core.domain.AnswerRevision;
import com.treelearn.core.domain.Branch;
import com.treelearn.core.domain.BranchAnchor;
import com.treelearn.core.domain.BranchSource;
import com.treelearn.core.domain.ContextPlan;
import com.treelearn.core.domain.ContextSnapshot;
import com.treelearn.core.domain.ContextSnapshotCodec;
import com.treelearn.core.domain.DomainErrorCode;
import com.treelearn.core.domain.DomainResult;
import com.treelearn.core.domain.GenerationCommand;
import com.treelearn.core.domain.Message;

import org.junit.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * The conversation's real data path: what a stored position reads as, what each command writes, and
 * what every refusal shows.
 *
 * <p>The stores, the usage decision and the request build are all the production ones; only the rows
 * are a list, which is what lets the deciding half run without a database.
 */
public class ChatWiringTest {

    private final WiringDoubles.Key key = new WiringDoubles.Key();
    private final WiringDoubles.Chats store = new WiringDoubles.Chats();
    private final WiringDoubles.Generations generation = new WiringDoubles.Generations();

    private ChatWiring wiring() {
        return new ChatWiring(store, generation, key,
                Clock.fixed(Instant.ofEpochMilli(WiringDoubles.NOW), ZoneOffset.UTC),
                PromptLimits.budget());
    }

    private static ChatStore.Position user(int ordinal, String text) {
        return new ChatStore.Position(Message.user(WiringDoubles.BRANCH_ID, ordinal, text,
                WiringDoubles.QUOTED_LINE, WiringDoubles.NOW), null, 0, 0, false, "", "");
    }

    private static ChatStore.Position answer(int ordinal, String messageId, AnswerRevision revision) {
        return new ChatStore.Position(Message.restore(messageId, WiringDoubles.BRANCH_ID,
                com.treelearn.core.domain.MessageRole.AI, ordinal, null, "", WiringDoubles.NOW,
                revision == null ? null : revision.id, null), revision, revision == null ? 0 : 1, 0,
                false, "", "");
    }

    private static AnswerRevision revision(String messageId, String reasoning, String answer) {
        DomainResult<AnswerRevision> created = AnswerRevision.create(messageId, 1, reasoning, answer,
                WiringDoubles.MODEL, false, AnswerPolicy.version(), WiringDoubles.NOW);
        assertTrue(created.isOk());
        return created.value();
    }

    // ---------------------------------------------------------------- reads

    @Test
    public void readMapsAStoredVersionToStableWithItsReasoning() {
        AnswerRevision stable = revision(WiringDoubles.ANSWER_ID, "the reasoning", "the answer");
        store.conversation = new ChatStore.Conversation("a branch title", Arrays.asList(
                user(0, WiringDoubles.FIRST_QUESTION), answer(1, WiringDoubles.ANSWER_ID, stable)));

        ChatData.Snapshot snapshot = wiring().read(WiringDoubles.BRANCH_ID);

        assertEquals("a branch title", snapshot.branchTitle);
        assertEquals(2, snapshot.messages.size());
        ChatMessage asked = snapshot.messages.get(0);
        assertFalse(asked.isAi());
        assertEquals(WiringDoubles.FIRST_QUESTION, asked.text);
        assertEquals(WiringDoubles.QUOTED_LINE, asked.quote);
        ChatMessage answered = snapshot.messages.get(1);
        assertTrue(answered.isStable());
        assertEquals("the answer", answered.text);
        assertEquals("the reasoning", answered.reasoning);
        assertEquals(stable.id, answered.activeRevisionId);
        assertTrue(answered.canCopy());
        assertTrue(answered.canFork());
        assertTrue(snapshot.answerRecommendations.isEmpty());
        assertEquals("", snapshot.recommendationsRevisionId);
    }

    @Test
    public void readMapsAnAttemptInFlightToGeneratingInThePhaseItReached() {
        store.conversation = new ChatStore.Conversation("", Arrays.asList(
                new ChatStore.Position(Message.restore(WiringDoubles.ANSWER_ID, WiringDoubles.BRANCH_ID,
                        com.treelearn.core.domain.MessageRole.AI, 1, null, "", WiringDoubles.NOW, null,
                        null), null, 0, 0, true, "still reasoning", "")));

        ChatMessage forming = wiring().read(WiringDoubles.BRANCH_ID).messages.get(0);

        assertTrue(forming.isGenerating());
        assertEquals(ChatMessage.Phase.REASONING, forming.phase());
        assertTrue(forming.canExpandReasoning());
        assertFalse(forming.canCopy());
        assertFalse(forming.canFork());
    }

    @Test
    public void readMapsAnAttemptThatReachedTheAnswerStreamToTheAnswerPhase() {
        store.conversation = new ChatStore.Conversation("", Arrays.asList(
                new ChatStore.Position(Message.restore(WiringDoubles.ANSWER_ID, WiringDoubles.BRANCH_ID,
                        com.treelearn.core.domain.MessageRole.AI, 1, null, "", WiringDoubles.NOW, null,
                        null), null, 0, 0, true, "the reasoning", "half an answer")));

        ChatMessage forming = wiring().read(WiringDoubles.BRANCH_ID).messages.get(0);

        assertTrue(forming.isGenerating());
        assertEquals(ChatMessage.Phase.ANSWER, forming.phase());
        assertEquals("half an answer", forming.text);
    }

    @Test
    public void readMapsAnEndedAttemptWithNoVersionToFailed() {
        store.conversation = new ChatStore.Conversation("", Arrays.asList(
                new ChatStore.Position(Message.restore(WiringDoubles.ANSWER_ID, WiringDoubles.BRANCH_ID,
                        com.treelearn.core.domain.MessageRole.AI, 1, null, "", WiringDoubles.NOW, null,
                        null), null, 0, 0, false, "", "")));

        ChatMessage failed = wiring().read(WiringDoubles.BRANCH_ID).messages.get(0);

        assertTrue(failed.isFailed());
        assertEquals("", failed.text);
        assertFalse(failed.canCopy());
        assertFalse(failed.canFork());
        assertTrue(failed.canRetry());
    }

    // ---------------------------------------------------------------- writes

    @Test
    public void sendPersistsTheTurnBeforeAdmittingTheAttempt() {
        key.configured = true;
        store.nextOrdinal = 2;

        ChatCommandResult result = wiring().send(WiringDoubles.BRANCH_ID, "  a follow-up  ",
                WiringDoubles.QUOTED_LINE, null, WiringDoubles.MODEL, true);

        assertEquals(1, store.appendCalls);
        assertEquals(2, store.appendedUser.ordinal);
        assertEquals(3, store.appendedAi.ordinal);
        assertEquals("a follow-up", store.appendedUser.userContent);
        assertEquals(WiringDoubles.QUOTED_LINE, store.appendedUser.quotedText());
        assertEquals(WiringDoubles.BRANCH_ID, store.appendedUser.branchId);
        assertEquals(1, generation.submitCalls);
        assertEquals(0, generation.firstCalls);
        assertEquals(store.appendedAi.id, generation.lastCommand.messageId);
        assertTrue(generation.lastCommand.thinkingEnabled);
        assertNotNull(generation.lastRequest);
        // The row keeps the cited sentence (T-037), so the request carries it: the current input is
        // the framed citation followed by the *trimmed* question, never the padded draft.
        String latest = lastMessage(generation.lastRequest);
        assertEquals(cited("a follow-up"), latest);
        assertEquals(latest.length(), generation.lastRequest.currentInputChars);
        assertFalse(latest, latest.contains("  a follow-up  "));
        assertTrue(result.isAccepted());
        assertEquals(WiringDoubles.OPERATION_ID, result.operationId());
    }

    @Test
    public void sendRefusesWithoutPersistingWhenNoKeyIsStored() {
        key.configured = false;

        ChatCommandResult result = wiring().send(WiringDoubles.BRANCH_ID, "a follow-up", "", null, WiringDoubles.MODEL, false);

        assertFalse(result.isAccepted());
        assertEquals(HomeViewState.Notice.KEY_MISSING, result.notice());
        assertEquals(0, store.appendCalls);
        assertEquals(0, generation.submitCalls);
    }

    @Test
    public void retryReassemblesTheRequestFromTheQuestionBehindThePosition() {
        key.configured = true;
        Message retried = Message.restore(WiringDoubles.ANSWER_ID, WiringDoubles.BRANCH_ID,
                com.treelearn.core.domain.MessageRole.AI, 1, null, "", WiringDoubles.NOW, null, null);
        store.positions.put(WiringDoubles.ANSWER_ID, retried);
        store.conversation = new ChatStore.Conversation("", Arrays.asList(
                user(0, WiringDoubles.FIRST_QUESTION),
                answer(1, WiringDoubles.ANSWER_ID, null),
                user(2, WiringDoubles.LATER_QUESTION),
                answer(3, WiringDoubles.LATER_ANSWER_ID, null)));
        DomainResult<GenerationCommand> decided = GenerationCommand.create(WiringDoubles.BRANCH_ID,
                WiringDoubles.ANSWER_ID, AnswerPolicy.PURPOSE, AnswerPolicy.version(),
                WiringDoubles.MODEL, false, WiringDoubles.NOW);
        assertTrue(decided.isOk());
        store.retryResult = decided;

        ChatCommandResult result = wiring().retry(WiringDoubles.ANSWER_ID, WiringDoubles.MODEL, false,
                WiringDoubles.NOW);

        // The history stops before the question this position answers, and that question is the
        // request's current input - not the branch's latest one. The stored row also carries the
        // sentence the learner asked about, so a replacement asks about the same sentence again
        // instead of quietly becoming a question about nothing in particular.
        assertEquals(0, store.historyOrdinal);
        String latest = lastMessage(generation.lastRequest);
        assertEquals(cited(WiringDoubles.FIRST_QUESTION), latest);
        assertEquals(latest.length(), generation.lastRequest.currentInputChars);
        assertTrue(result.isAccepted());
        assertEquals(WiringDoubles.OPERATION_ID, result.operationId());
    }

    @Test
    public void retrySurfacesARefusedStoreDecisionAsStorage() {
        key.configured = true;
        store.positions.put(WiringDoubles.ANSWER_ID, Message.restore(WiringDoubles.ANSWER_ID,
                WiringDoubles.BRANCH_ID, com.treelearn.core.domain.MessageRole.AI, 1, null, "", WiringDoubles.NOW, null, null));
        store.retryResult = DomainResult.fail(DomainErrorCode.OPERATION_NOT_STABLE, "refused");

        ChatCommandResult result = wiring().retry(WiringDoubles.ANSWER_ID, WiringDoubles.MODEL, false,
                WiringDoubles.NOW);

        assertFalse(result.isAccepted());
        assertEquals(HomeViewState.Notice.STORAGE, result.notice());
        assertEquals(0, generation.submitCalls);
    }

    @Test
    public void resetRunsTheStoresResetVerdictOnTheSameRoute() {
        key.configured = true;
        store.positions.put(WiringDoubles.ANSWER_ID, Message.restore(WiringDoubles.ANSWER_ID,
                WiringDoubles.BRANCH_ID, com.treelearn.core.domain.MessageRole.AI, 1, null, "", WiringDoubles.NOW, null, null));
        store.conversation = new ChatStore.Conversation("", Arrays.asList(
                user(0, WiringDoubles.FIRST_QUESTION), answer(1, WiringDoubles.ANSWER_ID, null)));
        DomainResult<GenerationCommand> decided = GenerationCommand.create(WiringDoubles.BRANCH_ID,
                WiringDoubles.ANSWER_ID, AnswerPolicy.PURPOSE, AnswerPolicy.version(),
                WiringDoubles.MODEL, false, WiringDoubles.NOW);
        assertTrue(decided.isOk());
        store.resetResult = decided;

        ChatCommandResult result = wiring().reset(WiringDoubles.ANSWER_ID, WiringDoubles.MODEL, false,
                WiringDoubles.NOW);

        assertTrue(result.isAccepted());
        assertEquals(1, generation.submitCalls);
    }

    @Test
    public void selectRevisionNamesNoAttempt() {
        AnswerRevision other = revision(WiringDoubles.ANSWER_ID, "", "another answer");
        store.selectResult = DomainResult.ok(other);

        ChatCommandResult result = wiring().selectRevision(WiringDoubles.ANSWER_ID, other.id,
                WiringDoubles.NOW);

        assertTrue(result.isAccepted());
        assertEquals("", result.operationId());
        assertEquals("", result.branchId());
        assertEquals(0, generation.submitCalls);
    }

    @Test
    public void selectRevisionSurfacesAStoreRefusalAsStorage() {
        store.selectResult = DomainResult.fail(DomainErrorCode.REVISION_IMMUTABLE, "refused");

        ChatCommandResult result = wiring().selectRevision(WiringDoubles.ANSWER_ID,
                WiringDoubles.REVISION_ID, WiringDoubles.NOW);

        assertFalse(result.isAccepted());
        assertEquals(HomeViewState.Notice.STORAGE, result.notice());
    }

    @Test
    public void forkOpensTheChildBranchWithoutStartingAnAttempt() {
        // T-064 gave fork the same key guard send and replace already had, so a fork that runs
        // must be one a configured vault allowed through (PRD §6.2.3 exception 2).
        key.configured = true;
        AnswerRevision stable = revision(WiringDoubles.ANSWER_ID, "the reasoning", "the answer");
        Message source = Message.restore(WiringDoubles.ANSWER_ID, WiringDoubles.BRANCH_ID,
                com.treelearn.core.domain.MessageRole.AI, 1, null, "", WiringDoubles.NOW, stable.id,
                null);
        store.positions.put(WiringDoubles.ANSWER_ID, source);
        store.branch = Branch.root(WiringDoubles.TOPIC_ID, WiringDoubles.NOW);
        store.conversation = new ChatStore.Conversation("", Arrays.asList(
                user(0, WiringDoubles.FIRST_QUESTION), answer(1, WiringDoubles.ANSWER_ID, stable)));

        ChatCommandResult result = wiring().fork(WiringDoubles.ANSWER_ID, "", "", 0, 0);

        assertEquals(1, store.createBranchCalls);
        assertEquals(0, generation.submitCalls);
        assertNotNull(store.createdBranch);
        assertEquals(WiringDoubles.TOPIC_ID, store.createdBranch.topicId);
        assertEquals(store.createdBranch.id, store.createdSnapshot.branchId);
        assertEquals(store.createdBranch.source.contextSnapshotId, store.createdSnapshot.id);
        assertEquals(ContextSnapshot.SCHEMA_VERSION, store.createdSnapshot.schemaVersion);
        assertTrue(result.isAccepted());
        assertEquals(store.createdBranch.id, result.branchId());
        // A fork writes rows and starts nothing, so it must not name an attempt row.
        assertEquals("", result.operationId());
    }

    @Test
    public void forkRefusesAPositionThatDisplaysNoStableVersion() {
        // The vault is configured so the refusal that reaches this position is the storage one the
        // test names, not the key guard that now runs before it (T-064).
        key.configured = true;
        store.positions.put(WiringDoubles.ANSWER_ID, Message.restore(WiringDoubles.ANSWER_ID,
                WiringDoubles.BRANCH_ID, com.treelearn.core.domain.MessageRole.AI, 1, null, "", WiringDoubles.NOW, null, null));
        store.branch = Branch.root(WiringDoubles.TOPIC_ID, WiringDoubles.NOW);
        store.conversation = new ChatStore.Conversation("", Arrays.asList(
                user(0, WiringDoubles.FIRST_QUESTION), answer(1, WiringDoubles.ANSWER_ID, null)));

        ChatCommandResult result = wiring().fork(WiringDoubles.ANSWER_ID, "", "", 0, 0);

        assertFalse(result.isAccepted());
        assertEquals(HomeViewState.Notice.STORAGE, result.notice());
        assertEquals(0, store.createBranchCalls);
    }

    @Test
    public void forkRefusesALearnerPosition() {
        Message asked = Message.user(WiringDoubles.BRANCH_ID, 0, WiringDoubles.FIRST_QUESTION,
                WiringDoubles.NOW);
        store.positions.put(asked.id, asked);

        ChatCommandResult result = wiring().fork(asked.id, "", "", 0, 0);

        assertFalse(result.isAccepted());
        assertEquals(0, store.createBranchCalls);
    }

    /**
     * T-064: fork joins send and replace behind the same key guard (PRD §6.2.3 exception 2). The
     * refusal has to land before any row is chosen, so an unconfigured vault can never spend the
     * store writes a fork needs - and never open a branch it could not have asked a question in.
     */
    @Test
    public void forkWithoutKeyIsRefusedBeforeCreation() {
        // A position that is forkable in every other respect; only the vault is empty.
        AnswerRevision stable = revision(WiringDoubles.ANSWER_ID, "the reasoning", "the answer");
        Message source = Message.restore(WiringDoubles.ANSWER_ID, WiringDoubles.BRANCH_ID,
                com.treelearn.core.domain.MessageRole.AI, 1, null, "", WiringDoubles.NOW, stable.id,
                null);
        store.positions.put(WiringDoubles.ANSWER_ID, source);
        store.branch = Branch.root(WiringDoubles.TOPIC_ID, WiringDoubles.NOW);
        store.conversation = new ChatStore.Conversation("", Arrays.asList(
                user(0, WiringDoubles.FIRST_QUESTION), answer(1, WiringDoubles.ANSWER_ID, stable)));
        key.configured = false;

        ChatCommandResult result = wiring().fork(WiringDoubles.ANSWER_ID, "", "", 0, 0);

        assertFalse(result.isAccepted());
        assertEquals(HomeViewState.Notice.KEY_MISSING, result.notice());
        assertEquals(0, store.createBranchCalls);
        assertEquals(0, generation.submitCalls);
    }

    /**
     * T-064's failure channel: a birth snapshot that cannot be proven intact stops the request
     * instead of sending a silently shortened history (PRD §6.2.4 exception 2). The store raises,
     * the wiring turns it into the STORAGE notice the presenter draws.
     */
    @Test
    public void sendRefusesWhenTheBirthSnapshotCannotBeTrusted() {
        key.configured = true;
        store.nextOrdinal = 0;
        store.historyFailure = new ChatStore.SnapshotUnreadable(
                "the stored snapshot payload does not hash to its stored digest");

        ChatCommandResult result = wiring().send(WiringDoubles.BRANCH_ID, "a follow-up", "", null, WiringDoubles.MODEL, false);

        assertFalse(result.isAccepted());
        assertEquals(HomeViewState.Notice.STORAGE, result.notice());
        assertEquals(0, generation.submitCalls);
    }

    @Test
    public void forkSurfacesARefusedCreationAsStorage() {
        // The vault is configured so the refusal that reaches the store is the creation one the
        // test names, not the key guard that now runs before it (T-064).
        key.configured = true;
        AnswerRevision stable = revision(WiringDoubles.ANSWER_ID, "", "the answer");
        Message source = Message.restore(WiringDoubles.ANSWER_ID, WiringDoubles.BRANCH_ID,
                com.treelearn.core.domain.MessageRole.AI, 1, null, "", WiringDoubles.NOW, stable.id,
                null);
        store.positions.put(WiringDoubles.ANSWER_ID, source);
        store.branch = Branch.root(WiringDoubles.TOPIC_ID, WiringDoubles.NOW);
        store.conversation = new ChatStore.Conversation("", Arrays.asList(
                user(0, WiringDoubles.FIRST_QUESTION), answer(1, WiringDoubles.ANSWER_ID, stable)));
        store.branchRefusal = com.treelearn.data.db.dao.BranchCreation.Refusal.SOURCE_NOT_IN_THIS_TOPIC;

        ChatCommandResult result = wiring().fork(WiringDoubles.ANSWER_ID, "", "", 0, 0);

        assertFalse(result.isAccepted());
        assertEquals(HomeViewState.Notice.STORAGE, result.notice());
    }

    /**
     * T-065: a fork made from a selected sentence stores a ranged anchor - the quote, the offsets
     * and the digest derived by the codec the snapshot already owns.
     */
    @Test
    public void forkWithSelectionAnchorsRange() {
        key.configured = true;
        String answerText = "the answer";
        AnswerRevision stable = revision(WiringDoubles.ANSWER_ID, "the reasoning", answerText);
        Message source = Message.restore(WiringDoubles.ANSWER_ID, WiringDoubles.BRANCH_ID,
                com.treelearn.core.domain.MessageRole.AI, 1, null, "", WiringDoubles.NOW, stable.id,
                null);
        store.positions.put(WiringDoubles.ANSWER_ID, source);
        store.branch = Branch.root(WiringDoubles.TOPIC_ID, WiringDoubles.NOW);
        store.conversation = new ChatStore.Conversation("", Arrays.asList(
                user(0, WiringDoubles.FIRST_QUESTION), answer(1, WiringDoubles.ANSWER_ID, stable)));

        ChatCommandResult result = wiring().fork(WiringDoubles.ANSWER_ID, "", answerText, 0,
                answerText.length());

        assertTrue(result.isAccepted());
        BranchAnchor anchor = store.createdBranch.source.anchor;
        assertFalse("a selected sentence must not be stored as a whole-answer anchor",
                anchor.isWholeAnswer());
        assertEquals(0, anchor.startInclusive.intValue());
        assertEquals(answerText.length(), anchor.endExclusive.intValue());
        assertEquals(answerText, anchor.quote);
        assertEquals("the hash is the one digest derivation the codec owns",
                ContextSnapshotCodec.digestOf(answerText), anchor.hash);
    }

    /** T-065: the bottom action bar forks without a selection, so the anchor stays whole-answer. */
    @Test
    public void forkWithoutSelectionKeepsWholeAnswer() {
        key.configured = true;
        AnswerRevision stable = revision(WiringDoubles.ANSWER_ID, "the reasoning", "the answer");
        Message source = Message.restore(WiringDoubles.ANSWER_ID, WiringDoubles.BRANCH_ID,
                com.treelearn.core.domain.MessageRole.AI, 1, null, "", WiringDoubles.NOW, stable.id,
                null);
        store.positions.put(WiringDoubles.ANSWER_ID, source);
        store.branch = Branch.root(WiringDoubles.TOPIC_ID, WiringDoubles.NOW);
        store.conversation = new ChatStore.Conversation("", Arrays.asList(
                user(0, WiringDoubles.FIRST_QUESTION), answer(1, WiringDoubles.ANSWER_ID, stable)));

        ChatCommandResult result = wiring().fork(WiringDoubles.ANSWER_ID, "", "", 0, 0);

        assertTrue(result.isAccepted());
        assertTrue("an empty selection keeps the whole-answer anchor",
                store.createdBranch.source.anchor.isWholeAnswer());
    }

    /** T-065: a quote that disagrees with its offsets is refused in the domain, not stored. */
    @Test
    public void forkRefusesAQuoteThatDisagreesWithItsOffsets() {
        key.configured = true;
        AnswerRevision stable = revision(WiringDoubles.ANSWER_ID, "the reasoning", "the answer");
        Message source = Message.restore(WiringDoubles.ANSWER_ID, WiringDoubles.BRANCH_ID,
                com.treelearn.core.domain.MessageRole.AI, 1, null, "", WiringDoubles.NOW, stable.id,
                null);
        store.positions.put(WiringDoubles.ANSWER_ID, source);
        store.branch = Branch.root(WiringDoubles.TOPIC_ID, WiringDoubles.NOW);
        store.conversation = new ChatStore.Conversation("", Arrays.asList(
                user(0, WiringDoubles.FIRST_QUESTION), answer(1, WiringDoubles.ANSWER_ID, stable)));

        ChatCommandResult result = wiring().fork(WiringDoubles.ANSWER_ID, "", "the answer", 0, 4);

        assertFalse(result.isAccepted());
        assertEquals(HomeViewState.Notice.STORAGE, result.notice());
        assertEquals(0, store.createBranchCalls);
    }

    /**
     * T-065: the source card comes from the one snapshot read-back path (064), listing the upstream
     * history in source order, with the collapsed line naming the source answer - or the anchored
     * sentence when the fork carries one.
     */
    @Test
    public void sourceCardListsSnapshotHistory() {
        store.branch = childBranch(BranchAnchor.wholeAnswer());
        store.conversation = new ChatStore.Conversation("child title",
                Collections.<ChatStore.Position>emptyList(), store.branch,
                Collections.<Message>emptyList());
        store.history = Arrays.asList(
                ContextPlan.Entry.userMessage(WiringDoubles.QUESTION_ID,
                        WiringDoubles.FIRST_QUESTION),
                ContextPlan.Entry.stableRevision(WiringDoubles.REVISION_ID, "",
                        WiringDoubles.QUOTED_LINE));

        ChatData.Snapshot snapshot = wiring().read(WiringDoubles.BRANCH_ID);

        assertNotNull("a child branch carries a source card", snapshot.sourceCard);
        assertEquals(2, snapshot.sourceCard.history.size());
        assertTrue(snapshot.sourceCard.history.get(0).user);
        assertEquals(WiringDoubles.FIRST_QUESTION, snapshot.sourceCard.history.get(0).text);
        assertFalse(snapshot.sourceCard.history.get(1).user);
        assertEquals(WiringDoubles.QUOTED_LINE, snapshot.sourceCard.history.get(1).text);
        assertEquals("the collapsed line excerpts the source answer", WiringDoubles.QUOTED_LINE,
                snapshot.sourceCard.summary);

        store.branch = childBranch(BranchAnchor.ofRange(0, WiringDoubles.QUOTED_LINE.length(),
                WiringDoubles.QUOTED_LINE, ContextSnapshotCodec.digestOf(WiringDoubles.QUOTED_LINE))
                .value());
        store.conversation = new ChatStore.Conversation("child title",
                Collections.<ChatStore.Position>emptyList(), store.branch,
                Collections.<Message>emptyList());

        snapshot = wiring().read(WiringDoubles.BRANCH_ID);

        assertEquals("a ranged fork summarizes with the anchored sentence", WiringDoubles.QUOTED_LINE,
                snapshot.sourceCard.summary);
    }

    private static Branch childBranch(BranchAnchor anchor) {
        DomainResult<Branch> created = Branch.child(WiringDoubles.TOPIC_ID,
                BranchSource.restore("0e0e0e0e-0e0e-4e0e-8e0e-0e0e0e0e0e0e",
                        WiringDoubles.ANSWER_ID, WiringDoubles.REVISION_ID, anchor,
                        "0f0f0f0f-0f0f-4f0f-8f0f-0f0f0f0f0f0f"), WiringDoubles.NOW);
        assertTrue(created.isOk());
        return created.value();
    }

    @Test
    public void versionsReadsThePositionsStoredVersions() {
        List<AnswerRevision> stored = new ArrayList<AnswerRevision>();
        stored.add(revision(WiringDoubles.ANSWER_ID, "", "the first answer"));
        store.versions = stored;

        assertEquals(1, wiring().versions(WiringDoubles.ANSWER_ID).size());
    }

    @Test
    public void sendSurfacesACoordinatorRefusalAsStorage() {
        key.configured = true;
        generation.submitOutcome = GenerationCoordinator.SubmitOutcome.refused(
                GenerationCoordinator.Refusal.BRANCH_BUSY, null);

        ChatCommandResult result = wiring().send(WiringDoubles.BRANCH_ID, "a follow-up", "", null, WiringDoubles.MODEL, false);

        assertFalse(result.isAccepted());
        assertEquals(HomeViewState.Notice.STORAGE, result.notice());
        // The turn is durable before the attempt is admitted, so a refusal leaves a visible,
        // retryable position rather than a question that vanished with its refusal.
        assertEquals(1, store.appendCalls);
    }

    @Test
    public void sendSurfacesAnUnbuildableRequestAsRequestRejected() {
        key.configured = true;
        generation.submitOutcome = GenerationCoordinator.SubmitOutcome.refused(
                GenerationCoordinator.Refusal.PROMPT_BUILD, null);

        ChatCommandResult result = wiring().send(WiringDoubles.BRANCH_ID, "a follow-up", "", null, WiringDoubles.MODEL, false);

        assertEquals(HomeViewState.Notice.REQUEST_REJECTED, result.notice());
    }

    @Test
    public void historyEntriesAreHandedToTheProductionEncoder() {
        key.configured = true;
        store.nextOrdinal = 2;
        List<ContextPlan.Entry> entries = new ArrayList<ContextPlan.Entry>();
        entries.add(ContextPlan.Entry.userMessage(WiringDoubles.QUESTION_ID, WiringDoubles.FIRST_QUESTION));
        store.history = entries;

        ChatCommandResult result = wiring().send(WiringDoubles.BRANCH_ID, "a follow-up", "", null, WiringDoubles.MODEL, false);

        assertTrue(result.isAccepted());
        assertEquals(2, store.historyOrdinal);
        // system + the stored history entry + the follow-up
        assertEquals(3, generation.lastRequest.messages.size());
        assertEquals(WiringDoubles.FIRST_QUESTION.length(), generation.lastRequest.historyChars);
        assertEquals("a follow-up".length(), generation.lastRequest.currentInputChars);
    }

    // ------------------------------------------- the cited sentence reaches the request (T-044)

    @Test
    public void sendPutsTheCitedSentenceIntoTheRequestsCurrentInput() {
        key.configured = true;
        store.nextOrdinal = 2;

        ChatCommandResult result = wiring().send(WiringDoubles.BRANCH_ID, "  a follow-up  ",
                WiringDoubles.QUOTED_LINE, null, WiringDoubles.MODEL, false);

        assertTrue(result.isAccepted());
        // The screen drew the citation, so the request has to carry it too: one framed block, then
        // the question. The citation is escaped by the adapter, so it travels framed rather than raw.
        assertEquals(cited("a follow-up"), lastMessage(generation.lastRequest));
        assertEquals(lastMessage(generation.lastRequest).length(),
                generation.lastRequest.currentInputChars);
        // The citation is the current input's, not a second user turn: system + current input only.
        assertEquals(2, generation.lastRequest.messages.size());
    }

    @Test
    public void aTurnWithoutACitationTravelsAsTheQuestionAlone() {
        key.configured = true;
        store.nextOrdinal = 2;

        ChatCommandResult result = wiring().send(WiringDoubles.BRANCH_ID, "a follow-up", "", null, WiringDoubles.MODEL, false);

        assertTrue(result.isAccepted());
        String latest = lastMessage(generation.lastRequest);
        assertEquals("a follow-up", latest);
        assertEquals("a follow-up".length(), generation.lastRequest.currentInputChars);
        assertFalse(latest, latest.contains("<treelearn_"));
    }

    /**
     * The regression lock this task exists for: sending and re-answering the same question must
     * assemble one identical current input. Two entries that frame the citation differently - or one
     * that frames it and one that drops it - is exactly the drift the task forbids.
     */
    @Test
    public void sendAndRetryAgreeCharacterForCharacterOnTheCurrentInput() {
        key.configured = true;
        store.nextOrdinal = 2;
        assertTrue(wiring().send(WiringDoubles.BRANCH_ID, WiringDoubles.FIRST_QUESTION,
                WiringDoubles.QUOTED_LINE, null, WiringDoubles.MODEL, false).isAccepted());
        String fromSend = lastMessage(generation.lastRequest);

        store.positions.put(WiringDoubles.ANSWER_ID, Message.restore(WiringDoubles.ANSWER_ID,
                WiringDoubles.BRANCH_ID, com.treelearn.core.domain.MessageRole.AI, 1, null, "",
                WiringDoubles.NOW, null, null));
        store.conversation = new ChatStore.Conversation("", Arrays.asList(
                user(0, WiringDoubles.FIRST_QUESTION), answer(1, WiringDoubles.ANSWER_ID, null)));
        DomainResult<GenerationCommand> decided = GenerationCommand.create(WiringDoubles.BRANCH_ID,
                WiringDoubles.ANSWER_ID, AnswerPolicy.PURPOSE, AnswerPolicy.version(),
                WiringDoubles.MODEL, false, WiringDoubles.NOW);
        assertTrue(decided.isOk());
        store.retryResult = decided;

        assertTrue(wiring().retry(WiringDoubles.ANSWER_ID, WiringDoubles.MODEL, false,
                WiringDoubles.NOW).isAccepted());

        assertEquals(0, store.historyOrdinal);
        assertEquals("both entries must assemble the same current input for the same citation",
                fromSend, lastMessage(generation.lastRequest));
        assertEquals(cited(WiringDoubles.FIRST_QUESTION), lastMessage(generation.lastRequest));
    }

    private static String lastMessage(DeepSeekRequest request) {
        return request.messages.get(request.messages.size() - 1).content;
    }

    /** The current input a question with {@link WiringDoubles#QUOTED_LINE} cited must assemble to. */
    private static String cited(String question) {
        // 信封刻意在这里写死，而不是引用适配器的常量：用被测代码自己的常量构造期望值，
        // 断言就变成自证。常量与字面量的一致性由 :ai 的线格式用例负责钉死。
        return "<treelearn_quote>\n" + WiringDoubles.QUOTED_LINE + "\n</treelearn_quote>\n" + question;
    }
}
