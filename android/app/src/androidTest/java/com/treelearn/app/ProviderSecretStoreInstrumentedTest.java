package com.treelearn.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.treelearn.data.secret.KeystoreSecretStore;
import com.treelearn.data.secret.ProviderKeyNamespace;

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
 * T-084 acceptance evidence for the multi-provider key namespace on a real device.
 *
 * <p>One provider, one alias ({@code tl.provider.<provider-uuid>}); a legacy single-key entry
 * migrates lazily to the builtin DeepSeek provider's alias and only after the new envelope is
 * confirmed written - a failed migration keeps the old entry, because a duplicated key is
 * recoverable and a lost one is not. As in T-005, the plaintext scan carries a positive control so
 * that "found nothing" can only mean the envelope was findable and the plaintext was not.
 *
 * <p>Both fixtures are synthetic placeholders; no real credential is committed.
 */
@RunWith(AndroidJUnit4.class)
public class ProviderSecretStoreInstrumentedTest {

    private static final String BUILTIN_DEEPSEEK_ID = "00000000-0000-0000-0000-000000000001";

    private static final String PROVIDER_A_ID = "11111111-1111-1111-1111-111111111111";

    private static final String PROVIDER_B_ID = "22222222-2222-2222-2222-222222222222";

    private static final String SYNTHETIC_SECRET =
            "SYNTHETIC T084 PLACEHOLDER KEY (not a real credential)";

    private static final String SYNTHETIC_OTHER =
            "SYNTHETIC T084 SECOND PROVIDER VALUE (also not a real credential)";

    private static final long MAX_SCANNED_FILE_BYTES = 16L * 1024L * 1024L;

    private Context context;

    private KeystoreSecretStore store;

    @Before
    public void setUp() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        store = new KeystoreSecretStore(context);
        store.delete();
        store.delete(BUILTIN_DEEPSEEK_ID);
        store.delete(PROVIDER_A_ID);
        store.delete(PROVIDER_B_ID);
    }

    @After
    public void tearDown() {
        store.delete();
        store.delete(BUILTIN_DEEPSEEK_ID);
        store.delete(PROVIDER_A_ID);
        store.delete(PROVIDER_B_ID);
    }

    @Test
    public void twoProvidersStoreReadAndDeleteIndependently() throws Exception {
        store.save(PROVIDER_A_ID, SYNTHETIC_SECRET.toCharArray());
        store.save(PROVIDER_B_ID, SYNTHETIC_OTHER.toCharArray());

        KeystoreSecretStore afterRestart = new KeystoreSecretStore(context);

        assertEquals(SYNTHETIC_SECRET, new String(afterRestart.read(PROVIDER_A_ID)));
        assertEquals(SYNTHETIC_OTHER, new String(afterRestart.read(PROVIDER_B_ID)));

        afterRestart.delete(PROVIDER_A_ID);

        assertFalse(afterRestart.hasSecret(PROVIDER_A_ID));
        assertEquals("deleting one provider must not touch another",
                SYNTHETIC_OTHER, new String(afterRestart.read(PROVIDER_B_ID)));
    }

    @Test
    public void hasSecretAnswersExistencePerProviderAndNeverReturnsTheKey() throws Exception {
        assertFalse(store.hasSecret(PROVIDER_A_ID));

        store.save(PROVIDER_A_ID, SYNTHETIC_SECRET.toCharArray());

        assertTrue(store.hasSecret(PROVIDER_A_ID));
        assertFalse("existence for another provider is a separate fact",
                store.hasSecret(PROVIDER_B_ID));
    }

    @Test
    public void theLegacyKeyMigratesToTheBuiltinProviderNamespaceExactlyOnce() throws Exception {
        store.save(SYNTHETIC_SECRET.toCharArray());
        assertTrue(preferences().contains(KeystoreSecretStore.PREFERENCE_KEY_ENVELOPE));

        assertTrue("the first read-path call migrates", store.migrateLegacySecret(BUILTIN_DEEPSEEK_ID));

        assertEquals(SYNTHETIC_SECRET, new String(store.read(BUILTIN_DEEPSEEK_ID)));
        assertFalse("the legacy envelope is gone after a confirmed write",
                preferences().contains(KeystoreSecretStore.PREFERENCE_KEY_ENVELOPE));
        assertFalse("the legacy Keystore alias is retired",
                androidKeyStore().containsAlias(KeystoreSecretStore.KEY_ALIAS));
        assertTrue("the builtin provider now owns its own alias",
                androidKeyStore().containsAlias(ProviderKeyNamespace.keystoreAlias(BUILTIN_DEEPSEEK_ID)));

        assertFalse("a second migration is a no-op", store.migrateLegacySecret(BUILTIN_DEEPSEEK_ID));
        assertEquals("the migrated key still reads after the no-op",
                SYNTHETIC_SECRET, new String(store.read(BUILTIN_DEEPSEEK_ID)));
    }

    @Test
    public void aFailedMigrationKeepsTheLegacyEntry() throws Exception {
        store.save(SYNTHETIC_SECRET.toCharArray());
        preferences().edit()
                .putString(KeystoreSecretStore.PREFERENCE_KEY_ENVELOPE, "not-an-envelope")
                .commit();

        assertFalse(store.migrateLegacySecret(BUILTIN_DEEPSEEK_ID));

        assertTrue("a failed migration must not drop the old entry, even unreadable",
                preferences().contains(KeystoreSecretStore.PREFERENCE_KEY_ENVELOPE));
        assertFalse(store.hasSecret(BUILTIN_DEEPSEEK_ID));
    }

    @Test
    public void providerKeysExistOnDiskOnlyAsCiphertext() throws Exception {
        store.save(PROVIDER_A_ID, SYNTHETIC_SECRET.toCharArray());
        store.save(PROVIDER_B_ID, SYNTHETIC_OTHER.toCharArray());
        String storedEnvelope = preferences()
                .getString(ProviderKeyNamespace.keystoreAlias(PROVIDER_A_ID), null);
        assertNotNull("saving must leave an envelope in Preferences", storedEnvelope);

        List<String> carryingEnvelope =
                filesContaining(storedEnvelope.getBytes(StandardCharsets.UTF_8));
        List<String> carryingFirstPlaintext =
                filesContaining(SYNTHETIC_SECRET.getBytes(StandardCharsets.UTF_8));
        List<String> carryingSecondPlaintext =
                filesContaining(SYNTHETIC_OTHER.getBytes(StandardCharsets.UTF_8));

        assertFalse("positive control: the scan must be able to find the stored envelope, "
                + "otherwise its verdict on the plaintext means nothing", carryingEnvelope.isEmpty());
        assertTrue("the first key must not exist in clear text anywhere under "
                + context.getDataDir() + " (found in " + carryingFirstPlaintext + ")",
                carryingFirstPlaintext.isEmpty());
        assertTrue("the second key must not exist in clear text anywhere under "
                + context.getDataDir() + " (found in " + carryingSecondPlaintext + ")",
                carryingSecondPlaintext.isEmpty());
    }

    private SharedPreferences preferences() {
        return context.getSharedPreferences(KeystoreSecretStore.PREFERENCES_NAME,
                Context.MODE_PRIVATE);
    }

    private static KeyStore androidKeyStore() throws Exception {
        KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
        keyStore.load(null);
        return keyStore;
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
