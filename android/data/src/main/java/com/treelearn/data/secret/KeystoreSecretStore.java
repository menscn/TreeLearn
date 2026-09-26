package com.treelearn.data.secret;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.Key;
import java.security.KeyStore;
import java.util.Arrays;

import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * Stores the DeepSeek API key as an AES-GCM envelope under a key the Android Keystore owns
 * (technical design 4 and 14).
 *
 * <p>The Keystore never hands out the AES key, so a Preferences file that leaks or a device image
 * that is read offline still yields nothing. What lands on disk is exactly one ciphertext, one IV
 * and a schema version - never the key, and never enough of it to reconstruct the key.
 *
 * <p>Reads are call-for-use by contract: {@link #read()} returns a fresh {@code char[]} that the
 * caller owns and should wipe, and this class keeps no decrypted copy, no field, no cache and no
 * {@code toString()} that carries the key. {@link #hasSecret()} answers "is an envelope stored",
 * which is not the same as "is it readable": a corrupted envelope or a lost Keystore entry still
 * reports {@code true} and fails loudly on {@link #read()}, so the UI can ask for the key again.
 *
 * <p>T-084 adds the provider namespace: {@link #save(String, char[])}-style overloads file each
 * provider's envelope under its own alias ({@link ProviderKeyNamespace}, one provider one alias),
 * and {@link #migrateLegacySecret(String)} moves the legacy single-key entry into a provider's slot
 * on the first read of the new interface, keeping the old entry whenever the move does not confirm.
 */
public final class KeystoreSecretStore {

    /** Preferences file for the secret alone: no other value shares it, and backup rules exclude it. */
    public static final String PREFERENCES_NAME = "treelearn_secret";

    public static final String PREFERENCE_KEY_ENVELOPE = "deepseek_api_key_envelope";

    /** Becomes an unreadable Keystore entry once it is deleted; nothing else may reuse this name. */
    public static final String KEY_ALIAS = "com.treelearn.secret.deepseek-api-key";

    private static final String KEYSTORE_PROVIDER = "AndroidKeyStore";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int KEY_SIZE_BITS = 256;
    private static final int GCM_TAG_BITS = 128;

    private final SharedPreferences preferences;

    public KeystoreSecretStore(Context context) {
        this(context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE));
    }

    KeystoreSecretStore(SharedPreferences preferences) {
        this.preferences = preferences;
    }

    public boolean hasSecret() {
        return preferences.contains(PREFERENCE_KEY_ENVELOPE);
    }

    /**
     * Whether an envelope is stored for this provider. Deliberately a boolean: existence is not
     * readability, and no answer here may carry the key itself (KeyVault contract).
     */
    public boolean hasSecret(String providerId) {
        return preferences.contains(ProviderKeyNamespace.keystoreAlias(providerId));
    }

    public void save(char[] secret) throws SecretStoreException {
        saveEnvelope(PREFERENCE_KEY_ENVELOPE, KEY_ALIAS, secret);
    }

    /** Stores the credential under the provider's own alias, overwriting any previous value. */
    public void save(String providerId, char[] secret) throws SecretStoreException {
        String alias = ProviderKeyNamespace.keystoreAlias(providerId);
        saveEnvelope(alias, alias, secret);
    }

    public char[] read() throws SecretStoreException {
        return readEnvelope(PREFERENCE_KEY_ENVELOPE, KEY_ALIAS);
    }

    /** Reads the provider's credential; call-for-use, the caller wipes the returned buffer. */
    public char[] read(String providerId) throws SecretStoreException {
        String alias = ProviderKeyNamespace.keystoreAlias(providerId);
        return readEnvelope(alias, alias);
    }

    public void delete() {
        deleteEnvelope(PREFERENCE_KEY_ENVELOPE, KEY_ALIAS);
    }

    /**
     * Removes this provider's envelope and Keystore entry; never throws: a leftover alias is
     * unusable without its ciphertext, and clearing a key the user asked to clear must succeed.
     */
    public void delete(String providerId) {
        String alias = ProviderKeyNamespace.keystoreAlias(providerId);
        deleteEnvelope(alias, alias);
    }

    /**
     * Lazily moves the legacy single-key entry into a provider's namespace, called before the first
     * read of the new interface. The move is one-way and confirmed: the legacy value is read, saved
     * under the provider's alias, and only then dropped. Any failure keeps the legacy entry exactly
     * as it was - a duplicated key is recoverable, a lost one is not. Idempotent: a run with no
     * legacy entry, or one whose target already exists, does nothing.
     *
     * @return whether a migration actually happened
     */
    public boolean migrateLegacySecret(String targetProviderId) {
        if (!preferences.contains(PREFERENCE_KEY_ENVELOPE)
                || hasSecret(targetProviderId)) {
            return false;
        }
        char[] secret = null;
        try {
            secret = read();
            save(targetProviderId, secret);
        } catch (SecretStoreException unreadable) {
            return false;
        } finally {
            if (secret != null) {
                Arrays.fill(secret, '\0');
            }
        }
        delete();
        return true;
    }

    private void saveEnvelope(String preferenceKey, String alias, char[] secret)
            throws SecretStoreException {
        if (secret == null || secret.length == 0) {
            throw new IllegalArgumentException("a secret to store must not be empty");
        }
        byte[] plaintext = new String(secret).getBytes(StandardCharsets.UTF_8);
        try {
            byte[] initialisationVector;
            byte[] ciphertext;
            try {
                Cipher cipher = Cipher.getInstance(TRANSFORMATION);
                cipher.init(Cipher.ENCRYPT_MODE, ensureKey(alias));
                initialisationVector = cipher.getIV();
                ciphertext = cipher.doFinal(plaintext);
            } catch (GeneralSecurityException | IOException refused) {
                throw new SecretStoreException(SecretStoreError.SECURE_STORAGE_FAILURE,
                        "the Android Keystore refused to encrypt the API key; enter it again", refused);
            }
            String envelope = SecretEnvelope
                    .of(SecretEnvelope.CURRENT_SCHEMA_VERSION, initialisationVector, ciphertext).encode();
            // commit(), not apply(): apply() only promises the in-memory map, so a process killed
            // right after the user saved a key could come back with no key on disk. The one lint
            // warning on this file (ApplySharedPref) is this deliberate choice.
            if (!preferences.edit().putString(preferenceKey, envelope).commit()) {
                throw new SecretStoreException(SecretStoreError.SECURE_STORAGE_FAILURE,
                        "the encrypted API key could not be written to this device");
            }
        } finally {
            Arrays.fill(plaintext, (byte) 0);
        }
    }

    private char[] readEnvelope(String preferenceKey, String alias) throws SecretStoreException {
        String stored = preferences.getString(preferenceKey, null);
        if (stored == null) {
            throw new SecretStoreException(SecretStoreError.NO_STORED_SECRET,
                    "no API key has been stored on this device");
        }
        SecretEnvelope envelope = SecretEnvelope.decode(stored);
        byte[] plaintext;
        try {
            Key key = openKeyStore().getKey(alias, null);
            if (!(key instanceof SecretKey)) {
                throw new SecretStoreException(SecretStoreError.KEY_ENTRY_MISSING,
                        "the encrypted API key is on this device but its Keystore entry is gone; "
                                + "enter the API key again");
            }
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key,
                    new GCMParameterSpec(GCM_TAG_BITS, envelope.initialisationVector()));
            plaintext = cipher.doFinal(envelope.ciphertext());
        } catch (AEADBadTagException tampered) {
            throw new SecretStoreException(SecretStoreError.DECRYPTION_FAILED,
                    "the stored API key failed authentication and was discarded; enter it again", tampered);
        } catch (GeneralSecurityException | IOException unusable) {
            throw new SecretStoreException(SecretStoreError.DECRYPTION_FAILED,
                    "the stored API key could not be decrypted; enter it again", unusable);
        }
        try {
            return new String(plaintext, StandardCharsets.UTF_8).toCharArray();
        } finally {
            Arrays.fill(plaintext, (byte) 0);
        }
    }

    private void deleteEnvelope(String preferenceKey, String alias) {
        try {
            KeyStore keyStore = openKeyStore();
            if (keyStore.containsAlias(alias)) {
                keyStore.deleteEntry(alias);
            }
        } catch (GeneralSecurityException | IOException leftBehind) {
            // Deliberately not fatal; see the method comment.
        }
        preferences.edit().remove(preferenceKey).commit();
    }

    private static SecretKey ensureKey(String alias) throws GeneralSecurityException, IOException {
        KeyStore keyStore = openKeyStore();
        if (keyStore.containsAlias(alias)) {
            Key existing = keyStore.getKey(alias, null);
            if (existing instanceof SecretKey) {
                return (SecretKey) existing;
            }
        }
        KeyGenerator generator =
                KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER);
        generator.init(new KeyGenParameterSpec.Builder(alias,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(KEY_SIZE_BITS)
                .setRandomizedEncryptionRequired(true)
                .build());
        return generator.generateKey();
    }

    private static KeyStore openKeyStore() throws GeneralSecurityException, IOException {
        KeyStore keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER);
        keyStore.load(null);
        return keyStore;
    }
}
