package com.treelearn.web.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.treelearn.core.domain.AnswerRevision;
import com.treelearn.core.domain.GenerationModel;
import com.treelearn.core.domain.Message;
import com.treelearn.core.domain.Topic;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * T-2026-10-10-010: the UTC epoch-millis contract. Every time point is application-written and read
 * back exactly, a millisecond never rounds away, and recency ordering follows the written stamps —
 * the T-009 gap this task closes with a write-read-order assertion rather than a schema claim.
 */
class TimestampRoundTripTest extends AbstractStoreTest {

    @Test
    void topicsAreListedByRecencyAndTheMillisecondSurvivesTheRoundTrip() {
        long earlier = 1_752_000_000_123L;
        long later = 1_752_003_456_789L;
        TopicStore.Created a = topics.create(earlier);
        TopicStore.Created b = topics.create(later);

        assertEquals(List.of(b.topic.id, a.topic.id), topicOrder(),
                "the newest topic must be listed first");

        Topic readA = topics.get(a.topic.id).orElseThrow();
        assertEquals(earlier, readA.createdAt);
        Instant roundTripped = Instant.ofEpochMilli(readA.createdAt);
        assertTrue(roundTripped.toString().endsWith(".123Z"),
                "millisecond precision must not be rounded away: " + roundTripped);

        topics.touchLastVisited(a.topic.id, a.rootBranch.id, later + 1);
        assertEquals(List.of(a.topic.id, b.topic.id), topicOrder(),
                "the updated stamp alone must reorder the list");
    }

    @Test
    void messageAndRevisionTimestampsKeepMillisecondPrecision() {
        long when = 1_752_000_000_456L;
        TopicStore.Created created = topics.create(when);
        Message question = messages.appendUserMessage(created.rootBranch.id, 0, "q", null, when);
        Message answer = messages.appendAiPosition(created.rootBranch.id, 1, when + 1);
        AnswerRevision revision = revisions.insert(answer.id, "", "answer", GenerationModel.FLASH,
                false, LEARNING_ANSWER_POLICY, when + 2);

        assertEquals(when, messages.get(question.id).orElseThrow().createdAt);
        assertEquals(when, messages.listForBranch(created.rootBranch.id).get(0).message.createdAt);
        assertEquals(Instant.ofEpochMilli(when + 2),
                Instant.ofEpochMilli(revisions.get(revision.id).orElseThrow().createdAt));
    }

    private List<String> topicOrder() {
        return topics.listOrderedByRecency().stream().map(summary -> summary.topicId).toList();
    }
}
