package com.treelearn.core.domain;

/**
 * The seven built-in answer styles a topic may choose (SPEC-2026-09-21-ai-persona-preference).
 *
 * <p>A persona changes how an answer is taught, never what is true: the tutor baseline - honesty,
 * correction, no invention - is appended <em>after</em> this style at request-build time and cannot
 * be waived by it. The choice is topic-scoped and only affects answers generated after it is made;
 * an existing revision keeps the persona it was produced under.
 *
 * <p><strong>The wire id is the identity.</strong> The column stores {@link #wireId}, not the enum
 * name and not the Chinese label. Renaming a constant is therefore a compile-time edit; changing a
 * wire id is a data migration and must never be done. {@link #fromWireId} is the only read-side
 * mapping and falls back to {@link #DEFAULT} for values this build does not know, because a row left
 * behind by a downgrade must not make a whole topic list unreadable.
 */
public enum Persona {

    DEFAULT("default"),
    HAND_HOLDING("hand-holding"),
    STRICT_MENTOR("strict-mentor"),
    STUDY_BUDDY("study-buddy"),
    SARCASTIC_FRIEND("sarcastic-friend"),
    LATE_NIGHT("late-night"),
    LEARNING_REPAIR("learning-repair");

    /** The only value this build ever writes into a persona column. */
    public final String wireId;

    Persona(String wireId) {
        this.wireId = wireId;
    }

    /**
     * @return the persona for {@code wireId}, or {@link #DEFAULT} when the value is absent or not
     *         owned by this build. The fallback is deliberate and is reported separately by
     *         {@link #isKnown}; this method itself never throws for data read from a column
     */
    public static Persona fromWireId(String wireId) {
        for (Persona persona : values()) {
            if (persona.wireId.equals(wireId)) {
                return persona;
            }
        }
        return DEFAULT;
    }

    /** Whether {@code wireId} names a persona this build knows; false is the one-time notice trigger. */
    public static boolean isKnown(String wireId) {
        for (Persona persona : values()) {
            if (persona.wireId.equals(wireId)) {
                return true;
            }
        }
        return false;
    }

    /** Whether a request for {@code purpose} may carry this style (only the main answer may). */
    public boolean isDefault() {
        return this == DEFAULT;
    }
}
