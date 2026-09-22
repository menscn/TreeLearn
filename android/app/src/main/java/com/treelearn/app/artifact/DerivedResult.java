package com.treelearn.app.artifact;

import com.treelearn.core.domain.Guard;

/**
 * Either a validated payload or the classified refusal that replaced it (technical design 10).
 *
 * <p>A refusal carries no partial payload. "Guess the field out of the text" is exactly the
 * behaviour the design forbids, so a half-read envelope has nowhere to live in this type.
 */
public final class DerivedResult {

    private final ArtifactPayload payload;

    private final ArtifactOutcome refusal;

    private DerivedResult(ArtifactPayload payload, ArtifactOutcome refusal) {
        this.payload = payload;
        this.refusal = refusal;
    }

    static DerivedResult usable(ArtifactPayload payload) {
        return new DerivedResult(Guard.object(payload, "payload"), null);
    }

    static DerivedResult refused(ArtifactOutcome refusal) {
        return new DerivedResult(null, Guard.object(refusal, "refusal"));
    }

    public boolean isUsable() {
        return payload != null;
    }

    /** @return the payload, or null when this result is a refusal */
    public ArtifactPayload payload() {
        return payload;
    }

    /** @return the refusal, or null when this result is usable */
    public ArtifactOutcome refusal() {
        return refusal;
    }
}