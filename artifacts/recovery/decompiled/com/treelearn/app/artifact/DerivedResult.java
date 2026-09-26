/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.treelearn.core.domain.Guard
 */
package com.treelearn.app.artifact;

import com.treelearn.app.artifact.ArtifactOutcome;
import com.treelearn.app.artifact.ArtifactPayload;
import com.treelearn.core.domain.Guard;

public final class DerivedResult {
    private final ArtifactPayload payload;
    private final ArtifactOutcome refusal;

    private DerivedResult(ArtifactPayload payload, ArtifactOutcome refusal) {
        this.payload = payload;
        this.refusal = refusal;
    }

    static DerivedResult usable(ArtifactPayload payload) {
        return new DerivedResult((ArtifactPayload)Guard.object((Object)payload, (String)"payload"), null);
    }

    static DerivedResult refused(ArtifactOutcome refusal) {
        return new DerivedResult(null, (ArtifactOutcome)((Object)Guard.object((Object)((Object)refusal), (String)"refusal")));
    }

    public boolean isUsable() {
        return this.payload != null;
    }

    public ArtifactPayload payload() {
        return this.payload;
    }

    public ArtifactOutcome refusal() {
        return this.refusal;
    }
}

