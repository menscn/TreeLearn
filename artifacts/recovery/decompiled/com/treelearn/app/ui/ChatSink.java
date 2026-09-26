/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.treelearn.core.domain.AnswerRevision
 *  com.treelearn.core.domain.ReadingPosition
 */
package com.treelearn.app.ui;

import com.treelearn.app.ui.ChatViewState;
import com.treelearn.app.ui.ChildBranch;
import com.treelearn.core.domain.AnswerRevision;
import com.treelearn.core.domain.ReadingPosition;
import java.util.List;

public interface ChatSink {
    public void render(ChatViewState var1);

    public void copyToClipboard(String var1);

    public void openBranch(String var1);

    public void openChildren(String var1, List<ChildBranch> var2);

    public void openVersions(String var1, List<AnswerRevision> var2);

    public void openSettings();

    public void openOverview(String var1);

    public void openNavigation(String var1);

    public void close();

    public void renderPosition(String var1, ReadingPosition var2);
}

