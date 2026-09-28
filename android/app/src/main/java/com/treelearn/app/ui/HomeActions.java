package com.treelearn.app.ui;

import com.treelearn.core.domain.GenerationModel;

/**
 * What the home screen may ask the presenter to do.
 *
 * <p>It exists so the view depends on a contract instead of on {@link HomePresenter}: the screen
 * forwards a gesture, the presenter decides, and the decision is asserted in a JVM test.
 */
public interface HomeActions {

    void onDraftChanged(String text);

    void onSubmit();

    void onRecommendationTapped(Recommendation recommendation);

    /** Tapping a recent-learning row: resume the topic's last branch, no decision to make. */
    void onTopicTapped(TopicCard card);

    void onModelSelected(GenerationModel model);

    void onThinkingToggled(boolean enabled);
}
