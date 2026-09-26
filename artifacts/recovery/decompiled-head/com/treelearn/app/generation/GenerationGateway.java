/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.treelearn.ai.gateway.GenerationSink
 *  com.treelearn.ai.prompt.DeepSeekRequest
 *  com.treelearn.core.domain.GenerationModel
 */
package com.treelearn.app.generation;

import com.treelearn.ai.gateway.GenerationSink;
import com.treelearn.ai.prompt.DeepSeekRequest;
import com.treelearn.core.domain.GenerationModel;

public interface GenerationGateway {
    public Stream open(GenerationModel var1, DeepSeekRequest var2, GenerationSink var3);

    public static interface Stream {
        public void cancel();

        public boolean isCancelled();
    }
}

