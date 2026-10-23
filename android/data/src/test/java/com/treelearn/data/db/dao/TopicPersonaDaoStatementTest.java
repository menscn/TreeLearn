package com.treelearn.data.db.dao;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.treelearn.core.domain.Persona;
import com.treelearn.data.db.TreeLearnSchema;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.Test;

/**
 * T-2026-09-21 persona acceptance, JVM half: the topic style's read and narrow write.
 *
 * <p>Real Android SQLite execution stays with the device rounds (rules/sql.md), so this suite pins
 * what a plain JVM can judge: the fallback rule for a stored wire id, and the fact that
 * {@code updatePersona} writes the style column and nothing else. The narrow write is the point —
 * reusing {@code TopicDao.update} would refresh {@code updated_at} and move the tree to the top of
 * the home list, which the user decision of 2026-10-23 forbids.
 */
public class TopicPersonaDaoStatementTest {

    @Test
    public void aKnownStoredStylePassesThrough() {
        TopicDao.PersonaRead read = TopicDao.personaRead(Persona.STRICT_MENTOR.wireId);
        assertEquals(Persona.STRICT_MENTOR, read.persona);
        assertFalse(read.fellBack);
    }

    @Test
    public void anUnknownStoredStyleFallsBackAndSaysSo() {
        for (String stored : new String[]{null, "", "   ", "future-style", "Default"}) {
            TopicDao.PersonaRead read = TopicDao.personaRead(stored);
            assertEquals("stored <" + stored + "> must not break the read",
                    Persona.DEFAULT, read.persona);
            assertTrue("stored <" + stored + "> must be reported, not silently adopted",
                    read.fellBack);
        }
    }

    @Test
    public void theReadJoinsTheTopicThroughItsBranch() {
        assertTrue(TopicDao.PERSONA_OF_BRANCH_SELECT.contains("JOIN "
                + TreeLearnSchema.Topics.TABLE));
        assertTrue(TopicDao.PERSONA_OF_BRANCH_SELECT.contains("t."
                + TreeLearnSchema.Topics.PERSONA));
        assertTrue(TopicDao.PERSONA_OF_BRANCH_SELECT.contains("b."
                + TreeLearnSchema.Branches.ID));
    }

    @Test
    public void theNarrowWriteNeverTouchesTheActivityStamp() throws IOException {
        String body = methodBody(readSource(), "public static void updatePersona(");

        assertTrue("the write must name the style column", body.contains(
                "TreeLearnSchema.Topics.PERSONA"));
        assertFalse("a style switch must not refresh updated_at", body.contains("UPDATED_AT"));
        assertFalse("and it must not write the whole row through values(...)", body.contains(
                "values(topic)"));
    }

    private static String methodBody(String source, String signature) {
        int start = source.indexOf(signature);
        if (start < 0) {
            fail("no method starts with: " + signature);
        }
        int end = source.indexOf("\n    }", start);
        if (end < 0) {
            fail("cannot find the end of: " + signature);
        }
        return source.substring(start, end);
    }

    private static String readSource() throws IOException {
        Path[] candidates = {
                Paths.get("src/main/java/com/treelearn/data/db/dao/TopicDao.java"),
                Paths.get("data/src/main/java/com/treelearn/data/db/dao/TopicDao.java"),
                Paths.get("android/data/src/main/java/com/treelearn/data/db/dao/TopicDao.java"),
        };
        for (Path candidate : candidates) {
            if (Files.isRegularFile(candidate)) {
                return new String(Files.readAllBytes(candidate), StandardCharsets.UTF_8);
            }
        }
        throw new AssertionError("cannot locate TopicDao.java from "
                + Paths.get("").toAbsolutePath() + "; this assertion must not be skipped");
    }
}
