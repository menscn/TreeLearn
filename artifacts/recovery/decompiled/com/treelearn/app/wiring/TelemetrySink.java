/*
 * Decompiled with CFR 0.152.
 */
package com.treelearn.app.wiring;

import com.treelearn.app.generation.GenerationTelemetry;
import com.treelearn.app.wiring.LogcatTelemetry;
import com.treelearn.app.wiring.QuietTelemetry;

public final class TelemetrySink {
    private TelemetrySink() {
    }

    public static GenerationTelemetry forVariant(boolean release) {
        return release ? QuietTelemetry.INSTANCE : new LogcatTelemetry();
    }
}

