package com.treelearn.web.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.treelearn.core.domain.AnswerRevision;
import com.treelearn.core.domain.GenerationModel;
import com.treelearn.core.domain.Message;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * T-2026-10-10-010: revision numbering, message ordinals and the displayed-revision switch, on real
 * sqlite-jdbc. The store — never the caller — owns the next {@code revision_no} and {@code ordinal}.
 */
class RevisionAndOrdinalStoreTest extends AbstractStoreTest {

    @Test
    void revisionsAreNumberedByTheStoreInAscendingOrder() {
        TopicStore.Created created = topics.create(T0);
        Message answer = messages.appendAiPosition(created.rootBranch.id, 0, T0);

        AnswerRevision first = revisions.insert(answer.id, "", "first", GenerationModel.FLASH, false,
                LEARNING_ANSWER_POLICY, T0);
        AnswerRevision second = revisions.insert(answer.id, "思考", "second", GenerationModel.PRO,
                true, LEARNING_ANSWER_POLICY, T0 + 1);

        assertEquals(1, first.revisionNo);
        assertEquals(2, second.revisionNo);
        assertEquals(List.of(1, 2),
                revisions.listForMessage(answer.id).stream().map(r -> r.revisionNo).toList());
        assertEquals(2, revisions.countForMessage(answer.id));
    }

    @Test
    void aDuplicateRevisionNumberIsRefusedByTheStore() {
        TopicStore.Created created = topics.create(T0);
        Message answer = messages.appendAiPosition(created.rootBranch.id, 0, T0);
        revisions.insert(answer.id, "", "first", GenerationModel.FLASH, false, LEARNING_ANSWER_POLICY,
                T0);

        assertThrows(StorageException.class, () -> jdbc.update(
                "INSERT INTO " + DesktopSchema.AnswerRevisions.TABLE + "("
                        + DesktopSchema.AnswerRevisions.ID + ", "
                        + DesktopSchema.AnswerRevisions.MESSAGE_ID + ", "
                        + DesktopSchema.AnswerRevisions.REVISION_NO + ", "
                        + DesktopSchema.AnswerRevisions.ANSWER_CONTENT + ", "
                        + DesktopSchema.AnswerRevisions.MODEL + ", "
                        + DesktopSchema.AnswerRevisions.THINKING_ENABLED + ", "
                        + DesktopSchema.AnswerRevisions.PROMPT_POLICY_VERSION + ", "
                        + DesktopSchema.AnswerRevisions.CREATED_AT + ") VALUES (?, ?, 1, 'dup', ?, 0, ?, ?)",
                s -> {
                    s.setString(1, UUID.randomUUID().toString());
                    s.setString(2, answer.id);
                    s.setString(3, GenerationModel.FLASH.wireId);
                    s.setString(4, LEARNING_ANSWER_POLICY);
                    s.setLong(5, T0);
                }), "uq_answer_revisions_message_no must refuse a reused revision number");
    }

    @Test
    void theOrdinalMustBeUniqueWithinABranch() {
        TopicStore.Created created = topics.create(T0);
        messages.appendUserMessage(created.rootBranch.id, 0, "q", null, T0);
        assertThrows(StorageException.class,
                () -> messages.appendAiPosition(created.rootBranch.id, 0, T0),
                "uq_messages_branch_ordinal must refuse a reused ordinal");
    }

    @Test
    void nextOrdinalIsOneAboveTheHighestStored() {
        TopicStore.Created created = topics.create(T0);
        String rootId = created.rootBranch.id;
        assertEquals(0, messages.nextOrdinal(rootId));

        messages.appendUserMessage(rootId, 0, "q", null, T0);
        messages.appendAiPosition(rootId, 1, T0);
        assertEquals(2, messages.nextOrdinal(rootId));
    }

    @Test
    void switchingTheActiveRevisionIsReadBackByListForBranch() {
        TopicStore.Created created = topics.create(T0);
        String rootId = created.rootBranch.id;
        Message answer = messages.appendAiPosition(rootId, 0, T0);
        revisions.insert(answer.id, "", "first", GenerationModel.FLASH, false, LEARNING_ANSWER_POLICY,
                T0);
        AnswerRevision second = revisions.insert(answer.id, "思考", "second", GenerationModel.PRO,
                true, LEARNING_ANSWER_POLICY, T0 + 1);

        MessageStore.Position before = messages.listForBranch(rootId).get(0);
        assertFalse(before.hasActiveRevision());
        assertNull(before.answerContent);
        assertNull(before.revisionNo);

        messages.setActiveRevision(answer.id, second.id);

        MessageStore.Position after = messages.listForBranch(rootId).get(0);
        assertTrue(after.hasActiveRevision());
        assertEquals("second", after.answerContent);
        assertEquals("思考", after.reasoningContent);
        assertEquals(2, after.revisionNo);
        assertEquals(second.id, messages.get(answer.id).orElseThrow().activeRevisionId());
        assertEquals(second.id, revisions.findActiveForMessage(answer.id).orElseThrow().id);
    }

