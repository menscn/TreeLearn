package com.treelearn.core.domain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

/**
 * T-015 acceptance: one search hit is one located match, and the display order is total and stable
 * (technical design 6.3).
 *
 * <p>A hit is never a bare string. It carries the branch it belongs to and, when the match is inside
 * a message, the exact message and revision — that is what lets the result list jump back to the
 * semantic position instead of to "somewhere near the top". A hit that is not message-bound (a title)
 * must not pretend to have an ordinal, which is why the ordinal is validated against the message
 * reference rather than defaulted.
 */
public class SearchHitTest {

    private static final String TOPIC = "11111111-1111-4111-8111-111111111111";
    private static final String TOPIC_2 = "22222222-2222-4222-8222-222222222222";
    private static final String BRANCH_A = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa";
    private static final String BRANCH_B = "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb";
    private static final String MESSAGE = "cccccccc-cccc-4ccc-8ccc-cccccccccccc";
    private static final String REVISION = "dddddddd-dddd-4ddd-8ddd-dddddddddddd";
    private static final String REVISION_2 = "eeeeeeee-eeee-4eee-8eee-eeeeeeeeeeee";

    @Test
    public void theDisplayOrderIsFieldThenTopicThenBranchThenOrdinalThenRevision() {
        List<SearchHit> hits = new ArrayList<SearchHit>(Arrays.asList(
                answer(TOPIC_2, BRANCH_B, MESSAGE, 0, REVISION, 1),
                title(TOPIC, BRANCH_A),
                answer(TOPIC, BRANCH_A, MESSAGE, 4, REVISION, 2),
                reasoning(TOPIC, BRANCH_A, MESSAGE, 4, REVISION, 2),
                answer(TOPIC, BRANCH_A, MESSAGE, 2, REVISION_2, 1)));

        Collections.sort(hits, SearchHit.DISPLAY_ORDER);

        assertEquals(Arrays.asList(
                        SearchHit.Field.TITLE,
                        SearchHit.Field.ANSWER,
                        SearchHit.Field.ANSWER,
                        SearchHit.Field.ANSWER,
                        SearchHit.Field.REASONING),
                fields(hits));
        // Same field: the topic decides next, so the two answers of TOPIC come before the one in
        // TOPIC_2 no matter which order the store returned them in...
        assertEquals(TOPIC, hits.get(1).topicId);
        assertEquals(TOPIC, hits.get(2).topicId);
        assertEquals(TOPIC_2, hits.get(3).topicId);
        // ...and inside one branch the reading order decides, so two answers of one conversation
        // never swap places between two runs of the same query.
        assertEquals(2, hits.get(1).messageOrdinal);
        assertEquals(4, hits.get(2).messageOrdinal);
        assertEquals(0, hits.get(3).messageOrdinal);
    }

    @Test
    public void aTitleHitIsNotMessageBoundAndLocatesNothing() {
        SearchHit hit = title(TOPIC, BRANCH_A);

        assertNull(hit.messageId);
        assertNull(hit.locatorAnchor());
        assertEquals(-1, hit.messageOrdinal);
    }

    @Test
    public void aQuestionHitIsMessageBoundAndOrderedAfterTitle() {
        SearchHit question = SearchHit.of(SearchHit.Field.QUESTION, TOPIC, BRANCH_A, MESSAGE, 0,
                null, 0, "my question");
        SearchHit title = title(TOPIC, BRANCH_A);

        assertEquals(MESSAGE, question.messageId);
        assertTrue(SearchHit.DISPLAY_ORDER.compare(title, question) < 0);
    }

    @Test
    public void anAnswerHitLocatesTheExactMessageAndRevision() {
        SearchHit hit = answer(TOPIC, BRANCH_A, MESSAGE, 2, REVISION, 3);

        ReadingAnchor anchor = hit.locatorAnchor();
        assertEquals(MESSAGE, anchor.messageId);
        assertEquals(REVISION, anchor.revisionId);
        assertTrue(anchor.isRevisionBound());
    }

    @Test
    public void aFailedAttemptHitLocatesTheMessageWithoutClaimingARevision() {
        // A partial answer never became a stable revision (design 9.3), so the hit must not invent
        // one: it points at the position and lets the screen show whatever is running there.
        SearchHit hit = SearchHit.of(SearchHit.Field.PARTIAL_ANSWER, TOPIC, BRANCH_A, MESSAGE, 7,
                null, 0, "synthetic partial text");

        assertNull(hit.revisionId);
        assertEquals(MESSAGE, hit.locatorAnchor().messageId);
        assertNull(hit.locatorAnchor().revisionId);
    }

    @Test
    public void aHitWithoutAMessageMustNotClaimAnOrdinal() {
        assertThrows(IllegalArgumentException.class,
                () -> SearchHit.of(SearchHit.Field.TITLE, TOPIC, BRANCH_A, null, 3, null, 0, "x"));
        assertThrows(IllegalArgumentException.class,
                () -> SearchHit.of(SearchHit.Field.ANSWER, TOPIC, BRANCH_A, MESSAGE, -1,
                        REVISION, 1, "x"));
    }

    @Test
    public void aRevisionCannotBeAttachedToAHitThatNamesNoMessage() {
        assertThrows(IllegalArgumentException.class,
                () -> SearchHit.of(SearchHit.Field.ANSWER, TOPIC, BRANCH_A, null, -1, REVISION, 1, "x"));
    }

    @Test
    public void identifiersAndSnippetsAreValidatedLikeEveryOtherDomainValue() {
        assertThrows(IllegalArgumentException.class,
                () -> SearchHit.of(SearchHit.Field.TITLE, "not-a-uuid", BRANCH_A, null, -1, null, 0, "x"));
        assertThrows(IllegalArgumentException.class,
                () -> SearchHit.of(SearchHit.Field.TITLE, TOPIC, BRANCH_A, null, -1, null, 0, null));
        assertThrows(IllegalArgumentException.class,
                () -> SearchHit.of(null, TOPIC, BRANCH_A, null, -1, null, 0, "x"));
    }

    private static List<SearchHit.Field> fields(List<SearchHit> hits) {
        List<SearchHit.Field> fields = new ArrayList<SearchHit.Field>();
        for (SearchHit hit : hits) {
            fields.add(hit.field);
        }
        return fields;
    }

    private static SearchHit title(String topicId, String branchId) {
        return SearchHit.of(SearchHit.Field.TITLE, topicId, branchId, null, -1, null, 0, "title text");
    }

    private static SearchHit answer(String topicId, String branchId, String messageId, int ordinal,
            String revisionId, int revisionNo) {
        return SearchHit.of(SearchHit.Field.ANSWER, topicId, branchId, messageId, ordinal, revisionId,
                revisionNo, "answer text");
    }

    private static SearchHit reasoning(String topicId, String branchId, String messageId, int ordinal,
            String revisionId, int revisionNo) {
        return SearchHit.of(SearchHit.Field.REASONING, topicId, branchId, messageId, ordinal,
                revisionId, revisionNo, "reasoning text");
    }
}
