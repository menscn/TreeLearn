package com.treelearn.app.wiring;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class BranchHeadingTest {

    @Test
    public void storedTitleWinsOverQuestionAndSourceAnswer() {
        assertEquals("正式标题", BranchHeading.of("正式标题", "首个问题", "来源答案。"));
    }

    @Test
    public void firstQuestionWinsWhenStoredTitleIsMissing() {
        assertEquals("首个问题", BranchHeading.of("", "首个问题", "来源答案。"));
    }

    @Test
    public void sourceAnswerProvidesTheFirstSentenceWhenQuestionIsMissing() {
        assertEquals("来源答案首句。", BranchHeading.of("", "", "来源答案首句。来源答案后续。"));
    }

    @Test
    public void unnamedFallbackKeepsAnEmptyBranchRowReadable() {
        assertEquals("未命名分支", BranchHeading.of(null, null, "   "));
    }

    @Test
    public void legacyReadersKeepTheirEmptyHeadingWhenNoNameExists() {
        assertEquals("", BranchHeading.of(null, null));
    }
}
