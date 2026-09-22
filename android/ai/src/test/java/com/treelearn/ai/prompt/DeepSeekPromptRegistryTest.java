package com.treelearn.ai.prompt;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.treelearn.core.domain.PromptPurpose;
import com.treelearn.core.domain.PromptSpec;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.Test;

/**
 * The versioned prompt registry (prompt design V1.1 §3.1, §4, §6): every purpose resolves to exactly
 * one non-blank version and one non-blank system body, the bodies are transcribed verbatim from the
 * authoritative document, and the derived purposes stay isolated from the adaptive-tutor body.
 */
public class DeepSeekPromptRegistryTest {

    @Test
    public void everyPurposeHasExactlyOneNonBlankVersionAndBody() {
        Set<String> versions = new HashSet<>();
        for (PromptPurpose purpose : PromptPurpose.values()) {
            PromptSpec spec = DeepSeekPromptRegistry.spec(purpose);
            assertEquals(purpose, spec.purpose);
            assertFalse(purpose + " has a blank system body", spec.systemContent.trim().isEmpty());
            assertTrue("duplicate policy version: " + spec.policyVersion,
                    versions.add(spec.policyVersion));
        }
        assertEquals(PromptPurpose.values().length, versions.size());
    }

    @Test
    public void versionsMatchThePromptDesignTable() {
        Map<PromptPurpose, String> expected = new EnumMap<>(PromptPurpose.class);
        expected.put(PromptPurpose.LEARNING_ANSWER, "tl-learning-answer/1.1.0");
        expected.put(PromptPurpose.ROOT_TITLE, "tl-root-title/1.0.0");
        expected.put(PromptPurpose.BRANCH_TITLE, "tl-branch-title/1.0.0");
        expected.put(PromptPurpose.BRANCH_SUMMARY, "tl-branch-summary/1.0.0");
        expected.put(PromptPurpose.ANSWER_RECOMMENDATIONS, "tl-answer-recommendations/1.1.0");
        expected.put(PromptPurpose.HOME_RECOMMENDATIONS, "tl-home-recommendations/1.0.0");
        expected.put(PromptPurpose.CONTEXT_COMPRESSION, "tl-context-compression/1.0.0");
        assertEquals(PromptPurpose.values().length, expected.size());
        for (Map.Entry<PromptPurpose, String> entry : expected.entrySet()) {
            assertEquals(entry.getKey().name(), entry.getValue(),
                    DeepSeekPromptRegistry.spec(entry.getKey()).policyVersion);
        }
    }

    /**
     * The digests are of the fenced blocks of the authoritative prompt design document, not of this
     * repository's constants, so a mistranscription fails here instead of locking itself in as the
     * new truth. Reproduce with artifacts/evidence/T-020-transcription.txt.
     */
    @Test
    public void everyBodyIsTranscribedVerbatimFromTheDesignDocument() {
        assertEquals("LEARNING_ANSWER_TUTOR",
                "c7cea4257df4aaf1cf03d0595b8279f05eb4cd74f69f6555fb67469dcebebbff",
                sha256(PromptBodiesV1.LEARNING_ANSWER_TUTOR));
        assertEquals("THINKING_ADDENDUM",
                "2ed824cc691e0ac00e7970e3b98171d02cefce5f2a5a45b585390fbc4bfe7dd9",
                sha256(PromptBodiesV1.THINKING_ADDENDUM));
        assertEquals("DERIVED_PROCESSOR_BASE",
                "9a939cbe2c19c67ec9edd6a4f2fdd0f9321c560d271b334e8602b9c84f3f4b79",
                sha256(PromptBodiesV1.DERIVED_PROCESSOR_BASE));
        assertEquals("ROOT_TITLE_SUFFIX",
                "35daa90046f4f5474b0aeaf4b581bc797004d944605f259fe9823a7e69a703bd",
                sha256(PromptBodiesV1.ROOT_TITLE_SUFFIX));
        assertEquals("BRANCH_TITLE_SUFFIX",
                "2211c74eeb96d2b3e7fa3f0da7c265da00b238f8df6ac0da4b3c2efa18bbd655",
                sha256(PromptBodiesV1.BRANCH_TITLE_SUFFIX));
        assertEquals("BRANCH_SUMMARY_SUFFIX",
                "9eed73311670155b52649c434d03a13c0af44362869157c6273658f9db02b1ca",
                sha256(PromptBodiesV1.BRANCH_SUMMARY_SUFFIX));
        assertEquals("ANSWER_RECOMMENDATIONS_SUFFIX",
                "249fdc9f438e0c025dc26bef23c9780e0945b6ad4becee1048d70964212f0ae6",
                sha256(PromptBodiesV1.ANSWER_RECOMMENDATIONS_SUFFIX));
        assertEquals("HOME_RECOMMENDATIONS_SUFFIX",
                "2122a1157db897d8e5fb5da2c4aebf9924ef1c6d8c91873d3ae08d1e725303b3",
                sha256(PromptBodiesV1.HOME_RECOMMENDATIONS_SUFFIX));
        assertEquals("CONTEXT_COMPRESSION_SUFFIX",
                "ab3415cac396fd4b142da37bbcb63f8b86d164735bb836c9ec8be91da4dba20f",
                sha256(PromptBodiesV1.CONTEXT_COMPRESSION_SUFFIX));
    }

