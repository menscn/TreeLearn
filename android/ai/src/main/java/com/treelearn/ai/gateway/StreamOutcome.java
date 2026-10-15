package com.treelearn.ai.gateway;

import com.treelearn.core.domain.Guard;

/**
 * How one streaming call ended. Reported once, after the connection is closed.
 *
 * <p>{@link #COMPLETED} means the provider sent its end-of-stream sentinel: the stream finished
 * normally. It says nothing about whether the answer is usable — an empty answer is still a
 * completed stream — because creating a stable revision is the caller's transaction.
 *
 * <p>{@link #kind} plus {@link #error} are the whole contract for a caller deciding what to persist
 * and what to offer the user; {@link #reasoningChars} and {@link #answerChars} are counters for the
 * caller's diagnostic, not a replacement for the streamed text, which only the caller holds.
 */
public final class StreamOutcome {

    public enum Kind {
        /** The end-of-stream sentinel arrived. */
        COMPLETED,
        /** The connection ended without the sentinel: partial content must not become stable. */
        INTERRUPTED,
        /** The caller cancelled, or refused the accepted response. */
        CANCELED,
        /** A classified {@link AiGatewayError} ended the call. */
        FAILED
    }

    public final Kind kind;
    /** Non-null only for {@link Kind#FAILED}. */
    public final AiGatewayError error;
    /** The provider status, or 0 when no response was obtained. */
    public final int httpStatus;
    public final int reasoningChars;
    public final int answerChars;

    public StreamOutcome(Kind kind, AiGatewayError error, int httpStatus, int reasoningChars,
            int answerChars) {
        this.kind = Guard.object(kind, "kind");
        this.error = error;
        this.httpStatus = Guard.atLeast(httpStatus, 0, "httpStatus");
        this.reasoningChars = Guard.atLeast(reasoningChars, 0, "reasoningChars");
        this.answerChars = Guard.atLeast(answerChars, 0, "answerChars");
    }

    @Override
    public String toString() {
        return "StreamOutcome[" + kind + " http=" + httpStatus + " reasoningChars=" + reasoningChars
                + " answerChars=" + answerChars + (error == null ? "" : " " + error) + "]";
    }
}