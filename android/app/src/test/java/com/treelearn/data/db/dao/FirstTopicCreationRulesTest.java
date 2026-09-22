package com.treelearn.data.db.dao;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.treelearn.core.domain.Topic;
import com.treelearn.data.db.dao.FirstTopicCreation.Outcome;
import com.treelearn.data.db.dao.FirstTopicCreation.Refusal;

import org.junit.Test;

/**
 * The two judgements of the first-topic write, proved on the JVM (no device, no emulator).
 *
 * <p><strong>What this class can and cannot prove.</strong> The five-row-group commit, the rollback
 * to nothing, {@code PRAGMA foreign_key_check} and close-and-reopen equality all need a real Android
 * SQLite engine, so they live in {@code FirstTopicCreationInstrumentedTest} and are <em>deferred</em>
 * while the emulator is out of use. What is left over is exactly the part of this task that is a
 * <em>judgement</em> rather than a store call, and a judgement is a function:
 * {@link FirstTopicCreation#decideSourceRefusal} (the cross-topic source rule) and
 * {@link FirstTopicCreation#classifyStorageRefusal} (which store refusal may be named to the caller).
 * Both are pure, so both are pinned here — on every build, in about a second, without a device.
 *
 * <p>The store has already been asked what its refusal text looks like: the samples below are the
 * <em>measured</em> strings from {@code artifacts/evidence/T-025-constraint-code-probe.txt}, not
 * reconstructions. Pinning the real text is the point: an earlier revision of this class matched an
 * upper-cased copy of that text, which compiled, read correctly and never matched, so every duplicate
 * topic was reported to the caller as an unnamed storage failure.
 */
public final class FirstTopicCreationRulesTest {

    private static final String TOPIC = "aaaaaaaa-0000-4000-8000-000000000001";

    private static final String OTHER_TOPIC = "bbbbbbbb-0000-4000-8000-000000000002";

    /** Measured on API 36: a second row claiming an existing {@code topics.id}. */
    private static final String DUPLICATE_TOPIC_ID =
            "UNIQUE constraint failed: topics.id (code 1555 SQLITE_CONSTRAINT_PRIMARYKEY)";

    /** Measured on API 36: a second root branch of one topic, a partial unique index, code 2067. */
    private static final String SECOND_ROOT_BRANCH =
            "UNIQUE constraint failed: branches.topic_id (code 2067 SQLITE_CONSTRAINT_UNIQUE)";

    // ------------------------------------------------------------------
    // The cross-topic source rule: the only defence of a rule no CHECK can express.
    // ------------------------------------------------------------------

    @Test
    public void aSourceOwnedByTheTargetTopicIsAccepted() {
        assertNull("both locators belong to the topic being written",
                FirstTopicCreation.decideSourceRefusal(TOPIC, TOPIC, TOPIC));
    }

    @Test
    public void aSourceMessageOwnedByAnotherTopicIsRefused() {
        assertEquals("source_message_id cascades, so a cross-topic pointer would let deleting one"
                        + " topic delete another topic's branch",
                Refusal.SOURCE_MESSAGE_NOT_IN_TOPIC,
                FirstTopicCreation.decideSourceRefusal(TOPIC, OTHER_TOPIC, TOPIC));
    }

    @Test
    public void aSourceRevisionOwnedByAnotherTopicIsRefused() {
        assertEquals(Refusal.SOURCE_MESSAGE_NOT_IN_TOPIC,
                FirstTopicCreation.decideSourceRefusal(TOPIC, TOPIC, OTHER_TOPIC));
    }

    @Test
    public void aSourceThatNoRowCarriesIsRefusedRatherThanDeferredToTheForeignKey() {
        assertEquals("no message row at all",
                Refusal.SOURCE_MESSAGE_NOT_IN_TOPIC,
                FirstTopicCreation.decideSourceRefusal(TOPIC, null, null));
        assertEquals("message absent while the revision resolves",
                Refusal.SOURCE_MESSAGE_NOT_IN_TOPIC,
                FirstTopicCreation.decideSourceRefusal(TOPIC, null, TOPIC));
        assertEquals("revision absent while the message resolves",
                Refusal.SOURCE_MESSAGE_NOT_IN_TOPIC,
                FirstTopicCreation.decideSourceRefusal(TOPIC, TOPIC, null));
    }

    // ------------------------------------------------------------------
    // Which store refusal may be named to the caller.
    // ------------------------------------------------------------------

