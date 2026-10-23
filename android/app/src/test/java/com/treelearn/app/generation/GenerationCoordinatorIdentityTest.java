package com.treelearn.app.generation;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.treelearn.ai.prompt.DeepSeekRequest;
import com.treelearn.ai.prompt.DeepSeekRequestAssembler;
import com.treelearn.ai.prompt.PromptBudget;
import com.treelearn.core.domain.DomainResult;
import com.treelearn.core.domain.GenerationCommand;
import com.treelearn.core.domain.GenerationModel;
import com.treelearn.core.domain.Persona;
import com.treelearn.core.domain.PromptPurpose;

import java.util.Collections;

import org.junit.Test;

/**
 * The coordinator's identity check is conjunctive: version <em>and</em> style
 * (SPEC-2026-09-21-ai-persona-preference 状态迁移).
 *
 * <p>The two dimensions are pinned by different modules — the command carries what the topic row
 * said, the assembler copies it onto the request — so a disagreement in either one means the row
 * about to be written would record an identity that was never sent. A check that only compared the
 * version would let a request composed with another style pass while the attempt claims the
 * topic's style, which is exactly the false witness this suite exists to prevent.
 */
public class GenerationCoordinatorIdentityTest {

    private static final String BRANCH = "11111111-1111-4111-8111-111111111111";
    private static final String MESSAGE = "22222222-2222-4222-8222-222222222222";
    private static final long NOW = 1_700_000_000_000L;

    private static GenerationCommand command(Persona persona) {
        DomainResult<GenerationCommand> created = GenerationCommand.create(BRANCH, MESSAGE,
                PromptPurpose.LEARNING_ANSWER, "tl-learning-answer/1.1.0", GenerationModel.FLASH,
                false, persona, NOW);
        assertTrue(created.toString(), created.isOk());
        return created.value();
    }

    private static DeepSeekRequest learningAnswer(Persona persona) {
        return DeepSeekRequestAssembler.assemble(PromptPurpose.LEARNING_ANSWER, false,
                Collections.emptyList(), "问题", persona, new PromptBudget(1_000_000, 512));
    }

    @Test
    public void theSameVersionAndStyleMatch() {
        assertTrue(GenerationCoordinator.requestMatches(command(Persona.STUDY_BUDDY),
                learningAnswer(Persona.STUDY_BUDDY)));
    }

    @Test
    public void aDifferentStyleIsRefusedEvenWhenTheVersionAgrees() {
        assertFalse("the version alone does not identify the request",
                GenerationCoordinator.requestMatches(command(Persona.STRICT_MENTOR),
                        learningAnswer(Persona.DEFAULT)));
    }

    @Test
    public void aDifferentVersionIsRefusedEvenWhenTheStyleAgrees() {
        DeepSeekRequest otherPurpose = DeepSeekRequestAssembler.assemble(
                PromptPurpose.ROOT_TITLE, false, Collections.emptyList(), "{}", Persona.DEFAULT,
                new PromptBudget(1_000_000, 512));
        assertFalse(GenerationCoordinator.requestMatches(command(Persona.DEFAULT), otherPurpose));
    }

    @Test
    public void aMissingRequestNeverMatches() {
        assertFalse(GenerationCoordinator.requestMatches(command(Persona.DEFAULT), null));
    }
}
