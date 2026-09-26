/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  android.database.sqlite.SQLiteDatabase
 *  com.treelearn.data.db.TreeLearnDatabaseHelper
 *  com.treelearn.data.db.dao.ProviderDao
 *  com.treelearn.data.db.dao.ProviderDao$Row
 */
package com.treelearn.app.wiring;

import android.database.sqlite.SQLiteDatabase;
import com.treelearn.app.generation.QuickModelGateway;
import com.treelearn.app.ui.ModelSettingsPresenter;
import com.treelearn.app.wiring.SqliteModelSettingsStore;
import com.treelearn.data.db.TreeLearnDatabaseHelper;
import com.treelearn.data.db.dao.ProviderDao;

public final class SqliteQuickModelRoutes
implements QuickModelGateway.Routes {
    private final TreeLearnDatabaseHelper helper;
    private final SqliteModelSettingsStore modelSettings;

    public SqliteQuickModelRoutes(TreeLearnDatabaseHelper helper, SqliteModelSettingsStore modelSettings) {
        if (helper == null || modelSettings == null) {
            throw new IllegalArgumentException("the quick-model routes need both parts");
        }
        this.helper = helper;
        this.modelSettings = modelSettings;
    }

    @Override
    public QuickModelGateway.Route fastAssignment() {
        ModelSettingsPresenter.Assignment assignment = this.modelSettings.read(ModelSettingsPresenter.Role.FAST);
        if (assignment.providerId == null || assignment.modelId == null) {
            return null;
        }
        ProviderDao.Row provider = ProviderDao.findById((SQLiteDatabase)this.helper.getWritableDatabase(), (String)assignment.providerId);
        if (provider == null) {
            return null;
        }
        return new QuickModelGateway.Route(provider.id, provider.baseUrl, assignment.modelId);
    }
}

