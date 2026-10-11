package com.treelearn.web.storage;

import static com.treelearn.web.storage.DesktopSchema.ContextSnapshots.BRANCH_ID;
import static com.treelearn.web.storage.DesktopSchema.ContextSnapshots.CREATED_AT;
import static com.treelearn.web.storage.DesktopSchema.ContextSnapshots.DIGEST;
import static com.treelearn.web.storage.DesktopSchema.ContextSnapshots.ID;
import static com.treelearn.web.storage.DesktopSchema.ContextSnapshots.PAYLOAD;
import static com.treelearn.web.storage.DesktopSchema.ContextSnapshots.SCHEMA_VERSION;
import static com.treelearn.web.storage.DesktopSchema.ContextSnapshots.TABLE;

import com.treelearn.core.domain.ContextSnapshot;
import com.treelearn.core.domain.DomainResult;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/**
 * Row access for {@code context_snapshots} (task T-2026-10-10-011).
 *
 * <p>Semantic reference — the Android {@code com.treelearn.data.db.dao.ContextSnapshotDao} it mirrors:
 * {@code ContextSnapshotDao.insert} → {@link #insert(String, List, long)} and
 * {@code ContextSnapshotDao.findByBranchId} → {@link #findForBranch(String)}. One branch has at most
 * one birth snapshot ({@code uq_context_snapshots_branch}), and a snapshot disappears only together
 * with its branch, never on its own — both enforced by the T-009 triggers, not by this class.
 *
 * <p><strong>The payload is opaque to this layer.</strong> {@link ContextSnapshot#create} is the only
 * producer of the canonical JSON and its digest, and {@link ContextSnapshot#fromStored} is the only
 * reader; this store moves the string exactly as it was handed and never parses, repairs or
 * re-serialises it. A row that cannot be proven intact — an unreadable schema version, a payload that
 * is not this schema's canonical shape, or one that does not hash to its stored digest — stops the read
 * with a {@link StorageException} rather than being partially believed.
 */
public final class SnapshotStore {

    private static final String INSERT = "INSERT INTO " + TABLE + "(" + ID + ", " + BRANCH_ID + ", "
            + SCHEMA_VERSION + ", " + DIGEST + ", " + PAYLOAD + ", " + CREATED_AT
            + ") VALUES (?, ?, ?, ?, ?, ?)";
    private static final String SELECT_FOR_BRANCH = "SELECT * FROM " + TABLE + " WHERE " + BRANCH_ID
            + " = ?";

    private final JdbcSupport jdbc;

    public SnapshotStore(JdbcSupport jdbc) {
        this.jdbc = jdbc;
    }

    /** Writes the birth snapshot of a branch; a second one for the same branch fails the unique index. */
    public ContextSnapshot insert(String branchId, List<ContextSnapshot.Source> sources, long now) {
        ContextSnapshot snapshot = ContextSnapshot.create(branchId, sources, now);
        jdbc.update(INSERT, binder(snapshot));
        return snapshot;
    }

    /**
     * Transaction-scoped insert of an already-built snapshot: the fork entry point mints the snapshot id
     * before the branch exists (the branch's provenance pins it), so this store must write the exact
     * instance it is handed — the pointer column and the provenance can then never disagree.
     */
    public void insert(Connection connection, ContextSnapshot snapshot) {
        jdbc.update(connection, INSERT, binder(snapshot));
    }

    private static JdbcSupport.Binder binder(ContextSnapshot snapshot) {
        return s -> {
            s.setString(1, snapshot.id); s.setString(2, snapshot.branchId);
            s.setInt(3, snapshot.schemaVersion); s.setString(4, snapshot.digest);
            s.setString(5, snapshot.payload); s.setLong(6, snapshot.createdAt);
        };
    }

    /** The branch's birth snapshot, rehydrated and digest-verified, or empty when it has none. */
    public Optional<ContextSnapshot> findForBranch(String branchId) {
        return jdbc.queryOne(SELECT_FOR_BRANCH, s -> s.setString(1, branchId), SnapshotStore::rehydrate);
    }

    /** The one reader of a snapshot row: it re-runs the codec and refuses a row it cannot trust. */
    private static ContextSnapshot rehydrate(ResultSet rows) throws SQLException {
        String id = rows.getString(ID);
        String branchId = rows.getString(BRANCH_ID);
        DomainResult<ContextSnapshot> rehydrated = ContextSnapshot.fromStored(id, branchId,
                rows.getInt(SCHEMA_VERSION), rows.getString(DIGEST), rows.getString(PAYLOAD),
                rows.getLong(CREATED_AT));
        if (rehydrated.isFailure()) {
            throw new StorageException("the birth snapshot " + id + " of branch " + branchId
                    + " cannot be read faithfully: " + rehydrated.error());
        }
        return rehydrated.value();
    }
}
