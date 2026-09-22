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

    public void save(char[] secret) throws SecretStoreException {
        if (secret == null || secret.length == 0) {
            throw new IllegalArgumentException("a secret to store must not be empty");
        }
        byte[] plaintext = new String(secret).getBytes(StandardCharsets.UTF_8);
        try {
            byte[] initialisationVector;
            byte[] ciphertext;
            try {
                Cipher cipher = Cipher.getInstance(TRANSFORMATION);
                cipher.init(Cipher.ENCRYPT_MODE, ensureKey());
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
            if (!preferences.edit().putString(PREFERENCE_KEY_ENVELOPE, envelope).commit()) {
                throw new SecretStoreException(SecretStoreError.SECURE_STORAGE_FAILURE,
                        "the encrypted API key could not be written to this device");
            }
        } finally {
            Arrays.fill(plaintext, (byte) 0);
        }
    }

    public char[] read() throws SecretStoreException {
        String stored = preferences.getString(PREFERENCE_KEY_ENVELOPE, null);
        if (stored == null) {
            throw new SecretStoreException(SecretStoreError.NO_STORED_SECRET,
                    "no API key has been stored on this device");
        }
        SecretEnvelope envelope = SecretEnvelope.decode(stored);
        byte[] plaintext;
        try {
            Key key = openKeyStore().getKey(KEY_ALIAS, null);
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

    /**
     * Removes the ciphertext and the Keystore entry.
     *
     * <p>Never throws: dropping the ciphertext is what makes the key unrecoverable, and a Keystore
     * hiccup must not leave a user unable to clear a key they asked to clear. A leftover alias is
     * unusable without its ciphertext and is reused by the next {@link #save}.
     */
    public void delete() {
        try {
            KeyStore keyStore = openKeyStore();
            if (keyStore.containsAlias(KEY_ALIAS)) {
                keyStore.deleteEntry(KEY_ALIAS);
            }
        } catch (GeneralSecurityException | IOException leftBehind) {
            // Deliberately not fatal; see the method comment.
        }
        preferences.edit().remove(PREFERENCE_KEY_ENVELOPE).commit();
    }

    private static SecretKey ensureKey() throws GeneralSecurityException, IOException {
        KeyStore keyStore = openKeyStore();
        if (keyStore.containsAlias(KEY_ALIAS)) {
            Key existing = keyStore.getKey(KEY_ALIAS, null);
            if (existing instanceof SecretKey) {
                return (SecretKey) existing;
            }
        }
        KeyGenerator generator =
                KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER);
        generator.init(new KeyGenParameterSpec.Builder(KEY_ALIAS,
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
