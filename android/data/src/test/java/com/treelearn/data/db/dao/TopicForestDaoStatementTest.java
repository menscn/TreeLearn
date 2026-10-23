package com.treelearn.data.db.dao;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.treelearn.core.domain.Persona;
import com.treelearn.data.db.TreeLearnSchema;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.junit.Test;

/**
 * T-2026-10-23-068: the forest's one read-only projection.
 *
 * <p>The real statement runs on a device; what a plain JVM can pin is the shape that makes the
 * forest honest: total branches include archived ones, the archived count is a filter on the same
 * branch table, the style is the topic's own column, and there is exactly one statement for the
 * whole forest.
 */
public final class TopicForestDaoStatementTest {

    private static String id(String tag) {
        return UUID.nameUUIDFromBytes(tag.getBytes(StandardCharsets.UTF_8)).toString();
    }

    @Test
    public void theTotalCountsEveryBranchIncludingTheArchivedOnes() {
        String statement = TopicDao.TOPIC_FOREST_COUNTS_SELECT;

        assertTrue("the total is a count over the topic's branches",
                statement.contains("COUNT(*)"));
        assertTrue("the archived count filters the same branch table",
                statement.contains(TreeLearnSchema.Branches.ARCHIVED_AT));
        assertTrue("the count is grouped by the topic without a per-topic parameter",
                statement.contains(TreeLearnSchema.Branches.TOPIC_ID));
        assertFalse("one statement for the whole forest, not one per id",
                statement.contains(TreeLearnSchema.Topics.ID + "=?"));
    }

    @Test
    public void theProjectionAlsoCarriesTheLastUseAndTheStoredStyle() {
        String statement = TopicDao.TOPIC_FOREST_COUNTS_SELECT;

        assertTrue("the last-use stamp is the topic's own",
                statement.contains(TreeLearnSchema.Topics.UPDATED_AT));
        assertTrue("the style is the topic's own column",
                statement.contains(TreeLearnSchema.Topics.PERSONA));
        assertTrue("the topic id is the join key",
                statement.contains(TreeLearnSchema.Topics.ID));
    }

    @Test
    public void theForestReadIsReadOnly() {
        String statement = TopicDao.TOPIC_FOREST_COUNTS_SELECT.toUpperCase(java.util.Locale.ROOT);

        assertFalse(statement.contains("INSERT"));
        assertFalse(statement.contains("UPDATE "));
        assertFalse(statement.contains("DELETE"));
        assertTrue("a read must start with SELECT", statement.trim().startsWith("SELECT"));
    }

    @Test
    public void oneForestCountKeepsTheFourFactsAndTheStoredStyle() {
        String topicId = id("topic");
        TopicDao.ForestCount count = new TopicDao.ForestCount(topicId, 9, 2,
                1_756_000_000_000L, Persona.STUDY_BUDDY.wireId);

        assertEquals(topicId, count.topicId);
        assertEquals(9, count.branchCount);
        assertEquals(2, count.archivedCount);
        assertEquals(1_756_000_000_000L, count.lastVisitedAt);
        assertEquals(Persona.STUDY_BUDDY.wireId, count.personaWireId);
    }

    @Test(expected = IllegalArgumentException.class)
    public void archivedBranchesCannotExceedAllBranches() {
        new TopicDao.ForestCount(id("topic"), 2, 3, 1L, Persona.DEFAULT.wireId);
    }
}
