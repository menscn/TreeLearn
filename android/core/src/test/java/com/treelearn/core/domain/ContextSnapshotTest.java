package com.treelearn.core.domain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;

/**
 * The immutable birth snapshot of a non-root branch (technical design 5.3, 6.1).
 *
 * <p>These cases exist because the snapshot is a <em>frozen reference list</em>, not a cached copy
 * of a conversation: after the parent branch has gained answers, renamed itself and switched which
 * revision an answer position displays, the snapshot taken at birth must still name exactly the rows
 * it named then. That is what makes an old child branch keep its own semantics.
 */
public class ContextSnapshotTest {

    private static final String TOPIC_ID = "5e6f7081-92a3-4eb1-8a3b-4c5d6e7f8091";
    private static final String BRANCH_ID = "0f1e2d3c-4b5a-4978-8697-a5b4c3d2e1f0";
    private static final String QUESTION_ID = "1a2b3c4d-5e6f-4a7b-8c9d-0e1f2a3b4c5d";
    private static final String REVISION_1 = "2b3c4d5e-6f70-4b8c-9d0e-1f2a3b4c5d6e";
    private static final String REVISION_2 = "3c4d5e6f-7081-4c9d-8e1f-2a3b4c5d6e7f";
    private static final String AI_MESSAGE_ID = "4d5e6f70-8192-4da0-9f2a-3b4c5d6e7f80";
    private static final long T0 = 1_700_000_000_000L;

    private static ContextSnapshot snapshot(ContextSnapshot.Source... sources) {
        return ContextSnapshot.create(BRANCH_ID, Arrays.asList(sources), T0);
    }

    private static ContextSnapshot.Source user(String id) {
        return ContextSnapshot.Source.userMessage(id);
    }

    private static ContextSnapshot.Source revision(String id) {
        return ContextSnapshot.Source.stableRevision(id);
    }

    @Test
    public void theCanonicalPayloadIsTheDocumentedWireShapeAndItsDigestIsDerived() {
        ContextSnapshot snapshot = snapshot(user(QUESTION_ID), revision(REVISION_1));

        assertEquals("the payload is canonical text, not an artefact of hash iteration order",
                "{\"sources\":[{\"kind\":\"USER_MESSAGE\",\"ref\":\"" + QUESTION_ID
                        + "\"},{\"kind\":\"STABLE_REVISION\",\"ref\":\"" + REVISION_1 + "\"}]}",
                snapshot.payload);
        assertEquals("the digest is derived from the payload, never supplied",
                ContextSnapshotCodec.digestOf(snapshot.payload), snapshot.digest);
        assertEquals(ContextSnapshot.SCHEMA_VERSION, snapshot.schemaVersion);
        assertNotEquals("a different context must not share a digest",
                snapshot.digest, ContextSnapshotCodec.digestOf(snapshot(user(QUESTION_ID)).payload));
    }

    @Test
    public void equalContextsProduceByteIdenticalPayloads() {
        assertEquals(snapshot(user(QUESTION_ID), revision(REVISION_1)).payload,
                snapshot(user(QUESTION_ID), revision(REVISION_1)).payload);
    }

    @Test
    public void theSnapshotIsUnaffectedByEverythingThatHappensUpstreamAfterwards() {
        ContextSnapshot snapshot = snapshot(user(QUESTION_ID), revision(REVISION_1));
        String payload = snapshot.payload;
        String digest = snapshot.digest;
        List<ContextSnapshot.Source> sources = snapshot.sources;

        // After the child branch was born: the source answer gained a second stable revision, the
        // position switched to it, and the parent branch was renamed and continued.
        Message sourcePosition = Message.restore(AI_MESSAGE_ID, BRANCH_ID, MessageRole.AI, 1, null,
                T0, REVISION_1, null);
        assertTrue("the fixture must be able to display the pinned revision",
                sourcePosition.activateRevision(REVISION_2).isOk());
        Branch parent = Branch.root(TOPIC_ID, T0);
        parent.setTitle("重命名后的父分支", T0 + 1);
        parent.onStableAnswer(T0 + 2);

        assertSame("the snapshot hands out its own list", sources, snapshot.sources);
        assertEquals("the payload never moves", payload, snapshot.payload);
        assertEquals("the digest never moves", digest, snapshot.digest);
        assertEquals("the pinned revision stays pinned", REVISION_1, snapshot.sources.get(1).refId);
        assertNotEquals("the snapshot never follows the displayed revision",
                REVISION_2, snapshot.sources.get(1).refId);
        assertEquals(2, snapshot.sources.size());
    }

