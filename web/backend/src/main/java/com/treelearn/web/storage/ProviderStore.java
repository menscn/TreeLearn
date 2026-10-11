package com.treelearn.web.storage;

import static com.treelearn.web.storage.DesktopSchema.Providers.BASE_URL;
import static com.treelearn.web.storage.DesktopSchema.Providers.CREATED_AT;
import static com.treelearn.web.storage.DesktopSchema.Providers.ENABLED;
import static com.treelearn.web.storage.DesktopSchema.Providers.ID;
import static com.treelearn.web.storage.DesktopSchema.Providers.IS_BUILTIN;
import static com.treelearn.web.storage.DesktopSchema.Providers.NAME;
import static com.treelearn.web.storage.DesktopSchema.Providers.TABLE;
import static com.treelearn.web.storage.DesktopSchema.Providers.UPDATED_AT;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/**
 * Row access for {@code providers} (task T-2026-10-10-012). Configuration only: the table carries no
 * key column, and the desktop credential port (T-013) owns secrets, so nothing here reads or writes a
 * key.
 *
 * <p>Semantic reference — the Android {@code com.treelearn.data.db.dao.ProviderDao} it mirrors:
 * <ul>
 *   <li>{@code ProviderDao.listByCreatedAt} → {@link #list()}: the same {@code created_at ASC, id ASC}
 *       order, so the provider list cannot sort differently per engine.</li>
 *   <li>{@code ProviderDao.findById} / {@code ProviderDao.findByName} → {@link #get(String)} /
 *       {@link #findByName(String)}.</li>
 *   <li>{@code ProviderDao.insert} → {@link #insert(Provider)}: a blank id/name/baseUrl is refused
 *       with an {@link IllegalArgumentException} before the database sees it.</li>
 *   <li>{@code ProviderDao.updateConfig} + {@code ProviderDao.setEnabled} → {@link #update}: the
 *       desktop caller always holds the full provider, so name, base URL and the enabled bit land in
 *       one write together with {@code updated_at}.</li>
 *   <li>{@code ProviderDao.delete} → {@link #delete(String)}: the provider's model rows go with it
 *       through the schema's ON DELETE CASCADE. The "last provider" business warning belongs to the
 *       calling use case, not here.</li>
 * </ul>
 *
 * <p>Name uniqueness is the store's {@code UNIQUE} index on exact text; the [DUP] trim-then-compare
 * rule belongs to the form layer, so a duplicate insert reaches the caller as a readable
 * {@link StorageException} instead of being silently absorbed here.
 */
public final class ProviderStore {

    private static final String INSERT = "INSERT INTO " + TABLE + "(" + ID + ", " + NAME + ", "
            + BASE_URL + ", " + ENABLED + ", " + IS_BUILTIN + ", " + CREATED_AT + ", " + UPDATED_AT
            + ") VALUES (?, ?, ?, ?, ?, ?, ?)";
    private static final String SELECT_ONE = "SELECT * FROM " + TABLE + " WHERE " + ID + " = ?";
    private static final String SELECT_BY_NAME = "SELECT * FROM " + TABLE + " WHERE " + NAME
            + " = ?";
    private static final String SELECT_ALL = "SELECT * FROM " + TABLE + " ORDER BY " + CREATED_AT
            + " ASC, " + ID + " ASC";
    private static final String UPDATE = "UPDATE " + TABLE + " SET " + NAME + " = ?, " + BASE_URL
            + " = ?, " + ENABLED + " = ?, " + UPDATED_AT + " = ? WHERE " + ID + " = ?";
    private static final String DELETE = "DELETE FROM " + TABLE + " WHERE " + ID + " = ?";

    private final JdbcSupport jdbc;

    public ProviderStore(JdbcSupport jdbc) {
        this.jdbc = jdbc;
    }

    /** Every provider in creation order, id as the tie-breaker for equal timestamps. */
    public List<Provider> list() {
        return jdbc.queryList(SELECT_ALL, JdbcSupport.NO_PARAMS, ProviderStore::read);
    }

    public Optional<Provider> get(String id) {
        return jdbc.queryOne(SELECT_ONE, s -> s.setString(1, id), ProviderStore::read);
    }

    /** @return the provider whose name is exactly this text, or empty */
    public Optional<Provider> findByName(String name) {
        return jdbc.queryOne(SELECT_BY_NAME, s -> s.setString(1, name), ProviderStore::read);
    }

    /** Stores a provider; a second row with the same name is refused by the UNIQUE index. */
    public void insert(Provider provider) {
        requireRow(provider);
        jdbc.update(INSERT, s -> {
            s.setString(1, provider.id); s.setString(2, provider.name);
            s.setString(3, provider.baseUrl); s.setInt(4, provider.enabled ? 1 : 0);
            s.setInt(5, provider.builtin ? 1 : 0); s.setLong(6, provider.createdAt);
            s.setLong(7, provider.updatedAt);
        });
    }

    /**
     * Inserts a new, non-builtin provider row with {@code created_at == updated_at == now}; the API layer
     * (contract §6.6 {@code POST /api/providers}) uses this so it never has to reach the row constructor.
     */
    public void insertNew(String id, String name, String baseUrl, boolean enabled, long now) {
        insert(new Provider(id, name, baseUrl, enabled, false, now, now));
    }

    /** The single edit write: name, base URL and the enabled bit together with {@code updated_at}. */
    public void update(String id, String name, String baseUrl, boolean enabled, long now) {
        JdbcSupport.requireSingleRow(jdbc.update(UPDATE, s -> {
            s.setString(1, name); s.setString(2, baseUrl); s.setInt(3, enabled ? 1 : 0);
            s.setLong(4, now); s.setString(5, id);
        }), "updating provider " + id);
    }

    /** Deletes the provider; its model rows cascade with it (ON DELETE CASCADE). */
    public void delete(String id) {
        JdbcSupport.requireSingleRow(jdbc.update(DELETE, s -> s.setString(1, id)),
                "deleting provider " + id);
    }

    private static Provider read(ResultSet rows) throws SQLException {
        return new Provider(rows.getString(ID), rows.getString(NAME), rows.getString(BASE_URL),
                JdbcSupport.flag(rows, ENABLED), JdbcSupport.flag(rows, IS_BUILTIN),
                rows.getLong(CREATED_AT), rows.getLong(UPDATED_AT));
    }

    private static void requireRow(Provider provider) {
        if (provider == null || isBlank(provider.id) || isBlank(provider.name)
                || isBlank(provider.baseUrl)) {
            throw new IllegalArgumentException("a provider row needs non-blank id, name, baseUrl");
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    /** One {@code providers} row. No field is a secret: the table has no key column. */
    public static final class Provider {
        public final String id, name, baseUrl;
        public final boolean enabled, builtin;
        public final long createdAt, updatedAt;

        Provider(String id, String name, String baseUrl, boolean enabled, boolean builtin,
                long createdAt, long updatedAt) {
            this.id = id; this.name = name; this.baseUrl = baseUrl;
            this.enabled = enabled; this.builtin = builtin;
            this.createdAt = createdAt; this.updatedAt = updatedAt;
        }
    }
}
