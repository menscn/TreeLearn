package com.treelearn.app.ui;

/** What the settings screen may ask its presenter to do. */
public interface SettingsActions {

    /** @param candidate the field's characters; ownership transfers to the presenter, which wipes it */
    void onSavePressed(char[] candidate);

    void onDeletePressed();
}