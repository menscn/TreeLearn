/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.treelearn.core.domain.AnswerRevision
 *  com.treelearn.core.domain.GenerationModel
 */
package com.treelearn.app.ui;

import com.treelearn.app.ui.ChatCommandResult;
import com.treelearn.core.domain.AnswerRevision;
import com.treelearn.core.domain.GenerationModel;
import java.util.List;

public interface ChatCommands {
    public ChatCommandResult send(String var1, String var2, String var3, GenerationModel var4, boolean var5);

    public ChatCommandResult retry(String var1, GenerationModel var2, boolean var3, long var4);

    public ChatCommandResult reset(String var1, GenerationModel var2, boolean var3, long var4);

    public ChatCommandResult selectRevision(String var1, String var2, long var3);

    public ChatCommandResult fork(String var1, String var2, String var3, int var4, int var5);

    public List<AnswerRevision> versions(String var1);
}

