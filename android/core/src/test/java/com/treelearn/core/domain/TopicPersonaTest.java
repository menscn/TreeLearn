package com.treelearn.core.domain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

import org.junit.Test;

/**
 * The topic owns the current answer style (SPEC-2026-09-21-ai-persona-preference: 人设跟着主题走).
 *
 * <p>Changing the style is not activity: {@link Topic#withPersona} must leave both timestamps alone,
 * because the home list is ordered by {@code updated_at} and a style switch must not move a tree to
 * the top. Visiting and touching a topic in turn must not lose the style.
 */
public class TopicPersonaTest {

    private static final long NOW = 1_700_000_000_000L;

    @Test
    public void aFreshTopicStartsOnTheDefaultStyle() {
        assertEquals(Persona.DEFAULT, Topic.create(NOW).persona);
    }

    @Test
    public void theOlderConstructorMeansTheDefault() {
        Topic topic = new Topic(Guard.newId(), null, NOW, NOW);
        assertEquals(Persona.DEFAULT, topic.persona);
    }

    @Test
    public void switchingTheStyleKeepsIdentityAndBothTimestamps() {
        Topic topic = new Topic(Guard.newId(), null, Persona.STUDY_BUDDY, NOW, NOW + 10);
        Topic switched = topic.withPersona(Persona.STRICT_MENTOR);

        assertEquals(topic.id, switched.id);
        assertNull(switched.lastVisitedBranchId);
        assertEquals(topic.createdAt, switched.createdAt);
        assertEquals("a style switch must not count as activity", topic.updatedAt, switched.updatedAt);
        assertEquals(Persona.STRICT_MENTOR, switched.persona);
    }

    @Test
    public void visitingATopicKeepsItsStyle() {
        Topic topic = new Topic(Guard.newId(), null, Persona.LEARNING_REPAIR, NOW, NOW);
        Topic visited = topic.withLastVisitedBranch(Guard.newId(), NOW + 20);

        assertEquals(Persona.LEARNING_REPAIR, visited.persona);
        assertEquals(NOW + 20, visited.updatedAt);
    }

    @Test
    public void touchingATopicKeepsItsStyle() {
        Topic topic = new Topic(Guard.newId(), null, Persona.SARCASTIC_FRIEND, NOW, NOW);
        assertEquals(Persona.SARCASTIC_FRIEND, topic.touched(NOW + 30).persona);
    }

    @Test
    public void aMissingStyleIsACallerBug() {
        try {
            Topic.create(NOW).withPersona(null);
            fail("a persona is an enum choice; null must not silently mean the default");
        } catch (IllegalArgumentException expected) {
            assertEquals(true, expected.getMessage().contains("persona"));
        }
    }
}
