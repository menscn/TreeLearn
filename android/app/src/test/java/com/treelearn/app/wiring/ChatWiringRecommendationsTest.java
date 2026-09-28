package com.treelearn.app.wiring;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.treelearn.app.artifact.HomeDerivedPort;
import com.treelearn.app.ui.ChatData;
import com.treelearn.app.ui.Recommendation;
import com.treelearn.core.domain.AnswerRevision;
import com.treelearn.core.domain.Branch;
import com.treelearn.core.domain.DomainResult;
import com.treelearn.core.domain.Message;

import org.junit.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * The follow-up strip's read path, pinned end to end without a device.
 *
 * <p><strong>What this class exists for.</strong> The strip has two halves that live in different
 * files: {@code DerivedService} writes and reads back the artifact, {@code ChatWiring.read} decides
 * whether the stored strip belongs to the conversation being shown. The bug this pins was a read
 * path that returned {@code emptyList} unconditionally while the write path was already complete, so
 * the row rendered nothing and no test noticed. Every case below therefore asserts against the
 * <em>snapshot</em> the presenter consumes, not against the store: what the screen can render is the
 * only thing that decides whether the strip is fixed.
 *
 * <p>{@link DerivedService} is a real instance over a real {@code TreeLearnDatabaseHelper} only in
 * the instrumented suite; here the port is a fake that answers the one method {@code read} calls, so
 * the wiring's own decision - which revision the strip is bound to, and whether it is shown at all -
 * is the thing under test.
 */
public class ChatWiringRecommendationsTest {

    private static final String BRANCH_ID = WiringDoubles.BRANCH_ID;
    private static final List<String> QUESTIONS = Arrays.asList(
            "能举个具体例子吗？", "这和工程落地有什么关系？", "有哪些常见误区？");

    private final WiringDoubles.Key key = new WiringDoubles.Key();
    private final WiringDoubles.Chats store = new WiringDoubles.Chats();
    private final WiringDoubles.Generations generation = new WiringDoubles.Generations();
    private final FakeDerived derived = new FakeDerived();

    private ChatWiring wiring() {
        return new ChatWiring(store, generation, key,
                Clock.fixed(Instant.ofEpochMilli(WiringDoubles.NOW), ZoneOffset.UTC),
                PromptLimits.budget(), derived, () -> true);
    }

    private static ChatStore.Position answer(String messageId, AnswerRevision revision) {
        return new ChatStore.Position(Message.restore(messageId, BRANCH_ID,
                com.treelearn.core.domain.MessageRole.AI, 1, null, "", WiringDoubles.NOW,
                revision == null ? null : revision.id, null), revision, revision == null ? 0 : 1, 0,
                false, "", "");
    }

    private static AnswerRevision revision(String answer) {
        DomainResult<AnswerRevision> created = AnswerRevision.create(WiringDoubles.ANSWER_ID, 1, "",
                answer, WiringDoubles.MODEL, false, AnswerPolicy.version(), WiringDoubles.NOW);
        assertTrue("the fixture revision must be valid", created.isOk());
        return created.value();
    }

    /**
     * A stored conversation with one stable answer, the branch row the strip is scoped to, and the
     * revision the newest row displays.
     *
     * <p>The branch row is part of the fixture rather than optional: the strip is scoped to the
     * branch, so a read that carries no branch row is not a conversation the strip could belong to.
     *
     * @return the revision id the store will show, so a case can bind a strip to it
     */
    private String conversationWithStableAnswer() {
        AnswerRevision stable = revision("the answer");
        store.branch = Branch.root(WiringDoubles.TOPIC_ID, WiringDoubles.NOW);
        store.conversation = new ChatStore.Conversation("", Arrays.asList(
                new ChatStore.Position(Message.user(BRANCH_ID, 0, WiringDoubles.FIRST_QUESTION,
                        WiringDoubles.QUOTED_LINE, WiringDoubles.NOW), null, 0, 0, false, "", ""),
                answer(WiringDoubles.ANSWER_ID, stable)), store.branch,
                Collections.<Message>emptyList());
        return stable.id;
    }

