package com.treelearn.data.secret;

import java.util.Base64;

/**
 * The only representation of the DeepSeek API key that is ever persisted (technical design 4 and 14).
 *
 * <p>An envelope is one AES-GCM ciphertext, the initialisation vector the Android Keystore generated
 * for that single encryption, and a schema version so a later migration can recognise what it reads.
 * The key itself lives in the platform Keystore and cannot be reconstructed from anything here;
 * {@link #toString()} reports lengths only.
 *
 * <p>Encoded form: {@code <schemaVersion>:<base64 iv>:<base64 ciphertext>}. Every malformed input is
 * rejected, so the caller fails closed and asks for the key again instead of acting on a half-parsed
 * value.
 */
public final class SecretEnvelope {

    /** Bump only together with a decode branch that still understands the previous version. */
    public static final int CURRENT_SCHEMA_VERSION = 1;

    private final int schemaVersion;
    private final byte[] initialisationVector;
    private final byte[] ciphertext;

    private SecretEnvelope(int schemaVersion, byte[] initialisationVector, byte[] ciphertext) {
        this.schemaVersion = schemaVersion;
        this.initialisationVector = initialisationVector;
        this.ciphertext = ciphertext;
    }

    public static SecretEnvelope of(int schemaVersion, byte[] initialisationVector, byte[] ciphertext) {
        if (schemaVersion != CURRENT_SCHEMA_VERSION) {
            throw new IllegalArgumentException("unsupported envelope schema version: " + schemaVersion);
        }
        if (initialisationVector == null || initialisationVector.length == 0) {
            throw new IllegalArgumentException("an envelope needs the IV its ciphertext was produced with");
        }
        if (ciphertext == null || ciphertext.length == 0) {
            throw new IllegalArgumentException("an envelope needs a ciphertext");
        }
        return new SecretEnvelope(schemaVersion, initialisationVector.clone(), ciphertext.clone());
    }

    public static SecretEnvelope decode(String encoded) throws SecretStoreException {
        if (encoded == null) {
            throw corrupted("no envelope was stored");
        }
        String[] fields = encoded.split(":", -1);
        if (fields.length != 3) {
            throw corrupted("the stored envelope does not have three fields");
        }
        int schemaVersion;
        try {
            schemaVersion = Integer.parseInt(fields[0]);
        } catch (NumberFormatException notAVersion) {
            throw corrupted("the stored envelope carries no schema version");
        }
        if (schemaVersion != CURRENT_SCHEMA_VERSION) {
            throw new SecretStoreException(SecretStoreError.UNSUPPORTED_ENVELOPE_VERSION,
                    "the stored envelope was written by schema version " + schemaVersion);
        }
        byte[] initialisationVector = decodeBase64(fields[1]);
        byte[] ciphertext = decodeBase64(fields[2]);
        if (initialisationVector.length == 0 || ciphertext.length == 0) {
            throw corrupted("the stored envelope is missing its IV or its ciphertext");
        }
        return new SecretEnvelope(schemaVersion, initialisationVector, ciphertext);
    }

    public int schemaVersion() {
        return schemaVersion;
    }

    public byte[] initialisationVector() {
        return initialisationVector.clone();
    }

    public byte[] ciphertext() {
        return ciphertext.clone();
    }

    public String encode() {
        Base64.Encoder encoder = Base64.getEncoder();
        return schemaVersion + ":" + encoder.encodeToString(initialisationVector) + ":"
                + encoder.encodeToString(ciphertext);
    }

    @Override
    public String toString() {
        return "SecretEnvelope{schemaVersion=" + schemaVersion
                + ", ivBytes=" + initialisationVector.length
                + ", ciphertextBytes=" + ciphertext.length + "}";
    }

    private static SecretStoreException corrupted(String reason) {
        return new SecretStoreException(SecretStoreError.CORRUPTED_ENVELOPE, reason);
    }

    private static byte[] decodeBase64(String field) throws SecretStoreException {
        try {
            return Base64.getDecoder().decode(field);
        } catch (IllegalArgumentException notBase64) {
            throw corrupted("the stored envelope is not valid base64");
        }
    }
}
