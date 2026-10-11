package com.treelearn.core.domain;

/**
 * Stable, machine-checkable categories for domain business failures.
 *
 * <p>Codes are part of the domain contract: UI and tests branch on them instead of parsing
 * free-form messages, so a wording change never becomes a behaviour change.
 *
 * <p>Per the error-channel rule in {@link Guard}, this enum only covers the <em>third</em> channel —
 * arguments well formed, receiver in a valid state, business rules say no. A missing or malformed
 * argument is an {@link IllegalArgumentException} and a wrong-time call is an
 * {@link IllegalStateException}; neither gets a code here.
 *
 * <p>Six of these codes carry the fail-closed refusals of the context pipeline ({@code SNAPSHOT_*} and
 * {@code CONTEXT_*}): a stored birth snapshot that cannot be proven intact, a context source that
 * cannot be resolved, a summary that was never produced, or a context that does not fit the capacity
 * the caller already reserved. They are reported through {@link DomainResult} rather than thrown
 * because every one of them is a condition the caller must handle — refuse the request, tell the
 * learner it failed, offer a retry — and none of them is a crash.
 *
 * <p>Every code here has at least one producer in this package. A code with no producer is not
 * "surface we may need later": it is a rule nobody can be held to, and it invites a caller to branch
 * on a value that can never arrive. {@code OPERATION_TERMINAL} was removed on exactly that ground —
 * T-021's terminal-callback contract is expressed as {@link #ILLEGAL_STATE_TRANSITION}, so nothing
 * could ever return it. Add a code together with the code path that returns it, not before.
 */
public enum DomainErrorCode {

    /** The requested state transition is not allowed from the current state. */
    ILLEGAL_STATE_TRANSITION,

    /** An operation that never reached STABLE was used where a stable version is required. */
    OPERATION_NOT_STABLE,

    /** The operation cannot be stabilized yet (for example the final answer is still empty). */
    OPERATION_NOT_COMPLETABLE,

    /** A stable revision was asked to change. Stable versions are immutable by contract. */
    REVISION_IMMUTABLE,

    /** A generation command arrived without the prompt policy version it must pin. */
    PROMPT_POLICY_VERSION_REQUIRED,

    /** The pinned prompt policy version does not belong to the declared prompt purpose. */
    PROMPT_POLICY_VERSION_MISMATCH,

    /** A fork anchor range is malformed, or lacks the quote/hash fallback. */
    ANCHOR_INVALID,

    /** A sequence or ordinal moved backwards, or skipped ahead of what is committed. */
    SEQUENCE_OUT_OF_ORDER,

    /** The message role cannot carry the requested operation (for example a revision on a user message). */
    MESSAGE_ROLE_MISMATCH,

    /** This build cannot read the snapshot schema version a stored row declares. */
    SNAPSHOT_SCHEMA_UNSUPPORTED,

    /** A stored snapshot payload is not this schema's canonical JSON. */
    SNAPSHOT_PAYLOAD_INVALID,

    /** A stored snapshot payload does not hash to the digest stored beside it. */
    SNAPSHOT_DIGEST_MISMATCH,

    /** A context source the snapshot pins cannot be resolved in the store. */
    CONTEXT_SOURCE_MISSING,

    /** A summary the snapshot pins has not been produced, so the turns it covers would be lost. */
    CONTEXT_SUMMARY_MISSING,

    /** The context plus the capacity the caller already reserved does not fit the request. */
    CONTEXT_EXCEEDS_BUDGET
}
