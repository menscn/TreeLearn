package com.treelearn.core.domain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;

/**
 * Building the vendor-neutral context plan of one request (technical design 9.2, 9.4).
 *
 * <p>Two halves meet here and must not be confused: the <em>birth</em> half is read through the
 * immutable snapshot and keeps whatever revision it pinned, while the branch's <em>own</em> turns
 * follow the revision each position currently displays. The cases below pin both halves, and pin
 * that a context that cannot be assembled is refused rather than quietly shortened.
 */
public class BuildContextPlanUseCaseTest {

    private static final String TOPIC_ID = "a1b2c3d4-e5f6-4a7b-8c9d-0e1f2a3b4c5d";
    private static final String BRANCH_ID = "b2c3d4e5-f6a7-4b8c-9d0e-1f2a3b4c5d6e";
    private static final String PARENT_QUESTION_ID = "c3d4e5f6-a7b8-4c9d-8e1f-2a3b4c5d6e7f";
    private static final String PARENT_ANSWER_ID = "d4e5f6a7-b8c9-4da0-9f2a-3b4c5d6e7f80";
    private static final String OWN_ANSWER_ID = "e5f6a7b8-c9d0-4eb1-8a3b-4c5d6e7f8091";
    private static final String REVISION_1 = "f6a7b8c9-d0e1-4fc2-9b4c-5d6e7f8091a2";
    private static final String REVISION_2 = "a7b8c9d0-e1f2-40d3-8c5d-6e7f8091a2b3";
    private static final String SUMMARY_ID = "b8c9d0e1-f2a3-41e4-9d6e-7f8091a2b3c4";
    private static final String POLICY = "tl-learning-answer/1.0.0";
    private static final long T0 = 1_700_000_000_000L;
    private static final BuildContextPlanUseCase.Budget GENEROUS =
            new BuildContextPlanUseCase.Budget(100_000, 1);

    private static List<Message> noMessages() {
        return Collections.<Message>emptyList();
    }

    private static AnswerRevision revision(String id, String reasoning, String answer) {
        return AnswerRevision.restore(id, PARENT_ANSWER_ID, 1, reasoning, answer,
                GenerationModel.PRO, true, POLICY, T0);
    }

    private static DomainResult<ContextPlan> build(ContextSnapshot snapshot, List<Message> history,
            BuildContextPlanUseCase.Sources sources, BuildContextPlanUseCase.Budget budget) {
        return BuildContextPlanUseCase.build(snapshot, history, sources, budget);
    }

    private static ContextPlan plan(ContextSnapshot snapshot, List<Message> history,
            BuildContextPlanUseCase.Sources sources, BuildContextPlanUseCase.Budget budget) {
        DomainResult<ContextPlan> result = build(snapshot, history, sources, budget);
        assertTrue("expected a plan, got " + result, result.isOk());
        return result.value();
    }

    private static String render(List<ContextPlan.Entry> entries) {
        StringBuilder out = new StringBuilder();
        for (ContextPlan.Entry entry : entries) {
            out.append(entry.kind).append('|').append(entry.refId).append('|')
                    .append(entry.userText).append('|').append(entry.reasoningText).append('|')
                    .append(entry.answerText).append('|').append(entry.summaryText).append('\n');
        }
        return out.toString();
    }

    /** The caller's view of the rows the snapshot pins; the branch's own turns never come from here. */
    private static final class StoredSources implements BuildContextPlanUseCase.Sources {

        private final Map<String, String> userMessages = new LinkedHashMap<String, String>();
        private final Map<String, AnswerRevision> revisions =
                new LinkedHashMap<String, AnswerRevision>();
        private final Map<String, String> summaries = new LinkedHashMap<String, String>();

        StoredSources withUserMessage(String id, String text) {
            userMessages.put(id, text);
            return this;
        }

        StoredSources withRevision(AnswerRevision revision) {
            revisions.put(revision.id, revision);
            return this;
        }

        StoredSources withSummary(String id, String text) {
            summaries.put(id, text);
            return this;
        }

        @Override
        public String userMessage(String messageId) {
            return userMessages.get(messageId);
        }

