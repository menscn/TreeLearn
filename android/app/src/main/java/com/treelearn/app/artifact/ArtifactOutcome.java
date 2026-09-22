package com.treelearn.app.artifact;

/**
 * How one derived use case ended (technical design 10; prompt design 6.4, 7).
 *
 * <p>Stale is a normal result, not a failure: a slow call that loses its baseline is discarded and
 * the stored payload is left alone. Everything else is a refusal, and a refusal never replaces the
 * current artifact — that is the whole point of classifying it instead of throwing.
 *
 * <p>{@link #APPLIED} is the only outcome that wrote a row. The two context-compression refusals
 * exist because that purpose fails the main answer closed instead of degrading: a compression that
 * dropped the reasoning ({@link #REASONING_OMITTED}) or that did not actually shrink the context
 * ({@link #OVER_WINDOW}) is worse than no compression at all (prompt design 6.4).
 */
public enum ArtifactOutcome {

    /** The payload was written for the baseline it was derived from. */
    APPLIED,

    /** The baseline moved on (or the scope is gone), so the payload was dropped and the old value kept. */
    STALE_DISCARDED,

    /** The prompt could not be assembled, so no call was made. The old artifact is kept. */
    BUILD_REFUSED,

    /** The call produced nothing usable. The old artifact is kept. */
    GENERATION_FAILED,

    /** The output was not one legal JSON object of this purpose's envelope. The old artifact is kept. */
    PAYLOAD_INVALID,

    /** A compression answered with an empty summary; there is nothing faithful to store. */
    SUMMARY_EMPTY,

    /** A compression input did not carry the reasoning it must cover, so it fails closed. */
    REASONING_OMITTED,

    /** A compression summary was not smaller than the content it replaced, so it fails closed. */
    OVER_WINDOW
}