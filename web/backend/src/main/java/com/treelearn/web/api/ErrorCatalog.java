package com.treelearn.web.api;

import java.util.Set;
import org.springframework.http.HttpStatus;

/**
 * 错误码 → HTTP 状态与 retryable 的集中映射（契约 {@code web/contracts/api.md} §3.2/§3.3）。
 *
 * <p>同一错误码的状态固定；无法识别的码回落 {@code 400}（仅在词汇表之外出现，正常路径不会产生）。
 */
final class ErrorCatalog {

    private static final Set<String> RETRYABLE = Set.of(
            "RATE_LIMITED", "PROVIDER_UNAVAILABLE", "NETWORK_INTERRUPTED", "PARSE_FAILED",
            "STORAGE_REFUSED", "KEY_SAVE_FAILED", "WEB_SEARCH_FAILED", "INTERNAL_ERROR");

    private ErrorCatalog() {}

    static HttpStatus status(String code) {
        switch (code) {
            case "OPERATION_NOT_COMPLETABLE":
            case "NOT_FOUND":
                return HttpStatus.NOT_FOUND;
            case "OPERATION_NOT_STABLE":
            case "REVISION_IMMUTABLE":
            case "ILLEGAL_STATE_TRANSITION":
            case "SEQUENCE_OUT_OF_ORDER":
            case "CONTEXT_SOURCE_MISSING":
            case "CONTEXT_SUMMARY_MISSING":
            case "BRANCH_BUSY":
            case "DELETION_PLAN_EXPIRED":
            case "SCOPE_UNRESOLVABLE":
            case "TOPIC_ID_ALREADY_EXISTS":
            case "PROVIDER_NAME_TAKEN":
                return HttpStatus.CONFLICT;
            case "RATE_LIMITED":
            case "PROVIDER_UNAVAILABLE":
            case "NETWORK_INTERRUPTED":
            case "PARSE_FAILED":
            case "WEB_SEARCH_FAILED":
                return HttpStatus.SERVICE_UNAVAILABLE;
            case "PROMPT_POLICY_VERSION_REQUIRED":
            case "PROMPT_POLICY_VERSION_MISMATCH":
            case "SNAPSHOT_PAYLOAD_INVALID":
            case "SNAPSHOT_DIGEST_MISMATCH":
            case "SNAPSHOT_SCHEMA_UNSUPPORTED":
            case "STORAGE_REFUSED":
            case "KEY_SAVE_FAILED":
            case "INTERNAL_ERROR":
                return HttpStatus.INTERNAL_SERVER_ERROR;
            default:
                return HttpStatus.BAD_REQUEST;
        }
    }

    static boolean retryable(String code) {
        return RETRYABLE.contains(code);
    }
}
