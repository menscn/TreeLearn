package com.treelearn.core.domain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.lang.reflect.Modifier;

import org.junit.Test;

/**
 * Provenance rules: only a completed attempt can become a branch source, and every fork pins the
 * exact version and anchor it came from (technical design 5.2).
 */
public class BranchSourceTest {

    private static final long NOW = 1_700_000_000_000L;
    private static final String POLICY = "tl-learning-answer/1.0.0";

    private static DomainResult<BranchSource> fromState(GenerationState state, String revisionId) {
        return BranchSource.fromOperation(
                Guard.newId(), Guard.newId(), Guard.newId(), state, revisionId, BranchAnchor.wholeAnswer());
    }

    @Test
    public void everyUnfinishedOrFailedStateIsRefusedAsABranchSource() {
        GenerationState[] refused = {
                GenerationState.STAGED,
                GenerationState.VALIDATING,
                GenerationState.SUBMITTING,
                GenerationState.STREAMING_REASONING,
                GenerationState.STREAMING_ANSWER,
                GenerationState.FAILED,
                GenerationState.INTERRUPTED,
                GenerationState.CANCELED,
        };
        for (GenerationState state : refused) {
            DomainResult<BranchSource> result = fromState(state, Guard.newId());
            assertTrue(state + " must not become a branch source", result.isFailure());
            assertEquals("wrong code for " + state,
                    DomainErrorCode.OPERATION_NOT_STABLE, result.error().code);
        }
    }

