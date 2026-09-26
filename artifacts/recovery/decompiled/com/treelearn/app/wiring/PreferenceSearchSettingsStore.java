/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.treelearn.data.secret.KeystoreSecretStore
 *  com.treelearn.data.secret.SecretStoreException
 *  com.treelearn.data.settings.SettingsRepository
 */
package com.treelearn.app.wiring;

import com.treelearn.app.ui.KeySnapshot;
import com.treelearn.app.ui.SearchSettingsPresenter;
import com.treelearn.data.secret.KeystoreSecretStore;
import com.treelearn.data.secret.SecretStoreException;
import com.treelearn.data.settings.SettingsRepository;
import java.util.Arrays;
import java.util.Locale;

public final class PreferenceSearchSettingsStore
implements SearchSettingsPresenter.Store {
    private static final String ALIAS_PREFIX = "search_";
    private final SettingsRepository preferences;
    private final KeystoreSecretStore secrets;

    public PreferenceSearchSettingsStore(SettingsRepository preferences, KeystoreSecretStore secrets) {
        if (preferences == null || secrets == null) {
            throw new IllegalArgumentException("the search settings store needs both parts");
        }
        this.preferences = preferences;
        this.secrets = secrets;
    }

    @Override
    public boolean searchEnabled() {
        return this.preferences.searchEnabled();
    }

    @Override
    public void setSearchEnabled(boolean enabled) {
        this.preferences.setSearchEnabled(enabled);
    }

    @Override
    public SearchSettingsPresenter.Engine engine() {
        return (SearchSettingsPresenter.Engine)PreferenceSearchSettingsStore.parse((Enum[])SearchSettingsPresenter.Engine.values(), (String)this.preferences.preference("search_engine", ""), (Enum)SearchSettingsPresenter.Engine.ZHIPU);
    }

    @Override
    public void setEngine(SearchSettingsPresenter.Engine engine) {
        this.preferences.setPreference("search_engine", engine.name());
    }

    @Override
    public SearchSettingsPresenter.Tier tier() {
        return (SearchSettingsPresenter.Tier)PreferenceSearchSettingsStore.parse((Enum[])SearchSettingsPresenter.Tier.values(), (String)this.preferences.preference("search_tier", ""), (Enum)SearchSettingsPresenter.Tier.TEN);
    }

    @Override
    public void setTier(SearchSettingsPresenter.Tier tier) {
        this.preferences.setPreference("search_tier", tier.name());
    }

    @Override
    public boolean hasKey(SearchSettingsPresenter.Engine engine) {
        return this.secrets.hasSecret(PreferenceSearchSettingsStore.alias(engine));
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    @Override
    public KeySnapshot readKey(SearchSettingsPresenter.Engine engine) {
        String alias = PreferenceSearchSettingsStore.alias(engine);
        if (!this.secrets.hasSecret(alias)) {
            return KeySnapshot.missing();
        }
        char[] secret = null;
        try {
            secret = this.secrets.read(alias);
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
    public void saveKey(SearchSettingsPresenter.Engine engine, char[] secret) throws SearchSettingsPresenter.KeyRefused {
        try {
            this.secrets.save(PreferenceSearchSettingsStore.alias(engine), secret);
        }
        catch (SecretStoreException refused) {
            throw new SearchSettingsPresenter.KeyRefused("the keystore refused the write: " + ((Object)((Object)refused)).getClass().getSimpleName());
        }
    }

    @Override
    public void deleteKey(SearchSettingsPresenter.Engine engine) {
        this.secrets.delete(PreferenceSearchSettingsStore.alias(engine));
    }

    public static String aliasOf(SearchSettingsPresenter.Engine engine) {
        return ALIAS_PREFIX + engine.name().toLowerCase(Locale.ROOT);
    }

    private static String alias(SearchSettingsPresenter.Engine engine) {
        return PreferenceSearchSettingsStore.aliasOf(engine);
    }

    private static <E extends Enum<E>> E parse(E[] values, String stored, E fallback) {
        for (E value : values) {
            if (!((Enum)value).name().equals(stored)) continue;
            return value;
        }
        return fallback;
    }
}