    /** The regression: a derived strip bound to the displayed answer is what the snapshot carries. */
    @Test
    public void readCarriesTheStripBoundToTheDisplayedRevision() {
        String displayed = conversationWithStableAnswer();
        derived.stored = QUESTIONS;

        ChatData.Snapshot snapshot = wiring().read(BRANCH_ID);

        assertTrue("the store is asked for the displayed revision", derived.asked);
        assertEquals("and for that exact revision", displayed, derived.askedRevision);
        assertEquals("every stored question reaches the row", 3, snapshot.answerRecommendations.size());
        assertEquals(QUESTIONS.get(0), snapshot.answerRecommendations.get(0).question);
        assertEquals("the strip names the version it describes", displayed,
                snapshot.recommendationsRevisionId);
        for (Recommendation item : snapshot.answerRecommendations) {
            assertEquals(Recommendation.Kind.FOLLOW_UP, item.kind);
        }
    }

    /** A strip derived for another version must not be shown over this one. */
    @Test
    public void readHidesAStripWhoseBaselineMovedOn() {
        conversationWithStableAnswer();
        // The store was asked for the displayed revision and answered "no row for that baseline",
        // which is the same shape a revision switch produces.
        derived.stored = Collections.emptyList();

        ChatData.Snapshot snapshot = wiring().read(BRANCH_ID);

        assertTrue("a strip for another version reads as no strip",
                snapshot.answerRecommendations.isEmpty());
        assertEquals("", snapshot.recommendationsRevisionId);
    }

    /** With no derived service wired there is nothing to read, and that must not raise. */
    @Test
    public void readWithoutADerivedServiceShowsNoStrip() {
        conversationWithStableAnswer();
        ChatWiring withoutDerived = new ChatWiring(store, generation, key,
                Clock.fixed(Instant.ofEpochMilli(WiringDoubles.NOW), ZoneOffset.UTC),
                PromptLimits.budget());

        ChatData.Snapshot snapshot = withoutDerived.read(BRANCH_ID);

        assertTrue(snapshot.answerRecommendations.isEmpty());
        assertEquals("", snapshot.recommendationsRevisionId);
    }

    /** A conversation with no stable answer has no baseline, so the store is never asked. */
    @Test
    public void readAsksTheStoreOnlyWhenAnAnswerExists() {
        store.conversation = new ChatStore.Conversation("", Arrays.asList(
                new ChatStore.Position(Message.user(BRANCH_ID, 0, WiringDoubles.FIRST_QUESTION,
                        WiringDoubles.QUOTED_LINE, WiringDoubles.NOW), null, 0, 0, false, "", "")));

        ChatData.Snapshot snapshot = wiring().read(BRANCH_ID);

        assertTrue(snapshot.answerRecommendations.isEmpty());
        assertFalse("a question-only conversation offers no revision to bind a strip to",
                derived.asked);
    }

    /** The port under test: a fake that records which revision it was asked for, answers what the
     * test stored. {@code DerivedService} is final, and rightly so - the port is the seam. */
    private static final class FakeDerived implements HomeDerivedPort {

        List<String> stored = Collections.emptyList();
        boolean asked;
        String askedRevision;

        @Override
        public com.treelearn.app.artifact.ArtifactOutcome rootTitle(
                com.treelearn.core.domain.Branch branch, String question,
                com.treelearn.core.domain.GenerationModel model) {
            throw new UnsupportedOperationException("the chat read path does not derive titles");
        }

        @Override
        public List<String> homeRecommendations(String topicId,
                List<com.treelearn.app.artifact.TreeOutline.Entry> outline,
                com.treelearn.core.domain.GenerationModel model) {
            throw new UnsupportedOperationException("the chat read path does not derive a home strip");
        }

        @Override
        public List<String> answerRecommendations(String branchId, String basedOnRevisionId) {
            asked = true;
            askedRevision = basedOnRevisionId;
            return stored;
        }
    }
}
