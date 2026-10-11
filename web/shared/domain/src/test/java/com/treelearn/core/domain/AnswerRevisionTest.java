package com.treelearn.core.domain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import org.junit.Test;

/**
 * The immutability contract of a stable answer version, and the reason a reset can never rewrite
 * history that an existing child branch still depends on (technical design 5.1).
 */
public class AnswerRevisionTest {

    private static final long NOW = 1_700_000_000_000L;
    private static final String MESSAGE_ID = "3f0c0f4e-6b3a-4a1e-9c2f-7a5d1e2b4c60";
    private static final String POLICY_V1 = "tl-learning-answer/1.0.0";
    private static final String POLICY_V1_1 = "tl-learning-answer/1.1.0";

    private static AnswerRevision revision(
            String messageId, int no, String reasoning, String answer, String policy) {
        DomainResult<AnswerRevision> result = AnswerRevision.create(
                messageId, no, reasoning, answer, GenerationModel.PRO, true, policy, NOW);
        assertTrue("expected a revision, got " + result, result.isOk());
        return result.value();
    }

    @Test
    public void allFieldsAreFinalAndNoSetterExists() throws Exception {
        for (Field field : AnswerRevision.class.getDeclaredFields()) {
            if (field.isSynthetic() || Modifier.isStatic(field.getModifiers())) {
                continue;
            }
            assertTrue("field must be final: " + field.getName(),
                    Modifier.isFinal(field.getModifiers()));
        }
        for (Method method : AnswerRevision.class.getMethods()) {
            if (method.getDeclaringClass() == Object.class) {
                continue;
            }
            assertFalse("a stable revision must expose no mutator: " + method.getName(),
                    method.getName().startsWith("set"));
        }
    }

    @Test
    public void policyVersionIsCopiedVerbatim() {
        AnswerRevision created = revision(MESSAGE_ID, 1, "r", "a", POLICY_V1);
        assertEquals(POLICY_V1, created.promptPolicyVersion);
    }

    @Test
    public void missingOrBlankPolicyVersionIsRefused() {
        for (String policy : new String[] {null, "", "   ", "\t"}) {
            DomainResult<AnswerRevision> result = AnswerRevision.create(
                    MESSAGE_ID, 1, "r", "a", GenerationModel.PRO, true, policy, NOW);
            assertTrue("policy <" + policy + "> must be refused", result.isFailure());
            assertEquals(DomainErrorCode.PROMPT_POLICY_VERSION_REQUIRED, result.error().code);
        }
    }

    @Test
    public void aPolicyVersionThatNoPurposeOwnsIsRefused() {
        for (String bad : new String[] {
                "tl-not-a-purpose/1.0.0",
                "2026-09-21T10:00:00Z",
                "release-1.2.3",
                "tl-learning-answer/1.0",
                "tl-learning-answer/garbage",
                "1.0.0",
        }) {
            DomainResult<AnswerRevision> result = AnswerRevision.create(
                    MESSAGE_ID, 1, "r", "a", GenerationModel.PRO, true, bad, NOW);
            assertTrue("<" + bad + "> must be refused", result.isFailure());
            assertEquals("wrong code for <" + bad + ">",
                    DomainErrorCode.PROMPT_POLICY_VERSION_MISMATCH, result.error().code);
        }
    }

    @Test
    public void theConstructorIsNotAPublicCreationPath() {
        for (java.lang.reflect.Constructor<?> constructor : AnswerRevision.class.getDeclaredConstructors()) {
            assertTrue("AnswerRevision must not expose a public constructor: " + constructor,
                    Modifier.isPrivate(constructor.getModifiers()));
        }
    }

