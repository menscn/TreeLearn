package com.treelearn.app.ui;

/** What the settings screen is asked to do besides drawing. */
public interface SettingsSink {

    void render(SettingsViewState state);
}