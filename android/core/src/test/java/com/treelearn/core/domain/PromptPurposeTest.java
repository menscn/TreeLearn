package com.treelearn.core.domain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.junit.Test;

/**
 * Locks the prompt purpose list to the authoritative source:
 * {@code Docs/05-technical-design/知芽（TreeLearn）DeepSeek自适应学习导师提示词设计_V1.0.md} §3.1
 * ("首版 PromptPurpose") and its §6 version table.
 *
 * <p>A round-3-independent audit found the enum did not match that list — it had invented a separate
 * key-points purpose and was missing {@code ROOT_TITLE} and the recommendation purposes. These tests
 * exist so the mismatch cannot come back silently.
 */
public class PromptPurposeTest {

    /** §3.1 first-release list, in the order the document gives it. */
    private static final String[] AUTHORITATIVE_NAMES = {
            "LEARNING_ANSWER",
            "ROOT_TITLE",
            "BRANCH_TITLE",
            "BRANCH_SUMMARY",
            "ANSWER_RECOMMENDATIONS",
            "HOME_RECOMMENDATIONS",
            "CONTEXT_COMPRESSION",
    };

    /** §6 version table. */
    private static final String[] AUTHORITATIVE_POLICY_VERSIONS = {
            "tl-learning-answer/1.0.0",
            "tl-root-title/1.0.0",
            "tl-branch-title/1.0.0",
            "tl-branch-summary/1.0.0",
            "tl-answer-recommendations/1.0.0",
            "tl-home-recommendations/1.0.0",
            "tl-context-compression/1.0.0",
    };

    @Test
    public void theEnumMatchesTheAuthoritativeFirstReleaseListExactly() {
        Set<String> declared = new HashSet<String>();
        for (PromptPurpose purpose : PromptPurpose.values()) {
            declared.add(purpose.name());
        }
        Set<String> expected = new HashSet<String>(Arrays.asList(AUTHORITATIVE_NAMES));
        assertEquals("PromptPurpose must match prompt design V1.0 §3.1 exactly", expected, declared);
        assertEquals(AUTHORITATIVE_NAMES.length, PromptPurpose.values().length);
    }

    /**
     * The document lists the purposes in a meaningful order (teaching answer first, derived usages
     * after). A round-2-independent audit showed a set-only assertion let the order be permuted
     * without any test failing, so the order is locked here too.
     */
    @Test
    public void theDeclarationOrderMatchesTheDocument() {
        PromptPurpose[] values = PromptPurpose.values();
        for (int i = 0; i < AUTHORITATIVE_NAMES.length; i++) {
            assertEquals("position " + i + " of the §3.1 list",
                    AUTHORITATIVE_NAMES[i], values[i].name());
        }
    }

    @Test
    public void thereIsNoSeparateKeyPointsPurpose() {
        for (PromptPurpose purpose : PromptPurpose.values()) {
            assertFalse("AI key points are a payload field of BRANCH_SUMMARY, not a purpose",
                    purpose.name().contains("KEY_POINT"));
        }
    }

    @Test
    public void everyPurposeOwnsItsDocumentedVersion() {
        assertEquals(AUTHORITATIVE_POLICY_VERSIONS.length, PromptPurpose.values().length);
        for (String version : AUTHORITATIVE_POLICY_VERSIONS) {
            PromptPurpose owner = PromptPurpose.ownerOf(version);
            assertTrue("no purpose owns " + version, owner != null);
            assertEquals(version, owner.policyPrefix + "/1.0.0");
        }
    }

    @Test
    public void everyPrefixIsUnambiguous() {
        for (PromptPurpose a : PromptPurpose.values()) {
            for (PromptPurpose b : PromptPurpose.values()) {
                if (a == b) {
                    continue;
                }
                assertFalse("prefix of " + a + " must not be claimed by " + b,
                        b.ownsVersion(a.policyPrefix + "/1.0.0"));
            }
        }
    }

    @Test
    public void aVersionOfAnotherPurposeIsRefused() {
        assertFalse(PromptPurpose.BRANCH_TITLE.ownsVersion("tl-learning-answer/1.0.0"));
        assertFalse(PromptPurpose.LEARNING_ANSWER.ownsVersion("tl-root-title/1.0.0"));
        assertNull(PromptPurpose.ownerOf("tl-not-a-purpose/1.0.0"));
    }

