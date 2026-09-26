/*
 * Decompiled with CFR 0.152.
 */
package com.treelearn.app.ui;

import com.treelearn.app.ui.KeySnapshot;
import com.treelearn.app.ui.SettingsActions;
import com.treelearn.app.ui.SettingsSink;
import com.treelearn.app.ui.SettingsStore;
import com.treelearn.app.ui.SettingsViewState;
import com.treelearn.app.ui.UiThread;
import java.util.Arrays;
import java.util.concurrent.Executor;

public final class SettingsPresenter
implements SettingsActions {
    private final SettingsSink sink;
    private final UiThread ui;
    private final Executor background;
    private final SettingsStore store;
    private SettingsViewState state;

    public SettingsPresenter(SettingsSink sink, UiThread ui, Executor background, SettingsStore store) {
        this.sink = SettingsPresenter.require(sink, "sink");
        this.ui = SettingsPresenter.require(ui, "ui");
        this.background = SettingsPresenter.require(background, "background");
        this.store = SettingsPresenter.require(store, "store");
    }

    public SettingsViewState state() {
        return this.state;
    }

    public void start() {
        this.background.execute(() -> {
            KeySnapshot key = this.store.readKey();
            this.ui.post(() -> this.render(SettingsViewState.of(key)));
        });
    }

    @Override
    public void onSavePressed(char[] candidate) {
        if (this.state == null) {
            return;
        }
        if (candidate == null || candidate.length == 0) {
            this.render(this.state.withNotice(SettingsViewState.Notice.KEY_REQUIRED));
            return;
        }
        char[] owned = candidate;
        this.background.execute(() -> this.storeKey(owned));
    }

    @Override
    public void onDeletePressed() {
        if (this.state == null) {
            return;
        }
        this.background.execute(() -> {
            this.store.deleteKey();
            this.ui.post(() -> this.render(this.state.withKey(KeySnapshot.missing()).withNotice(null)));
        });
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    private void storeKey(char[] owned) {
        KeySnapshot stored = null;
        SettingsViewState.Notice failure = null;
        try {
            this.store.saveKey(owned);
            stored = this.store.readKey();
        }
        catch (SettingsStore.SecretStoreRefused refused) {
            failure = SettingsViewState.Notice.KEY_SAVE_FAILED;
        }
        finally {
            Arrays.fill(owned, '\u0000');
        }
        if (stored == null) {
            SettingsViewState.Notice notice = failure;
            this.ui.post(() -> this.render(this.state.withNotice(notice)));
            return;
        }
        KeySnapshot saved = stored;
        this.ui.post(() -> this.render(this.state.withKey(saved).withNotice(null)));
    }

    private void render(SettingsViewState next) {
        this.state = next;
        this.sink.render(next);
    }

    private static <T> T require(T value, String name) {
        if (value == null) {
            throw new IllegalArgumentException(name + " must not be null");
        }
        return value;
    }
}

