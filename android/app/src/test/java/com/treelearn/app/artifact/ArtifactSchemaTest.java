package com.treelearn.app.artifact;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.treelearn.core.domain.PromptPurpose;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import org.junit.Test;

/**
 * The artifact kind table and the strict output codec (technical design 10, prompt design 6).
 *
 * <p>Everything here is a pure-JVM contract: which usage an artifact belongs to, which JSON envelope
 * that usage is allowed to answer with, and what happens when the provider answers with something
 * else. No message, no assertion label and no failure message carries a prompt, a question, an answer
 * or a reasoning text, so a failing run is safe to keep as evidence.
 */
public final class ArtifactSchemaTest {

    private static final int CEILING = 3;

    @Test
    public void everyDerivedPurposeOwnsExactlyOneKindAndTheTeachingPurposeOwnsNone() {
        Set<String> wireIds = new HashSet<String>();
        for (PromptPurpose purpose : PromptPurpose.values()) {
            if (purpose == PromptPurpose.LEARNING_ANSWER) {
                try {
                    ArtifactKind.ofPurpose(purpose);
                    fail("the teaching purpose must never be reachable as a derived artifact kind");
                } catch (IllegalArgumentException expected) {
                    assertTrue(expected.getMessage().contains("LEARNING_ANSWER"));
                }
                continue;
            }
            if (purpose == PromptPurpose.MESSAGE_TRANSLATION) {
                // T-090: the translation is a derived display, never a stored artifact - the
                // stable-answer-immutability contract forbids a write path for it.
                try {
                    ArtifactKind.ofPurpose(purpose);
                    fail("the translation purpose must never be reachable as a derived artifact kind");
                } catch (IllegalArgumentException expected) {
                    assertTrue(expected.getMessage().contains("MESSAGE_TRANSLATION"));
                }
                continue;
            }
            ArtifactKind kind = ArtifactKind.ofPurpose(purpose);
            assertEquals(purpose, kind.purpose);
            assertTrue("wire ids must be unique", wireIds.add(kind.wireId));
            assertEquals(kind, ArtifactKind.fromWireId(kind.wireId));
        }
        assertEquals(PromptPurpose.values().length - 2, wireIds.size());
    }

    @Test
    public void aWireIdNoKindProducesIsRefused() {
        for (String unknown : new String[]{"learning_answer", "tl-root-title", "", "root-title"}) {
            try {
                ArtifactKind.fromWireId(unknown);
                fail("a wire id no kind produces must be refused");
            } catch (IllegalArgumentException expected) {
                assertTrue(expected.getMessage().startsWith("no artifact kind uses"));
            }
        }
    }

    @Test
    public void theKindTableAgreesWithHowEachInputCanChange() {
        assertEquals(ArtifactKind.Scope.BRANCH, ArtifactKind.ROOT_TITLE.scope);
        assertEquals(ArtifactKind.Scope.TOPIC, ArtifactKind.HOME_RECOMMENDATIONS.scope);
        assertFalse(ArtifactKind.ROOT_TITLE.revisionBound());
        assertFalse(ArtifactKind.BRANCH_TITLE.revisionBound());
        assertFalse(ArtifactKind.HOME_RECOMMENDATIONS.revisionBound());
        assertTrue(ArtifactKind.BRANCH_SUMMARY.revisionBound());
        assertTrue(ArtifactKind.ANSWER_RECOMMENDATIONS.revisionBound());
        assertTrue(ArtifactKind.CONTEXT_COMPRESSION.revisionBound());
        for (ArtifactKind kind : ArtifactKind.values()) {
            assertEquals("only a compression fails closed on an empty body",
                    kind == ArtifactKind.CONTEXT_COMPRESSION, kind.refusesEmptySummary());
        }
    }

