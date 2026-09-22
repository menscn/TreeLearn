package com.treelearn.data.secret;

/**
 * Why a key-store operation failed. Callers branch on this, so every constant here has a producer
 * in {@link KeystoreSecretStore} or {@link SecretEnvelope} - a classification nobody can ever
 * receive would send a caller down a path that does not exist.
 *
 * <p>All of them are fail-closed: none of them may be reported together with a partially decrypted
 * value. A user who sees one of these enters the key again (technical design 14).
 */
public enum SecretStoreError {

    /** Nothing has been stored on this device yet. Produced by a read on an empty Preferences. */
    NO_STORED_SECRET,

    /** The stored envelope is not decodable. Produced by the encoder/decoder in {@link SecretEnvelope}. */
    CORRUPTED_ENVELOPE,

    /** The envelope was written by a schema version this build has no migration branch for. */
    UNSUPPORTED_ENVELOPE_VERSION,

    /** The envelope is on disk but the Keystore entry that encrypted it is gone. */
    KEY_ENTRY_MISSING,

    /** AES-GCM authentication failed: the ciphertext was altered, or a different key wrote it. */
    DECRYPTION_FAILED,

    /** The platform's secure storage refused the operation, or the encrypted blob could not be written. */
    SECURE_STORAGE_FAILURE
}
