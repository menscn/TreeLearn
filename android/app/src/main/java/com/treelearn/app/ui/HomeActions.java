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

    void onTopicTapped(TopicCard card);

    void onRenameTopic(TopicCard card, String title);

    void onDeleteTopic(TopicCard card);

    /** Tapping the key hint: navigate, no decision to make. */
    void onSettingsPressed();

    /** Tapping the archive entry: navigate, no decision to make. */
    void onArchivedPressed();

    /** Tapping the search entry: navigate, no decision to make. */
    void onSearchPressed();

    void onModelSelected(GenerationModel model);

    void onThinkingToggled(boolean enabled);
}