    @Test
    public void aTitleShapeAcceptsExactlyOneNonBlankStringField() {
        assertEquals("熵", payloadOf(ArtifactKind.ROOT_TITLE, "{\"title\":\"熵\"}").title);
        assertEquals("熵", payloadOf(ArtifactKind.BRANCH_TITLE, "{\"title\":\"熵\"}").title);
        assertEquals(ArtifactOutcome.PAYLOAD_INVALID, refusalOf(ArtifactKind.ROOT_TITLE, "{\"title\":\"\"}"));
        assertEquals(ArtifactOutcome.PAYLOAD_INVALID, refusalOf(ArtifactKind.ROOT_TITLE, "{\"title\":\"  \"}"));
        assertEquals(ArtifactOutcome.PAYLOAD_INVALID, refusalOf(ArtifactKind.ROOT_TITLE, "{}"));
        assertEquals(ArtifactOutcome.PAYLOAD_INVALID, refusalOf(ArtifactKind.ROOT_TITLE, "{\"summary\":\"x\"}"));
        assertEquals(ArtifactOutcome.PAYLOAD_INVALID,
                refusalOf(ArtifactKind.ROOT_TITLE, "{\"title\":\"x\",\"extra\":\"y\"}"));
        assertEquals(ArtifactOutcome.PAYLOAD_INVALID, refusalOf(ArtifactKind.ROOT_TITLE, "{\"title\":7}"));
    }

    @Test
    public void aDecoratedOutputIsRefusedRatherThanTrimmed() {
        String[] decorated = {
                "```json\n{\"title\":\"熵\"}\n```",
                "{\"title\":\"熵\"}\n希望这有帮助。",
                "{\"title\":\"熵\"} {\"title\":\"熵\"}",
                "[{\"title\":\"熵\"}]",
                "\"熵\"",
                "{\"title\":\"熵\",}",
                "{\"title\":\"熵\" \"title\":\"熵\"}",
                "{title:\"熵\"}",
                "{\"title\":\"熵\"",
                "{\"title\":\"熵\"}\\x",
        };
        for (int index = 0; index < decorated.length; index++) {
            assertEquals("decorated case " + index, ArtifactOutcome.PAYLOAD_INVALID,
                    refusalOf(ArtifactKind.ROOT_TITLE, decorated[index]));
        }
    }

    @Test
    public void aBranchSummaryOwesBothFieldsButMayCarryAnEmptySummary() {
        ArtifactPayload payload = payloadOf(ArtifactKind.BRANCH_SUMMARY, "{\"summary\":\"\",\"key_points\":[]}");
        assertEquals("", payload.summary);
        assertTrue(payload.keyPoints.isEmpty());
        payload = payloadOf(ArtifactKind.BRANCH_SUMMARY, "{\"summary\":\"主线\",\"key_points\":[\"甲\",\"乙\"]}");
        assertEquals("主线", payload.summary);
        assertEquals(Arrays.asList("甲", "乙"), payload.keyPoints);
        assertEquals(ArtifactOutcome.PAYLOAD_INVALID,
                refusalOf(ArtifactKind.BRANCH_SUMMARY, "{\"summary\":\"主线\"}"));
        assertEquals(ArtifactOutcome.PAYLOAD_INVALID,
                refusalOf(ArtifactKind.BRANCH_SUMMARY, "{\"key_points\":[]}"));
        assertEquals(ArtifactOutcome.PAYLOAD_INVALID,
                refusalOf(ArtifactKind.BRANCH_SUMMARY, "{\"summary\":\"主线\",\"key_points\":\"甲\"}"));
        assertEquals(ArtifactOutcome.PAYLOAD_INVALID,
                refusalOf(ArtifactKind.BRANCH_SUMMARY, "{\"summary\":\"主线\",\"key_points\":[\"\"]}"));
    }

    @Test
    public void aCompressionWithoutASummaryFailsClosedInsteadOfShorteningTheContext() {
        assertEquals("旧结论", payloadOf(ArtifactKind.CONTEXT_COMPRESSION, "{\"summary\":\"旧结论\"}").summary);
        assertEquals(ArtifactOutcome.SUMMARY_EMPTY,
                refusalOf(ArtifactKind.CONTEXT_COMPRESSION, "{\"summary\":\"\"}"));
        assertEquals(ArtifactOutcome.PAYLOAD_INVALID,
                refusalOf(ArtifactKind.CONTEXT_COMPRESSION, "{\"summary\":\"旧结论\",\"key_points\":[]}"));
    }

