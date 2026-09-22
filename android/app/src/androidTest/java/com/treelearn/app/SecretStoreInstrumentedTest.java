package com.treelearn.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.treelearn.data.secret.KeystoreSecretStore;
import com.treelearn.data.secret.SecretEnvelope;
import com.treelearn.data.secret.SecretStoreError;
import com.treelearn.data.secret.SecretStoreException;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.KeyStore;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * T-005 acceptance evidence for the DeepSeek key store on a real device (API 36 emulator).
 *
 * <p>Only a real Keystore can answer the questions that matter here: whether the AES-GCM key lives
 * in the platform keystore, whether a ciphertext written by one instance is readable by the next
 * one, whether tampering is rejected, and whether the platform ever lets the plaintext reach the
 * disk. A JVM fake would prove none of that, so everything in this class runs on the device.
 *
 * <p>The negative assertions carry their own positive control instead of being trusted on their
 * own: {@link #theStoredSecretExistsOnDiskOnlyAsCiphertext} must FIND the stored envelope before
 * its "no plaintext anywhere" result counts, because a search that finds nothing at all would
 * otherwise read as a pass.
 *
 * <p>Both secrets below are obviously synthetic placeholders. No real credential is committed to
 * this repository, and the fixtures are shaped like nothing a provider would issue.
 */
@RunWith(AndroidJUnit4.class)
public class SecretStoreInstrumentedTest {

    private static final String SYNTHETIC_SECRET =
            "SYNTHETIC T005 PLACEHOLDER KEY (not a real credential)";

    private static final String SYNTHETIC_REPLACEMENT =
            "SYNTHETIC T005 REPLACEMENT VALUE (also not a real credential)";

    private static final long MAX_SCANNED_FILE_BYTES = 16L * 1024L * 1024L;

    private Context context;

    private KeystoreSecretStore store;

    @Before
    public void setUp() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        store = new KeystoreSecretStore(context);
        store.delete();
    }

    @After
    public void tearDown() {
        store.delete();
    }

    @Test
    public void aNewStoreInstanceReadsTheKeyBackFromPersistedState() throws Exception {
        store.save(SYNTHETIC_SECRET.toCharArray());

        KeystoreSecretStore afterRestart = new KeystoreSecretStore(context);

        assertTrue(afterRestart.hasSecret());
        assertEquals(SYNTHETIC_SECRET, new String(afterRestart.read()));
    }

    @Test
    public void theStoredSecretExistsOnDiskOnlyAsCiphertext() throws Exception {
        store.save(SYNTHETIC_SECRET.toCharArray());
        String storedEnvelope = preferences().getString(KeystoreSecretStore.PREFERENCE_KEY_ENVELOPE, null);
        assertNotNull("saving must leave an envelope in Preferences", storedEnvelope);

        List<String> carryingEnvelope =
                filesContaining(storedEnvelope.getBytes(StandardCharsets.UTF_8));
        List<String> carryingPlaintext =
                filesContaining(SYNTHETIC_SECRET.getBytes(StandardCharsets.UTF_8));

        assertFalse("positive control: the scan must be able to find the stored envelope, "
                        + "otherwise its verdict on the plaintext means nothing",
                carryingEnvelope.isEmpty());
        assertTrue("the API key must not exist in clear text anywhere under " + context.getDataDir()
                        + " (found in " + carryingPlaintext + ")",
                carryingPlaintext.isEmpty());
    }

    @Test
    public void savingAgainReplacesTheCiphertextAndDecryptsTheNewValue() throws Exception {
        store.save(SYNTHETIC_SECRET.toCharArray());
        String firstEnvelope = preferences().getString(KeystoreSecretStore.PREFERENCE_KEY_ENVELOPE, null);

        store.save(SYNTHETIC_REPLACEMENT.toCharArray());

        String secondEnvelope = preferences().getString(KeystoreSecretStore.PREFERENCE_KEY_ENVELOPE, null);
        assertFalse("replacing the key must not keep the previous ciphertext",
                firstEnvelope.equals(secondEnvelope));
        assertEquals(SYNTHETIC_REPLACEMENT, new String(store.read()));
        assertTrue(filesContaining(SYNTHETIC_SECRET.getBytes(StandardCharsets.UTF_8)).isEmpty());
    }

    @Test
    public void deletingRemovesBothTheKeystoreAliasAndTheCiphertext() throws Exception {
        store.save(SYNTHETIC_SECRET.toCharArray());

        store.delete();

        assertFalse(store.hasSecret());
        assertFalse(preferences().contains(KeystoreSecretStore.PREFERENCE_KEY_ENVELOPE));
        assertFalse("deleting the key must also drop the Keystore alias",
                androidKeyStore().containsAlias(KeystoreSecretStore.KEY_ALIAS));
        SecretStoreException failure = assertThrows(SecretStoreException.class, () -> store.read());
        assertEquals(SecretStoreError.NO_STORED_SECRET, failure.code());
    }

    @Test
    public void readingWithNothingStoredFailsClosed() {
        assertFalse(store.hasSecret());

        SecretStoreException failure = assertThrows(SecretStoreException.class, () -> store.read());

        assertEquals(SecretStoreError.NO_STORED_SECRET, failure.code());
        assertDiagnosable(failure, SYNTHETIC_SECRET);
    }

    @Test
    public void aCorruptedEnvelopeFailsClosedInsteadOfReturningGarbage() throws Exception {
        store.save(SYNTHETIC_SECRET.toCharArray());
        preferences().edit()
                .putString(KeystoreSecretStore.PREFERENCE_KEY_ENVELOPE, "not-an-envelope")
                .commit();

        SecretStoreException failure = assertThrows(SecretStoreException.class, () -> store.read());

        assertEquals(SecretStoreError.CORRUPTED_ENVELOPE, failure.code());
        assertDiagnosable(failure, SYNTHETIC_SECRET);
    }

    @Test
    public void tamperedCiphertextFailsAuthentication() throws Exception {
        store.save(SYNTHETIC_SECRET.toCharArray());
        SecretEnvelope envelope = SecretEnvelope.decode(
                preferences().getString(KeystoreSecretStore.PREFERENCE_KEY_ENVELOPE, null));
        byte[] tampered = envelope.ciphertext();
        tampered[tampered.length - 1] ^= 0x01;
        preferences().edit()
                .putString(KeystoreSecretStore.PREFERENCE_KEY_ENVELOPE,
                        SecretEnvelope.of(envelope.schemaVersion(), envelope.initialisationVector(), tampered)
                                .encode())
                .commit();

        SecretStoreException failure = assertThrows(SecretStoreException.class, () -> store.read());

        assertEquals("a flipped ciphertext bit must be reported as an authentication failure",
                SecretStoreError.DECRYPTION_FAILED, failure.code());
        assertDiagnosable(failure, SYNTHETIC_SECRET);
    }

    @Test
    public void aMissingKeystoreAliasRequiresTheKeyToBeEnteredAgain() throws Exception {
        store.save(SYNTHETIC_SECRET.toCharArray());
        androidKeyStore().deleteEntry(KeystoreSecretStore.KEY_ALIAS);

        assertTrue("the ciphertext is still on disk, so existence is not the same as readability",
                store.hasSecret());
        SecretStoreException failure = assertThrows(SecretStoreException.class, () -> store.read());

        assertEquals(SecretStoreError.KEY_ENTRY_MISSING, failure.code());
        assertDiagnosable(failure, SYNTHETIC_SECRET);
    }

    private SharedPreferences preferences() {
        return context.getSharedPreferences(KeystoreSecretStore.PREFERENCES_NAME, Context.MODE_PRIVATE);
    }

    private static KeyStore androidKeyStore() throws Exception {
        KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
        keyStore.load(null);
        return keyStore;
    }

    private static void assertDiagnosable(SecretStoreException failure, String secret) {
        assertNotNull("a failure has to say something a user or a log can act on", failure.getMessage());
        assertFalse("failure text must not echo the key: " + failure.getMessage(),
                failure.getMessage().contains(secret));
        assertFalse("failure toString must not echo the key: " + failure,
                failure.toString().contains(secret));
    }

    private List<String> filesContaining(byte[] needle) throws Exception {
        List<String> matches = new ArrayList<>();
        Deque<File> pending = new ArrayDeque<>();
        pending.add(context.getDataDir());
        while (!pending.isEmpty()) {
            File current = pending.removeFirst();
            File[] children = current.listFiles();
            if (children != null) {
                for (File child : children) {
                    pending.addLast(child);
                }
                continue;
            }
            if (current.isFile() && current.length() > 0 && current.length() <= MAX_SCANNED_FILE_BYTES
                    && ByteSearch.contains(Files.readAllBytes(current.toPath()), needle)) {
                matches.add(current.getAbsolutePath());
            }
        }
        return matches;
    }
}
