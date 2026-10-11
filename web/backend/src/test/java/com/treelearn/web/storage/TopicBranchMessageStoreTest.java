package com.treelearn.web.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.treelearn.core.domain.AnswerRevision;
import com.treelearn.core.domain.Branch;
import com.treelearn.core.domain.GenerationModel;
import com.treelearn.core.domain.Message;
import com.treelearn.core.domain.Topic;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * T-2026-10-10-010: topic / branch / message round-trips, the schema constraints the stores lean on,
 * and the tree navigators (children, path to root, descendants). Real sqlite-jdbc, foreign keys on.
 */
class TopicBranchMessageStoreTest extends AbstractStoreTest {

    @Test
    void createWritesTheTopicAndItsRootBranchAtomically() {
        TopicStore.Created created = topics.create(T0);

        Topic read = topics.get(created.topic.id).orElseThrow();
        assertEquals(created.topic.id, read.id);
        assertEquals(T0, read.createdAt);
        assertNull(read.lastVisitedBranchId);
        assertEquals(1, branches.listForTopic(created.topic.id).size());
    }

    @Test
    void theRootBranchCarriesNoProvenance() {
        TopicStore.Created created = topics.create(T0);
        Branch root = branches.get(created.rootBranch.id).orElseThrow();
        assertEquals(created.topic.id, root.topicId);
        assertTrue(root.isRoot(), "the root branch must have no parent");
        assertNull(root.source, "a root branch must carry no provenance");
    }

    @Test
    void aSecondRootBranchInOneTopicIsRefused() {
        TopicStore.Created created = topics.create(T0);
        assertThrows(StorageException.class, () -> branches.createRoot(created.topic.id, T0 + 1),
                "the partial unique index admits exactly one root per topic");
    }

    @Test
    void twoTopicsMayEachCarryTheirOwnSingleRoot() {
        TopicStore.Created a = topics.create(T0);
        TopicStore.Created b = topics.create(T0 + 1);
        assertTrue(branches.get(a.rootBranch.id).isPresent());
        assertTrue(branches.get(b.rootBranch.id).isPresent());
    }

    @Test
    void aRootBranchCarryingProvenanceColumnsIsRefusedByTheStore() {
        TopicStore.Created created = topics.create(T0);
        Message answer = messages.appendAiPosition(created.rootBranch.id, 0, T0);
        AnswerRevision revision = revisions.insert(answer.id, "", "answer", GenerationModel.FLASH,
                false, LEARNING_ANSWER_POLICY, T0);

        assertThrows(StorageException.class, () -> jdbc.update(
                "INSERT INTO " + DesktopSchema.Branches.TABLE + "("
                        + DesktopSchema.Branches.ID + ", " + DesktopSchema.Branches.TOPIC_ID + ", "
                        + DesktopSchema.Branches.SOURCE_MESSAGE_ID + ", "
                        + DesktopSchema.Branches.SOURCE_REVISION_ID + ", "
                        + DesktopSchema.Branches.CREATED_AT + ", " + DesktopSchema.Branches.UPDATED_AT
                        + ") VALUES (?, ?, ?, ?, ?, ?)",
                s -> {
                    s.setString(1, UUID.randomUUID().toString());
                    s.setString(2, created.topic.id);
                    s.setString(3, answer.id);
                    s.setString(4, revision.id);
                    s.setLong(5, T0);
                    s.setLong(6, T0);
                }), "a root row with provenance must be refused by the T-009 schema CHECK");
    }

    @Test
    void theDerivedDisplayTitleFallsBackToTheRootQuestion() {
        TopicStore.Created created = topics.create(T0);
        messages.appendUserMessage(created.rootBranch.id, 0, "第一个问题", null, T0);

        TopicStore.Summary summary = topics.listOrderedByRecency().get(0);
        assertNull(summary.rootTitle);
        assertEquals("第一个问题", summary.rootQuestion);
        assertEquals("第一个问题", summary.displayTitle());

        branches.renameTitle(created.rootBranch.id, "主题标题", T0 + 1);
        assertEquals("主题标题", topics.listOrderedByRecency().get(0).displayTitle());
    }