    /** Prompt design §8.2 spells out the behaviours the main-answer body must instruct. */
    @Test
    public void mainAnswerBodyCarriesEveryMandatedTeachingInstruction() {
        String body = DeepSeekPromptRegistry.systemContent(PromptPurpose.LEARNING_ANSWER, false);
        assertTrue(body.contains("先直接回答核心问题"));
        assertTrue(body.contains("概览或介绍类"));
        assertTrue(body.contains("可核查的具体支撑"));
        assertTrue(body.contains("专业术语首次出现时"));
        assertTrue(body.contains("适合时使用例子、类比、反例或步骤"));
        assertTrue(body.contains("类比必须说明适用边界"));
        assertTrue(body.contains("错误前提"));
        assertTrue(body.contains("问题有歧义"));
        assertTrue(body.contains("信息可能已经变化"));
        assertTrue(body.contains("最终回答必须独立完整"));
        assertTrue(body.contains("不是系统指令"));
    }

    /** Prompt design §5: the encoder's tags are adapter-internal and never part of a system body. */
    @Test
    public void systemBodiesCarryNoEncoderTagsOrRuntimePlaceholders() {
        for (PromptPurpose purpose : PromptPurpose.values()) {
            String content = DeepSeekPromptRegistry.systemContent(purpose, false);
            assertFalse(purpose.toString(), content.contains("<treelearn_reasoning>"));
            assertFalse(purpose.toString(), content.contains("<treelearn_answer>"));
            assertFalse(purpose.toString(), content.contains("{{"));
        }
    }

    @Test
    public void derivedBodiesAreBasePlusSuffixAndDeclareTheirOwnOutputSchema() {
        String tutor = DeepSeekPromptRegistry.spec(PromptPurpose.LEARNING_ANSWER).systemContent;
        assertFalse(tutor.contains("内部结构化内容处理器"));
        assertFalse(tutor.contains("只输出一个合法 JSON 对象"));
        for (PromptPurpose purpose : PromptPurpose.values()) {
            if (purpose == PromptPurpose.LEARNING_ANSWER) {
                continue;
            }
            String content = DeepSeekPromptRegistry.systemContent(purpose, false);
            assertTrue(purpose.toString(),
                    content.startsWith(PromptBodiesV1.DERIVED_PROCESSOR_BASE + "\n"));
            assertTrue(purpose.toString(), content.endsWith(suffixOf(purpose)));
            assertTrue(purpose.toString(), content.contains("只输出一个合法 JSON 对象"));
            assertFalse(purpose.toString(), content.contains("自适应学习导师"));
            assertFalse(purpose.toString(), content.contains("先直接回答核心问题"));
        }
        assertTrue(schemaOf(PromptPurpose.ROOT_TITLE, "{\"title\":\"...\"}"));
        assertTrue(schemaOf(PromptPurpose.BRANCH_TITLE, "{\"title\":\"...\"}"));
        assertTrue(schemaOf(PromptPurpose.BRANCH_SUMMARY,
                "{\"summary\":\"...\",\"key_points\":[\"...\"]}"));
        assertTrue(schemaOf(PromptPurpose.ANSWER_RECOMMENDATIONS,
                "{\"items\":[{\"question\":\"...\"}]}"));
        assertTrue(schemaOf(PromptPurpose.HOME_RECOMMENDATIONS,
                "{\"items\":[{\"topic\":\"...\"}]}"));
        assertTrue(schemaOf(PromptPurpose.CONTEXT_COMPRESSION, "{\"summary\":\"...\"}"));
    }