        @Override
        public AnswerRevision revision(String revisionId) {
            return revisions.get(revisionId);
        }

        @Override
        public String summary(String summaryId) {
            return summaries.get(summaryId);
        }
    }

    @Test
    public void theBirthHalfIsPinnedToTheSnapshotEvenAfterTheParentMovesOn() {
        ContextSnapshot snapshot = ContextSnapshot.create(BRANCH_ID, Arrays.asList(
                ContextSnapshot.Source.userMessage(PARENT_QUESTION_ID),
                ContextSnapshot.Source.stableRevision(REVISION_1)), T0);
        StoredSources sources = new StoredSources()
                .withUserMessage(PARENT_QUESTION_ID, "为什么天是蓝的？")
                .withRevision(revision(REVISION_1, "短波散射更强", "因为瑞利散射把蓝光散得更开。"))
                .withRevision(revision(REVISION_2, "另一套推理", "重写后的回答，绝不能进入出生上下文。"));

        List<ContextPlan.Entry> before = plan(snapshot, noMessages(), sources, GENEROUS).entries();

        // The parent keeps moving after the child was born: its answer position switches to the
        // second revision, the branch is renamed and gains another stable answer.
        Message position = Message.restore(PARENT_ANSWER_ID, BRANCH_ID, MessageRole.AI, 1, null, T0,
                REVISION_1, null);
        assertTrue("the fixture must be able to switch the displayed revision",
                position.activateRevision(REVISION_2).isOk());
        Branch parent = Branch.root(TOPIC_ID, T0);
        parent.setTitle("重命名后的父分支", T0 + 1);
        parent.onStableAnswer(T0 + 2);

        List<ContextPlan.Entry> after = plan(snapshot, noMessages(), sources, GENEROUS).entries();

        assertEquals("the plan is exactly what it was at birth", render(before), render(after));
        assertEquals(2, after.size());
        assertEquals(ContextPlan.EntryKind.USER_MESSAGE, after.get(0).kind);
        assertEquals("为什么天是蓝的？", after.get(0).userText);
        assertEquals(ContextPlan.EntryKind.STABLE_REVISION, after.get(1).kind);
        assertEquals(REVISION_1, after.get(1).refId);
        assertEquals("短波散射更强", after.get(1).reasoningText);
        assertFalse("the parent's newer answer never leaks into the child's context",
                render(after).contains("重写后的回答"));
    }

    @Test
    public void theBranchesOwnTurnFollowsTheRevisionThatPositionDisplays() {
        ContextSnapshot snapshot = ContextSnapshot.create(BRANCH_ID,
                Arrays.asList(ContextSnapshot.Source.stableRevision(REVISION_1)), T0);
        StoredSources sources = new StoredSources()
                .withRevision(revision(REVISION_1, "第一次推理", "第一版回答。"))
                .withRevision(revision(REVISION_2, "重置后的推理", "重写后的回答。"));
        Message ownAnswer = Message.restore(OWN_ANSWER_ID, BRANCH_ID, MessageRole.AI, 1, null, T0,
                REVISION_1, null);
        List<Message> history = Arrays.asList(Message.user(BRANCH_ID, 0, "继续追问", T0), ownAnswer);

        ContextPlan first = plan(snapshot, history, sources, GENEROUS);
        assertEquals("the plan is [birth revision, own question, own answer]", 3,
                first.entries().size());
        assertEquals("第一版回答。", first.entries().get(2).answerText);

        assertTrue(ownAnswer.activateRevision(REVISION_2).isOk());
        ContextPlan second = plan(snapshot, history, sources, GENEROUS);

        assertEquals(REVISION_2, second.entries().get(2).refId);
        assertEquals("重写后的回答。", second.entries().get(2).answerText);
        assertEquals("the pinned birth source above is untouched by that switch", REVISION_1,
                second.entries().get(0).refId);
        assertEquals("第一版回答。", second.entries().get(0).answerText);
    }

