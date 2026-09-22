package com.treelearn.core.domain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import org.junit.Test;

/**
 * A message is a fixed logical position, not a version container (technical design 5.1): the
 * learner's text never changes, and switching the displayed answer never overwrites a version.
 */
public class MessageTest {

    private static final long NOW = 1_700_000_000_000L;

    @Test
    public void learnerTextIsFinalAndThePositionExposesNoSetter() throws Exception {
        Field content = Message.class.getDeclaredField("userContent");
        assertTrue("userContent must be final", Modifier.isFinal(content.getModifiers()));

        for (Method method : Message.class.getMethods()) {
            if (method.getDeclaringClass() == Object.class) {
                continue;
            }
            assertFalse("a position must expose no setter: " + method.getName(),
                    method.getName().startsWith("set"));
        }
    }

    @Test
    public void aUserPositionCarriesNoRevisionAndNoAttempt() {
        Message user = Message.user(Guard.newId(), 0, "why is the sky blue?", NOW);
        assertTrue(user.isUser());
        assertFalse(user.isAi());
        assertEquals("why is the sky blue?", user.userContent);
        assertNull(user.activeRevisionId());
        assertNull(user.activeOperationId());
    }

    @Test
    public void anAiPositionCannotBeGivenLearnerText() {
        Message ai = Message.ai(Guard.newId(), 1, NOW);
        assertTrue(ai.isAi());
        assertNull("an AI position has no learner text", ai.userContent);
    }

    @Test
    public void blankLearnerTextIsRefused() {
        try {
            Message.user(Guard.newId(), 0, "   ", NOW);
            fail("a blank question must not create a position");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("userContent"));
        }
    }

    @Test
    public void activatingANewRevisionSwitchesTheDisplayedVersion() {
        Message ai = Message.ai(Guard.newId(), 1, NOW);
        String first = Guard.newId();
        String second = Guard.newId();

        assertTrue(ai.activateRevision(first).isOk());
        assertEquals(first, ai.activeRevisionId());
        assertTrue(ai.activateRevision(second).isOk());
        assertEquals(second, ai.activeRevisionId());
    }

    @Test
    public void reactivatingTheSameRevisionIsRefused() {
        Message ai = Message.ai(Guard.newId(), 1, NOW);
        String revision = Guard.newId();
        assertTrue(ai.activateRevision(revision).isOk());

        DomainResult<Void> again = ai.activateRevision(revision);
        assertTrue(again.isFailure());
        assertEquals(DomainErrorCode.REVISION_IMMUTABLE, again.error().code);
    }

    @Test
    public void aStableRevisionCannotBeAttachedToALearnerPosition() {
        Message user = Message.user(Guard.newId(), 0, "q", NOW);
        DomainResult<Void> result = user.activateRevision(Guard.newId());
        assertTrue(result.isFailure());
        assertEquals(DomainErrorCode.MESSAGE_ROLE_MISMATCH, result.error().code);
    }

    @Test
    public void aSecondAttemptAtTheSamePositionIsRefusedWhileOneIsRunning() {
        Message ai = Message.ai(Guard.newId(), 1, NOW);
        assertTrue(ai.bindOperation(Guard.newId()).isOk());

        DomainResult<Void> second = ai.bindOperation(Guard.newId());
        assertTrue("only one attempt may run at a position", second.isFailure());
        assertEquals(DomainErrorCode.ILLEGAL_STATE_TRANSITION, second.error().code);
    }

    @Test
    public void anAttemptCannotBeBoundToALearnerPosition() {
        Message user = Message.user(Guard.newId(), 0, "q", NOW);
        DomainResult<Void> result = user.bindOperation(Guard.newId());
        assertTrue(result.isFailure());
        assertEquals(DomainErrorCode.MESSAGE_ROLE_MISMATCH, result.error().code);
    }

    @Test
    public void theDisplayedRevisionCannotBeSwitchedWhileAnAttemptIsRunning() {
        Message ai = Message.ai(Guard.newId(), 1, NOW);
        String first = Guard.newId();
        assertTrue(ai.activateRevision(first).isOk());

        String operation = Guard.newId();
        assertTrue(ai.bindOperation(operation).isOk());

        DomainResult<Void> switched = ai.activateRevision(Guard.newId());
        assertTrue("switching the display mid-attempt must be refused, not silently tolerated",
                switched.isFailure());
        assertEquals(DomainErrorCode.ILLEGAL_STATE_TRANSITION, switched.error().code);
        assertEquals("the previous stable answer must still be displayed", first, ai.activeRevisionId());
        assertEquals(operation, ai.activeOperationId());
    }

    @Test
    public void endingTheAttemptInTheSameTransactionThenAllowsTheSwitch() {
        Message ai = Message.ai(Guard.newId(), 1, NOW);
        String first = Guard.newId();
        assertTrue(ai.activateRevision(first).isOk());
        assertTrue(ai.bindOperation(Guard.newId()).isOk());

        ai.clearActiveOperation();
        String second = Guard.newId();
        assertTrue(ai.activateRevision(second).isOk());
        assertEquals(second, ai.activeRevisionId());
        assertNull(ai.activeOperationId());
    }

    @Test
    public void clearingAFailedAttemptKeepsTheDisplayedRevision() {
        Message ai = Message.ai(Guard.newId(), 1, NOW);
        String first = Guard.newId();
        assertTrue(ai.activateRevision(first).isOk());
        assertTrue(ai.bindOperation(Guard.newId()).isOk());

        ai.clearActiveOperation();
        assertNull(ai.activeOperationId());
        assertEquals("a failed retry must not hide the previous stable answer",
                first, ai.activeRevisionId());
    }

    @Test
    public void ordinalIsBranchLocalAndNonNegative() {
        assertEquals(0, Message.user(Guard.newId(), 0, "q", NOW).ordinal);
        try {
            Message.ai(Guard.newId(), -1, NOW);
            fail("a negative ordinal must be refused");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("ordinal"));
        }
    }

    // ------------------------------------------------------------------
    // restore — the rehydration entry point read paths use
    // ------------------------------------------------------------------

    @Test
    public void restoreCarriesTheActiveReferencesNoCreationPathCouldProduce() {
        String revision = Guard.newId();
        String operation = Guard.newId();
        Message restored = Message.restore(Guard.newId(), Guard.newId(), MessageRole.AI, 4, null,
                NOW, revision, operation);

        assertEquals(MessageRole.AI, restored.role);
        assertEquals(4, restored.ordinal);
        assertNull("an AI position carries no learner text", restored.userContent);
        assertEquals("the displayed revision survives the round trip", revision,
                restored.activeRevisionId());
        assertEquals("the running attempt survives the round trip", operation,
                restored.activeOperationId());
    }

    @Test
    public void restoreKeepsTheRoleAndContentRules() {
        Message user = Message.restore(Guard.newId(), Guard.newId(), MessageRole.USER, 0, "text",
                NOW, null, null);
        assertEquals("text", user.userContent);
        try {
            Message.restore(Guard.newId(), Guard.newId(), MessageRole.USER, 0, "  ", NOW, null, null);
            fail("a USER row must carry non-blank content");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("userContent"));
        }
    }
}
