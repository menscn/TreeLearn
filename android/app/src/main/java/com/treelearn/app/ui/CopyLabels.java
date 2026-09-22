package com.treelearn.app.ui;

import com.treelearn.core.domain.Guard;

/**
 * The two headings the copied answer carries, injected rather than written into the presenter.
 *
 * <p>The clipboard text is user-visible, and every user-visible word in this module lives in
 * {@code res/values/strings.xml}. A presenter has no {@code Context}, so the two words are handed to
 * it as values by whoever builds it; the format itself stays in
 * {@link ChatMessage#copyText(CopyLabels)}, which is the only place it is defined.
 */
public final class CopyLabels {

    /** The heading before the reasoning, e.g. 思考过程. */
    public final String reasoning;
    /** The heading before the final answer, e.g. 最终回答. */
    public final String answer;

    private CopyLabels(String reasoning, String answer) {
        this.reasoning = Guard.nonBlank(reasoning, "reasoning");
        this.answer = Guard.nonBlank(answer, "answer");
    }

    public static CopyLabels of(String reasoningHeading, String answerHeading) {
        return new CopyLabels(reasoningHeading, answerHeading);
    }
}