package com.treelearn.web.storage;

import static com.treelearn.web.storage.DesktopSchema.AiArtifacts.BASED_ON_REVISION_ID;
import static com.treelearn.web.storage.DesktopSchema.AiArtifacts.CREATED_AT;
import static com.treelearn.web.storage.DesktopSchema.AiArtifacts.ID;
import static com.treelearn.web.storage.DesktopSchema.AiArtifacts.INPUT_DIGEST;
import static com.treelearn.web.storage.DesktopSchema.AiArtifacts.KIND;
import static com.treelearn.web.storage.DesktopSchema.AiArtifacts.PAYLOAD;
import static com.treelearn.web.storage.DesktopSchema.AiArtifacts.PROMPT_POLICY_VERSION;
import static com.treelearn.web.storage.DesktopSchema.AiArtifacts.PROMPT_PURPOSE;
import static com.treelearn.web.storage.DesktopSchema.AiArtifacts.SCOPE_ID;
import static com.treelearn.web.storage.DesktopSchema.AiArtifacts.TABLE;
import static com.treelearn.web.storage.DesktopSchema.AiArtifacts.UPDATED_AT;

import com.treelearn.core.domain.Guard;
import com.treelearn.core.domain.PromptPurpose;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/**
 * Row access for {@code ai_artifacts}: AI-derived products (title, summary, recommendations,
 * compression) that may be recomputed and therefore may go stale (task T-2026-10-10-011).
 *
 * <p>Semantic reference — the Android {@code com.treelearn.data.db.dao.AiArtifactDao} plus the write
 * composition in {@code com.treelearn.app.artifact.SqliteDerivedStore} it mirrors:
 * <ul>
 *   <li>{@code AiArtifactDao.insert} → {@link #insert(Artifact)}: a single conditional statement whose
 *       own {@code WHERE NOT EXISTS} makes a second row for one {@code (kind, scope_id)} impossible; a
 *       caller that would create one gets a {@link StorageException} instead, and must replace.</li>
 *   <li>{@code AiArtifactDao.applyIfBaselineMatches} → {@link #applyIfBaselineMatches(Artifact)}: a
 *       conditional {@code UPDATE} whose {@code WHERE} carries the baseline ({@code based_on_revision_id}
 *       and {@code input_digest}). One affected row means the baseline still held and the payload was
 *       replaced in place; zero means the result is stale and was dropped, leaving the stored payload
 *       untouched. There is deliberately no "SELECT, compare, then UPDATE".</li>
 *   <li>{@code SqliteDerivedStore.write}'s replace branch → {@link #replace(Artifact)}: when the stored
 *       baseline no longer matches, the old row is removed and the fresh one inserted, so a derivation
 *       for a moved baseline does not sit beside its predecessor.</li>
 *   <li>{@code DerivedService}'s digest-guarded reads ({@code homeRecommendations},
 *       {@code compressHistory}) → {@link #findFresh}: a stored artifact whose {@code input_digest} is not
 *       the caller's expected digest reads as none, so an older result never masquerades as the new
 *       one.</li>
 *   <li>{@code AiArtifactDao.deleteByScope} → {@link #deleteForScope}: the cleanup counterpart of the
 *       missing foreign key — {@code scope_id} is polymorphic, so deleting a scope does not cascade to
 *       its artifacts.</li>
 * </ul>
 *
 * <p>{@code payload} is opaque here; its schema belongs to the artifact codec.
 */
public final class ArtifactStore {

    /** The outcome of a conditional write-back. Stale is a normal result, not a failure. */
    public enum Writeback {
        /** The baseline matched and the stored payload was replaced. */
        APPLIED,
        /** The baseline no longer matched; the stored payload is untouched. */
        DISCARDED_STALE
    }

