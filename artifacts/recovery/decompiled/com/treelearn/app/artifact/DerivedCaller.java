/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.treelearn.ai.prompt.DeepSeekRequest
 *  com.treelearn.core.domain.GenerationModel
 */
package com.treelearn.app.artifact;

import com.treelearn.ai.prompt.DeepSeekRequest;
import com.treelearn.core.domain.GenerationModel;

public interface DerivedCaller {
    public String call(DeepSeekRequest var1, GenerationModel var2);
}