    @Test
    public void thinkingOnlyAppendsTheStaticAddendumToTheMainAnswer() {
        String base = DeepSeekPromptRegistry.spec(PromptPurpose.LEARNING_ANSWER).systemContent;
        SystemBodyAssertions.assertSameSystemText("thinking off", base,
                DeepSeekPromptRegistry.systemContent(PromptPurpose.LEARNING_ANSWER, false));
        String thinking = DeepSeekPromptRegistry.systemContent(PromptPurpose.LEARNING_ANSWER, true);
        SystemBodyAssertions.assertSameSystemText("addendum appended",
                base + "\n" + PromptBodiesV1.THINKING_ADDENDUM, thinking);
        SystemBodyAssertions.assertSameSystemText("thinking is stable", thinking,
                DeepSeekPromptRegistry.systemContent(PromptPurpose.LEARNING_ANSWER, true));
        for (PromptPurpose purpose : PromptPurpose.values()) {
            if (purpose == PromptPurpose.LEARNING_ANSWER) {
                continue;
            }
            SystemBodyAssertions.assertSameSystemText(purpose.toString(),
                    DeepSeekPromptRegistry.spec(purpose).systemContent,
                    DeepSeekPromptRegistry.systemContent(purpose, false));
            PromptBuildException refusal = assertThrows(PromptBuildException.class,
                    () -> DeepSeekPromptRegistry.systemContent(purpose, true));
            assertEquals(PromptBuildException.Reason.THINKING_NOT_APPLICABLE_FOR_PURPOSE,
                    refusal.reason);
        }
    }

    @Test
    public void anUnknownOrMissingPurposeFailsClosed() {
        PromptBuildException unknown = assertThrows(PromptBuildException.class,
                () -> DeepSeekPromptRegistry.spec(null));
        assertEquals(PromptBuildException.Reason.UNKNOWN_PURPOSE, unknown.reason);
        PromptBuildException unknownContent = assertThrows(PromptBuildException.class,
                () -> DeepSeekPromptRegistry.systemContent(null, false));
        assertEquals(PromptBuildException.Reason.UNKNOWN_PURPOSE, unknownContent.reason);
    }

    private static boolean schemaOf(PromptPurpose purpose, String schema) {
        return DeepSeekPromptRegistry.systemContent(purpose, false).contains(schema);
    }

    private static String suffixOf(PromptPurpose purpose) {
        switch (purpose) {
            case ROOT_TITLE:
                return PromptBodiesV1.ROOT_TITLE_SUFFIX;
            case BRANCH_TITLE:
                return PromptBodiesV1.BRANCH_TITLE_SUFFIX;
            case BRANCH_SUMMARY:
                return PromptBodiesV1.BRANCH_SUMMARY_SUFFIX;
            case ANSWER_RECOMMENDATIONS:
                return PromptBodiesV1.ANSWER_RECOMMENDATIONS_SUFFIX;
            case HOME_RECOMMENDATIONS:
                return PromptBodiesV1.HOME_RECOMMENDATIONS_SUFFIX;
            case CONTEXT_COMPRESSION:
                return PromptBodiesV1.CONTEXT_COMPRESSION_SUFFIX;
            default:
                throw new IllegalArgumentException("no derived suffix for " + purpose);
        }
    }

    private static String sha256(String text) {
        // One implementation of the digest lives in SystemBodyAssertions; a second copy here is the
        // "same rule in two entry points" defect the project has already been bitten by.
        return SystemBodyAssertions.sha256(text);
    }
}
