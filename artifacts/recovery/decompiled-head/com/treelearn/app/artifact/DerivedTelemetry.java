/*
 * Decompiled with CFR 0.152.
 */
package com.treelearn.app.artifact;

import com.treelearn.app.artifact.ArtifactKind;

public interface DerivedTelemetry {
    public void record(Event var1, ArtifactKind var2, String var3, String var4);

    public static enum Event {
        START,
        APPLIED,
        STALE_DISCARDED,
        FAILED;

    }
}