    @Test
    public void thePlanIsOrderedBirthFirstThenThisBranchesOwnTurns() {
        ContextSnapshot snapshot = ContextSnapshot.create(BRANCH_ID, Arrays.asList(
                ContextSnapshot.Source.userMessage(PARENT_QUESTION_ID),
                ContextSnapshot.Source.stableRevision(REVISION_1)), T0);
        StoredSources sources = new StoredSources()
                .withUserMessage(PARENT_QUESTION_ID, "出生时的问题")
                .withRevision(revision(REVISION_1, "出生时的推理", "出生时的回答。"))
                .withRevision(revision(REVISION_2, "本分支的推理", "本分支的回答。"));
        Message ownAnswer = Message.restore(OWN_ANSWER_ID, BRANCH_ID, MessageRole.AI, 1, null, T0,
                REVISION_2, null);

        ContextPlan plan = plan(snapshot,
                Arrays.asList(Message.user(BRANCH_ID, 0, "本分支的追问", T0), ownAnswer),
                sources, GENEROUS);

        assertEquals(4, plan.entries().size());
        assertEquals(ContextPlan.EntryKind.USER_MESSAGE, plan.entries().get(0).kind);
        assertEquals(ContextPlan.EntryKind.STABLE_REVISION, plan.entries().get(1).kind);
        assertEquals(ContextPlan.EntryKind.USER_MESSAGE, plan.entries().get(2).kind);
        assertEquals("本分支的追问", plan.entries().get(2).userText);
        assertEquals(ContextPlan.EntryKind.STABLE_REVISION, plan.entries().get(3).kind);
        assertEquals("本分支的回答。", plan.entries().get(3).answerText);
    }

    @Test
    public void aSourceTheStoreCannotResolveForbidsTheRequest() {
        ContextSnapshot snapshot = ContextSnapshot.create(BRANCH_ID, Arrays.asList(
                ContextSnapshot.Source.userMessage(PARENT_QUESTION_ID),
                ContextSnapshot.Source.stableRevision(REVISION_1)), T0);

        DomainResult<ContextPlan> withoutRevision = build(snapshot, noMessages(),
                new StoredSources().withUserMessage(PARENT_QUESTION_ID, "问题"), GENEROUS);
        assertTrue(withoutRevision.isFailure());
        assertEquals(DomainErrorCode.CONTEXT_SOURCE_MISSING, withoutRevision.error().code);
        assertThrows("a refusal never hands back a partial plan", IllegalStateException.class,
                () -> withoutRevision.value());

        DomainResult<ContextPlan> withoutMessage = build(snapshot, noMessages(),
                new StoredSources().withRevision(revision(REVISION_1, "推理", "回答")), GENEROUS);
        assertTrue(withoutMessage.isFailure());
        assertEquals(DomainErrorCode.CONTEXT_SOURCE_MISSING, withoutMessage.error().code);
    }

    @Test
    public void aSummaryTheCompressionNeverProducedForbidsTheRequestInsteadOfDroppingReasoning() {
        ContextSnapshot snapshot = ContextSnapshot.create(BRANCH_ID, Arrays.asList(
                ContextSnapshot.Source.summary(SUMMARY_ID),
                ContextSnapshot.Source.stableRevision(REVISION_1)), T0);

        DomainResult<ContextPlan> refused = build(snapshot, noMessages(),
                new StoredSources().withRevision(revision(REVISION_1, "近期推理", "近期回答。")),
                GENEROUS);

        assertTrue(refused.isFailure());
        assertEquals(DomainErrorCode.CONTEXT_SUMMARY_MISSING, refused.error().code);
    }

    @Test
    public void aSummaryThatCoversQuestionReasoningAndAnswerTravelsAsOneEntry() {
        ContextSnapshot snapshot = ContextSnapshot.create(BRANCH_ID,
                Arrays.asList(ContextSnapshot.Source.summary(SUMMARY_ID)), T0);
        StoredSources sources = new StoredSources()
                .withSummary(SUMMARY_ID, "问：早期问题。推理：早期推理。结论：早期回答。");

        ContextPlan plan = plan(snapshot, noMessages(), sources, GENEROUS);

        assertEquals(1, plan.entries().size());
        assertEquals(ContextPlan.EntryKind.SUMMARY, plan.entries().get(0).kind);
        assertEquals(SUMMARY_ID, plan.entries().get(0).refId);
        assertEquals("问：早期问题。推理：早期推理。结论：早期回答。",
                plan.entries().get(0).summaryText);
    }

