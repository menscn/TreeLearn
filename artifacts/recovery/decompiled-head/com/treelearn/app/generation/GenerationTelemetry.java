/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.treelearn.ai.gateway.AiGatewayError$Kind
 */
package com.treelearn.app.generation;

import com.treelearn.ai.gateway.AiGatewayError;

public interface GenerationTelemetry {
    public void record(Event var1, String var2, String var3, int var4, AiGatewayError.Kind var5);

    public static enum Event {
        START,
        ACCEPTED,
        STREAMING,
        STABLE,
        FAILED,
        INTERRUPTED,
        CANCELED,
        PROMPT_REFUSED,
        STORAGE_REFUSED,
        STALE_DISCARDED;

    }
}

