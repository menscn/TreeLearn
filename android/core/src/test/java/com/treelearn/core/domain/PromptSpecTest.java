package com.treelearn.core.domain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

/**
 * {@code PromptSpec} is the vendor-neutral half of the prompt contract (prompt design V1.0 §3.1,
 * technical design §9.2). Two properties carry the design: a spec cannot be built without a body,
 * and a spec cannot carry a field that ties {@code :core} to a provider.
 *
 * <p>A third property is locked here because an audit found it broken: the policy-version decision
 * must be the same decision {@code GenerationCommand} makes, for the same input and through the same
 * channel. See {@link #theVersionDecisionIsTheSameOneGenerationCommandMakes}.
 */
public class PromptSpecTest {

    private static final String LEARNING = "tl-learning-answer/1.0.0";

    private static PromptSpec spec(PromptPurpose purpose, String version, String body) {
        DomainResult<PromptSpec> result = PromptSpec.of(purpose, version, body);
        assertTrue("expected a spec but got " + result, result.isOk());
        return result.value();
    }

    @Test
    public void aSpecCarriesItsPurposeVersionAndBody() {
        PromptSpec spec = spec(PromptPurpose.LEARNING_ANSWER, LEARNING, "You are a tutor.");
        assertEquals(PromptPurpose.LEARNING_ANSWER, spec.purpose);
        assertEquals(LEARNING, spec.policyVersion);
        assertEquals("You are a tutor.", spec.systemContent);
    }

    @Test
    public void aBlankSystemContentIsRefused() {
        for (String blank : new String[] { "", "   ", "\n\t  " }) {
            try {
                PromptSpec.of(PromptPurpose.LEARNING_ANSWER, LEARNING, blank);
                fail("a blank system content must not produce a spec but did: <" + blank + ">");
            } catch (IllegalArgumentException expected) {
                assertTrue("message must name the offending field but was: " + expected.getMessage(),
                        expected.getMessage().contains("systemContent"));
            }
        }
    }

