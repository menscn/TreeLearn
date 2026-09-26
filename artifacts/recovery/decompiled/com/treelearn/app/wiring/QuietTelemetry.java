/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.treelearn.ai.gateway.AiGatewayError$Kind
 */
package com.treelearn.app.wiring;

import com.treelearn.ai.gateway.AiGatewayError;
import com.treelearn.app.generation.GenerationTelemetry;

public final class QuietTelemetry
implements GenerationTelemetry {
    public static final QuietTelemetry INSTANCE = new QuietTelemetry();

    private QuietTelemetry() {
    }

    @Override
    public void record(GenerationTelemetry.Event event, String operationId, String branchId, int chars, AiGatewayError.Kind errorKind) {
    }
}

