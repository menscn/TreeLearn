package com.treelearn.app;

public final class SettingsBackRoute {

    public enum Target {
        HOME,
        CHAT
    }

    private SettingsBackRoute() {
    }

    public static Target afterBack(String activeChatBranchId) {
        return activeChatBranchId == null ? Target.HOME : Target.CHAT;
    }
}
