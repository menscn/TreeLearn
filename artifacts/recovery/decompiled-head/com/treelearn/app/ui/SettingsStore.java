/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.treelearn.core.domain.GenerationModel
 */
package com.treelearn.app.ui;

import com.treelearn.app.ui.KeySnapshot;
import com.treelearn.core.domain.GenerationModel;

public interface SettingsStore {
    public GenerationModel model();

    public void setModel(GenerationModel var1);

    public boolean thinkingEnabled();

    public void setThinkingEnabled(boolean var1);

    public KeySnapshot readKey();

    public void saveKey(char[] var1) throws SecretStoreRefused;

    public void deleteKey();

    public static final class SecretStoreRefused
    extends Exception {
        public SecretStoreRefused(String detail) {
            super(detail);
        }
    }
}