    /**
     * A STABLE attempt always produced a revision, so omitting it is a caller bug. A
     * round-3-independent audit found this case returned the {@code OPERATION_NOT_STABLE} business
     * code while a null {@code operationState} threw, i.e. the same class of malformed input
     * travelled on two different channels. Both now throw.
     */
    @Test
    public void aStableStateWithoutARevisionIdIsACallerBugNotABusinessFailure() {
        for (String missing : new String[] {null, ""}) {
            try {
                fromState(GenerationState.STABLE, missing);
                fail("a STABLE attempt without a revision id must not be reported as a business failure");
            } catch (IllegalArgumentException expected) {
                assertTrue(expected.getMessage().contains("stableRevisionId"));
            }
        }
        try {
            fromState(null, Guard.newId());
            fail("a null operation state must not be reported as a business failure");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("operationState"));
        }
        try {
            fromState(GenerationState.STABLE, "not-a-uuid");
            fail("a non-UUID revision id must be refused at construction time");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("sourceRevisionId"));
        }
    }

    @Test
    public void stableStateYieldsProvenanceThatPinsExactlyThatRevision() {
        String revisionId = Guard.newId();
        DomainResult<BranchSource> result = fromState(GenerationState.STABLE, revisionId);
        assertTrue(result.isOk());
        BranchSource source = result.value();
        assertEquals(revisionId, source.sourceRevisionId);
        assertTrue(source.pinsRevision(revisionId));
        assertFalse(source.pinsRevision(Guard.newId()));
    }

    @Test
    public void aRangedAnchorNeedsBothOffsetsAndTheQuoteAndHashFallback() {
        assertTrue(BranchAnchor.ofRange(3, 9, "q12345", "hash").isOk());

        assertEquals(DomainErrorCode.ANCHOR_INVALID,
                BranchAnchor.ofRange(-1, 9, "q12345", "hash").error().code);
        assertEquals(DomainErrorCode.ANCHOR_INVALID,
                BranchAnchor.ofRange(9, 9, "q12345", "hash").error().code);
        assertEquals(DomainErrorCode.ANCHOR_INVALID,
                BranchAnchor.ofRange(9, 3, "q12345", "hash").error().code);
        assertEquals(DomainErrorCode.ANCHOR_INVALID,
                BranchAnchor.ofRange(3, 9, null, "hash").error().code);
        assertEquals(DomainErrorCode.ANCHOR_INVALID,
                BranchAnchor.ofRange(3, 9, "", "hash").error().code);
        assertEquals(DomainErrorCode.ANCHOR_INVALID,
                BranchAnchor.ofRange(3, 9, "q12345", null).error().code);
        assertEquals(DomainErrorCode.ANCHOR_INVALID,
                BranchAnchor.ofRange(3, 9, "q12345", "").error().code);
    }

    /**
     * The two locators must describe the same range. A round-3-independent audit found
     * {@code ofRange(0, 5, "abc", "h")} was accepted, so the primary (offsets) and fallback (quote)
     * locators could silently disagree.
     */
    @Test
    public void theQuoteMustBeExactlyTheAnchoredRange() {
        assertEquals(DomainErrorCode.ANCHOR_INVALID,
                BranchAnchor.ofRange(0, 5, "abc", "h").error().code);
        assertEquals(DomainErrorCode.ANCHOR_INVALID,
                BranchAnchor.ofRange(0, 5, "abcdef", "h").error().code);
        assertTrue(BranchAnchor.ofRange(0, 5, "abcde", "h").isOk());
        assertTrue(BranchAnchor.ofRange(10, 11, "x", "h").isOk());
    }

    @Test
    public void wholeAnswerForkCarriesNoOffsetsButStillPinsARevision() {
        BranchAnchor anchor = BranchAnchor.wholeAnswer();
        assertTrue(anchor.isWholeAnswer());
        assertEquals(null, anchor.startInclusive);
        assertEquals(null, anchor.endExclusive);

        String revisionId = Guard.newId();
        String messageId = Guard.newId();
        DomainResult<BranchSource> result = BranchSource.fromOperation(
                Guard.newId(), messageId, Guard.newId(),
                GenerationState.STABLE, revisionId, anchor);
        assertTrue(result.isOk());
        assertEquals("an entire-answer fork still has to pin the version it came from",
                revisionId, result.value().sourceRevisionId);
        assertEquals(messageId, result.value().sourceMessageId);
    }

    @Test
    public void provenanceRejectsMalformedIdentifiers() {
        try {
            BranchSource.restore("not-a-uuid", Guard.newId(), Guard.newId(),
                    BranchAnchor.wholeAnswer(), Guard.newId());
            fail("a non-UUID parent id must be refused at construction time");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("parentBranchId"));
        }
    }

    /**
     * A round-3-independent audit found the constructor was public, which let any caller build
     * provenance directly and skip the STABLE rule. Creation must go through {@link
     * BranchSource#fromOperation}, and rehydration through the explicitly named {@code restore}.
     */
    @Test
    public void theConstructorIsNotAPublicCreationPath() {
        for (java.lang.reflect.Constructor<?> constructor : BranchSource.class.getDeclaredConstructors()) {
            assertTrue("BranchSource must not expose a public constructor: " + constructor,
                    Modifier.isPrivate(constructor.getModifiers()));
        }
    }

    @Test
    public void restoreIsRehydrationOnlyButStillValidatesItsInput() {
        String parent = Guard.newId();
        String message = Guard.newId();
        String revision = Guard.newId();
        String snapshot = Guard.newId();
        BranchSource restored = BranchSource.restore(
                parent, message, revision, BranchAnchor.wholeAnswer(), snapshot);

        assertEquals(parent, restored.parentBranchId);
        assertEquals(message, restored.sourceMessageId);
        assertEquals(revision, restored.sourceRevisionId);
        assertEquals(snapshot, restored.contextSnapshotId);
        assertTrue(restored.pinsRevision(revision));
    }

    @Test
    public void aNonRootBranchCannotExistWithoutProvenance() {
        // Provenance is a mandatory argument, so omitting it is a caller bug (throwing channel),
        // not a business refusal. A round-4-independent audit found this used to return
        // BRANCH_SOURCE_INVALID while BranchSource.fromOperation threw for the same mistake; the
        // constant had no other producer and was removed.
        try {
            Branch.child(Guard.newId(), null, NOW);
            fail("a child branch without provenance must be refused");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("provenance"));
        }
    }

    @Test
    public void aRootBranchCarriesNoProvenance() {
        Branch root = Branch.root(Guard.newId(), NOW);
        assertTrue(root.isRoot());
        assertEquals(null, root.parentBranchId);
        assertEquals(null, root.source);
    }

    @Test
    public void aChildBranchInheritsItsParentFromTheProvenance() {
        DomainResult<BranchSource> source = fromState(GenerationState.STABLE, Guard.newId());
        assertTrue(source.isOk());
        DomainResult<Branch> child = Branch.child(Guard.newId(), source.value(), NOW);
        assertTrue(child.isOk());
        assertFalse(child.value().isRoot());
        assertEquals(source.value().parentBranchId, child.value().parentBranchId);
        assertEquals("tl-learning-answer/1.0.0", POLICY);
    }
}
