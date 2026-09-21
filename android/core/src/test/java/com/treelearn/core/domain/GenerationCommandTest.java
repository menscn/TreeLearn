package com.treelearn.core.domain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.UUID;
import org.junit.Test;

/**
 * A generation command pins the prompt policy version <em>before</em> the remote submission boundary
 * (technical design §9.2). Everything here is about that pinning: the version must be present, must
 * belong to the declared purpose, and must survive untouched into the operation and the revision.
 */
public class GenerationCommandTest {

    private static final String BRANCH = "11111111-1111-4111-8111-111111111111";
    private static final String MESSAGE = "22222222-2222-4222-8222-222222222222";
    private static final String LEARNING = "tl-learning-answer/1.0.0";

    private static DomainResult<GenerationCommand> command(PromptPurpose purpose, String version) {
        return GenerationCommand.create(
                BRANCH, MESSAGE, purpose, version, GenerationModel.FLASH, true, 1_700_000_000_000L);
    }

    @Test
    public void aCommandPinsTheVersionOfItsDeclaredPurpose() {
        DomainResult<GenerationCommand> result = command(PromptPurpose.LEARNING_ANSWER, LEARNING);
        assertTrue("a well-formed command must be accepted: " + result, result.isOk());
        GenerationCommand created = result.value();
        assertEquals(BRANCH, created.branchId);
        assertEquals(MESSAGE, created.messageId);
        assertEquals(PromptPurpose.LEARNING_ANSWER, created.purpose);
        assertEquals(LEARNING, created.promptPolicyVersion);
        assertEquals(GenerationModel.FLASH, created.model);
        assertTrue(created.thinkingEnabled);
        assertEquals(1_700_000_000_000L, created.createdAt);
        // Ids are application-generated UUIDs; a caller never supplies one for a command.
        assertEquals(created.id, UUID.fromString(created.id).toString());
    }

    /**
     * No normalisation may happen on the way in. The interesting inputs are the ones a "tidy up"
     * implementation would rewrite: the longest legal semver (nine digits per component, the cap
     * {@code PromptPurpose.SEMVER} allows) and a multi-digit patch. Note that a version cannot be
     * tested with surrounding whitespace, because {@code ownsVersion} already refuses it — that
     * refusal is asserted in {@link #versionLookalikesAreRefused}.
     */
    @Test
    public void theVersionIsPinnedWithoutReformatting() {
        String longest = "tl-learning-answer/999999999.0.999999999";
        GenerationCommand created = command(PromptPurpose.LEARNING_ANSWER, longest).value();
        assertEquals("the nine-digit boundary of the semver rule must survive unchanged",
                longest, created.promptPolicyVersion);
    }

    @Test
    public void aMissingVersionIsRefusedWithThePromptPolicyCode() {
        for (String missing : new String[] { null, "", "   " }) {
            DomainResult<GenerationCommand> result =
                    command(PromptPurpose.LEARNING_ANSWER, missing);
            assertTrue("a command without a version must be refused: <" + missing + ">",
                    result.isFailure());
            assertEquals(DomainErrorCode.PROMPT_POLICY_VERSION_REQUIRED, result.error().code);
        }
    }

    @Test
    public void aVersionOwnedByAnotherPurposeIsRefused() {
        DomainResult<GenerationCommand> result = command(PromptPurpose.ROOT_TITLE, LEARNING);
        assertTrue("a version of another purpose must be refused", result.isFailure());
        assertEquals(DomainErrorCode.PROMPT_POLICY_VERSION_MISMATCH, result.error().code);
    }

    /**
     * §3.2 forbids app versions, timestamps and any other stand-in. A bare semver, a date-shaped
     * value and a leading-zero component must all be refused even though they look version-like.
     */
    @Test
    public void versionLookalikesAreRefused() {
        for (String lookalike : new String[] {
                "1.0.0",
                "tl-learning-answer/2026.09.21",
                "tl-learning-answer/01.0.0",
                "tl-learning-answer/1.0",
                "tl-learning-answer/1.0.0.1",
                "tl-learning-answer/",
        }) {
            DomainResult<GenerationCommand> result =
                    command(PromptPurpose.LEARNING_ANSWER, lookalike);
            assertTrue("must be refused as a policy version: " + lookalike, result.isFailure());
            assertEquals("must be refused as a policy version: " + lookalike,
                    DomainErrorCode.PROMPT_POLICY_VERSION_MISMATCH, result.error().code);
        }
    }

