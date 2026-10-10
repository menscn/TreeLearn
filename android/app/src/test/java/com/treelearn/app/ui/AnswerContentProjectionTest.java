package com.treelearn.app.ui;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.treelearn.core.domain.Annotation;

import java.util.Collections;

import org.junit.Test;

public final class AnswerContentProjectionTest {

    @Test
    public void completeEnvelopeSeparatesReasoningFromAnswer() {
        AnswerContentProjection projection = AnswerContentProjection.project(
                "<treelearn_reasoning>private material</treelearn_reasoning>"
                        + "<treelearn_answer>visible result</treelearn_answer>", false);

        assertTrue("complete envelope keeps only its answer in the answer projection",
                "visible result".equals(projection.answer));
        assertTrue("complete envelope routes reasoning to its separate projection",
                "private material".equals(projection.reasoning));
    }

    @Test
    public void reasoningOnlyAndUnclosedReasoningFailClosed() {
        AnswerContentProjection reasoningOnly = AnswerContentProjection.project(
                "<treelearn_reasoning>private material</treelearn_reasoning>", false);
        AnswerContentProjection unclosed = AnswerContentProjection.project(
                "<treelearn_reasoning>private material", false);

        assertTrue("reasoning-only content does not become an answer",
                reasoningOnly.answer.isEmpty());
        assertTrue("unclosed reasoning does not become an answer", unclosed.answer.isEmpty());
        assertTrue("unclosed reasoning remains available only to the separate projection",
                "private material".equals(unclosed.reasoning));
    }

    @Test
    public void everyStreamingBoundaryKeepsFramingOutOfTheAnswer() {
        String content = "<treelearn_reasoning>private material</treelearn_reasoning>"
                + "<treelearn_answer>visible result</treelearn_answer>";

        for (int boundary = 0; boundary <= content.length(); boundary++) {
            AnswerContentProjection projection = AnswerContentProjection.project(
                    content.substring(0, boundary), true);
            assertTrue("streaming projection never exposes reasoning",
                    !projection.answer.contains("private material"));
            assertTrue("streaming projection never exposes framing",
                    !projection.answer.contains("treelearn_reasoning")
                            && !projection.answer.contains("treelearn_answer"));
        }
    }

    @Test
    public void incompleteAnswerClosingTagIsWithheldDuringStreaming() {
        AnswerContentProjection projection = AnswerContentProjection.project(
                "<treelearn_reasoning>private material</treelearn_reasoning>"
                        + "<treelearn_answer>visible result</treelearn_ans", true);

        assertTrue("incomplete closing framing is withheld",
                "visible result".equals(projection.answer));
    }

    @Test
    public void failedMessageWithPartialReasoningOpenerFailsClosed() {
        ChatMessage failed = ChatMessage.ai("22222222-2222-4222-8222-222222222222",
                "<treelearn_reason", "", ChatMessage.Status.FAILED,
                ChatMessage.Phase.ANSWER, "", 0, 0, false, 1L);

        assertTrue("a failed partial opener is not visible",
                AnswerContentProjection.project(failed).answer.isEmpty());
    }

    @Test
    public void uniqueSelectionMapsToItsRawFramedAnswerRange() {
        String raw = "<treelearn_reasoning>private</treelearn_reasoning>"
                + "<treelearn_answer>visible phrase here</treelearn_answer>";
        AnswerContentProjection projection = AnswerContentProjection.project(raw, false);
        AnswerContentProjection.SourceRange range =
                projection.sourceRangeForUniqueText("phrase");

        assertEquals(raw.indexOf("phrase"), range.start);
        assertEquals(raw.indexOf("phrase") + "phrase".length(), range.end);
        assertNull(projection.sourceRangeForUniqueText("absent"));
        AnswerContentProjection repeated = AnswerContentProjection.project(
                "<treelearn_reasoning>x</treelearn_reasoning>"
                        + "<treelearn_answer>repeat and repeat</treelearn_answer>", false);
        assertNull(repeated.sourceRangeForUniqueText("repeat"));
    }

    @Test
    public void annotationRangeUsesTheSameProjectedProseCoordinatesAsTheVisibleAnswer() {
        String raw = "<treelearn_reasoning>private</treelearn_reasoning>"
                + "<treelearn_answer>visible answer text</treelearn_answer>";
        AnswerContentProjection projection = AnswerContentProjection.project(raw, false);
        int start = projection.answer.indexOf("answer");
        Highlighter.Plan plan = Highlighter.plan(Collections.<Annotation>emptyList(),
                Annotation.Locator.prose(0), start, start + "answer".length());

        assertEquals("answer", projection.answer.substring(plan.merge.span.start,
                plan.merge.span.end));
    }

    @Test
    public void inlineLabelsAndCodeRemainLiteralAnswerContent() {
        String prose = "Use <treelearn_answer> as a literal label.";
        String code = "```xml\n<treelearn_reasoning>literal</treelearn_reasoning>\n```";

        assertTrue("inline labels remain answer content",
                prose.equals(AnswerContentProjection.project(prose, false).answer));
        assertTrue("code examples remain answer content",
                code.equals(AnswerContentProjection.project(code, false).answer));
    }

    @Test
    public void aFramedAnswerCanContainLiteralClosingTagsInCode() {
        String answer = "```html\n<div>\n</treelearn_answer>\n</div>\n```";
        String content = "<treelearn_reasoning>private material</treelearn_reasoning>"
                + "<treelearn_answer>" + answer + "</treelearn_answer>";
        AnswerContentProjection projection = AnswerContentProjection.project(content, false);

        assertTrue("a literal closing tag inside code is retained",
                answer.equals(projection.answer));
    }
}
