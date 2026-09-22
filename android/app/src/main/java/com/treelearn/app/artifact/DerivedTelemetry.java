package com.treelearn.app.artifact;

/**
 * The redacted record of a derived task (technical design 15).
 *
 * <p>The signature is the redaction policy made mechanical. Every parameter is one of the four
 * things the design allows on a derived log line — the usage, a random attempt id, the first eight
 * characters of the input digest and the status — and there is no parameter for a question, an
 * answer, a reasoning text, an outline, a prompt body or a key, so a caller cannot leak one by
 * passing it by mistake.
 *
 * <p>The id is generated per attempt and never derived from content, so the same input derived twice
 * logs two different ids and the log cannot be joined to the stored text.
 */
public interface DerivedTelemetry {

    /** The four statuses technical design 15 records for a derived task. */
    enum Event {
        START,
        APPLIED,
        STALE_DISCARDED,
        FAILED
    }

    /**
     * @param attemptId a fresh random id for this attempt, never part of a stored artifact's identity
     * @param digestPrefix the first eight characters of the input digest, never the input
     */
    void record(Event event, ArtifactKind kind, String attemptId, String digestPrefix);
}