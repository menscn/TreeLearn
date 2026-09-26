/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.treelearn.core.domain.GenerationModel
 *  com.treelearn.core.domain.ReadingAnchor
 */
package com.treelearn.app.ui;

import com.treelearn.core.domain.GenerationModel;
import com.treelearn.core.domain.ReadingAnchor;
import java.util.Set;

public interface ChatActions {
    public void onDraftChanged(String var1);

    public void onSend();

    public void onStopRequested();

    public void onFollowLatestPressed();

    public void onReasoningToggled(String var1);

    public void onCopyPressed(String var1);

    public void onSelectionCopyPressed(String var1);

    public void onQuotePressed(String var1, String var2);

    public void onQuoteCleared();

    public void onRetryPressed(String var1);

    public void onResetPressed(String var1);

    public void onForkPressed(String var1, String var2, int var3, int var4);

    public void onSourceCardToggled();

    public void onChildrenPressed(String var1);

    public void onVersionPressed(String var1);

    public void onRevisionSelected(String var1, String var2);

    public void onRecommendationFollowed(String var1);

    public void onRecommendationForked(String var1);

    public void onSettingsPressed();

    public void onOverviewPressed();

    public void onNavigationPressed();

    public void onModelSelected(GenerationModel var1);

    public void onThinkingToggled(boolean var1);

    public void onBackPressed();

    public void onRowsLaidOut(int var1, Set<String> var2);

    public void onLeaving(ReadingAnchor var1);

    public void onViewportChanged(Set<String> var1);
}

