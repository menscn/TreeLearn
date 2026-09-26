/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.treelearn.ai.prompt.DeepSeekRequest
 *  com.treelearn.ai.prompt.PromptBuildException
 *  com.treelearn.core.domain.Guard
 *  com.treelearn.data.db.dao.AiArtifactDao$Row
 */
package com.treelearn.app.artifact;

import com.treelearn.ai.prompt.DeepSeekRequest;
import com.treelearn.ai.prompt.PromptBuildException;
import com.treelearn.app.artifact.ArtifactKind;
import com.treelearn.app.artifact.ArtifactOutcome;
import com.treelearn.app.artifact.ArtifactPayload;
import com.treelearn.app.artifact.ArtifactPayloadCodec;
import com.treelearn.app.artifact.DerivedCaller;
import com.treelearn.app.artifact.DerivedRequest;
import com.treelearn.app.artifact.DerivedResult;
import com.treelearn.app.artifact.DerivedStore;
import com.treelearn.app.artifact.DerivedTelemetry;
import com.treelearn.core.domain.Guard;
import com.treelearn.data.db.dao.AiArtifactDao;

public final class DerivedOperation {
    private static final int DIGEST_PREFIX_CHARS = 8;
    private final DerivedStore store;
    private final DerivedTelemetry telemetry;
    private final DerivedCaller caller;
    private final DerivedRequest request;
    private final long now;

    public DerivedOperation(DerivedStore store, DerivedTelemetry telemetry, DerivedCaller caller, DerivedRequest request, long now) {
        this.store = (DerivedStore)Guard.object((Object)store, (String)"store");
        this.telemetry = (DerivedTelemetry)Guard.object((Object)telemetry, (String)"telemetry");
        this.caller = (DerivedCaller)Guard.object((Object)caller, (String)"caller");
        this.request = (DerivedRequest)Guard.object((Object)request, (String)"request");
        this.now = Guard.epochMillis((long)now, (String)"now");
    }

    public ArtifactOutcome run() {
        DeepSeekRequest assembled;
        ArtifactKind kind = this.request.kind();
        String attemptId = Guard.newId();
        String digestPrefix = this.request.inputDigest().substring(0, 8);
        this.telemetry.record(DerivedTelemetry.Event.START, kind, attemptId, digestPrefix);
        if (kind == ArtifactKind.CONTEXT_COMPRESSION && !this.request.inputCoversReasoning()) {
            return this.failed(ArtifactOutcome.REASONING_OMITTED, kind, attemptId, digestPrefix);
        }
        try {
            assembled = this.request.assemble();
        }
        catch (PromptBuildException notBuilt) {
            return this.failed(ArtifactOutcome.BUILD_REFUSED, kind, attemptId, digestPrefix);
        }
        String output = this.caller.call(assembled, this.request.model());
        if (output == null || output.trim().isEmpty()) {
            return this.failed(ArtifactOutcome.GENERATION_FAILED, kind, attemptId, digestPrefix);
        }
        DerivedResult parsed = ArtifactPayloadCodec.parse(kind, output, this.request.itemCeiling());
        if (!parsed.isUsable()) {
            return this.failed(parsed.refusal(), kind, attemptId, digestPrefix);
        }
        if (kind == ArtifactKind.CONTEXT_COMPRESSION && parsed.payload().summary.length() >= this.request.compressionSourceChars()) {
            return this.failed(ArtifactOutcome.OVER_WINDOW, kind, attemptId, digestPrefix);
        }
        return this.writeBack(parsed.payload(), attemptId, kind, digestPrefix);
    }

    private ArtifactOutcome writeBack(ArtifactPayload payload, String attemptId, ArtifactKind kind, String digestPrefix) {
        ArtifactOutcome status = this.store.inTransaction(() -> {
            String live = this.store.liveBaseline(kind, this.request.scopeId());
            if (live == null || !live.equals(this.request.baselineRef())) {
                return ArtifactOutcome.STALE_DISCARDED;
            }
            this.store.write(this.row(payload, attemptId));
            return ArtifactOutcome.APPLIED;
        });
        this.telemetry.record(status == ArtifactOutcome.APPLIED ? DerivedTelemetry.Event.APPLIED : DerivedTelemetry.Event.STALE_DISCARDED, kind, attemptId, digestPrefix);
        return status;
    }

    private AiArtifactDao.Row row(ArtifactPayload payload, String attemptId) {
        return new AiArtifactDao.Row(attemptId, this.request.kind().wireId, this.request.scopeId(), this.request.kind().purpose.policyPrefix, this.request.policyVersion(), payload.canonicalJson(), this.request.basedOnRevisionId(), this.request.inputDigest(), this.now, this.now);
    }

    private ArtifactOutcome failed(ArtifactOutcome outcome, ArtifactKind kind, String attemptId, String digestPrefix) {
        this.telemetry.record(DerivedTelemetry.Event.FAILED, kind, attemptId, digestPrefix);
        return outcome;
    }
}

