package com.treelearn.app;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class SettingsBackRouteTest {

    @Test
    public void settingsOpenedFromHomeReturnsHome() {
        assertEquals(SettingsBackRoute.Target.HOME, SettingsBackRoute.afterBack(null));
    }

    @Test
    public void settingsOpenedFromChatReturnsToChat() {
        assertEquals(SettingsBackRoute.Target.CHAT,
                SettingsBackRoute.afterBack("branch-123"));
    }
}
