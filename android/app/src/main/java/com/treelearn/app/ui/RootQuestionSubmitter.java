package com.treelearn.app.ui;

import com.treelearn.core.domain.GenerationModel;

/**
 * Starts the first learning tree from a root question.
 *
 * <p>The home screen never talks to the store or the provider itself: it hands the question over and
 * renders what comes back. The implementation owns the two rules the PRD puts on this path - the
 * topic and its root branch are formed as one unit (6.2.1, "首次创建原子性"), and a refused attempt
 * leaves no topic behind (6.2.13/6.2.14).
 *
 * <p>A refusal is a returned value, not an exception: the presenter must keep the draft on screen and
 * tell the learner which failure happened, so an implementation classifies and returns rather than
 * throwing for a business outcome.
 */
public interface RootQuestionSubmitter {

    SubmitResult submit(String question, GenerationModel model, boolean thinkingEnabled);
}