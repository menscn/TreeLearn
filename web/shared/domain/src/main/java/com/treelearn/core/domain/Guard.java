package com.treelearn.core.domain;

import java.util.UUID;

/**
 * Construction-time invariant checks for the pure-Java domain model in {@code :core}.
 *
 * <p>Every domain value object validates its own invariants in its constructor and fails fast with
 * {@link IllegalArgumentException}: a programming error must never reach the database. Domain
 * <em>business</em> failures (an illegal state transition, a non-stable revision used as a branch
 * source, an out-of-order sequence) are reported through {@link DomainResult} instead, never by
 * throwing, so callers are forced to handle them.
 *
 * <p><strong>The error-channel rule</strong> (stated once, here, because independent audits found
 * individual entry points mixing the channels for the same class of input). There are exactly
 * three, and every public entry point of this package must pick the one that matches:
 *
 * <ul>
 *   <li><strong>Malformed input</strong> — a null where a value is required, a non-UUID identifier,
 *       a negative timestamp: {@link IllegalArgumentException}. The caller has a bug. This is what
 *       {@link #id}, {@link #object}, {@link #nonBlank} and friends throw, and factories must throw
 *       it too rather than convert a missing argument into an error code.
 *   <li><strong>Wrong time</strong> — the arguments are well formed, but the receiver is in a state
 *       where the operation does not apply at all (asking a terminal state for its recovery target):
 *       {@link IllegalStateException}. The caller misread the lifecycle.
 *   <li><strong>Business refusal</strong> — arguments are well formed and the receiver is in a valid
 *       state, but the domain rules say no (an attempt that never reached {@code STABLE} used as a
 *       branch source, a reading position ahead of what exists, two arguments that are individually
 *       valid but contradict each other): {@link DomainResult} failure. The caller must handle it, so
 *       it is never signalled by throwing.
 * </ul>
 *
 * <p>{@link DomainResult} is a <em>third</em> channel alongside the two exceptions; a method that
 * returns it still throws for the first two cases. Round-3 and round-4 independent audits found
 * entry points that disagreed about which case a missing argument was; the three bullets above are
 * the single answer.
 *
 * <p><strong>The declared exceptions to the first bullet</strong>, listed here so the list exists in
 * exactly one place instead of being re-argued at each call site. The prompt policy version is the
 * only mandatory value in this package whose <em>absence</em> is itself a business condition rather
 * than a programming error: prompt design §3.2 makes the version mandatory, so "there is no version"
 * is expressible, reportable and must survive to the caller as a code. Exactly three entry points
 * return a code instead of throwing when the version is absent:
 *
 * <ul>
 *   <li>{@link AnswerRevision#create} — a stable answer must record the version that produced it.
 *   <li>{@link GenerationCommand#create} — a command must pin the version before submission.
 *   <li>{@link PromptSpec#of} — a prompt spec is only meaningful against the version it implements.
 * </ul>
 *
 * <p>The three do not ask the same question, and that is deliberate rather than drift: a revision
 * records no <em>declared</em> purpose, so {@code AnswerRevision} asks whether <em>any</em> usage owns
 * the version ({@link PromptPurpose#ownerOf}), while the other two ask whether the purpose they were
 * handed owns it ({@link PromptPurpose#versionFailureFor}). Both predicates, and the shared
 * definition of "absent" ({@link PromptPurpose#isAbsent}), live in {@link PromptPurpose}, so the rule
 * has one home per question.
 *
 * <p>Nothing else in this package does this. A new entry point that wants the same treatment must be
 * added to this list. A round-1-independent audit of T-021 found {@code GenerationCommand} had taken
 * the exemption without declaring it and that {@code PromptSpec} had answered the same cross-purpose
 * input through the opposite channel, which is why the list is written down and why the ownership
 * decision is now shared.
 */
public final class Guard {

    private Guard() {
    }

    /**
     * Returns {@code value} if it is a canonical lowercase UUID string.
     *
     * <p>{@link UUID#fromString(String)} is lenient — it accepts {@code "1-1-1-1-1"} and short
     * groups, and one UUID therefore has many spellings. Because identifiers are compared and keyed
     * as strings, a second spelling would silently defeat {@code BranchSource.pinsRevision} and
     * create duplicate primary keys. The canonical form is therefore re-derived and compared.
     */
    public static String id(String value, String field) {
        if (value == null || value.isEmpty()) {
            throw new IllegalArgumentException(field + " must be a non-empty UUID string");
        }
        UUID parsed;
        try {
            parsed = UUID.fromString(value);
        } catch (IllegalArgumentException cause) {
            throw new IllegalArgumentException(field + " must be a UUID string but was: " + value);
        }
        String canonical = parsed.toString();
        if (!canonical.equals(value)) {
            throw new IllegalArgumentException(field + " must use the canonical lowercase UUID form "
                    + canonical + " but was: " + value);
        }
        return value;
    }

    /** Returns {@code value} if it is null or a well-formed UUID string. */
    public static String idOrNull(String value, String field) {
        return value == null ? null : id(value, field);
    }

    /** Generates a new domain identifier. All domain IDs are application-generated UUIDs. */
    public static String newId() {
        return UUID.randomUUID().toString();
    }

    public static String text(String value, String field) {
        if (value == null) {
            throw new IllegalArgumentException(field + " must not be null");
        }
        return value;
    }

    /** Non-null check for any reference type (enums, value objects). */
    public static <T> T object(T value, String field) {
        if (value == null) {
            throw new IllegalArgumentException(field + " must not be null");
        }
        return value;
    }

    public static String nonBlank(String value, String field) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }

    public static long epochMillis(long value, String field) {
        if (value < 0) {
            throw new IllegalArgumentException(field + " must not be negative: " + value);
        }
        return value;
    }

    /** For monotonic counters such as unread sequences, where "epoch" would be misleading. */
    public static long nonNegative(long value, String field) {
        if (value < 0) {
            throw new IllegalArgumentException(field + " must not be negative: " + value);
        }
        return value;
    }

    public static int atLeast(int value, int minimum, String field) {
        if (value < minimum) {
            throw new IllegalArgumentException(field + " must be >= " + minimum + " but was " + value);
        }
        return value;
    }
}