    @Test
    public void laterMutationsOfTheCallerListCannotReachTheSnapshot() {
        List<ContextSnapshot.Source> caller = new ArrayList<ContextSnapshot.Source>();
        caller.add(user(QUESTION_ID));
        caller.add(revision(REVISION_1));
        ContextSnapshot snapshot = ContextSnapshot.create(BRANCH_ID, caller, T0);

        caller.add(revision(REVISION_2));
        caller.set(0, ContextSnapshot.Source.summary(QUESTION_ID));

        assertEquals("the snapshot copied the list it was handed", 2, snapshot.sources.size());
        assertEquals(ContextSnapshot.SourceKind.USER_MESSAGE, snapshot.sources.get(0).kind);
        assertEquals(QUESTION_ID, snapshot.sources.get(0).refId);
        assertEquals(ContextSnapshot.SourceKind.STABLE_REVISION, snapshot.sources.get(1).kind);
        assertThrows("the exposed list is unmodifiable", UnsupportedOperationException.class,
                () -> snapshot.sources.add(user(QUESTION_ID)));
    }

    @Test
    public void aStoredSnapshotReadsBackThroughTheStructuredParser() {
        ContextSnapshot snapshot = snapshot(user(QUESTION_ID), revision(REVISION_1));

        DomainResult<ContextSnapshot> stored = ContextSnapshot.fromStored(snapshot.id, BRANCH_ID,
                ContextSnapshot.SCHEMA_VERSION, snapshot.digest, snapshot.payload, T0);

        assertTrue("expected a readable snapshot, got " + stored, stored.isOk());
        assertEquals(snapshot.payload, stored.value().payload);
        assertEquals(snapshot.digest, stored.value().digest);
        assertEquals(2, stored.value().sources.size());
        assertEquals(ContextSnapshot.SourceKind.USER_MESSAGE, stored.value().sources.get(0).kind);
        assertEquals(QUESTION_ID, stored.value().sources.get(0).refId);
        assertEquals(ContextSnapshot.SourceKind.STABLE_REVISION, stored.value().sources.get(1).kind);
        assertEquals(REVISION_1, stored.value().sources.get(1).refId);
    }

    @Test
    public void aPayloadWhoseDigestDoesNotMatchIsRefused() {
        ContextSnapshot snapshot = snapshot(revision(REVISION_1));
        String otherContext = snapshot(user(QUESTION_ID)).payload;

        DomainResult<ContextSnapshot> stored = ContextSnapshot.fromStored(snapshot.id, BRANCH_ID,
                ContextSnapshot.SCHEMA_VERSION, ContextSnapshotCodec.digestOf(otherContext),
                snapshot.payload, T0);

        assertTrue(stored.isFailure());
        assertEquals(DomainErrorCode.SNAPSHOT_DIGEST_MISMATCH, stored.error().code);
    }

    @Test
    public void everyPayloadOutsideTheSchemaIsRefused() {
        String good = snapshot(revision(REVISION_1)).payload;
        String[] rejected = {
            "",
            "   ",
            "not json at all",
            "42",
            "\"text\"",
            "[]",
            "{}",
            "{\"sources\":{}}",
            "{\"sources\":[\"x\"]}",
            "{\"sources\":[{}]}",
            "{\"sources\":[{\"kind\":\"STABLE_REVISION\"}]}",
            "{\"sources\":[{\"kind\":\"STABLE_REVISION\",\"ref\":\"" + REVISION_1 + "\",\"extra\":1}]}",
            "{\"sources\":[{\"kind\":\"STABLE_REVISION\",\"ref\":\"" + REVISION_1
                    + "\",\"ref\":\"" + REVISION_2 + "\"}]}",
            "{\"sources\":[{\"kind\":\"NO_SUCH_KIND\",\"ref\":\"" + REVISION_1 + "\"}]}",
            "{\"sources\":[{\"kind\":\"STABLE_REVISION\",\"ref\":\"not-a-uuid\"}]}",
            "{\"sources\":[]}",
            "{\"sources\":[],\"extra\":1}",
            "{\"sources\":[{\"kind\":\"STABLE_REVISION\",\"ref\":\"" + REVISION_1 + "\"}]} trailing",
            "{\"sources\":[{\"kind\":\"STABLE_REVISION\",\"ref\":\"" + REVISION_1 + "\"},]}",
            good.substring(0, good.length() - 1),
        };

        for (String payload : rejected) {
            DomainResult<ContextSnapshot> stored = ContextSnapshot.fromStored(snapshot(revision(REVISION_1)).id,
                    BRANCH_ID, ContextSnapshot.SCHEMA_VERSION, ContextSnapshotCodec.digestOf(payload),
                    payload, T0);
            assertTrue("this payload must be refused: <" + payload + ">", stored.isFailure());
            assertEquals("this payload must be refused as malformed: <" + payload + ">",
                    DomainErrorCode.SNAPSHOT_PAYLOAD_INVALID, stored.error().code);
        }
    }

