package com.treelearn.core.domain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

import org.junit.Test;

/**
 * Branch identity, unread derivation and archive/collapse behaviour (technical design 11).
 *
 * <p>Unread is derived from a sequence gap rather than stored as a flag, which is what stops a
 * title or summary refresh from raising a phantom unread badge.
 */
public class BranchTest {

    private static final long NOW = 1_700_000_000_000L;

    @Test
    public void unreadDerivesFromTheSequenceGap() {
        Branch branch = Branch.root(Guard.newId(), NOW);
        assertEquals(0, branch.unreadCount());

        branch.onStableAnswer(NOW);
        assertEquals(1, branch.unreadCount());
        branch.onStableAnswer(NOW);
        assertEquals(2, branch.unreadCount());

        assertTrue(branch.markSeenThrough(2).isOk());
        assertEquals(0, branch.unreadCount());

        branch.onStableAnswer(NOW);
        assertEquals(1, branch.unreadCount());
    }

    @Test
    public void readingPositionCannotMoveBackwards() {
        Branch branch = Branch.root(Guard.newId(), NOW);
        branch.onStableAnswer(NOW);
        branch.onStableAnswer(NOW);
        assertTrue(branch.markSeenThrough(2).isOk());

        DomainResult<Void> backwards = branch.markSeenThrough(1);
        assertTrue(backwards.isFailure());
        assertEquals(DomainErrorCode.SEQUENCE_OUT_OF_ORDER, backwards.error().code);
    }

    @Test
    public void readingPositionCannotRunAheadOfWhatExists() {
        Branch branch = Branch.root(Guard.newId(), NOW);
        branch.onStableAnswer(NOW);

        DomainResult<Void> ahead = branch.markSeenThrough(5);
        assertTrue("a stale UI callback must not mark a not-yet-arrived answer as read",
                ahead.isFailure());
        assertEquals(DomainErrorCode.SEQUENCE_OUT_OF_ORDER, ahead.error().code);
    }

    @Test
    public void titleAndCollapseUpdatesNeverCreateUnread() {
        Branch branch = Branch.root(Guard.newId(), NOW);
        long before = branch.latestStableSequence();

        branch.setTitle("derived title", NOW);
        branch.setCollapsed(true, NOW);
        branch.setCollapsed(false, NOW);

        assertEquals(before, branch.latestStableSequence());
        assertEquals(0, branch.unreadCount());
    }

    @Test
    public void archivingKeepsTheBranchesOwnHistory() {
        Branch branch = Branch.root(Guard.newId(), NOW);
        branch.onStableAnswer(NOW);

        branch.archive(NOW);
        assertTrue(branch.isArchived());
        assertTrue(branch.archivedAt() != null);
        assertEquals("archiving must not lose unread state", 1, branch.unreadCount());

        branch.unarchive(NOW);
        assertFalse(branch.isArchived());
        assertNull(branch.archivedAt());
        assertEquals(1, branch.unreadCount());
    }

