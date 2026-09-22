package com.treelearn.app.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.treelearn.app.generation.PositionCommands;
import com.treelearn.core.domain.Message;
import com.treelearn.core.domain.MessageRole;

import org.junit.Test;

/**
 * The cross-entry comparison the chat rules require: for the same position, the answer the screen
 * permits and the answer T-010's use case gives must be the same one.
 *
 * <p>The rendered message decides whether a button is enabled and {@code PositionCommands} decides
 * whether the store will act, so both judge retry, reset and version switching. Written as two
 * judgements in two modules they can drift; this suite drives the same inputs through both and fails
 * if they ever disagree, which is the only thing that keeps "the enabled button is the button the use
 * case accepts" true after either side changes.
 *
 * <p>The comparison deliberately covers the two shapes a position can have as well as the in-flight
 * flag: an AI position with a displayed version, an AI position without one, and a learner position.
 */
public final class ChatPositionAgreementTest {

    @Test
    public void retryResetAndVersionSwitchAgreeOnAnAnswerThatDisplaysAVersion() {
        for (boolean running : new boolean[] {false, true}) {
            Message position = position(MessageRole.AI, ChatTestDoubles.REV_1);
            ChatMessage rendered = rendered(ChatMessage.Status.STABLE, ChatTestDoubles.REV_1, running);

            assertEquals(refused(PositionCommands.retryRefusal(position, running)),
                    !rendered.canRetry());
            assertEquals(refused(PositionCommands.resetRefusal(position, running)),
                    !rendered.canReset());
            assertVersionSwitchAgrees(position, rendered, running);
        }
    }

    @Test
    public void retryResetAndVersionSwitchAgreeOnAnAnswerWithNoVersionYet() {
        for (boolean running : new boolean[] {false, true}) {
            Message position = position(MessageRole.AI, null);
            ChatMessage rendered = rendered(ChatMessage.Status.FAILED, "", running);

            assertEquals(refused(PositionCommands.retryRefusal(position, running)),
                    !rendered.canRetry());
            assertEquals(refused(PositionCommands.resetRefusal(position, running)),
                    !rendered.canReset());
            assertVersionSwitchAgrees(position, rendered, running);
        }
    }

    @Test
    public void retryResetAndVersionSwitchAgreeOnALearnerPosition() {
        for (boolean running : new boolean[] {false, true}) {
            Message position = position(MessageRole.USER, null);
            ChatMessage rendered = ChatMessage.user(ChatTestDoubles.M_ASK, "a question", "", 1L);

            assertEquals(refused(PositionCommands.retryRefusal(position, running)),
                    !rendered.canRetry());
            assertEquals(refused(PositionCommands.resetRefusal(position, running)),
                    !rendered.canReset());
            assertVersionSwitchAgrees(position, rendered, running);
        }
    }

    private static void assertVersionSwitchAgrees(Message position, ChatMessage rendered,
            boolean running) {
        String[] candidates = {ChatTestDoubles.REV_1, ChatTestDoubles.REV_2};
        for (String candidate : candidates) {
            boolean useCaseAllows = PositionCommands.selectRefusal(position, candidate, position.id,
                    running) == null;
            assertEquals("version switch for " + candidate + " (running=" + running + ")",
                    useCaseAllows, rendered.canSelectRevision(candidate));
        }
    }

    private static boolean refused(PositionCommands.Refusal refusal) {
        return refusal != null;
    }

    private static Message position(MessageRole role, String activeRevisionId) {
        return Message.restore(ChatTestDoubles.M_ANSWER, ChatTestDoubles.BRANCH, role, 1, "a question",
                1L, activeRevisionId, null);
    }

    private static ChatMessage rendered(ChatMessage.Status status, String activeRevisionId,
            boolean attemptRunning) {
        return ChatMessage.ai(ChatTestDoubles.M_ANSWER, "an answer", "a thought", status,
                ChatMessage.Phase.ANSWER, activeRevisionId, 1, 0, attemptRunning, 1L);
    }
}