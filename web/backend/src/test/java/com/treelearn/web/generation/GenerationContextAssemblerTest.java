package com.treelearn.web.generation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.treelearn.ai.prompt.PromptBudget;
import com.treelearn.core.domain.AnswerRevision;
import com.treelearn.core.domain.Branch;
import com.treelearn.core.domain.BranchAnchor;
import com.treelearn.core.domain.BranchSource;
import com.treelearn.core.domain.ContextPlan;
import com.treelearn.core.domain.ContextSnapshot;
import com.treelearn.core.domain.DomainErrorCode;
import com.treelearn.core.domain.DomainResult;
import com.treelearn.core.domain.GenerationModel;
import com.treelearn.core.domain.GenerationState;
import com.treelearn.core.domain.Message;
import com.treelearn.core.domain.PromptPurpose;
import com.treelearn.web.storage.AnswerRevisionStore;
import com.treelearn.web.storage.ArtifactStore;
import com.treelearn.web.storage.BranchStore;
import com.treelearn.web.storage.DatabaseMigrator;
import com.treelearn.web.storage.DesktopDatabase;
import com.treelearn.web.storage.JdbcSupport;
import com.treelearn.web.storage.MessageStore;
import com.treelearn.web.storage.SnapshotStore;
import com.treelearn.web.storage.TopicStore;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * T-2026-10-10-023: the desktop mirror of Android's context assembly (contract §5.10; the Android
 * references {@code SqliteChatStore.history}, {@code SnapshotHistoryMerger.merge} and
 * {@code ChatWiring.historyForRequest} are named at each assertion). Runs on the real sqlite-jdbc
 * engine; every plan is a {@link ContextPlan}, produced by the shared constructors only.
 */
class GenerationContextAssemblerTest {

    private static final long T0 = 1_752_000_000_000L;
    private static final String LEARNING_ANSWER_POLICY = "tl-learning-answer/1.1.0";

    @TempDir
    Path dataDir;

    JdbcSupport jdbc;
    TopicStore topics;
    BranchStore branches;
    MessageStore messages;
    AnswerRevisionStore revisions;
    SnapshotStore snapshots;
    ArtifactStore artifacts;

    @BeforeEach
    void setUp() {
        new DatabaseMigrator().migrate(dataDir.resolve("treelearn.db"));
        jdbc = new JdbcSupport(new DesktopDatabase(dataDir));
        topics = new TopicStore(jdbc);
        branches = new BranchStore(jdbc);
        messages = new MessageStore(jdbc);
        revisions = new AnswerRevisionStore(jdbc);
        snapshots = new SnapshotStore(jdbc);
        artifacts = new ArtifactStore(jdbc);
    }

    private GenerationContextAssembler assembler(int capacityChars) {
        return new GenerationContextAssembler(messages, revisions, snapshots, artifacts,
                new PromptBudget(capacityChars, 0));
    }

    /** Appends one complete round at the branch's next two ordinals and returns its revision. */
    private AnswerRevision appendRound(String branchId, int questionOrdinal, String question,
            String reasoning, String answer, long now) {
        messages.appendUserMessage(branchId, questionOrdinal, question, null, now);
        Message position = messages.appendAiPosition(branchId, questionOrdinal + 1, now);
        AnswerRevision revision = revisions.insert(position.id, reasoning, answer,
                GenerationModel.FLASH, false, LEARNING_ANSWER_POLICY, now);
        messages.setActiveRevision(position.id, revision.id);
        return revision;
    }

    @Test
    void aRootBranchReplaysItsOwnTurnsAndExcludesTheCurrentQuestion() {
        Branch root = topics.create(T0).rootBranch;
        appendRound(root.id, 0, "第一个问题", "推理一", "回答一", T0);

        ContextPlan plan = assembler(60000).assemble(root.id, 2, 0).value();

        assertEquals(2, plan.entries().size());
        assertEquals(ContextPlan.EntryKind.USER_MESSAGE, plan.entries().get(0).kind);
        assertEquals("第一个问题", plan.entries().get(0).userText);
        assertEquals(ContextPlan.EntryKind.STABLE_REVISION, plan.entries().get(1).kind);
        assertEquals("推理一", plan.entries().get(1).reasoningText);
        assertEquals("回答一", plan.entries().get(1).answerText);

        ContextPlan beforeQuestion = assembler(60000).assemble(root.id, 1, 0).value();
        assertEquals(1, beforeQuestion.entries().size(),
                "history is the rows strictly before the current question's ordinal");
    }