    @Test
    public void aNonSemverSuffixIsRefused() {
        for (String bad : new String[] {
                "tl-learning-answer/garbage",
                "tl-learning-answer/1.0",
                "tl-learning-answer/1",
                "tl-learning-answer/",
                "tl-learning-answer",
                "tl-learning-answer/1.0.0-beta",
                "tl-learning-answer/1.0.0 ",
                "tl-learning-answer/1.0.0\t",
                "tl-learning-answer/1.0.0\n",
                // A round-2-independent audit found these were accepted: they are not canonical
                // semver, and a date-shaped value is explicitly forbidden by design §3.2.
                "tl-learning-answer/01.0.0",
                "tl-learning-answer/1.0.00",
                "tl-learning-answer/2026.09.21",
                // A round-3-independent audit found the components were unbounded, so a value that
                // would overflow an int on a future parse was accepted.
                "tl-learning-answer/99999999999999999999.0.0",
                "tl-learning-answer/1.0000000000.0",
        }) {
            assertFalse("<" + bad + "> must be refused", PromptPurpose.LEARNING_ANSWER.ownsVersion(bad));
        }
        assertNull(PromptPurpose.ownerOf(null));
    }

    @Test
    public void aCanonicalSemverSuffixIsAccepted() {
        assertTrue(PromptPurpose.LEARNING_ANSWER.ownsVersion("tl-learning-answer/0.1.0"));
        assertTrue(PromptPurpose.LEARNING_ANSWER.ownsVersion("tl-learning-answer/1.0.0"));
        assertTrue(PromptPurpose.LEARNING_ANSWER.ownsVersion("tl-learning-answer/12.34.567"));
        assertTrue(PromptPurpose.LEARNING_ANSWER.ownsVersion("tl-learning-answer/999999999.0.0"));
    }

    @Test
    public void theTeachingAnswerPurposeOwnsTheDocumentedVersion() {
        assertTrue(PromptPurpose.LEARNING_ANSWER.ownsVersion("tl-learning-answer/1.0.0"));
        assertEquals("tl-learning-answer", PromptPurpose.LEARNING_ANSWER.policyPrefix);
    }

    /**
     * The shared definition of "this request pinned no version". It exists so a caller asking
     * {@link PromptPurpose#isAbsent} and a caller asking
     * {@link PromptPurpose#versionFailureFor} cannot disagree about what "absent" means — an audit
     * found the same check open-coded at three prompt-policy entry points.
     */
    @Test
    public void absenceOfAVersionHasOneDefinition() {
        for (String absent : new String[] { null, "", "   ", "\t", "\n" }) {
            assertTrue("<" + absent + "> must count as absent", PromptPurpose.isAbsent(absent));
        }
        for (String present : new String[] {
                "tl-learning-answer/1.0.0", " 1.0.0", "tl-learning-answer/", "garbage" }) {
            assertFalse("<" + present + "> must not count as absent",
                    PromptPurpose.isAbsent(present));
        }
    }

    /**
     * The single decision point for "may this purpose be used with this version". Both
     * {@code GenerationCommand.create} and {@code PromptSpec.of} delegate to it, and
     * {@code PromptSpecTest.theVersionDecisionIsTheSameOneGenerationCommandMakes} compares the two
     * entry points directly, so a drift between them cannot pass unnoticed again.
     */
    @Test
    public void theVersionDecisionIsExpressedAsADomainError() {
        assertEquals(DomainErrorCode.PROMPT_POLICY_VERSION_REQUIRED,
                PromptPurpose.versionFailureFor(PromptPurpose.LEARNING_ANSWER, null).code);
        assertEquals(DomainErrorCode.PROMPT_POLICY_VERSION_REQUIRED,
                PromptPurpose.versionFailureFor(PromptPurpose.LEARNING_ANSWER, "   ").code);
        assertEquals(DomainErrorCode.PROMPT_POLICY_VERSION_MISMATCH,
                PromptPurpose.versionFailureFor(
                        PromptPurpose.LEARNING_ANSWER, "tl-root-title/1.0.0").code);
        assertEquals(DomainErrorCode.PROMPT_POLICY_VERSION_MISMATCH,
                PromptPurpose.versionFailureFor(PromptPurpose.LEARNING_ANSWER, "1.0.0").code);
        assertNull("a version owned by the declared purpose is usable",
                PromptPurpose.versionFailureFor(
                        PromptPurpose.LEARNING_ANSWER, "tl-learning-answer/1.0.0"));
        try {
            PromptPurpose.versionFailureFor(null, "tl-learning-answer/1.0.0");
            fail("a null purpose is a caller bug and must throw, per the error-channel rule");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("purpose"));
        }
    }
}