    @Test
    public void titleMayBeAbsentButNeverBlank() {
        Branch branch = Branch.root(Guard.newId(), NOW);
        assertNull("a branch starts with no derived title", branch.title());
        branch.setTitle("a derived title", NOW);
        assertEquals("a derived title", branch.title());
        branch.setTitle(null, NOW);
        assertNull("clearing the derived title is allowed", branch.title());

        try {
            branch.setTitle("   ", NOW);
            fail("a blank title must be refused");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("title"));
        }
    }

    @Test
    public void updatedAtIsReadableAndNeverPredatesCreation() {
        Branch branch = Branch.root(Guard.newId(), NOW);
        assertEquals(NOW, branch.updatedAt());

        branch.setTitle("t", NOW + 10);
        assertEquals(NOW + 10, branch.updatedAt());

        try {
            branch.setTitle("t", NOW - 1);
            fail("an update must not predate creation");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("predate"));
        }
        try {
            branch.setCollapsed(true, NOW - 1);
            fail("an update must not predate creation");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("predate"));
        }
    }

    @Test
    public void archivingStampsUpdatedAtWithTheArchiveTime() {
        Branch branch = Branch.root(Guard.newId(), NOW);
        branch.archive(NOW + 50);
        assertEquals(NOW + 50, branch.updatedAt());
        assertEquals(Long.valueOf(NOW + 50), branch.archivedAt());

        branch.unarchive(NOW + 60);
        assertEquals(NOW + 60, branch.updatedAt());
        assertNull(branch.archivedAt());
    }

    /**
     * A round-3-independent audit found rejected mutations still took effect: {@code setTitle("X",
     * staleNow)} set the title and only then threw. Validation must happen before any mutation.
     */
    @Test
    public void aRejectedMutationLeavesTheBranchUntouched() {
        Branch branch = Branch.root(Guard.newId(), NOW + 100);
        assertEquals(NOW + 100, branch.updatedAt());

        long stale = NOW;
        try {
            branch.setTitle("should not stick", stale);
            fail("a stale timestamp must be refused");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("predate"));
        }
        assertNull("a refused setTitle must not have changed the title", branch.title());
        assertEquals("a refused mutation must not move updatedAt", NOW + 100, branch.updatedAt());

        try {
            branch.setCollapsed(true, stale);
            fail("a stale timestamp must be refused");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("predate"));
        }
        assertFalse("a refused setCollapsed must not have changed the flag", branch.isCollapsed());

        try {
            branch.onStableAnswer(stale);
            fail("a stale timestamp must be refused");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("predate"));
        }
        assertEquals("a refused onStableAnswer must not have bumped the sequence",
                0, branch.latestStableSequence());
        assertEquals(0, branch.unreadCount());

        branch.archive(NOW + 200);
        assertTrue(branch.isArchived());
        try {
            branch.unarchive(stale);
            fail("a stale timestamp must be refused");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("predate"));
        }
        assertTrue("a refused unarchive must not have cleared the archive stamp",
                branch.isArchived());
    }

    @Test
    public void aBranchKnowsWhetherItIsRoot() {
        Branch root = Branch.root(Guard.newId(), NOW);
        assertTrue(root.isRoot());

        DomainResult<BranchSource> source = BranchSource.fromOperation(
                Guard.newId(), Guard.newId(), Guard.newId(),
                GenerationState.STABLE, Guard.newId(), BranchAnchor.wholeAnswer());
        DomainResult<Branch> child = Branch.child(Guard.newId(), source.value(), NOW);
        assertTrue(child.isOk());
        assertFalse(child.value().isRoot());
    }

    @Test
    public void topicAndBranchIdentifiersAreUuidStrings() {
        String topicId = Guard.newId();
        Branch branch = Branch.root(topicId, NOW);
        assertEquals(topicId, branch.topicId);
        assertSame(Branch.class, branch.getClass());

        try {
            BranchSource.restore("x", Guard.newId(), Guard.newId(),
                    BranchAnchor.wholeAnswer(), Guard.newId());
            fail("a non-UUID identifier must be refused");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("parentBranchId"));
        }
    }

    @Test
    public void lastSeenCanNeverExceedLatestByConstruction() throws Exception {
        for (Field field : Branch.class.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers()) || field.isSynthetic()) {
                continue;
            }
            if (field.getName().equals("latestStableSequence")
                    || field.getName().equals("lastSeenStableSequence")) {
                assertTrue(field.getName() + " must be private", Modifier.isPrivate(field.getModifiers()));
            }
        }
        Branch branch = Branch.root(Guard.newId(), NOW);
        assertEquals(0, branch.lastSeenStableSequence());
        assertEquals(0, branch.latestStableSequence());
    }

    // ------------------------------------------------------------------
    // restore — the rehydration entry point read paths use
    // ------------------------------------------------------------------

    @Test
    public void restoreCarriesStateThatNoCreationPathCouldProduce() {
        String parent = Guard.newId();
        String message = Guard.newId();
        String revision = Guard.newId();
        String snapshot = Guard.newId();
        BranchSource source = BranchSource.restore(parent, message, revision,
                BranchAnchor.wholeAnswer(), snapshot);

        // Every mutable field is set to something a freshly created branch would never hold, so a
        // restore that quietly re-derived them would be caught here rather than in the field.
        Branch restored = Branch.restore(Guard.newId(), Guard.newId(), parent, source,
                "a title a session produced", NOW, NOW + 5_000, 7L, 3L, true, Long.valueOf(NOW + 6_000));

        assertEquals("a title a session produced", restored.title());
        assertEquals(NOW, restored.createdAt);
        assertEquals(NOW + 5_000, restored.updatedAt());
        assertEquals(7L, restored.latestStableSequence());
        assertEquals(3L, restored.lastSeenStableSequence());
        assertEquals(4L, restored.unreadCount());
        assertTrue(restored.isCollapsed());
        assertTrue(restored.isArchived());
        assertEquals(Long.valueOf(NOW + 6_000), restored.archivedAt());
        assertSame("provenance is carried, not rebuilt", source, restored.source);
        assertFalse(restored.isRoot());
    }

    @Test
    public void restoreRefusesAnUpdatedAtOlderThanCreation() {
        try {
            Branch.restore(Guard.newId(), Guard.newId(), null, null, null, NOW, NOW - 1, 0L, 0L,
                    false, null);
            fail("a row modified before it existed must be refused, not clamped");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("updatedAt"));
        }
    }

    @Test
    public void restoreKeepsTheRootAndProvenanceRulesOfTheConstructor() {
        BranchSource source = BranchSource.restore(Guard.newId(), Guard.newId(), Guard.newId(),
                BranchAnchor.wholeAnswer(), Guard.newId());
        try {
            Branch.restore(Guard.newId(), Guard.newId(), null, source, null, NOW, NOW, 0L, 0L,
                    false, null);
            fail("a root row carrying provenance must be refused");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("root"));
        }
        try {
            Branch.restore(Guard.newId(), Guard.newId(), Guard.newId(), null, null, NOW, NOW, 0L,
                    0L, false, null);
            fail("a non-root row without provenance must be refused");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("provenance"));
        }
    }
}