    @Test
    public void eachRecommendationShapeUsesItsOwnItemKey() {
        assertEquals(Arrays.asList("下一步?"),
                payloadOf(ArtifactKind.ANSWER_RECOMMENDATIONS, "{\"items\":[{\"question\":\"下一步?\"}]}").items);
        assertEquals(Arrays.asList("新主题"),
                payloadOf(ArtifactKind.HOME_RECOMMENDATIONS, "{\"items\":[{\"topic\":\"新主题\"}]}").items);
        assertEquals(ArtifactOutcome.PAYLOAD_INVALID,
                refusalOf(ArtifactKind.HOME_RECOMMENDATIONS, "{\"items\":[{\"question\":\"下一步?\"}]}"));
        assertEquals(ArtifactOutcome.PAYLOAD_INVALID,
                refusalOf(ArtifactKind.ANSWER_RECOMMENDATIONS, "{\"items\":[{\"question\":\"a\",\"topic\":\"b\"}]}"));
        assertEquals(ArtifactOutcome.PAYLOAD_INVALID,
                refusalOf(ArtifactKind.ANSWER_RECOMMENDATIONS, "{\"items\":[\"下一步?\"]}"));
        assertEquals(ArtifactOutcome.PAYLOAD_INVALID,
                refusalOf(ArtifactKind.ANSWER_RECOMMENDATIONS, "{\"items\":[{\"question\":\"\"}]}"));
        assertEquals(ArtifactOutcome.PAYLOAD_INVALID,
                refusalOf(ArtifactKind.ANSWER_RECOMMENDATIONS, "{\"items\":{\"question\":\"a\"}}"));
    }

    @Test
    public void aRecommendationArrayMustNotExceedTheDeclaredCeiling() {
        String three = "{\"items\":[{\"topic\":\"a\"},{\"topic\":\"b\"},{\"topic\":\"c\"}]}";
        assertTrue(ArtifactPayloadCodec.parse(ArtifactKind.HOME_RECOMMENDATIONS, three, 3).isUsable());
        assertEquals(ArtifactOutcome.PAYLOAD_INVALID,
                ArtifactPayloadCodec.parse(ArtifactKind.HOME_RECOMMENDATIONS, three, 2).refusal());
        try {
            ArtifactPayloadCodec.parse(ArtifactKind.HOME_RECOMMENDATIONS, three, 0);
            fail("a ceiling of zero is a caller bug, not an empty list");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("itemCeiling"));
        }
    }

    @Test
    public void theStoredPayloadIsReEncodedFromTheParsedFields() {
        assertEquals("{\"summary\":\"主线\",\"key_points\":[\"乙\",\"甲\"]}",
                payloadOf(ArtifactKind.BRANCH_SUMMARY,
                        "{ \"key_points\" : [ \"乙\" , \"甲\" ] , \"summary\" : \"主线\" }").canonicalJson());
        assertEquals("{\"title\":\"熵\"}",
                payloadOf(ArtifactKind.ROOT_TITLE, "{ \"title\" : \"熵\" }").canonicalJson());
        assertEquals("{\"items\":[{\"question\":\"a\"}]}",
                payloadOf(ArtifactKind.ANSWER_RECOMMENDATIONS,
                        "{\"items\":[{\"question\":\"a\"}]}").canonicalJson());
        assertEquals("{\"items\":[{\"topic\":\"a\"}]}",
                payloadOf(ArtifactKind.HOME_RECOMMENDATIONS,
                        "{\"items\":[{\"topic\":\"a\"}]}").canonicalJson());
        assertEquals("{\"summary\":\"旧结论\"}",
                payloadOf(ArtifactKind.CONTEXT_COMPRESSION, "{\"summary\":\"旧结论\"}").canonicalJson());
    }

    @Test
    public void aRefusedParseCarriesNoPartialPayload() {
        DerivedResult refused = ArtifactPayloadCodec.parse(ArtifactKind.ROOT_TITLE, "{\"title\":\"\"}", CEILING);
        assertFalse(refused.isUsable());
        assertNull(refused.payload());
        assertEquals(ArtifactOutcome.PAYLOAD_INVALID, refused.refusal());
    }

    private static ArtifactPayload payloadOf(ArtifactKind kind, String text) {
        DerivedResult result = ArtifactPayloadCodec.parse(kind, text, CEILING);
        assertTrue("expected a usable payload for " + kind, result.isUsable());
        return result.payload();
    }

    private static ArtifactOutcome refusalOf(ArtifactKind kind, String text) {
        DerivedResult result = ArtifactPayloadCodec.parse(kind, text, CEILING);
        assertFalse("expected a refusal for " + kind, result.isUsable());
        return result.refusal();
    }
}