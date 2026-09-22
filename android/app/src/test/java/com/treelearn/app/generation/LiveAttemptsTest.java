package com.treelearn.app.generation;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.treelearn.ai.prompt.DeepSeekMessage;
import com.treelearn.ai.prompt.DeepSeekRequest;
import com.treelearn.ai.prompt.DeepSeekRequestAssembler;
import com.treelearn.ai.prompt.PromptBudget;
import com.treelearn.core.domain.DomainResult;
import com.treelearn.core.domain.GenerationCommand;
import com.treelearn.core.domain.GenerationModel;
import com.treelearn.core.domain.GenerationOperation;
import com.treelearn.core.domain.PromptPurpose;

import java.util.Collections;

import org.junit.Test;

/**
 * T-008 plain-JVM evidence for the late-event rule of technical design 7 and 9.3.
 *
 * <p>The question "does this callback still belong to the active attempt?" is decided by a registry
 * that holds no database and no Android type, so the drop rule is pinned without an emulator: once an
 * attempt has an outcome its events stop being current, and retrying in place under the same command
 * is what makes the previous operation's events the ones that no longer land.
 */
public final class LiveAttemptsTest {

    private static final long T0 = 1_756_000_000_000L;
    private static final String BRANCH_ONE = "11111111-1111-4111-8111-111111111111";
    private static final String BRANCH_TWO = "22222222-2222-4222-8222-222222222222";
    private static final String MESSAGE = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa";

    @Test
    public void anEventArrivingAfterTheAttemptLeftIsNoLongerCurrent() {
        LiveAttempts attempts = new LiveAttempts();
        GenerationCommand command = command(BRANCH_ONE);
        LiveAttempt live = attempt(command);
        attempts.open(live);

        assertTrue("while it is the live attempt its deltas may land", attempts.isCurrent(live));

        attempts.close(command.id);

        assertFalse("a delta after the outcome must be discarded, not written over the result",
                attempts.isCurrent(live));
        assertNull("and the command has no live attempt any more", attempts.find(command.id));
        assertNull("so a cancel by operation id finds nothing either",
                attempts.byOperation(live.operation.id));
    }

    @Test
    public void retryingInPlaceUnderTheSameCommandMakesTheEarlierOperationStale() {
        LiveAttempts attempts = new LiveAttempts();
        GenerationCommand command = command(BRANCH_ONE);
        LiveAttempt abandoned = attempt(command);
        attempts.open(abandoned);
        attempts.close(command.id);

        LiveAttempt retry = attempt(command);
        attempts.open(retry);

        assertTrue(attempts.isCurrent(retry));
        assertFalse("the abandoned attempt must not overwrite the retry that replaced it",
                attempts.isCurrent(abandoned));
        assertNull(attempts.byOperation(abandoned.operation.id));
        assertSame("the retry owns the command now", retry, attempts.find(command.id));
        assertSame(retry, attempts.byOperation(retry.operation.id));
    }

    @Test
    public void byOperationResolvesTheAttemptCancelNames() {
        LiveAttempts attempts = new LiveAttempts();
        LiveAttempt first = attempt(command(BRANCH_ONE));
        LiveAttempt second = attempt(command(BRANCH_TWO));
        attempts.open(first);
        attempts.open(second);

        assertSame(first, attempts.byOperation(first.operation.id));
        assertSame(second, attempts.byOperation(second.operation.id));
        assertNull("an unknown operation id names nothing, so cancel refuses instead of guessing",
                attempts.byOperation("00000000-0000-4000-8000-000000000000"));
        assertTrue("two commands are live at once: that is the cap of two, seen from the registry",
                attempts.isCurrent(first) && attempts.isCurrent(second));
    }

    /** A production-shaped attempt: the command, its attempt, and the assembled request. */
    private static LiveAttempt attempt(GenerationCommand command) {
        GenerationOperation operation = GenerationOperation.start(command, T0);
        DeepSeekRequest request = DeepSeekRequestAssembler.assemble(command.purpose,
                command.thinkingEnabled, Collections.<DeepSeekMessage>emptyList(), "为什么天空是蓝色的",
                new PromptBudget(10_000, 64));
        return new LiveAttempt(command, operation, request, null);
    }

    private static GenerationCommand command(String branchId) {
        DomainResult<GenerationCommand> created = GenerationCommand.create(branchId, MESSAGE,
                PromptPurpose.LEARNING_ANSWER, "tl-learning-answer/1.0.0", GenerationModel.FLASH,
                true, T0);
        assertTrue("fixture command must be creatable: " + created, created.isOk());
        return created.value();
    }
}
