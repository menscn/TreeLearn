package com.treelearn.app.generation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.treelearn.core.domain.DomainErrorCode;
import com.treelearn.core.domain.DomainResult;
import com.treelearn.core.domain.GenerationCommand;
import com.treelearn.core.domain.GenerationModel;
import com.treelearn.core.domain.Guard;
import com.treelearn.core.domain.Message;
import com.treelearn.core.domain.MessageRole;
import com.treelearn.core.domain.PromptPurpose;

import org.junit.Test;

/**
 * The command-permission rules of retry, reset and revision selection (technical design 5.1, 7).
 *
 * <p>These run on the JVM because the rules are pure: they read a position and answer yes or no. What
 * they deliberately do <em>not</em> cover is the store side - that a refused command writes nothing,
 * that a retry adds no learner message and no turn, and that a selected older version leaves child
 * branches untouched. Those need SQLite and live in
 * {@code AnswerVersionsInstrumentedTest}.
 */
public final class PositionCommandsTest {

    /** Owned by {@link PromptPurpose#LEARNING_ANSWER}; the purpose a retry of an answer keeps. */
    private static final String POLICY_VERSION = "tl-learning-answer/1.0.0";
    /** A legal version owned by a different purpose, so the mismatch path is reachable. */
    private static final String FOREIGN_POLICY_VERSION = "tl-branch-title/1.0.0";
    private static final long NOW = 1_700_000_000_000L;
    /**
     * Branch, position, revision and attempt ids are canonical UUIDs everywhere they are stored
     * ({@link Guard#id}), so the fixture must use real ones: a readable placeholder such as
     * "branch-1" would make {@link Message#restore} throw before any rule under test ever ran.
     */
    private static final String BRANCH = Guard.newId();
    private static final String POSITION = Guard.newId();
    private static final String REVISION_1 = Guard.newId();
    private static final String REVISION_2 = Guard.newId();
    private static final String UNOWNED_REVISION = Guard.newId();
    private static final String OPERATION_1 = Guard.newId();

    // ------------------------------------------------------------------ retry

    @Test
    public void retryOnAMissingPositionIsRefusedAsNotFound() {
        assertEquals(PositionCommands.Refusal.MESSAGE_NOT_FOUND,
                PositionCommands.retryRefusal(null, false));
    }

    @Test
    public void retryOnALearnerPositionIsRefusedByRole() {
        Message learner = Message.user(BRANCH, 0, "why is the sky blue?", NOW);
        assertEquals(PositionCommands.Refusal.NOT_AN_AI_POSITION,
                PositionCommands.retryRefusal(learner, false));
    }

    @Test
    public void retryWhileAnAttemptIsRunningIsRefused() {
        assertEquals(PositionCommands.Refusal.ATTEMPT_ALREADY_RUNNING,
                PositionCommands.retryRefusal(aiPosition(null, null), true));
    }

    @Test
    public void retryIsAllowedOnAFailedPositionThatShowsNoVersionYet() {
        assertNull(PositionCommands.retryRefusal(aiPosition(null, null), false));
    }

    @Test
    public void retryIsAllowedOnAPositionThatAlreadyShowsAStableVersion() {
        assertNull(PositionCommands.retryRefusal(aiPosition(REVISION_1, null), false));
    }

    // ------------------------------------------------------------------ reset

    @Test
    public void resetWithoutAStableVersionIsRefused() {
        assertEquals(PositionCommands.Refusal.NO_STABLE_VERSION,
                PositionCommands.resetRefusal(aiPosition(null, null), false));
    }

    @Test
    public void resetOnAShownStableVersionIsAllowed() {
        assertNull(PositionCommands.resetRefusal(aiPosition(REVISION_1, null), false));
    }

    @Test
    public void resetWhileAnAttemptIsRunningIsRefused() {
        assertEquals(PositionCommands.Refusal.ATTEMPT_ALREADY_RUNNING,
                PositionCommands.resetRefusal(aiPosition(REVISION_1, null), true));
    }

    @Test
    public void resetOnALearnerPositionIsRefusedByRole() {
        Message learner = Message.user(BRANCH, 0, "why is the sky blue?", NOW);
        assertEquals(PositionCommands.Refusal.NOT_AN_AI_POSITION,
                PositionCommands.resetRefusal(learner, false));
    }

    // ----------------------------------------------------------------- select

    @Test
    public void selectingTheVersionAlreadyShownIsRefused() {
        Message position = aiPosition(REVISION_2, null);
        assertEquals(PositionCommands.Refusal.REVISION_ALREADY_ACTIVE,
                PositionCommands.selectRefusal(position, REVISION_2, position.id, false));
    }