    @Test
    public void restoreIsRehydrationOnly() {
        String id = Guard.newId();
        AnswerRevision restored = AnswerRevision.restore(
                id, MESSAGE_ID, 3, "reasoning", "answer", GenerationModel.FLASH, false, POLICY_V1, NOW);
        assertEquals(id, restored.id);
        assertEquals(MESSAGE_ID, restored.messageId);
        assertEquals(3, restored.revisionNo);
        assertEquals(POLICY_V1, restored.promptPolicyVersion);
    }

    @Test
    public void blankAnswerCannotBecomeStable() {
        try {
            AnswerRevision.create(MESSAGE_ID, 1, "some reasoning", "   ", GenerationModel.PRO, true, POLICY_V1, NOW);
            fail("a blank final answer must never be frozen into a stable revision");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("answerContent"));
        }
    }

    @Test
    public void revisionNumberMustBePositive() {
        try {
            AnswerRevision.create(MESSAGE_ID, 0, "r", "a", GenerationModel.PRO, true, POLICY_V1, NOW);
            fail("revision numbers start at 1");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("revisionNo"));
        }
    }

    /**
     * The declared check order of the package, applied to the fourth entry point that returns a
     * {@code DomainResult}: argument shapes are reported before the policy decision.
     *
     * <p>This entry point was the one missed when the rule was first written down. With the version
     * checked first, a single call carrying four separate caller bugs (a malformed message id, a
     * zero revision number, a blank answer and a negative timestamp) was answered with
     * {@code PROMPT_POLICY_VERSION_REQUIRED} — one business code hiding four programming errors,
     * and a caller would "fix" it by pinning a version and keep every one of them.
     */
    @Test
    public void aCallerBugIsReportedBeforeTheVersionDecision() {
        assertRejectedBeforeTheVersion(null, 1, "r", "a", NOW, "messageId");
        assertRejectedBeforeTheVersion("not-a-uuid", 1, "r", "a", NOW, "messageId");
        assertRejectedBeforeTheVersion(MESSAGE_ID, 0, "r", "a", NOW, "revisionNo");
        assertRejectedBeforeTheVersion(MESSAGE_ID, 1, "r", "   ", NOW, "answerContent");
        assertRejectedBeforeTheVersion(MESSAGE_ID, 1, null, "a", NOW, "reasoningContent");
        assertRejectedBeforeTheVersion(MESSAGE_ID, 1, "r", "a", -1L, "createdAt");
        try {
            AnswerRevision.create(MESSAGE_ID, 1, "r", "a", null, true, null, NOW);
            fail("a missing model must throw even when the version is missing too");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("model"));
        }
    }

    /** Asserts the shape fault named by {@code field} wins over an absent <em>and</em> a foreign version. */
    private static void assertRejectedBeforeTheVersion(
            String messageId, int no, String reasoning, String answer, long now, String field) {
        for (String version : new String[] { null, "tl-root-title/1.0.0" }) {
            try {
                AnswerRevision.create(
                        messageId, no, reasoning, answer, GenerationModel.PRO, true, version, now);
                fail("a malformed <" + field + "> must throw even when the version is unusable too");
            } catch (IllegalArgumentException expected) {
                assertTrue("expected the " + field + " fault to win but got: " + expected.getMessage(),
                        expected.getMessage().contains(field));
            }
        }
    }

    /**
     * The declared difference between the two rehydration/creation paths, pinned so it cannot drift
     * silently: {@link AnswerRevision#create} refuses a policy version no purpose owns, while
     * {@link AnswerRevision#restore} accepts it, because on reload the stored row is the authority and
     * re-deriving ownership would make a retired prefix unloadable — the row would be there and the
     * app could not read it. A round-5-independent audit asked for this difference to be documented
     * <em>and</em> asserted rather than only described in prose.
     */
    @Test
    public void createChecksPolicyOwnershipWhileRestoreTrustsTheStoredRow() {
        assertTrue("create must refuse an unattributable version",
                AnswerRevision.create(MESSAGE_ID, 1, "r", "a", GenerationModel.PRO, true,
                        "garbage-not-a-version", NOW).isFailure());
        AnswerRevision restored = AnswerRevision.restore(
                Guard.newId(), MESSAGE_ID, 1, "r", "a", GenerationModel.PRO, true,
                "garbage-not-a-version", NOW);
        assertEquals("restore must hand back exactly what the row holds, without re-deriving "
                + "ownership", "garbage-not-a-version", restored.promptPolicyVersion);
        // What restore still refuses is a field that cannot be read at all.
        try {
            AnswerRevision.restore(Guard.newId(), MESSAGE_ID, 1, "r", "a", GenerationModel.PRO, true,
                    "   ", NOW);
            fail("restore must still refuse a blank version: a row with no version is unreadable");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("promptPolicyVersion"));
        }
    }

    /**
     * The behaviour the whole model exists for: after a reset, the old child branch still resolves
     * to the old revision and the old reasoning, while the position displays the new one.
     */
    @Test
    public void resetCreatesNewRevisionAndOldBranchSourceKeepsTheOldOne() {
        Message aiPosition = Message.ai(Guard.newId(), 1, NOW);
        AnswerRevision first = revision(aiPosition.id, 1, "reasoning-that-led-to-answer-1",
                "answer-1", POLICY_V1);
        assertTrue(aiPosition.activateRevision(first.id).isOk());

        DomainResult<BranchSource> sourceResult = BranchSource.fromOperation(
                Guard.newId(), aiPosition.id, Guard.newId(),
                GenerationState.STABLE, first.id, BranchAnchor.wholeAnswer());
        assertTrue(sourceResult.isOk());
        DomainResult<Branch> childResult =
                Branch.child(Guard.newId(), sourceResult.value(), NOW);
        assertTrue(childResult.isOk());
        Branch child = childResult.value();

        // Reset: a NEW stable revision is produced and the position switches to it.
        AnswerRevision second = revision(aiPosition.id, 2, "reasoning-that-led-to-answer-2",
                "answer-2", POLICY_V1_1);
        assertEquals(first.id, aiPosition.activeRevisionId());
        assertTrue(aiPosition.activateRevision(second.id).isOk());

        assertEquals("the position must now display the new revision",
                second.id, aiPosition.activeRevisionId());
        assertNotEquals(first.id, second.id);
        assertTrue("revision numbers must increase", second.revisionNo > first.revisionNo);

        // The child branch must NOT follow the switch.
        assertSame(sourceResult.value(), child.source);
        assertTrue("the child still pins the revision it was forked from",
                child.source.pinsRevision(first.id));
        assertFalse(child.source.pinsRevision(second.id));
        assertEquals(first.id, child.source.sourceRevisionId);

        // And the old revision keeps exactly the reasoning and policy version it was born with.
        assertEquals("reasoning-that-led-to-answer-1", first.reasoningContent);
        assertEquals("answer-1", first.answerContent);
        assertEquals(POLICY_V1, first.promptPolicyVersion);
        assertEquals("reasoning-that-led-to-answer-2", second.reasoningContent);
        assertEquals(POLICY_V1_1, second.promptPolicyVersion);
        assertNotNull(first.id);
    }

    @Test
    public void reasoningAndAnswerBelongToTheSameStableVersion() {
        AnswerRevision created = revision(MESSAGE_ID, 1, "the reasoning", "the answer", POLICY_V1);
        assertEquals("the reasoning", created.reasoningContent);
        assertEquals("the answer", created.answerContent);
        assertTrue(created.hasReasoning());
    }

    @Test
    public void thinkingDisabledKeepsAnEmptyReasoningFieldButNoNull() {
        DomainResult<AnswerRevision> result = AnswerRevision.create(
                MESSAGE_ID, 1, "", "the answer", GenerationModel.FLASH, false, POLICY_V1, NOW);
        assertTrue(result.isOk());
        assertFalse(result.value().hasReasoning());
        assertEquals("", result.value().reasoningContent);
    }
}
