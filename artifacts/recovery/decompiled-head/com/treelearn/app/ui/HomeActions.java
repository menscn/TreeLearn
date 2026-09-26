/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.treelearn.core.domain.GenerationModel
 */
package com.treelearn.app.ui;

import com.treelearn.app.ui.Recommendation;
import com.treelearn.app.ui.TopicCard;
import com.treelearn.core.domain.GenerationModel;

public interface HomeActions {
    public void onDraftChanged(String var1);

    public void onSubmit();

    public void onRecommendationTapped(Recommendation var1);

    public void onTopicTapped(TopicCard var1);

    public void onRenameTopic(TopicCard var1, String var2);

    public void onDeleteTopic(TopicCard var1);

    public void onSettingsPressed();

    public void onArchivedPressed();

    public void onSearchPressed();

    public void onModelSelected(GenerationModel var1);

    public void onThinkingToggled(boolean var1);
}

