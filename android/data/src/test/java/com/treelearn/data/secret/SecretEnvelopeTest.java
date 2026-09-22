package com.treelearn.data.secret;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * T-005 JVM acceptance evidence for the persisted secret envelope (technical design 4 / 14).
 *
 * <p>An envelope is the ONLY thing that ever reaches SharedPreferences: an AES-GCM ciphertext and
 * the initialisation vector the Android Keystore generated for it, plus a schema version so a later
 * migration can recognise what it is reading. Nothing in this file touches the platform, so the
 * codec contract is verified in milliseconds instead of on a device.
 *
 * <p>The value below is an obviously synthetic placeholder; no real DeepSeek credential is stored
 * in this repository (rules/sql.md and the T-005 watch-outs both require that).
 */
public class SecretEnvelopeTest {

    private static final String SYNTHETIC_SECRET = "SYNTHETIC T005 PLACEHOLDER (not a real credential)";

    private static final byte[] SYNTHETIC_IV = "t005-iv-0001".getBytes(StandardCharsets.UTF_8);

    @Test
    public void roundTripsVersionIvAndCiphertextThroughTheEncodedForm() throws Exception {
        byte[] ciphertext = SYNTHETIC_SECRET.getBytes(StandardCharsets.UTF_8);

        SecretEnvelope encoded = SecretEnvelope.of(SecretEnvelope.CURRENT_SCHEMA_VERSION, SYNTHETIC_IV, ciphertext);
        SecretEnvelope decoded = SecretEnvelope.decode(encoded.encode());

        assertEquals(SecretEnvelope.CURRENT_SCHEMA_VERSION, decoded.schemaVersion());
        assertArrayEquals(SYNTHETIC_IV, decoded.initialisationVector());
        assertArrayEquals(ciphertext, decoded.ciphertext());
    }

    @Test
    public void neverExposesIvOrCiphertextThroughToString() {
        byte[] ciphertext = SYNTHETIC_SECRET.getBytes(StandardCharsets.UTF_8);
        SecretEnvelope envelope = SecretEnvelope.of(SecretEnvelope.CURRENT_SCHEMA_VERSION, SYNTHETIC_IV, ciphertext);

        String rendered = envelope.toString();

        assertFalse("toString must not carry the plaintext", rendered.contains(SYNTHETIC_SECRET));
        assertFalse("toString must not carry the raw ciphertext",
                rendered.contains(Base64.getEncoder().encodeToString(ciphertext)));
        assertFalse("toString must not carry the raw IV",
                rendered.contains(Base64.getEncoder().encodeToString(SYNTHETIC_IV)));
        assertTrue("toString still has to say enough to debug a migration: " + rendered,
                rendered.contains(Integer.toString(ciphertext.length)));
    }

    @Test
    public void handsOutCopiesSoACallerCannotMutateTheEnvelope() {
        byte[] ciphertext = SYNTHETIC_SECRET.getBytes(StandardCharsets.UTF_8);
        SecretEnvelope envelope = SecretEnvelope.of(SecretEnvelope.CURRENT_SCHEMA_VERSION, SYNTHETIC_IV, ciphertext);

        envelope.initialisationVector()[0] ^= 0x7f;
        envelope.ciphertext()[0] ^= 0x7f;

        assertArrayEquals(SYNTHETIC_IV, envelope.initialisationVector());
        assertArrayEquals(ciphertext, envelope.ciphertext());
    }

    @Test
    public void refusesToBuildAnIncompleteEnvelope() {
        byte[] ciphertext = SYNTHETIC_SECRET.getBytes(StandardCharsets.UTF_8);

        assertThrows(IllegalArgumentException.class,
                () -> SecretEnvelope.of(0, SYNTHETIC_IV, ciphertext));
        assertThrows(IllegalArgumentException.class,
                () -> SecretEnvelope.of(SecretEnvelope.CURRENT_SCHEMA_VERSION, null, ciphertext));
        assertThrows(IllegalArgumentException.class,
                () -> SecretEnvelope.of(SecretEnvelope.CURRENT_SCHEMA_VERSION, new byte[0], ciphertext));
        assertThrows(IllegalArgumentException.class,
                () -> SecretEnvelope.of(SecretEnvelope.CURRENT_SCHEMA_VERSION, SYNTHETIC_IV, null));
        assertThrows(IllegalArgumentException.class,
                () -> SecretEnvelope.of(SecretEnvelope.CURRENT_SCHEMA_VERSION, SYNTHETIC_IV, new byte[0]));
    }

    @Test
    public void classifiesEveryMalformedEncodingAsACorruptedEnvelope() {
        String[] malformed = {
            "",
            "1",
            "1:AAAA",
            "1:AAAA:BBBB:CCCC",
            "v1:AAAA:BBBB",
            "1::BBBB",
            "1:AAAA:",
            "1:!!!!:BBBB",
            "1:AAAA:!!!!",
            "1:AAAA:BBBB ",
        };

        for (String candidate : malformed) {
            SecretStoreException failure =
                    assertThrows("expected a fail-closed decode of <" + candidate + ">",
                            SecretStoreException.class, () -> SecretEnvelope.decode(candidate));
            assertEquals("<" + candidate + "> must be reported as a corrupted envelope",
                    SecretStoreError.CORRUPTED_ENVELOPE, failure.code());
        }
        assertThrows(SecretStoreException.class, () -> SecretEnvelope.decode(null));
    }

    @Test
    public void refusesAnEnvelopeWrittenByAnUnknownSchemaVersion() {
        byte[] ciphertext = SYNTHETIC_SECRET.getBytes(StandardCharsets.UTF_8);
        String fromTheFuture = "2:" + Base64.getEncoder().encodeToString(SYNTHETIC_IV) + ":"
                + Base64.getEncoder().encodeToString(ciphertext);

        SecretStoreException failure = assertThrows(SecretStoreException.class,
                () -> SecretEnvelope.decode(fromTheFuture));

        assertEquals(SecretStoreError.UNSUPPORTED_ENVELOPE_VERSION, failure.code());
        assertFalse(failure.getMessage(), failure.getMessage().contains(SYNTHETIC_SECRET));
    }
}
