package com.treelearn.core.domain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.lang.reflect.Field;

import org.junit.Test;

/** Topic identity: a tree label always comes from the root branch, never from the topic row. */
public class TopicTest {

    private static final long NOW = 1_700_000_000_000L;

    @Test
    public void aTopicCarriesNoNameOfItsOwn() {
        for (Field field : Topic.class.getDeclaredFields()) {
            String name = field.getName().toLowerCase();
            assertTrue("a topic must not store an independent name/title but declares: " + name,
                    !name.equals("name") && !name.equals("title"));
        }
    }

    @Test
    public void aFreshTopicHasNotBeenVisitedYet() {
        Topic topic = Topic.create(NOW);
        assertNull(topic.lastVisitedBranchId);
        assertEquals(NOW, topic.createdAt);
        assertEquals(NOW, topic.updatedAt);
        assertTrue(topic.id != null && !topic.id.isEmpty());
    }

    @Test
    public void visitingABranchKeepsIdentityAndCreationTime() {
        Topic topic = Topic.create(NOW);
        String branchId = Guard.newId();
        Topic visited = topic.withLastVisitedBranch(branchId, NOW + 500);

        assertEquals("the aggregate identity must not change", topic.id, visited.id);
        assertEquals(topic.createdAt, visited.createdAt);
        assertEquals(branchId, visited.lastVisitedBranchId);
        assertEquals(NOW + 500, visited.updatedAt);
    }

    @Test
    public void anUpdateMustNotPredateCreation() {
        Topic topic = Topic.create(NOW);
        try {
            topic.withLastVisitedBranch(Guard.newId(), NOW - 1);
            fail("an update must not predate creation");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("predate"));
        }
        try {
            topic.touched(NOW - 1);
            fail("an update must not predate creation");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("predate"));
        }
        assertEquals("touching at the creation instant is allowed", NOW, topic.touched(NOW).updatedAt);
    }

    @Test
    public void malformedIdentifiersAreRefusedAtConstruction() {
        try {
            new Topic("not-a-uuid", null, NOW, NOW);
            fail("a non-UUID topic id must be refused");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("id"));
        }
        try {
            new Topic(Guard.newId(), "not-a-uuid", NOW, NOW);
            fail("a non-UUID last visited branch must be refused");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("lastVisitedBranchId"));
        }
    }

    @Test
    public void timestampsAreUtcEpochMillisAndNeverNegative() {
        try {
            Topic.create(-1L);
            fail("a negative epoch millis must be refused");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("createdAt"));
        }
    }
}
