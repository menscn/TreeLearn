package com.treelearn.ai.gateway;

import com.treelearn.core.domain.Guard;

/**
 * One classified gateway failure (technical design 9.5).
 *
 * <p>The categories are the ones the design fixes, and this class is their single home: the status
 * mapping lives in {@link #forHttpStatus} so the streaming path and the balance path cannot disagree
 * about what, say, a 402 means. Callers branch on {@link #kind}, never on {@link #detail}.
 *
 * <p><strong>Redaction is a constructor contract.</strong> {@link #detail} is built from category
 * names, numbers and exception class names only. The provider's error body is never read for a
 * failed response (see {@code HttpsTransport}), so it cannot reach a diagnostic, a log line or this
 * field.
 *
 * <p>Every category has at least one producer in this package: {@link #keyMissing} (no stored key),
 * {@link #forHttpStatus} (any non-2xx response), {@link #networkInterrupted} (transport failure),
 * {@link #parseFailed} (unusable payload) and {@link #inBandProviderError} (an error object inside a
 * 2xx stream).
 */
public final class AiGatewayError {

    /** Stable, machine-checkable failure categories. */
    public enum Kind {
        /** No API key is stored on this device, so no request may be sent. */
        KEY_MISSING,
        /** The provider rejected the credential (HTTP 401/403). */
        AUTH_INVALID,
        /** The account cannot pay for this call (HTTP 402 or an unavailable balance). */
        BALANCE_UNAVAILABLE,
        /** The provider rejected the request itself (HTTP 400/422 and other 4xx). */
        REQUEST_INVALID,
        /** The provider throttled the call (HTTP 429). */
        RATE_LIMITED,
        /** The provider failed to serve the call (HTTP 5xx). */
        PROVIDER_UNAVAILABLE,
        /** The connection failed, timed out or ended before the stream completed. */
        NETWORK_INTERRUPTED,
        /** A stream payload could not be trusted, so it was refused instead of half-applied. */
        PARSE_FAILED
    }

    public final Kind kind;
    /** The HTTP status that produced this failure, or 0 when no response was obtained. */
    public final int httpStatus;
    /** Sanitized explanation: category names and numbers only, never a payload or a key. */
    public final String detail;

    private AiGatewayError(Kind kind, int httpStatus, String detail) {
        this.kind = Guard.object(kind, "kind");
        this.httpStatus = Guard.atLeast(httpStatus, 0, "httpStatus");
        this.detail = Guard.nonBlank(detail, "detail");
    }

    public static AiGatewayError of(Kind kind, int httpStatus, String detail) {
        return new AiGatewayError(kind, httpStatus, detail);
    }

    public static AiGatewayError keyMissing() {
        return of(Kind.KEY_MISSING, 0, "no API key is stored on this device, so no request was sent");
    }

    /**
     * The single status mapping. 401/403 and 402/429 are named by the design; every other 4xx is a
     * request the provider refused and every 5xx is a provider-side failure. Anything else that is
     * not 2xx (a redirect we do not follow, an unexpected 1xx) is treated as a provider-side failure
     * rather than as success.
     */
    public static AiGatewayError forHttpStatus(int status) {
        if (status == 401 || status == 403) {
            return of(Kind.AUTH_INVALID, status, "the provider refused the credential");
        }
        if (status == 402) {
            return of(Kind.BALANCE_UNAVAILABLE, status, "the provider declined to bill this call");
        }
        if (status == 429) {
            return of(Kind.RATE_LIMITED, status, "the provider throttled this call");
        }
        if (status / 100 == 4) {
            return of(Kind.REQUEST_INVALID, status, "the provider rejected the request");
        }
        if (status / 100 == 5) {
            return of(Kind.PROVIDER_UNAVAILABLE, status, "the provider failed to serve the request");
        }
        return of(Kind.PROVIDER_UNAVAILABLE, status, "a non-2xx response outside 4xx and 5xx");
    }

    /** @param reason the exception class name, never a message that could carry a payload */
    public static AiGatewayError networkInterrupted(String reason) {
        return of(Kind.NETWORK_INTERRUPTED, 0,
                "the connection failed before the stream completed: " + reason);
    }

    public static AiGatewayError parseFailed(String detail) {
        return of(Kind.PARSE_FAILED, 0, detail);
    }

    public static AiGatewayError inBandProviderError() {
        return of(Kind.PROVIDER_UNAVAILABLE, 0,
                "the provider reported an error inside an accepted stream");
    }

    @Override
    public String toString() {
        return "AiGatewayError[" + kind + " http=" + httpStatus + " detail=" + detail + "]";
    }
}