    @Test
    void aBirthSnapshotStaysFrozenWhenTheParentResetsItsDisplayedRevision() {
        TopicStore.Created created = topics.create(T0);
        Branch parent = created.rootBranch;
        messages.appendUserMessage(parent.id, 0, "上游问题", null, T0);
        Message parentAnswer = messages.appendAiPosition(parent.id, 1, T0);
        AnswerRevision original = revisions.insert(parentAnswer.id, "上游推理", "上游回答",
                GenerationModel.FLASH, false, LEARNING_ANSWER_POLICY, T0);
        messages.setActiveRevision(parentAnswer.id, original.id);

        Branch child = fork(created.topic.id, parent, parentAnswer.id, original.id, T0 + 1);

        // The parent resets what it displays; the child's birth version must not move (contract §8.5).
        AnswerRevision reset = revisions.insert(parentAnswer.id, "改写推理", "改写回答",
                GenerationModel.FLASH, false, LEARNING_ANSWER_POLICY, T0 + 2);
        messages.setActiveRevision(parentAnswer.id, reset.id);

        ContextPlan plan = assembler(60000).assemble(child.id, 0, 0).value();

        assertEquals(2, plan.entries().size());
        assertEquals("上游问题", plan.entries().get(0).userText);
        assertEquals("上游回答", plan.entries().get(1).answerText,
                "the child replays the revision its snapshot pinned, not the parent's new one");
        assertFalse(plan.entries().stream().anyMatch(entry -> "改写回答".equals(entry.answerText)),
                "a parent reset must never leak into a frozen child context");
    }

    @Test
    void aSnapshotSummarySourceIsRefusedRatherThanInvented() {
        TopicStore.Created created = topics.create(T0);
        Branch parent = created.rootBranch;
        messages.appendUserMessage(parent.id, 0, "上游问题", null, T0);
        Message parentAnswer = messages.appendAiPosition(parent.id, 1, T0);
        AnswerRevision original = revisions.insert(parentAnswer.id, "上游推理", "上游回答",
                GenerationModel.FLASH, false, LEARNING_ANSWER_POLICY, T0);
        messages.setActiveRevision(parentAnswer.id, original.id);

        String snapshotId = UUID.randomUUID().toString();
        BranchSource source = BranchSource.fromOperation(parent.id, parentAnswer.id, snapshotId,
                GenerationState.STABLE, original.id, BranchAnchor.wholeAnswer()).value();
        Branch child = branches.createChild(created.topic.id, source, T0 + 1);
        ContextSnapshot snapshot = snapshots.insert(child.id, List.of(
                ContextSnapshot.Source.summary(UUID.randomUUID().toString())), T0 + 1);
        branches.attachContextSnapshot(child.id, snapshot.id);

        DomainResult<ContextPlan> plan = assembler(60000).assemble(child.id, 0, 0);

        assertTrue(plan.isFailure());
        assertEquals(DomainErrorCode.CONTEXT_SUMMARY_MISSING, plan.error().code,
                "the desktop has no summary read path yet, so it refuses rather than puts words in "
                        + "the upstream conversation");
    }