    @Test
    public void aMissingBodyOrPurposeIsACallerBug() {
        try {
            PromptSpec.of(PromptPurpose.LEARNING_ANSWER, LEARNING, null);
            fail("a null body must not produce a spec");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("systemContent"));
        }
        try {
            PromptSpec.of(null, LEARNING, "body");
            fail("a null purpose must not produce a spec");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("purpose"));
        }
    }

    /**
     * An absent version is reported as a code, not thrown: §3.2 makes the policy version mandatory, so
     * "this spec pins no version" is a condition {@code :ai} must report. {@code PromptSpec} is one of
     * the three entry points {@link Guard} lists as exceptions to its "missing argument throws" rule,
     * together with {@code AnswerRevision.create} and {@code GenerationCommand.create}.
     */
    @Test
    public void aBlankVersionIsReportedAsTheMissingPolicyCode() {
        for (String blank : new String[] { null, "", "   " }) {
            DomainResult<PromptSpec> result =
                    PromptSpec.of(PromptPurpose.LEARNING_ANSWER, blank, "body");
            assertTrue("a spec without a version must not be built: <" + blank + ">", result.isFailure());
            assertEquals("failure for <" + blank + ">",
                    DomainErrorCode.PROMPT_POLICY_VERSION_REQUIRED, result.error().code);
        }
    }

    /**
     * The body and the version are supplied side by side, so handing over the body of one policy
     * under another policy's version is a wiring mistake rather than a runtime condition: the
     * registry holds both as constants. It is still reported as a <em>business refusal</em> rather
     * than thrown, because both arguments are individually well formed and only their pairing is
     * wrong — the same shape as {@code ANCHOR_INVALID}, and the same decision
     * {@link GenerationCommand#create} makes for the identical input. That equality is asserted
     * separately below so the two cannot drift apart again.
     */
    @Test
    public void aVersionOwnedByAnotherPurposeIsReportedAsAMismatch() {
        DomainResult<PromptSpec> foreign =
                PromptSpec.of(PromptPurpose.ROOT_TITLE, LEARNING, "body");
        assertTrue("a version owned by another purpose must not be accepted", foreign.isFailure());
        assertEquals(DomainErrorCode.PROMPT_POLICY_VERSION_MISMATCH, foreign.error().code);

        DomainResult<PromptSpec> prefixless =
                PromptSpec.of(PromptPurpose.LEARNING_ANSWER, "1.0.0", "body");
        assertTrue("a version without its purpose prefix must not be accepted", prefixless.isFailure());
        assertEquals(DomainErrorCode.PROMPT_POLICY_VERSION_MISMATCH, prefixless.error().code);
    }

    /**
     * The regression lock for the defect an independent audit found in round 1: the two prompt-policy
     * entry points answered the same input through opposite channels, so a caller had to know which
     * factory it was holding to know whether to catch or to inspect. Both now route through
     * {@link PromptPurpose#versionFailureFor}, and this test compares them directly on the same
     * inputs rather than trusting that they do.
     */
    @Test
    public void theVersionDecisionIsTheSameOneGenerationCommandMakes() {
        String[] versions = { null, "", "   ", LEARNING, "1.0.0", "tl-root-title/1.0.0",
                "tl-learning-answer/1.0", "tl-learning-answer/01.0.0" };

        for (String version : versions) {
            DomainResult<PromptSpec> fromSpec =
                    PromptSpec.of(PromptPurpose.LEARNING_ANSWER, version, "body");
            DomainResult<GenerationCommand> fromCommand = GenerationCommand.create(
                    "0f4d2a1e-5c6b-4a7d-8e9f-0a1b2c3d4e5f",
                    "1f4d2a1e-5c6b-4a7d-8e9f-0a1b2c3d4e5f",
                    PromptPurpose.LEARNING_ANSWER,
                    version,
                    GenerationModel.PRO,
                    true,
                    1_700_000_000_000L);

            assertEquals("ok/failure for version <" + version + ">",
                    fromSpec.isOk(), fromCommand.isOk());
            if (fromSpec.isFailure()) {
                assertNotNull("a failure must carry a code", fromSpec.error().code);
                assertEquals("error code for version <" + version + ">",
                        fromSpec.error().code, fromCommand.error().code);
            }
        }
    }

    /**
     * Mechanically enforces "does not reference any DeepSeek type": the declared field set is exactly
     * the three vendor-neutral members, and every field type is either a JDK type or a domain type.
     * A provider-shaped field (a request flag, a thinking control, a DTO) would fail this.
     */
    @Test
    public void theSpecHasNoProviderSpecificField() {
        List<String> allowed = Arrays.asList("purpose", "policyVersion", "systemContent");
        Field[] fields = PromptSpec.class.getDeclaredFields();
        assertEquals("PromptSpec must declare exactly the three contract members", 3, fields.length);
        for (Field field : fields) {
            assertTrue(field.getName() + " is not part of the vendor-neutral contract",
                    allowed.contains(field.getName()));
            String type = field.getType().getName();
            assertTrue(field.getName() + " has a provider-shaped type: " + type,
                    type.startsWith("java.lang.") || type.startsWith("com.treelearn.core.domain."));
        }
    }

    /**
     * §15 forbids prompt content in a log line; the description therefore keeps the identity and a
     * length instead of the body. The class javadoc claimed this and nothing asserted it — a
     * round-2-independent audit of T-021 found the property true but unpinned, so a future "make
     * toString useful" edit could have leaked the whole system prompt without a test failing.
     */
    @Test
    public void theDescriptionNeverCarriesThePromptBody() {
        PromptSpec spec = spec(PromptPurpose.LEARNING_ANSWER, LEARNING, "SECRET TUTOR PERSONA");
        String description = spec.toString();
        assertFalse("the body must not appear in a log line: " + description,
                description.contains("SECRET TUTOR PERSONA"));
        assertFalse(description.contains("SECRET"));
        assertTrue(description, description.contains("LEARNING_ANSWER"));
        assertTrue(description, description.contains(LEARNING));
        assertTrue("the length is the useful part: " + description,
                description.contains(spec.systemContent.length() + " body chars"));
    }
}
