package com.treelearn.app.wiring;

import android.database.sqlite.SQLiteDatabase;
import com.treelearn.ai.gateway.AiGateway;
import com.treelearn.ai.gateway.ProviderEndpoint;
import com.treelearn.app.ui.KeySnapshot;
import com.treelearn.app.ui.ProviderAdminPresenter;
import com.treelearn.data.db.DatabaseTransactionRunner;
import com.treelearn.data.db.TreeLearnDatabaseHelper;
import com.treelearn.data.db.dao.ModelDao;
import com.treelearn.data.db.dao.ProviderDao;
import com.treelearn.data.secret.KeystoreSecretStore;
import com.treelearn.data.secret.SecretStoreException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public final class SqliteProviderAdminStore
implements ProviderAdminPresenter.Store {
    private final TreeLearnDatabaseHelper helper;
    private final DatabaseTransactionRunner transactions;
    private final KeystoreSecretStore secrets;
    private final AiGateway gateway;
    private final Clock clock;

    public SqliteProviderAdminStore(TreeLearnDatabaseHelper helper, DatabaseTransactionRunner transactions, KeystoreSecretStore secrets, AiGateway gateway, Clock clock) {
        if (helper == null || transactions == null || secrets == null || gateway == null || clock == null) {
            throw new IllegalArgumentException("the provider admin store needs all five parts");
        }
        this.helper = helper;
        this.transactions = transactions;
        this.secrets = secrets;
        this.gateway = gateway;
        this.clock = clock;
    }

    @Override
    public List<ProviderAdminPresenter.Row> list() {
        ArrayList<ProviderAdminPresenter.Row> rows = new ArrayList<ProviderAdminPresenter.Row>();
        for (ProviderDao.Row row : ProviderDao.listByCreatedAt((SQLiteDatabase)this.helper.getWritableDatabase())) {
            rows.add(new ProviderAdminPresenter.Row(row.id, row.name, row.baseUrl, row.enabled, ModelDao.findByProvider((SQLiteDatabase)this.helper.getWritableDatabase(), (String)row.id).size(), this.secrets.hasSecret(row.id)));
        }
        return rows;
    }

    @Override
    public boolean nameTaken(String name, String ignoreId) {
        ProviderDao.Row existing = ProviderDao.findByName((SQLiteDatabase)this.helper.getWritableDatabase(), (String)name);
        return existing != null && !existing.id.equals(ignoreId);
    }

    @Override
    public ProviderAdminPresenter.Row create(String name, String baseUrl) {
        ProviderDao.Row row = new ProviderDao.Row(UUID.randomUUID().toString(), name, baseUrl, true, false, this.clock.millis(), this.clock.millis());
        this.transactions.run(this.helper.getWritableDatabase(), database -> {
            ProviderDao.insert((SQLiteDatabase)database, (ProviderDao.Row)row);
            return null;
        });
        return new ProviderAdminPresenter.Row(row.id, row.name, row.baseUrl, row.enabled, 0, false);
    }

    @Override
    public void rename(String id2, String name, String baseUrl) {
        this.transactions.run(this.helper.getWritableDatabase(), database -> {
            ProviderDao.updateConfig((SQLiteDatabase)database, (String)id2, (String)name, (String)baseUrl);
            return null;
        });
    }

    @Override
    public void setEnabled(String id2, boolean enabled) {
        this.transactions.run(this.helper.getWritableDatabase(), database -> {
            ProviderDao.setEnabled((SQLiteDatabase)database, (String)id2, (boolean)enabled);
            return null;
        });
    }

    @Override
    public void delete(String id2) {
        this.transactions.run(this.helper.getWritableDatabase(), database -> {
            ProviderDao.delete((SQLiteDatabase)database, (String)id2);
            return null;
        });
        // Only after the row is gone: the secret delete never throws (KeystoreSecretStore
        // contract), so a leftover alias is the only failure mode and this removes it.
        this.secrets.delete(id2);
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    @Override
    public KeySnapshot readKey(String providerId) {
        if (!this.secrets.hasSecret(providerId)) {
            return KeySnapshot.missing();
        }
        char[] secret = null;
        try {
            secret = this.secrets.read(providerId);
            KeySnapshot keySnapshot = KeySnapshot.ofStoredKey(secret);
            return keySnapshot;
        }
        catch (SecretStoreException unreadable) {
            KeySnapshot keySnapshot = KeySnapshot.unreadable();
            return keySnapshot;
        }
        finally {
            if (secret != null) {
                Arrays.fill(secret, '\u0000');
            }
        }
    }

    @Override
    public void saveKey(String providerId, char[] secret) throws ProviderAdminPresenter.KeyRefused {
        try {
            this.secrets.save(providerId, secret);
        }
        catch (SecretStoreException refused) {
            throw new ProviderAdminPresenter.KeyRefused("the keystore refused the write: " + ((Object)((Object)refused)).getClass().getSimpleName());
        }
    }

    @Override
    public void deleteKey(String providerId) {
        this.secrets.delete(providerId);
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    @Override
    public ProviderAdminPresenter.Fetch fetch(String providerId) {
        ProviderDao.Row row = ProviderDao.findById((SQLiteDatabase)this.helper.getWritableDatabase(), (String)providerId);
        if (row == null) {
            return new ProviderAdminPresenter.Fetch(true, 0);
        }
        char[] secret = null;
        try {
            secret = this.secrets.read(providerId);
            AiGateway.Models models = this.gateway.listModels(new String(secret), ProviderEndpoint.of((String)row.baseUrl, (String)"list"));
            if (!models.ok()) {
                ProviderAdminPresenter.Fetch fetch = new ProviderAdminPresenter.Fetch(true, 0);
                return fetch;
            }
            int added = (Integer)this.transactions.run(this.helper.getWritableDatabase(), database -> ModelDao.mergeRemote((SQLiteDatabase)database, (String)providerId, (List)models.ids, () -> UUID.randomUUID().toString()));
            ProviderAdminPresenter.Fetch fetch = new ProviderAdminPresenter.Fetch(false, added);
            return fetch;
        }
        catch (SecretStoreException unreadable) {
            ProviderAdminPresenter.Fetch fetch = new ProviderAdminPresenter.Fetch(true, 0);
            return fetch;
        }
        finally {
            if (secret != null) {
                Arrays.fill(secret, '\u0000');
            }
        }
    }
}