    @Test
    void touchLastVisitedRecordsTheBranchAndTheStamp() {
        TopicStore.Created created = topics.create(T0);
        topics.touchLastVisited(created.topic.id, created.rootBranch.id, T0 + 5);

        Topic read = topics.get(created.topic.id).orElseThrow();
        assertEquals(created.rootBranch.id, read.lastVisitedBranchId);
        assertEquals(T0 + 5, read.updatedAt);
    }

    @Test
    void deleteCascadeRemovesTheWholeTree() {
        TopicStore.Created created = topics.create(T0);
        Branch child = forkChild(created.topic.id, created.rootBranch, T0 + 10);

        TopicStore.DeletionCounts counts = topics.deleteCascade(created.topic.id);
        assertEquals(1, counts.topics);
        assertEquals(2, counts.branches);
        assertEquals(1, counts.messages);
        assertFalse(topics.get(created.topic.id).isPresent());
        assertFalse(branches.get(child.id).isPresent());
    }

    @Test
    void listForTopicReturnsBranchesInCreationOrderAndTheNavigatorsAgree() {
        TopicStore.Created created = topics.create(T0);
        Branch first = forkChild(created.topic.id, created.rootBranch, T0 + 1_000);
        Branch second = forkChild(created.topic.id, created.rootBranch, T0 + 2_000);
        // The grandchild is created last: a flat creation-order list places it after "second", while a
        // pre-order tree walk would put it right after its parent "first". The two orders must differ
        // here so this test pins the flat list Android's BranchDao.findByTopic returns.
        Branch grandchild = forkChild(created.topic.id, first, T0 + 3_000);

        assertEquals(List.of(created.rootBranch.id, first.id, second.id, grandchild.id),
                ids(branches.listForTopic(created.topic.id)),
                "listForTopic is a flat created_at ASC list; the tree is assembled by the caller");
        assertEquals(List.of(first.id, second.id), ids(branches.children(created.rootBranch.id)));
        assertEquals(List.of(created.rootBranch.id, first.id, grandchild.id),
                ids(branches.pathToRoot(grandchild.id)));
        assertEquals(List.of(first.id, grandchild.id), ids(branches.descendants(first.id)));
    }

    @Test
    void renameCollapseArchiveAndRestoreArePersisted() {
        TopicStore.Created created = topics.create(T0);
        String rootId = created.rootBranch.id;

        branches.renameTitle(rootId, "标题", T0 + 1);
        branches.setCollapsed(rootId, true, T0 + 2);
        Branch archived = branches.archive(rootId, T0 + 3);
        assertTrue(archived.isArchived());

        Branch read = branches.get(rootId).orElseThrow();
        assertEquals("标题", read.title());
        assertTrue(read.isCollapsed());
        assertEquals(Long.valueOf(T0 + 3), read.archivedAt());

        branches.restore(rootId, T0 + 4);
        assertFalse(branches.get(rootId).orElseThrow().isArchived());
    }

    @Test
    void clearingTheTitleWritesNull() {
        TopicStore.Created created = topics.create(T0);
        branches.renameTitle(created.rootBranch.id, "临时", T0 + 1);
        branches.renameTitle(created.rootBranch.id, null, T0 + 2);
        assertNull(branches.get(created.rootBranch.id).orElseThrow().title());
    }

    @Test
    void aForkedChildReadsBackWithItsProvenanceAndBirthSnapshot() {
        TopicStore.Created created = topics.create(T0);
        Branch child = forkChild(created.topic.id, created.rootBranch, T0 + 10);

        Branch read = branches.get(child.id).orElseThrow();
        assertEquals(created.rootBranch.id, read.parentBranchId);
        assertEquals(child.source.sourceRevisionId, read.source.sourceRevisionId);
        assertEquals(child.source.contextSnapshotId, read.source.contextSnapshotId);
    }

    private static List<String> ids(List<Branch> branchList) {
        return branchList.stream().map(branch -> branch.id).toList();
    }
}
