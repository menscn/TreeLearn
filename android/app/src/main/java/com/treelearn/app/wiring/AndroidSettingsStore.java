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

/**
 * The two stored facts the UI reads, backed by the real stores: the credential (AES-GCM envelope in
 * the platform keystore, T-005) and the non-sensitive preferences (model tier, thinking switch).
 *
 * <p>This class is the only place in the app layer that touches key material, and it does so for one
 * purpose: to mask it. The buffer returned by {@link KeystoreSecretStore#read()} is wiped in a
 * {@code finally} before this method returns, so no caller and no field ever holds the secret.
 *
 * <p>{@link #isConfigured()} answers "is an envelope stored", which is deliberately weaker than "is
 * it readable": the store reports {@code true} for a corrupted envelope too, and only
 * {@link #readKey()} can tell the difference. The two answers map onto the two UI states - the
 * composer's send guard uses the cheap one, the settings screen shows the honest one.
 */
public final class AndroidSettingsStore implements SettingsStore, KeyVault {

    /** The built-in provider slot every legacy single-key read and write is migrated into. */
    private static final String BUILTIN_DEEPSEEK_ID = "00000000-0000-0000-0000-000000000001";

    private final SettingsRepository preferences;
    private final KeystoreSecretStore secrets;

    public AndroidSettingsStore(Context context) {
        this.preferences = new SettingsRepository(context);
        this.secrets = new KeystoreSecretStore(context);
    }

    @Override
    public GenerationModel model() {
        return preferences.model();
    }

    @Override
    public void setModel(GenerationModel model) {
        preferences.setModel(model);
    }

    @Override
    public boolean thinkingEnabled() {
        return preferences.thinkingEnabled();
    }

    @Override
    public void setThinkingEnabled(boolean enabled) {
        preferences.setThinkingEnabled(enabled);
    }

    @Override
    public boolean searchEnabled() {
        return preferences.searchEnabled();
    }

    @Override
    public void setSearchEnabled(boolean enabled) {
        preferences.setSearchEnabled(enabled);
    }

    @Override
    public boolean isConfigured() {
        return isConfigured(BUILTIN_DEEPSEEK_ID);
    }

    @Override
    public boolean isConfigured(String providerId) {
        secrets.migrateLegacySecret(BUILTIN_DEEPSEEK_ID);
        return secrets.hasSecret(providerId);
    }

    @Override
    public KeySnapshot readKey() {
        secrets.migrateLegacySecret(BUILTIN_DEEPSEEK_ID);
        if (!secrets.hasSecret(BUILTIN_DEEPSEEK_ID)) {
            return KeySnapshot.missing();
        }
        char[] secret = null;
        try {
            secret = secrets.read(BUILTIN_DEEPSEEK_ID);
            return KeySnapshot.ofStoredKey(secret);
        } catch (SecretStoreException unreadable) {
            return KeySnapshot.unreadable();
        } finally {
            if (secret != null) {
                Arrays.fill(secret, '\0');
            }
        }
    }

    @Override
    public void saveKey(char[] secret) throws SecretStoreRefused {
        try {
            secrets.save(BUILTIN_DEEPSEEK_ID, secret);
        } catch (SecretStoreException refused) {
            // Category and exception name only: a keystore failure message is never key material,
            // but nothing here is allowed to become a place a secret could travel through.
            throw new SecretStoreRefused("the keystore refused the write: "
                    + refused.getClass().getSimpleName());
        }
    }

    @Override
    public void deleteKey() {
        secrets.delete(BUILTIN_DEEPSEEK_ID);
        secrets.delete();
    }
}