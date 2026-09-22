package com.treelearn.app.artifact;

import com.treelearn.ai.prompt.DeepSeekRequest;
import com.treelearn.ai.prompt.PromptBuildException;
import com.treelearn.core.domain.Guard;
import com.treelearn.data.db.dao.AiArtifactDao;

/**
 * One derived use case, run end to end: build the request, make the call, validate the answer, and
 * write it back only while the baseline still holds (technical design 10; prompt design 6.4, 7).
 *
 * <p><strong>Every refusal keeps the old artifact.</strong> A prompt that cannot be assembled,
 * a call that produced nothing, an output outside the purpose's own envelope, and a compression
 * that lost its reasoning or did not shrink are all classified and returned; none of them writes a
 * row and none of them falls back to another purpose. In particular nothing here ever retries a
 * derived call with the teaching prompt.
 *
 * <p><strong>Stale is normal.</strong> The write-back re-reads the scope's live baseline inside one
 * transaction and compares it with {@link DerivedRequest#baselineRef()}; a mismatch means a slow
 * result lost its race and is dropped with the stored payload untouched — the same treatment a
 * cleared {@code based_on_revision_id} gets from the artifacts table itself.
 *
 * <p>The only thing that leaves this class besides the outcome is a redacted log line: the kind, a
 * random attempt id, eight characters of the input digest and the status (§15).
 */
public final class DerivedOperation {

    private static final int DIGEST_PREFIX_CHARS = 8;

    private final DerivedStore store;

    private final DerivedTelemetry telemetry;

    private final DerivedCaller caller;

    private final DerivedRequest request;

    private final long now;

    public DerivedOperation(DerivedStore store, DerivedTelemetry telemetry, DerivedCaller caller,
            DerivedRequest request, long now) {
        this.store = Guard.object(store, "store");
        this.telemetry = Guard.object(telemetry, "telemetry");
        this.caller = Guard.object(caller, "caller");
        this.request = Guard.object(request, "request");
        this.now = Guard.epochMillis(now, "now");
    }

    /** @return how this attempt ended; only {@link ArtifactOutcome#APPLIED} stored anything */
    public ArtifactOutcome run() {
        ArtifactKind kind = request.kind();
        String attemptId = Guard.newId();
        String digestPrefix = request.inputDigest().substring(0, DIGEST_PREFIX_CHARS);
        telemetry.record(DerivedTelemetry.Event.START, kind, attemptId, digestPrefix);
        if (kind == ArtifactKind.CONTEXT_COMPRESSION && !request.inputCoversReasoning()) {
            return failed(ArtifactOutcome.REASONING_OMITTED, kind, attemptId, digestPrefix);
        }
        DeepSeekRequest assembled;
        try {
            assembled = request.assemble();
        } catch (PromptBuildException notBuilt) {
            return failed(ArtifactOutcome.BUILD_REFUSED, kind, attemptId, digestPrefix);
        }
        String output = caller.call(assembled, request.model());
        if (output == null || output.trim().isEmpty()) {
            return failed(ArtifactOutcome.GENERATION_FAILED, kind, attemptId, digestPrefix);
        }
        DerivedResult parsed = ArtifactPayloadCodec.parse(kind, output, request.itemCeiling());
        if (!parsed.isUsable()) {
            return failed(parsed.refusal(), kind, attemptId, digestPrefix);
        }
        if (kind == ArtifactKind.CONTEXT_COMPRESSION
                && parsed.payload().summary.length() >= request.compressionSourceChars()) {
            return failed(ArtifactOutcome.OVER_WINDOW, kind, attemptId, digestPrefix);
        }
        return writeBack(parsed.payload(), attemptId, kind, digestPrefix);
    }

    private ArtifactOutcome writeBack(ArtifactPayload payload, String attemptId, ArtifactKind kind,
            String digestPrefix) {
        ArtifactOutcome status = store.inTransaction(() -> {
            String live = store.liveBaseline(kind, request.scopeId());
            if (live == null || !live.equals(request.baselineRef())) {
                return ArtifactOutcome.STALE_DISCARDED;
            }
            store.write(row(payload, attemptId));
            return ArtifactOutcome.APPLIED;
        });
        telemetry.record(status == ArtifactOutcome.APPLIED
                ? DerivedTelemetry.Event.APPLIED : DerivedTelemetry.Event.STALE_DISCARDED,
                kind, attemptId, digestPrefix);
        return status;
    }

    private AiArtifactDao.Row row(ArtifactPayload payload, String attemptId) {
        return new AiArtifactDao.Row(attemptId, request.kind().wireId, request.scopeId(),
                request.kind().purpose.policyPrefix, request.policyVersion(), payload.canonicalJson(),
                request.basedOnRevisionId(), request.inputDigest(), now, now);
    }

    private ArtifactOutcome failed(ArtifactOutcome outcome, ArtifactKind kind, String attemptId,
            String digestPrefix) {
        telemetry.record(DerivedTelemetry.Event.FAILED, kind, attemptId, digestPrefix);
        return outcome;
    }
}