    @Test
    void switchingTheActiveRevisionOfAUserPositionIsRefused() {
        TopicStore.Created created = topics.create(T0);
        String rootId = created.rootBranch.id;
        Message answer = messages.appendAiPosition(rootId, 0, T0);
        AnswerRevision revision = revisions.insert(answer.id, "", "answer", GenerationModel.FLASH,
                false, LEARNING_ANSWER_POLICY, T0);
        Message learner = messages.appendUserMessage(rootId, 1, "问题", null, T0);

        assertThrows(StorageException.class,
                () -> messages.setActiveRevision(learner.id, revision.id),
                "only an AI position may display a revision; a USER row must be refused");
        assertNull(messages.get(learner.id).orElseThrow().activeRevisionId(),
                "the refused switch must not have written the row");
    }

    @Test
    void switchingTheActiveRevisionWhileAnAttemptIsRunningIsRefused() {
        TopicStore.Created created = topics.create(T0);
        String rootId = created.rootBranch.id;
        Message answer = messages.appendAiPosition(rootId, 0, T0);
        AnswerRevision first = revisions.insert(answer.id, "", "first", GenerationModel.FLASH, false,
                LEARNING_ANSWER_POLICY, T0);
        AnswerRevision second = revisions.insert(answer.id, "思考", "second", GenerationModel.PRO,
                true, LEARNING_ANSWER_POLICY, T0 + 1);
        messages.setActiveRevision(answer.id, first.id);
        messages.setActiveOperation(answer.id, insertActiveOperationRow(rootId, answer.id, T0 + 2));

        assertThrows(StorageException.class,
                () -> messages.setActiveRevision(answer.id, second.id),
                "an in-flight attempt must be ended before the displayed revision can switch");
        assertEquals(first.id, messages.get(answer.id).orElseThrow().activeRevisionId(),
                "the refused switch must leave the displayed revision alone");
    }

    @Test
    void reactivatingTheRevisionAlreadyDisplayedIsRefusedAsTheDomainDoes() {
        TopicStore.Created created = topics.create(T0);
        Message answer = messages.appendAiPosition(created.rootBranch.id, 0, T0);
        AnswerRevision revision = revisions.insert(answer.id, "", "answer", GenerationModel.FLASH,
                false, LEARNING_ANSWER_POLICY, T0);
        messages.setActiveRevision(answer.id, revision.id);

        assertThrows(StorageException.class, () -> messages.setActiveRevision(answer.id, revision.id),
                "the domain refuses to re-activate the revision already displayed");
        assertEquals(revision.id, messages.get(answer.id).orElseThrow().activeRevisionId());
    }

    @Test
    void bindingASecondAttemptWhileOneIsRunningIsRefused() {
        TopicStore.Created created = topics.create(T0);
        String rootId = created.rootBranch.id;
        Message answer = messages.appendAiPosition(rootId, 0, T0);
        String operationId = insertActiveOperationRow(rootId, answer.id, T0);
        messages.setActiveOperation(answer.id, operationId);
        assertEquals(operationId, messages.get(answer.id).orElseThrow().activeOperationId());

        assertThrows(StorageException.class,
                () -> messages.setActiveOperation(answer.id, operationId),
                "an attempt is already running, so a second bind is refused by the domain");
        assertEquals(operationId, messages.get(answer.id).orElseThrow().activeOperationId());
    }

    @Test
    void blankContentIsRefusedAndTheQuoteTranslatesBothWays() {
        TopicStore.Created created = topics.create(T0);
        String rootId = created.rootBranch.id;

        assertThrows(IllegalArgumentException.class,
                () -> messages.appendUserMessage(rootId, 0, "   ", null, T0),
                "a blank learner message is a caller bug, refused before any write");

        Message quoted = messages.appendUserMessage(rootId, 0, "正文", "引用片段", T0);
        assertEquals("引用片段", messages.get(quoted.id).orElseThrow().quotedText());

        Message plain = messages.appendUserMessage(rootId, 1, "另一条", null, T0);
        assertEquals("", messages.get(plain.id).orElseThrow().quotedText(),
                "a stored NULL quote reads back as the domain's empty string");
        assertEquals(List.of("正文", "另一条"), messages.listUserContents(rootId));
    }

    @Test
    void gettingAMessageThatDoesNotExistIsEmpty() {
        assertTrue(messages.get(UUID.randomUUID().toString()).isEmpty());
    }
}
