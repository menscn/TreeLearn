package com.treelearn.app.artifact;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.treelearn.core.domain.AnswerRevision;
import com.treelearn.core.domain.ContextPlan;
import com.treelearn.core.domain.GenerationModel;
import com.treelearn.core.domain.Guard;
import com.treelearn.core.domain.JsonText;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Test;

/**
 * The structured inputs of the six derived purposes (prompt design 6, technical design 10).
 *
 * <p>Every input is built here as a JSON value tree and rendered by {@link JsonText}, so escaping is
 * the codec's job and no caller can assemble an envelope by concatenating strings. The assertions
 * below pin the field names of the design document, the fact that a home recommendation can only ever
 * carry an outline, and that a compression keeps the reasoning it was given.
 *
 * <p>The marker text is a synthetic label, never a real question or answer: an assertion label that
 * carried content would put content into the test report.
 */
public final class ArtifactInputTest {

    private static final String MARKER = "DERIVED-INPUT-MARKER-7C41";

    @Test
    public void aTitleInputIsOneFieldAndNothingElse() {
        Map<?, ?> root = object(ArtifactInputs.rootTitle("什么是熵?"));
        assertEquals(setOf("root_question"), root.keySet());
        assertEquals("什么是熵?", root.get("root_question"));
    }

    @Test
    public void structuredEncodingEscapesInsteadOfConcatenating() {
        String hostile = "引号\" 反斜杠\\ 换行\n 制表\t 控制\u0007";
        String json = ArtifactInputs.rootTitle(hostile);
        assertFalse("a raw control character must never reach the envelope", json.contains("\n"));
        assertFalse(json.contains("\t"));
        assertEquals(hostile, object(json).get("root_question"));
    }

    @Test
    public void aBranchTitleCarriesTheAnchorAndTheFirstQuestion() {
        Map<?, ?> root = object(ArtifactInputs.branchTitle("", "首个问题"));
        assertEquals(setOf("source_quote", "first_question"), root.keySet());
        assertEquals("", root.get("source_quote"));
        assertEquals("首个问题", root.get("first_question"));
    }

    @Test
    public void aBranchSummaryCarriesBothHalvesAndEveryFieldOfAStableVersion() {
        Map<?, ?> root = object(ArtifactInputs.branchSummary(
                Arrays.asList(ContextPlan.Entry.userMessage(Guard.newId(), "根问题")),
                Arrays.asList(
                        ContextPlan.Entry.stableRevision(Guard.newId(), "推理正文", "回答正文"),
                        ContextPlan.Entry.summary(Guard.newId(), "早期摘要"))));
        assertEquals(setOf("birth_context", "branch_messages"), root.keySet());
        List<?> birth = list(root.get("birth_context"));
        List<?> branch = list(root.get("branch_messages"));
        assertEquals(1, birth.size());
        assertEquals(2, branch.size());
        Map<?, ?> question = map(birth.get(0));
        assertEquals(setOf("kind", "text"), question.keySet());
        assertEquals("USER_MESSAGE", question.get("kind"));
        Map<?, ?> answer = map(branch.get(0));
        assertEquals(setOf("kind", "reasoning", "answer"), answer.keySet());
        assertEquals("STABLE_REVISION", answer.get("kind"));
        assertEquals("推理正文", answer.get("reasoning"));
        assertEquals("回答正文", answer.get("answer"));
        Map<?, ?> summary = map(branch.get(1));
        assertEquals(setOf("kind", "summary"), summary.keySet());
        assertEquals("SUMMARY", summary.get("kind"));
    }