    @Test
    void olderRoundsAreReplacedByOneCompressionSummaryAtTheAndroidSplit() {
        TopicStore.Created created = topics.create(T0);
        Branch branch = created.rootBranch;
        List<AnswerRevision> roundRevisions = new ArrayList<>();
        for (int round = 0; round <= 10; round++) {
            roundRevisions.add(appendRound(branch.id, round * 2, "问题" + round, "推理" + round,
                    "回答" + round, T0 + round));
        }
        AnswerRevision latest = roundRevisions.get(10);
        artifacts.insert(ArtifactStore.Artifact.create("context_compression", branch.id,
                PromptPurpose.CONTEXT_COMPRESSION, "tl-context-compression/1.0.0",
                "{\"summary\":\"早前摘要\"}", latest.id, "digest-1", T0 + 11));

        ContextPlan plan = assembler(60000).assemble(branch.id, 22, 0).value();

        assertEquals(ContextPlan.EntryKind.SUMMARY, plan.entries().get(0).kind,
                "older rounds become one summary entry at the front");
        assertEquals("早前摘要", plan.entries().get(0).summaryText);
        assertEquals(21, plan.entries().size(),
                "ten complete rounds are kept after the summary (1 summary + 2 * 10 turns)");
        ContextPlan.Entry last = plan.entries().get(plan.entries().size() - 1);
        assertEquals(ContextPlan.EntryKind.STABLE_REVISION, last.kind);
        assertEquals(latest.id, last.refId, "the retained window ends at the newest revision");
    }

    @Test
    void overBudgetContextIsRefusedWithTheSharedCode() {
        Branch root = topics.create(T0).rootBranch;
        messages.appendUserMessage(root.id, 0, "这一句话比容量长得多", null, T0);

        DomainResult<ContextPlan> plan = assembler(8).assemble(root.id, 1, 0);

        assertTrue(plan.isFailure());
        assertEquals(DomainErrorCode.CONTEXT_EXCEEDS_BUDGET, plan.error().code);
    }

    @Test
    void anUnresolvableSnapshotSourceIsRefused() {
        TopicStore.Created created = topics.create(T0);
        Branch parent = created.rootBranch;
        Message question = messages.appendUserMessage(parent.id, 0, "上游问题", null, T0);
        Message parentAnswer = messages.appendAiPosition(parent.id, 1, T0);
        AnswerRevision original = revisions.insert(parentAnswer.id, "上游推理", "上游回答",
                GenerationModel.FLASH, false, LEARNING_ANSWER_POLICY, T0);
        messages.setActiveRevision(parentAnswer.id, original.id);

        String snapshotId = UUID.randomUUID().toString();
        BranchSource source = BranchSource.fromOperation(parent.id, parentAnswer.id, snapshotId,
                GenerationState.STABLE, original.id, BranchAnchor.wholeAnswer()).value();
        Branch child = branches.createChild(created.topic.id, source, T0 + 1);
        ContextSnapshot snapshot = snapshots.insert(child.id, List.of(
                ContextSnapshot.Source.userMessage(UUID.randomUUID().toString())), T0 + 1);
        branches.attachContextSnapshot(child.id, snapshot.id);

        DomainResult<ContextPlan> plan = assembler(60000).assemble(child.id, 0, 0);

        assertTrue(plan.isFailure());
        assertEquals(DomainErrorCode.CONTEXT_SOURCE_MISSING, plan.error().code,
                "a snapshot source the store cannot resolve stops the whole plan");
        assertNotNull(question.id);
    }

    private Branch fork(String topicId, Branch parent, String answerMessageId, String revisionId,
            long now) {
        String snapshotId = UUID.randomUUID().toString();
        BranchSource source = BranchSource.fromOperation(parent.id, answerMessageId, snapshotId,
                GenerationState.STABLE, revisionId, BranchAnchor.wholeAnswer()).value();
        Branch child = branches.createChild(topicId, source, now);
        ContextSnapshot snapshot = snapshots.insert(child.id, List.of(
                ContextSnapshot.Source.userMessage(firstUserMessage(parent.id)),
                ContextSnapshot.Source.stableRevision(revisionId)), now);
        branches.attachContextSnapshot(child.id, snapshot.id);
        return child;
    }

    private String firstUserMessage(String branchId) {
        for (MessageStore.Position position : messages.listForBranch(branchId)) {
            if (position.message.isUser()) {
                return position.message.id;
            }
        }
        throw new IllegalStateException("the fork fixture wrote no user message");
    }
}
