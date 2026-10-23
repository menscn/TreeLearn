package com.treelearn.core.domain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * The persona is part of the generation identity from the command through the attempt into the
 * stable revision (SPEC-2026-09-21-ai-persona-preference, 技术方案 §5.1, §9.2).
 *
 * <p>It travels the same route the pinned policy version already travels: fixed before the remote
 * call, copied unchanged, and never re-derived when an answer is stabilized. That is what makes
 * "each successful answer records the style that produced it" reproducible after the topic's
 * current choice has moved on.
 */
public class PersonaIdentityTest {

    private static final String BRANCH = "11111111-1111-4111-8111-111111111111";
    private static final String MESSAGE = "22222222-2222-4222-8222-222222222222";
    private static final String VERSION = "tl-learning-answer/1.1.0";
    private static final long NOW = 1_700_000_000_000L;

    private static GenerationCommand command(Persona persona) {
        DomainResult<GenerationCommand> created = GenerationCommand.create(
                BRANCH, MESSAGE, PromptPurpose.LEARNING_ANSWER, VERSION, GenerationModel.FLASH,
                true, persona, NOW);
        assertTrue(created.toString(), created.isOk());
        return created.value();
    }

    @Test
    public void aCommandPinsThePersonaItWasCreatedWith() {
        GenerationCommand command = command(Persona.STRICT_MENTOR);
        assertEquals(Persona.STRICT_MENTOR, command.persona);
        assertTrue("identity, not content, may be described: " + command,
                command.toString().contains(Persona.STRICT_MENTOR.wireId));
    }

    @Test
    public void theOlderFactoryStaysSourceCompatibleAndMeansTheDefault() {
        GenerationCommand command = GenerationCommand.create(
                BRANCH, MESSAGE, PromptPurpose.LEARNING_ANSWER, VERSION, GenerationModel.FLASH,
                false, NOW).value();
        assertEquals(Persona.DEFAULT, command.persona);
    }

    @Test
    public void aPersonaIsMandatoryOnTheNewFactory() {
        try {
            GenerationCommand.create(BRANCH, MESSAGE, PromptPurpose.LEARNING_ANSWER, VERSION,
                    GenerationModel.FLASH, true, null, NOW);
            throw new AssertionError("a null persona must be a caller bug, not a default");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("persona"));
        }
    }

    @Test
    public void theAttemptAndTheStableRevisionCarryTheCommandsPersona() {
        GenerationCommand command = command(Persona.LATE_NIGHT);
        GenerationOperation operation = GenerationOperation.start(command, NOW);
        assertEquals(Persona.LATE_NIGHT, operation.persona);

        operation.advanceTo(GenerationState.VALIDATING);
        operation.advanceTo(GenerationState.SUBMITTING);
        operation.appendAnswer("answer");
        DomainResult<AnswerRevision> stabilized = operation.stabilize(1, NOW + 10);
        assertTrue(stabilized.toString(), stabilized.isOk());
        AnswerRevision revision = stabilized.value();

        assertEquals(Persona.LATE_NIGHT, revision.persona);
        assertTrue("the stored identity may be described: " + revision,
                revision.toString().contains(Persona.LATE_NIGHT.wireId));
        assertEquals("the pinned version is unaffected by the second dimension",
                VERSION, revision.promptPolicyVersion);
    }

    @Test
    public void theOlderRevisionFactoriesStillMeanTheDefault() {
        AnswerRevision created = AnswerRevision.create(MESSAGE, 1, "", "answer",
                GenerationModel.FLASH, false, VERSION, NOW).value();
        assertEquals(Persona.DEFAULT, created.persona);

        AnswerRevision restored = AnswerRevision.restore(Guard.newId(), MESSAGE, 1, "", "answer",
                GenerationModel.FLASH, false, VERSION, NOW);
        assertEquals(Persona.DEFAULT, restored.persona);
    }

    @Test
    public void restoreKeepsThePersonaTheRowStored() {
        AnswerRevision restored = AnswerRevision.restore(Guard.newId(), MESSAGE, 2, "", "answer",
                GenerationModel.PRO, true, Persona.SARCASTIC_FRIEND, VERSION, NOW);
        assertEquals(Persona.SARCASTIC_FRIEND, restored.persona);
    }

    @Test
    public void aCommandDescriptionCarriesNoPromptText() {
        GenerationCommand command = command(Persona.HAND_HOLDING);
        String description = command.toString();
        assertFalse("a command holds no prompt body: " + description,
                description.contains("You are"));
        assertFalse("and no persona addendum: " + description,
                description.contains("回答风格"));
    }
}
