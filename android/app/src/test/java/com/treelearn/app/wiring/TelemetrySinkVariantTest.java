package com.treelearn.app.wiring;


import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.treelearn.app.generation.GenerationTelemetry;

import org.junit.Test;

/**
 * T-018: the telemetry variant selection. The autonomous decision recorded on the task is
 * "release TelemetrySink 使用 no-op；debug 也只允许脱敏元数据" — release ships a sink with no
 * output path at all, debug keeps the redacted logcat line. The factory is the single judgment
 * point, so the variant rule is pinned here once instead of being re-decided at every
 * construction site.
 */
public class TelemetrySinkVariantTest {

    @Test
    public void theReleaseVariantGetsTheQuietSinkWithNoOutputPath() {
        GenerationTelemetry sink = TelemetrySink.forVariant(true);

        assertSame(QuietTelemetry.INSTANCE, sink);
    }

    @Test
    public void theDebugVariantKeepsTheRedactedLogcatSink() {
        GenerationTelemetry sink = TelemetrySink.forVariant(false);

        assertTrue(sink instanceof LogcatTelemetry);
    }

    @Test
    public void theQuietSinkAcceptsEveryEventWithoutProducingAnything() {
        // The no-op contract is structural: record() must be callable for the full event range
        // and leave no observable trace (an empty body). A failure or an exception here would
        // mean the release path grew an output side effect.
        for (GenerationTelemetry.Event event : GenerationTelemetry.Event.values()) {
            QuietTelemetry.INSTANCE.record(event, "op", "branch", 12, null);
            QuietTelemetry.INSTANCE.record(event, null, null, 0, null);
        }
    }
}