    @Test
    public void selectingAVersionOfAnotherPositionIsRefused() {
        Message position = aiPosition(REVISION_2, null);
        assertEquals(PositionCommands.Refusal.REVISION_NOT_OF_THIS_POSITION,
                PositionCommands.selectRefusal(position, REVISION_1, Guard.newId(), false));
    }

    @Test
    public void selectingAVersionThatDoesNotExistIsRefused() {
        Message position = aiPosition(REVISION_2, null);
        assertEquals(PositionCommands.Refusal.REVISION_NOT_OF_THIS_POSITION,
                PositionCommands.selectRefusal(position, UNOWNED_REVISION, null, false));
    }

    @Test
    public void selectingAnOlderVersionOfTheSamePositionIsAllowed() {
        Message position = aiPosition(REVISION_2, null);
        assertNull(PositionCommands.selectRefusal(position, REVISION_1, position.id, false));
    }

    @Test
    public void selectingAVersionOnALearnerPositionIsRefusedByRole() {
        Message learner = Message.user(BRANCH, 0, "why is the sky blue?", NOW);
        assertEquals(PositionCommands.Refusal.NOT_AN_AI_POSITION,
                PositionCommands.selectRefusal(learner, REVISION_1, learner.id, false));
    }

    @Test
    public void selectingAFirstVersionOnAPositionThatShowsNoneIsAllowed() {
        Message position = aiPosition(null, null);
        assertNull(PositionCommands.selectRefusal(position, REVISION_1, position.id, false));
    }

    @Test
    public void selectingWhileAnAttemptIsRunningIsRefused() {
        Message position = aiPosition(REVISION_1, OPERATION_1);
        assertEquals(PositionCommands.Refusal.ATTEMPT_ALREADY_RUNNING,
                PositionCommands.selectRefusal(position, REVISION_2, position.id, true));
    }

    // ------------------------------------------------- agreement with the domain

    /**
     * This pre-check exists because a refusal has to happen <em>before</em> an operation row is written
     * and a call opened, while the domain's own guards run on the position afterwards. Two places
     * judging one question is exactly how the same input ends up answered two ways, so this test
     * compares them on the same inputs: for each case the pre-check's refusal and the domain method's
     * error code must describe the same judgement, and the case both allow must be allowed by both.
     */
    @Test
    public void thePreCheckAgreesWithTheDomainsOwnGuardsOnTheSameInputs() {
        Message learner = Message.user(BRANCH, 0, "why is the sky blue?", NOW);
        assertEquals(PositionCommands.Refusal.NOT_AN_AI_POSITION,
                PositionCommands.retryRefusal(learner, false));
        assertEquals(DomainErrorCode.MESSAGE_ROLE_MISMATCH,
                learner.bindOperation(Guard.newId()).error().code);

        Message busy = aiPosition(REVISION_1, OPERATION_1);
        assertEquals(PositionCommands.Refusal.ATTEMPT_ALREADY_RUNNING,
                PositionCommands.retryRefusal(busy, true));
        assertEquals(DomainErrorCode.ILLEGAL_STATE_TRANSITION,
                busy.bindOperation(Guard.newId()).error().code);
        assertEquals(PositionCommands.Refusal.ATTEMPT_ALREADY_RUNNING,
                PositionCommands.selectRefusal(busy, REVISION_2, busy.id, true));
        assertEquals(DomainErrorCode.ILLEGAL_STATE_TRANSITION,
                busy.activateRevision(REVISION_2).error().code);

        Message idling = aiPosition(REVISION_1, null);
        assertEquals(PositionCommands.Refusal.REVISION_ALREADY_ACTIVE,
                PositionCommands.selectRefusal(idling, REVISION_1, idling.id, false));
        assertEquals(DomainErrorCode.REVISION_IMMUTABLE,
                idling.activateRevision(REVISION_1).error().code);

        Message switching = aiPosition(REVISION_2, null);
        assertNull("both entry points must allow this one",
                PositionCommands.selectRefusal(switching, REVISION_1, switching.id, false));
        assertTrue(switching.activateRevision(REVISION_1).isOk());
    }

    // ------------------------------------------------- refusal to error code

