package com.treelearn.app.ui;

import java.util.Arrays;
import java.util.concurrent.Executor;

/**
 * The settings screen's decisions: read the credential's state, replace it, delete it.
 *
 * <p>The key text never becomes part of a view state, so no rendered state - and no log line built
 * from one - can carry it. The buffer handed to {@link #onSavePressed(char[])} belongs to this class
 * the moment it arrives and is wiped in a {@code finally}, whether the write succeeded, was refused or
 * threw.
 *
 * <p>Model and thinking are not here: V3.1 6.2.18 point 2 places that switch in the question input
 * instead, and this screen must not offer a second place to change it.
 */
public final class SettingsPresenter implements SettingsActions {

    private final SettingsSink sink;
    private final UiThread ui;
    private final Executor background;
    private final SettingsStore store;

    /** Null until the first read completes; nothing may be saved before the state is known. */
    private SettingsViewState state;

    public SettingsPresenter(SettingsSink sink, UiThread ui, Executor background,
            SettingsStore store) {
        this.sink = require(sink, "sink");
        this.ui = require(ui, "ui");
        this.background = require(background, "background");
        this.store = require(store, "store");
    }

    /** The last state handed to the sink; the tests read it, the screen does not. */
    public SettingsViewState state() {
        return state;
    }

    public void start() {
        background.execute(() -> {
            final KeySnapshot key = store.readKey();
            ui.post(() -> render(SettingsViewState.of(key)));
        });
    }

    /** @param candidate the field's characters; ownership transfers here and the buffer is wiped */
    @Override
    public void onSavePressed(char[] candidate) {
        if (state == null) {
            return;
        }
        if (candidate == null || candidate.length == 0) {
            render(state.withNotice(SettingsViewState.Notice.KEY_REQUIRED));
            return;
        }
        final char[] owned = candidate;
        background.execute(() -> storeKey(owned));
    }

    @Override
    public void onDeletePressed() {
        if (state == null) {
            return;
        }
        background.execute(() -> {
            store.deleteKey();
            ui.post(() -> render(state.withKey(KeySnapshot.missing()).withNotice(null)));
        });
    }

    private void storeKey(char[] owned) {
        KeySnapshot stored = null;
        SettingsViewState.Notice failure = null;
        try {
            store.saveKey(owned);
            stored = store.readKey();
        } catch (SettingsStore.SecretStoreRefused refused) {
            failure = SettingsViewState.Notice.KEY_SAVE_FAILED;
        } finally {
            Arrays.fill(owned, '\0');
        }
        if (stored == null) {
            final SettingsViewState.Notice notice = failure;
            ui.post(() -> render(state.withNotice(notice)));
            return;
        }
        final KeySnapshot saved = stored;
        ui.post(() -> render(state.withKey(saved).withNotice(null)));
    }

    private void render(SettingsViewState next) {
        state = next;
        sink.render(next);
    }

    private static <T> T require(T value, String name) {
        if (value == null) {
            throw new IllegalArgumentException(name + " must not be null");
        }
        return value;
    }
}