    @Test
    public void aSchemaVersionThisBuildCannotReadIsRefused() {
        ContextSnapshot snapshot = snapshot(revision(REVISION_1));

        for (int version : new int[] {0, 2, 99}) {
            DomainResult<ContextSnapshot> stored = ContextSnapshot.fromStored(snapshot.id, BRANCH_ID,
                    version, snapshot.digest, snapshot.payload, T0);
            assertTrue("version " + version + " must be refused", stored.isFailure());
            assertEquals("version " + version + " must name the schema, not the bytes",
                    DomainErrorCode.SNAPSHOT_SCHEMA_UNSUPPORTED, stored.error().code);
        }
    }

    @Test
    public void everySourceKindSurvivesTheCodec() {
        for (ContextSnapshot.SourceKind kind : ContextSnapshot.SourceKind.values()) {
            ContextSnapshot snapshot = snapshot(ContextSnapshot.Source.of(kind, REVISION_1));

            DomainResult<ContextSnapshot> stored = ContextSnapshot.fromStored(snapshot.id, BRANCH_ID,
                    ContextSnapshot.SCHEMA_VERSION, snapshot.digest, snapshot.payload, T0);

            assertTrue("kind " + kind + " must round-trip, got " + stored, stored.isOk());
            assertEquals(kind, stored.value().sources.get(0).kind);
        }
    }

    @Test
    public void aSnapshotWithoutSourcesIsNotASnapshot() {
        assertThrows("an empty context list must be refused at construction",
                IllegalArgumentException.class, () -> ContextSnapshot.create(BRANCH_ID,
                        new ArrayList<ContextSnapshot.Source>(), T0));
        assertThrows("an omitted context list must be refused at construction",
                IllegalArgumentException.class,
                () -> ContextSnapshot.create(BRANCH_ID, null, T0));
    }

    @Test
    public void malformedIdentifiersAndTimestampsAreCallerBugs() {
        assertThrows(IllegalArgumentException.class,
                () -> ContextSnapshot.create("not-a-uuid", Arrays.asList(revision(REVISION_1)), T0));
        assertThrows(IllegalArgumentException.class,
                () -> ContextSnapshot.create(BRANCH_ID, Arrays.asList(revision(REVISION_1)), -1L));
        assertThrows(IllegalArgumentException.class,
                () -> ContextSnapshot.Source.stableRevision("not-a-uuid"));
        assertThrows(IllegalArgumentException.class,
                () -> ContextSnapshot.Source.of(null, REVISION_1));
        assertThrows(IllegalArgumentException.class,
                () -> ContextSnapshot.fromStored("not-a-uuid", BRANCH_ID, 1, "digest", "{}", T0));
    }

    @Test
    public void theSnapshotIdentityNeverCarriesContent() {
        ContextSnapshot snapshot = snapshot(revision(REVISION_1));

        assertTrue("the payload carries a reference, never a copy of any content",
                snapshot.payload.contains(REVISION_1));
        assertFalse("a diagnostic must not be able to carry the payload",
                snapshot.toString().contains(snapshot.payload));
        assertTrue("a diagnostic still names the snapshot it belongs to",
                snapshot.toString().contains(snapshot.id));
    }
}