    /**
     * The one place a refusal becomes the error code a caller sees.
     *
     * <p>It is pinned here because {@code AnswerVersions} reports these codes to the UI: if the
     * mapping lived there it would be a second judgement about the same question, and the two could
     * drift the moment either side changed. Five of the six codes are the ones the domain's own
     * guards already return for the identical judgement; {@code MESSAGE_NOT_FOUND} and
     * {@code REVISION_NOT_OF_THIS_POSITION} share {@code OPERATION_NOT_COMPLETABLE}, because
     * {@code :core} has no code that separates "this position is gone" from "no such version of this
     * position", and inventing one there is outside this task's write scope.
     */
    @Test
    public void everyRefusalCarriesTheCodeTheDomainUsesForTheSameJudgement() {
        assertEquals(DomainErrorCode.OPERATION_NOT_COMPLETABLE,
                PositionCommands.codeFor(PositionCommands.Refusal.MESSAGE_NOT_FOUND));
        assertEquals(DomainErrorCode.MESSAGE_ROLE_MISMATCH,
                PositionCommands.codeFor(PositionCommands.Refusal.NOT_AN_AI_POSITION));
        assertEquals(DomainErrorCode.ILLEGAL_STATE_TRANSITION,
                PositionCommands.codeFor(PositionCommands.Refusal.ATTEMPT_ALREADY_RUNNING));
        assertEquals(DomainErrorCode.OPERATION_NOT_STABLE,
                PositionCommands.codeFor(PositionCommands.Refusal.NO_STABLE_VERSION));
        assertEquals(DomainErrorCode.OPERATION_NOT_COMPLETABLE,
                PositionCommands.codeFor(PositionCommands.Refusal.REVISION_NOT_OF_THIS_POSITION));
        assertEquals(DomainErrorCode.REVISION_IMMUTABLE,
                PositionCommands.codeFor(PositionCommands.Refusal.REVISION_ALREADY_ACTIVE));
    }
    // ------------------------------------------------------ replacement baseline

    @Test
    public void theReplacementCommandKeepsTheBaselineAndPinsTheGivenVersion() {
        PositionCommands.Baseline baseline = baseline(GenerationModel.PRO, true);
        DomainResult<GenerationCommand> replacement = baseline.commandFor(POLICY_VERSION, NOW + 1);

        assertTrue(replacement.isOk());
        GenerationCommand command = replacement.value();
        assertEquals("same branch", baseline.branchId, command.branchId);
        assertEquals("same position: a retry adds no learner message and no turn",
                baseline.messageId, command.messageId);
        assertEquals("same purpose", baseline.purpose, command.purpose);
        assertEquals("the model the attempt was admitted with, not the current setting",
                baseline.model, command.model);
        assertEquals("the thinking flag the attempt was admitted with",
                baseline.thinkingEnabled, command.thinkingEnabled);
        assertEquals(POLICY_VERSION, command.promptPolicyVersion);
        assertFalse("a retry is a new attempt, not a re-run of the same command",
                command.id.equals(baseline.commandFor(POLICY_VERSION, NOW + 2).value().id));
    }

    @Test
    public void theReplacementCommandRefusesAVersionNoPurposeOwns() {
        DomainResult<GenerationCommand> replacement =
                baseline(GenerationModel.FLASH, false).commandFor(FOREIGN_POLICY_VERSION, NOW + 1);

        assertTrue(replacement.isFailure());
        assertEquals(DomainErrorCode.PROMPT_POLICY_VERSION_MISMATCH, replacement.error().code);
    }

    @Test
    public void theReplacementCommandReportsAMissingVersionAsADeclaredFailure() {
        DomainResult<GenerationCommand> replacement =
                baseline(GenerationModel.FLASH, false).commandFor(null, NOW + 1);

        assertTrue("a missing version is a declared business condition, not a caller bug",
                replacement.isFailure());
        assertEquals(DomainErrorCode.PROMPT_POLICY_VERSION_REQUIRED, replacement.error().code);
    }

    @Test
    public void theBaselineRulesOutAMalformedPositionInsteadOfBuildingACommand() {
        try {
            new PositionCommands.Baseline(BRANCH, "", PromptPurpose.LEARNING_ANSWER,
                    GenerationModel.PRO, false);
            throw new AssertionError("a blank message id is a caller bug and must throw");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage() != null);
        }
    }

    private static PositionCommands.Baseline baseline(GenerationModel model, boolean thinking) {
        return new PositionCommands.Baseline(BRANCH, POSITION,
                PromptPurpose.LEARNING_ANSWER, model, thinking);
    }

    private static Message aiPosition(String activeRevisionId, String activeOperationId) {
        return Message.restore(Guard.newId(), BRANCH, MessageRole.AI, 1, null, NOW,
                activeRevisionId, activeOperationId);
    }
}
