package com.treelearn.web.storage;

import static com.treelearn.web.storage.DesktopSchema.Models.CREATED_AT;
import static com.treelearn.web.storage.DesktopSchema.Models.DISPLAY_NAME;
import static com.treelearn.web.storage.DesktopSchema.Models.ID;
import static com.treelearn.web.storage.DesktopSchema.Models.MODEL_ID;
import static com.treelearn.web.storage.DesktopSchema.Models.PROVIDER_ID;
import static com.treelearn.web.storage.DesktopSchema.Models.TABLE;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Row access for {@code models} (task T-2026-10-10-012).
 *
 * <p>Semantic reference — the Android {@code com.treelearn.data.db.dao.ModelDao} it mirrors:
 * <ul>
 *   <li>{@code ModelDao.findByProvider} → {@link #listForProvider(String)}: the same {@code created_at
 *       ASC, id ASC} order.</li>
 *   <li>{@code ModelDao.mergeRemote} (with {@code ModelDao.newModelIds}) → {@link #upsertMany}: the
 *       same T-086 / F8 口径 — the plan never reduces the store, it only adds the remote ids that are
 *       not already stored, compared case-exactly, with duplicates inside the remote list itself
 *       skipped. The whole batch is one transaction, so a failed insert rolls every row of the fetch
 *       back together and the local list stays exactly as it was.</li>
 *   <li>{@code ModelDao.delete} → {@link #delete(String)}: the same {@code WHERE id = ?} on the row's
 *       own identity and the same "exactly its own row, or refuse" rule.</li>
 * </ul>
 *
 * <p>{@code model_id} is the provider-side wire id. Its uniqueness within a provider is the schema's
 * {@code uq_models_provider_model} index under BINARY collation — case-exact — so {@code Model-X} and
 * {@code model-x} coexist and this class adds no second, case-insensitive dedup of its own; the
 * case-exact Java {@link HashSet} only plans which ids to insert, exactly as Android does.
 *
 * <p>Not provided, on purpose:
 * <ul>
 *   <li>{@code deleteForProvider}: a model row never outlives its provider (ON DELETE CASCADE), so the
 *       schema already removes every model of a deleted provider; a second, hand-written scope delete
 *       could only diverge from that cascade.</li>
 *   <li>{@code countsByProvider}: a per-provider model count is derivable from {@link #listAll()}
 *       (group and count in the caller) and has no independent product path, so this store carries no
 *       second way to express the same number.</li>
 * </ul>
 */
public final class ModelStore {

    private static final String INSERT = "INSERT INTO " + TABLE + "(" + ID + ", " + PROVIDER_ID
            + ", " + MODEL_ID + ", " + DISPLAY_NAME + ", " + CREATED_AT + ") VALUES (?, ?, ?, ?, ?)";
    private static final String SELECT_BY_PROVIDER = "SELECT * FROM " + TABLE + " WHERE "
            + PROVIDER_ID + " = ? ORDER BY " + CREATED_AT + " ASC, " + ID + " ASC";
    private static final String SELECT_ALL = "SELECT * FROM " + TABLE + " ORDER BY " + PROVIDER_ID
            + " ASC, " + CREATED_AT + " ASC, " + ID + " ASC";
    private static final String SELECT_MODEL_IDS = "SELECT " + MODEL_ID + " FROM " + TABLE
            + " WHERE " + PROVIDER_ID + " = ?";
    private static final String DELETE = "DELETE FROM " + TABLE + " WHERE " + ID + " = ?";

    private final JdbcSupport jdbc;

    public ModelStore(JdbcSupport jdbc) {
        this.jdbc = jdbc;
    }

    /** The provider's models in creation order; empty, never null, for an unknown id. */
    public List<Model> listForProvider(String providerId) {
        return jdbc.queryList(SELECT_BY_PROVIDER, s -> s.setString(1, providerId), ModelStore::read);
    }

    /** Every stored model, grouped by provider then in creation order — the list a settings read needs. */
    public List<Model> listAll() {
        return jdbc.queryList(SELECT_ALL, JdbcSupport.NO_PARAMS, ModelStore::read);
    }

    /**
     * Applies one fetched model list: only the ids not already stored are inserted, each row naming the
     * wire id as its display name. Returns the number of rows added. Case-exact throughout.
     */
    public int upsertMany(String providerId, List<String> remoteModelIds, Supplier<String> idSupplier,
            long now) {
        return jdbc.runInTransaction(connection -> {
            Set<String> unseen = new HashSet<>(jdbc.queryList(connection, SELECT_MODEL_IDS,
                    s -> s.setString(1, providerId), rows -> rows.getString(1)));
            int added = 0;
            for (String modelId : remoteModelIds) {
                if (!unseen.add(modelId)) {
                    continue;
                }
                jdbc.update(connection, INSERT, s -> {
                    s.setString(1, idSupplier.get()); s.setString(2, providerId);
                    s.setString(3, modelId); s.setString(4, modelId); s.setLong(5, now);
                });
                added++;
            }
            return added;
        });
    }

    /**
     * Deletes the one model row named by its own identity {@code id} — the row PK, never the shared
     * wire {@code model_id}, so a wire id two providers both use cannot make one delete touch two
     * rows. Mirrors {@code ModelDao.delete}: a delete must touch exactly its own row, and a missing id
     * is refused rather than silently succeeding. This is the store backing the SPEC-2026-09-23
     * provider edit screen's 已选模型列表（逐项可删）.
     */
    public void delete(String id) {
        JdbcSupport.requireSingleRow(jdbc.update(DELETE, s -> s.setString(1, id)),
                "deleting model " + id);
    }

    private static Model read(ResultSet rows) throws SQLException {
        return new Model(rows.getString(ID), rows.getString(PROVIDER_ID), rows.getString(MODEL_ID),
                rows.getString(DISPLAY_NAME), rows.getLong(CREATED_AT));
    }

    /** One {@code models} row: {@code modelId} is the provider-side wire id. */
    public static final class Model {
        public final String id, providerId, modelId, displayName;
        public final long createdAt;

        Model(String id, String providerId, String modelId, String displayName, long createdAt) {
            this.id = id; this.providerId = providerId; this.modelId = modelId;
            this.displayName = displayName; this.createdAt = createdAt;
        }
    }
}
