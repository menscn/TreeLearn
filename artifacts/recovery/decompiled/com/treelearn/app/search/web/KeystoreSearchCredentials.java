/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.treelearn.data.secret.KeystoreSecretStore
 *  com.treelearn.data.secret.SecretStoreException
 */
package com.treelearn.app.search.web;

import com.treelearn.app.search.web.SearchCredentials;
import com.treelearn.app.ui.SearchSettingsPresenter;
import com.treelearn.app.wiring.PreferenceSearchSettingsStore;
import com.treelearn.data.secret.KeystoreSecretStore;
import com.treelearn.data.secret.SecretStoreException;

public final class KeystoreSearchCredentials
implements SearchCredentials {
    private final KeystoreSecretStore secrets;

    public KeystoreSearchCredentials(KeystoreSecretStore secrets) {
        if (secrets == null) {
            throw new IllegalArgumentException("the keystore must be supplied");
        }
        this.secrets = secrets;
    }

    @Override
    public char[] keyFor(SearchSettingsPresenter.Engine engine) {
        if (!this.secrets.hasSecret(PreferenceSearchSettingsStore.aliasOf(engine))) {
            return null;
        }
        try {
            return this.secrets.read(PreferenceSearchSettingsStore.aliasOf(engine));
        }
        catch (SecretStoreException unreadable) {
            return null;
        }
    }
}

