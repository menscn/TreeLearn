package com.treelearn.core.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The immutable birth context of a non-root branch (technical design 5.3, 6.1).
 *
 * <p>It is an ordered list of <em>references</em> — this branch's creation pinned which user message,
 * which stable answer revision and which finished summary it was built from — plus a schema version
 * and the SHA-256 of its own canonical payload. Nothing here is a copy of an answer, so a snapshot
 * cannot drift with, or bloat, the conversation it describes.
 *
 * <p><strong>Why it exists at all.</strong> Reading the parent branch live would mean a later reset,
 * rename or summary refresh silently re-interpreted a branch that was created before it: two runs of
 * the same child branch would send different context. Freezing the reference list at creation, and
 * pinning {@code sourceRevisionId} rather than following whatever revision a position displays today,
 * is what keeps an old child branch's meaning stable.
 *
 * <p><strong>Immutability is structural, not conventional.</strong> Every field is final, the source
 * list is copied on construction and handed out unmodifiable, and there is no setter. The store backs
 * this up: {@code context_snapshots} rows cannot be updated or deleted on their own.
 *
 * <p><strong>The payload is opaque to the database layer.</strong> {@code ContextSnapshotDao} moves
 * the string unchanged and never parses it; only {@link ContextSnapshotCodec} knows its shape, and
 * {@link #fromStored} is the single reader.
 */
public final class ContextSnapshot {

    /** The payload schema this build writes and is willing to read; it lives in the column, not the text. */
    public static final int SCHEMA_VERSION = 1;

    /** What one reference points at. The set is closed and every value is produced by this package. */
    public enum SourceKind {

        /** A persisted learner message. */
        USER_MESSAGE,

        /** One stable, immutable answer revision. */
        STABLE_REVISION,

        /** A finished context summary that covers the question, the reasoning and the answer together. */
        SUMMARY
    }

    /** One reference inside the ordered list. It carries an identity, never content. */
    public static final class Source {

        public final SourceKind kind;
        public final String refId;

        private Source(SourceKind kind, String refId) {
            this.kind = Guard.object(kind, "source kind");
            this.refId = Guard.id(refId, "source refId");
        }

        /** @throws IllegalArgumentException when {@code refId} is not a canonical UUID string */
        public static Source of(SourceKind kind, String refId) {
            return new Source(kind, refId);
        }

        public static Source userMessage(String messageId) {
            return new Source(SourceKind.USER_MESSAGE, messageId);
        }

        public static Source stableRevision(String revisionId) {
            return new Source(SourceKind.STABLE_REVISION, revisionId);
        }

        public static Source summary(String summaryId) {
            return new Source(SourceKind.SUMMARY, summaryId);
        }

        @Override
        public String toString() {
            return kind + ":" + refId;
        }
    }

    public final String id;
    public final String branchId;
    public final int schemaVersion;
    /** Ordered, immutable and never empty. */
    public final List<Source> sources;
    /** The canonical JSON the database stores verbatim. */
    public final String payload;
    /** Lowercase hexadecimal SHA-256 of {@link #payload}. */
    public final String digest;
    public final long createdAt;

    private ContextSnapshot(String id, String branchId, int schemaVersion, List<Source> sources,
            String payload, String digest, long createdAt) {
        this.id = Guard.id(id, "id");
        this.branchId = Guard.id(branchId, "branchId");
        this.schemaVersion = Guard.atLeast(schemaVersion, 1, "schemaVersion");
        this.sources = Collections.unmodifiableList(new ArrayList<Source>(sources));
        this.payload = Guard.nonBlank(payload, "payload");
        this.digest = Guard.nonBlank(digest, "digest");
        this.createdAt = Guard.epochMillis(createdAt, "createdAt");
    }

    /**
     * Creates the birth snapshot of a branch that is being created right now.
     *
     * <p>The digest is derived here and can never be supplied by a caller, so no path exists that
     * could store a payload whose digest was computed over something else.
     *
     * @param sources the ordered references; the list is copied and must not be empty — a branch
     *        always descends from at least the turn it forked off
     * @throws IllegalArgumentException on a non-UUID branch, a non-UUID reference, an empty list or a
     *         negative timestamp: these are caller bugs, not business conditions
     */
    public static ContextSnapshot create(String branchId, List<Source> sources, long now) {
        Guard.id(branchId, "branchId");
        Guard.epochMillis(now, "createdAt");
        List<Source> checked = new ArrayList<Source>(Guard.object(sources, "sources"));
        if (checked.isEmpty()) {
            throw new IllegalArgumentException("a birth snapshot must name at least one source row");
        }
        for (Source source : checked) {
            Guard.object(source, "source");
        }
        String payload = ContextSnapshotCodec.encode(checked);
        return new ContextSnapshot(Guard.newId(), branchId, SCHEMA_VERSION, checked, payload,
                ContextSnapshotCodec.digestOf(payload), now);
    }

    /**
     * Rehydrates a snapshot already stored as a row. This is the only reader of the payload.
     *
     * <p>Refusals are decided in one fixed order — schema version, structure, then digest — so a row
     * that is wrong in several ways always reports the same first defect.
     *
     * @param digest the digest stored in the row, recomputed and compared against {@code payload}
     * @return the snapshot, or a failure with {@link DomainErrorCode#SNAPSHOT_SCHEMA_UNSUPPORTED},
     *         {@link DomainErrorCode#SNAPSHOT_PAYLOAD_INVALID} or
     *         {@link DomainErrorCode#SNAPSHOT_DIGEST_MISMATCH}. A birth context that cannot be proven
     *         intact must stop the request rather than be partially believed.
     * @throws IllegalArgumentException when the row's identity or timestamp is malformed — that is a
     *         caller bug, not a damaged payload
     */
    public static DomainResult<ContextSnapshot> fromStored(String id, String branchId,
            int schemaVersion, String digest, String payload, long createdAt) {
        Guard.id(id, "id");
        Guard.id(branchId, "branchId");
        Guard.epochMillis(createdAt, "createdAt");
        DomainResult<List<Source>> decoded = ContextSnapshotCodec.decode(schemaVersion, payload);
        if (decoded.isFailure()) {
            return DomainResult.fail(decoded.error());
        }
        String recomputed = ContextSnapshotCodec.digestOf(payload);
        if (!recomputed.equals(digest)) {
            return DomainResult.fail(DomainErrorCode.SNAPSHOT_DIGEST_MISMATCH,
                    "the stored snapshot payload does not hash to its stored digest; the row was "
                            + "edited or written around the codec, and its context can no longer be "
                            + "trusted");
        }
        return DomainResult.ok(new ContextSnapshot(id, branchId, schemaVersion, decoded.value(),
                payload, digest, createdAt));
    }

    @Override
    public String toString() {
        // Identity, counts and a digest prefix only: a diagnostic must never carry the payload (§15).
        return "ContextSnapshot[" + id + " branch=" + branchId + " schema=" + schemaVersion
                + " sources=" + sources.size() + " digest=" + digest.substring(0, 8) + "]";
    }
}