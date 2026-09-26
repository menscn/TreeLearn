/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  android.util.Log
 *  com.treelearn.ai.gateway.AiGatewayError$Kind
 */
package com.treelearn.app.wiring;

import android.util.Log;
import com.treelearn.ai.gateway.AiGatewayError;
import com.treelearn.app.generation.GenerationTelemetry;

public final class LogcatTelemetry
implements GenerationTelemetry {
    private static final String TAG = "TreelearnGen";

    @Override
    public void record(GenerationTelemetry.Event event, String operationId, String branchId, int chars, AiGatewayError.Kind errorKind) {
        Log.i((String)TAG, (String)((Object)((Object)event) + " operation=" + (operationId == null ? "-" : operationId) + " branch=" + (branchId == null ? "-" : branchId) + " chars=" + chars + (errorKind == null ? "" : " error=" + errorKind)));
    }
}

