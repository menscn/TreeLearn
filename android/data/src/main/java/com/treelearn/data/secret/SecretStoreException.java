package com.treelearn.data.secret;

/**
 * A fail-closed secret-store failure.
 *
 * <p>Messages describe the class of failure so the user can act on it ("enter the key again") and
 * never carry the key, its ciphertext or its length. The cause is attached for the stack trace only;
 * it comes from platform crypto, which reports no key material either.
 */
public final class SecretStoreException extends Exception {

    private static final long serialVersionUID = 1L;

    private final SecretStoreError code;

    public SecretStoreException(SecretStoreError code, String message) {
        super(message);
        this.code = code;
    }

    public SecretStoreException(SecretStoreError code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public SecretStoreError code() {
        return code;
    }
}
