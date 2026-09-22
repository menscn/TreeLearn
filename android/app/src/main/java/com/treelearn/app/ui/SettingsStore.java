package com.treelearn.app.ui;

import com.treelearn.core.domain.GenerationModel;

/**
 * The local preferences both the settings screen and the composer read and write.
 *
 * <p>One port for the two screens on purpose: the model tier and the thinking switch are a single
 * stored fact each, and the input-bar chip and the settings screen must never disagree about what is
 * currently selected.
 */
public interface SettingsStore {

    GenerationModel model();

    void setModel(GenerationModel model);

    boolean thinkingEnabled();

    void setThinkingEnabled(boolean enabled);

    /**
     * The stored credential, masked.
     *
     * <p>Implementations decrypt only to build the mask and must wipe the buffer immediately; nothing
     * that leaves this call may carry key material beyond those few characters.
     */
    KeySnapshot readKey();

    /**
     * Stores (or replaces) the credential.
     *
     * @param secret the caller's buffer; ownership transfers to the implementation, which wipes it
     * @throws SecretStoreRefused when the platform keystore refused the write
     */
    void saveKey(char[] secret) throws SecretStoreRefused;

    /** Removes the credential. Never fails: clearing a key the learner asked to clear must succeed. */
    void deleteKey();

    /** Thrown when the platform keystore refused a save, so the screen can ask the learner to retry. */
    final class SecretStoreRefused extends Exception {

        public SecretStoreRefused(String detail) {
            super(detail);
        }
    }
}