    /**
     * The error-channel rule in {@link Guard}: the channel is chosen by the <em>kind</em> of mistake,
     * not by convenience, so a missing identifier throws while a missing policy version reports a
     * code. The version is a declared business condition — it is one of the three entry points listed
     * in {@link Guard} as exceptions to that rule, with {@code AnswerRevision.create} and
     * {@code PromptSpec.of} — while an identifier is not.
     */
    @Test
    public void malformedArgumentsAreCallerBugsNotBusinessFailures() {
        Object[][] invalid = {
                { null, MESSAGE, PromptPurpose.LEARNING_ANSWER, LEARNING, GenerationModel.FLASH },
                { "not-a-uuid", MESSAGE, PromptPurpose.LEARNING_ANSWER, LEARNING, GenerationModel.FLASH },
                { BRANCH, null, PromptPurpose.LEARNING_ANSWER, LEARNING, GenerationModel.FLASH },
                { BRANCH, MESSAGE, null, LEARNING, GenerationModel.FLASH },
                { BRANCH, MESSAGE, PromptPurpose.LEARNING_ANSWER, LEARNING, null },
        };
        for (Object[] row : invalid) {
            try {
                GenerationCommand.create(
                        (String) row[0], (String) row[1], (PromptPurpose) row[2], (String) row[3],
                        (GenerationModel) row[4], false, 1L);
                fail("malformed argument must throw rather than report a business failure: "
                        + java.util.Arrays.toString(row));
            } catch (IllegalArgumentException expected) {
                assertFalse(expected.getMessage() == null || expected.getMessage().isEmpty());
            }
        }
        try {
            GenerationCommand.create(
                    BRANCH, MESSAGE, PromptPurpose.LEARNING_ANSWER, LEARNING,
                    GenerationModel.FLASH, false, -1L);
            fail("a negative timestamp must throw");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("createdAt"));
        }
    }

    /**
     * The declared check order of the package, applied to this entry point: argument shapes are
     * reported before the policy decision, so a call that carries <em>both</em> a malformed argument
     * and an unusable version still surfaces the caller bug.
     *
     * <p>Without this order the malformed identifier would be answered with
     * {@code PROMPT_POLICY_VERSION_REQUIRED}, which reads as "your input is fine, just pin a version"
     * — the caller would fix the wrong thing and keep the bug. A round-1-independent audit of T-021
     * observed exactly that shadowing here.
     */
    @Test
    public void aCallerBugIsReportedBeforeTheVersionDecision() {
        String[] malformedIds = { "not-a-uuid", null };
        for (String malformed : malformedIds) {
            try {
                GenerationCommand.create(
                        malformed, MESSAGE, PromptPurpose.LEARNING_ANSWER, null,
                        GenerationModel.FLASH, false, 1_700_000_000_000L);
                fail("a malformed branchId plus a missing version must throw, not report a code");
            } catch (IllegalArgumentException expected) {
                assertTrue("message must name the offending field but was: " + expected.getMessage(),
                        expected.getMessage().contains("branchId"));
            }
        }
        try {
            GenerationCommand.create(
                    BRANCH, MESSAGE, null, null, GenerationModel.FLASH, false, 1_700_000_000_000L);
            fail("a null purpose plus a missing version must throw, not report a code");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("purpose"));
        }
    }

    /**
     * Criterion 2 in mechanical form: the pin lives in a {@code final} field, so no code path —
     * including a future one — can reassign it after construction.
     */
    @Test
    public void thePinnedVersionIsFinal() throws Exception {
        Field field = GenerationCommand.class.getField("promptPolicyVersion");
        assertTrue("promptPolicyVersion must be final", Modifier.isFinal(field.getModifiers()));
        for (java.lang.reflect.Method method : GenerationCommand.class.getDeclaredMethods()) {
            assertFalse("no setter may exist on a pinned command: " + method.getName(),
                    method.getName().toLowerCase().startsWith("set"));
        }
    }

    /**
     * The command carries no conversation text and no prompt body, so its description is allowed to
     * name the identity, the pinned version and the model tier — and must not carry anything else.
     * Nothing asserted this before a round-2-independent audit of T-021 pointed out that this
     * {@code toString} was one of two with a §15-style claim and no test behind it.
     */
    @Test
    public void theDescriptionNamesThePinAndCarriesNoPromptText() {
        GenerationCommand created = command(PromptPurpose.LEARNING_ANSWER, LEARNING).value();
        String description = created.toString();
        assertTrue(description, description.contains(MESSAGE));
        assertTrue(description, description.contains("LEARNING_ANSWER"));
        assertTrue(description, description.contains(LEARNING));
        assertTrue(description, description.contains(GenerationModel.FLASH.wireId));
        assertTrue("the thinking flag is part of the pin: " + description,
                description.contains("thinking"));
        assertFalse("a command has no prompt body to leak, so none may appear: " + description,
                description.contains("You are"));
    }
}
