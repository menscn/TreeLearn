package com.treelearn.app.wiring;

import android.util.Log;

import com.treelearn.ai.gateway.AiGatewayError;
import com.treelearn.app.generation.GenerationTelemetry;

/**
 * The production telemetry sink: one logcat line per attempt event, and no content ever.
 *
 * <p>The interface is the guard, not this class. {@link GenerationTelemetry#record} cannot carry a
 * question, an answer, a reasoning text, a prompt body or a credential - it takes an event, two row
 * ids, a character count and an error category - so the line below is structurally incapable of
 * leaking the conversation (technical design 15). Do not widen it to "carry a message for
 * debugging": that is the change this shape exists to prevent.
 */
public final class LogcatTelemetry implements GenerationTelemetry {

    private static final String TAG = "TreelearnGen";

    @Override
    public void record(Event event, String operationId, String branchId, int chars,
            AiGatewayError.Kind errorKind) {
        Log.i(TAG, event + " operation=" + (operationId == null ? "-" : operationId)
                + " branch=" + (branchId == null ? "-" : branchId)
                + " chars=" + chars
                + (errorKind == null ? "" : " error=" + errorKind));
    }
}