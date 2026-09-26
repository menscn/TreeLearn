/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  android.content.Context
 *  com.treelearn.core.domain.GenerationModel
 *  com.treelearn.data.secret.KeystoreSecretStore
 *  com.treelearn.data.secret.SecretStoreException
 *  com.treelearn.data.settings.SettingsRepository
 */
package com.treelearn.app.wiring;

import android.content.Context;
import com.treelearn.app.ui.KeySnapshot;
import com.treelearn.app.ui.KeyVault;
import com.treelearn.app.ui.SettingsStore;
import com.treelearn.core.domain.GenerationModel;
import com.treelearn.data.secret.KeystoreSecretStore;
import com.treelearn.data.secret.SecretStoreException;
import com.treelearn.data.settings.SettingsRepository;
import java.util.Arrays;

public final class AndroidSettingsStore
implements SettingsStore,
KeyVault {
    private final SettingsRepository preferences;
    private final KeystoreSecretStore secrets;

    public AndroidSettingsStore(Context context) {
        this.preferences = new SettingsRepository(context);
        this.secrets = new KeystoreSecretStore(context);
    }

    @Override
    public GenerationModel model() {
        return this.preferences.model();
    }

    @Override
    public void setModel(GenerationModel model) {
        this.preferences.setModel(model);
    }

    @Override
    public boolean thinkingEnabled() {
        return this.preferences.thinkingEnabled();
    }

    @Override
    public void setThinkingEnabled(boolean enabled) {
        this.preferences.setThinkingEnabled(enabled);
    }

    @Override
    public boolean isConfigured() {
        return this.secrets.hasSecret();
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    @Override
    public KeySnapshot readKey() {
        if (!this.secrets.hasSecret()) {
            return KeySnapshot.missing();
        }
        char[] secret = null;
        try {
            secret = this.secrets.read();
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
    public void saveKey(char[] secret) throws SettingsStore.SecretStoreRefused {
        try {
            this.secrets.save(secret);
        }
        catch (SecretStoreException refused) {
            throw new SettingsStore.SecretStoreRefused("the keystore refused the write: " + ((Object)((Object)refused)).getClass().getSimpleName());
        }
    }

    @Override
    public void deleteKey() {
        this.secrets.delete();
    }
}

