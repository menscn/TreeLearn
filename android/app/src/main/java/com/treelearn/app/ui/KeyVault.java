package com.treelearn.app.ui;

/**
 * The one question the home composer asks about the credential: is a key stored on this device?
 *
 * <p>Deliberately a boolean and not the key itself. Whether a key is <em>accepted</em> by the
 * provider is a different fact, learned from a failed attempt and reported as a
 * {@link HomeViewState.Notice}; nothing here can decrypt, log or leak the secret.
 */
public interface KeyVault {

    boolean isConfigured();

    /** The same question for one provider slot, not the legacy single key. */
    boolean isConfigured(String providerId);
}