    @Test
    public void theCapacityTheCallerReservedForSystemAndTheQuestionIsNotFree() {
        ContextSnapshot snapshot = ContextSnapshot.create(BRANCH_ID,
                Arrays.asList(ContextSnapshot.Source.stableRevision(REVISION_1)), T0);
        StoredSources sources = new StoredSources()
                .withRevision(revision(REVISION_1, "推理正文", "回答正文。"));
        int contextChars = plan(snapshot, noMessages(), sources, GENEROUS).chars();
        assertTrue("the fixture must produce a non-empty context", contextChars > 0);

        assertTrue("context plus its reservation fits exactly",
                build(snapshot, noMessages(), sources,
                        new BuildContextPlanUseCase.Budget(contextChars + 40, 40)).isOk());

        DomainResult<ContextPlan> refused = build(snapshot, noMessages(), sources,
                new BuildContextPlanUseCase.Budget(contextChars + 40, 41));

        assertTrue(refused.isFailure());
        assertEquals(DomainErrorCode.CONTEXT_EXCEEDS_BUDGET, refused.error().code);
        assertThrows("an over-budget context is refused, never trimmed", IllegalStateException.class,
                () -> refused.value());
    }

    @Test
    public void anAnswerPositionWithoutADisplayedRevisionContributesNoTurn() {
        ContextSnapshot snapshot = ContextSnapshot.create(BRANCH_ID,
                Arrays.asList(ContextSnapshot.Source.stableRevision(REVISION_1)), T0);
        StoredSources sources = new StoredSources()
                .withRevision(revision(REVISION_1, "推理", "回答"));

        ContextPlan plan = plan(snapshot,
                Arrays.asList(Message.user(BRANCH_ID, 0, "本轮问题", T0), Message.ai(BRANCH_ID, 1, T0)),
                sources, GENEROUS);

        assertEquals("a running attempt is not a context source", 2, plan.entries().size());
        assertEquals(ContextPlan.EntryKind.USER_MESSAGE, plan.entries().get(1).kind);
        assertEquals("本轮问题", plan.entries().get(1).userText);
    }

    @Test
    public void anEmptyHistoryIsAValidRequest() {
        ContextSnapshot snapshot = ContextSnapshot.create(BRANCH_ID,
                Arrays.asList(ContextSnapshot.Source.stableRevision(REVISION_1)), T0);

        ContextPlan plan = plan(snapshot, noMessages(),
                new StoredSources().withRevision(revision(REVISION_1, "推理", "回答")), GENEROUS);

        assertEquals(1, plan.entries().size());
        assertTrue(plan.chars() > 0);
    }

    @Test
    public void malformedArgumentsAreCallerBugs() {
        ContextSnapshot snapshot = ContextSnapshot.create(BRANCH_ID,
                Arrays.asList(ContextSnapshot.Source.stableRevision(REVISION_1)), T0);
        StoredSources sources = new StoredSources();

        assertThrows(IllegalArgumentException.class,
                () -> build(null, noMessages(), sources, GENEROUS));
        assertThrows(IllegalArgumentException.class,
                () -> build(snapshot, null, sources, GENEROUS));
        assertThrows(IllegalArgumentException.class,
                () -> build(snapshot, noMessages(), null, GENEROUS));
        assertThrows(IllegalArgumentException.class,
                () -> build(snapshot, noMessages(), sources, null));
        assertThrows(IllegalArgumentException.class,
                () -> build(snapshot, new ArrayList<Message>(Arrays.asList((Message) null)), sources,
                        GENEROUS));
        assertThrows(IllegalArgumentException.class, () -> new BuildContextPlanUseCase.Budget(0, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new BuildContextPlanUseCase.Budget(10, -1));
        assertThrows(IllegalArgumentException.class,
                () -> new BuildContextPlanUseCase.Budget(10, 10));
    }
}