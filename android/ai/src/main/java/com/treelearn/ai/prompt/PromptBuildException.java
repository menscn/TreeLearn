package com.treelearn.ai.prompt;

import com.treelearn.core.domain.PromptPurpose;

/**
 * A refused prompt or request build: the caller must not call DeepSeek (prompt design V1.0 §7).
 *
 * <p>It is the {@code :ai} counterpart of the domain's business-refusal channel: the arguments are
 * well formed, but the policy says no — an unknown purpose, a system body that would not be first,
 * free text where a derived JSON payload is required, or a context that cannot hold the reservation.
 * Callers branch on {@link #reason}, never on the message text, so rewording a refusal is never a
 * behaviour change.
 *
 * <p><strong>Redaction is a constructor contract.</strong> The message carries the category, the
 * purpose and character counts only. Prompt bodies, user text, history text and derived payloads
 * are never allowed in it (prompt design §7, technical design §15), which is why the detail is a
 * caller-supplied string that the assembler builds from numbers and category names alone.
 */
public final class PromptBuildException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** Stable, machine-checkable refusal categories. Every value has a producer in this package. */
    public enum Reason {
        /** No purpose was supplied, so no versioned system body can be selected. */
        UNKNOWN_PURPOSE,
        /** A registered version is not owned by the purpose it is registered for. */
        REGISTRY_INCONSISTENT,
        /** The encoded history list itself is missing; an empty list means "no history". */
        MISSING_HISTORY,
        /** A history entry is missing or blank. */
        MALFORMED_HISTORY_MESSAGE,
        /** A history entry carries the system role, which would produce a second system message. */
        SYSTEM_MESSAGE_IN_HISTORY,
        /** The current input is missing or blank. */
        BLANK_CURRENT_INPUT,
        /** A derived purpose was handed free text instead of one structured JSON object. */
        DERIVED_INPUT_NOT_JSON,
        /** Thinking was requested for a derived purpose, which has no thinking policy. */
        THINKING_NOT_APPLICABLE_FOR_PURPOSE,
        /** The system prompt, the current input and the minimum answer do not fit the budget. */
        RESERVE_EXCEEDS_BUDGET,
        /** The reserved capacity plus the encoded history exceeds the budget. */
        HISTORY_EXCEEDS_BUDGET
    }

    public final Reason reason;

    /**
     * @param purpose the purpose the refusal belongs to, or null when none was supplied
     * @param safeDetail a category-level explanation built from numbers and category names; it must
     *        never carry prompt, user, history or derived-input text
     */
    public PromptBuildException(Reason reason, PromptPurpose purpose, String safeDetail) {
        super("prompt build refused [" + reason + "] purpose="
                + (purpose == null ? "-" : purpose.name()) + ": " + safeDetail);
        this.reason = reason;
    }
}