    @Test
    public void aTakenTopicIdIsNamedByTheExtendedCodeAndTheConstraintThatSpoke() {
        assertEquals(Refusal.TOPIC_ID_ALREADY_EXISTS,
                FirstTopicCreation.classifyStorageRefusal(1555, DUPLICATE_TOPIC_ID));
    }

    @Test
    public void theMeasuredDeviceTextIsTheTextThatMatches() {
        assertTrue("the classification must survive SQLite's own spelling, case included",
                FirstTopicCreation.classifyStorageRefusal(1555, DUPLICATE_TOPIC_ID)
                        == Refusal.TOPIC_ID_ALREADY_EXISTS);
        assertEquals("an upper-cased copy of the same sentence is not the text SQLite emits, so it"
                        + " must not be reported as a taken topic id either way - what matters is"
                        + " that the measured text above is the one that classifies",
                Refusal.STORAGE_REFUSED,
                FirstTopicCreation.classifyStorageRefusal(1555,
                        "UNIQUE CONSTRAINT FAILED: topics.id (code 1555 SQLITE_CONSTRAINT_PRIMARYKEY)"));
    }

    @Test
    public void anotherConstraintSharingTheCodeIsNotReportedAsATakenTopicId() {
        assertEquals("a second row reusing another table's primary key is a caller bug, not a"
                        + " duplicate topic",
                Refusal.STORAGE_REFUSED,
                FirstTopicCreation.classifyStorageRefusal(1555,
                        "UNIQUE constraint failed: messages.id (code 1555 SQLITE_CONSTRAINT_PRIMARYKEY)"));
        assertEquals(Refusal.STORAGE_REFUSED,
                FirstTopicCreation.classifyStorageRefusal(2067, SECOND_ROOT_BRANCH));
        assertEquals(Refusal.STORAGE_REFUSED,
                FirstTopicCreation.classifyStorageRefusal(1299,
                        "NOT NULL constraint failed: messages.user_content (code 1299"
                                + " SQLITE_CONSTRAINT_NOTNULL)"));
    }

    @Test
    public void aRefusalCarryingNoExtendedCodeIsNeverNamed() {
        assertEquals(Refusal.STORAGE_REFUSED, FirstTopicCreation.classifyStorageRefusal(0, ""));
        assertEquals(Refusal.STORAGE_REFUSED,
                FirstTopicCreation.classifyStorageRefusal(0, DUPLICATE_TOPIC_ID));
    }

    // ------------------------------------------------------------------
    // The result type the caller branches on.
    // ------------------------------------------------------------------

    @Test
    public void anOutcomeIsExactlyOneOfCreatedOrRefused() {
        Topic topic = new Topic(TOPIC, null, 1_756_000_000_000L, 1_756_000_000_000L);

        Outcome created = Outcome.created(topic);
        assertTrue(created.isCreated());
        assertFalse(created.isRefused());
        assertSame("the caller hands this topic to the UI, so it is the same instance", topic,
                created.topic);
        assertNull("a created outcome carries no refusal", created.refusal);
        assertNull(created.detail);

        Outcome refused = Outcome.refused(Refusal.SOURCE_MESSAGE_NOT_IN_TOPIC, "why it was refused");
        assertTrue(refused.isRefused());
        assertFalse(refused.isCreated());
        assertNull("a refusal leaves no topic behind", refused.topic);
        assertEquals(Refusal.SOURCE_MESSAGE_NOT_IN_TOPIC, refused.refusal);
    }

    @Test
    public void aRefusalMustCarryADetailTheCallerCanActOn() {
        try {
            Outcome.refused(Refusal.STORAGE_REFUSED, "   ");
            fail("a blank detail must be refused: it tells the caller nothing to act on");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("detail"));
        }
        try {
            Outcome.refused(null, "detail");
            fail("a refusal without a reason has no producer and must not be constructible");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("refusal"));
        }
    }

    @Test
    public void theRefusalDoesNotEchoUserContentIntoDiagnostics() {
        Outcome refusal = Outcome.refused(Refusal.SOURCE_MESSAGE_NOT_IN_TOPIC,
                "the branch declares source message m1");
        assertTrue(refusal.toString().contains("SOURCE_MESSAGE_NOT_IN_TOPIC"));
        assertTrue(refusal.toString().contains("m1"));
    }
}
