package com.treelearn.app.wiring;

import android.database.sqlite.SQLiteDatabase;
import com.treelearn.app.ui.ModelSettingsPresenter;
import com.treelearn.data.db.TreeLearnDatabaseHelper;
import com.treelearn.data.db.dao.ModelDao;
import com.treelearn.data.db.dao.ProviderDao;
import com.treelearn.data.settings.SettingsRepository;
import java.util.ArrayList;
import java.util.List;

public final class SqliteModelSettingsStore
implements ModelSettingsPresenter.Store {
    private static final String SEPARATOR = "\u001f";
    private final TreeLearnDatabaseHelper helper;
    private final SettingsRepository preferences;

    public SqliteModelSettingsStore(TreeLearnDatabaseHelper helper, SettingsRepository preferences) {
        if (helper == null || preferences == null) {
            throw new IllegalArgumentException("the model settings store needs both parts");
        }
        this.helper = helper;
        this.preferences = preferences;
    }

    @Override
    public List<ModelSettingsPresenter.Option> options() {
        SQLiteDatabase database = this.helper.getWritableDatabase();
        ArrayList<ModelSettingsPresenter.Option> options = new ArrayList<ModelSettingsPresenter.Option>();
        for (ProviderDao.Row provider : ProviderDao.listByCreatedAt((SQLiteDatabase)database)) {
            if (!provider.enabled) continue;
            for (ModelDao.Row model : ModelDao.findByProvider((SQLiteDatabase)database, (String)provider.id)) {
                options.add(new ModelSettingsPresenter.Option(provider.id, provider.name, model.modelId));
            }
        }
        return options;
    }

    @Override
    public ModelSettingsPresenter.Assignment read(ModelSettingsPresenter.Role role) {
        String stored = this.preferences.preference(SqliteModelSettingsStore.key(role), "");
        int split = stored.indexOf(SEPARATOR);
        String providerId = split > 0 ? stored.substring(0, split) : null;
        String modelId = split > 0 ? stored.substring(split + SEPARATOR.length()) : null;
        SQLiteDatabase database = this.helper.getWritableDatabase();
        return ModelSettingsPresenter.resolve(providerId, modelId, this.options(), this.builtinDefault(database), this.earliest(database));
    }

    @Override
    public void save(ModelSettingsPresenter.Role role, String providerId, String modelId) {
        this.preferences.setPreference(SqliteModelSettingsStore.key(role), providerId + SEPARATOR + modelId);
    }

    @Override
    public ModelSettingsPresenter.Budget readBudget() {
        String stored = this.preferences.preference("thinking_budget", ModelSettingsPresenter.Budget.AUTO.name());
        for (ModelSettingsPresenter.Budget budget : ModelSettingsPresenter.Budget.values()) {
            if (!budget.name().equals(stored)) continue;
            return budget;
        }
        return ModelSettingsPresenter.Budget.AUTO;
    }

    @Override
    public void saveBudget(ModelSettingsPresenter.Budget budget) {
        this.preferences.setPreference("thinking_budget", budget.name());
    }

    @Override
    public boolean readSuggestions() {
        return this.preferences.answerSuggestions();
    }

    @Override
    public void saveSuggestions(boolean enabled) {
        this.preferences.setAnswerSuggestions(enabled);
    }

    private ModelSettingsPresenter.Option builtinDefault(SQLiteDatabase database) {
        ProviderDao.Row builtin = ProviderDao.findById((SQLiteDatabase)database, (String)"00000000-0000-0000-0000-000000000001");
        if (builtin == null || !builtin.enabled) {
            return null;
        }
        for (ModelDao.Row model : ModelDao.findByProvider((SQLiteDatabase)database, (String)builtin.id)) {
            if (!"00000000-0000-0000-0000-000000000002".equals(model.id)) continue;
            return new ModelSettingsPresenter.Option(builtin.id, builtin.name, model.modelId);
        }
        return null;
    }

    private ModelSettingsPresenter.Option earliest(SQLiteDatabase database) {
        for (ProviderDao.Row provider : ProviderDao.listByCreatedAt((SQLiteDatabase)database)) {
            List models;
            if (!provider.enabled || (models = ModelDao.findByProvider((SQLiteDatabase)database, (String)provider.id)).isEmpty()) continue;
            return new ModelSettingsPresenter.Option(provider.id, provider.name, ((ModelDao.Row)models.get((int)0)).modelId);
        }
        return null;
    }

    private static String key(ModelSettingsPresenter.Role role) {
        switch (role) {
            case FAST: {
                return "role_fast_model";
            }
            case TRANSLATION: {
                return "role_translation_model";
            }
        }
        return "role_chat_model";
    }
}