    private static final String COLUMNS = ID + ", " + KIND + ", " + SCOPE_ID + ", " + PROMPT_PURPOSE
            + ", " + PROMPT_POLICY_VERSION + ", " + PAYLOAD + ", " + BASED_ON_REVISION_ID + ", "
            + INPUT_DIGEST + ", " + CREATED_AT + ", " + UPDATED_AT;
    private static final String INSERT_IF_ABSENT = "INSERT INTO " + TABLE + "(" + COLUMNS
            + ") SELECT ?, ?, ?, ?, ?, ?, ?, ?, ?, ? WHERE NOT EXISTS (SELECT 1 FROM " + TABLE
            + " WHERE " + KIND + " = ? AND " + SCOPE_ID + " = ?)";
    private static final String SELECT_BY_PAIR = "SELECT * FROM " + TABLE + " WHERE " + KIND + " = ? AND "
            + SCOPE_ID + " = ?";
    private static final String SELECT_BY_SCOPE = "SELECT * FROM " + TABLE + " WHERE " + SCOPE_ID
            + " = ? ORDER BY " + KIND + " ASC, " + ID + " ASC";
    private static final String APPLY_BASELINE = "UPDATE " + TABLE + " SET " + PAYLOAD + " = ?, "
            + PROMPT_PURPOSE + " = ?, " + PROMPT_POLICY_VERSION + " = ?, " + BASED_ON_REVISION_ID
            + " = ?, " + INPUT_DIGEST + " = ?, " + UPDATED_AT + " = ? WHERE " + KIND + " = ? AND "
            + SCOPE_ID + " = ? AND " + BASED_ON_REVISION_ID + " IS NOT NULL AND " + BASED_ON_REVISION_ID
            + " = ? AND " + INPUT_DIGEST + " = ?";
    private static final String DELETE_BY_SCOPE = "DELETE FROM " + TABLE + " WHERE " + KIND + " = ? AND "
            + SCOPE_ID + " = ?";

    private final JdbcSupport jdbc;

    public ArtifactStore(JdbcSupport jdbc) {
        this.jdbc = jdbc;
    }

    /** Inserts the first artifact for a {@code (kind, scope_id)} pair; a second one is refused. */
    public Artifact insert(Artifact candidate) {
        return jdbc.runInTransaction(connection -> insert(connection, candidate));
    }

    private Artifact insert(Connection connection, Artifact candidate) {
        int changed = jdbc.update(connection, INSERT_IF_ABSENT, s -> {
            s.setString(1, candidate.id); s.setString(2, candidate.kind);
            s.setString(3, candidate.scopeId); s.setString(4, candidate.purpose.policyPrefix);
            s.setString(5, candidate.promptPolicyVersion); s.setString(6, candidate.payload);
            s.setString(7, candidate.basedOnRevisionId); s.setString(8, candidate.inputDigest);
            s.setLong(9, candidate.createdAt); s.setLong(10, candidate.updatedAt);
            s.setString(11, candidate.kind); s.setString(12, candidate.scopeId);
        });
        if (changed != 1) {
            throw new StorageException("an artifact for kind=" + candidate.kind + " scope="
                    + candidate.scopeId + " already exists; the first write of a baseline is insert and"
                    + " every later one is applyIfBaselineMatches or replace");
        }
        return candidate;
    }

    /** Replaces the payload only if the stored baseline still matches the candidate's. */
    public Writeback applyIfBaselineMatches(Artifact candidate) {
        int updated = jdbc.update(APPLY_BASELINE, s -> {
            s.setString(1, candidate.payload); s.setString(2, candidate.purpose.policyPrefix);
            s.setString(3, candidate.promptPolicyVersion);
            s.setString(4, candidate.basedOnRevisionId); s.setString(5, candidate.inputDigest);
            s.setLong(6, candidate.updatedAt); s.setString(7, candidate.kind);
            s.setString(8, candidate.scopeId); s.setString(9, candidate.basedOnRevisionId);
            s.setString(10, candidate.inputDigest);
        });
        if (updated == 1) {
            return Writeback.APPLIED;
        }
        if (updated == 0) {
            return Writeback.DISCARDED_STALE;
        }
        throw new StorageException("a baseline write-back for kind=" + candidate.kind + " scope="
                + candidate.scopeId + " matched " + updated + " rows; insert is conditional, so more"
                + " than one current artifact for a pair means the store holds duplicates");
    }

    /** Removes the stored artifact for the pair and writes the fresh one, in one transaction. */
    public Artifact replace(Artifact candidate) {
        return jdbc.runInTransaction(connection -> {
            jdbc.update(connection, DELETE_BY_SCOPE, s -> {
                s.setString(1, candidate.kind); s.setString(2, candidate.scopeId);
            });
            return insert(connection, candidate);
        });
    }