    @Test
    public void aCompressionCarriesTheQuestionTheReasoningAndTheAnswer() {
        AnswerRevision revision = revision("推理" + MARKER, "回答" + MARKER);
        assertTrue(revision.reasoningContent.contains(MARKER));
        String json = ArtifactInputs.contextCompression(Arrays.asList(
                new ArtifactInputs.CompressionTurn("问题" + MARKER, revision)));
        Map<?, ?> root = object(json);
        assertEquals(setOf("messages"), root.keySet());
        List<?> messages = list(root.get("messages"));
        assertEquals(2, messages.size());
        Map<?, ?> question = map(messages.get(0));
        assertEquals(setOf("kind", "text"), question.keySet());
        assertEquals("USER_MESSAGE", question.get("kind"));
        Map<?, ?> answer = map(messages.get(1));
        assertEquals(setOf("kind", "reasoning", "answer"), answer.keySet());
        assertEquals("STABLE_REVISION", answer.get("kind"));
        // the reasoning reaches the envelope unshortened: it is never dropped to save window space
        assertEquals("推理" + MARKER, answer.get("reasoning"));
        assertEquals("回答" + MARKER, answer.get("answer"));
        assertEquals("问题" + MARKER, question.get("text"));
    }

    @Test
    public void aHomeRecommendationSubmitsOnlyTheOutlineTheDesignAllows() {
        AnswerRevision revision = revision("推理" + MARKER, "回答" + MARKER);
        assertTrue("the marker must exist somewhere a careless encoder could reach it",
                revision.answerContent.contains(MARKER));
        String json = ArtifactInputs.homeRecommendations(Arrays.asList(
                TreeOutline.Entry.topic("主题标题", "简短摘要"),
                TreeOutline.Entry.branch("分支标题", "来源锚点", "简短摘要")), 4);
        Map<?, ?> root = object(json);
        assertEquals(setOf("tree_outline", "max_items"), root.keySet());
        assertFalse("a home recommendation never submits a conversation body", json.contains(MARKER));
        assertFalse(json.contains("latest_answer"));
        List<?> outline = list(root.get("tree_outline"));
        assertEquals(2, outline.size());
        Map<?, ?> topic = map(outline.get(0));
        assertEquals(setOf("level", "title", "source_quote", "summary"), topic.keySet());
        assertEquals("TOPIC", topic.get("level"));
        assertEquals("主题标题", topic.get("title"));
        assertEquals("简短摘要", topic.get("summary"));
        Map<?, ?> branch = map(outline.get(1));
        assertEquals(setOf("level", "title", "source_quote", "summary"), branch.keySet());
        assertEquals("BRANCH", branch.get("level"));
        assertEquals("来源锚点", branch.get("source_quote"));
    }

    @Test
    public void anAnswerRecommendationCarriesTheLatestAnswerTheOutlineAndTheCeiling() {
        Map<?, ?> root = object(ArtifactInputs.answerRecommendations(
                "最新稳定回答", Arrays.asList(TreeOutline.Entry.branch("分支标题", "锚点", "摘要")), 5));
        assertEquals(setOf("latest_answer", "tree_outline", "max_items"), root.keySet());
        assertEquals("最新稳定回答", root.get("latest_answer"));
        assertEquals(Long.valueOf(5), root.get("max_items"));
        assertEquals(1, list(root.get("tree_outline")).size());
    }

    @Test
    public void aCeilingBelowOneIsACallerBugNotAnEmptyList() {
        try {
            ArtifactInputs.answerRecommendations("回答", Arrays.asList(), 0);
            fail("a ceiling of zero must be refused at build time");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("maxItems"));
        }
    }

    private static AnswerRevision revision(String reasoning, String answer) {
        return AnswerRevision.create(Guard.newId(), 1, reasoning, answer, GenerationModel.FLASH, false,
                "tl-learning-answer/1.0.0", 1_700_000_000_000L).value();
    }

    private static Map<?, ?> object(String json) {
        return map(JsonText.read(json));
    }

    private static Map<?, ?> map(Object value) {
        assertTrue("expected an object but found " + typeOf(value), value instanceof Map);
        return (Map<?, ?>) value;
    }

    private static List<?> list(Object value) {
        assertTrue("expected an array but found " + typeOf(value), value instanceof List);
        return (List<?>) value;
    }

    private static String typeOf(Object value) {
        return value == null ? "null" : value.getClass().getSimpleName();
    }

    private static Set<String> setOf(String... names) {
        return new HashSet<String>(Arrays.asList(names));
    }
}