    /** The current artifact for this pair, or empty when none was ever derived. */
    public Optional<Artifact> find(String kind, String scopeId) {
        return jdbc.queryOne(SELECT_BY_PAIR, s -> {
            s.setString(1, kind); s.setString(2, scopeId);
        }, ArtifactStore::read);
    }

    /**
     * The stored artifact, but only while its input digest is the caller's expected one.
     *
     * <p>A row whose digest differs was derived from different input, so it is reported as none: the
     * caller re-derives rather than showing an outdated result as the current one (the read-side of
     * Android's {@code DerivedService}).
     */
    public Optional<Artifact> findFresh(String kind, String scopeId, String expectedInputDigest) {
        Optional<Artifact> stored = find(kind, scopeId);
        if (stored.isEmpty() || !expectedInputDigest.equals(stored.get().inputDigest)) {
            return Optional.empty();
        }
        return stored;
    }

    /** Every artifact bound to this scope, in kind then id order. */
    public List<Artifact> listForScope(String scopeId) {
        return jdbc.queryList(SELECT_BY_SCOPE, s -> s.setString(1, scopeId), ArtifactStore::read);
    }

    /** Removes every artifact of this kind bound to this scope; returns the number of rows removed. */
    public int deleteForScope(String kind, String scopeId) {
        return jdbc.update(DELETE_BY_SCOPE, s -> {
            s.setString(1, kind); s.setString(2, scopeId);
        });
    }

    private static Artifact read(ResultSet rows) throws SQLException {
        return new Artifact(rows.getString(ID), rows.getString(KIND), rows.getString(SCOPE_ID),
                PromptPurpose.fromPrefix(rows.getString(PROMPT_PURPOSE)),
                rows.getString(PROMPT_POLICY_VERSION), rows.getString(PAYLOAD),
                rows.getString(BASED_ON_REVISION_ID), rows.getString(INPUT_DIGEST),
                rows.getLong(CREATED_AT), rows.getLong(UPDATED_AT));
    }

    /**
     * One {@code ai_artifacts} row. Unlike a stable revision it is a cache of a derivation, so its
     * payload may be replaced — but only while the baseline it was derived from still holds.
     */
    public static final class Artifact {

        public final String id, kind, scopeId;
        public final PromptPurpose purpose;
        public final String promptPolicyVersion, payload, inputDigest;
        /** Nullable: a cleared baseline ({@code ON DELETE SET NULL}) means the baseline is gone. */
        public final String basedOnRevisionId;
        public final long createdAt, updatedAt;

        Artifact(String id, String kind, String scopeId, PromptPurpose purpose,
                String promptPolicyVersion, String payload, String basedOnRevisionId, String inputDigest,
                long createdAt, long updatedAt) {
            this.id = Guard.id(id, "artifact id");
            this.kind = Guard.nonBlank(kind, "artifact kind");
            this.scopeId = Guard.nonBlank(scopeId, "artifact scopeId");
            this.purpose = Guard.object(purpose, "artifact purpose");
            this.promptPolicyVersion = Guard.nonBlank(promptPolicyVersion, "artifact policyVersion");
            this.payload = Guard.nonBlank(payload, "artifact payload");
            this.basedOnRevisionId = Guard.idOrNull(basedOnRevisionId, "artifact basedOnRevisionId");
            this.inputDigest = Guard.nonBlank(inputDigest, "artifact inputDigest");
            this.createdAt = Guard.epochMillis(createdAt, "artifact createdAt");
            this.updatedAt = Guard.epochMillis(updatedAt, "artifact updatedAt");
        }

        /** Builds a candidate to write: a fresh identity stamped at {@code now}. */
        public static Artifact create(String kind, String scopeId, PromptPurpose purpose,
                String promptPolicyVersion, String payload, String basedOnRevisionId,
                String inputDigest, long now) {
            return new Artifact(Guard.newId(), kind, scopeId, purpose, promptPolicyVersion, payload,
                    basedOnRevisionId, inputDigest, now, now);
        }
    }
}
